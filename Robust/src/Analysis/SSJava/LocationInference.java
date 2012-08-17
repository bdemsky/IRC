package Analysis.SSJava;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;
import IR.Operation;
import IR.State;
import IR.SymbolTable;
import IR.TypeDescriptor;
import IR.VarDescriptor;
import IR.Tree.ArrayAccessNode;
import IR.Tree.AssignmentNode;
import IR.Tree.BlockExpressionNode;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.CastNode;
import IR.Tree.CreateObjectNode;
import IR.Tree.DeclarationNode;
import IR.Tree.ExpressionNode;
import IR.Tree.FieldAccessNode;
import IR.Tree.IfStatementNode;
import IR.Tree.Kind;
import IR.Tree.LiteralNode;
import IR.Tree.LoopNode;
import IR.Tree.MethodInvokeNode;
import IR.Tree.NameNode;
import IR.Tree.OpNode;
import IR.Tree.ReturnNode;
import IR.Tree.SubBlockNode;
import IR.Tree.SwitchStatementNode;
import IR.Tree.TertiaryNode;
import IR.Tree.TreeNode;
import Util.Pair;

public class LocationInference {

  State state;
  SSJavaAnalysis ssjava;

  List<ClassDescriptor> toanalyzeList;
  List<MethodDescriptor> toanalyzeMethodList;
  Map<MethodDescriptor, FlowGraph> mapMethodDescriptorToFlowGraph;

  // map a method descriptor to its set of parameter descriptors
  Map<MethodDescriptor, Set<Descriptor>> mapMethodDescriptorToParamDescSet;

  // keep current descriptors to visit in fixed-point interprocedural analysis,
  private Stack<MethodDescriptor> methodDescriptorsToVisitStack;

  // map a class descriptor to a field lattice
  private Map<ClassDescriptor, SSJavaLattice<String>> cd2lattice;

  // map a method descriptor to a method lattice
  private Map<MethodDescriptor, SSJavaLattice<String>> md2lattice;

  // map a method descriptor to the set of method invocation nodes which are
  // invoked by the method descriptor
  private Map<MethodDescriptor, Set<MethodInvokeNode>> mapMethodDescriptorToMethodInvokeNodeSet;

  private Map<MethodInvokeNode, Map<Integer, NodeTupleSet>> mapMethodInvokeNodeToArgIdxMap;

  private Map<MethodDescriptor, MethodLocationInfo> mapMethodDescToMethodLocationInfo;

  private Map<ClassDescriptor, LocationInfo> mapClassToLocationInfo;

  private Map<MethodDescriptor, Set<MethodDescriptor>> mapMethodToCalleeSet;

  private Map<MethodDescriptor, Set<FlowNode>> mapMethodDescToParamNodeFlowsToReturnValue;

  private Map<String, Vector<String>> mapFileNameToLineVector;

  private Map<Descriptor, Integer> mapDescToDefinitionLine;

  public static final String GLOBALLOC = "GLOBALLOC";

  public static final String TOPLOC = "TOPLOC";

  public static final Descriptor GLOBALDESC = new NameDescriptor(GLOBALLOC);

  public static final Descriptor TOPDESC = new NameDescriptor(TOPLOC);

  public static String newline = System.getProperty("line.separator");

  LocationInfo curMethodInfo;

  boolean debug = true;

  public LocationInference(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.toanalyzeList = new ArrayList<ClassDescriptor>();
    this.toanalyzeMethodList = new ArrayList<MethodDescriptor>();
    this.mapMethodDescriptorToFlowGraph = new HashMap<MethodDescriptor, FlowGraph>();
    this.cd2lattice = new HashMap<ClassDescriptor, SSJavaLattice<String>>();
    this.md2lattice = new HashMap<MethodDescriptor, SSJavaLattice<String>>();
    this.methodDescriptorsToVisitStack = new Stack<MethodDescriptor>();
    this.mapMethodDescriptorToMethodInvokeNodeSet =
        new HashMap<MethodDescriptor, Set<MethodInvokeNode>>();
    this.mapMethodInvokeNodeToArgIdxMap =
        new HashMap<MethodInvokeNode, Map<Integer, NodeTupleSet>>();
    this.mapMethodDescToMethodLocationInfo = new HashMap<MethodDescriptor, MethodLocationInfo>();
    this.mapMethodToCalleeSet = new HashMap<MethodDescriptor, Set<MethodDescriptor>>();
    this.mapClassToLocationInfo = new HashMap<ClassDescriptor, LocationInfo>();

    this.mapFileNameToLineVector = new HashMap<String, Vector<String>>();
    this.mapDescToDefinitionLine = new HashMap<Descriptor, Integer>();
    this.mapMethodDescToParamNodeFlowsToReturnValue =
        new HashMap<MethodDescriptor, Set<FlowNode>>();
  }

  public void setupToAnalyze() {
    SymbolTable classtable = state.getClassSymbolTable();
    toanalyzeList.clear();
    toanalyzeList.addAll(classtable.getValueSet());
    // Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
    // public int compare(ClassDescriptor o1, ClassDescriptor o2) {
    // return o1.getClassName().compareToIgnoreCase(o2.getClassName());
    // }
    // });
  }

  public void setupToAnalazeMethod(ClassDescriptor cd) {

    SymbolTable methodtable = cd.getMethodTable();
    toanalyzeMethodList.clear();
    toanalyzeMethodList.addAll(methodtable.getValueSet());
    Collections.sort(toanalyzeMethodList, new Comparator<MethodDescriptor>() {
      public int compare(MethodDescriptor o1, MethodDescriptor o2) {
        return o1.getSymbol().compareToIgnoreCase(o2.getSymbol());
      }
    });
  }

  public boolean toAnalyzeMethodIsEmpty() {
    return toanalyzeMethodList.isEmpty();
  }

  public boolean toAnalyzeIsEmpty() {
    return toanalyzeList.isEmpty();
  }

  public ClassDescriptor toAnalyzeNext() {
    return toanalyzeList.remove(0);
  }

  public MethodDescriptor toAnalyzeMethodNext() {
    return toanalyzeMethodList.remove(0);
  }

  public void inference() {

    // 1) construct value flow graph
    constructFlowGraph();

    // 2) construct lattices
    inferLattices();

    simplifyLattices();

    debug_writeLatticeDotFile();

    // 3) check properties
    checkLattices();

    // 4) generate annotated source codes
    generateAnnoatedCode();

  }

  private void addMapClassDefinitionToLineNum(ClassDescriptor cd, String strLine, int lineNum) {

    String classSymbol = cd.getSymbol();
    int idx = classSymbol.lastIndexOf("$");
    if (idx != -1) {
      classSymbol = classSymbol.substring(idx + 1);
    }

    String pattern = "class " + classSymbol + " ";
    if (strLine.indexOf(pattern) != -1) {
      mapDescToDefinitionLine.put(cd, lineNum);
    }
  }

  private void addMapMethodDefinitionToLineNum(Set<MethodDescriptor> methodSet, String strLine,
      int lineNum) {
    for (Iterator iterator = methodSet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      String pattern = md.getMethodDeclaration();
      if (strLine.indexOf(pattern) != -1) {
        mapDescToDefinitionLine.put(md, lineNum);
        methodSet.remove(md);
        return;
      }
    }

  }

  private void readOriginalSourceFiles() {

    SymbolTable classtable = state.getClassSymbolTable();

    Set<ClassDescriptor> classDescSet = new HashSet<ClassDescriptor>();
    classDescSet.addAll(classtable.getValueSet());

    try {
      // inefficient implement. it may re-visit the same file if the file
      // contains more than one class definitions.
      for (Iterator iterator = classDescSet.iterator(); iterator.hasNext();) {
        ClassDescriptor cd = (ClassDescriptor) iterator.next();

        Set<MethodDescriptor> methodSet = new HashSet<MethodDescriptor>();
        methodSet.addAll(cd.getMethodTable().getValueSet());

        String sourceFileName = cd.getSourceFileName();
        Vector<String> lineVec = new Vector<String>();

        mapFileNameToLineVector.put(sourceFileName, lineVec);

        BufferedReader in = new BufferedReader(new FileReader(sourceFileName));
        String strLine;
        int lineNum = 1;
        lineVec.add(""); // the index is started from 1.
        while ((strLine = in.readLine()) != null) {
          lineVec.add(lineNum, strLine);
          addMapClassDefinitionToLineNum(cd, strLine, lineNum);
          addMapMethodDefinitionToLineNum(methodSet, strLine, lineNum);
          lineNum++;
        }

      }

    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private String generateLatticeDefinition(Descriptor desc) {

    Set<String> sharedLocSet = new HashSet<String>();

    SSJavaLattice<String> lattice = getLattice(desc);
    String rtr = "@LATTICE(\"";

    Map<String, Set<String>> map = lattice.getTable();
    Set<String> keySet = map.keySet();
    boolean first = true;
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      String key = (String) iterator.next();
      if (!key.equals(lattice.getTopItem())) {
        Set<String> connectedSet = map.get(key);

        if (connectedSet.size() == 1) {
          if (connectedSet.iterator().next().equals(lattice.getBottomItem())) {
            if (!first) {
              rtr += ",";
            } else {
              rtr += "LOC,";
              first = false;
            }
            rtr += key;
            if (lattice.isSharedLoc(key)) {
              rtr += "," + key + "*";
            }
          }
        }

        for (Iterator iterator2 = connectedSet.iterator(); iterator2.hasNext();) {
          String loc = (String) iterator2.next();
          if (!loc.equals(lattice.getBottomItem())) {
            if (!first) {
              rtr += ",";
            } else {
              rtr += "LOC,";
              first = false;
            }
            rtr += loc + "<" + key;
            if (lattice.isSharedLoc(key) && (!sharedLocSet.contains(key))) {
              rtr += "," + key + "*";
              sharedLocSet.add(key);
            }
            if (lattice.isSharedLoc(loc) && (!sharedLocSet.contains(loc))) {
              rtr += "," + loc + "*";
              sharedLocSet.add(loc);
            }

          }
        }
      }
    }

    rtr += "\")";

    if (desc instanceof MethodDescriptor) {
      TypeDescriptor returnType = ((MethodDescriptor) desc).getReturnType();

      MethodLocationInfo methodLocInfo = getMethodLocationInfo((MethodDescriptor) desc);

      if (returnType != null && (!returnType.isVoid())) {
        rtr +=
            "\n@RETURNLOC(\"" + generateLocationAnnoatation(methodLocInfo.getReturnLoc()) + "\")";
      }

      rtr += "\n@THISLOC(\"this\")";
      rtr += "\n@GLOBALLOC(\"GLOBALLOC\")";

      CompositeLocation pcLoc = methodLocInfo.getPCLoc();
      if (pcLoc != null) {
        rtr += "\n@PCLOC(\"" + generateLocationAnnoatation(pcLoc) + "\")";
      }

    }

    return rtr;
  }

  private void generateAnnoatedCode() {

    readOriginalSourceFiles();

    setupToAnalyze();
    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);

      LocationInfo locInfo = mapClassToLocationInfo.get(cd);
      String sourceFileName = cd.getSourceFileName();

      if (cd.isInterface()) {
        continue;
      }

      int classDefLine = mapDescToDefinitionLine.get(cd);
      Vector<String> sourceVec = mapFileNameToLineVector.get(sourceFileName);

      if (locInfo == null) {
        locInfo = getLocationInfo(cd);
      }

      for (Iterator iter = cd.getFields(); iter.hasNext();) {
        FieldDescriptor fieldDesc = (FieldDescriptor) iter.next();
        if (!(fieldDesc.isStatic() && fieldDesc.isFinal())) {
          String locIdentifier = locInfo.getFieldInferLocation(fieldDesc).getLocIdentifier();
          if (!getLattice(cd).getElementSet().contains(locIdentifier)) {
            getLattice(cd).put(locIdentifier);
          }
        }
      }

      String fieldLatticeDefStr = generateLatticeDefinition(cd);
      String annoatedSrc = fieldLatticeDefStr + newline + sourceVec.get(classDefLine);
      sourceVec.set(classDefLine, annoatedSrc);

      // generate annotations for field declarations
      LocationInfo fieldLocInfo = getLocationInfo(cd);
      Map<Descriptor, CompositeLocation> inferLocMap = fieldLocInfo.getMapDescToInferLocation();

      for (Iterator iter = cd.getFields(); iter.hasNext();) {
        FieldDescriptor fd = (FieldDescriptor) iter.next();

        String locAnnotationStr;
        CompositeLocation inferLoc = inferLocMap.get(fd);

        if (inferLoc != null) {
          // infer loc is null if the corresponding field is static and final
          locAnnotationStr = "@LOC(\"" + generateLocationAnnoatation(inferLoc) + "\")";
          int fdLineNum = fd.getLineNum();
          String orgFieldDeclarationStr = sourceVec.get(fdLineNum);
          String fieldDeclaration = fd.toString();
          fieldDeclaration = fieldDeclaration.substring(0, fieldDeclaration.length() - 1);
          String annoatedStr = locAnnotationStr + " " + orgFieldDeclarationStr;
          sourceVec.set(fdLineNum, annoatedStr);
        }

      }

      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();

        if (!ssjava.needTobeAnnotated(md)) {
          continue;
        }

        SSJavaLattice<String> methodLattice = md2lattice.get(md);
        if (methodLattice != null) {

          int methodDefLine = md.getLineNum();

          MethodLocationInfo methodLocInfo = getMethodLocationInfo(md);

          Map<Descriptor, CompositeLocation> methodInferLocMap =
              methodLocInfo.getMapDescToInferLocation();
          Set<Descriptor> localVarDescSet = methodInferLocMap.keySet();

          Set<String> localLocElementSet = methodLattice.getElementSet();

          for (Iterator iterator = localVarDescSet.iterator(); iterator.hasNext();) {
            Descriptor localVarDesc = (Descriptor) iterator.next();
            CompositeLocation inferLoc = methodInferLocMap.get(localVarDesc);

            String localLocIdentifier = inferLoc.get(0).getLocIdentifier();
            if (!localLocElementSet.contains(localLocIdentifier)) {
              methodLattice.put(localLocIdentifier);
            }

            String locAnnotationStr = "@LOC(\"" + generateLocationAnnoatation(inferLoc) + "\")";

            if (!isParameter(md, localVarDesc)) {
              if (mapDescToDefinitionLine.containsKey(localVarDesc)) {
                int varLineNum = mapDescToDefinitionLine.get(localVarDesc);
                String orgSourceLine = sourceVec.get(varLineNum);
                int idx =
                    orgSourceLine.indexOf(generateVarDeclaration((VarDescriptor) localVarDesc));
                assert (idx != -1);
                String annoatedStr =
                    orgSourceLine.substring(0, idx) + locAnnotationStr + " "
                        + orgSourceLine.substring(idx);
                sourceVec.set(varLineNum, annoatedStr);
              }
            } else {
              String methodDefStr = sourceVec.get(methodDefLine);

              int idx =
                  getParamLocation(methodDefStr,
                      generateVarDeclaration((VarDescriptor) localVarDesc));

              assert (idx != -1);

              String annoatedStr =
                  methodDefStr.substring(0, idx) + locAnnotationStr + " "
                      + methodDefStr.substring(idx);
              sourceVec.set(methodDefLine, annoatedStr);
            }

          }

          // check if the lattice has to have the location type for the this
          // reference...

          // boolean needToAddthisRef = hasThisReference(md);
          if (localLocElementSet.contains("this")) {
            methodLattice.put("this");
          }

          String methodLatticeDefStr = generateLatticeDefinition(md);
          String annoatedStr = methodLatticeDefStr + newline + sourceVec.get(methodDefLine);
          sourceVec.set(methodDefLine, annoatedStr);

        }
      }

    }

    codeGen();
  }

  private boolean hasThisReference(MethodDescriptor md) {

    FlowGraph fg = getFlowGraph(md);
    Set<FlowNode> nodeSet = fg.getNodeSet();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode flowNode = (FlowNode) iterator.next();
      if (flowNode.getDescTuple().get(0).equals(md.getThis())) {
        return true;
      }
    }

    return false;
  }

  private int getParamLocation(String methodStr, String paramStr) {

    String pattern = paramStr + ",";

    int idx = methodStr.indexOf(pattern);
    if (idx != -1) {
      return idx;
    } else {
      pattern = paramStr + ")";
      return methodStr.indexOf(pattern);
    }

  }

  private String generateVarDeclaration(VarDescriptor varDesc) {

    TypeDescriptor td = varDesc.getType();
    String rtr = td.toString();
    if (td.isArray()) {
      for (int i = 0; i < td.getArrayCount(); i++) {
        rtr += "[]";
      }
    }
    rtr += " " + varDesc.getName();
    return rtr;

  }

  private String generateLocationAnnoatation(CompositeLocation loc) {
    String rtr = "";
    // method location
    Location methodLoc = loc.get(0);
    rtr += methodLoc.getLocIdentifier();

    for (int i = 1; i < loc.getSize(); i++) {
      Location element = loc.get(i);
      rtr += "," + element.getDescriptor().getSymbol() + "." + element.getLocIdentifier();
    }

    return rtr;
  }

  private boolean isParameter(MethodDescriptor md, Descriptor localVarDesc) {
    return getFlowGraph(md).isParamDesc(localVarDesc);
  }

  private String extractFileName(String fileName) {
    int idx = fileName.lastIndexOf("/");
    if (idx == -1) {
      return fileName;
    } else {
      return fileName.substring(idx + 1);
    }

  }

  private void codeGen() {

    Set<String> originalFileNameSet = mapFileNameToLineVector.keySet();
    for (Iterator iterator = originalFileNameSet.iterator(); iterator.hasNext();) {
      String orgFileName = (String) iterator.next();
      String outputFileName = extractFileName(orgFileName);

      Vector<String> sourceVec = mapFileNameToLineVector.get(orgFileName);

      try {

        FileWriter fileWriter = new FileWriter("./infer/" + outputFileName);
        BufferedWriter out = new BufferedWriter(fileWriter);

        for (int i = 0; i < sourceVec.size(); i++) {
          out.write(sourceVec.get(i));
          out.newLine();
        }
        out.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

    }

  }

  private void simplifyLattices() {

    // generate lattice dot file
    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);

      SSJavaLattice<String> classLattice = cd2lattice.get(cd);
      if (classLattice != null) {
        classLattice.removeRedundantEdges();
      }

      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        SSJavaLattice<String> methodLattice = md2lattice.get(md);
        if (methodLattice != null) {
          methodLattice.removeRedundantEdges();
        }
      }
    }

  }

  private void checkLattices() {

    LinkedList<MethodDescriptor> descriptorListToAnalyze = ssjava.getSortedDescriptors();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    // descriptorListToAnalyze.removeFirst();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(descriptorListToAnalyze);

    while (!descriptorListToAnalyze.isEmpty()) {
      MethodDescriptor md = descriptorListToAnalyze.removeFirst();
      checkLatticesOfVirtualMethods(md);
    }

  }

  private void debug_writeLatticeDotFile() {
    // generate lattice dot file

    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);

      SSJavaLattice<String> classLattice = cd2lattice.get(cd);
      if (classLattice != null) {
        ssjava.writeLatticeDotFile(cd, null, classLattice);
        debug_printDescriptorToLocNameMapping(cd);
      }

      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        SSJavaLattice<String> methodLattice = md2lattice.get(md);
        if (methodLattice != null) {
          ssjava.writeLatticeDotFile(cd, md, methodLattice);
          debug_printDescriptorToLocNameMapping(md);
        }
      }
    }

  }

  private void debug_printDescriptorToLocNameMapping(Descriptor desc) {

    LocationInfo info = getLocationInfo(desc);
    System.out.println("## " + desc + " ##");
    System.out.println(info.getMapDescToInferLocation());
    LocationInfo locInfo = getLocationInfo(desc);
    System.out.println("mapping=" + locInfo.getMapLocSymbolToDescSet());
    System.out.println("###################");

  }

  private void inferLattices() {

    // do fixed-point analysis

    ssjava.init();
    LinkedList<MethodDescriptor> descriptorListToAnalyze = ssjava.getSortedDescriptors();

    // Collections.sort(descriptorListToAnalyze, new
    // Comparator<MethodDescriptor>() {
    // public int compare(MethodDescriptor o1, MethodDescriptor o2) {
    // return o1.getSymbol().compareToIgnoreCase(o2.getSymbol());
    // }
    // });

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    // descriptorListToAnalyze.removeFirst();

    Set<MethodDescriptor> methodDescriptorToVistSet = new HashSet<MethodDescriptor>();
    methodDescriptorToVistSet.addAll(descriptorListToAnalyze);

    while (!descriptorListToAnalyze.isEmpty()) {
      MethodDescriptor md = descriptorListToAnalyze.removeFirst();
      methodDescriptorsToVisitStack.add(md);
    }

    // analyze scheduled methods until there are no more to visit
    while (!methodDescriptorsToVisitStack.isEmpty()) {
      // start to analyze leaf node
      MethodDescriptor md = methodDescriptorsToVisitStack.pop();

      SSJavaLattice<String> methodLattice =
          new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM);

      MethodLocationInfo methodInfo = new MethodLocationInfo(md);
      curMethodInfo = methodInfo;

      System.out.println();
      System.out.println("SSJAVA: Inferencing the lattice from " + md);

      try {
        analyzeMethodLattice(md, methodLattice, methodInfo);
      } catch (CyclicFlowException e) {
        throw new Error("Fail to generate the method lattice for " + md);
      }

      SSJavaLattice<String> prevMethodLattice = getMethodLattice(md);
      MethodLocationInfo prevMethodInfo = getMethodLocationInfo(md);

      if ((!methodLattice.equals(prevMethodLattice)) || (!methodInfo.equals(prevMethodInfo))) {

        setMethodLattice(md, methodLattice);
        setMethodLocInfo(md, methodInfo);

        // results for callee changed, so enqueue dependents caller for
        // further analysis
        Iterator<MethodDescriptor> depsItr = ssjava.getDependents(md).iterator();
        while (depsItr.hasNext()) {
          MethodDescriptor methodNext = depsItr.next();
          if (!methodDescriptorsToVisitStack.contains(methodNext)
              && methodDescriptorToVistSet.contains(methodNext)) {
            methodDescriptorsToVisitStack.add(methodNext);
          }
        }

      }

    }

    descriptorListToAnalyze = ssjava.getSortedDescriptors();
    for (Iterator iterator = descriptorListToAnalyze.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      calculateExtraLocations(md);
    }

  }

  private void setMethodLocInfo(MethodDescriptor md, MethodLocationInfo methodInfo) {
    mapMethodDescToMethodLocationInfo.put(md, methodInfo);
  }

  private void checkLatticesOfVirtualMethods(MethodDescriptor md) {

    if (!md.isStatic()) {
      Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
      setPossibleCallees.addAll(ssjava.getCallGraph().getMethods(md));

      for (Iterator iterator = setPossibleCallees.iterator(); iterator.hasNext();) {
        MethodDescriptor mdCallee = (MethodDescriptor) iterator.next();
        if (!md.equals(mdCallee)) {
          checkConsistency(md, mdCallee);
        }
      }

    }

  }

  private void checkConsistency(MethodDescriptor md1, MethodDescriptor md2) {

    // check that two lattice have the same relations between parameters(+PC
    // LOC, GLOBAL_LOC RETURN LOC)

    List<CompositeLocation> list1 = new ArrayList<CompositeLocation>();
    List<CompositeLocation> list2 = new ArrayList<CompositeLocation>();

    MethodLocationInfo locInfo1 = getMethodLocationInfo(md1);
    MethodLocationInfo locInfo2 = getMethodLocationInfo(md2);

    Map<Integer, CompositeLocation> paramMap1 = locInfo1.getMapParamIdxToInferLoc();
    Map<Integer, CompositeLocation> paramMap2 = locInfo2.getMapParamIdxToInferLoc();

    int numParam = locInfo1.getMapParamIdxToInferLoc().keySet().size();

    // add location types of paramters
    for (int idx = 0; idx < numParam; idx++) {
      list1.add(paramMap1.get(Integer.valueOf(idx)));
      list2.add(paramMap2.get(Integer.valueOf(idx)));
    }

    // add program counter location
    list1.add(locInfo1.getPCLoc());
    list2.add(locInfo2.getPCLoc());

    if (!md1.getReturnType().isVoid()) {
      // add return value location
      CompositeLocation rtrLoc1 = getMethodLocationInfo(md1).getReturnLoc();
      CompositeLocation rtrLoc2 = getMethodLocationInfo(md2).getReturnLoc();
      list1.add(rtrLoc1);
      list2.add(rtrLoc2);
    }

    // add global location type
    if (md1.isStatic()) {
      CompositeLocation globalLoc1 =
          new CompositeLocation(new Location(md1, locInfo1.getGlobalLocName()));
      CompositeLocation globalLoc2 =
          new CompositeLocation(new Location(md2, locInfo2.getGlobalLocName()));
      list1.add(globalLoc1);
      list2.add(globalLoc2);
    }

    for (int i = 0; i < list1.size(); i++) {
      CompositeLocation locA1 = list1.get(i);
      CompositeLocation locA2 = list2.get(i);
      for (int k = 0; k < list1.size(); k++) {
        if (i != k) {
          CompositeLocation locB1 = list1.get(k);
          CompositeLocation locB2 = list2.get(k);
          boolean r1 = isGreaterThan(getLattice(md1), locA1, locB1);

          boolean r2 = isGreaterThan(getLattice(md1), locA2, locB2);

          if (r1 != r2) {
            throw new Error("The method " + md1 + " is not consistent with the method " + md2
                + ".:: They have a different ordering relation between locations (" + locA1 + ","
                + locB1 + ") and (" + locA2 + "," + locB2 + ").");
          }
        }
      }
    }

  }

  private String getSymbol(int idx, FlowNode node) {
    Descriptor desc = node.getDescTuple().get(idx);
    return desc.getSymbol();
  }

  private Descriptor getDescriptor(int idx, FlowNode node) {
    Descriptor desc = node.getDescTuple().get(idx);
    return desc;
  }

  private void analyzeMethodLattice(MethodDescriptor md, SSJavaLattice<String> methodLattice,
      MethodLocationInfo methodInfo) throws CyclicFlowException {

    // first take a look at method invocation nodes to newly added relations
    // from the callee
    analyzeLatticeMethodInvocationNode(md, methodLattice, methodInfo);

    if (!md.isStatic()) {
      // set the this location
      String thisLocSymbol = md.getThis().getSymbol();
      methodInfo.setThisLocName(thisLocSymbol);
    }

    // set the global location
    methodInfo.setGlobalLocName(LocationInference.GLOBALLOC);
    methodInfo.mapDescriptorToLocation(GLOBALDESC, new CompositeLocation(
        new Location(md, GLOBALLOC)));

    // visit each node of method flow graph
    FlowGraph fg = getFlowGraph(md);
    Set<FlowNode> nodeSet = fg.getNodeSet();

    // for the method lattice, we need to look at the first element of
    // NTuple<Descriptor>
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode srcNode = (FlowNode) iterator.next();

      Set<FlowEdge> outEdgeSet = srcNode.getOutEdgeSet();
      for (Iterator iterator2 = outEdgeSet.iterator(); iterator2.hasNext();) {
        FlowEdge outEdge = (FlowEdge) iterator2.next();
        FlowNode dstNode = outEdge.getDst();

        NTuple<Descriptor> srcNodeTuple = srcNode.getDescTuple();
        NTuple<Descriptor> dstNodeTuple = dstNode.getDescTuple();

        if (outEdge.getInitTuple().equals(srcNodeTuple)
            && outEdge.getEndTuple().equals(dstNodeTuple)) {

          if ((srcNodeTuple.size() > 1 && dstNodeTuple.size() > 1)
              && srcNodeTuple.get(0).equals(dstNodeTuple.get(0))) {

            // value flows between fields
            Descriptor desc = srcNodeTuple.get(0);
            ClassDescriptor classDesc;

            if (desc.equals(GLOBALDESC)) {
              classDesc = md.getClassDesc();
            } else {
              VarDescriptor varDesc = (VarDescriptor) srcNodeTuple.get(0);
              classDesc = varDesc.getType().getClassDesc();
            }
            extractRelationFromFieldFlows(classDesc, srcNode, dstNode, 1);

          } else {
            // value flow between local var - local var or local var - field
            addRelationToLattice(md, methodLattice, methodInfo, srcNode, dstNode);
          }
        }
      }
    }

    // create mapping from param idx to inferred composite location

    int offset;
    if (!md.isStatic()) {
      // add 'this' reference location
      offset = 1;
      methodInfo.addMapParamIdxToInferLoc(0, methodInfo.getInferLocation(md.getThis()));
    } else {
      offset = 0;
    }

    for (int idx = 0; idx < md.numParameters(); idx++) {
      Descriptor paramDesc = md.getParameter(idx);
      CompositeLocation inferParamLoc = methodInfo.getInferLocation(paramDesc);
      methodInfo.addMapParamIdxToInferLoc(idx + offset, inferParamLoc);
    }

  }

  private void calculateExtraLocations(MethodDescriptor md) {
    // calcualte pcloc, returnloc,...

    SSJavaLattice<String> methodLattice = getMethodLattice(md);
    MethodLocationInfo methodInfo = getMethodLocationInfo(md);
    FlowGraph fg = getFlowGraph(md);
    Set<FlowNode> nodeSet = fg.getNodeSet();

    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode flowNode = (FlowNode) iterator.next();
      if (flowNode.isDeclaratonNode()) {
        CompositeLocation inferLoc = methodInfo.getInferLocation(flowNode.getDescTuple().get(0));
        String locIdentifier = inferLoc.get(0).getLocIdentifier();
        if (!methodLattice.containsKey(locIdentifier)) {
          methodLattice.put(locIdentifier);
        }

      }
    }

    Map<Integer, CompositeLocation> mapParamToLoc = methodInfo.getMapParamIdxToInferLoc();
    Set<Integer> paramIdxSet = mapParamToLoc.keySet();

    try {
      if (!ssjava.getMethodContainingSSJavaLoop().equals(md)) {
        // calculate the initial program counter location
        // PC location is higher than location types of all parameters
        String pcLocSymbol = "PCLOC";

        Set<CompositeLocation> paramInFlowSet = new HashSet<CompositeLocation>();

        for (Iterator iterator = paramIdxSet.iterator(); iterator.hasNext();) {
          Integer paramIdx = (Integer) iterator.next();

          FlowNode paramFlowNode = fg.getParamFlowNode(paramIdx);

          if (fg.getIncomingFlowNodeSet(paramFlowNode).size() > 0) {
            // parameter has in-value flows
            CompositeLocation inferLoc = mapParamToLoc.get(paramIdx);
            paramInFlowSet.add(inferLoc);
          }
        }

        if (paramInFlowSet.size() > 0) {
          CompositeLocation lowestLoc = getLowest(methodLattice, paramInFlowSet);
          assert (lowestLoc != null);
          methodInfo.setPCLoc(lowestLoc);
        }

      }

      // calculate a return location
      // the return location type is lower than all parameters and location
      // types
      // of return values
      if (!md.getReturnType().isVoid()) {
        // first, generate the set of return value location types that starts
        // with
        // 'this' reference

        Set<CompositeLocation> inferFieldReturnLocSet = new HashSet<CompositeLocation>();

        Set<FlowNode> paramFlowNode = getParamNodeFlowingToReturnValue(md);
        Set<CompositeLocation> inferParamLocSet = new HashSet<CompositeLocation>();
        if (paramFlowNode != null) {
          for (Iterator iterator = paramFlowNode.iterator(); iterator.hasNext();) {
            FlowNode fn = (FlowNode) iterator.next();
            CompositeLocation inferLoc =
                generateInferredCompositeLocation(methodInfo, getFlowGraph(md).getLocationTuple(fn));
            inferParamLocSet.add(inferLoc);
          }
        }

        Set<FlowNode> returnNodeSet = fg.getReturnNodeSet();

        skip: for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
          FlowNode returnNode = (FlowNode) iterator.next();
          CompositeLocation inferReturnLoc =
              generateInferredCompositeLocation(methodInfo, fg.getLocationTuple(returnNode));
          if (inferReturnLoc.get(0).getLocIdentifier().equals("this")) {
            // if the location type of the return value matches "this" reference
            // then, check whether this return value is equal to/lower than all
            // of
            // parameters that possibly flow into the return values
            for (Iterator iterator2 = inferParamLocSet.iterator(); iterator2.hasNext();) {
              CompositeLocation paramInferLoc = (CompositeLocation) iterator2.next();

              if ((!paramInferLoc.equals(inferReturnLoc))
                  && !isGreaterThan(methodLattice, paramInferLoc, inferReturnLoc)) {
                continue skip;
              }
            }
            inferFieldReturnLocSet.add(inferReturnLoc);

          }
        }

        if (inferFieldReturnLocSet.size() > 0) {

          CompositeLocation returnLoc = getLowest(methodLattice, inferFieldReturnLocSet);
          if (returnLoc == null) {
            // in this case, assign <'this',bottom> to the RETURNLOC
            returnLoc = new CompositeLocation(new Location(md, md.getThis().getSymbol()));
            returnLoc.addLocation(new Location(md.getClassDesc(), getLattice(md.getClassDesc())
                .getBottomItem()));
          }
          methodInfo.setReturnLoc(returnLoc);

        } else {
          String returnLocSymbol = "RETURNLOC";
          CompositeLocation returnLocInferLoc =
              new CompositeLocation(new Location(md, returnLocSymbol));
          methodInfo.setReturnLoc(returnLocInferLoc);

          for (Iterator iterator = paramIdxSet.iterator(); iterator.hasNext();) {
            Integer paramIdx = (Integer) iterator.next();
            CompositeLocation inferLoc = mapParamToLoc.get(paramIdx);
            String paramLocLocalSymbol = inferLoc.get(0).getLocIdentifier();
            if (!methodLattice.isGreaterThan(paramLocLocalSymbol, returnLocSymbol)) {
              addRelationHigherToLower(methodLattice, methodInfo, paramLocLocalSymbol,
                  returnLocSymbol);
            }
          }

          for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
            FlowNode returnNode = (FlowNode) iterator.next();
            CompositeLocation inferLoc =
                generateInferredCompositeLocation(methodInfo, fg.getLocationTuple(returnNode));
            if (!isGreaterThan(methodLattice, inferLoc, returnLocInferLoc)) {
              addRelation(methodLattice, methodInfo, inferLoc, returnLocInferLoc);
            }
          }

        }

      }
    } catch (CyclicFlowException e) {
      e.printStackTrace();
    }

  }

  private Set<String> getHigherLocSymbolThan(SSJavaLattice<String> lattice, String loc) {
    Set<String> higherLocSet = new HashSet<String>();

    Set<String> locSet = lattice.getTable().keySet();
    for (Iterator iterator = locSet.iterator(); iterator.hasNext();) {
      String element = (String) iterator.next();
      if (lattice.isGreaterThan(element, loc) && (!element.equals(lattice.getTopItem()))) {
        higherLocSet.add(element);
      }
    }
    return higherLocSet;
  }

  private CompositeLocation getLowest(SSJavaLattice<String> methodLattice,
      Set<CompositeLocation> set) {

    CompositeLocation lowest = set.iterator().next();

    if (set.size() == 1) {
      return lowest;
    }

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      CompositeLocation loc = (CompositeLocation) iterator.next();

      if ((!loc.equals(lowest)) && (!isComparable(methodLattice, lowest, loc))) {
        // if there is a case where composite locations are incomparable, just
        // return null
        return null;
      }

      if ((!loc.equals(lowest)) && isGreaterThan(methodLattice, lowest, loc)) {
        lowest = loc;
      }
    }
    return lowest;
  }

  private boolean isComparable(SSJavaLattice<String> methodLattice, CompositeLocation comp1,
      CompositeLocation comp2) {

    int size = comp1.getSize() >= comp2.getSize() ? comp2.getSize() : comp1.getSize();

    for (int idx = 0; idx < size; idx++) {
      Location loc1 = comp1.get(idx);
      Location loc2 = comp2.get(idx);

      Descriptor desc1 = loc1.getDescriptor();
      Descriptor desc2 = loc2.getDescriptor();

      if (!desc1.equals(desc2)) {
        throw new Error("Fail to compare " + comp1 + " and " + comp2);
      }

      String symbol1 = loc1.getLocIdentifier();
      String symbol2 = loc2.getLocIdentifier();

      SSJavaLattice<String> lattice;
      if (idx == 0) {
        lattice = methodLattice;
      } else {
        lattice = getLattice(desc1);
      }

      if (symbol1.equals(symbol2)) {
        continue;
      } else if (!lattice.isComparable(symbol1, symbol2)) {
        return false;
      }

    }

    return true;
  }

  private boolean isGreaterThan(SSJavaLattice<String> methodLattice, CompositeLocation comp1,
      CompositeLocation comp2) {

    int size = comp1.getSize() >= comp2.getSize() ? comp2.getSize() : comp1.getSize();

    for (int idx = 0; idx < size; idx++) {
      Location loc1 = comp1.get(idx);
      Location loc2 = comp2.get(idx);

      Descriptor desc1 = loc1.getDescriptor();
      Descriptor desc2 = loc2.getDescriptor();

      if (!desc1.equals(desc2)) {
        throw new Error("Fail to compare " + comp1 + " and " + comp2);
      }

      String symbol1 = loc1.getLocIdentifier();
      String symbol2 = loc2.getLocIdentifier();

      SSJavaLattice<String> lattice;
      if (idx == 0) {
        lattice = methodLattice;
      } else {
        lattice = getLattice(desc1);
      }

      if (symbol1.equals(symbol2)) {
        continue;
      } else if (lattice.isGreaterThan(symbol1, symbol2)) {
        return true;
      } else {
        return false;
      }

    }

    return false;
  }

  private void recursiveAddRelationToLattice(int idx, MethodDescriptor md,
      CompositeLocation srcInferLoc, CompositeLocation dstInferLoc) throws CyclicFlowException {

    String srcLocSymbol = srcInferLoc.get(idx).getLocIdentifier();
    String dstLocSymbol = dstInferLoc.get(idx).getLocIdentifier();

    if (srcLocSymbol.equals(dstLocSymbol)) {
      recursiveAddRelationToLattice(idx + 1, md, srcInferLoc, dstInferLoc);
    } else {

      Descriptor parentDesc = srcInferLoc.get(idx).getDescriptor();
      LocationInfo locInfo = getLocationInfo(parentDesc);

      addRelationHigherToLower(getLattice(parentDesc), getLocationInfo(parentDesc), srcLocSymbol,
          dstLocSymbol);
    }

  }

  private void analyzeLatticeMethodInvocationNode(MethodDescriptor mdCaller,
      SSJavaLattice<String> methodLattice, MethodLocationInfo methodInfo)
      throws CyclicFlowException {

    // the transformation for a call site propagates all relations between
    // parameters from the callee
    // if the method is virtual, it also grab all relations from any possible
    // callees

    Set<MethodInvokeNode> setMethodInvokeNode =
        mapMethodDescriptorToMethodInvokeNodeSet.get(mdCaller);

    if (setMethodInvokeNode != null) {

      for (Iterator iterator = setMethodInvokeNode.iterator(); iterator.hasNext();) {
        MethodInvokeNode min = (MethodInvokeNode) iterator.next();
        MethodDescriptor mdCallee = min.getMethod();
        Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
        if (mdCallee.isStatic()) {
          setPossibleCallees.add(mdCallee);
        } else {
          Set<MethodDescriptor> calleeSet = ssjava.getCallGraph().getMethods(mdCallee);
          // removes method descriptors that are not invoked by the caller
          calleeSet.retainAll(mapMethodToCalleeSet.get(mdCaller));
          setPossibleCallees.addAll(calleeSet);
        }

        for (Iterator iterator2 = setPossibleCallees.iterator(); iterator2.hasNext();) {
          MethodDescriptor possibleMdCallee = (MethodDescriptor) iterator2.next();
          propagateRelationToCaller(min, mdCaller, possibleMdCallee, methodLattice, methodInfo);
        }

      }
    }

  }

  private void propagateRelationToCaller(MethodInvokeNode min, MethodDescriptor mdCaller,
      MethodDescriptor possibleMdCallee, SSJavaLattice<String> methodLattice,
      MethodLocationInfo methodInfo) throws CyclicFlowException {

    SSJavaLattice<String> calleeLattice = getMethodLattice(possibleMdCallee);
    MethodLocationInfo calleeLocInfo = getMethodLocationInfo(possibleMdCallee);
    FlowGraph calleeFlowGraph = getFlowGraph(possibleMdCallee);

    int numParam = calleeLocInfo.getNumParam();
    for (int i = 0; i < numParam; i++) {
      CompositeLocation param1 = calleeLocInfo.getParamCompositeLocation(i);
      for (int k = 0; k < numParam; k++) {
        if (i != k) {
          CompositeLocation param2 = calleeLocInfo.getParamCompositeLocation(k);

          if (isGreaterThan(getLattice(possibleMdCallee), param1, param2)) {
            NodeTupleSet argDescTupleSet1 = getNodeTupleSetByArgIdx(min, i);
            NodeTupleSet argDescTupleSet2 = getNodeTupleSetByArgIdx(min, k);

            // the callee has the relation in which param1 is higher than param2
            // therefore, the caller has to have the relation in which arg1 is
            // higher than arg2

            for (Iterator<NTuple<Descriptor>> iterator = argDescTupleSet1.iterator(); iterator
                .hasNext();) {
              NTuple<Descriptor> argDescTuple1 = iterator.next();

              for (Iterator<NTuple<Descriptor>> iterator2 = argDescTupleSet2.iterator(); iterator2
                  .hasNext();) {
                NTuple<Descriptor> argDescTuple2 = iterator2.next();

                // retreive inferred location by the local var descriptor

                NTuple<Location> tuple1 = getFlowGraph(mdCaller).getLocationTuple(argDescTuple1);
                NTuple<Location> tuple2 = getFlowGraph(mdCaller).getLocationTuple(argDescTuple2);

                // CompositeLocation higherInferLoc =
                // methodInfo.getInferLocation(argTuple1.get(0));
                // CompositeLocation lowerInferLoc =
                // methodInfo.getInferLocation(argTuple2.get(0));

                CompositeLocation inferLoc1 = generateInferredCompositeLocation(methodInfo, tuple1);
                CompositeLocation inferLoc2 = generateInferredCompositeLocation(methodInfo, tuple2);

                // addRelation(methodLattice, methodInfo, inferLoc1, inferLoc2);

                addFlowGraphEdge(mdCaller, argDescTuple1, argDescTuple2);

              }

            }

          }
        }
      }
    }

  }

  private CompositeLocation generateInferredCompositeLocation(MethodLocationInfo methodInfo,
      NTuple<Location> tuple) {

    // first, retrieve inferred location by the local var descriptor
    CompositeLocation inferLoc = new CompositeLocation();

    CompositeLocation localVarInferLoc =
        methodInfo.getInferLocation(tuple.get(0).getLocDescriptor());

    localVarInferLoc.get(0).setLocDescriptor(tuple.get(0).getLocDescriptor());

    for (int i = 0; i < localVarInferLoc.getSize(); i++) {
      inferLoc.addLocation(localVarInferLoc.get(i));
    }

    for (int i = 1; i < tuple.size(); i++) {
      Location cur = tuple.get(i);
      Descriptor enclosingDesc = cur.getDescriptor();
      Descriptor curDesc = cur.getLocDescriptor();

      Location inferLocElement;
      if (curDesc == null) {
        // in this case, we have a newly generated location.
        inferLocElement = new Location(enclosingDesc, cur.getLocIdentifier());
      } else {
        String fieldLocSymbol =
            getLocationInfo(enclosingDesc).getInferLocation(curDesc).get(0).getLocIdentifier();
        inferLocElement = new Location(enclosingDesc, fieldLocSymbol);
        inferLocElement.setLocDescriptor(curDesc);
      }

      inferLoc.addLocation(inferLocElement);

    }

    assert (inferLoc.get(0).getLocDescriptor().getSymbol() == inferLoc.get(0).getLocIdentifier());
    return inferLoc;
  }

  private void addRelation(SSJavaLattice<String> methodLattice, MethodLocationInfo methodInfo,
      CompositeLocation srcInferLoc, CompositeLocation dstInferLoc) throws CyclicFlowException {

    System.out.println("addRelation --- srcInferLoc=" + srcInferLoc + "  dstInferLoc="
        + dstInferLoc);
    String srcLocalLocSymbol = srcInferLoc.get(0).getLocIdentifier();
    String dstLocalLocSymbol = dstInferLoc.get(0).getLocIdentifier();

    if (srcInferLoc.getSize() == 1 && dstInferLoc.getSize() == 1) {
      // add a new relation to the local lattice
      addRelationHigherToLower(methodLattice, methodInfo, srcLocalLocSymbol, dstLocalLocSymbol);
    } else if (srcInferLoc.getSize() > 1 && dstInferLoc.getSize() > 1) {
      // both src and dst have assigned to a composite location

      if (!srcLocalLocSymbol.equals(dstLocalLocSymbol)) {
        addRelationHigherToLower(methodLattice, methodInfo, srcLocalLocSymbol, dstLocalLocSymbol);
      } else {
        recursivelyAddRelation(1, srcInferLoc, dstInferLoc);
      }
    } else {
      // either src or dst has assigned to a composite location
      if (!srcLocalLocSymbol.equals(dstLocalLocSymbol)) {
        addRelationHigherToLower(methodLattice, methodInfo, srcLocalLocSymbol, dstLocalLocSymbol);
      }
    }

    System.out.println();

  }

  public LocationInfo getLocationInfo(Descriptor d) {
    if (d instanceof MethodDescriptor) {
      return getMethodLocationInfo((MethodDescriptor) d);
    } else {
      return getFieldLocationInfo((ClassDescriptor) d);
    }
  }

  private MethodLocationInfo getMethodLocationInfo(MethodDescriptor md) {

    if (!mapMethodDescToMethodLocationInfo.containsKey(md)) {
      mapMethodDescToMethodLocationInfo.put(md, new MethodLocationInfo(md));
    }

    return mapMethodDescToMethodLocationInfo.get(md);

  }

  private LocationInfo getFieldLocationInfo(ClassDescriptor cd) {

    if (!mapClassToLocationInfo.containsKey(cd)) {
      mapClassToLocationInfo.put(cd, new LocationInfo(cd));
    }

    return mapClassToLocationInfo.get(cd);

  }

  private void addRelationToLattice(MethodDescriptor md, SSJavaLattice<String> methodLattice,
      MethodLocationInfo methodInfo, FlowNode srcNode, FlowNode dstNode) throws CyclicFlowException {

    System.out.println();
    System.out.println("### addRelationToLattice src=" + srcNode + " dst=" + dstNode);

    // add a new binary relation of dstNode < srcNode
    FlowGraph flowGraph = getFlowGraph(md);
    try {
      System.out.println("***** src composite case::");
      calculateCompositeLocation(flowGraph, methodLattice, methodInfo, srcNode);

      CompositeLocation srcInferLoc =
          generateInferredCompositeLocation(methodInfo, flowGraph.getLocationTuple(srcNode));
      CompositeLocation dstInferLoc =
          generateInferredCompositeLocation(methodInfo, flowGraph.getLocationTuple(dstNode));
      addRelation(methodLattice, methodInfo, srcInferLoc, dstInferLoc);
    } catch (CyclicFlowException e) {
      // there is a cyclic value flow... try to calculate a composite location
      // for the destination node
      System.out.println("***** dst composite case::");
      calculateCompositeLocation(flowGraph, methodLattice, methodInfo, dstNode);
      CompositeLocation srcInferLoc =
          generateInferredCompositeLocation(methodInfo, flowGraph.getLocationTuple(srcNode));
      CompositeLocation dstInferLoc =
          generateInferredCompositeLocation(methodInfo, flowGraph.getLocationTuple(dstNode));
      try {
        addRelation(methodLattice, methodInfo, srcInferLoc, dstInferLoc);
      } catch (CyclicFlowException e1) {
        throw new Error("Failed to merge cyclic value flows into a shared location.");
      }
    }

  }

  private void recursivelyAddRelation(int idx, CompositeLocation srcInferLoc,
      CompositeLocation dstInferLoc) throws CyclicFlowException {

    String srcLocSymbol = srcInferLoc.get(idx).getLocIdentifier();
    String dstLocSymbol = dstInferLoc.get(idx).getLocIdentifier();

    Descriptor parentDesc = srcInferLoc.get(idx).getDescriptor();

    if (srcLocSymbol.equals(dstLocSymbol)) {
      // check if it is the case of shared location
      if (srcInferLoc.getSize() == (idx + 1) && dstInferLoc.getSize() == (idx + 1)) {
        Location inferLocElement = srcInferLoc.get(idx);
        System.out.println("SET SHARED LOCATION=" + inferLocElement);
        getLattice(inferLocElement.getDescriptor())
            .addSharedLoc(inferLocElement.getLocIdentifier());
      } else if (srcInferLoc.getSize() > (idx + 1) && dstInferLoc.getSize() > (idx + 1)) {
        recursivelyAddRelation(idx + 1, srcInferLoc, dstInferLoc);
      }
    } else {
      addRelationHigherToLower(getLattice(parentDesc), getLocationInfo(parentDesc), srcLocSymbol,
          dstLocSymbol);
    }
  }

  private void recursivelyAddCompositeRelation(MethodDescriptor md, FlowGraph flowGraph,
      MethodLocationInfo methodInfo, FlowNode srcNode, FlowNode dstNode, Descriptor srcDesc,
      Descriptor dstDesc) throws CyclicFlowException {

    CompositeLocation inferSrcLoc;
    CompositeLocation inferDstLoc = methodInfo.getInferLocation(dstDesc);

    if (srcNode.getDescTuple().size() > 1) {
      // field access
      inferSrcLoc = new CompositeLocation();

      NTuple<Location> locTuple = flowGraph.getLocationTuple(srcNode);
      for (int i = 0; i < locTuple.size(); i++) {
        inferSrcLoc.addLocation(locTuple.get(i));
      }

    } else {
      inferSrcLoc = methodInfo.getInferLocation(srcDesc);
    }

    if (dstNode.getDescTuple().size() > 1) {
      // field access
      inferDstLoc = new CompositeLocation();

      NTuple<Location> locTuple = flowGraph.getLocationTuple(dstNode);
      for (int i = 0; i < locTuple.size(); i++) {
        inferDstLoc.addLocation(locTuple.get(i));
      }

    } else {
      inferDstLoc = methodInfo.getInferLocation(dstDesc);
    }

    recursiveAddRelationToLattice(1, md, inferSrcLoc, inferDstLoc);
  }

  private void addPrefixMapping(Map<NTuple<Location>, Set<NTuple<Location>>> map,
      NTuple<Location> prefix, NTuple<Location> element) {

    if (!map.containsKey(prefix)) {
      map.put(prefix, new HashSet<NTuple<Location>>());
    }
    map.get(prefix).add(element);
  }

  private boolean calculateCompositeLocation(FlowGraph flowGraph,
      SSJavaLattice<String> methodLattice, MethodLocationInfo methodInfo, FlowNode flowNode)
      throws CyclicFlowException {

    Descriptor localVarDesc = flowNode.getDescTuple().get(0);
    NTuple<Location> flowNodelocTuple = flowGraph.getLocationTuple(flowNode);

    if (localVarDesc.equals(methodInfo.getMethodDesc())) {
      return false;
    }

    Set<FlowNode> inNodeSet = flowGraph.getIncomingFlowNodeSet(flowNode);
    Set<FlowNode> reachableNodeSet = flowGraph.getReachableFlowNodeSet(flowNode);

    Map<NTuple<Location>, Set<NTuple<Location>>> mapPrefixToIncomingLocTupleSet =
        new HashMap<NTuple<Location>, Set<NTuple<Location>>>();

    List<NTuple<Location>> prefixList = new ArrayList<NTuple<Location>>();

    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      FlowNode inNode = (FlowNode) iterator.next();
      NTuple<Location> inNodeTuple = flowGraph.getLocationTuple(inNode);

      CompositeLocation inNodeInferredLoc =
          generateInferredCompositeLocation(methodInfo, inNodeTuple);

      NTuple<Location> inNodeInferredLocTuple = inNodeInferredLoc.getTuple();

      for (int i = 1; i < inNodeInferredLocTuple.size(); i++) {
        NTuple<Location> prefix = inNodeInferredLocTuple.subList(0, i);
        if (!prefixList.contains(prefix)) {
          prefixList.add(prefix);
        }
        addPrefixMapping(mapPrefixToIncomingLocTupleSet, prefix, inNodeInferredLocTuple);
      }
    }

    Collections.sort(prefixList, new Comparator<NTuple<Location>>() {
      public int compare(NTuple<Location> arg0, NTuple<Location> arg1) {
        int s0 = arg0.size();
        int s1 = arg1.size();
        if (s0 > s1) {
          return -1;
        } else if (s0 == s1) {
          return 0;
        } else {
          return 1;
        }
      }
    });

    System.out.println("prefixList=" + prefixList);
    System.out.println("reachableNodeSet=" + reachableNodeSet);

    // find out reachable nodes that have the longest common prefix
    for (int i = 0; i < prefixList.size(); i++) {
      NTuple<Location> curPrefix = prefixList.get(i);
      Set<NTuple<Location>> reachableCommonPrefixSet = new HashSet<NTuple<Location>>();

      for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
        FlowNode reachableNode = (FlowNode) iterator2.next();
        NTuple<Location> reachLocTuple = flowGraph.getLocationTuple(reachableNode);
        CompositeLocation reachLocInferLoc =
            generateInferredCompositeLocation(methodInfo, reachLocTuple);
        if (reachLocInferLoc.getTuple().startsWith(curPrefix)) {
          reachableCommonPrefixSet.add(reachLocTuple);
        }
      }

      if (!reachableCommonPrefixSet.isEmpty()) {
        // found reachable nodes that start with the prefix curPrefix
        // need to assign a composite location

        // first, check if there are more than one the set of locations that has
        // the same length of the longest reachable prefix, no way to assign
        // a composite location to the input local var
        prefixSanityCheck(prefixList, i, flowGraph, reachableNodeSet);

        Set<NTuple<Location>> incomingCommonPrefixSet =
            mapPrefixToIncomingLocTupleSet.get(curPrefix);

        int idx = curPrefix.size();
        NTuple<Location> element = incomingCommonPrefixSet.iterator().next();
        Descriptor desc = element.get(idx).getDescriptor();

        SSJavaLattice<String> lattice = getLattice(desc);
        LocationInfo locInfo = getLocationInfo(desc);

        CompositeLocation inferLocation =
            generateInferredCompositeLocation(methodInfo, flowNodelocTuple);

        // methodInfo.getInferLocation(localVarDesc);
        CompositeLocation newInferLocation = new CompositeLocation();

        if (inferLocation.getTuple().startsWith(curPrefix)) {
          // the same infer location is already existed. no need to do
          // anything
          System.out.println("NO ATTEMPT TO MAKE A COMPOSITE LOCATION curPrefix=" + curPrefix);
          return true;
        } else {
          // assign a new composite location

          // String oldMethodLocationSymbol =
          // inferLocation.get(0).getLocIdentifier();
          String newLocSymbol = "Loc" + (SSJavaLattice.seed++);
          for (int locIdx = 0; locIdx < curPrefix.size(); locIdx++) {
            newInferLocation.addLocation(curPrefix.get(locIdx));
          }
          Location newLocationElement = new Location(desc, newLocSymbol);
          newInferLocation.addLocation(newLocationElement);

          // maps local variable to location types of the common prefix
          methodInfo.mapDescriptorToLocation(localVarDesc, newInferLocation.clone());

          // methodInfo.mapDescriptorToLocation(localVarDesc, newInferLocation);
          addMapLocSymbolToInferredLocation(methodInfo.getMethodDesc(), localVarDesc,
              newInferLocation);
          methodInfo.removeMaplocalVarToLocSet(localVarDesc);

          // add the field/var descriptor to the set of the location symbol
          int lastIdx = flowNode.getDescTuple().size() - 1;
          Descriptor lastFlowNodeDesc = flowNode.getDescTuple().get(lastIdx);
          Descriptor enclosinglastLastFlowNodeDesc = flowNodelocTuple.get(lastIdx).getDescriptor();

          CompositeLocation newlyInferredLocForFlowNode =
              generateInferredCompositeLocation(methodInfo, flowNodelocTuple);
          Location lastInferLocElement =
              newlyInferredLocForFlowNode.get(newlyInferredLocForFlowNode.getSize() - 1);
          Descriptor enclosingLastInferLocElement = lastInferLocElement.getDescriptor();

          // getLocationInfo(enclosingLastInferLocElement).addMapLocSymbolToDescSet(
          // lastInferLocElement.getLocIdentifier(), lastFlowNodeDesc);
          getLocationInfo(enclosingLastInferLocElement).addMapLocSymbolToRelatedInferLoc(
              lastInferLocElement.getLocIdentifier(), enclosinglastLastFlowNodeDesc,
              lastFlowNodeDesc);

          // clean up the previous location
          // Location prevInferLocElement =
          // inferLocation.get(inferLocation.getSize() - 1);
          // Descriptor prevEnclosingDesc = prevInferLocElement.getDescriptor();
          //
          // SSJavaLattice<String> targetLattice;
          // LocationInfo targetInfo;
          // if (prevEnclosingDesc.equals(methodInfo.getMethodDesc())) {
          // targetLattice = methodLattice;
          // targetInfo = methodInfo;
          // } else {
          // targetLattice = getLattice(prevInferLocElement.getDescriptor());
          // targetInfo = getLocationInfo(prevInferLocElement.getDescriptor());
          // }
          //
          // Set<Pair<Descriptor, Descriptor>> associstedDescSet =
          // targetInfo.getRelatedInferLocSet(prevInferLocElement.getLocIdentifier());
          //
          // if (associstedDescSet.size() == 1) {
          // targetLattice.remove(prevInferLocElement.getLocIdentifier());
          // } else {
          // associstedDescSet.remove(lastFlowNodeDesc);
          // }

        }

        System.out.println("curPrefix=" + curPrefix);
        System.out.println("ASSIGN NEW COMPOSITE LOCATION =" + newInferLocation + "    to "
            + flowNode);

        String newlyInsertedLocName =
            newInferLocation.get(newInferLocation.getSize() - 1).getLocIdentifier();

        System.out.println("-- add in-flow");
        for (Iterator iterator = incomingCommonPrefixSet.iterator(); iterator.hasNext();) {
          NTuple<Location> tuple = (NTuple<Location>) iterator.next();
          Location loc = tuple.get(idx);
          String higher = loc.getLocIdentifier();
          addRelationHigherToLower(lattice, locInfo, higher, newlyInsertedLocName);
        }

        System.out.println("-- add out flow");
        for (Iterator iterator = reachableCommonPrefixSet.iterator(); iterator.hasNext();) {
          NTuple<Location> tuple = (NTuple<Location>) iterator.next();
          if (tuple.size() > idx) {
            Location loc = tuple.get(idx);
            String lower = loc.getLocIdentifier();
            // String lower =
            // locInfo.getFieldInferLocation(loc.getLocDescriptor()).getLocIdentifier();
            addRelationHigherToLower(lattice, locInfo, newlyInsertedLocName, lower);
          }
        }

        return true;
      }

    }

    return false;

  }

  private void addMapLocSymbolToInferredLocation(MethodDescriptor md, Descriptor localVar,
      CompositeLocation inferLoc) {

    Location locElement = inferLoc.get((inferLoc.getSize() - 1));
    Descriptor enclosingDesc = locElement.getDescriptor();
    LocationInfo locInfo = getLocationInfo(enclosingDesc);
    locInfo.addMapLocSymbolToRelatedInferLoc(locElement.getLocIdentifier(), md, localVar);
  }

  private boolean isCompositeLocation(CompositeLocation cl) {
    return cl.getSize() > 1;
  }

  private boolean containsNonPrimitiveElement(Set<Descriptor> descSet) {
    for (Iterator iterator = descSet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();

      if (desc.equals(LocationInference.GLOBALDESC)) {
        return true;
      } else if (desc instanceof VarDescriptor) {
        if (!((VarDescriptor) desc).getType().isPrimitive()) {
          return true;
        }
      } else if (desc instanceof FieldDescriptor) {
        if (!((FieldDescriptor) desc).getType().isPrimitive()) {
          return true;
        }
      }

    }
    return false;
  }

  private void addRelationHigherToLower(SSJavaLattice<String> lattice, LocationInfo locInfo,
      String higher, String lower) throws CyclicFlowException {

    System.out.println("---addRelationHigherToLower " + higher + " -> " + lower
        + " to the lattice of " + locInfo.getDescIdentifier());
    // if (higher.equals(lower) && lattice.isSharedLoc(higher)) {
    // return;
    // }
    Set<String> cycleElementSet = lattice.getPossibleCycleElements(higher, lower);

    boolean hasNonPrimitiveElement = false;
    for (Iterator iterator = cycleElementSet.iterator(); iterator.hasNext();) {
      String cycleElementLocSymbol = (String) iterator.next();

      Set<Descriptor> descSet = locInfo.getDescSet(cycleElementLocSymbol);
      if (containsNonPrimitiveElement(descSet)) {
        hasNonPrimitiveElement = true;
        break;
      }
    }

    if (hasNonPrimitiveElement) {
      System.out.println("#Check cycle= " + lower + " < " + higher + "     cycleElementSet="
          + cycleElementSet);
      // if there is non-primitive element in the cycle, no way to merge cyclic
      // elements into the shared location
      throw new CyclicFlowException();
    }

    if (cycleElementSet.size() > 0) {

      String newSharedLoc = "SharedLoc" + (SSJavaLattice.seed++);

      System.out.println("---ASSIGN NEW SHARED LOC=" + newSharedLoc + "   to  " + cycleElementSet);
      lattice.mergeIntoSharedLocation(cycleElementSet, newSharedLoc);

      for (Iterator iterator = cycleElementSet.iterator(); iterator.hasNext();) {
        String oldLocSymbol = (String) iterator.next();

        Set<Pair<Descriptor, Descriptor>> inferLocSet = locInfo.getRelatedInferLocSet(oldLocSymbol);
        System.out.println("---update related locations=" + inferLocSet);
        for (Iterator iterator2 = inferLocSet.iterator(); iterator2.hasNext();) {
          Pair<Descriptor, Descriptor> pair = (Pair<Descriptor, Descriptor>) iterator2.next();
          Descriptor enclosingDesc = pair.getFirst();
          Descriptor desc = pair.getSecond();

          CompositeLocation inferLoc;
          if (curMethodInfo.md.equals(enclosingDesc)) {
            inferLoc = curMethodInfo.getInferLocation(desc);
          } else {
            inferLoc = getLocationInfo(enclosingDesc).getInferLocation(desc);
          }

          Location locElement = inferLoc.get(inferLoc.getSize() - 1);

          locElement.setLocIdentifier(newSharedLoc);
          locInfo.addMapLocSymbolToRelatedInferLoc(newSharedLoc, enclosingDesc, desc);

          if (curMethodInfo.md.equals(enclosingDesc)) {
            inferLoc = curMethodInfo.getInferLocation(desc);
          } else {
            inferLoc = getLocationInfo(enclosingDesc).getInferLocation(desc);
          }
          System.out.println("---New Infer Loc=" + inferLoc);

        }
        locInfo.removeRelatedInferLocSet(oldLocSymbol, newSharedLoc);

      }

      lattice.addSharedLoc(newSharedLoc);

    } else if (!lattice.isGreaterThan(higher, lower)) {
      lattice.addRelationHigherToLower(higher, lower);
    }
  }

  private void replaceOldLocWithNewLoc(SSJavaLattice<String> methodLattice, String oldLocSymbol,
      String newLocSymbol) {

    if (methodLattice.containsKey(oldLocSymbol)) {
      methodLattice.substituteLocation(oldLocSymbol, newLocSymbol);
    }

  }

  private void prefixSanityCheck(List<NTuple<Location>> prefixList, int curIdx,
      FlowGraph flowGraph, Set<FlowNode> reachableNodeSet) {

    NTuple<Location> curPrefix = prefixList.get(curIdx);

    for (int i = curIdx + 1; i < prefixList.size(); i++) {
      NTuple<Location> prefixTuple = prefixList.get(i);

      if (curPrefix.startsWith(prefixTuple)) {
        continue;
      }

      for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
        FlowNode reachableNode = (FlowNode) iterator2.next();
        NTuple<Location> reachLocTuple = flowGraph.getLocationTuple(reachableNode);
        if (reachLocTuple.startsWith(prefixTuple)) {
          // TODO
          throw new Error("Failed to generate a composite location");
        }
      }
    }
  }

  public boolean isPrimitiveLocalVariable(FlowNode node) {
    VarDescriptor varDesc = (VarDescriptor) node.getDescTuple().get(0);
    return varDesc.getType().isPrimitive();
  }

  private SSJavaLattice<String> getLattice(Descriptor d) {
    if (d instanceof MethodDescriptor) {
      return getMethodLattice((MethodDescriptor) d);
    } else {
      return getFieldLattice((ClassDescriptor) d);
    }
  }

  private SSJavaLattice<String> getMethodLattice(MethodDescriptor md) {
    if (!md2lattice.containsKey(md)) {
      md2lattice.put(md, new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM));
    }
    return md2lattice.get(md);
  }

  private void setMethodLattice(MethodDescriptor md, SSJavaLattice<String> lattice) {
    md2lattice.put(md, lattice);
  }

  private void extractRelationFromFieldFlows(ClassDescriptor cd, FlowNode srcNode,
      FlowNode dstNode, int idx) throws CyclicFlowException {

    if (srcNode.getDescTuple().get(idx).equals(dstNode.getDescTuple().get(idx))
        && srcNode.getDescTuple().size() > (idx + 1) && dstNode.getDescTuple().size() > (idx + 1)) {
      // value flow between fields: we don't need to add a binary relation
      // for this case

      Descriptor desc = srcNode.getDescTuple().get(idx);
      ClassDescriptor classDesc;

      if (idx == 0) {
        classDesc = ((VarDescriptor) desc).getType().getClassDesc();
      } else {
        classDesc = ((FieldDescriptor) desc).getType().getClassDesc();
      }

      extractRelationFromFieldFlows(classDesc, srcNode, dstNode, idx + 1);

    } else {

      Descriptor srcFieldDesc = srcNode.getDescTuple().get(idx);
      Descriptor dstFieldDesc = dstNode.getDescTuple().get(idx);

      // add a new binary relation of dstNode < srcNode
      SSJavaLattice<String> fieldLattice = getFieldLattice(cd);
      LocationInfo fieldInfo = getFieldLocationInfo(cd);

      String srcSymbol = fieldInfo.getFieldInferLocation(srcFieldDesc).getLocIdentifier();
      String dstSymbol = fieldInfo.getFieldInferLocation(dstFieldDesc).getLocIdentifier();

      addRelationHigherToLower(fieldLattice, fieldInfo, srcSymbol, dstSymbol);

    }

  }

  public SSJavaLattice<String> getFieldLattice(ClassDescriptor cd) {
    if (!cd2lattice.containsKey(cd)) {
      cd2lattice.put(cd, new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM));
    }
    return cd2lattice.get(cd);
  }

  public LinkedList<MethodDescriptor> computeMethodList() {

    Set<MethodDescriptor> toSort = new HashSet<MethodDescriptor>();

    setupToAnalyze();

    Set<MethodDescriptor> visited = new HashSet<MethodDescriptor>();
    Set<MethodDescriptor> reachableCallee = new HashSet<MethodDescriptor>();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);
      toanalyzeMethodList.removeAll(visited);

      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        if ((!visited.contains(md))
            && (ssjava.needTobeAnnotated(md) || reachableCallee.contains(md))) {

          // creates a mapping from a method descriptor to virtual methods
          Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
          if (md.isStatic()) {
            setPossibleCallees.add(md);
          } else {
            setPossibleCallees.addAll(ssjava.getCallGraph().getMethods(md));
          }

          Set<MethodDescriptor> calleeSet = ssjava.getCallGraph().getCalleeSet(md);
          Set<MethodDescriptor> needToAnalyzeCalleeSet = new HashSet<MethodDescriptor>();

          for (Iterator iterator = calleeSet.iterator(); iterator.hasNext();) {
            MethodDescriptor calleemd = (MethodDescriptor) iterator.next();
            if ((!ssjava.isTrustMethod(calleemd))
                && (!ssjava.isSSJavaUtil(calleemd.getClassDesc()))) {
              if (!visited.contains(calleemd)) {
                toanalyzeMethodList.add(calleemd);
              }
              reachableCallee.add(calleemd);
              needToAnalyzeCalleeSet.add(calleemd);
            }
          }

          mapMethodToCalleeSet.put(md, needToAnalyzeCalleeSet);

          visited.add(md);

          toSort.add(md);
        }
      }
    }

    return ssjava.topologicalSort(toSort);

  }

  public void constructFlowGraph() {

    LinkedList<MethodDescriptor> methodDescList = computeMethodList();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();
      if (state.SSJAVADEBUG) {
        System.out.println();
        System.out.println("SSJAVA: Constructing a flow graph: " + md);

        // creates a mapping from a parameter descriptor to its index
        Map<Descriptor, Integer> mapParamDescToIdx = new HashMap<Descriptor, Integer>();
        int offset = 0;
        if (!md.isStatic()) {
          offset = 1;
          mapParamDescToIdx.put(md.getThis(), 0);
        }

        for (int i = 0; i < md.numParameters(); i++) {
          Descriptor paramDesc = (Descriptor) md.getParameter(i);
          mapParamDescToIdx.put(paramDesc, new Integer(i + offset));
        }

        FlowGraph fg = new FlowGraph(md, mapParamDescToIdx);
        mapMethodDescriptorToFlowGraph.put(md, fg);

        analyzeMethodBody(md.getClassDesc(), md);
      }
    }
    _debug_printGraph();

  }

  private void analyzeMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    NodeTupleSet implicitFlowTupleSet = new NodeTupleSet();
    analyzeFlowBlockNode(md, md.getParameterTable(), bn, implicitFlowTupleSet);
  }

  private void analyzeFlowBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn,
      NodeTupleSet implicitFlowTupleSet) {

    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      analyzeBlockStatementNode(md, bn.getVarTable(), bsn, implicitFlowTupleSet);
    }

  }

  private void analyzeBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn, NodeTupleSet implicitFlowTupleSet) {

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      analyzeBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn, implicitFlowTupleSet);
      break;

    case Kind.DeclarationNode:
      analyzeFlowDeclarationNode(md, nametable, (DeclarationNode) bsn, implicitFlowTupleSet);
      break;

    case Kind.IfStatementNode:
      analyzeFlowIfStatementNode(md, nametable, (IfStatementNode) bsn, implicitFlowTupleSet);
      break;

    case Kind.LoopNode:
      analyzeFlowLoopNode(md, nametable, (LoopNode) bsn, implicitFlowTupleSet);
      break;

    case Kind.ReturnNode:
      analyzeFlowReturnNode(md, nametable, (ReturnNode) bsn, implicitFlowTupleSet);
      break;

    case Kind.SubBlockNode:
      analyzeFlowSubBlockNode(md, nametable, (SubBlockNode) bsn, implicitFlowTupleSet);
      break;

    case Kind.ContinueBreakNode:
      break;

    case Kind.SwitchStatementNode:
      analyzeSwitchStatementNode(md, nametable, (SwitchStatementNode) bsn);
      break;

    }

  }

  private void analyzeSwitchStatementNode(MethodDescriptor md, SymbolTable nametable,
      SwitchStatementNode bsn) {
    // TODO Auto-generated method stub
  }

  private void analyzeFlowSubBlockNode(MethodDescriptor md, SymbolTable nametable,
      SubBlockNode sbn, NodeTupleSet implicitFlowTupleSet) {
    analyzeFlowBlockNode(md, nametable, sbn.getBlockNode(), implicitFlowTupleSet);
  }

  private void analyzeFlowReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode rn,
      NodeTupleSet implicitFlowTupleSet) {

    ExpressionNode returnExp = rn.getReturnExpression();

    if (returnExp != null) {
      NodeTupleSet nodeSet = new NodeTupleSet();
      analyzeFlowExpressionNode(md, nametable, returnExp, nodeSet, false);

      FlowGraph fg = getFlowGraph(md);

      // annotate the elements of the node set as the return location
      for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
        NTuple<Descriptor> returnDescTuple = (NTuple<Descriptor>) iterator.next();
        fg.addReturnFlowNode(returnDescTuple);
        for (Iterator iterator2 = implicitFlowTupleSet.iterator(); iterator2.hasNext();) {
          NTuple<Descriptor> implicitFlowDescTuple = (NTuple<Descriptor>) iterator2.next();
          fg.addValueFlowEdge(implicitFlowDescTuple, returnDescTuple);
        }
      }
    }

  }

  private void analyzeFlowLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln,
      NodeTupleSet implicitFlowTupleSet) {

    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {

      NodeTupleSet condTupleNode = new NodeTupleSet();
      analyzeFlowExpressionNode(md, nametable, ln.getCondition(), condTupleNode, null,
          implicitFlowTupleSet, false);
      condTupleNode.addTupleSet(implicitFlowTupleSet);

      // add edges from condNodeTupleSet to all nodes of conditional nodes
      analyzeFlowBlockNode(md, nametable, ln.getBody(), condTupleNode);

    } else {
      // check 'for loop' case
      BlockNode bn = ln.getInitializer();
      bn.getVarTable().setParent(nametable);
      for (int i = 0; i < bn.size(); i++) {
        BlockStatementNode bsn = bn.get(i);
        analyzeBlockStatementNode(md, bn.getVarTable(), bsn, implicitFlowTupleSet);
      }

      NodeTupleSet condTupleNode = new NodeTupleSet();
      analyzeFlowExpressionNode(md, bn.getVarTable(), ln.getCondition(), condTupleNode, null,
          implicitFlowTupleSet, false);
      condTupleNode.addTupleSet(implicitFlowTupleSet);

      analyzeFlowBlockNode(md, bn.getVarTable(), ln.getUpdate(), condTupleNode);
      analyzeFlowBlockNode(md, bn.getVarTable(), ln.getBody(), condTupleNode);

    }

  }

  private void analyzeFlowIfStatementNode(MethodDescriptor md, SymbolTable nametable,
      IfStatementNode isn, NodeTupleSet implicitFlowTupleSet) {

    NodeTupleSet condTupleNode = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, isn.getCondition(), condTupleNode, null,
        implicitFlowTupleSet, false);

    // add edges from condNodeTupleSet to all nodes of conditional nodes
    condTupleNode.addTupleSet(implicitFlowTupleSet);
    analyzeFlowBlockNode(md, nametable, isn.getTrueBlock(), condTupleNode);

    if (isn.getFalseBlock() != null) {
      analyzeFlowBlockNode(md, nametable, isn.getFalseBlock(), condTupleNode);
    }

  }

  private void analyzeFlowDeclarationNode(MethodDescriptor md, SymbolTable nametable,
      DeclarationNode dn, NodeTupleSet implicitFlowTupleSet) {

    VarDescriptor vd = dn.getVarDescriptor();
    mapDescToDefinitionLine.put(vd, dn.getNumLine());
    NTuple<Descriptor> tupleLHS = new NTuple<Descriptor>();
    tupleLHS.add(vd);
    FlowNode fn = getFlowGraph(md).createNewFlowNode(tupleLHS);
    fn.setDeclarationNode();

    if (dn.getExpression() != null) {

      NodeTupleSet tupleSetRHS = new NodeTupleSet();
      analyzeFlowExpressionNode(md, nametable, dn.getExpression(), tupleSetRHS, null,
          implicitFlowTupleSet, false);

      // add a new flow edge from rhs to lhs
      for (Iterator<NTuple<Descriptor>> iter = tupleSetRHS.iterator(); iter.hasNext();) {
        NTuple<Descriptor> from = iter.next();
        addFlowGraphEdge(md, from, tupleLHS);
      }

    }

  }

  private void analyzeBlockExpressionNode(MethodDescriptor md, SymbolTable nametable,
      BlockExpressionNode ben, NodeTupleSet implicitFlowTupleSet) {
    analyzeFlowExpressionNode(md, nametable, ben.getExpression(), null, null, implicitFlowTupleSet,
        false);
  }

  private NTuple<Descriptor> analyzeFlowExpressionNode(MethodDescriptor md, SymbolTable nametable,
      ExpressionNode en, NodeTupleSet nodeSet, boolean isLHS) {
    return analyzeFlowExpressionNode(md, nametable, en, nodeSet, null, new NodeTupleSet(), isLHS);
  }

  private NTuple<Descriptor> analyzeFlowExpressionNode(MethodDescriptor md, SymbolTable nametable,
      ExpressionNode en, NodeTupleSet nodeSet, NTuple<Descriptor> base,
      NodeTupleSet implicitFlowTupleSet, boolean isLHS) {

    // note that expression node can create more than one flow node
    // nodeSet contains of flow nodes
    // base is always assigned to null except the case of a name node!

    NTuple<Descriptor> flowTuple;

    switch (en.kind()) {

    case Kind.AssignmentNode:
      analyzeFlowAssignmentNode(md, nametable, (AssignmentNode) en, nodeSet, base,
          implicitFlowTupleSet);
      break;

    case Kind.FieldAccessNode:
      flowTuple =
          analyzeFlowFieldAccessNode(md, nametable, (FieldAccessNode) en, nodeSet, base,
              implicitFlowTupleSet, isLHS);
      if (flowTuple != null) {
        nodeSet.addTuple(flowTuple);
      }
      return flowTuple;

    case Kind.NameNode:
      NodeTupleSet nameNodeSet = new NodeTupleSet();
      flowTuple =
          analyzeFlowNameNode(md, nametable, (NameNode) en, nameNodeSet, base, implicitFlowTupleSet);
      if (flowTuple != null) {
        nodeSet.addTuple(flowTuple);
      }
      return flowTuple;

    case Kind.OpNode:
      analyzeFlowOpNode(md, nametable, (OpNode) en, nodeSet, implicitFlowTupleSet);
      break;

    case Kind.CreateObjectNode:
      analyzeCreateObjectNode(md, nametable, (CreateObjectNode) en);
      break;

    case Kind.ArrayAccessNode:
      analyzeFlowArrayAccessNode(md, nametable, (ArrayAccessNode) en, nodeSet, isLHS);
      break;

    case Kind.LiteralNode:
      analyzeLiteralNode(md, nametable, (LiteralNode) en);
      break;

    case Kind.MethodInvokeNode:
      analyzeFlowMethodInvokeNode(md, nametable, (MethodInvokeNode) en, nodeSet,
          implicitFlowTupleSet);
      break;

    case Kind.TertiaryNode:
      analyzeFlowTertiaryNode(md, nametable, (TertiaryNode) en, nodeSet, implicitFlowTupleSet);
      break;

    case Kind.CastNode:
      analyzeFlowCastNode(md, nametable, (CastNode) en, nodeSet, base, implicitFlowTupleSet);
      break;
    // case Kind.InstanceOfNode:
    // checkInstanceOfNode(md, nametable, (InstanceOfNode) en, td);
    // return null;

    // case Kind.ArrayInitializerNode:
    // checkArrayInitializerNode(md, nametable, (ArrayInitializerNode) en,
    // td);
    // return null;

    // case Kind.ClassTypeNode:
    // checkClassTypeNode(md, nametable, (ClassTypeNode) en, td);
    // return null;

    // case Kind.OffsetNode:
    // checkOffsetNode(md, nametable, (OffsetNode)en, td);
    // return null;

    }
    return null;

  }

  private void analyzeFlowCastNode(MethodDescriptor md, SymbolTable nametable, CastNode cn,
      NodeTupleSet nodeSet, NTuple<Descriptor> base, NodeTupleSet implicitFlowTupleSet) {

    analyzeFlowExpressionNode(md, nametable, cn.getExpression(), nodeSet, base,
        implicitFlowTupleSet, false);

  }

  private void analyzeFlowTertiaryNode(MethodDescriptor md, SymbolTable nametable, TertiaryNode tn,
      NodeTupleSet nodeSet, NodeTupleSet implicitFlowTupleSet) {

    NodeTupleSet tertiaryTupleNode = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, tn.getCond(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    // add edges from tertiaryTupleNode to all nodes of conditional nodes
    tertiaryTupleNode.addTupleSet(implicitFlowTupleSet);
    analyzeFlowExpressionNode(md, nametable, tn.getTrueExpr(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    analyzeFlowExpressionNode(md, nametable, tn.getFalseExpr(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    nodeSet.addTupleSet(tertiaryTupleNode);

  }

  private void addMapCallerMethodDescToMethodInvokeNodeSet(MethodDescriptor caller,
      MethodInvokeNode min) {
    Set<MethodInvokeNode> set = mapMethodDescriptorToMethodInvokeNodeSet.get(caller);
    if (set == null) {
      set = new HashSet<MethodInvokeNode>();
      mapMethodDescriptorToMethodInvokeNodeSet.put(caller, set);
    }
    set.add(min);
  }

  private void addParamNodeFlowingToReturnValue(MethodDescriptor md, FlowNode fn) {

    if (!mapMethodDescToParamNodeFlowsToReturnValue.containsKey(md)) {
      mapMethodDescToParamNodeFlowsToReturnValue.put(md, new HashSet<FlowNode>());
    }
    mapMethodDescToParamNodeFlowsToReturnValue.get(md).add(fn);
  }

  private Set<FlowNode> getParamNodeFlowingToReturnValue(MethodDescriptor md) {
    return mapMethodDescToParamNodeFlowsToReturnValue.get(md);
  }

  private void analyzeFlowMethodInvokeNode(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min, NodeTupleSet nodeSet, NodeTupleSet implicitFlowTupleSet) {

    if (nodeSet == null) {
      nodeSet = new NodeTupleSet();
    }

    addMapCallerMethodDescToMethodInvokeNodeSet(md, min);

    MethodDescriptor calleeMethodDesc = min.getMethod();

    NameDescriptor baseName = min.getBaseName();
    boolean isSystemout = false;
    if (baseName != null) {
      isSystemout = baseName.getSymbol().equals("System.out");
    }

    if (!ssjava.isSSJavaUtil(calleeMethodDesc.getClassDesc())
        && !ssjava.isTrustMethod(calleeMethodDesc) && !isSystemout) {

      FlowGraph calleeFlowGraph = getFlowGraph(calleeMethodDesc);
      Set<FlowNode> calleeReturnSet = calleeFlowGraph.getReturnNodeSet();

      if (min.getExpression() != null) {

        NodeTupleSet baseNodeSet = new NodeTupleSet();
        analyzeFlowExpressionNode(md, nametable, min.getExpression(), baseNodeSet, null,
            implicitFlowTupleSet, false);

        if (!min.getMethod().isStatic()) {
          addArgIdxMap(min, 0, baseNodeSet);

          for (Iterator iterator = calleeReturnSet.iterator(); iterator.hasNext();) {
            FlowNode returnNode = (FlowNode) iterator.next();
            NTuple<Descriptor> returnDescTuple = returnNode.getDescTuple();
            if (returnDescTuple.startsWith(calleeMethodDesc.getThis())) {
              // the location type of the return value is started with 'this'
              // reference
              for (Iterator<NTuple<Descriptor>> baseIter = baseNodeSet.iterator(); baseIter
                  .hasNext();) {
                NTuple<Descriptor> baseTuple = baseIter.next();
                NTuple<Descriptor> inFlowTuple = new NTuple<Descriptor>(baseTuple.getList());
                inFlowTuple.addAll(returnDescTuple.subList(1, returnDescTuple.size()));
                nodeSet.addTuple(inFlowTuple);
              }
            } else {
              Set<FlowNode> inFlowSet = calleeFlowGraph.getIncomingFlowNodeSet(returnNode);
              for (Iterator iterator2 = inFlowSet.iterator(); iterator2.hasNext();) {
                FlowNode inFlowNode = (FlowNode) iterator2.next();
                if (inFlowNode.getDescTuple().startsWith(calleeMethodDesc.getThis())) {
                  nodeSet.addTupleSet(baseNodeSet);
                }
              }
            }
          }
        }
      }

      // analyze parameter flows

      if (min.numArgs() > 0) {

        int offset;
        if (min.getMethod().isStatic()) {
          offset = 0;
        } else {
          offset = 1;
        }

        for (int i = 0; i < min.numArgs(); i++) {
          ExpressionNode en = min.getArg(i);
          int idx = i + offset;
          NodeTupleSet argTupleSet = new NodeTupleSet();
          analyzeFlowExpressionNode(md, nametable, en, argTupleSet, true);
          // if argument is liternal node, argTuple is set to NULL.
          addArgIdxMap(min, idx, argTupleSet);
          FlowNode paramNode = calleeFlowGraph.getParamFlowNode(idx);
          if (hasInFlowTo(calleeFlowGraph, paramNode, calleeReturnSet)
              || calleeMethodDesc.getModifiers().isNative()) {
            addParamNodeFlowingToReturnValue(calleeMethodDesc, paramNode);
            nodeSet.addTupleSet(argTupleSet);
          }
        }

      }

    }

  }

  private boolean hasInFlowTo(FlowGraph fg, FlowNode inNode, Set<FlowNode> nodeSet) {
    // return true if inNode has in-flows to nodeSet
    Set<FlowNode> reachableSet = fg.getReachableFlowNodeSet(inNode);
    for (Iterator iterator = reachableSet.iterator(); iterator.hasNext();) {
      FlowNode fn = (FlowNode) iterator.next();
      if (nodeSet.contains(fn)) {
        return true;
      }
    }
    return false;
  }

  private NodeTupleSet getNodeTupleSetByArgIdx(MethodInvokeNode min, int idx) {
    return mapMethodInvokeNodeToArgIdxMap.get(min).get(new Integer(idx));
  }

  private void addArgIdxMap(MethodInvokeNode min, int idx, NodeTupleSet tupleSet) {
    Map<Integer, NodeTupleSet> mapIdxToTupleSet = mapMethodInvokeNodeToArgIdxMap.get(min);
    if (mapIdxToTupleSet == null) {
      mapIdxToTupleSet = new HashMap<Integer, NodeTupleSet>();
      mapMethodInvokeNodeToArgIdxMap.put(min, mapIdxToTupleSet);
    }
    mapIdxToTupleSet.put(new Integer(idx), tupleSet);
  }

  private void analyzeFlowMethodParameters(MethodDescriptor callermd, SymbolTable nametable,
      MethodInvokeNode min, NodeTupleSet nodeSet) {

    if (min.numArgs() > 0) {

      int offset;
      if (min.getMethod().isStatic()) {
        offset = 0;
      } else {
        offset = 1;
        // NTuple<Descriptor> thisArgTuple = new NTuple<Descriptor>();
        // thisArgTuple.add(callermd.getThis());
        // NodeTupleSet argTupleSet = new NodeTupleSet();
        // argTupleSet.addTuple(thisArgTuple);
        // addArgIdxMap(min, 0, argTupleSet);
        // nodeSet.addTuple(thisArgTuple);
      }

      for (int i = 0; i < min.numArgs(); i++) {
        ExpressionNode en = min.getArg(i);
        NodeTupleSet argTupleSet = new NodeTupleSet();
        analyzeFlowExpressionNode(callermd, nametable, en, argTupleSet, false);
        // if argument is liternal node, argTuple is set to NULL.
        addArgIdxMap(min, i + offset, argTupleSet);
        nodeSet.addTupleSet(argTupleSet);
      }

    }

  }

  private void analyzeLiteralNode(MethodDescriptor md, SymbolTable nametable, LiteralNode en) {

  }

  private void analyzeFlowArrayAccessNode(MethodDescriptor md, SymbolTable nametable,
      ArrayAccessNode aan, NodeTupleSet nodeSet, boolean isLHS) {

    NodeTupleSet expNodeTupleSet = new NodeTupleSet();
    NTuple<Descriptor> base =
        analyzeFlowExpressionNode(md, nametable, aan.getExpression(), expNodeTupleSet, isLHS);

    NodeTupleSet idxNodeTupleSet = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, aan.getIndex(), idxNodeTupleSet, isLHS);

    if (isLHS) {
      // need to create an edge from idx to array
      for (Iterator<NTuple<Descriptor>> idxIter = idxNodeTupleSet.iterator(); idxIter.hasNext();) {
        NTuple<Descriptor> idxTuple = idxIter.next();
        for (Iterator<NTuple<Descriptor>> arrIter = expNodeTupleSet.iterator(); arrIter.hasNext();) {
          NTuple<Descriptor> arrTuple = arrIter.next();
          getFlowGraph(md).addValueFlowEdge(idxTuple, arrTuple);
        }
      }

      nodeSet.addTupleSet(expNodeTupleSet);
    } else {
      nodeSet.addTupleSet(expNodeTupleSet);
      nodeSet.addTupleSet(idxNodeTupleSet);
    }
  }

  private void analyzeCreateObjectNode(MethodDescriptor md, SymbolTable nametable,
      CreateObjectNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeFlowOpNode(MethodDescriptor md, SymbolTable nametable, OpNode on,
      NodeTupleSet nodeSet, NodeTupleSet implicitFlowTupleSet) {

    NodeTupleSet leftOpSet = new NodeTupleSet();
    NodeTupleSet rightOpSet = new NodeTupleSet();

    // left operand
    analyzeFlowExpressionNode(md, nametable, on.getLeft(), leftOpSet, null, implicitFlowTupleSet,
        false);

    if (on.getRight() != null) {
      // right operand
      analyzeFlowExpressionNode(md, nametable, on.getRight(), rightOpSet, null,
          implicitFlowTupleSet, false);
    }

    Operation op = on.getOp();

    switch (op.getOp()) {

    case Operation.UNARYPLUS:
    case Operation.UNARYMINUS:
    case Operation.LOGIC_NOT:
      // single operand
      nodeSet.addTupleSet(leftOpSet);
      break;

    case Operation.LOGIC_OR:
    case Operation.LOGIC_AND:
    case Operation.COMP:
    case Operation.BIT_OR:
    case Operation.BIT_XOR:
    case Operation.BIT_AND:
    case Operation.ISAVAILABLE:
    case Operation.EQUAL:
    case Operation.NOTEQUAL:
    case Operation.LT:
    case Operation.GT:
    case Operation.LTE:
    case Operation.GTE:
    case Operation.ADD:
    case Operation.SUB:
    case Operation.MULT:
    case Operation.DIV:
    case Operation.MOD:
    case Operation.LEFTSHIFT:
    case Operation.RIGHTSHIFT:
    case Operation.URIGHTSHIFT:

      // there are two operands
      nodeSet.addTupleSet(leftOpSet);
      nodeSet.addTupleSet(rightOpSet);
      break;

    default:
      throw new Error(op.toString());
    }

  }

  private NTuple<Descriptor> analyzeFlowNameNode(MethodDescriptor md, SymbolTable nametable,
      NameNode nn, NodeTupleSet nodeSet, NTuple<Descriptor> base, NodeTupleSet implicitFlowTupleSet) {

    if (base == null) {
      base = new NTuple<Descriptor>();
    }

    NameDescriptor nd = nn.getName();

    if (nd.getBase() != null) {
      base =
          analyzeFlowExpressionNode(md, nametable, nn.getExpression(), nodeSet, base,
              implicitFlowTupleSet, false);
      if (base == null) {
        // base node has the top location
        return base;
      }
    } else {
      String varname = nd.toString();
      if (varname.equals("this")) {
        // 'this' itself!
        base.add(md.getThis());
        return base;
      }

      Descriptor d = (Descriptor) nametable.get(varname);

      if (d instanceof VarDescriptor) {
        VarDescriptor vd = (VarDescriptor) d;
        base.add(vd);
      } else if (d instanceof FieldDescriptor) {
        // the type of field descriptor has a location!
        FieldDescriptor fd = (FieldDescriptor) d;
        if (fd.isStatic()) {
          if (fd.isFinal()) {
            // if it is 'static final', no need to have flow node for the TOP
            // location
            return null;
          } else {
            // if 'static', assign the default GLOBAL LOCATION to the first
            // element of the tuple
            base.add(GLOBALDESC);
          }
        } else {
          // the location of field access starts from this, followed by field
          // location
          base.add(md.getThis());
        }

        base.add(fd);
      } else if (d == null) {
        // access static field
        base.add(GLOBALDESC);
        // base.add(nn.getField());
        return base;

        // FieldDescriptor fd = nn.getField();addFlowGraphEdge
        //
        // MethodLattice<String> localLattice = ssjava.getMethodLattice(md);
        // String globalLocId = localLattice.getGlobalLoc();
        // if (globalLocId == null) {
        // throw new
        // Error("Method lattice does not define global variable location at "
        // + generateErrorMessage(md.getClassDesc(), nn));
        // }
        // loc.addLocation(new Location(md, globalLocId));
        //
        // Location fieldLoc = (Location) fd.getType().getExtension();
        // loc.addLocation(fieldLoc);
        //
        // return loc;

      }
    }
    getFlowGraph(md).createNewFlowNode(base);

    return base;

  }

  private NTuple<Descriptor> analyzeFlowFieldAccessNode(MethodDescriptor md, SymbolTable nametable,
      FieldAccessNode fan, NodeTupleSet nodeSet, NTuple<Descriptor> base,
      NodeTupleSet implicitFlowTupleSet, boolean isLHS) {

    ExpressionNode left = fan.getExpression();
    TypeDescriptor ltd = left.getType();
    FieldDescriptor fd = fan.getField();

    String varName = null;
    if (left.kind() == Kind.NameNode) {
      NameDescriptor nd = ((NameNode) left).getName();
      varName = nd.toString();
    }

    if (ltd.isClassNameRef() || (varName != null && varName.equals("this"))) {
      // using a class name directly or access using this
      if (fd.isStatic() && fd.isFinal()) {
        return null;
      }
    }

    NodeTupleSet idxNodeTupleSet = new NodeTupleSet();
    if (left instanceof ArrayAccessNode) {

      ArrayAccessNode aan = (ArrayAccessNode) left;
      left = aan.getExpression();
      analyzeFlowExpressionNode(md, nametable, aan.getIndex(), idxNodeTupleSet, base,
          implicitFlowTupleSet, isLHS);
      nodeSet.addTupleSet(idxNodeTupleSet);
    }
    base =
        analyzeFlowExpressionNode(md, nametable, left, nodeSet, base, implicitFlowTupleSet, isLHS);

    if (base == null) {
      // in this case, field is TOP location
      return null;
    } else {

      NTuple<Descriptor> flowFieldTuple = new NTuple<Descriptor>(base.toList());

      if (!left.getType().isPrimitive()) {

        if (!fd.getSymbol().equals("length")) {
          // array.length access, just have the location of the array
          flowFieldTuple.add(fd);
          nodeSet.removeTuple(base);
        }

      }
      getFlowGraph(md).createNewFlowNode(flowFieldTuple);

      if (isLHS) {
        for (Iterator<NTuple<Descriptor>> idxIter = idxNodeTupleSet.iterator(); idxIter.hasNext();) {
          NTuple<Descriptor> idxTuple = idxIter.next();
          getFlowGraph(md).addValueFlowEdge(idxTuple, flowFieldTuple);
        }
      }

      return flowFieldTuple;

    }

  }

  private void debug_printTreeNode(TreeNode tn) {

    System.out.println("DEBUG: " + tn.printNode(0) + "                line#=" + tn.getNumLine());

  }

  private void analyzeFlowAssignmentNode(MethodDescriptor md, SymbolTable nametable,
      AssignmentNode an, NodeTupleSet nodeSet, NTuple<Descriptor> base,
      NodeTupleSet implicitFlowTupleSet) {

    NodeTupleSet nodeSetRHS = new NodeTupleSet();
    NodeTupleSet nodeSetLHS = new NodeTupleSet();

    boolean postinc = true;
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC)) {
      postinc = false;
    }
    // if LHS is array access node, need to capture value flows between an array
    // and its index value
    analyzeFlowExpressionNode(md, nametable, an.getDest(), nodeSetLHS, null, implicitFlowTupleSet,
        true);

    if (!postinc) {
      // analyze value flows of rhs expression
      analyzeFlowExpressionNode(md, nametable, an.getSrc(), nodeSetRHS, null, implicitFlowTupleSet,
          false);

      // System.out.println("-analyzeFlowAssignmentNode=" + an.printNode(0));
      // System.out.println("-nodeSetLHS=" + nodeSetLHS);
      // System.out.println("-nodeSetRHS=" + nodeSetRHS);
      // System.out.println("-implicitFlowTupleSet=" + implicitFlowTupleSet);
      // System.out.println("-");

      if (an.getOperation().getOp() >= 2 && an.getOperation().getOp() <= 12) {
        // if assignment contains OP+EQ operator, creates edges from LHS to LHS
        for (Iterator<NTuple<Descriptor>> iter = nodeSetLHS.iterator(); iter.hasNext();) {
          NTuple<Descriptor> fromTuple = iter.next();
          for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
            NTuple<Descriptor> toTuple = iter2.next();
            addFlowGraphEdge(md, fromTuple, toTuple);
          }
        }
      }

      // creates edges from RHS to LHS
      for (Iterator<NTuple<Descriptor>> iter = nodeSetRHS.iterator(); iter.hasNext();) {
        NTuple<Descriptor> fromTuple = iter.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> toTuple = iter2.next();
          addFlowGraphEdge(md, fromTuple, toTuple);
        }
      }

      // creates edges from implicitFlowTupleSet to LHS
      for (Iterator<NTuple<Descriptor>> iter = implicitFlowTupleSet.iterator(); iter.hasNext();) {
        NTuple<Descriptor> fromTuple = iter.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> toTuple = iter2.next();
          addFlowGraphEdge(md, fromTuple, toTuple);
        }
      }

    } else {
      // postinc case
      for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
        NTuple<Descriptor> tuple = iter2.next();
        addFlowGraphEdge(md, tuple, tuple);
      }

      // creates edges from implicitFlowTupleSet to LHS
      for (Iterator<NTuple<Descriptor>> iter = implicitFlowTupleSet.iterator(); iter.hasNext();) {
        NTuple<Descriptor> fromTuple = iter.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> toTuple = iter2.next();
          addFlowGraphEdge(md, fromTuple, toTuple);
        }
      }

    }

    if (nodeSet != null) {
      nodeSet.addTupleSet(nodeSetLHS);
    }
  }

  public FlowGraph getFlowGraph(MethodDescriptor md) {
    return mapMethodDescriptorToFlowGraph.get(md);
  }

  private boolean addFlowGraphEdge(MethodDescriptor md, NTuple<Descriptor> from,
      NTuple<Descriptor> to) {
    // TODO
    // return true if it adds a new edge
    FlowGraph graph = getFlowGraph(md);
    graph.addValueFlowEdge(from, to);
    return true;
  }

  public void _debug_printGraph() {
    Set<MethodDescriptor> keySet = mapMethodDescriptorToFlowGraph.keySet();

    for (Iterator<MethodDescriptor> iterator = keySet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      FlowGraph fg = mapMethodDescriptorToFlowGraph.get(md);
      try {
        fg.writeGraph();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }

}

class CyclicFlowException extends Exception {

}
