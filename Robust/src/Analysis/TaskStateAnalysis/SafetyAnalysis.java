package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.io.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import Util.Edge;

public class SafetyAnalysis {
  private Hashtable executiongraph;
  private Hashtable<ClassDescriptor, Hashtable<FlagState, Set<OptionalTaskDescriptor>>> safeexecution;   //to use to build code
  private State state;
  private TaskAnalysis taskanalysis;
  private Hashtable<ClassDescriptor, Hashtable<OptionalTaskDescriptor, OptionalTaskDescriptor>> optionaltaskdescriptors;
  private Hashtable<FlagState, Hashtable<TaskIndex, Set<OptionalTaskDescriptor>>> fstotimap;

  private ClassDescriptor processedclass;

  public Hashtable<ClassDescriptor, Hashtable<FlagState, Set<OptionalTaskDescriptor>>> getResult() {
    return safeexecution;
  }

  public Hashtable<ClassDescriptor, Hashtable<OptionalTaskDescriptor, OptionalTaskDescriptor>> getOptionalTaskDescriptors() {
    return optionaltaskdescriptors;
  }

  /* Structure that stores a possible optional task which would be
     safe to execute and the possible flagstates the object could be
     in before executing the task during an execution without
     failure*/

  /*Constructor*/
  public SafetyAnalysis(Hashtable executiongraph, State state, TaskAnalysis taskanalysis) {
    this.executiongraph = executiongraph;
    this.safeexecution = new Hashtable();
    this.state = state;
    this.taskanalysis = taskanalysis;
    this.optionaltaskdescriptors = new Hashtable();
    this.fstotimap=new Hashtable<FlagState, Hashtable<TaskIndex, Set<OptionalTaskDescriptor>>>();
  }

  /* Builds map of fs -> EGTasknodes that can fire on fs for class cd */

  private Hashtable<FlagState, Set<EGTaskNode>> buildMap(ClassDescriptor cd) {
    Hashtable<FlagState, Set<EGTaskNode>> table=new Hashtable<FlagState, Set<EGTaskNode>>();
    for(Iterator it=((Set)executiongraph.get(cd)).iterator(); it.hasNext();) {
      EGTaskNode node=(EGTaskNode)it.next();
      if (node.getFS()!=null) {
	if (!table.containsKey(node.getFS()))
	  table.put(node.getFS(), new HashSet<EGTaskNode>());
	table.get(node.getFS()).add(node);
      }
    }
    return table;
  }


  public Set<OptionalTaskDescriptor> getOptions(FlagState fs, TaskDescriptor td, int index) {
    return fstotimap.get(fs).get(new TaskIndex(td, index));
  }

  public Set<OptionalTaskDescriptor> getOptions(FlagState fs, TaskIndex ti) {
    return fstotimap.get(fs).get(ti);
  }

  public Set<TaskIndex> getTaskIndex(FlagState fs) {
    return fstotimap.get(fs).keySet();
  }


  /* Builds map of fs -> set of fs that depend on this fs */

  private Hashtable<FlagState, Set<FlagState>> buildUseMap(ClassDescriptor cd) {
    Hashtable<FlagState, Set<FlagState>> table=new Hashtable<FlagState, Set<FlagState>>();
    for(Iterator it=((Set)executiongraph.get(cd)).iterator(); it.hasNext();) {
      EGTaskNode node=(EGTaskNode)it.next();
      if (node.getFS()!=null) {
	if (!table.containsKey(node.getPostFS()))
	  table.put(node.getPostFS(), new HashSet<FlagState>());
	table.get(node.getPostFS()).add(node.getFS());
      }
    }
    return table;
  }

  public void doAnalysis() {
    Enumeration classit=taskanalysis.flagstates.keys();

    while (classit.hasMoreElements()) {
      ClassDescriptor cd=(ClassDescriptor)classit.nextElement();
      if (!executiongraph.containsKey(cd))
	continue;
      Hashtable<FlagState, Set<OptionalTaskDescriptor>> fstootd=new Hashtable<FlagState, Set<OptionalTaskDescriptor>>();
      safeexecution.put(cd, fstootd);

      optionaltaskdescriptors.put(cd, new Hashtable<OptionalTaskDescriptor, OptionalTaskDescriptor>());

      Hashtable<FlagState, Set<EGTaskNode>> fstoegmap=buildMap(cd);
      Hashtable<FlagState, Set<FlagState>> fsusemap=buildUseMap(cd);

      HashSet<FlagState> tovisit=new HashSet<FlagState>();
      tovisit.addAll(taskanalysis.getFlagStates(cd));

      while(!tovisit.isEmpty()) {
	FlagState fs=tovisit.iterator().next();
	tovisit.remove(fs);
	if (!fstoegmap.containsKey(fs))
	  continue;          //This FS has no task that can trigger on it
	Set<EGTaskNode> nodeset=fstoegmap.get(fs);
	analyzeFS(fs, nodeset, fstootd, fsusemap, tovisit);
      }
    }
    printTEST();
  }

  public void analyzeFS(FlagState fs, Set<EGTaskNode> egset, Hashtable<FlagState, Set<OptionalTaskDescriptor>> fstootd, Hashtable<FlagState, Set<FlagState>> fsusemap, HashSet<FlagState> tovisit) {
    Hashtable<TaskIndex, Set<OptionalTaskDescriptor>>  timap=new Hashtable<TaskIndex, Set<OptionalTaskDescriptor>>();
    Set<TaskIndex> tiselfloops=new HashSet<TaskIndex>();

    for(Iterator<EGTaskNode> egit=egset.iterator(); egit.hasNext();) {
      EGTaskNode egnode=egit.next();
      Set<OptionalTaskDescriptor> setotd;
      if (egnode.isOptional()) {
	setotd=new HashSet<OptionalTaskDescriptor>();
	HashSet<FlagState> enterfsset=new HashSet<FlagState>();
	enterfsset.add(fs);
	ClassDescriptor cd=fs.getClassDescriptor();
	OptionalTaskDescriptor newotd=new OptionalTaskDescriptor(egnode.getTD(), egnode.getIndex(), enterfsset, new Predicate());
	if(optionaltaskdescriptors.get(cd).containsKey(newotd)) {
	  newotd = optionaltaskdescriptors.get(cd).get(newotd);
	} else {
	  newotd.setuid();
	  resultingFS(newotd);
	  optionaltaskdescriptors.get(cd).put(newotd, newotd);
	}
	setotd.add(newotd);
      } else if (tagChange(egnode)) {
	//Conservatively handle tag changes
	setotd=new HashSet<OptionalTaskDescriptor>();
      } else if(egnode.isMultipleParams()) {
	if( goodMultiple(egnode)){
	  Predicate p=returnPredicate(egnode);
	  Set<OptionalTaskDescriptor> oldsetotd;
	  if (fstootd.containsKey(egnode.getPostFS()))
	    oldsetotd=fstootd.get(egnode.getPostFS());
	  else
	    oldsetotd=new HashSet<OptionalTaskDescriptor>();
	  setotd=new HashSet<OptionalTaskDescriptor>();
	  for(Iterator<OptionalTaskDescriptor> otdit=oldsetotd.iterator(); otdit.hasNext();) {
	    OptionalTaskDescriptor oldotd=otdit.next();
	    Predicate newp=combinePredicates(oldotd.predicate, p);
	    OptionalTaskDescriptor newotd=new OptionalTaskDescriptor(oldotd.td, oldotd.getIndex(), oldotd.enterflagstates, newp);
	    ClassDescriptor cd=fs.getClassDescriptor();
	    if(optionaltaskdescriptors.get(cd).containsKey(newotd)) {
	      newotd = optionaltaskdescriptors.get(cd).get(newotd);
	    } else {
	      newotd.setuid();
	      resultingFS(newotd);
	      optionaltaskdescriptors.get(cd).put(newotd, newotd);
	    }
	    setotd.add(newotd);
	  }
	} else {
	  //Can't propagate anything
	  setotd=new HashSet<OptionalTaskDescriptor>();
	}
      } else {
	if (fstootd.containsKey(egnode.getPostFS()))
	  setotd=fstootd.get(egnode.getPostFS());
	else
	  setotd=new HashSet<OptionalTaskDescriptor>();
      }
      TaskIndex ti=egnode.isRuntime() ? new TaskIndex() : new TaskIndex(egnode.getTD(), egnode.getIndex());
      if (!ti.runtime) {
	//runtime edges don't do anything...don't have to take
	//them, can't predict when we can.
	if (state.selfloops.contains(egnode.getTD().getSymbol())) {
	  System.out.println("Self loop for: "+egnode.getTD()+" "+egnode.getIndex());
	  if (timap.containsKey(ti)) {
	    if (egnode.getPostFS()!=fs) {
	      if (tiselfloops.contains(ti)) {
		//dump old self loop
		timap.put(ti, setotd);
		tiselfloops.remove(ti);
	      } else {
		//standard and case
		timap.put(ti, createIntersection(timap.get(ti), setotd, fs.getClassDescriptor()));
	      }
	    }
	  } else {
	    //mark as self loop
	    timap.put(ti, setotd);
	    if (egnode.getPostFS()==fs) {
	      tiselfloops.add(ti);
	    }
	  }
	} else if (timap.containsKey(ti)) {
	  //AND case
	  timap.put(ti, createIntersection(timap.get(ti), setotd, fs.getClassDescriptor()));
	} else {
	  timap.put(ti, setotd);
	}
      }
    }

    //Combine all options
    HashSet<OptionalTaskDescriptor> set=new HashSet<OptionalTaskDescriptor>();
    for(Iterator<Set<OptionalTaskDescriptor>> it=timap.values().iterator(); it.hasNext();) {
      Set<OptionalTaskDescriptor> otdset=it.next();
      set.addAll(otdset);
    }

    if (!fstootd.containsKey(fs)||
        !fstootd.get(fs).equals(set)) {
      fstootd.put(fs, set);
      //Requeue all flagstates that may use our updated results
      if (fsusemap.containsKey(fs)) {
	tovisit.addAll(fsusemap.get(fs));
      }
    }
    fstotimap.put(fs, timap);
  }

  private HashSet createIntersection(Set A, Set B, ClassDescriptor cd) {
    HashSet result = new HashSet();
    for(Iterator b_it = B.iterator(); b_it.hasNext();){
      OptionalTaskDescriptor otd_b = (OptionalTaskDescriptor)b_it.next();
      for(Iterator a_it = A.iterator(); a_it.hasNext();){
	OptionalTaskDescriptor otd_a = (OptionalTaskDescriptor)a_it.next();
	if(otd_a.td==otd_b.td&&
	   otd_a.getIndex()==otd_b.getIndex()) {
	  HashSet newfs = new HashSet();
	  newfs.addAll(otd_a.enterflagstates);
	  newfs.addAll(otd_b.enterflagstates);
	  OptionalTaskDescriptor newotd = new OptionalTaskDescriptor(otd_b.td, otd_b.getIndex(), newfs, combinePredicates(otd_a.predicate, otd_b.predicate));
	  if(optionaltaskdescriptors.get(cd).get(newotd)!=null){
	    newotd = optionaltaskdescriptors.get(cd).get(newotd);
	  } else {
	    newotd.setuid();
	    resultingFS(newotd);
	    optionaltaskdescriptors.get(cd).put(newotd, newotd);
	  }
	  result.add(newotd);
	}
      }
    }
    return result;
  }

  // This method returns true if the only parameter whose flag is
  // modified is the tracked one

  private boolean goodMultiple(EGTaskNode tn) {
    TaskDescriptor td = tn.getTD();
    FlatMethod fm = state.getMethodFlat(td);
    TempDescriptor tmp=fm.getParameter(tn.getIndex());

    Set<FlatNode> nodeset=fm.getNodeSet();

    for(Iterator<FlatNode> nodeit=nodeset.iterator(); nodeit.hasNext();) {
      FlatNode fn=nodeit.next();
      if (fn.kind()==FKind.FlatFlagActionNode) {
	FlatFlagActionNode ffan=(FlatFlagActionNode)fn;
	if (ffan.getTaskType() == FlatFlagActionNode.TASKEXIT) {
	  for(Iterator it_tfp=ffan.getTempFlagPairs(); it_tfp.hasNext();) {
	    TempFlagPair tfp=(TempFlagPair)it_tfp.next();
	    TempDescriptor tempd = tfp.getTemp();
	    if(tempd!=tmp)
	      return false;               //return false if a taskexit modifies one of the other parameters
	  }
	}
      }
    }
    return true;
  }

  private Predicate returnPredicate(EGTaskNode tn) {
    Predicate result = new Predicate();
    TaskDescriptor td = tn.getTD();
    for(int i=0; i<td.numParameters(); i++) {
      if(i!=tn.getIndex()) {
	VarDescriptor vd = td.getParameter(i);
	result.vardescriptors.add(vd);
	HashSet<FlagExpressionNode> flaglist = new HashSet<FlagExpressionNode>();
	flaglist.add(td.getFlag(vd));
	result.flags.put(vd, flaglist);
	if (td.getTag(vd)!=null)
	  result.tags.put(vd, td.getTag(vd));
      }
    }
    return result;
  }

  private Predicate combinePredicates(Predicate A, Predicate B) {
    Predicate result = new Predicate();
    result.vardescriptors.addAll(A.vardescriptors);
    result.flags.putAll(A.flags);
    result.tags.putAll(A.tags);
    Collection c = B.vardescriptors;
    for(Iterator varit = c.iterator(); varit.hasNext();){     //maybe change that
      VarDescriptor vd = (VarDescriptor)varit.next();
      if(result.vardescriptors.contains(vd))
	System.out.println("Already in ");
      else {
	result.vardescriptors.add(vd);
      }
    }
    Collection vardesc = result.vardescriptors;
    for(Iterator varit = vardesc.iterator(); varit.hasNext();){
      VarDescriptor vd = (VarDescriptor)varit.next();
      HashSet bflags = B.flags.get(vd);
      if( bflags == null ){
	continue;
      } else {
	if (result.flags.containsKey(vd))
	  ((HashSet)result.flags.get(vd)).addAll(bflags);
	else
	  result.flags.put(vd, bflags);
      }
      TagExpressionList btags = B.tags.get(vd);
      if( btags != null ){
	if (result.tags.containsKey(vd))
	  System.out.println("Tag found but there should be nothing to do because same tag");
	else
	  result.tags.put(vd, btags);
      }
    }
    return result;
  }

  ////////////////////
  /* returns a set of the possible sets of flagstates
     resulting from the execution of the optional task.
     To do it with have to look for TaskExit FlatNodes
     in the IR.
   */
  private void resultingFS(OptionalTaskDescriptor otd) {
    Stack stack = new Stack();
    HashSet result = new HashSet();
    FlatMethod fm = state.getMethodFlat((TaskDescriptor)otd.td);
    FlatNode fn = (FlatNode)fm;

    Stack nodestack=new Stack();
    HashSet discovered=new HashSet();
    nodestack.push(fm);
    discovered.add(fm);
    TempDescriptor temp=fm.getParameter(otd.getIndex());

    //Iterating through the nodes
    while(!nodestack.isEmpty()) {
      FlatNode fn1 = (FlatNode) nodestack.pop();
      if (fn1.kind()==FKind.FlatFlagActionNode) {
	FlatFlagActionNode ffan=(FlatFlagActionNode)fn1;
	if (ffan.getTaskType() == FlatFlagActionNode.TASKEXIT) {
	  HashSet tempset = new HashSet();
	  for(Iterator it_fs = otd.enterflagstates.iterator(); it_fs.hasNext();){
	    FlagState fstemp = (FlagState)it_fs.next();
	    Vector<FlagState> processed=new Vector<FlagState>();

	    for(Iterator it_tfp=ffan.getTempFlagPairs(); it_tfp.hasNext();) {
	      TempFlagPair tfp=(TempFlagPair)it_tfp.next();
	      if (tfp.getTemp()==temp)
		fstemp=fstemp.setFlag(tfp.getFlag(),ffan.getFlagChange(tfp));
	    }

	    processed.add(fstemp);
	    //Process clears first

	    for(Iterator it_ttp=ffan.getTempTagPairs(); it_ttp.hasNext();) {
	      TempTagPair ttp=(TempTagPair)it_ttp.next();

	      if (temp==ttp.getTemp()) {
		Vector<FlagState> oldprocess=processed;
		processed=new Vector<FlagState>();

		for (Enumeration en=oldprocess.elements(); en.hasMoreElements();){
		  FlagState fsworking=(FlagState)en.nextElement();
		  if (!ffan.getTagChange(ttp)){
		    processed.addAll(Arrays.asList(fsworking.clearTag(ttp.getTag())));
		  } else processed.add(fsworking);
		}
	      }
	    }
	    //Process sets next
	    for(Iterator it_ttp=ffan.getTempTagPairs(); it_ttp.hasNext();) {
	      TempTagPair ttp=(TempTagPair)it_ttp.next();

	      if (temp==ttp.getTemp()) {
		Vector<FlagState> oldprocess=processed;
		processed=new Vector<FlagState>();

		for (Enumeration en=oldprocess.elements(); en.hasMoreElements();){
		  FlagState fsworking=(FlagState)en.nextElement();
		  if (ffan.getTagChange(ttp)){
		    processed.addAll(Arrays.asList(fsworking.setTag(ttp.getTag())));
		  } else processed.add(fsworking);
		}
	      }
	    }
	    //Add to exit states
	    tempset.addAll(processed);
	  }
	  result.add(tempset);
	  continue;           // avoid queueing the return node if reachable
	}
      } else if (fn1.kind()==FKind.FlatReturnNode) {
	result.add(otd.enterflagstates);
      }

      /* Queue other nodes past this one */
      for(int i=0; i<fn1.numNext(); i++) {
	FlatNode fnext=fn1.getNext(i);
	if (!discovered.contains(fnext)) {
	  discovered.add(fnext);
	  nodestack.push(fnext);
	}
      }
    }
    otd.exitfses=result;
  }

  private void printTEST() {
    Enumeration e = safeexecution.keys();
    while (e.hasMoreElements()) {
      ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
      System.out.println("\nTesting class : "+cdtemp.getSymbol()+"\n");
      Hashtable hashtbtemp = safeexecution.get(cdtemp);
      Enumeration fses = hashtbtemp.keys();
      while(fses.hasMoreElements()){
	FlagState fs = (FlagState)fses.nextElement();
	System.out.println("\t"+fs.getTextLabel()+"\n\tSafe tasks to execute :\n");
	HashSet availabletasks = (HashSet)hashtbtemp.get(fs);
	for(Iterator otd_it = availabletasks.iterator(); otd_it.hasNext();){
	  OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
	  System.out.println("\t\tTASK "+otd.td.getSymbol()+" UID : "+otd.getuid()+"\n");
	  System.out.println("\t\twith flags :");
	  for(Iterator myfses = otd.enterflagstates.iterator(); myfses.hasNext();){
	    System.out.println("\t\t\t"+((FlagState)myfses.next()).getTextLabel());
	  }
	  System.out.println("\t\tand exitflags :");
	  for(Iterator fseshash = otd.exitfses.iterator(); fseshash.hasNext();){
	    HashSet temphs = (HashSet)fseshash.next();
	    System.out.println("");
	    for(Iterator exfses = temphs.iterator(); exfses.hasNext();){
	      System.out.println("\t\t\t"+((FlagState)exfses.next()).getTextLabel());
	    }
	  }
	  Predicate predicate = otd.predicate;
	  System.out.println("\t\tPredicate constraints :");
	  Collection c = predicate.vardescriptors;
	  for(Iterator varit = c.iterator(); varit.hasNext();){
	    VarDescriptor vard = (VarDescriptor)varit.next();
	    System.out.println("\t\t\tClass "+vard.getType().getClassDesc().getSymbol());
	  }
	  System.out.println("\t\t------------");
	}
      }

      System.out.println("\n\n\n\tOptionaltaskdescriptors contains : ");
      Collection c_otd = optionaltaskdescriptors.get(cdtemp).values();
      for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext();){
	OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
	System.out.println("\t\tTASK "+otd.td.getSymbol()+" UID : "+otd.getuid()+"\n");
	System.out.println("\t\twith flags :");
	for(Iterator myfses = otd.enterflagstates.iterator(); myfses.hasNext();){
	  System.out.println("\t\t\t"+((FlagState)myfses.next()).getTextLabel());
	}
	System.out.println("\t\tand exitflags :");
	for(Iterator fseshash = otd.exitfses.iterator(); fseshash.hasNext();){
	  HashSet temphs = (HashSet)fseshash.next();
	  System.out.println("");
	  for(Iterator exfses = temphs.iterator(); exfses.hasNext();){
	    System.out.println("\t\t\t"+((FlagState)exfses.next()).getTextLabel());
	  }
	}
	Predicate predicate = otd.predicate;
	System.out.println("\t\tPredicate contains :");
	Collection c = predicate.vardescriptors;
	for(Iterator varit = c.iterator(); varit.hasNext();){
	  VarDescriptor vard = (VarDescriptor)varit.next();
	  System.out.println("\t\t\tClass "+vard.getType().getClassDesc().getSymbol());
	  HashSet temphash = predicate.flags.get(vard.getName());
	  if(temphash == null) System.out.println("null hashset");
	  else System.out.println("\t\t\t"+temphash.size()+" flag(s)");

	}
	System.out.println("\t\t------------");
      }
    }
  }

  /*check if there has been a tag Change*/
  private boolean tagChange(EGTaskNode tn) {
    if(tn.getTD() == null) return false;    //to be fixed
    FlatMethod fm = state.getMethodFlat(tn.getTD());
    FlatNode fn = (FlatNode)fm;

    Stack nodestack=new Stack();
    HashSet discovered=new HashSet();
    nodestack.push(fm);
    discovered.add(fm);

    //Iterating through the nodes
    while(!nodestack.isEmpty()) {
      FlatNode fn1 = (FlatNode) nodestack.pop();
      if (fn1.kind()==FKind.FlatFlagActionNode) {
	FlatFlagActionNode ffan=(FlatFlagActionNode)fn1;
	if (ffan.getTaskType() == FlatFlagActionNode.TASKEXIT) {
	  Iterator it_ttp=ffan.getTempTagPairs();
	  if(it_ttp.hasNext()){
	    System.out.println("Tag change detected in Task "+tn.getName());
	    return true;
	  } else continue;         // avoid queueing the return node if reachable
	}
      }

      /* Queue other nodes past this one */
      for(int i=0; i<fn1.numNext(); i++) {
	FlatNode fnext=fn1.getNext(i);
	if (!discovered.contains(fnext)) {
	  discovered.add(fnext);
	  nodestack.push(fnext);
	}
      }
    }
    return false;
  }

  /////////DEBUG
  /*Thoose two tasks create the dot file named markedgraph.dot */

  private void createDOTFile(String classname, Collection v) throws java.io.IOException {
    java.io.PrintWriter output;
    File dotfile_flagstates= new File("markedgraph_"+classname+".dot");
    FileOutputStream dotstream=new FileOutputStream(dotfile_flagstates,false);
    output = new java.io.PrintWriter(dotstream, true);
    output.println("digraph dotvisitor {");
    output.println("\tnode [fontsize=10,height=\"0.1\", width=\"0.1\"];");
    output.println("\tedge [fontsize=6];");
    traverse(output, v);
    output.println("}\n");
  }

  private void traverse(java.io.PrintWriter output, Collection v) {
    EGTaskNode tn;

    for(Iterator it1 = v.iterator(); it1.hasNext();){
      tn = (EGTaskNode)it1.next();
      output.println("\t"+tn.getLabel()+" [label=\""+tn.getTextLabel()+"\"");
      if (tn.isOptional()){
	if (tn.isMultipleParams())
	  output.println(", shape = tripleoctagon");
	else
	  output.println(", shape=doubleoctagon");
      } else if (tn.isMultipleParams())
	output.println(", shape=octagon");
      output.println("];");

      for(Iterator it2 = tn.edges(); it2.hasNext();){
	EGTaskNode tn2 = (EGTaskNode)((Edge)it2.next()).getTarget();
	output.println("\t"+tn.getLabel()+" -> "+tn2.getLabel()+";");
      }
    }
  }
}
