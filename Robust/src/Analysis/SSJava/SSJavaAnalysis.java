package Analysis.SSJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import Analysis.CallGraph.CallGraph;
import Analysis.Loops.LoopOptimize;
import Analysis.Loops.LoopTerminate;
import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.Descriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.TypeUtil;
import IR.Flat.BuildFlat;
import IR.Flat.FlatMethod;
import IR.Flat.TempDescriptor;
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

  State state;
  TypeUtil tu;
  FlowDownCheck flowDownChecker;
  MethodAnnotationCheck methodAnnotationChecker;

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

  CallGraph callgraph;

  public SSJavaAnalysis(State state, TypeUtil tu, CallGraph callgraph) {
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
  }

  public void doCheck() {
    doMethodAnnotationCheck();
    if (state.SSJAVADEBUG) {
      debugPrint();
    }
    parseLocationAnnotation();
    doFlowDownCheck();
    doDefinitelyWrittenCheck();
    doSingleReferenceCheck();
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
  }

  public void doFlowDownCheck() {
    flowDownChecker = new FlowDownCheck(this, state);
    flowDownChecker.flowDownCheck();
  }

  public void doDefinitelyWrittenCheck() {
    DefinitelyWrittenCheck checker = new DefinitelyWrittenCheck(this, state);
    checker.definitelyWrittenCheck();
  }

  public void doSingleReferenceCheck() {
    SingleReferenceCheck checker = new SingleReferenceCheck(this, state);
    checker.singleReferenceCheck();
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
              new SSJavaLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM);
          cd2lattice.put(cd, locOrder);
          parseClassLatticeDefinition(cd, an.getValue(), locOrder);

          if (state.SSJAVADEBUG) {
            // generate lattice dot file
            writeLatticeDotFile(cd, locOrder);
          }

        } else if (marker.equals(METHODDEFAULT)) {
          MethodLattice<String> locOrder =
              new MethodLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM);
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
                    new MethodLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM);
                md2lattice.put(md, locOrder);
                parseMethodDefaultLatticeDefinition(cd, an.getValue(), locOrder);
              } else if (an.getMarker().equals(TERMINATE)) {
                // developer explicitly wants to skip loop termination analysis
                String value = an.getValue();
                int maxIteration = 0;
                if (value != null) {
                  maxIteration = Integer.parseInt(value);
                }
                System.out.println("###md=" + md);
                skipLoopTerminate.put(md, new Integer(maxIteration));
              }
            }
          }
        }

      }

    }
  }

  private void writeLatticeDotFile(ClassDescriptor cd, SSJavaLattice<String> locOrder) {

    String className = cd.getSymbol().replaceAll("[\\W_]", "");

    Set<Pair<String, String>> pairSet = locOrder.getOrderingPairSet();

    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(className + ".dot"));

      bw.write("digraph " + className + " {\n");

      for (Iterator iterator = pairSet.iterator(); iterator.hasNext();) {
        // pair is in the form of <higher, lower>
        Pair<String, String> pair = (Pair<String, String>) iterator.next();

        String highLocId = pair.getFirst();
        if (locOrder.isSharedLoc(highLocId)) {
          highLocId = "\"" + highLocId + "*\"";
        }
        String lowLocId = pair.getSecond();
        if (locOrder.isSharedLoc(lowLocId)) {
          lowLocId = "\"" + lowLocId + "*\"";
        }
        bw.write(highLocId + " -> " + lowLocId + ";\n");
      }
      bw.write("}\n");
      bw.close();

    } catch (IOException e) {
      e.printStackTrace();
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
      } else if (orderElement.contains("*")) {
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
          + "' is not defined in the default local variable lattice at " + cd.getSourceFileName());
    }

    if (locOrder.getGlobalLoc() != null && !locOrder.containsKey(locOrder.getGlobalLoc())) {
      throw new Error("Variable global location '" + locOrder.getGlobalLoc()
          + "' is not defined in the default local variable lattice at " + cd.getSourceFileName());
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
      return cd2methodDefault.get(md.getClassDesc());
    }
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
    annotationRequireClassSet.add(cd);
    annotationRequireSet.add(md);
  }

  public Set<MethodDescriptor> getAnnotationRequireSet() {
    return annotationRequireSet;
  }

  public void doLoopTerminationCheck(LoopOptimize lo, FlatMethod fm) {
    LoopTerminate lt = new LoopTerminate();
    if (needTobeAnnotated(fm.getMethod())) {
      lt.terminateAnalysis(fm, lo.getLoopInvariant(fm));
    }
  }

  public void doLoopTerminationCheck(LoopOptimize lo) {
    LoopTerminate lt = new LoopTerminate();
    for (Iterator iterator = annotationRequireSet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      if (!skipLoopTerminate.containsKey(md)) {
        FlatMethod fm = state.getMethodFlat(md);
        lt.terminateAnalysis(fm, lo.getLoopInvariant(fm));
      }
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

}
