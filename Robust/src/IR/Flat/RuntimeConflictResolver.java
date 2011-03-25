package IR.Flat;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import Util.Pair;
import Analysis.Disjoint.*;
import Analysis.Pointer.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import IR.State;
import IR.TypeDescriptor;
import Analysis.OoOJava.ConflictGraph;
import Analysis.OoOJava.ConflictNode;
import Analysis.OoOJava.OoOJavaAnalysis;
import Util.CodePrinter;

/* An instance of this class manages all OoOJava coarse-grained runtime conflicts
 * by generating C-code to either rule out the conflict at runtime or resolve one.
 * 
 * How to Use:
 * 1) Instantiate singleton object (String input is to specify output dir)
 * 2) Call void close() 
 */
public class RuntimeConflictResolver {
  private CodePrinter headerFile, cFile;
  private static final String hashAndQueueCFileDir = "oooJava/";
  
  //This keeps track of taints we've traversed to prevent printing duplicate traverse functions
  //The Integer keeps track of the weakly connected group it's in (used in enumerateHeapRoots)
  //private Hashtable<Taint, Integer> doneTaints;
  private Hashtable<Pair, Integer> idMap=new Hashtable<Pair,Integer>();
  
  //Keeps track of stallsites that we've generated code for. 
  protected Hashtable <FlatNode, TempDescriptor> processedStallSites = new Hashtable <FlatNode, TempDescriptor>();
 
  public int currentID=1;
  private int totalWeakGroups;
  private OoOJavaAnalysis oooa;  
  private State globalState;
  
  // initializing variables can be found in printHeader()
  private static final String allocSiteInC = "allocsite";
  private static final String queryAndAddToVistedHashtable = "hashRCRInsert";
  private static final String enqueueInC = "enqueueRCRQueue(";
  private static final String dequeueFromQueueInC = "dequeueRCRQueue()";
  private static final String clearQueue = "resetRCRQueue()";
  // Make hashtable; hashRCRCreate(unsigned int size, double loadfactor)
  private static final String mallocVisitedHashtable = "hashRCRCreate(128, 0.75)";
  private static final String deallocVisitedHashTable = "hashRCRDelete()";
  private static final String resetVisitedHashTable = "hashRCRreset()";

  public RuntimeConflictResolver( String buildir, 
                                  OoOJavaAnalysis oooa, 
                                  State state) 
  throws FileNotFoundException {
    this.oooa         = oooa;
    this.globalState  = state;

    processedStallSites = new Hashtable <FlatNode, TempDescriptor>();
    BuildStateMachines bsm  = oooa.getBuildStateMachines();
    totalWeakGroups         = bsm.getTotalNumOfWeakGroups();
    
    setupOutputFiles(buildir);

    for( Pair<FlatNode, TempDescriptor> p: bsm.getAllMachineNames() ) {
      FlatNode                taskOrStallSite      =  p.getFirst();
      TempDescriptor          var                  =  p.getSecond();
      StateMachineForEffects  stateMachine         = bsm.getStateMachine( taskOrStallSite, var );

      //prints the traversal code
      printCMethod( taskOrStallSite, var, stateMachine); 
    }
    
    //IMPORTANT must call .close() elsewhere to finish printing the C files.  
  }
  
  /*
   * This method generates a C method for every inset variable and rblock. 
   * 
   * The C method works by generating a large switch statement that will run the appropriate 
   * checking code for each object based on the current state. The switch statement is 
   * surrounded by a while statement which dequeues objects to be checked from a queue. An
   * object is added to a queue only if it contains a conflict (in itself or in its referencees)
   * and we came across it while checking through it's referencer. Because of this property, 
   * conflicts will be signaled by the referencer; the only exception is the inset variable which can 
   * signal a conflict within itself. 
   */
  
  private void printCMethod( FlatNode               taskOrStallSite,
                             TempDescriptor         var,
                             StateMachineForEffects smfe) {

    // collect info for code gen
    FlatSESEEnterNode task          = null;
    String            inVar         = var.getSafeSymbol();
    SMFEState         initialState  = smfe.getInitialState();
    boolean           isStallSite   = !(taskOrStallSite instanceof FlatSESEEnterNode);    
    int               weakID        = smfe.getWeaklyConnectedGroupID(taskOrStallSite);
    
    String blockName;    
    //No need generate code for empty traverser
    if (smfe.isEmpty())
      return;

    if( isStallSite ) {
      blockName = taskOrStallSite.toString();
      processedStallSites.put(taskOrStallSite, var);
    } else {
      task = (FlatSESEEnterNode) taskOrStallSite;
      
      //if the task is the main task, there's no traverser
      if(task.isMainSESE)
        return;
      
      blockName = task.getPrettyIdentifier();
    }


    
    String methodName = "void traverse___" + inVar + removeInvalidChars(blockName) + "___(void * InVar, ";
    int    index      = -1;

    if( isStallSite ) {
      methodName += "SESEstall *record)";
    } else {
      methodName += task.getSESErecordName() +" *record)";
      //TODO check that this HACK is correct (i.e. adding and then polling immediately afterwards)
      task.addInVarForDynamicCoarseConflictResolution(var);
      index = task.getInVarsForDynamicCoarseConflictResolution().indexOf( var );
    }
    
    cFile.println( methodName + " {");
    headerFile.println( methodName + ";" );

    cFile.println(  "  int totalcount = RUNBIAS;");      
    if( isStallSite ) {
      cFile.println("  record->rcrRecords[0].count = RUNBIAS;");
    } else {
      cFile.println("  record->rcrRecords["+index+"].count = RUNBIAS;");
    }

    //clears queue and hashtable that keeps track of where we've been. 
    cFile.println(clearQueue + ";");
    cFile.println(resetVisitedHashTable + ";"); 
    cFile.println("  RCRQueueEntry * queueEntry; //needed for dequeuing");
    
    cFile.println("  int traverserState = "+initialState.getID()+";");

    //generic cast to ___Object___ to access ptr->allocsite field. 
    cFile.println("  struct ___Object___ * ptr = (struct ___Object___ *) InVar;");
    cFile.println("  if (InVar != NULL) {");
    cFile.println("    " + queryAndAddToVistedHashtable + "(ptr, "+initialState.getID()+");");
    cFile.println("    do {");

    if( !isStallSite ) {
      cFile.println("      if(unlikely(record->common.doneExecuting)) {");
      cFile.println("        record->common.rcrstatus=0;");
      cFile.println("        return;");
      cFile.println("      }");
    }

    
    // Traverse the StateMachineForEffects (a graph)
    // that serves as a plan for building the heap examiner code.
    // SWITCH on the states in the state machine, THEN
    //   SWITCH on the concrete object's allocation site THEN
    //     consider conflicts, enqueue more work, inline more SWITCHES, etc.
      
    boolean needswitch=smfe.getStates().size()>1;

    if (needswitch) {
      cFile.println("  switch( traverserState ) {");
    }
    for(SMFEState state: smfe.getStates()) {

      if(state.getRefCount() != 1 || initialState == state) {
	if (needswitch) {
	  cFile.println("    case "+state.getID()+":");
	} else {
	  cFile.println("  if(traverserState=="+state.getID()+") {");
	}
        
        printAllocChecksInsideState("ptr->allocsite", state, taskOrStallSite, var, "ptr", 0, weakID);
        
	cFile.println("      break;");
      }
    }
    
    if (needswitch) {
      cFile.println("        default: break;");
    }
    cFile.println("      } // end switch on traverser state");
    cFile.println("      queueEntry = " + dequeueFromQueueInC + ";");
    cFile.println("      if(queueEntry == NULL) {");
    cFile.println("        break;");
    cFile.println("      }");
    cFile.println("      ptr = queueEntry->object;");
    cFile.println("      traverserState = queueEntry->traverserState;");
    cFile.println("    } while(ptr != NULL);");
    cFile.println("  } // end if inVar not null");
   

    if( isStallSite ) {
      cFile.println("  if(atomic_sub_and_test(totalcount,&(record->rcrRecords[0].count))) {");
      cFile.println("    psem_give_tag(record->common.parentsStallSem, record->tag);");
      cFile.println("    BARRIER();");
      cFile.println("  }");
    } else {
      cFile.println("  if(atomic_sub_and_test(totalcount,&(record->rcrRecords["+index+"].count))) {");
      cFile.println("    int flag=LOCKXCHG32(&(record->rcrRecords["+index+"].flag),0);");
      cFile.println("    if(flag) {");
      //we have resolved a heap root...see if this was the last dependence
      cFile.println("      if(atomic_sub_and_test(1, &(record->common.unresolvedDependencies))) workScheduleSubmit((void *)record);");
      cFile.println("    }");
      cFile.println("  }");
    }

    cFile.println("}");
    cFile.flush();
  }
  
  public void printAllocChecksInsideState(String input, SMFEState state, FlatNode fn, TempDescriptor tmp, String prefix, int depth, int weakID) {
    EffectsTable et = new EffectsTable(state);
    boolean needswitch=et.getAllAllocs().size()>1;
    if (needswitch) {
      cFile.println("      switch(" + input + ") {");
    }

    //we assume that all allocs given in the effects are starting locs. 
    for(Alloc a: et.getAllAllocs()) {
      if (needswitch) {
	cFile.println("    case "+a.getUniqueAllocSiteID()+": {");
      } else {
	cFile.println("     if("+input+"=="+a.getUniqueAllocSiteID()+") {");
      }
      addChecker(a, fn, tmp, state, et, "ptr", 0, weakID);
      if (needswitch) {
	cFile.println("       }");
	cFile.println("       break;");
      }
    }
    if (needswitch) {
      cFile.println("      default:");
      cFile.println("        break;");
    }
    cFile.println("      }");
  }
  
  public void addChecker(Alloc a, FlatNode fn, TempDescriptor tmp, SMFEState state, EffectsTable et, String prefix, int depth, int weakID) {
    insertEntriesIntoHashStructureNew(fn, tmp, et, a, prefix, depth, weakID);
    
    int pdepth = depth+1;
    
    if(a.getType().isArray()) {
      String childPtr = "((struct ___Object___ **)(((char *) &(((struct ArrayObject *)"+ prefix+")->___length___))+sizeof(int)))[i]";
      String currPtr = "arrayElement" + pdepth;
      
      cFile.println("  int i;");
      cFile.println("  struct ___Object___ * "+currPtr+";");
      cFile.println("  for(i = 0; i<((struct ArrayObject *) " + prefix + " )->___length___; i++ ) {");
      
      for(Effect e: et.getEffects(a)) {
	if (state.transitionsTo(e).isEmpty()) {
	  printRefSwitch(fn, tmp, pdepth, childPtr, currPtr, state.transitionsTo(e), weakID);
	}
      }
      cFile.println("}");
    }  else {
      //All other cases
      String currPtr = "myPtr" + pdepth;
      cFile.println("    struct ___Object___ * "+currPtr+";");
      
      for(Effect e: et.getEffects(a)) {
	if (!state.transitionsTo(e).isEmpty()) {
	  String childPtr = "((struct "+a.getType().getSafeSymbol()+" *)"+prefix +")->" + e.getField().getSafeSymbol();
	  printRefSwitch(fn, tmp, pdepth, childPtr, currPtr, state.transitionsTo(e), weakID);
	}
      }
    }
  }

  private void printRefSwitch(FlatNode fn, TempDescriptor tmp, int pdepth, String childPtr, String currPtr, Set<SMFEState> transitions, int weakID) {    
    
    for(SMFEState tr: transitions) {
      if(tr.getRefCount() == 1) {       //in-lineable case
	//Don't need to update state counter since we don't care really if it's inlined...
	cFile.println("    "+currPtr+"= (struct ___Object___ * ) " + childPtr + ";");
	cFile.println("    if (" + currPtr + " != NULL) { ");
	
	printAllocChecksInsideState(currPtr+"->"+allocSiteInC, tr, fn, tmp, currPtr, pdepth+1, weakID);
        
	cFile.println("    }"); //break for internal switch and if
      } else {                          //non-inlineable cases
	cFile.println("    " + enqueueInC + childPtr + ", "+tr.getID()+");");
      } 
    }
  }
  
  
  //FlatNode and TempDescriptor are what are used to make the taint
  private void insertEntriesIntoHashStructureNew(FlatNode fn, TempDescriptor tmp, EffectsTable et, Alloc a, String prefix, int depth, int weakID) {
    int index = 0;
    boolean isRblock = (fn instanceof FlatSESEEnterNode);
    if (isRblock) {
      FlatSESEEnterNode fsese = (FlatSESEEnterNode) fn;
      index = fsese.getInVarsForDynamicCoarseConflictResolution().indexOf(tmp);
    }
    
    String strrcr = isRblock ? "&record->rcrRecords[" + index + "], " : "NULL, ";
    String tasksrc =isRblock ? "(SESEcommon *) record, ":"(SESEcommon *)(((INTPTR)record)|1LL), ";

    if(et.hasWriteConflict(a)) {
      cFile.append("    int tmpkey" + depth + " = rcr_generateKey(" + prefix + ");\n");
      if (et.leadsToConflict(a))
        cFile.append("    int tmpvar" + depth + " = rcr_WTWRITEBINCASE(allHashStructures[" + weakID + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
      else
        cFile.append("    int tmpvar" + depth + " = rcr_WRITEBINCASE(allHashStructures["+ weakID + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
    } else  if(et.hasReadConflict(a)) { 
      cFile.append("    int tmpkey" + depth + " = rcr_generateKey(" + prefix + ");\n");
      if (et.leadsToConflict(a))
        cFile.append("    int tmpvar" + depth + " = rcr_WTREADBINCASE(allHashStructures[" + weakID + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
      else
        cFile.append("    int tmpvar" + depth + " = rcr_READBINCASE(allHashStructures["+ weakID + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
    }

    if (et.hasReadConflict(a) || et.hasWriteConflict(a)) {
      cFile.append("if (!(tmpvar" + depth + "&READYMASK)) totalcount--;\n");
    }
  }

  private void setupOutputFiles(String buildir) throws FileNotFoundException {
    cFile = new CodePrinter(new File(buildir + "RuntimeConflictResolver" + ".c"));
    headerFile = new CodePrinter(new File(buildir + "RuntimeConflictResolver" + ".h"));
    
    cFile.println("#include \"" + hashAndQueueCFileDir + "hashRCR.h\"\n#include \""
        + hashAndQueueCFileDir + "Queue_RCR.h\"\n#include <stdlib.h>");
    cFile.println("#include \"classdefs.h\"");
    cFile.println("#include \"structdefs.h\"");
    cFile.println("#include \"mlp_runtime.h\"");
    cFile.println("#include \"RuntimeConflictResolver.h\"");
    cFile.println("#include \"hashStructure.h\"");
    
    headerFile.println("#ifndef __3_RCR_H_");
    headerFile.println("#define __3_RCR_H_");
  }
  
  //The official way to generate the name for a traverser call
  public String getTraverserInvocation(TempDescriptor invar, String varString, FlatNode fn) {
    String flatname;
    if(fn instanceof FlatSESEEnterNode) {  //is SESE block
      flatname = ((FlatSESEEnterNode) fn).getPrettyIdentifier();
    } else {  //is stallsite
      flatname = fn.toString();
    }
    
    return "traverse___" + invar.getSafeSymbol() + removeInvalidChars(flatname) + "___("+varString+");";
  }
  
  public String removeInvalidChars(String in) {
    StringBuilder s = new StringBuilder(in);
    for(int i = 0; i < s.length(); i++) {
      if(s.charAt(i) == ' ' || 
         s.charAt(i) == '.' || 
         s.charAt(i) == '=' ||
         s.charAt(i) == '[' ||
         s.charAt(i) == ']'    ) {

        s.deleteCharAt(i);
        i--;
      }
    }
    return s.toString();
  }

  public int getTraverserID(TempDescriptor invar, FlatNode fn) {
    Pair<TempDescriptor, FlatNode> t = new Pair<TempDescriptor, FlatNode>(invar, fn);
    if (idMap.containsKey(t)) {
      return idMap.get(t).intValue();
    }
    int value=currentID++;
    idMap.put(t, new Integer(value));
    return value;
  }
  
  public void close() {
    //Prints out the master traverser Invocation that'll call all other traversers
    //based on traverserID
    printMasterTraverserInvocation();    
    createMasterHashTableArray();
    
    // Adds Extra supporting methods
    cFile.println("void initializeStructsRCR() {\n  " + mallocVisitedHashtable + ";\n  " + clearQueue + ";\n}");
    cFile.println("void destroyRCR() {\n  " + deallocVisitedHashTable + ";\n}");
    
    headerFile.println("void initializeStructsRCR();\nvoid destroyRCR();");
    headerFile.println("#endif\n");

    cFile.close();
    headerFile.close();
  }

  private void printMasterTraverserInvocation() {
    headerFile.println("int tasktraverse(SESEcommon * record);");
    cFile.println("int tasktraverse(SESEcommon * record) {");
    cFile.println("  if(!CAS(&record->rcrstatus,1,2)) {");

    //release traverser reference...no traversal necessary
    cFile.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
    cFile.println("    RELEASE_REFERENCE_TO(record);");
    cFile.println("#endif");

    cFile.println("    return;");
    cFile.println("  }");
    cFile.println("  switch(record->classID) {");
    
    for(Iterator<FlatSESEEnterNode> seseit=oooa.getAllSESEs().iterator();seseit.hasNext();) {
      FlatSESEEnterNode fsen=seseit.next();
      cFile.println(    "    /* "+fsen.getPrettyIdentifier()+" */");
      cFile.println(    "    case "+fsen.getIdentifier()+": {");
      cFile.println(    "      "+fsen.getSESErecordName()+" * rec=("+fsen.getSESErecordName()+" *) record;");
      Vector<TempDescriptor> invars=fsen.getInVarsForDynamicCoarseConflictResolution();
      for(int i=0;i<invars.size();i++) {
        TempDescriptor tmp=invars.get(i);
        
	/* In some cases we don't want to a dynamic traversal if it is
	 * unlikely to increase parallelism...these are cases where we
	 * are just enabling a stall site to possible clear faster*/

	boolean isValidToPrune=true;
	for( FlatSESEEnterNode parentSESE: fsen.getParents() ) {
	  ConflictGraph     graph      = oooa.getConflictGraph(parentSESE);
          String            id         = tmp + "_sese" + fsen.getPrettyIdentifier();
	  ConflictNode      node       = graph.getId2cn().get(id);
	  isValidToPrune &= node.IsValidToPrune();
	}
	if (i!=0) {
	  cFile.println("      if (record->rcrstatus!=0)");
	}
	
	if(globalState.NOSTALLTR && isValidToPrune) {
	  cFile.println("    /*  " + getTraverserInvocation(tmp, "rec->"+tmp+", rec", fsen)+"*/");
	} else {
	  cFile.println("      " + getTraverserInvocation(tmp, "rec->"+tmp+", rec", fsen));
	}
      }
      //release traverser reference...traversal finished...
      //executing thread will clean bins for us
      cFile.println("     record->rcrstatus=0;");
      cFile.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
      cFile.println("    RELEASE_REFERENCE_TO(record);");
      cFile.println("#endif");
      cFile.println(    "    }");
      cFile.println(    "    break;");
    }
    
    for(FlatNode stallsite: processedStallSites.keySet()) {
      TempDescriptor var = processedStallSites.get(stallsite);
      
      cFile.println(    "    case -" + getTraverserID(var, stallsite)+ ": {");
      cFile.println(    "      SESEstall * rec=(SESEstall*) record;");
      cFile.println(    "      " + getTraverserInvocation(var, "rec->___obj___, rec", stallsite)+";");
      cFile.println(    "     record->rcrstatus=0;");
      cFile.println(    "    }");
      cFile.println("    break;");
    }

    cFile.println("    default:");
    cFile.println("      printf(\"Invalid SESE ID was passed in: %d.\\n\",record->classID);");
    cFile.println("      break;");
    cFile.println("  }");
    cFile.println("}");
  }
  
  private void createMasterHashTableArray() {
    headerFile.println("struct Hashtable_rcr ** createAndFillMasterHashStructureArray();");
    cFile.println("struct Hashtable_rcr ** createAndFillMasterHashStructureArray() {");

    cFile.println("  struct Hashtable_rcr **table=rcr_createMasterHashTableArray("+totalWeakGroups + ");");
    
    for(int i = 0; i < totalWeakGroups; i++) {
      cFile.println("  table["+i+"] = (struct Hashtable_rcr *) rcr_createHashtable();");
    }
    cFile.println("  return table;");
    cFile.println("}");
  }

  public int getWeakID(TempDescriptor invar, FlatNode fn) {
    //return weakMap.get(new Pair(invar, fn)).intValue();
    return 0;
  }


  public boolean hasEmptyTraversers(FlatSESEEnterNode fsen) {
    boolean hasEmpty = true;
    
    Set<FlatSESEEnterNode> children = fsen.getChildren();
    for (Iterator<FlatSESEEnterNode> iterator = children.iterator(); iterator.hasNext();) {
      FlatSESEEnterNode child = (FlatSESEEnterNode) iterator.next();
      hasEmpty &= child.getInVarsForDynamicCoarseConflictResolution().size() == 0;
    }
    return hasEmpty;
  }  

  
  //Simply rehashes and combines all effects for a AffectedAllocSite + Field.
  private class EffectsTable {
    private Hashtable<Alloc,Set<Effect>> table;
    SMFEState state;

    public EffectsTable(SMFEState state) {
      table = new Hashtable<Alloc, Set<Effect>>();
      this.state=state;
      for(Effect e: state.getEffectsAllowed()) {
	Set<Effect> eg;
        if((eg = table.get(e.getAffectedAllocSite())) == null) {
          eg = new HashSet<Effect>();
          table.put(e.getAffectedAllocSite(), eg);
        }
        eg.add(e);
      }
    }
    
    public boolean leadsToConflict(Alloc a) {
      for(Effect e:getEffects(a)) {
	if (!state.transitionsTo(e).isEmpty())
	  return true;
      }
      return false;
    }

    public boolean hasWriteConflict(Alloc a) {
      for(Effect e:getEffects(a)) {
	if (e.isWrite() && state.getConflicts().contains(e))
	  return true;
      }
      return false;
    }

    public boolean hasReadConflict(Alloc a) {
      for(Effect e:getEffects(a)) {
	if (e.isRead() && state.getConflicts().contains(e))
	  return true;
      }
      return false;
    }

    public Set<Effect> getEffects(Alloc a) {
      return table.get(a);
    }

    public Set<Alloc> getAllAllocs() {
      return table.keySet();
    }
  }
}
