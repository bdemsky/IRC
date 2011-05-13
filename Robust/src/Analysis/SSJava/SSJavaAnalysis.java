package Analysis.SSJava;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.State;

public class SSJavaAnalysis {

  public static final String LATTICE = "LATTICE";
  public static final String METHODDEFAULT = "METHODDEFAULT";
  public static final String THISLOC = "THISLOC";
  public static final String GLOBALLOC = "GLOBALLOC";
  public static final String LOC = "LOC";
  public static final String DELTA = "delta";
  State state;
  FlowDownCheck flowDownChecker;

  // class -> field lattice
  Hashtable<ClassDescriptor, SSJavaLattice<String>> cd2lattice;

  // class -> default local variable lattice
  Hashtable<ClassDescriptor, MethodLattice<String>> cd2methodDefault;

  // method -> local variable lattice
  Hashtable<MethodDescriptor, MethodLattice<String>> md2lattice;

  public SSJavaAnalysis(State state) {
    this.state = state;
    cd2lattice = new Hashtable<ClassDescriptor, SSJavaLattice<String>>();
    cd2methodDefault = new Hashtable<ClassDescriptor, MethodLattice<String>>();
    md2lattice = new Hashtable<MethodDescriptor, MethodLattice<String>>();
  }

  public void doCheck() {
    parseLocationAnnotation();
    doFlowDownCheck();
    doLoopCheck();
  }

  public void doFlowDownCheck() {
    flowDownChecker = new FlowDownCheck(this, state);
    flowDownChecker.flowDownCheck();
  }

  public void doLoopCheck() {
    DefinitelyWrittenCheck checker = new DefinitelyWrittenCheck(state);
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
          SSJavaLattice<String> locOrder = new SSJavaLattice<String>("_top_", "_bottom_");
          cd2lattice.put(cd, locOrder);
          parseClassLatticeDefinition(cd, an.getValue(), locOrder);
        } else if (marker.equals(METHODDEFAULT)) {
          MethodLattice<String> locOrder = new MethodLattice<String>("_top_", "_bottom_");
          cd2methodDefault.put(cd, locOrder);
          parseMethodLatticeDefinition(cd, an.getValue(), locOrder);
        }
      }
      if (!cd2lattice.containsKey(cd)) {
        throw new Error("Class " + cd.getSymbol()
            + " doesn't have any location hierarchy declaration at " + cd.getSourceFileName());
      }

      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        // parsing location hierarchy declaration for the method
        Vector<AnnotationDescriptor> methodAnnotations = cd.getModifier().getAnnotations();
        for (int i = 0; i < methodAnnotations.size(); i++) {
          AnnotationDescriptor an = methodAnnotations.elementAt(i);
          if (an.getMarker().equals(LATTICE)) {
            MethodLattice<String> locOrder = new MethodLattice<String>("_top_", "_bottom_");
            cd2lattice.put(cd, locOrder);
            parseMethodLatticeDefinition(cd, an.getValue(), locOrder);
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
        locOrder.addSpinLoc(orderElement);
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
        locOrder.addSpinLoc(orderElement);
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

}
