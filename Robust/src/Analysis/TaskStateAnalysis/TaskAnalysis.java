package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;

public class TaskAnalysis {
  State state;
  Hashtable flagstates;
  Hashtable flags;
  Hashtable extern_flags;
  Queue<FlagState> toprocess;
  TagAnalysis taganalysis;
  Hashtable cdtorootnodes;
  Hashtable tdToFEdges;

  TypeUtil typeutil;

  /**
   * Class Constructor
   *
   * @param state a flattened State object
   * @see State
   */
  public TaskAnalysis(State state, TagAnalysis taganalysis, TypeUtil typeutil) {
    this.state=state;
    this.typeutil=typeutil;
    this.taganalysis=taganalysis;
  }

  /** Builds a table of flags for each class in the Bristlecone
   *	program.  It creates two hashtables: one which holds the
   *	ClassDescriptors and arrays of * FlagDescriptors as key-value
   *	pairs; the other holds the ClassDescriptor and the * number of
   *	external flags for that specific class.
   */

  private void getFlagsfromClasses() {
    flags=new Hashtable();
    extern_flags = new Hashtable();

    /** Iterate through the classes used in the program to build
     * the table of flags
     */
    for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext(); ) {

      ClassDescriptor cd = (ClassDescriptor)it_classes.next();
      Vector vFlags=new Vector();
      FlagDescriptor flag[];
      int ctr=0;


      /* Adding the flags of the super class */
      ClassDescriptor tmp=cd;
      while(tmp!=null) {
        for(Iterator it_cflags=tmp.getFlags(); it_cflags.hasNext(); ) {
          FlagDescriptor fd = (FlagDescriptor)it_cflags.next();
          vFlags.add(fd);
        }
        tmp=tmp.getSuperDesc();
      }


      if (vFlags.size()!=0) {
        flag=new FlagDescriptor[vFlags.size()];

        for(int i=0; i < vFlags.size(); i++) {
          if (((FlagDescriptor)vFlags.get(i)).getExternal()) {
            flag[ctr]=(FlagDescriptor)vFlags.get(i);
            vFlags.remove(flag[ctr]);
            ctr++;
          }
        }
        for(int i=0; i < vFlags.size(); i++) {
          flag[i+ctr]=(FlagDescriptor)vFlags.get(i);
        }
        extern_flags.put(cd,new Integer(ctr));
        flags.put(cd,flag);

      }
    }
  }
  /** Method which starts up the analysis
   */

  public void taskAnalysis() throws java.io.IOException {
    flagstates=new Hashtable();
    Hashtable<FlagState,FlagState> sourcenodes;
    cdtorootnodes=new Hashtable();
    tdToFEdges=new Hashtable();

    getFlagsfromClasses();

    int externs;
    toprocess=new LinkedList<FlagState>();

    for(Iterator it_classes=(Iterator)flags.keys(); it_classes.hasNext(); ) {
      ClassDescriptor cd=(ClassDescriptor)it_classes.next();
      externs=((Integer)extern_flags.get(cd)).intValue();
      FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);
      flagstates.put(cd,new Hashtable<FlagState,FlagState>());
      cdtorootnodes.put(cd,new Vector());
    }


    ClassDescriptor startupobject=typeutil.getClass(TypeUtil.StartupClass);

    sourcenodes=(Hashtable<FlagState,FlagState>)flagstates.get(startupobject);
    FlagState fsstartup=new FlagState(startupobject);


    FlagDescriptor[] fd=(FlagDescriptor[])flags.get(startupobject);

    fsstartup=fsstartup.setFlag(fd[0],true);
    fsstartup.setAsSourceNode();
    ((Vector)cdtorootnodes.get(startupobject)).add(fsstartup);

    sourcenodes.put(fsstartup,fsstartup);
    toprocess.add(fsstartup);

    /** Looping through the flagstates in the toprocess queue to
     * perform the state analysis */
    while (!toprocess.isEmpty()) {
      FlagState trigger=toprocess.poll();
      createPossibleRuntimeStates(trigger);

      analyseTasks(trigger);
    }

    /** Creating DOT files */
    Enumeration e=flagstates.keys();

    while (e.hasMoreElements()) {
      ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
      createDOTfile(cdtemp);
    }
  }


  /** Analyses the set of tasks based on the given flagstate, checking
   *  to see which tasks are triggered and what new flagstates are created
   *  from the base flagstate.
   *  @param fs A FlagState object which is used to analyse the task
   *  @see FlagState
   */

  private void analyseTasks(FlagState fs) {
    ClassDescriptor cd=fs.getClassDescriptor();
    Hashtable<FlagState,FlagState> sourcenodes=(Hashtable<FlagState,FlagState>)flagstates.get(cd);

    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator(); it_tasks.hasNext(); ) {
      TaskDescriptor td = (TaskDescriptor)it_tasks.next();
      String taskname=td.getSymbol();

      if(!tdToFEdges.containsKey(td)) {
        tdToFEdges.put(td, new Vector<FEdge>());
      }

      /** counter to keep track of the number of parameters (of the
       *  task being analyzed) that are satisfied by the flagstate.
       */
      int trigger_ctr=0;
      TempDescriptor temp=null;
      FlatMethod fm = state.getMethodFlat(td);
      int parameterindex=0;

      for(int i=0; i < td.numParameters(); i++) {
        FlagExpressionNode fen=td.getFlag(td.getParameter(i));
        TagExpressionList tel=td.getTag(td.getParameter(i));

        /** Checking to see if the parameter is of the same
         *  type/class as the flagstate's and also if the
         *  flagstate fs triggers the given task*/

        if (typeutil.isSuperorType(td.getParamType(i).getClassDesc(),cd)
            && isTaskTrigger_flag(fen,fs)
            && isTaskTrigger_tag(tel,fs)) {
          temp=fm.getParameter(i);
          parameterindex=i;
          trigger_ctr++;
        }
      }

      if (trigger_ctr==0)   //Look at next task
        continue;

      if (trigger_ctr>1)
        System.out.println("Illegal Operation: A single flagstate cannot satisfy more than one parameter of a task:"+fs + " in "+td);


      Set newstates=taganalysis.getFlagStates(td);
      for(Iterator fsit=newstates.iterator(); fsit.hasNext(); ) {
        FlagState fsnew=(FlagState) fsit.next();
        System.out.println("SOURCE:"+fsnew);

        if (!((Hashtable<FlagState,FlagState>)flagstates.get(fsnew.getClassDescriptor())).containsKey(fsnew)) {
          ((Hashtable<FlagState,FlagState>)flagstates.get(fsnew.getClassDescriptor())).put(fsnew, fsnew);
          toprocess.add(fsnew);
        } else {
          fsnew=((Hashtable<FlagState, FlagState>)flagstates.get(fsnew.getClassDescriptor())).get(fsnew);
        }
        fsnew.setAsSourceNode();
        fsnew.addAllocatingTask(td);

        if(!((Vector)cdtorootnodes.get(fsnew.getClassDescriptor())).contains(fsnew)) {
          ((Vector)cdtorootnodes.get(fsnew.getClassDescriptor())).add(fsnew);
        }
      }

      Stack nodestack=new Stack();
      HashSet discovered=new HashSet();
      nodestack.push(fm);
      discovered.add(fm);
      //Iterating through the nodes

      while(!nodestack.isEmpty()) {
        FlatNode fn1 = (FlatNode) nodestack.pop();

        if (fn1.kind()==FKind.FlatReturnNode) {
          /* Self edge */
          FEdge newedge=new FEdge(fs, taskname, td, parameterindex);
          ((Vector<FEdge>)tdToFEdges.get(td)).add(newedge);
          fs.addEdge(newedge);
          newedge.setisbackedge(true);
          continue;
        } else if (fn1.kind()==FKind.FlatFlagActionNode) {
          FlatFlagActionNode ffan=(FlatFlagActionNode)fn1;
          if (ffan.getTaskType() == FlatFlagActionNode.PRE) {
            if (ffan.getTempFlagPairs().hasNext()||ffan.getTempTagPairs().hasNext())
              throw new Error("PRE FlagActions not supported");

          } else if (ffan.getTaskType() == FlatFlagActionNode.TASKEXIT) {
            Vector<FlagState> fsv_taskexit=evalTaskExitNode(ffan,cd,fs,temp);
            Vector<FlagState> initFStates = ffan.getInitFStates(temp.getType().getClassDesc());
            if(!initFStates.contains(fs)) {
              initFStates.addElement(fs);
            }
            Vector<FlagState> targetFStates = ffan.getTargetFStates(fs);
            for(Enumeration en=fsv_taskexit.elements(); en.hasMoreElements(); ) {
              FlagState fs_taskexit=(FlagState)en.nextElement();
              if (!sourcenodes.containsKey(fs_taskexit)) {
                toprocess.add(fs_taskexit);
              }
              //seen this node already
              fs_taskexit=canonicalizeFlagState(sourcenodes,fs_taskexit);
              FEdge newedge=new FEdge(fs_taskexit,taskname, td, parameterindex);
              newedge.setTaskExitIndex(ffan.getTaskExitIndex());
              ((Vector<FEdge>)tdToFEdges.get(td)).add(newedge);
              fs.addEdge(newedge);

              if(!targetFStates.contains(fs_taskexit)) {
                targetFStates.addElement(fs_taskexit);
              }
            }
            continue;
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
    }
  }


/** Determines whether the given flagstate satisfies a
 *  single parameter in the given task.
 *  @param fen FlagExpressionNode
 *  @see FlagExpressionNode
 *  @param fs  FlagState
 *  @see FlagState
 *  @return <CODE>true</CODE> if fs satisfies the boolean expression
    denoted by fen else <CODE>false</CODE>.
 */


  public static boolean isTaskTrigger_flag(FlagExpressionNode fen,FlagState fs) {
    if (fen==null)
      return true;
    else if (fen instanceof FlagNode)
      return fs.get(((FlagNode)fen).getFlag());
    else
      switch (((FlagOpNode)fen).getOp().getOp()) {
      case Operation.LOGIC_AND:
        return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) && (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));

      case Operation.LOGIC_OR:
        return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) || (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));

      case Operation.LOGIC_NOT:
        return !(isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs));

      default:
        return false;
      }
  }

  private boolean isTaskTrigger_tag(TagExpressionList tel, FlagState fs) {

    if (tel!=null) {
      for (int i=0; i<tel.numTags(); i++) {
        switch (fs.getTagCount(tel.getType(i))) {
        case FlagState.ONETAG:
        case FlagState.MULTITAGS:
          break;

        case FlagState.NOTAGS:
          return false;
        }
      }
    }
    return true;
  }


  private Vector<FlagState> evalTaskExitNode(FlatFlagActionNode ffan,ClassDescriptor cd,FlagState fs, TempDescriptor temp) {
    FlagState fstemp=fs;
    Vector<FlagState> processed=new Vector<FlagState>();

    //Process the flag changes

    for(Iterator it_tfp=ffan.getTempFlagPairs(); it_tfp.hasNext(); ) {
      TempFlagPair tfp=(TempFlagPair)it_tfp.next();
      if (temp==tfp.getTemp())
        fstemp=fstemp.setFlag(tfp.getFlag(),ffan.getFlagChange(tfp));
    }

    //Process the tag changes

    processed.add(fstemp);

    //Process clears first
    for(Iterator it_ttp=ffan.getTempTagPairs(); it_ttp.hasNext(); ) {
      TempTagPair ttp=(TempTagPair)it_ttp.next();

      if (temp==ttp.getTemp()) {
        Vector<FlagState> oldprocess=processed;
        processed=new Vector<FlagState>();

        for (Enumeration en=oldprocess.elements(); en.hasMoreElements(); ) {
          FlagState fsworking=(FlagState)en.nextElement();
          if (!ffan.getTagChange(ttp)) {
            processed.addAll(Arrays.asList(fsworking.clearTag(ttp.getTag())));
          } else processed.add(fsworking);
        }
      }
    }
    //Process sets next
    for(Iterator it_ttp=ffan.getTempTagPairs(); it_ttp.hasNext(); ) {
      TempTagPair ttp=(TempTagPair)it_ttp.next();

      if (temp==ttp.getTemp()) {
        Vector<FlagState> oldprocess=processed;
        processed=new Vector<FlagState>();

        for (Enumeration en=oldprocess.elements(); en.hasMoreElements(); ) {
          FlagState fsworking=(FlagState)en.nextElement();
          if (ffan.getTagChange(ttp)) {
            processed.addAll(Arrays.asList(fsworking.setTag(ttp.getTag())));
          } else processed.add(fsworking);
        }
      }
    }
    return processed;
  }


  private FlagState canonicalizeFlagState(Hashtable sourcenodes, FlagState fs) {
    if (sourcenodes.containsKey(fs))
      return (FlagState)sourcenodes.get(fs);
    else {
      sourcenodes.put(fs,fs);
      return fs;
    }
  }

  /** Creates a DOT file using the flagstates for a given class
   *  @param cd ClassDescriptor of the class
   *  @throws java.io.IOException
   *  @see ClassDescriptor
   */

  public void createDOTfile(ClassDescriptor cd) throws java.io.IOException {
    File dotfile_flagstates= new File("graph"+cd.getSymbol()+".dot");
    FileOutputStream dotstream=new FileOutputStream(dotfile_flagstates,false);
    FlagState.DOTVisitor.visit(dotstream,((Hashtable)flagstates.get(cd)).values());
  }

  /** Returns the flag states for the class descriptor. */
  public Set<FlagState> getFlagStates(ClassDescriptor cd) {
    if (flagstates.containsKey(cd))
      return ((Hashtable<FlagState, FlagState>)flagstates.get(cd)).keySet();
    else
      return new HashSet<FlagState>();
  }


  private void createPossibleRuntimeStates(FlagState fs) {
    ClassDescriptor cd = fs.getClassDescriptor();
    Hashtable<FlagState,FlagState> sourcenodes=(Hashtable<FlagState,FlagState>)flagstates.get(cd);
    FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);
    int externs=((Integer)extern_flags.get(cd)).intValue();

    if(externs==0)
      return;

    int noOfIterations=(1<<externs) - 1;
    boolean BoolValTable[]=new boolean[externs];


    for(int i=0; i < externs; i++) {
      BoolValTable[i]=fs.get(fd[i]);
    }

    for(int k=0; k<noOfIterations; k++) {
      for(int j=0; j < externs; j++) {
        if ((k% (1<<j)) == 0)
          BoolValTable[j]=(!BoolValTable[j]);
      }

      FlagState fstemp=fs;

      for(int i=0; i < externs; i++) {
        fstemp=fstemp.setFlag(fd[i],BoolValTable[i]);
      }
      if (!sourcenodes.containsKey(fstemp))
        toprocess.add(fstemp);

      fstemp=canonicalizeFlagState(sourcenodes,fstemp);
      fs.addEdge(new FEdge(fstemp,"Runtime", null, -1));
    }
  }

  public Vector getRootNodes(ClassDescriptor cd) {
    return (Vector)cdtorootnodes.get(cd);
  }

  public Vector<FEdge> getFEdgesFromTD(TaskDescriptor td) {
    return (Vector<FEdge>)tdToFEdges.get(td);
  }
}

