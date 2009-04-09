package IR;
import IR.Tree.*;
import IR.Flat.*;
import IR.*;
import java.util.*;
import Analysis.TaskStateAnalysis.*;

public class State {

    public int lines;
  public State() {
    this.classes=new SymbolTable();
    this.tasks=new SymbolTable();
    this.treemethodmap=new Hashtable();
    this.flatmethodmap=new Hashtable();
    this.parsetrees=new HashSet();
    this.arraytypes=new HashSet();
    this.arraytonumber=new Hashtable();
    this.tagmap=new Hashtable();
    this.selfloops=new HashSet();
    this.excprefetch=new HashSet();
    this.classpath=new Vector();
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
  public boolean WEBINTERFACE=false;
  public boolean TASK=false;
  public boolean FASTCHECK=false;
  public boolean DSM=false;
  public boolean PREFETCH=false;
  public boolean TASKSTATE=false;
  public boolean TAGSTATE=false;
  public boolean FLATIRGRAPH=false;
  public boolean FLATIRGRAPHTASKS=false;
  public boolean FLATIRGRAPHUSERMETHODS=false;
  public boolean FLATIRGRAPHLIBMETHODS=false;
  public boolean MULTICORE=false;
  public boolean OWNERSHIP=false;
  public int OWNERSHIPALLOCDEPTH=3;
  public boolean OWNERSHIPWRITEDOTS=false;
  public boolean OWNERSHIPWRITEALL=false;
  public String OWNERSHIPALIASFILE=null;
  public boolean OPTIONAL=false;
  public boolean ARRAYBOUNDARYCHECK=true;
  public boolean RAW=false;
  public boolean SCHEDULING=false;
  public boolean USEPROFILE=false;
  public boolean THREAD=false;
  public boolean CONSCHECK=false;
  public boolean INSTRUCTIONFAILURE=false;
  public boolean MLP=false;
  public boolean MLPDEBUG=false;
  public static double TRUEPROB=0.8;
  public static boolean PRINTFLAT=false;
  public static boolean PRINTSCHEDULING=false;
  public static boolean PRINTSCHEDULESIM=false;
  public static boolean PRINTCRITICALPATH=false;
  public static boolean ABORTREADERS=false;
  public static boolean SINGLETM=false;
  public int CORENUM = 1;
  public String structfile;
  public String main;
  public String outputdir = "/scratch/";

  public HashSet selfloops;
  public HashSet excprefetch;
  public Vector classpath;
  public SymbolTable classes;
  public SymbolTable tasks;
  public Set parsetrees;
  public Hashtable treemethodmap;
  public Hashtable flatmethodmap;
  private HashSet arraytypes;
  public Hashtable arraytonumber;
  private int numclasses=0;
  private int numtasks=0;
  private int arraycount=0;
  public boolean OPTIMIZE=false;


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

  public void addClass(ClassDescriptor tdn) {
    if (classes.contains(tdn.getSymbol()))
      throw new Error("Class "+tdn.getSymbol()+" defined twice");
    classes.add(tdn);
    numclasses++;
  }

  public int numClasses() {
    return numclasses;
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

  /** Returns Flat IR representation of MethodDescriptor md. */

  public FlatMethod getMethodFlat(MethodDescriptor md) {
    return (FlatMethod)flatmethodmap.get(md);
  }

  /** Returns Flat IR representation of TaskDescriptor td. */

  public FlatMethod getMethodFlat(TaskDescriptor td) {
    return (FlatMethod)flatmethodmap.get(td);
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
}
