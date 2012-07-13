package Analysis.SSJava;

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

  private Map<MethodInvokeNode, Map<Integer, NTuple<Descriptor>>> mapMethodInvokeNodeToArgIdxMap;

  private Map<MethodDescriptor, MethodLocationInfo> mapMethodDescToMethodLocationInfo;

  private Map<ClassDescriptor, LocationInfo> mapClassToLocationInfo;

  private Map<MethodDescriptor, Set<MethodDescriptor>> mapMethodDescToPossibleMethodDescSet;

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
        new HashMap<MethodInvokeNode, Map<Integer, NTuple<Descriptor>>>();
    this.mapMethodDescToMethodLocationInfo = new HashMap<MethodDescriptor, MethodLocationInfo>();
    this.mapMethodDescToPossibleMethodDescSet =
        new HashMap<MethodDescriptor, Set<MethodDescriptor>>();
    this.mapClassToLocationInfo = new HashMap<ClassDescriptor, LocationInfo>();
  }

  public void setupToAnalyze() {
    SymbolTable classtable = state.getClassSymbolTable();
    toanalyzeList.clear();
    toanalyzeList.addAll(classtable.getValueSet());
    Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
      public int compare(ClassDescriptor o1, ClassDescriptor o2) {
        return o1.getClassName().compareToIgnoreCase(o2.getClassName());
      }
    });
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

    debug_writeLatticeDotFile();

    // 3) check properties
    checkLattices();

  }

  private void checkLattices() {

    LinkedList<MethodDescriptor> descriptorListToAnalyze = ssjava.getSortedDescriptors();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    descriptorListToAnalyze.removeFirst();

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
      }

      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        if (ssjava.needTobeAnnotated(md)) {
          SSJavaLattice<String> methodLattice = md2lattice.get(md);
          if (methodLattice != null) {
            ssjava.writeLatticeDotFile(cd, md, methodLattice);
          }
        }
      }
    }

  }

  private void inferLattices() {

    // do fixed-point analysis

    LinkedList<MethodDescriptor> descriptorListToAnalyze = ssjava.getSortedDescriptors();

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by
    // dependency in the call graph
    methodDescriptorsToVisitStack.clear();

    descriptorListToAnalyze.removeFirst();

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

      System.out.println();
      System.out.println("SSJAVA: Inferencing the lattice from " + md);

      analyzeMethodLattice(md, methodLattice);

      SSJavaLattice<String> prevMethodLattice = getMethodLattice(md);

      if (!methodLattice.equals(prevMethodLattice)) {

        setMethodLattice(md, methodLattice);

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
    // LOC, RETURN LOC)

    MethodLocationInfo methodInfo1 = getMethodLocationInfo(md1);

    SSJavaLattice<String> lattice1 = getMethodLattice(md1);
    SSJavaLattice<String> lattice2 = getMethodLattice(md2);

    Set<String> paramLocNameSet1 = methodInfo1.getParameterLocNameSet();

    for (Iterator iterator = paramLocNameSet1.iterator(); iterator.hasNext();) {
      String locName1 = (String) iterator.next();
      for (Iterator iterator2 = paramLocNameSet1.iterator(); iterator2.hasNext();) {
        String locName2 = (String) iterator2.next();

        // System.out.println("COMPARE " + locName1 + " - " + locName2 + " "
        // + lattice1.isGreaterThan(locName1, locName2) + "-"
        // + lattice2.isGreaterThan(locName1, locName2));

        if (!locName1.equals(locName2)) {

          boolean r1 = lattice1.isGreaterThan(locName1, locName2);
          boolean r2 = lattice2.isGreaterThan(locName1, locName2);

          if (r1 != r2) {
            throw new Error("The method " + md1 + " is not consistent with the method " + md2
                + ".:: They have a different ordering relation between parameters " + locName1
                + " and " + locName2 + ".");
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

  private void analyzeMethodLattice(MethodDescriptor md, SSJavaLattice<String> methodLattice) {

    MethodLocationInfo methodInfo = getMethodLocationInfo(md);

    // first take a look at method invocation nodes to newly added relations
    // from the callee
    analyzeLatticeMethodInvocationNode(md);

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
            VarDescriptor varDesc = (VarDescriptor) srcNodeTuple.get(0);
            ClassDescriptor varClassDesc = varDesc.getType().getClassDesc();
            extractRelationFromFieldFlows(varClassDesc, srcNode, dstNode, 1);

          } else {
            // for the method lattice, we need to look at the first element of
            // NTuple<Descriptor>
            if (srcNodeTuple.size() == 1 || dstNodeTuple.size() == 1) {
              // in this case, take a look at connected nodes at the local level
              addRelationToLattice(md, methodLattice, srcNode, dstNode);
            }
          }

        }
      }
    }

    // grab the this location if the method use the 'this' reference
    String thisLocSymbol = md.getThis().getSymbol();
    if (methodLattice.getKeySet().contains(thisLocSymbol)) {
      methodInfo.setThisLocName(thisLocSymbol);
    }

    // calculate a return location
    if (!md.getReturnType().isVoid()) {
      Set<FlowNode> returnNodeSet = fg.getReturnNodeSet();
      Set<String> returnVarSymbolSet = new HashSet<String>();

      for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
        FlowNode rtrNode = (FlowNode) iterator.next();
        String localSymbol = rtrNode.getDescTuple().get(0).getSymbol();
        returnVarSymbolSet.add(localSymbol);
      }

      String returnGLB = methodLattice.getGLB(returnVarSymbolSet);
      if (returnGLB.equals(SSJavaAnalysis.BOTTOM)) {
        // need to insert a new location in-between the bottom and all locations
        // that is directly connected to the bottom
        String returnNewLocationSymbol = "Loc" + (SSJavaLattice.seed++);
        methodLattice.insertNewLocationAtOneLevelHigher(returnGLB, returnNewLocationSymbol);
        methodInfo.setReturnLocName(returnNewLocationSymbol);
      } else {
        methodInfo.setReturnLocName(returnGLB);
      }
    }

  }

  private void analyzeLatticeMethodInvocationNode(MethodDescriptor mdCaller) {

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
          setPossibleCallees.addAll(ssjava.getCallGraph().getMethods(mdCallee));
        }

        for (Iterator iterator2 = setPossibleCallees.iterator(); iterator2.hasNext();) {
          MethodDescriptor possibleMdCallee = (MethodDescriptor) iterator2.next();
          propagateRelationToCaller(min, mdCaller, possibleMdCallee);
        }

      }
    }

  }

  private void propagateRelationToCaller(MethodInvokeNode min, MethodDescriptor mdCaller,
      MethodDescriptor possibleMdCallee) {

    SSJavaLattice<String> calleeLattice = getMethodLattice(possibleMdCallee);

    FlowGraph calleeFlowGraph = getFlowGraph(possibleMdCallee);

    // find parameter node
    Set<FlowNode> paramNodeSet = calleeFlowGraph.getParameterNodeSet();

    for (Iterator iterator = paramNodeSet.iterator(); iterator.hasNext();) {
      FlowNode paramFlowNode1 = (FlowNode) iterator.next();

      for (Iterator iterator2 = paramNodeSet.iterator(); iterator2.hasNext();) {
        FlowNode paramFlowNode2 = (FlowNode) iterator2.next();

        String paramSymbol1 = getSymbol(0, paramFlowNode1);
        String paramSymbol2 = getSymbol(0, paramFlowNode2);
        // if two parameters have a relation, we need to propagate this relation
        // to the caller
        if (!(paramSymbol1.equals(paramSymbol2))
            && calleeLattice.isComparable(paramSymbol1, paramSymbol2)) {
          int higherLocIdxCallee;
          int lowerLocIdxCallee;
          if (calleeLattice.isGreaterThan(paramSymbol1, paramSymbol2)) {
            higherLocIdxCallee = calleeFlowGraph.getParamIdx(paramFlowNode1.getDescTuple());
            lowerLocIdxCallee = calleeFlowGraph.getParamIdx(paramFlowNode2.getDescTuple());
          } else {
            higherLocIdxCallee = calleeFlowGraph.getParamIdx(paramFlowNode2.getDescTuple());
            lowerLocIdxCallee = calleeFlowGraph.getParamIdx(paramFlowNode1.getDescTuple());
          }

          NTuple<Descriptor> higherArg = getArgTupleByArgIdx(min, higherLocIdxCallee);
          NTuple<Descriptor> lowerArg = getArgTupleByArgIdx(min, lowerLocIdxCallee);

          addFlowGraphEdge(mdCaller, higherArg, lowerArg);

        }

      }

    }

  }

  private LocationInfo getLocationInfo(Descriptor d) {
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
      FlowNode srcNode, FlowNode dstNode) {

    System.out.println("### addRelationToLattice src=" + srcNode + " dst=" + dstNode);

    // add a new binary relation of dstNode < srcNode
    FlowGraph flowGraph = getFlowGraph(md);
    MethodLocationInfo methodInfo = getMethodLocationInfo(md);

    String srcOriginSymbol = getSymbol(0, srcNode);
    String dstOriginSymbol = getSymbol(0, dstNode);

    Descriptor srcDesc = getDescriptor(0, srcNode);
    Descriptor dstDesc = getDescriptor(0, dstNode);

    String srcSymbol = methodInfo.getLocName(srcOriginSymbol);
    String dstSymbol = methodInfo.getLocName(dstOriginSymbol);

    if (srcNode.isParameter()) {
      int paramIdx = flowGraph.getParamIdx(srcNode.getDescTuple());
      methodInfo.addParameter(srcSymbol, srcDesc, paramIdx);
    } else {
      methodInfo.addMappingOfLocNameToDescriptor(srcSymbol, srcDesc);
    }

    if (dstNode.isParameter()) {
      int paramIdx = flowGraph.getParamIdx(dstNode.getDescTuple());
      methodInfo.addParameter(dstSymbol, dstDesc, paramIdx);
    } else {
      methodInfo.addMappingOfLocNameToDescriptor(dstSymbol, dstDesc);
    }

    // consider a composite location case

    boolean isSrcLocalVar = false;
    boolean isDstLocalVar = false;
    if (srcNode.getDescTuple().size() == 1) {
      isSrcLocalVar = true;
    }

    if (dstNode.getDescTuple().size() == 1) {
      isDstLocalVar = true;
    }

    boolean isAssignedCompositeLocation = false;
    if (isSrcLocalVar && isDstLocalVar) {
      // both src and dst are local var

      // CompositeLocation inferSrc=methodInfo.getInferLocation(srcNode);
      // CompositeLocation inferDst=methodInfo.getInferLocation(dstNode);
      // if( (inferSrc!=null && inferSrc.getSize()>1) || (inferDst!=null &&
      // inferDst.getSize()>1)){
      // isAssignedCompositeLocation = true;
      // }

      // TODO: need to fix
      isAssignedCompositeLocation =
          calculateCompositeLocationForLocalVar(flowGraph, methodLattice, methodInfo, srcNode);
      calculateCompositeLocationForLocalVar(flowGraph, methodLattice, methodInfo, dstNode);

    } else if (isSrcLocalVar) {
      // src is local var
      isAssignedCompositeLocation =
          calculateCompositeLocationForLocalVar(flowGraph, methodLattice, methodInfo, srcNode);
    } else if (isDstLocalVar) {
      // dst is local var
      isAssignedCompositeLocation =
          calculateCompositeLocationForLocalVar(flowGraph, methodLattice, methodInfo, dstNode);
    }

    if (!isAssignedCompositeLocation) {
      if (!methodLattice.isGreaterThan(srcSymbol, dstSymbol)) {
        // if the lattice does not have this relation, add it
        methodLattice.addRelationHigherToLower(srcSymbol, dstSymbol);
      }
    }

    // Set<String> cycleElementSet =
    // methodLattice.getPossibleCycleElements(srcSymbol, dstSymbol);
    //
    // System.out.println("### POSSIBLE CYCLE ELEMENT SET=" + cycleElementSet);
    // boolean hasNonPrimitiveElement = false;
    // for (Iterator iterator = cycleElementSet.iterator(); iterator.hasNext();)
    // {
    // String cycleElementSymbol = (String) iterator.next();
    // Set<Descriptor> flowNodeSet =
    // methodInfo.getFlowNodeSet(cycleElementSymbol);
    // for (Iterator iterator2 = flowNodeSet.iterator(); iterator2.hasNext();) {
    // Descriptor desc = (Descriptor) iterator2.next();
    // if (!isPrimitiveTypeDescriptor(desc)) {
    // hasNonPrimitiveElement = true;
    // }
    // System.out
    // .println("flowNode=" + desc + " is primitive?=" +
    // isPrimitiveTypeDescriptor(desc));
    // }
    // }
    //
    // if (hasNonPrimitiveElement) {
    // // if there is non-primitive element in the cycle, no way to merge cyclic
    // // elements into the shared location
    // System.out.println("Failed to merge cyclic value flows into a shared location.");
    //
    // // try to assign more fine-grind composite location to the source or the
    // // destination
    // System.out.println("SRC=" + srcSymbol + "    DST=" + dstSymbol);
    // System.out.println("### SRCNODE=" + srcNode + " DSTNODE=" + dstNode);
    //
    // FlowNode targetNode;
    // if (isPrimitiveLocalVariable(srcNode)) {
    // targetNode = srcNode;
    // } else {
    // targetNode = dstNode;
    // }
    //
    // calculateMoreFineGrainedLocation(md, methodLattice, targetNode);
    //
    // return;
    // }
    //
    // if (cycleElementSet.size() > 0) {
    // String newSharedLoc = "SharedLoc" + (SSJavaLattice.seed++);
    // methodLattice.mergeIntoSharedLocation(cycleElementSet, newSharedLoc);
    //
    // for (Iterator iterator = cycleElementSet.iterator(); iterator.hasNext();)
    // {
    // String locName = (String) iterator.next();
    // methodInfo.mapDescSymbolToLocName(locName, newSharedLoc);
    // }
    //
    // } else if (!methodLattice.isGreaterThan(srcSymbol, dstSymbol)) {
    // // if the lattice does not have this relation, add it
    // methodLattice.addRelationHigherToLower(srcSymbol, dstSymbol);
    // }
    //
    // System.out.println("methodLattice=" + methodLattice.getKeySet());

  }

  private void addPrefixMapping(Map<NTuple<Location>, Set<NTuple<Location>>> map,
      NTuple<Location> prefix, NTuple<Location> element) {

    if (!map.containsKey(prefix)) {
      map.put(prefix, new HashSet<NTuple<Location>>());
    }
    map.get(prefix).add(element);

  }

  private NTuple<Location> getLocationTuple(MethodLocationInfo methodInfo, FlowGraph flowGraph,
      FlowNode flowNode) {

    NTuple<Location> locTuple;
    CompositeLocation inferLoc = methodInfo.getInferLocation(flowNode);
    if (inferLoc != null) {
      // the flow node has already been assigned to the location
      locTuple = new NTuple<Location>();
      NTuple<Location> inferLocTuple = inferLoc.getTuple();
      for (int i = 0; i < inferLocTuple.size(); i++) {
        locTuple.add(inferLocTuple.get(i));
      }
    } else {
      locTuple = flowGraph.getLocationTuple(flowNode);
    }

    return locTuple;
  }

  private boolean calculateCompositeLocationForLocalVar(FlowGraph flowGraph,
      SSJavaLattice<String> methodLattice, MethodLocationInfo methodInfo, FlowNode flowNode) {

    Set<FlowNode> inNodeSet = flowGraph.getIncomingFlowNodeSet(flowNode);
    Set<FlowNode> reachableNodeSet = flowGraph.getReachableFlowNodeSet(flowNode);

    Map<NTuple<Location>, Set<NTuple<Location>>> mapPrefixToIncomingLocTupleSet =
        new HashMap<NTuple<Location>, Set<NTuple<Location>>>();

    List<NTuple<Location>> prefixList = new ArrayList<NTuple<Location>>();

    for (Iterator iterator = inNodeSet.iterator(); iterator.hasNext();) {
      FlowNode inNode = (FlowNode) iterator.next();
      NTuple<Location> inTuple = getLocationTuple(methodInfo, flowGraph, inNode);

      if (inTuple.size() > 1) {
        for (int i = 1; i < inTuple.size(); i++) {
          NTuple<Location> prefix = inTuple.subList(0, i);
          if (!prefixList.contains(prefix)) {
            prefixList.add(prefix);
          }
          addPrefixMapping(mapPrefixToIncomingLocTupleSet, prefix, inTuple);
        }
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

    // find out reachable nodes that have the longest common prefix
    for (int i = 0; i < prefixList.size(); i++) {
      NTuple<Location> curPrefix = prefixList.get(i);
      Set<NTuple<Location>> reachableCommonPrefixSet = new HashSet<NTuple<Location>>();

      for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
        FlowNode reachableNode = (FlowNode) iterator2.next();
        NTuple<Location> reachLocTuple = flowGraph.getLocationTuple(reachableNode);
        if (reachLocTuple.startsWith(curPrefix)) {
          reachableCommonPrefixSet.add(reachLocTuple);
        }
      }

      if (!reachableCommonPrefixSet.isEmpty()) {
        Set<NTuple<Location>> incomingCommonPrefixSet =
            mapPrefixToIncomingLocTupleSet.get(curPrefix);

        int idx = curPrefix.size();
        NTuple<Location> element = incomingCommonPrefixSet.iterator().next();
        Descriptor desc = element.get(idx).getDescriptor();

        SSJavaLattice<String> lattice = getLattice(desc);
        LocationInfo locInfo = getLocationInfo(desc);

        CompositeLocation inferNode = methodInfo.getInferLocation(flowNode);
        String nodeSymbol;
        if (inferNode != null) {

        } else {
          String newLocSymbol = "Loc" + (SSJavaLattice.seed++);
          inferNode = new CompositeLocation();
          for (int locIdx = 0; locIdx < curPrefix.size(); locIdx++) {
            inferNode.addLocation(curPrefix.get(locIdx));
          }
          inferNode.addLocation(new Location(desc, newLocSymbol));
          methodInfo.mapFlowNodeToInferLocation(flowNode, inferNode);
          if (flowNode.getDescTuple().size() == 1) {
            // local variable case
            modifyLocalLatticeAccrodingToNewlyAddedCompositeLocation(methodLattice, methodInfo,
                inferNode, flowNode);
          }
        }

        nodeSymbol = inferNode.get(inferNode.getSize() - 1).getLocIdentifier();

        for (Iterator iterator = incomingCommonPrefixSet.iterator(); iterator.hasNext();) {
          NTuple<Location> tuple = (NTuple<Location>) iterator.next();
          Location loc = tuple.get(idx);
          String higher = locInfo.getLocName(loc.getLocIdentifier());
          lattice.addRelationHigherToLower(higher, nodeSymbol);
        }

        for (Iterator iterator = reachableCommonPrefixSet.iterator(); iterator.hasNext();) {
          NTuple<Location> tuple = (NTuple<Location>) iterator.next();
          Location loc = tuple.get(idx);
          String lower = locInfo.getLocName(loc.getLocIdentifier());
          lattice.addRelationHigherToLower(nodeSymbol, lower);
        }

        return true;
      }

    }

    return false;

  }

  private void modifyLocalLatticeAccrodingToNewlyAddedCompositeLocation(
      SSJavaLattice<String> methodLattice, MethodLocationInfo methodInfo,
      CompositeLocation inferNode, FlowNode flowNode) {

    Location localLocation = inferNode.get(0);
    String newLocName = methodInfo.getLocName(localLocation.getLocIdentifier());
    String oldLocName = methodInfo.getLocName(flowNode.getDescTuple().get(0).getSymbol());

    methodInfo.mapDescSymbolToLocName(oldLocName, newLocName);
    methodLattice.substituteLocation(oldLocName, newLocName);

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
      FlowNode dstNode, int idx) {

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

      String srcOriginalSymbol = srcFieldDesc.getSymbol();
      String dstOriginalSymbol = dstFieldDesc.getSymbol();

      LocationInfo fieldInfo = getFieldLocationInfo(cd);

      String srcSymbol = fieldInfo.getLocName(srcOriginalSymbol);
      String dstSymbol = fieldInfo.getLocName(dstOriginalSymbol);

      Set<String> cycleElementSet = fieldLattice.getPossibleCycleElements(srcSymbol, dstSymbol);

      if (cycleElementSet.size() > 0) {
        String newSharedLoc = "SharedLoc" + (SSJavaLattice.seed++);
        fieldLattice.mergeIntoSharedLocation(cycleElementSet, newSharedLoc);

        for (Iterator iterator = cycleElementSet.iterator(); iterator.hasNext();) {
          String locName = (String) iterator.next();
          fieldInfo.mapDescSymbolToLocName(locName, newSharedLoc);
        }

      } else if (!fieldLattice.isGreaterThan(srcSymbol, dstSymbol)) {
        // if the lattice does not have this relation, add it
        fieldLattice.addRelationHigherToLower(srcSymbol, dstSymbol);
      }

    }

  }

  public SSJavaLattice<String> getFieldLattice(ClassDescriptor cd) {
    if (!cd2lattice.containsKey(cd)) {
      cd2lattice.put(cd, new SSJavaLattice<String>(SSJavaAnalysis.TOP, SSJavaAnalysis.BOTTOM));
    }
    return cd2lattice.get(cd);
  }

  public void constructFlowGraph() {

    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);
      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        if (ssjava.needTobeAnnotated(md)) {
          if (state.SSJAVADEBUG) {
            System.out.println();
            System.out.println("SSJAVA: Constructing a flow graph: " + md);
          }

          // creates a mapping from a method descriptor to virtual methods
          Set<MethodDescriptor> setPossibleCallees = new HashSet<MethodDescriptor>();
          if (md.isStatic()) {
            setPossibleCallees.add(md);
          } else {
            setPossibleCallees.addAll(ssjava.getCallGraph().getMethods(md));
          }
          mapMethodDescToPossibleMethodDescSet.put(md, setPossibleCallees);

          // creates a mapping from a parameter descriptor to its index
          Map<Descriptor, Integer> mapParamDescToIdx = new HashMap<Descriptor, Integer>();
          int offset = md.isStatic() ? 0 : 1;
          for (int i = 0; i < md.numParameters(); i++) {
            Descriptor paramDesc = (Descriptor) md.getParameter(i);
            mapParamDescToIdx.put(paramDesc, new Integer(i + offset));
          }

          FlowGraph fg = new FlowGraph(md, mapParamDescToIdx);
          mapMethodDescriptorToFlowGraph.put(md, fg);

          analyzeMethodBody(cd, md);
        }
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

    NodeTupleSet nodeSet = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, returnExp, nodeSet, false);

    FlowGraph fg = getFlowGraph(md);

    // annotate the elements of the node set as the return location
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> returnDescTuple = (NTuple<Descriptor>) iterator.next();
      fg.setReturnFlowNode(returnDescTuple);
      for (Iterator iterator2 = implicitFlowTupleSet.iterator(); iterator2.hasNext();) {
        NTuple<Descriptor> implicitFlowDescTuple = (NTuple<Descriptor>) iterator2.next();
        fg.addValueFlowEdge(implicitFlowDescTuple, returnDescTuple);
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
      analyzeFlowBlockNode(md, bn.getVarTable(), bn, implicitFlowTupleSet);
      bn.getVarTable().setParent(nametable);

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
    NTuple<Descriptor> tupleLHS = new NTuple<Descriptor>();
    tupleLHS.add(vd);
    getFlowGraph(md).createNewFlowNode(tupleLHS);

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
      analyzeFlowAssignmentNode(md, nametable, (AssignmentNode) en, base, implicitFlowTupleSet);
      break;

    case Kind.FieldAccessNode:
      flowTuple =
          analyzeFlowFieldAccessNode(md, nametable, (FieldAccessNode) en, nodeSet, base,
              implicitFlowTupleSet);
      nodeSet.addTuple(flowTuple);
      return flowTuple;

    case Kind.NameNode:
      NodeTupleSet nameNodeSet = new NodeTupleSet();
      flowTuple =
          analyzeFlowNameNode(md, nametable, (NameNode) en, nameNodeSet, base, implicitFlowTupleSet);
      nodeSet.addTuple(flowTuple);
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
      analyzeFlowMethodInvokeNode(md, nametable, (MethodInvokeNode) en, implicitFlowTupleSet);
      break;

    case Kind.TertiaryNode:
      analyzeFlowTertiaryNode(md, nametable, (TertiaryNode) en, nodeSet, implicitFlowTupleSet);
      break;

    case Kind.CastNode:
      analyzeFlowCastNode(md, nametable, (CastNode) en, implicitFlowTupleSet);
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
      NodeTupleSet implicitFlowTupleSet) {

    NodeTupleSet nodeTupleSet = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, cn.getExpression(), nodeTupleSet, false);

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

  private void analyzeFlowMethodInvokeNode(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min, NodeTupleSet implicitFlowTupleSet) {

    addMapCallerMethodDescToMethodInvokeNodeSet(md, min);

    MethodDescriptor calleeMD = min.getMethod();

    NameDescriptor baseName = min.getBaseName();
    boolean isSystemout = false;
    if (baseName != null) {
      isSystemout = baseName.getSymbol().equals("System.out");
    }

    if (!ssjava.isSSJavaUtil(calleeMD.getClassDesc()) && !ssjava.isTrustMethod(calleeMD)
        && !calleeMD.getModifiers().isNative() && !isSystemout) {

      // CompositeLocation baseLocation = null;
      if (min.getExpression() != null) {

        NodeTupleSet baseNodeSet = new NodeTupleSet();
        analyzeFlowExpressionNode(calleeMD, nametable, min.getExpression(), baseNodeSet, null,
            implicitFlowTupleSet, false);

      } else {
        if (min.getMethod().isStatic()) {
          // String globalLocId = ssjava.getMethodLattice(md).getGlobalLoc();
          // if (globalLocId == null) {
          // throw new
          // Error("Method lattice does not define global variable location at "
          // + generateErrorMessage(md.getClassDesc(), min));
          // }
          // baseLocation = new CompositeLocation(new Location(md,
          // globalLocId));
        } else {
          // 'this' var case
          // String thisLocId = ssjava.getMethodLattice(md).getThisLoc();
          // baseLocation = new CompositeLocation(new Location(md, thisLocId));
        }
      }

      // constraint case:
      // if (constraint != null) {
      // int compareResult =
      // CompositeLattice.compare(constraint, baseLocation, true,
      // generateErrorMessage(cd, min));
      // if (compareResult != ComparisonResult.GREATER) {
      // // if the current constraint is higher than method's THIS location
      // // no need to check constraints!
      // CompositeLocation calleeConstraint =
      // translateCallerLocToCalleeLoc(calleeMD, baseLocation, constraint);
      // // System.out.println("check method body for constraint:" + calleeMD +
      // // " calleeConstraint="
      // // + calleeConstraint);
      // checkMethodBody(calleeMD.getClassDesc(), calleeMD, calleeConstraint);
      // }
      // }

      analyzeFlowMethodParameters(md, nametable, min);

      // checkCalleeConstraints(md, nametable, min, baseLocation, constraint);

      // checkCallerArgumentLocationConstraints(md, nametable, min,
      // baseLocation, constraint);

      if (!min.getMethod().getReturnType().isVoid()) {
        // If method has a return value, compute the highest possible return
        // location in the caller's perspective
        // CompositeLocation ceilingLoc =
        // computeCeilingLocationForCaller(md, nametable, min, baseLocation,
        // constraint);
        // return ceilingLoc;
      }
    }

    // return new CompositeLocation(Location.createTopLocation(md));

  }

  private NTuple<Descriptor> getArgTupleByArgIdx(MethodInvokeNode min, int idx) {
    return mapMethodInvokeNodeToArgIdxMap.get(min).get(new Integer(idx));
  }

  private void addArgIdxMap(MethodInvokeNode min, int idx, NTuple<Descriptor> argTuple) {
    Map<Integer, NTuple<Descriptor>> mapIdxToArgTuple = mapMethodInvokeNodeToArgIdxMap.get(min);
    if (mapIdxToArgTuple == null) {
      mapIdxToArgTuple = new HashMap<Integer, NTuple<Descriptor>>();
      mapMethodInvokeNodeToArgIdxMap.put(min, mapIdxToArgTuple);
    }
    mapIdxToArgTuple.put(new Integer(idx), argTuple);
  }

  private void analyzeFlowMethodParameters(MethodDescriptor callermd, SymbolTable nametable,
      MethodInvokeNode min) {

    if (min.numArgs() > 0) {

      int offset = min.getMethod().isStatic() ? 0 : 1;

      for (int i = 0; i < min.numArgs(); i++) {
        ExpressionNode en = min.getArg(i);
        NTuple<Descriptor> argTuple =
            analyzeFlowExpressionNode(callermd, nametable, en, new NodeTupleSet(), false);

        addArgIdxMap(min, i + offset, argTuple);
      }

    }

  }

  private void analyzeLiteralNode(MethodDescriptor md, SymbolTable nametable, LiteralNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeFlowArrayAccessNode(MethodDescriptor md, SymbolTable nametable,
      ArrayAccessNode aan, NodeTupleSet nodeSet, boolean isLHS) {

    NodeTupleSet expNodeTupleSet = new NodeTupleSet();
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
      analyzeFlowExpressionNode(md, nametable, nn.getExpression(), nodeSet, base,
          implicitFlowTupleSet, false);
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
            // if it is 'static final', the location has TOP since no one can
            // change its value
            // loc.addLocation(Location.createTopLocation(md));
            // return loc;
          } else {
            // if 'static', the location has pre-assigned global loc
            // MethodLattice<String> localLattice = ssjava.getMethodLattice(md);
            // String globalLocId = localLattice.getGlobalLoc();
            // if (globalLocId == null) {
            // throw new
            // Error("Global location element is not defined in the method " +
            // md);
            // }
            // Location globalLoc = new Location(md, globalLocId);
            //
            // loc.addLocation(globalLoc);
          }
        } else {
          // the location of field access starts from this, followed by field
          // location
          base.add(md.getThis());
        }

        base.add(fd);
      } else if (d == null) {
        // access static field
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
      NodeTupleSet implicitFlowTupleSet) {

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
        // loc.addLocation(Location.createTopLocation(md));
        // return loc;
      }
    }

    // if (left instanceof ArrayAccessNode) {
    // ArrayAccessNode aan = (ArrayAccessNode) left;
    // left = aan.getExpression();
    // }
    // fanNodeSet
    base =
        analyzeFlowExpressionNode(md, nametable, left, nodeSet, base, implicitFlowTupleSet, false);

    if (!left.getType().isPrimitive()) {

      if (fd.getSymbol().equals("length")) {
        // TODO
        // array.length access, return the location of the array
        // return loc;
      }

      base.add(fd);
    }

    getFlowGraph(md).createNewFlowNode(base);
    return base;

  }

  private void analyzeFlowAssignmentNode(MethodDescriptor md, SymbolTable nametable,
      AssignmentNode an, NTuple<Descriptor> base, NodeTupleSet implicitFlowTupleSet) {

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
