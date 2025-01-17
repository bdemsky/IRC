package IR;
import IR.Tree.*;
import IR.Flat.*;
import IR.*;
import Util.Lattice;
import Util.Pair;

import java.util.*;
import Analysis.TaskStateAnalysis.*;
import Analysis.CallGraph.CallGraph;

public class State {
  public static long startTime;
  public static long lastTime;
  public static void logEvent(String event) {
    long currTime=System.nanoTime();
    double delta=((double)(currTime-startTime))/1000000000;
    double deltaLast=((double)(currTime-lastTime))/1000000000;
    System.out.println("TLOG: Time of "+event+"="+delta);
    System.out.println("TLOG: Elapsed time "+event+"="+deltaLast);
    lastTime=currTime;
  }

  public static void initTimer() {
    startTime=System.nanoTime();
    lastTime=startTime;
  }

  public int lines;
  public State() {
    this.classes=new SymbolTable();
    this.tasks=new SymbolTable();
    this.sclasses=new SymbolTable();
    this.treemethodmap=new Hashtable();
    this.flatmethodmap=new Hashtable();
    this.parsetrees=new HashSet();
    this.arraytypes=new HashSet();
    this.arraytonumber=new Hashtable();
    this.tagmap=new Hashtable();
    this.selfloops=new HashSet();
    this.excprefetch=new HashSet();
    this.classpath=new Vector();
    this.fn2labelMap=new Hashtable();
    this.lines=0;
  }

  public void addParseNode(ParseNode parsetree) {
    parsetrees.add(parsetree);
  }

  public void storeAnalysisResult(Hashtable<ClassDescriptor, Hashtable<FlagState, Set<OptionalTaskDescriptor>>> analysisresults) {
    this.analysisresults=analysisresults;
  }

  public Hashtable<ClassDescriptor, Hashtable<FlagState, Set<OptionalTaskDescriptor>>> getAnalysisResult() {
    return analysisresults;
  }


  public void storeOptionalTaskDescriptors(Hashtable<ClassDescriptor, Hashtable<OptionalTaskDescriptor, OptionalTaskDescriptor>> optionaltaskdescriptors) {
    this.optionaltaskdescriptors=optionaltaskdescriptors;
  }

  public Hashtable<ClassDescriptor, Hashtable<OptionalTaskDescriptor, OptionalTaskDescriptor>> getOptionalTaskDescriptors() {
    return optionaltaskdescriptors;
  }


  /** Boolean flag which indicates whether compiler is compiling a task-based
   * program. */
  public boolean JNI=false;
  public boolean CAPTURE_NULL_DEREFERENCES=false;
  public boolean POINTER=false;
  public boolean COREPROF=false;
  public boolean WEBINTERFACE=false;
  public boolean MINIMIZE=false;
  public boolean TASK=false;
  public boolean FASTCHECK=false;
  public boolean PREFETCH=false;
  public boolean TASKSTATE=false;
  public boolean TAGSTATE=false;
  public boolean FLATIRGRAPH=false;
  public boolean FLATIRGRAPHTASKS=false;
  public boolean FLATIRGRAPHUSERMETHODS=false;
  public boolean FLATIRGRAPHLIBMETHODS=false;
  public boolean KEEP_RG_FOR_ALL_PROGRAM_POINTS=false;
  public boolean OWNERSHIP=false;
  public int OWNERSHIPALLOCDEPTH=3;
  public boolean OWNERSHIPWRITEDOTS=false;
  public boolean OWNERSHIPWRITEALL=false;
  public String OWNERSHIPALIASFILE=null;
  public boolean OWNERSHIPALIASTAB=false;
  public int OWNERSHIPDEBUGCALLCOUNT=0;
  public String OWNERSHIPDEBUGCALLEE=null;
  public String OWNERSHIPDEBUGCALLER=null;


  public boolean DISJOINT=false;

  public int DISJOINTALLOCDEPTH=1;
  public boolean DISJOINTRELEASEMODE=false;
  public boolean DISJOINTDETERMINISM=false;

  public boolean DISJOINTDVISITSTACK=true;
  public boolean DISJOINTDVISITPQUE=false;
  public boolean DISJOINTDVISITSTACKEESONTOP=false;

  public boolean DISJOINTWRITEDOTS=false;
  public boolean DISJOINTWRITEALL=false;
  public boolean DISJOINTWRITEIHMS=false;
  public boolean DISJOINTWRITEINITCONTEXTS=false;

  public String DISJOINTALIASFILE=null;
  public boolean DISJOINTALIASTAB=false;

  public String DISJOINTDEBUGCALLEE=null;
  public String DISJOINTDEBUGCALLER=null;
  public int DISJOINTDEBUGCALLVISITTOSTART=0;
  public int DISJOINTDEBUGCALLNUMVISITS=0;
  public boolean DISJOINTDEBUGCALLSTOPAFTER=false;

  public String DISJOINTSNAPSYMBOL=null;
  public int DISJOINTSNAPVISITTOSTART=0;
  public int DISJOINTSNAPNUMVISITS=0;
  public boolean DISJOINTSNAPSTOPAFTER=false;
  public boolean DISJOINTDEBUGSCHEDULING=false;
  public boolean DISJOINT_WRITE_ALL_NODE_FINAL_GRAPHS=false;
  public boolean DISJOINT_COUNT_VISITS=false;
  public boolean DISJOINT_COUNT_GRAPH_ELEMENTS=false;
  public String DISJOINT_COUNT_GRAPH_ELEMENTS_FILE=null;

  public boolean POINTSTO_CHECK_V_RUNTIME=false;

  public boolean DO_DEFINITE_REACH_ANALYSIS=false;
  public boolean DISJOINT_USE_GLOBAL_SWEEP=true;
  public boolean DISJOINT_USE_STRONG_UPDATE=true;
  public boolean DISJOINT_USE_PREDICATES=true;
  public boolean DISJOINT_SUMMARIZE_PER_CLASS=false;


  public boolean OOOJAVA=false;
  public boolean OOODEBUG=false;
  public boolean RCR=false;
  public boolean RCR_DEBUG=false;
  public boolean RCR_DEBUG_VERBOSE=false;
  public boolean NOSTALLTR=false;

  //SSJava
  public boolean SSJAVA=false;
  public boolean SSJAVADEBUG=false;
  public boolean SSJAVAINFER=false;
  public boolean SSJAVA_GENCODE_PREVENT_CRASHES=false;
  public boolean SSJAVA_INJECT_ERROR=false;
  public boolean SSJAVA_INFER_NAIVE_WRITEDOTS=false;
  public int     SSJAVA_INV_ERROR_PROB=0;
  public int     SSJAVA_ERROR_SEED=0;
  public Set<MethodDescriptor> annotationRequireSet;

  public boolean OPTIONAL=false;
  public boolean NOLOOP=false;
  public boolean ARRAYPAD=false;
  public boolean THREAD=false;
  public boolean CONSCHECK=false;
  public boolean INSTRUCTIONFAILURE=false;
  public int OOO_NUMCORES=0;
  public int OOO_MAXSESEAGE=0;
  public boolean METHODEFFECTS=false;
  public static double TRUEPROB=0.8;
  public static boolean PRINTFLAT=false;

  //DSM options
  public boolean DSM=false;
  public boolean DSMTASK=false;
  public static boolean ABORTREADERS=false;

  // Recovery Stats Options
  public boolean DSMRECOVERYSTATS=false;
  //STM options
  public boolean EVENTMONITOR=false;
  public static boolean STMARRAY=false;
  public static boolean SINGLETM=false;
  public static boolean READSET=false;
  public boolean SANDBOX=false;
  public boolean DCOPTS=false;
  public boolean DELAYCOMP=false;
  public boolean DUALVIEW=false;
  public boolean HYBRID=false;

  // Bamboo options
  public boolean MULTICORE=false;
  public boolean MULTICOREGC=false;
  public boolean PMC=false;
  public boolean BAMBOOCOMPILETIME = false;
  public boolean ARRAYBOUNDARYCHECK=true;
  public boolean RAW=false;
  public boolean SCHEDULING=false;
  public boolean USEPROFILE=false;
  public static boolean PRINTSCHEDULING=false;
  public static boolean PRINTSCHEDULESIM=false;
  public static boolean PRINTCRITICALPATH=false;
  public int CORENUM = 1;
  public int CORENUM4GC = 0;
  public String profilename = null;
  public String outputdir = "/scratch/";
  // MGC options
  public boolean MGC=false;
  public boolean OBJECTLOCKDEBUG=false;

  //Other options
  public String structfile;
  public String main;
  public boolean INLINEATOMIC=false;
  public int inlineatomicdepth;
  public HashSet selfloops;
  public HashSet excprefetch;
  public Vector classpath;
  public SymbolTable classes;
  public SymbolTable tasks;
  public SymbolTable sclasses; // table of classes with static field/blocks
  public Set parsetrees;
  public Hashtable treemethodmap;
  public Hashtable flatmethodmap;
  SymbolTable methods2gen;
  private HashSet arraytypes;
  public Hashtable arraytonumber;
  private int numclasses=1; // start from 1 instead of 0 for multicore gc
  private int numinterfaces = 0;
  private int numtasks=0;
  private int numstaticblocks=0;
  private int arraycount=0;
  public Hashtable fn2labelMap;
  public boolean OPTIMIZE=false;
  public boolean LINENUM=false;

  private Hashtable<ClassDescriptor, Hashtable<OptionalTaskDescriptor, OptionalTaskDescriptor>> optionaltaskdescriptors;
  private Hashtable<ClassDescriptor, Hashtable<FlagState, Set<OptionalTaskDescriptor>>> analysisresults;

  private Hashtable tagmap;
  private int numtags=0;

  public void addArrayType(TypeDescriptor td) {
    if (!arraytypes.contains(td)) {
      arraytypes.add(td);
      arraytonumber.put(td,new Integer(arraycount++));
    }
  }

  public Iterator getArrayIterator() {
    return arraytypes.iterator();
  }

  public int getTagId(TagDescriptor tag) {
    if (tagmap.containsKey(tag)) {
      return ((Integer) tagmap.get(tag)).intValue();
    } else {
      tagmap.put(tag, new Integer(numtags));
      return numtags++;
    }
  }

  public int getArrayNumber(TypeDescriptor td) {
    if (arraytonumber.containsKey(td))
      return ((Integer)arraytonumber.get(td)).intValue();
    else return -1;
  }

  public int numArrays() {
    return arraytypes.size();
  }

  public static TypeDescriptor getTypeDescriptor(int t) {
    TypeDescriptor td=new TypeDescriptor(t);
    return td;
  }

  public static TypeDescriptor getTypeDescriptor(NameDescriptor n) {
    TypeDescriptor td=new TypeDescriptor(n);
    return td;
  }

  public static TypeDescriptor getTypeDescriptor(String n) {
    TypeDescriptor td=new TypeDescriptor(n);
    return td;
  }

  public void addClass(ClassDescriptor tdn) {
    if (classes.contains(tdn.getSymbol()))
      throw new Error("Class "+tdn.getSymbol()+" defined twice");
    classes.add(tdn);
    if(tdn.isInterface()) {
      numinterfaces++;
    } else {
      numclasses++;
    }
    if((tdn.numstaticfields != 0) || (tdn.numstaticblocks != 0)) {
      sclasses.add(tdn);
    }
  }

  public int numClasses() {
    return numclasses;
  }

  public int numInterfaces() {
    return numinterfaces;
  }

  public int numStaticBlocks() {
    return numstaticblocks;
  }

  public BlockNode getMethodBody(MethodDescriptor md) {
    return (BlockNode)treemethodmap.get(md);
  }

  public BlockNode getMethodBody(TaskDescriptor td) {
    return (BlockNode)treemethodmap.get(td);
  }

  public SymbolTable getClassSymbolTable() {
    return classes;
  }

  public SymbolTable getTaskSymbolTable() {
    return tasks;
  }

  public SymbolTable getSClassSymbolTable() {
    return sclasses;
  }

  /** Returns Flat IR representation of MethodDescriptor md. */

  public FlatMethod getMethodFlat(MethodDescriptor md) {
    return (FlatMethod)flatmethodmap.get(md);
  }

  /** Returns Flat IR representation of TaskDescriptor td. */

  public FlatMethod getMethodFlat(TaskDescriptor td) {
    return (FlatMethod)flatmethodmap.get(td);
  }

  // The descriptor is either a method or task desc
  // and should call one of the above methods
  public FlatMethod getMethodFlat(Descriptor d) {
    FlatMethod fm;
    if( d instanceof MethodDescriptor ) {
      fm = getMethodFlat( (MethodDescriptor) d);
    } else {
      assert d instanceof TaskDescriptor;
      fm = getMethodFlat( (TaskDescriptor) d);
    }
    return fm;
  }

  public void addTreeCode(MethodDescriptor md, BlockNode bn) {
    treemethodmap.put(md,bn);
  }

  public void addTreeCode(TaskDescriptor td, BlockNode bn) {
    treemethodmap.put(td,bn);
  }

  public void addFlatCode(MethodDescriptor md, FlatMethod bn) {
    flatmethodmap.put(md,bn);
  }

  public void addFlatCode(TaskDescriptor td, FlatMethod bn) {
    flatmethodmap.put(td,bn);
  }

  public void addTask(TaskDescriptor td) {
    if (tasks.contains(td.getSymbol()))
      throw new Error("Task "+td.getSymbol()+" defined twice");
    tasks.add(td);
    numtasks++;
  }
  
  public void setAnnotationRequireSet(Set<MethodDescriptor> set){
    annotationRequireSet=set;
  }
  
  public Set<MethodDescriptor> getAnnotationRequireSet(){
    return annotationRequireSet;
  }

}
