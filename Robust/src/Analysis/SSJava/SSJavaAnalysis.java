package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import Analysis.CallGraph.CallGraph;
import Analysis.Loops.GlobalFieldType;
import Analysis.Loops.LoopFinder;
import Analysis.Loops.LoopOptimize;
import Analysis.Loops.LoopTerminate;
import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.SymbolTable;
import IR.TypeUtil;
import IR.Flat.BuildFlat;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import Util.Pair;

public class SSJavaAnalysis {

  public static final String SSJAVA = "SSJAVA";
  public static final String LATTICE = "LATTICE";
  public static final String METHODDEFAULT = "METHODDEFAULT";
  public static final String THISLOC = "THISLOC";
  public static final String GLOBALLOC = "GLOBALLOC";
  public static final String RETURNLOC = "RETURNLOC";
  public static final String LOC = "LOC";
  public static final String DELTA = "DELTA";
  public static final String TERMINATE = "TERMINATE";
  public static final String DELEGATE = "DELEGATE";
  public static final String DELEGATETHIS = "DELEGATETHIS";
  public static final String TRUST = "TRUST";

  public static final String TOP = "_top_";
  public static final String BOTTOM = "_bottom_";

  State state;
  TypeUtil tu;
  FlowDownCheck flowDownChecker;
  MethodAnnotationCheck methodAnnotationChecker;
  BuildFlat bf;

  // set containing method requires to be annoated
  Set<MethodDescriptor> annotationRequireSet;

  // class -> field lattice
  Hashtable<ClassDescriptor, SSJavaLattice<String>> cd2lattice;

  // class -> default local variable lattice
  Hashtable<ClassDescriptor, MethodLattice<String>> cd2methodDefault;

  // method -> local variable lattice
  Hashtable<MethodDescriptor, MethodLattice<String>> md2lattice;

  // method set that does not want to have loop termination analysis
  Hashtable<MethodDescriptor, Integer> skipLoopTerminate;

  // map shared location to its descriptors
  Hashtable<Location, Set<Descriptor>> mapSharedLocation2DescriptorSet;

  // set containing a class that has at least one annoated method
  Set<ClassDescriptor> annotationRequireClassSet;

  // the set of method descriptor required to check the linear type property
  Set<MethodDescriptor> linearTypeCheckMethodSet;

  // the set of method descriptors annotated as "TRUST"
  Set<MethodDescriptor> trustWorthyMDSet;

  // points to method containing SSJAVA Loop
  private MethodDescriptor methodContainingSSJavaLoop;

  private FlatNode ssjavaLoopEntrance;

  // keep the field ownership from the linear type checking
  Hashtable<MethodDescriptor, Set<FieldDescriptor>> mapMethodToOwnedFieldSet;

  Set<FlatNode> sameHeightWriteFlatNodeSet;

  CallGraph callgraph;

  LinearTypeCheck checker;

  // maps a descriptor to its known dependents: namely
  // methods or tasks that call the descriptor's method
  // AND are part of this analysis (reachable from main)
  private Hashtable<Descriptor, Set<MethodDescriptor>> mapDescriptorToSetDependents;

  private LinkedList<MethodDescriptor> sortedDescriptors;

  public SSJavaAnalysis(State state, TypeUtil tu, BuildFlat bf, CallGraph callgraph) {
    this.state = state;
    this.tu = tu;
    this.callgraph = callgraph;
    this.cd2lattice = new Hashtable<ClassDescriptor, SSJavaLattice<String>>();
    this.cd2methodDefault = new Hashtable<ClassDescriptor, MethodLattice<String>>();
    this.md2lattice = new Hashtable<MethodDescriptor, MethodLattice<String>>();
    this.annotationRequireSet = new HashSet<MethodDescriptor>();
    this.annotationRequireClassSet = new HashSet<ClassDescriptor>();
    this.skipLoopTerminate = new Hashtable<MethodDescriptor, Integer>();
    this.mapSharedLocation2DescriptorSet = new Hashtable<Location, Set<Descriptor>>();
    this.linearTypeCheckMethodSet = new HashSet<MethodDescriptor>();
    this.bf = bf;
    this.trustWorthyMDSet = new HashSet<MethodDescriptor>();
    this.mapMethodToOwnedFieldSet = new Hashtable<MethodDescriptor, Set<FieldDescriptor>>();
    this.sameHeightWriteFlatNodeSet = new HashSet<FlatNode>();
    this.mapDescriptorToSetDependents = new Hashtable<Descriptor, Set<MethodDescriptor>>();
    this.sortedDescriptors = new LinkedList<MethodDescriptor>();
  }

  public void doCheck() {
    doMethodAnnotationCheck();
    computeLinearTypeCheckMethodSet();
    doLinearTypeCheck();

    init();

    if (state.SSJAVADEBUG) {
      // debugPrint();
    }
    if (state.SSJAVAINFER) {
      inference();
    } else {
      parseLocationAnnotation();
      doFlowDownCheck();
      doDefinitelyWrittenCheck();
      doLoopCheck();
    }
  }

  private void init() {
    // perform topological sort over the set of methods accessed by the main
    // event loop
    Set<MethodDescriptor> methodDescriptorsToAnalyze = new HashSet<MethodDescriptor>();
    methodDescriptorsToAnalyze.addAll(getAnnotationRequireSet());
    sortedDescriptors = topologicalSort(methodDescriptorsToAnalyze);
  }

  public LinkedList<MethodDescriptor> getSortedDescriptors() {
    return (LinkedList<MethodDescriptor>) sortedDescriptors.clone();
  }

  private void inference() {
    LocationInference inferEngine = new LocationInference(this, state);
    inferEngine.inference();
  }

  private void doLoopCheck() {
    GlobalFieldType gft = new GlobalFieldType(callgraph, state, tu.getMain());
    LoopOptimize lo = new LoopOptimize(gft, tu);

    SymbolTable classtable = state.getClassSymbolTable();

    List<ClassDescriptor> toanalyzeList = new ArrayList<ClassDescriptor>();
    List<MethodDescriptor> toanalyzeMethodList = new ArrayList<MethodDescriptor>();

    toanalyzeList.addAll(classtable.getValueSet());
    Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
      public int compare(ClassDescriptor o1, ClassDescriptor o2) {
        return o1.getClassName().compareTo(o2.getClassName());
      }
    });

    for (int i = 0; i < toanalyzeList.size(); i++) {
      ClassDescriptor cd = toanalyzeList.get(i);

      SymbolTable methodtable = cd.getMethodTable();
      toanalyzeMethodList.clear();
      toanalyzeMethodList.addAll(methodtable.getValueSet());
      Collections.sort(toanalyzeMethodList, new Comparator<MethodDescriptor>() {
        public int compare(MethodDescriptor o1, MethodDescriptor o2) {
          return o1.getSymbol().compareTo(o2.getSymbol());
        }
      });

      for (int mdIdx = 0; mdIdx < toanalyzeMethodList.size(); mdIdx++) {
        MethodDescriptor md = toanalyzeMethodList.get(mdIdx);
        if (needTobeAnnotated(md)) {
          lo.analyze(state.getMethodFlat(md));
          doLoopTerminationCheck(lo, state.getMethodFlat(md));
        }
      }

    }

  }

  public void addTrustMethod(MethodDescriptor md) {
    trustWorthyMDSet.add(md);
  }

  public boolean isTrustMethod(MethodDescriptor md) {
    return trustWorthyMDSet.contains(md);
  }

  private void computeLinearTypeCheckMethodSet() {

    Set<MethodDescriptor> allCalledSet = callgraph.getMethodCalls(tu.getMain());
    linearTypeCheckMethodSet.addAll(allCalledSet);

    Set<MethodDescriptor> trustedSet = new HashSet<MethodDescriptor>();

    for (Iterator iterator = trustWorthyMDSet.iterator(); iterator.hasNext();) {
      MethodDescriptor trustMethod = (MethodDescriptor) iterator.next();
      Set<MethodDescriptor> calledFromTrustMethodSet = callgraph.getMethodCalls(trustMethod);
      trustedSet.add(trustMethod);
      trustedSet.addAll(calledFromTrustMethodSet);
    }

    linearTypeCheckMethodSet.removeAll(trustedSet);

    // if a method is called only by trusted method, no need to check linear
    // type & flow down rule
    for (Iterator iterator = trustedSet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      Set<MethodDescriptor> callerSet = callgraph.getCallerSet(md);
      if (!trustedSet.containsAll(callerSet) && !trustWorthyMDSet.contains(md)) {
        linearTypeCheckMethodSet.add(md);
      }
    }

  }

  private void doLinearTypeCheck() {
    LinearTypeCheck checker = new LinearTypeCheck(this, state);
    checker.linearTypeCheck();
  }

  public void debugPrint() {
    System.out.println("SSJAVA: SSJava is checking the following methods:");
    for (Iterator<MethodDescriptor> iterator = annotationRequireSet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = iterator.next();
      System.out.print(" " + md);
    }
    System.out.println();
  }

  private void doMethodAnnotationCheck() {
    methodAnnotationChecker = new MethodAnnotationCheck(this, state, tu);
    methodAnnotationChecker.methodAnnoatationCheck();
    methodAnnotationChecker.methodAnnoataionInheritanceCheck();
    state.setAnnotationRequireSet(annotationRequireSet);
  }

  public void doFlowDownCheck() {
    flowDownChecker = new FlowDownCheck(this, state);
    flowDownChecker.flowDownCheck();
  }

  public void doDefinitelyWrittenCheck() {
    DefinitelyWrittenCheck checker = new DefinitelyWrittenCheck(this, state);
    checker.definitelyWrittenCheck();
  }

  private void parseLocationAnnotation() {
    Iterator it = state.getClassSymbolTable().getDescriptorsIterator();
    while (it.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor) it.next();
      // parsing location hierarchy declaration for the class
      Vector<AnnotationDescriptor> classAnnotations = cd.getModifier().getAnnotations();
      for (int i = 0; i < classAnnotations.size(); i++) {
        AnnotationDescriptor an = classAnnotations.elementAt(i);
        String marker = an.getMarker();
        if (marker.equals(LATTICE)) {
          SSJavaLattice<String> locOrder =
              new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);
          cd2lattice.put(cd, locOrder);
          parseClassLatticeDefinition(cd, an.getValue(), locOrder);

          if (state.SSJAVADEBUG) {
            // generate lattice dot file
            writeLatticeDotFile(cd, null, locOrder);
          }

        } else if (marker.equals(METHODDEFAULT)) {
          MethodLattice<String> locOrder =
              new MethodLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);
          cd2methodDefault.put(cd, locOrder);
          parseMethodDefaultLatticeDefinition(cd, an.getValue(), locOrder);
        }
      }

      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        // parsing location hierarchy declaration for the method

        if (needTobeAnnotated(md)) {
          Vector<AnnotationDescriptor> methodAnnotations = md.getModifiers().getAnnotations();
          if (methodAnnotations != null) {
            for (int i = 0; i < methodAnnotations.size(); i++) {
              AnnotationDescriptor an = methodAnnotations.elementAt(i);
              if (an.getMarker().equals(LATTICE)) {
                // developer explicitly defines method lattice
                MethodLattice<String> locOrder =
                    new MethodLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);
                md2lattice.put(md, locOrder);
                parseMethodDefaultLatticeDefinition(cd, an.getValue(), locOrder);
              } else if (an.getMarker().equals(TERMINATE)) {
                // developer explicitly wants to skip loop termination analysis
                String value = an.getValue();
                int maxIteration = 0;
                if (value != null) {
                  maxIteration = Integer.parseInt(value);
                }
                skipLoopTerminate.put(md, new Integer(maxIteration));
              }
            }
          }
        }

      }

    }
  }

  public <T> void writeLatticeDotFile(ClassDescriptor cd, MethodDescriptor md,
      SSJavaLattice<T> locOrder) {

    String fileName = "lattice_";
    if (md != null) {
      fileName +=
          cd.getSymbol().replaceAll("[\\W_]", "") + "_" + md.getSymbol().replaceAll("[\\W_]", "");
    } else {
      fileName += cd.getSymbol().replaceAll("[\\W_]", "");
    }

    Set<Pair<T, T>> pairSet = locOrder.getOrderingPairSet();

    if (pairSet.size() > 0) {
      try {
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName + ".dot"));

        bw.write("digraph " + fileName + " {\n");

        for (Iterator iterator = pairSet.iterator(); iterator.hasNext();) {
          // pair is in the form of <higher, lower>
          Pair<T, T> pair = (Pair<T, T>) iterator.next();

          T highLocId = pair.getFirst();
          String highLocStr, lowLocStr;
          if (locOrder.isSharedLoc(highLocId)) {
            highLocStr = "\"" + highLocId + "*\"";
          } else {
            highLocStr = highLocId.toString();
          }
          T lowLocId = pair.getSecond();
          if (locOrder.isSharedLoc(lowLocId)) {
            lowLocStr = "\"" + lowLocId + "*\"";
          } else {
            lowLocStr = lowLocId.toString();
          }
          bw.write(highLocId + " -> " + lowLocId + ";\n");
        }
        bw.write("}\n");
        bw.close();

      } catch (IOException e) {
        e.printStackTrace();
      }

    }

  }

  private void parseMethodDefaultLatticeDefinition(ClassDescriptor cd, String value,
      MethodLattice<String> locOrder) {

    value = value.replaceAll(" ", ""); // remove all blank spaces

    StringTokenizer tokenizer = new StringTokenizer(value, ",");

    while (tokenizer.hasMoreTokens()) {
      String orderElement = tokenizer.nextToken();
      int idx = orderElement.indexOf("<");
      if (idx > 0) {// relative order element
        String lowerLoc = orderElement.substring(0, idx);
        String higherLoc = orderElement.substring(idx + 1);
        locOrder.put(higherLoc, lowerLoc);
        if (locOrder.isIntroducingCycle(higherLoc)) {
          throw new Error("Error: the order relation " + lowerLoc + " < " + higherLoc
              + " introduces a cycle.");
        }
      } else if (orderElement.startsWith(THISLOC + "=")) {
        String thisLoc = orderElement.substring(8);
        locOrder.setThisLoc(thisLoc);
      } else if (orderElement.startsWith(GLOBALLOC + "=")) {
        String globalLoc = orderElement.substring(10);
        locOrder.setGlobalLoc(globalLoc);
      } else if (orderElement.startsWith(RETURNLOC + "=")) {
        String returnLoc = orderElement.substring(10);
        locOrder.setReturnLoc(returnLoc);
      } else if (orderElement.endsWith("*")) {
        // spin loc definition
        locOrder.addSharedLoc(orderElement.substring(0, orderElement.length() - 1));
      } else {
        // single element
        locOrder.put(orderElement);
      }
    }

    // sanity checks
    if (locOrder.getThisLoc() != null && !locOrder.containsKey(locOrder.getThisLoc())) {
      throw new Error("Variable 'this' location '" + locOrder.getThisLoc()
          + "' is not defined in the local variable lattice at " + cd.getSourceFileName());
    }

    if (locOrder.getGlobalLoc() != null && !locOrder.containsKey(locOrder.getGlobalLoc())) {
      throw new Error("Variable global location '" + locOrder.getGlobalLoc()
          + "' is not defined in the local variable lattice at " + cd.getSourceFileName());
    }
  }

  private void parseClassLatticeDefinition(ClassDescriptor cd, String value,
      SSJavaLattice<String> locOrder) {

    value = value.replaceAll(" ", ""); // remove all blank spaces

    StringTokenizer tokenizer = new StringTokenizer(value, ",");

    while (tokenizer.hasMoreTokens()) {
      String orderElement = tokenizer.nextToken();
      int idx = orderElement.indexOf("<");

      if (idx > 0) {// relative order element
        String lowerLoc = orderElement.substring(0, idx);
        String higherLoc = orderElement.substring(idx + 1);
        locOrder.put(higherLoc, lowerLoc);
        if (locOrder.isIntroducingCycle(higherLoc)) {
          throw new Error("Error: the order relation " + lowerLoc + " < " + higherLoc
              + " introduces a cycle.");
        }
      } else if (orderElement.contains("*")) {
        // spin loc definition
        locOrder.addSharedLoc(orderElement.substring(0, orderElement.length() - 1));
      } else {
        // single element
        locOrder.put(orderElement);
      }
    }

    // sanity check
    Set<String> spinLocSet = locOrder.getSharedLocSet();
    for (Iterator iterator = spinLocSet.iterator(); iterator.hasNext();) {
      String spinLoc = (String) iterator.next();
      if (!locOrder.containsKey(spinLoc)) {
        throw new Error("Spin location '" + spinLoc
            + "' is not defined in the default local variable lattice at " + cd.getSourceFileName());
      }
    }
  }

  public Hashtable<ClassDescriptor, SSJavaLattice<String>> getCd2lattice() {
    return cd2lattice;
  }

  public Hashtable<ClassDescriptor, MethodLattice<String>> getCd2methodDefault() {
    return cd2methodDefault;
  }

  public Hashtable<MethodDescriptor, MethodLattice<String>> getMd2lattice() {
    return md2lattice;
  }

  public SSJavaLattice<String> getClassLattice(ClassDescriptor cd) {
    return cd2lattice.get(cd);
  }

  public MethodLattice<String> getMethodDefaultLattice(ClassDescriptor cd) {
    return cd2methodDefault.get(cd);
  }

  public MethodLattice<String> getMethodLattice(MethodDescriptor md) {
    if (md2lattice.containsKey(md)) {
      return md2lattice.get(md);
    } else {

      if (cd2methodDefault.containsKey(md.getClassDesc())) {
        return cd2methodDefault.get(md.getClassDesc());
      } else {
        throw new Error("Method Lattice of " + md + " is not defined.");
      }

    }
  }

  public boolean needToCheckLinearType(MethodDescriptor md) {
    return linearTypeCheckMethodSet.contains(md);
  }

  public boolean needTobeAnnotated(MethodDescriptor md) {
    return annotationRequireSet.contains(md);
  }

  public boolean needToBeAnnoated(ClassDescriptor cd) {
    return annotationRequireClassSet.contains(cd);
  }

  public void addAnnotationRequire(ClassDescriptor cd) {
    annotationRequireClassSet.add(cd);
  }

  public void addAnnotationRequire(MethodDescriptor md) {

    ClassDescriptor cd = md.getClassDesc();
    // if a method requires to be annotated, class containg that method also
    // requires to be annotated
    if (!isSSJavaUtil(cd)) {
      annotationRequireClassSet.add(cd);
      annotationRequireSet.add(md);
    }
  }

  public Set<MethodDescriptor> getAnnotationRequireSet() {
    return annotationRequireSet;
  }

  public void doLoopTerminationCheck(LoopOptimize lo, FlatMethod fm) {
    LoopTerminate lt = new LoopTerminate(this, state);
    if (needTobeAnnotated(fm.getMethod())) {
      lt.terminateAnalysis(fm, lo.getLoopInvariant(fm));
    }
  }

  public CallGraph getCallGraph() {
    return callgraph;
  }

  public SSJavaLattice<String> getLattice(Descriptor d) {

    if (d instanceof MethodDescriptor) {
      return getMethodLattice((MethodDescriptor) d);
    } else {
      return getClassLattice((ClassDescriptor) d);
    }

  }

  public boolean isSharedLocation(Location loc) {
    SSJavaLattice<String> lattice = getLattice(loc.getDescriptor());
    return lattice.getSharedLocSet().contains(loc.getLocIdentifier());
  }

  public void mapSharedLocation2Descriptor(Location loc, Descriptor d) {
    Set<Descriptor> set = mapSharedLocation2DescriptorSet.get(loc);
    if (set == null) {
      set = new HashSet<Descriptor>();
      mapSharedLocation2DescriptorSet.put(loc, set);
    }
    set.add(d);
  }

  public BuildFlat getBuildFlat() {
    return bf;
  }

  public MethodDescriptor getMethodContainingSSJavaLoop() {
    return methodContainingSSJavaLoop;
  }

  public void setMethodContainingSSJavaLoop(MethodDescriptor methodContainingSSJavaLoop) {
    this.methodContainingSSJavaLoop = methodContainingSSJavaLoop;
  }

  public boolean isSSJavaUtil(ClassDescriptor cd) {
    if (cd.getSymbol().equals("SSJAVA")) {
      return true;
    }
    return false;
  }

  public void setFieldOnwership(MethodDescriptor md, FieldDescriptor field) {

    Set<FieldDescriptor> fieldSet = mapMethodToOwnedFieldSet.get(md);
    if (fieldSet == null) {
      fieldSet = new HashSet<FieldDescriptor>();
      mapMethodToOwnedFieldSet.put(md, fieldSet);
    }
    fieldSet.add(field);
  }

  public boolean isOwnedByMethod(MethodDescriptor md, FieldDescriptor field) {
    Set<FieldDescriptor> fieldSet = mapMethodToOwnedFieldSet.get(md);
    if (fieldSet != null) {
      return fieldSet.contains(field);
    }
    return false;
  }

  public FlatNode getSSJavaLoopEntrance() {
    return ssjavaLoopEntrance;
  }

  public void setSSJavaLoopEntrance(FlatNode ssjavaLoopEntrance) {
    this.ssjavaLoopEntrance = ssjavaLoopEntrance;
  }

  public void addSameHeightWriteFlatNode(FlatNode fn) {
    this.sameHeightWriteFlatNodeSet.add(fn);
  }

  public boolean isSameHeightWrite(FlatNode fn) {
    return this.sameHeightWriteFlatNodeSet.contains(fn);
  }

  public LinkedList<MethodDescriptor> topologicalSort(Set<MethodDescriptor> toSort) {

    Set<MethodDescriptor> discovered = new HashSet<MethodDescriptor>();

    LinkedList<MethodDescriptor> sorted = new LinkedList<MethodDescriptor>();

    Iterator<MethodDescriptor> itr = toSort.iterator();
    while (itr.hasNext()) {
      MethodDescriptor d = itr.next();

      if (!discovered.contains(d)) {
        dfsVisit(d, toSort, sorted, discovered);
      }
    }

    return sorted;
  }

  // While we're doing DFS on call graph, remember
  // dependencies for efficient queuing of methods
  // during interprocedural analysis:
  //
  // a dependent of a method decriptor d for this analysis is:
  // 1) a method or task that invokes d
  // 2) in the descriptorsToAnalyze set
  private void dfsVisit(MethodDescriptor md, Set<MethodDescriptor> toSort,
      LinkedList<MethodDescriptor> sorted, Set<MethodDescriptor> discovered) {

    discovered.add(md);

    Iterator itr2 = callgraph.getCalleeSet(md).iterator();
    while (itr2.hasNext()) {
      MethodDescriptor dCallee = (MethodDescriptor) itr2.next();
      addDependent(dCallee, md);
    }

    Iterator itr = callgraph.getCallerSet(md).iterator();
    while (itr.hasNext()) {
      MethodDescriptor dCaller = (MethodDescriptor) itr.next();
      // only consider callers in the original set to analyze
      if (!toSort.contains(dCaller)) {
        continue;
      }
      if (!discovered.contains(dCaller)) {
        addDependent(md, // callee
            dCaller // caller
        );

        dfsVisit(dCaller, toSort, sorted, discovered);
      }
    }

    // for leaf-nodes last now!
    sorted.addLast(md);
  }

  public void addDependent(MethodDescriptor callee, MethodDescriptor caller) {
    Set<MethodDescriptor> deps = mapDescriptorToSetDependents.get(callee);
    if (deps == null) {
      deps = new HashSet<MethodDescriptor>();
    }
    deps.add(caller);
    mapDescriptorToSetDependents.put(callee, deps);
  }

  public Set<MethodDescriptor> getDependents(MethodDescriptor callee) {
    Set<MethodDescriptor> deps = mapDescriptorToSetDependents.get(callee);
    if (deps == null) {
      deps = new HashSet<MethodDescriptor>();
      mapDescriptorToSetDependents.put(callee, deps);
    }
    return deps;
  }

}
