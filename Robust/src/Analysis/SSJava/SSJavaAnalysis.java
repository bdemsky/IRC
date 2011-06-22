package Analysis.SSJava;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import Analysis.Loops.LoopOptimize;
import Analysis.Loops.LoopTerminate;
import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.TypeUtil;
import IR.Flat.FlatMethod;

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

  // if a method has annotations, the mapping has true
  Hashtable<MethodDescriptor, Boolean> md2needAnnotation;

  // class -> field lattice
  Hashtable<ClassDescriptor, SSJavaLattice<String>> cd2lattice;

  // class -> default local variable lattice
  Hashtable<ClassDescriptor, MethodLattice<String>> cd2methodDefault;

  // method -> local variable lattice
  Hashtable<MethodDescriptor, MethodLattice<String>> md2lattice;

  // method set that does not have loop termination analysis
  Hashtable<MethodDescriptor, Integer> skipLoopTerminate;

  public SSJavaAnalysis(State state, TypeUtil tu) {
    this.state = state;
    this.tu = tu;
    this.cd2lattice = new Hashtable<ClassDescriptor, SSJavaLattice<String>>();
    this.cd2methodDefault = new Hashtable<ClassDescriptor, MethodLattice<String>>();
    this.md2lattice = new Hashtable<MethodDescriptor, MethodLattice<String>>();
    this.md2needAnnotation = new Hashtable<MethodDescriptor, Boolean>();
    this.skipLoopTerminate = new Hashtable<MethodDescriptor, Integer>();
  }

  public void doCheck() {
    doMethodAnnotationCheck();
    parseLocationAnnotation();
    doFlowDownCheck();
    doDefinitelyWrittenCheck();
    doSingleReferenceCheck();
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
    DefinitelyWrittenCheck checker = new DefinitelyWrittenCheck(state);
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
        } else if (marker.equals(METHODDEFAULT)) {
          MethodLattice<String> locOrder =
              new MethodLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM);
          cd2methodDefault.put(cd, locOrder);
          parseMethodLatticeDefinition(cd, an.getValue(), locOrder);
        }
      }

      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        // parsing location hierarchy declaration for the method

        if (needAnnotation(md)) {
          Vector<AnnotationDescriptor> methodAnnotations = md.getModifiers().getAnnotations();
          if (methodAnnotations != null) {
            for (int i = 0; i < methodAnnotations.size(); i++) {
              AnnotationDescriptor an = methodAnnotations.elementAt(i);
              if (an.getMarker().equals(LATTICE)) {
                // developer explicitly defines method lattice
                MethodLattice<String> locOrder =
                    new MethodLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM);
                md2lattice.put(md, locOrder);
                parseMethodLatticeDefinition(cd, an.getValue(), locOrder);
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

  private void parseMethodLatticeDefinition(ClassDescriptor cd, String value,
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
      } else if (orderElement.contains("*")) {
        // spin loc definition
        locOrder.addSpinLoc(orderElement.substring(0, orderElement.length() - 1));
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
        locOrder.addSpinLoc(orderElement.substring(0, orderElement.length() - 1));
      } else {
        // single element
        locOrder.put(orderElement);
      }
    }

    // sanity check
    Set<String> spinLocSet = locOrder.getSpinLocSet();
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

  public MethodLattice<String> getMethodLattice(MethodDescriptor md) {
    if (md2lattice.containsKey(md)) {
      return md2lattice.get(md);
    } else {
      return cd2methodDefault.get(md.getClassDesc());
    }
  }

  public boolean needAnnotation(MethodDescriptor md) {
    return md2needAnnotation.containsKey(md);
  }

  public void putNeedAnnotation(MethodDescriptor md) {
    md2needAnnotation.put(md, new Boolean(true));
  }

  public Hashtable<MethodDescriptor, Boolean> getMd2hasAnnotation() {
    return md2needAnnotation;
  }

  public void doLoopTerminationCheck(LoopOptimize lo) {
    LoopTerminate lt = new LoopTerminate();
    Set<MethodDescriptor> mdSet = md2needAnnotation.keySet();
    for (Iterator iterator = mdSet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      if (!skipLoopTerminate.containsKey(md)) {
        FlatMethod fm = state.getMethodFlat(md);
        lt.terminateAnalysis(fm, lo.getLoopInvariant(fm));
      }
    }

  }

}
