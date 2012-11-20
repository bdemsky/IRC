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
import IR.Tree.SwitchBlockNode;
import IR.Tree.SwitchStatementNode;
import IR.Tree.TertiaryNode;
import IR.Tree.TreeNode;
import Util.Pair;

public class LocationInference {

  static int COUNT = 0;

  State state;
  SSJavaAnalysis ssjava;

  List<ClassDescriptor> temp_toanalyzeList;
  List<MethodDescriptor> temp_toanalyzeMethodList;
  Map<MethodDescriptor, FlowGraph> mapMethodDescriptorToFlowGraph;

  LinkedList<MethodDescriptor> toanalyze_methodDescList;

  InheritanceTree<ClassDescriptor> inheritanceTree;

  // map a method descriptor to its set of parameter descriptors
  Map<MethodDescriptor, Set<Descriptor>> mapMethodDescriptorToParamDescSet;

  // keep current descriptors to visit in fixed-point interprocedural analysis,
  private Stack<MethodDescriptor> methodDescriptorsToVisitStack;

  // map a class descriptor to a field lattice
  private Map<ClassDescriptor, SSJavaLattice<String>> cd2lattice;

  // map a method descriptor to a method lattice
  private Map<MethodDescriptor, SSJavaLattice<String>> md2lattice;

  // map a method/class descriptor to a hierarchy graph
  private Map<Descriptor, HierarchyGraph> mapDescriptorToHierarchyGraph;

  // map a method/class descriptor to a skeleton hierarchy graph
  private Map<Descriptor, HierarchyGraph> mapDescriptorToSkeletonHierarchyGraph;

  private Map<Descriptor, HierarchyGraph> mapDescriptorToSimpleHierarchyGraph;

  // map a method/class descriptor to a skeleton hierarchy graph with combination nodes
  private Map<Descriptor, HierarchyGraph> mapDescriptorToCombineSkeletonHierarchyGraph;

  // map a descriptor to a simple lattice
  private Map<Descriptor, SSJavaLattice<String>> mapDescriptorToSimpleLattice;

  // map a method descriptor to the set of method invocation nodes which are
  // invoked by the method descriptor
  private Map<MethodDescriptor, Set<MethodInvokeNode>> mapMethodDescriptorToMethodInvokeNodeSet;

  private Map<MethodInvokeNode, Map<Integer, NTuple<Descriptor>>> mapMethodInvokeNodeToArgIdxMap;

  private Map<MethodInvokeNode, NTuple<Descriptor>> mapMethodInvokeNodeToBaseTuple;

  private Map<MethodInvokeNode, Set<NTuple<Location>>> mapMethodInvokeNodeToPCLocTupleSet;

  private Map<MethodDescriptor, MethodLocationInfo> mapMethodDescToMethodLocationInfo;

  private Map<ClassDescriptor, LocationInfo> mapClassToLocationInfo;

  private Map<MethodDescriptor, Set<MethodDescriptor>> mapMethodToCalleeSet;

  private Map<MethodDescriptor, Set<FlowNode>> mapMethodDescToParamNodeFlowsToReturnValue;

  private Map<String, Vector<String>> mapFileNameToLineVector;

  private Map<Descriptor, Integer> mapDescToDefinitionLine;

  private Map<Descriptor, LocationSummary> mapDescToLocationSummary;

  private Map<MethodDescriptor, Set<MethodInvokeNode>> mapMethodDescToMethodInvokeNodeSet;

  // maps a method descriptor to a sub global flow graph that captures all value flows caused by the
  // set of callees reachable from the method
  private Map<MethodDescriptor, GlobalFlowGraph> mapMethodDescriptorToSubGlobalFlowGraph;

  private Map<MethodInvokeNode, Map<NTuple<Descriptor>, NTuple<Descriptor>>> mapMethodInvokeNodeToMapCallerArgToCalleeArg;

  private Map<MethodDescriptor, Boolean> mapMethodDescriptorToCompositeReturnCase;

  private Map<MethodDescriptor, MethodDescriptor> mapMethodDescToHighestOverriddenMethodDesc;

  private Map<MethodDescriptor, Set<MethodDescriptor>> mapHighestOverriddenMethodDescToMethodDescSet;

  private Map<MethodDescriptor, Set<NTuple<Descriptor>>> mapHighestOverriddenMethodDescToSetHigherThanRLoc;

  private Map<MethodDescriptor, NTuple<Descriptor>> mapHighestOverriddenMethodDescToReturnLocTuple;

  private Map<MethodDescriptor, Set<NTuple<Descriptor>>> mapHighestOverriddenMethodDescToSetLowerThanPCLoc;

  public static final String GLOBALLOC = "GLOBALLOC";

  public static final String INTERLOC = "INTERLOC";

  public static final String PCLOC = "_PCLOC_";

  public static final String RLOC = "_RLOC_";

  public static final Descriptor GLOBALDESC = new NameDescriptor(GLOBALLOC);

  public static final Descriptor TOPDESC = new NameDescriptor(SSJavaAnalysis.TOP);

  public static final Descriptor BOTTOMDESC = new NameDescriptor(SSJavaAnalysis.BOTTOM);

  public static final Descriptor RETURNLOC = new NameDescriptor(RLOC);

  public static final Descriptor LITERALDESC = new NameDescriptor("LITERAL");

  public static final HNode TOPHNODE = new HNode(TOPDESC);

  public static final HNode BOTTOMHNODE = new HNode(BOTTOMDESC);

  public static String newline = System.getProperty("line.separator");

  LocationInfo curMethodInfo;

  private boolean hasChanges = false;

  boolean debug = true;

  public static int locSeed = 0;

  private Stack<String> arrayAccessNodeStack;

  public LocationInference(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.temp_toanalyzeList = new ArrayList<ClassDescriptor>();
    this.temp_toanalyzeMethodList = new ArrayList<MethodDescriptor>();
    this.mapMethodDescriptorToFlowGraph = new HashMap<MethodDescriptor, FlowGraph>();
    this.cd2lattice = new HashMap<ClassDescriptor, SSJavaLattice<String>>();
    this.md2lattice = new HashMap<MethodDescriptor, SSJavaLattice<String>>();
    this.methodDescriptorsToVisitStack = new Stack<MethodDescriptor>();
    this.mapMethodDescriptorToMethodInvokeNodeSet =
        new HashMap<MethodDescriptor, Set<MethodInvokeNode>>();
    this.mapMethodInvokeNodeToArgIdxMap =
        new HashMap<MethodInvokeNode, Map<Integer, NTuple<Descriptor>>>();
    this.mapMethodDescToMethodLocationInfo = new HashMap<MethodDescriptor, MethodLocationInfo>();
    this.mapMethodToCalleeSet = new HashMap<MethodDescriptor, Set<MethodDescriptor>>();
    this.mapClassToLocationInfo = new HashMap<ClassDescriptor, LocationInfo>();

    this.mapFileNameToLineVector = new HashMap<String, Vector<String>>();
    this.mapDescToDefinitionLine = new HashMap<Descriptor, Integer>();
    this.mapMethodDescToParamNodeFlowsToReturnValue =
        new HashMap<MethodDescriptor, Set<FlowNode>>();

    this.mapDescriptorToHierarchyGraph = new HashMap<Descriptor, HierarchyGraph>();
    this.mapMethodInvokeNodeToBaseTuple = new HashMap<MethodInvokeNode, NTuple<Descriptor>>();

    this.mapDescriptorToSkeletonHierarchyGraph = new HashMap<Descriptor, HierarchyGraph>();
    this.mapDescriptorToCombineSkeletonHierarchyGraph = new HashMap<Descriptor, HierarchyGraph>();
    this.mapDescriptorToSimpleHierarchyGraph = new HashMap<Descriptor, HierarchyGraph>();

    this.mapDescriptorToSimpleLattice = new HashMap<Descriptor, SSJavaLattice<String>>();

    this.mapDescToLocationSummary = new HashMap<Descriptor, LocationSummary>();

    this.mapMethodDescriptorToSubGlobalFlowGraph = new HashMap<MethodDescriptor, GlobalFlowGraph>();

    this.mapMethodInvokeNodeToMapCallerArgToCalleeArg =
        new HashMap<MethodInvokeNode, Map<NTuple<Descriptor>, NTuple<Descriptor>>>();

    this.mapMethodInvokeNodeToPCLocTupleSet =
        new HashMap<MethodInvokeNode, Set<NTuple<Location>>>();

    this.arrayAccessNodeStack = new Stack<String>();

    this.mapMethodDescToMethodInvokeNodeSet =
        new HashMap<MethodDescriptor, Set<MethodInvokeNode>>();

    this.mapMethodDescriptorToCompositeReturnCase = new HashMap<MethodDescriptor, Boolean>();

    mapMethodDescToHighestOverriddenMethodDesc = new HashMap<MethodDescriptor, MethodDescriptor>();

    mapHighestOverriddenMethodDescToSetHigherThanRLoc =
        new HashMap<MethodDescriptor, Set<NTuple<Descriptor>>>();

    mapHighestOverriddenMethodDescToSetLowerThanPCLoc =
        new HashMap<MethodDescriptor, Set<NTuple<Descriptor>>>();

    mapHighestOverriddenMethodDescToMethodDescSet =
        new HashMap<MethodDescriptor, Set<MethodDescriptor>>();

    mapHighestOverriddenMethodDescToReturnLocTuple =
        new HashMap<MethodDescriptor, NTuple<Descriptor>>();

  }

  public void setupToAnalyze() {
    SymbolTable classtable = state.getClassSymbolTable();
    temp_toanalyzeList.clear();
    temp_toanalyzeList.addAll(classtable.getValueSet());
    // Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
    // public int compare(ClassDescriptor o1, ClassDescriptor o2) {
    // return o1.getClassName().compareToIgnoreCase(o2.getClassName());
    // }
    // });
  }

  public void setupToAnalazeMethod(ClassDescriptor cd) {

    SymbolTable methodtable = cd.getMethodTable();
    temp_toanalyzeMethodList.clear();
    temp_toanalyzeMethodList.addAll(methodtable.getValueSet());
    Collections.sort(temp_toanalyzeMethodList, new Comparator<MethodDescriptor>() {
      public int compare(MethodDescriptor o1, MethodDescriptor o2) {
        return o1.getSymbol().compareToIgnoreCase(o2.getSymbol());
      }
    });
  }

  public boolean toAnalyzeMethodIsEmpty() {
    return temp_toanalyzeMethodList.isEmpty();
  }

  public boolean toAnalyzeIsEmpty() {
    return temp_toanalyzeList.isEmpty();
  }

  public ClassDescriptor toAnalyzeNext() {
    return temp_toanalyzeList.remove(0);
  }

  public MethodDescriptor toAnalyzeMethodNext() {
    return temp_toanalyzeMethodList.remove(0);
  }

  public void inference() {

    ssjava.init();

    // construct value flow graph
    constructFlowGraph();

    buildInheritanceTree();

    constructGlobalFlowGraph();

    checkReturnNodes();

    assignCompositeLocation();
    updateFlowGraph();
    calculateExtraLocations();
    addAdditionalOrderingConstraints();

    _debug_writeFlowGraph();

    // System.exit(0);

    constructHierarchyGraph();

    calculateReturnPCLocInheritance();
    addInheritanceConstraints();

    debug_writeHierarchyDotFiles();

    System.exit(0);

    simplifyHierarchyGraph();

    debug_writeSimpleHierarchyDotFiles();

    constructSkeletonHierarchyGraph();

    debug_writeSkeletonHierarchyDotFiles();

    insertCombinationNodes();

    debug_writeSkeletonCombinationHierarchyDotFiles();

    buildLattice();

    debug_writeLattices();

    updateCompositeLocationAssignments();

    generateMethodSummary();

    generateAnnoatedCode();

    for (Iterator iterator = cd2lattice.keySet().iterator(); iterator.hasNext();) {
      ClassDescriptor cd = (ClassDescriptor) iterator.next();
      SSJavaLattice<String> lattice = getLattice(cd);
      HierarchyGraph hg = mapDescriptorToHierarchyGraph.get(cd);
      // System.out.println("~~~\t" + cd + "\t" + lattice.getKeySet().size() + "\t"
      // + hg.getNodeSet().size());
    }

    for (Iterator iterator = md2lattice.keySet().iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      SSJavaLattice<String> locOrder = getLattice(md);
      // writeLatticeDotFile(md.getClassDesc(), md, getMethodLattice(md));
      HierarchyGraph hg = mapDescriptorToHierarchyGraph.get(md);
      // System.out.println("~~~\t" + md.getClassDesc() + "_" + md + "\t"
      // + locOrder.getKeySet().size() + "\t" + hg.getNodeSet().size());
    }

    System.exit(0);

  }

  private void calculateReturnPCLocInheritance() {
    DFSInheritanceTreeReturnPCLoc(inheritanceTree.getRootNode());

    calculateLowestReturnLocInheritance();
  }

  private void calculateLowestReturnLocInheritance() {

    Set<MethodDescriptor> keySet = mapHighestOverriddenMethodDescToMethodDescSet.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      MethodDescriptor highestMethodDesc = (MethodDescriptor) iterator.next();

      if (getMethodSummary(highestMethodDesc).getRETURNLoc() != null) {
        Set<MethodDescriptor> methodDescSet =
            mapHighestOverriddenMethodDescToMethodDescSet.get(highestMethodDesc);
        for (Iterator iterator2 = methodDescSet.iterator(); iterator2.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) iterator2.next();
          CompositeLocation returnLoc = getMethodSummary(md).getRETURNLoc();
          if (returnLoc.getSize() == 1) {
            Set<FlowNode> paramFlowNodeFlowingToReturnValueSet =
                getParamNodeFlowingToReturnValue(md);

            if (paramFlowNodeFlowingToReturnValueSet.size() == md.numParameters()) {
              // means return loc is lower than a composite location starting with 'this'
              NTuple<Descriptor> rTuple = new NTuple<Descriptor>();
              rTuple.add(returnLoc.get(0).getLocDescriptor());
              mapHighestOverriddenMethodDescToReturnLocTuple.put(highestMethodDesc, rTuple);
            }
          } else {
            if (!mapHighestOverriddenMethodDescToReturnLocTuple.containsKey(highestMethodDesc)) {
              NTuple<Descriptor> rTuple = new NTuple<Descriptor>();
              for (int i = 0; i < returnLoc.getSize(); i++) {
                rTuple.add(returnLoc.get(i).getLocDescriptor());
              }
              mapHighestOverriddenMethodDescToReturnLocTuple.put(highestMethodDesc, rTuple);
            }
          }
        }

      }

    }

  }

  private void addMapHighestMethodDescToMethodDesc(MethodDescriptor highest, MethodDescriptor md) {
    if (!mapHighestOverriddenMethodDescToMethodDescSet.containsKey(highest)) {
      mapHighestOverriddenMethodDescToMethodDescSet.put(highest, new HashSet<MethodDescriptor>());
    }
    mapHighestOverriddenMethodDescToMethodDescSet.get(highest).add(md);
  }

  private void DFSInheritanceTreeReturnPCLoc(Node<ClassDescriptor> node) {

    ClassDescriptor cd = node.getData();

    for (Iterator iterator = cd.getMethods(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      MethodDescriptor highestMethodDesc = getHighestOverriddenMethod(md.getClassDesc(), md);
      System.out.println("md=" + md + "  highest=" + highestMethodDesc);

      mapMethodDescToHighestOverriddenMethodDesc.put(md, highestMethodDesc);
      addMapHighestMethodDescToMethodDesc(highestMethodDesc, md);

      MethodSummary summary = getMethodSummary(md);
      FlowGraph flowGraph = getFlowGraph(md);

      System.out.println("#####summary.getPCLoc()=" + summary.getPCLoc() + "  rloc="
          + summary.getRETURNLoc());

      // ////////////////////////////
      // calculate PCLOC constraints
      if (summary.getPCLoc().get(0).isTop()) {
        mapHighestOverriddenMethodDescToSetLowerThanPCLoc.put(highestMethodDesc,
            new HashSet<NTuple<Descriptor>>());
      } else {
        Set<NTuple<Descriptor>> lowerSet =
            mapHighestOverriddenMethodDescToSetLowerThanPCLoc.get(highestMethodDesc);

        if (lowerSet == null || lowerSet.size() > 0) {

          if (lowerSet == null) {
            lowerSet = new HashSet<NTuple<Descriptor>>();
            mapHighestOverriddenMethodDescToSetLowerThanPCLoc.put(highestMethodDesc, lowerSet);
          }

          CompositeLocation pcLoc = summary.getPCLoc();
          Set<FlowEdge> outEdgeSet =
              flowGraph
                  .getOutEdgeSet(flowGraph.getFlowNode(translateToDescTuple(pcLoc.getTuple())));

          for (Iterator iterator2 = outEdgeSet.iterator(); iterator2.hasNext();) {
            FlowEdge flowEdge = (FlowEdge) iterator2.next();
            lowerSet.add(flowEdge.getEndTuple());
          }
        }

        System.out.println("---lowerSet=" + lowerSet);
      }

      // ////////////////////////////
      // calculate RETURN LOC constraints
      CompositeLocation returnLoc = summary.getRETURNLoc();
      if (returnLoc != null) {
        System.out.println("md=" + md + "   returnloc=" + returnLoc);
        Set<FlowNode> inNodeSet =
            flowGraph.getIncomingFlowNodeSet(flowGraph.getFlowNode(translateToDescTuple(returnLoc
                .getTuple())));

        Set<NTuple<Descriptor>> higherSet =
            mapHighestOverriddenMethodDescToSetHigherThanRLoc.get(highestMethodDesc);
        if (higherSet == null) {
          higherSet = new HashSet<NTuple<Descriptor>>();
          mapHighestOverriddenMethodDescToSetHigherThanRLoc.put(highestMethodDesc, higherSet);
        }

        for (Iterator iterator2 = inNodeSet.iterator(); iterator2.hasNext();) {
          FlowNode flowNode = (FlowNode) iterator2.next();
          higherSet.add(flowNode.getCurrentDescTuple());
        }
      }

    }

    // traverse children
    List<Node<ClassDescriptor>> children = node.getChildren();
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      Node<ClassDescriptor> child = (Node<ClassDescriptor>) iterator.next();
      DFSInheritanceTreeReturnPCLoc(child);
    }
  }

  private void addTupleLowerThanPCLoc(NTuple<Descriptor> tuple) {

  }

  private MethodDescriptor getHighestOverriddenMethod(ClassDescriptor curClassDesc,
      MethodDescriptor curMethodDesc) {

    Node<ClassDescriptor> curNode = inheritanceTree.getTreeNode(curClassDesc);
    Node<ClassDescriptor> parentNode = curNode.getParent();

    if (parentNode != null) {
      ClassDescriptor parentClassDesc = parentNode.getData();
      if (parentClassDesc.getMethodTable().contains(curMethodDesc.getSymbol())) {
        Set<MethodDescriptor> methodDescSet =
            parentClassDesc.getMethodTable().getSet(curMethodDesc.getSymbol());
        for (Iterator iterator = methodDescSet.iterator(); iterator.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) iterator.next();
          if (md.matches(curMethodDesc)) {
            return getHighestOverriddenMethod(parentClassDesc, md);
          }
        }
      }
      // traverse to the parent!
      return getHighestOverriddenMethod(parentNode.getData(), curMethodDesc);
    }
    return curMethodDesc;
  }

  private void buildInheritanceTree() {

    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    System.out.println("methodDescList=" + methodDescList);

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();
      ClassDescriptor child = md.getClassDesc();
      ClassDescriptor parent = child.getSuperDesc();
      System.out.println("parent=" + child.getSuperDesc() + "  child=" + child);
      if (parent != null) {
        inheritanceTree.addParentChild(child.getSuperDesc(), child);
      }
    }

  }

  private void addInheritanceConstraints() {

    // DFS the inheritance tree and propagates nodes/edges of parent to child

    Node<ClassDescriptor> rootNode = inheritanceTree.getRootNode();
    DFSInheritanceTree(rootNode);

  }

  private void DFSInheritanceTree(Node<ClassDescriptor> parentNode) {

    ClassDescriptor parentClassDescriptor = parentNode.getData();

    List<Node<ClassDescriptor>> children = parentNode.getChildren();
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      Node<ClassDescriptor> childNode = (Node<ClassDescriptor>) iterator.next();
      ClassDescriptor childClassDescriptor = childNode.getData();

      HierarchyGraph parentGraph = getHierarchyGraph(parentClassDescriptor);
      HierarchyGraph childGraph = getHierarchyGraph(childClassDescriptor);

      Set<HNode> parentNodeSet = parentGraph.getNodeSet();

      // copies nodes/edges from the parent class...
      for (Iterator iterator2 = parentNodeSet.iterator(); iterator2.hasNext();) {
        HNode parentHNode = (HNode) iterator2.next();

        Set<HNode> parentIncomingHNode = parentGraph.getIncomingNodeSet(parentHNode);
        Set<HNode> parentOutgoingHNode = parentGraph.getOutgoingNodeSet(parentHNode);

        for (Iterator iterator3 = parentIncomingHNode.iterator(); iterator3.hasNext();) {
          HNode inHNode = (HNode) iterator3.next();
          childGraph.addEdge(inHNode.getDescriptor(), parentHNode.getDescriptor());
        }

        for (Iterator iterator3 = parentOutgoingHNode.iterator(); iterator3.hasNext();) {
          HNode outHNode = (HNode) iterator3.next();
          childGraph.addEdge(parentHNode.getDescriptor(), outHNode.getDescriptor());
        }

      }

      // copies nodes/edges from parent methods to overridden methods

      for (Iterator iterator3 = childClassDescriptor.getMethods(); iterator3.hasNext();) {
        MethodDescriptor childMethodDescriptor = (MethodDescriptor) iterator3.next();

        MethodDescriptor parentMethodDesc =
            getParentMethodDesc(childMethodDescriptor.getClassDesc(), childMethodDescriptor);

        if (parentMethodDesc != null) {

          HierarchyGraph parentMethodGraph = getHierarchyGraph(parentMethodDesc);
          HierarchyGraph childMethodGraph = getHierarchyGraph(childMethodDescriptor);

          // copies nodes/edges from the parent method...
          for (Iterator iterator2 = parentMethodGraph.getNodeSet().iterator(); iterator2.hasNext();) {
            HNode parentHNode = (HNode) iterator2.next();

            Set<HNode> parentIncomingHNode = parentMethodGraph.getIncomingNodeSet(parentHNode);
            Set<HNode> parentOutgoingHNode = parentMethodGraph.getOutgoingNodeSet(parentHNode);

            for (Iterator iterator4 = parentIncomingHNode.iterator(); iterator4.hasNext();) {
              HNode inHNode = (HNode) iterator4.next();
              childMethodGraph.addEdge(inHNode.getDescriptor(), parentHNode.getDescriptor());
            }

            for (Iterator iterator4 = parentOutgoingHNode.iterator(); iterator4.hasNext();) {
              HNode outHNode = (HNode) iterator4.next();
              childMethodGraph.addEdge(parentHNode.getDescriptor(), outHNode.getDescriptor());
            }

          }

        }

      }

      DFSInheritanceTree(childNode);
    }

  }

  private MethodDescriptor getParentMethodDesc(ClassDescriptor classDesc,
      MethodDescriptor methodDesc) {

    Node<ClassDescriptor> childNode = inheritanceTree.getTreeNode(classDesc);
    Node<ClassDescriptor> parentNode = childNode.getParent();

    if (parentNode != null) {
      ClassDescriptor parentClassDesc = parentNode.getData();
      if (parentClassDesc.getMethodTable().contains(methodDesc.getSymbol())) {
        Set<MethodDescriptor> methodDescSet =
            parentClassDesc.getMethodTable().getSet(methodDesc.getSymbol());
        for (Iterator iterator = methodDescSet.iterator(); iterator.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) iterator.next();
          if (md.matches(methodDesc)) {
            return md;
          }
        }
      }

      // traverse to the parent!
      getParentMethodDesc(parentNode.getData(), methodDesc);

    }

    return null;
  }

  private void checkReturnNodes() {
    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();

      if (md.getReturnType() != null && !md.getReturnType().isVoid()) {
        checkFlowNodeReturnThisField(md);
      }
      // // in this case, this method will return the composite location that starts with 'this'
      // FlowGraph flowGraph = getFlowGraph(md);
      // Set<FlowNode> returnNodeSet = flowGraph.getReturnNodeSet();
      // }

    }

  }

  private void updateFlowGraph() {

    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();
      if (state.SSJAVADEBUG) {
        System.out.println();
        System.out.println("SSJAVA: Updating a flow graph: " + md);
        propagateFlowsFromCalleesWithNoCompositeLocation(md);
      }
    }
  }

  public Map<NTuple<Descriptor>, NTuple<Descriptor>> getMapCallerArgToCalleeParam(
      MethodInvokeNode min) {

    if (!mapMethodInvokeNodeToMapCallerArgToCalleeArg.containsKey(min)) {
      mapMethodInvokeNodeToMapCallerArgToCalleeArg.put(min,
          new HashMap<NTuple<Descriptor>, NTuple<Descriptor>>());
    }

    return mapMethodInvokeNodeToMapCallerArgToCalleeArg.get(min);
  }

  public void addMapCallerArgToCalleeParam(MethodInvokeNode min, NTuple<Descriptor> callerArg,
      NTuple<Descriptor> calleeParam) {
    getMapCallerArgToCalleeParam(min).put(callerArg, calleeParam);
  }

  private void assignCompositeLocation() {
    calculateGlobalValueFlowCompositeLocation();
    translateCompositeLocationAssignmentToFlowGraph();
  }

  private void translateCompositeLocationAssignmentToFlowGraph() {
    System.out.println("\nSSJAVA: Translate composite location assignments to flow graphs:");
    MethodDescriptor methodEventLoopDesc = ssjava.getMethodContainingSSJavaLoop();
    translateCompositeLocationAssignmentToFlowGraph(methodEventLoopDesc);
  }

  private void translateCompositeLocationAssignmentToFlowGraph2() {
    System.out.println("\nSSJAVA: Translate composite location assignments to flow graphs:");
    MethodDescriptor methodEventLoopDesc = ssjava.getMethodContainingSSJavaLoop();
    translateCompositeLocationAssignmentToFlowGraph(methodEventLoopDesc);
  }

  private void addAdditionalOrderingConstraints() {
    System.out.println("\nSSJAVA: Add addtional ordering constriants:");
    MethodDescriptor methodEventLoopDesc = ssjava.getMethodContainingSSJavaLoop();
    addAddtionalOrderingConstraints(methodEventLoopDesc);
    // calculateReturnHolderLocation();
  }

  private void calculateReturnHolderLocation() {
    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();

      FlowGraph fg = getFlowGraph(md);
      Set<FlowNode> nodeSet = fg.getNodeSet();
      for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
        FlowNode flowNode = (FlowNode) iterator.next();
        if (flowNode.isFromHolder()) {
          calculateCompositeLocationFromFlowGraph(md, flowNode);
        }
      }

    }
  }

  private void updateCompositeLocationAssignments() {

    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();

      // System.out.println("\n#updateCompositeLocationAssignments=" + md);

      FlowGraph flowGraph = getFlowGraph(md);

      MethodSummary methodSummary = getMethodSummary(md);

      Set<FlowNode> nodeSet = flowGraph.getNodeSet();
      for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
        FlowNode node = (FlowNode) iterator.next();
        System.out.println("-node=" + node + "   node.getDescTuple=" + node.getDescTuple());
        if (node.getCompositeLocation() != null) {
          CompositeLocation compLoc = node.getCompositeLocation();
          CompositeLocation updatedCompLoc = updateCompositeLocation(compLoc);
          node.setCompositeLocation(updatedCompLoc);
          System.out.println("---updatedCompLoc1=" + updatedCompLoc);
        } else {
          NTuple<Descriptor> descTuple = node.getDescTuple();
          System.out.println("update desc=" + descTuple);
          CompositeLocation compLoc = convertToCompositeLocation(md, descTuple);
          compLoc = updateCompositeLocation(compLoc);
          node.setCompositeLocation(compLoc);
          System.out.println("---updatedCompLoc2=" + compLoc);
        }

        if (node.isDeclaratonNode()) {
          Descriptor localVarDesc = node.getDescTuple().get(0);
          CompositeLocation compLoc = updateCompositeLocation(node.getCompositeLocation());
          methodSummary.addMapVarNameToInferCompLoc(localVarDesc, compLoc);
        }
      }

      // update PCLOC and RETURNLOC if they have a composite location assignment
      if (methodSummary.getRETURNLoc() != null) {
        methodSummary.setRETURNLoc(updateCompositeLocation(methodSummary.getRETURNLoc()));
      }
      if (methodSummary.getPCLoc() != null) {
        methodSummary.setPCLoc(updateCompositeLocation(methodSummary.getPCLoc()));
      }

    }

  }

  private CompositeLocation updateCompositeLocation(CompositeLocation compLoc) {
    CompositeLocation updatedCompLoc = new CompositeLocation();
    for (int i = 0; i < compLoc.getSize(); i++) {
      Location loc = compLoc.get(i);
      String nodeIdentifier = loc.getLocIdentifier();
      Descriptor enclosingDesc = loc.getDescriptor();
      String locName;
      if (!enclosingDesc.equals(GLOBALDESC)) {
        LocationSummary locSummary = getLocationSummary(enclosingDesc);
        // HierarchyGraph scGraph = getSkeletonCombinationHierarchyGraph(enclosingDesc);
        HierarchyGraph scGraph = getSimpleHierarchyGraph(enclosingDesc);
        if (scGraph != null) {
          HNode curNode = scGraph.getCurrentHNode(nodeIdentifier);
          System.out.println("nodeID=" + nodeIdentifier + " curNode=" + curNode
              + "  enclosingDesc=" + enclosingDesc);
          if (curNode != null) {
            nodeIdentifier = curNode.getName();
          }
        }
        locName = locSummary.getLocationName(nodeIdentifier);
      } else {
        locName = nodeIdentifier;
      }
      Location updatedLoc = new Location(enclosingDesc, locName);
      updatedCompLoc.addLocation(updatedLoc);
    }

    return updatedCompLoc;
  }

  private void translateCompositeLocationAssignmentToFlowGraph(MethodDescriptor mdCaller) {

    // System.out.println("\n\n###translateCompositeLocationAssignmentToFlowGraph mdCaller="
    // + mdCaller);

    // First, assign a composite location to a node in the flow graph
    GlobalFlowGraph callerGlobalFlowGraph = getSubGlobalFlowGraph(mdCaller);

    FlowGraph callerFlowGraph = getFlowGraph(mdCaller);
    Map<Location, CompositeLocation> callerMapLocToCompLoc =
        callerGlobalFlowGraph.getMapLocationToInferCompositeLocation();

    Set<Location> methodLocSet = callerMapLocToCompLoc.keySet();
    for (Iterator iterator = methodLocSet.iterator(); iterator.hasNext();) {
      Location methodLoc = (Location) iterator.next();
      if (methodLoc.getDescriptor().equals(mdCaller)) {
        CompositeLocation inferCompLoc = callerMapLocToCompLoc.get(methodLoc);
        assignCompositeLocationToFlowGraph(callerFlowGraph, methodLoc, inferCompLoc);
      }
    }

    Set<MethodInvokeNode> minSet = mapMethodDescriptorToMethodInvokeNodeSet.get(mdCaller);

    Set<MethodDescriptor> calleeSet = new HashSet<MethodDescriptor>();
    for (Iterator iterator = minSet.iterator(); iterator.hasNext();) {
      MethodInvokeNode min = (MethodInvokeNode) iterator.next();
      // need to translate a composite location that is started with the base
      // tuple of 'min'.
      translateMapLocationToInferCompositeLocationToCalleeGraph(callerGlobalFlowGraph, min);
      MethodDescriptor mdCallee = min.getMethod();
      calleeSet.add(mdCallee);

    }

    for (Iterator iterator = calleeSet.iterator(); iterator.hasNext();) {
      MethodDescriptor callee = (MethodDescriptor) iterator.next();
      translateCompositeLocationAssignmentToFlowGraph(callee);
    }

  }

  private CompositeLocation translateArgCompLocToParamCompLoc(MethodInvokeNode min,
      CompositeLocation argCompLoc) {

    System.out.println("--------translateArgCompLocToParamCompLoc argCompLoc=" + argCompLoc);
    MethodDescriptor mdCallee = min.getMethod();
    FlowGraph calleeFlowGraph = getFlowGraph(mdCallee);

    NTuple<Location> argLocTuple = argCompLoc.getTuple();
    Location argLocalLoc = argLocTuple.get(0);

    Map<Integer, NTuple<Descriptor>> mapIdxToArgTuple = mapMethodInvokeNodeToArgIdxMap.get(min);
    Set<Integer> idxSet = mapIdxToArgTuple.keySet();
    for (Iterator iterator2 = idxSet.iterator(); iterator2.hasNext();) {
      Integer idx = (Integer) iterator2.next();

      if (idx == 0 && !min.getMethod().isStatic()) {
        continue;
      }

      NTuple<Descriptor> argTuple = mapIdxToArgTuple.get(idx);
      if (argTuple.size() > 0 && argTuple.get(0).equals(argLocalLoc.getLocDescriptor())) {
        // it matches with the current argument composite location
        // so what is the corresponding parameter local descriptor?
        FlowNode paramNode = calleeFlowGraph.getParamFlowNode(idx);
        // System.out.println("----------found paramNode=" + paramNode);
        NTuple<Descriptor> paramDescTuple = paramNode.getCurrentDescTuple();

        NTuple<Location> newParamTupleFromArgTuple = translateToLocTuple(mdCallee, paramDescTuple);
        for (int i = 1; i < argLocTuple.size(); i++) {
          newParamTupleFromArgTuple.add(argLocTuple.get(i));
        }

        // System.out.println("-----------newParamTuple=" + newParamTupleFromArgTuple);
        return new CompositeLocation(newParamTupleFromArgTuple);

      }
    }
    return null;
  }

  private void addAddtionalOrderingConstraints(MethodDescriptor mdCaller) {

    // First, assign a composite location to a node in the flow graph
    GlobalFlowGraph callerGlobalFlowGraph = getSubGlobalFlowGraph(mdCaller);

    FlowGraph callerFlowGraph = getFlowGraph(mdCaller);
    Map<Location, CompositeLocation> callerMapLocToCompLoc =
        callerGlobalFlowGraph.getMapLocationToInferCompositeLocation();
    Set<Location> methodLocSet = callerMapLocToCompLoc.keySet();

    Set<MethodInvokeNode> minSet = mapMethodDescriptorToMethodInvokeNodeSet.get(mdCaller);

    Set<MethodDescriptor> calleeSet = new HashSet<MethodDescriptor>();
    for (Iterator iterator = minSet.iterator(); iterator.hasNext();) {
      MethodInvokeNode min = (MethodInvokeNode) iterator.next();
      MethodDescriptor mdCallee = min.getMethod();
      calleeSet.add(mdCallee);

      //
      // add an additional ordering constraint
      // if the first element of a parameter composite location matches 'this' reference,
      // the corresponding argument in the caller is required to be higher than the translated
      // parameter location in the caller lattice
      // TODO
      // addOrderingConstraintFromCompLocParamToArg(mdCaller, min);

      //
      // update return flow nodes in the caller
      CompositeLocation returnLoc = getMethodSummary(mdCallee).getRETURNLoc();
      System.out.println("### min=" + min.printNode(0) + "  returnLoc=" + returnLoc);
      if (returnLoc != null && returnLoc.get(0).getLocDescriptor().equals(mdCallee.getThis())
          && returnLoc.getSize() > 1) {
        System.out.println("###RETURN COMP LOC=" + returnLoc);
        NTuple<Location> returnLocTuple = returnLoc.getTuple();
        NTuple<Descriptor> baseTuple = mapMethodInvokeNodeToBaseTuple.get(min);
        System.out.println("###basetuple=" + baseTuple);
        NTuple<Descriptor> newReturnTuple = baseTuple.clone();
        for (int i = 1; i < returnLocTuple.size(); i++) {
          newReturnTuple.add(returnLocTuple.get(i).getLocDescriptor());
        }
        System.out.println("###NEW RETURN TUPLE FOR CALLER=" + newReturnTuple);

        FlowReturnNode holderNode = callerFlowGraph.getFlowReturnNode(min);
        NodeTupleSet holderTupleSet =
            getNodeTupleSetFromReturnNode(getFlowGraph(mdCaller), holderNode);

        callerFlowGraph.getFlowReturnNode(min).setNewTuple(newReturnTuple);

        // then need to remove old constraints
        // TODO SAT
        System.out.println("###REMOVE OLD CONSTRAINTS=" + holderNode);
        for (Iterator<NTuple<Descriptor>> iter = holderTupleSet.iterator(); iter.hasNext();) {
          NTuple<Descriptor> tupleFromHolder = iter.next();
          Set<FlowEdge> holderOutEdge = callerFlowGraph.getOutEdgeSet(holderNode);
          for (Iterator iterator2 = holderOutEdge.iterator(); iterator2.hasNext();) {
            FlowEdge outEdge = (FlowEdge) iterator2.next();
            NTuple<Descriptor> toberemovedTuple = outEdge.getEndTuple();
            // System.out.println("---remove " + tupleFromHolder + " -> " + toberemovedTuple);
            callerFlowGraph.removeEdge(tupleFromHolder, toberemovedTuple);
          }
        }

      } else {
        // if the return loc set was empty and later pcloc was connected to the return loc
        // need to make sure that return loc reflects to this changes.
        FlowReturnNode flowReturnNode = callerFlowGraph.getFlowReturnNode(min);
        if (flowReturnNode != null && flowReturnNode.getReturnTupleSet().isEmpty()) {

          if (needToUpdateReturnLocHolder(min.getMethod(), flowReturnNode)) {
            NTuple<Descriptor> baseTuple = mapMethodInvokeNodeToBaseTuple.get(min);
            NTuple<Descriptor> newReturnTuple = baseTuple.clone();
            flowReturnNode.addTuple(newReturnTuple);
          }

        }

      }

    }

    for (Iterator iterator = calleeSet.iterator(); iterator.hasNext();) {
      MethodDescriptor callee = (MethodDescriptor) iterator.next();
      addAddtionalOrderingConstraints(callee);
    }

  }

  private boolean needToUpdateReturnLocHolder(MethodDescriptor mdCallee,
      FlowReturnNode flowReturnNode) {
    FlowGraph fg = getFlowGraph(mdCallee);
    MethodSummary summary = getMethodSummary(mdCallee);
    CompositeLocation returnCompLoc = summary.getRETURNLoc();
    NTuple<Descriptor> returnDescTuple = translateToDescTuple(returnCompLoc.getTuple());
    Set<FlowNode> incomingNodeToReturnNode =
        fg.getIncomingFlowNodeSet(fg.getFlowNode(returnDescTuple));
    for (Iterator iterator = incomingNodeToReturnNode.iterator(); iterator.hasNext();) {
      FlowNode inNode = (FlowNode) iterator.next();
      if (inNode.getDescTuple().get(0).equals(mdCallee.getThis())) {
        return true;
      }
    }
    return false;
  }

  private void addMapMethodDescToMethodInvokeNodeSet(MethodInvokeNode min) {
    MethodDescriptor md = min.getMethod();
    if (!mapMethodDescToMethodInvokeNodeSet.containsKey(md)) {
      mapMethodDescToMethodInvokeNodeSet.put(md, new HashSet<MethodInvokeNode>());
    }
    mapMethodDescToMethodInvokeNodeSet.get(md).add(min);
  }

  private Set<MethodInvokeNode> getMethodInvokeNodeSetByMethodDesc(MethodDescriptor md) {
    if (!mapMethodDescToMethodInvokeNodeSet.containsKey(md)) {
      mapMethodDescToMethodInvokeNodeSet.put(md, new HashSet<MethodInvokeNode>());
    }
    return mapMethodDescToMethodInvokeNodeSet.get(md);
  }

  private void addOrderingConstraintFromCompLocParamToArg(MethodDescriptor mdCaller,
      MethodInvokeNode min) {
    System.out.println("-addOrderingConstraintFromCompLocParamToArg=" + min.printNode(0));

    GlobalFlowGraph globalGraph = getSubGlobalFlowGraph(ssjava.getMethodContainingSSJavaLoop());

    Set<NTuple<Location>> pcLocTupleSet = getPCLocTupleSet(min);

    MethodDescriptor mdCallee = min.getMethod();

    FlowGraph calleeFlowGraph = getFlowGraph(mdCallee);
    for (int idx = 0; idx < calleeFlowGraph.getNumParameters(); idx++) {
      FlowNode paramNode = calleeFlowGraph.getParamFlowNode(idx);
      NTuple<Location> globalParamLocTuple =
          translateToLocTuple(mdCallee, paramNode.getDescTuple());
      translateToLocTuple(mdCallee, paramNode.getDescTuple());
      CompositeLocation compLoc = paramNode.getCompositeLocation();
      System.out.println("---paramNode=" + paramNode + "    compLoc=" + compLoc);
      if (compLoc != null) {
        NTuple<Descriptor> argTuple = getNodeTupleByArgIdx(min, idx);
        NTuple<Location> globalArgLocTuple = translateToLocTuple(mdCaller, argTuple);

        if (!isLiteralValueLocTuple(globalArgLocTuple)
            && !isLiteralValueLocTuple(globalParamLocTuple)) {
          if (!globalGraph.hasValueFlowEdge(globalArgLocTuple, globalParamLocTuple)) {
            System.out.println("----- add global flow globalArgLocTuple=" + globalArgLocTuple
                + "-> globalParamLocTuple=" + globalParamLocTuple);
            hasChanges = true;
            globalGraph.addValueFlowEdge(globalArgLocTuple, globalParamLocTuple);
          }
        }

        for (Iterator iterator = pcLocTupleSet.iterator(); iterator.hasNext();) {
          NTuple<Location> pcLocTuple = (NTuple<Location>) iterator.next();

          if (!isLiteralValueLocTuple(pcLocTuple) && !isLiteralValueLocTuple(globalParamLocTuple)) {
            if (!globalGraph.hasValueFlowEdge(pcLocTuple, globalParamLocTuple)) {
              System.out
                  .println("----- add global flow PCLOC="
                      + pcLocTuple
                      + "-> globalParamLocTu!globalArgLocTuple.get(0).getLocDescriptor().equals(LITERALDESC)ple="
                      + globalParamLocTuple);
              hasChanges = true;

              globalGraph.addValueFlowEdge(pcLocTuple, globalParamLocTuple);
            }
          }

        }
      }
    }
  }

  private boolean isLiteralValueLocTuple(NTuple<Location> locTuple) {
    return locTuple.get(0).getLocDescriptor().equals(LITERALDESC);
  }

  public void assignCompositeLocationToFlowGraph(FlowGraph flowGraph, Location loc,
      CompositeLocation inferCompLoc) {
    Descriptor localDesc = loc.getLocDescriptor();

    Set<FlowNode> nodeSet = flowGraph.getNodeSet();
    for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      FlowNode node = (FlowNode) iterator.next();
      if (node.getDescTuple().startsWith(localDesc)
          && !node.getDescTuple().get(0).equals(LITERALDESC)) {
        // need to assign the inferred composite location to this node
        CompositeLocation newCompLoc = generateCompositeLocation(node.getDescTuple(), inferCompLoc);
        node.setCompositeLocation(newCompLoc);
        System.out.println("SET Node=" + node + "  inferCompLoc=" + newCompLoc);
      }
    }
  }

  private CompositeLocation generateCompositeLocation(NTuple<Descriptor> nodeDescTuple,
      CompositeLocation inferCompLoc) {

    System.out.println("generateCompositeLocation=" + nodeDescTuple + " with inferCompLoc="
        + inferCompLoc);

    MethodDescriptor md = (MethodDescriptor) inferCompLoc.get(0).getDescriptor();

    CompositeLocation newCompLoc = new CompositeLocation();
    for (int i = 0; i < inferCompLoc.getSize(); i++) {
      newCompLoc.addLocation(inferCompLoc.get(i));
    }

    Descriptor lastDescOfPrefix = nodeDescTuple.get(0);
    Descriptor enclosingDescriptor;
    if (lastDescOfPrefix instanceof InterDescriptor) {
      enclosingDescriptor = getFlowGraph(md).getEnclosingDescriptor(lastDescOfPrefix);
    } else {
      enclosingDescriptor = ((VarDescriptor) lastDescOfPrefix).getType().getClassDesc();
    }

    for (int i = 1; i < nodeDescTuple.size(); i++) {
      Descriptor desc = nodeDescTuple.get(i);
      Location locElement = new Location(enclosingDescriptor, desc);
      newCompLoc.addLocation(locElement);

      enclosingDescriptor = ((FieldDescriptor) desc).getClassDescriptor();
    }

    return newCompLoc;
  }

  private void translateMapLocationToInferCompositeLocationToCalleeGraph(
      GlobalFlowGraph callerGraph, MethodInvokeNode min) {

    MethodDescriptor mdCallee = min.getMethod();
    MethodDescriptor mdCaller = callerGraph.getMethodDescriptor();
    Map<Location, CompositeLocation> callerMapLocToCompLoc =
        callerGraph.getMapLocationToInferCompositeLocation();

    Map<Integer, NTuple<Descriptor>> mapIdxToArgTuple = mapMethodInvokeNodeToArgIdxMap.get(min);

    FlowGraph calleeFlowGraph = getFlowGraph(mdCallee);
    GlobalFlowGraph calleeGlobalGraph = getSubGlobalFlowGraph(mdCallee);

    NTuple<Location> baseLocTuple = null;
    if (mapMethodInvokeNodeToBaseTuple.containsKey(min)) {
      baseLocTuple = translateToLocTuple(mdCaller, mapMethodInvokeNodeToBaseTuple.get(min));
    }

    // System.out.println("\n-#translate caller=" + mdCaller + " infer composite loc to callee="
    // + mdCallee + " baseLocTuple=" + baseLocTuple);

    Set<Location> keySet = callerMapLocToCompLoc.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Location key = (Location) iterator.next();
      CompositeLocation callerCompLoc = callerMapLocToCompLoc.get(key);

      if (!key.getDescriptor().equals(mdCaller)) {

        CompositeLocation newCalleeCompLoc;
        if (baseLocTuple != null && callerCompLoc.getTuple().startsWith(baseLocTuple)) {
          // System.out.println("-----need to translate callerCompLoc=" + callerCompLoc
          // + " with baseTuple=" + baseLocTuple);
          newCalleeCompLoc =
              translateCompositeLocationToCallee(callerCompLoc, baseLocTuple, mdCallee);

          calleeGlobalGraph.addMapLocationToInferCompositeLocation(key, newCalleeCompLoc);
          // System.out.println("1---key=" + key + "  callerCompLoc=" + callerCompLoc
          // + "  newCalleeCompLoc=" + newCalleeCompLoc);
          // System.out.println("-----caller=" + mdCaller + "    callee=" + mdCallee);
          if (!newCalleeCompLoc.get(0).getDescriptor().equals(mdCallee)) {
            System.exit(0);
          }

          // System.out.println("-----baseLoctuple=" + baseLocTuple);
        } else {
          // check if it is the global access
          Location compLocFirstElement = callerCompLoc.getTuple().get(0);
          if (compLocFirstElement.getDescriptor().equals(mdCallee)
              && compLocFirstElement.getLocDescriptor().equals(GLOBALDESC)) {

            newCalleeCompLoc = new CompositeLocation();
            Location newMethodLoc = new Location(mdCallee, GLOBALDESC);

            newCalleeCompLoc.addLocation(newMethodLoc);
            for (int i = 1; i < callerCompLoc.getSize(); i++) {
              newCalleeCompLoc.addLocation(callerCompLoc.get(i));
            }
            calleeGlobalGraph.addMapLocationToInferCompositeLocation(key, newCalleeCompLoc);
            // System.out.println("2---key=" + key + "  callerCompLoc=" + callerCompLoc
            // + "  newCalleeCompLoc=" + newCalleeCompLoc);
            // System.out.println("-----caller=" + mdCaller + "    callee=" + mdCallee);

          } else {
            int paramIdx = getParamIdx(callerCompLoc, mapIdxToArgTuple);
            if (paramIdx == -1) {
              // here, the first element of the current composite location comes from the current
              // callee
              // so transfer the same composite location to the callee
              if (!calleeGlobalGraph.contrainsInferCompositeLocationMapKey(key)) {
                if (callerCompLoc.get(0).getDescriptor().equals(mdCallee)) {
                  // System.out.println("3---key=" + key + "  callerCompLoc=" + callerCompLoc
                  // + "  newCalleeCompLoc=" + callerCompLoc);
                  // System.out.println("-----caller=" + mdCaller + "    callee=" + mdCallee);
                  calleeGlobalGraph.addMapLocationToInferCompositeLocation(key, callerCompLoc);
                } else {
                  // System.out.println("3---SKIP key=" + key + " callerCompLoc=" + callerCompLoc);
                }
              }
              continue;
            }

            // It is the case where two parameters have relative orderings between them by having
            // composite locations
            // if we found the param idx, it means that the first part of the caller composite
            // location corresponds to the one of arguments.
            // for example, if the caller argument is <<caller.this>,<Decoder.br>>
            // and the current caller composite location mapping
            // <<caller.this>,<Decoder.br>,<Br.value>>
            // and the parameter which matches with the caller argument is 'Br brParam'
            // then, the translated callee composite location will be <<callee.brParam>,<Br.value>>
            NTuple<Descriptor> argTuple = mapIdxToArgTuple.get(paramIdx);

            FlowNode paramFlowNode = calleeFlowGraph.getParamFlowNode(paramIdx);
            NTuple<Location> paramLocTuple =
                translateToLocTuple(mdCallee, paramFlowNode.getDescTuple());
            newCalleeCompLoc = new CompositeLocation();
            for (int i = 0; i < paramLocTuple.size(); i++) {
              newCalleeCompLoc.addLocation(paramLocTuple.get(i));
            }
            for (int i = argTuple.size(); i < callerCompLoc.getSize(); i++) {
              newCalleeCompLoc.addLocation(callerCompLoc.get(i));
            }
            calleeGlobalGraph.addMapLocationToInferCompositeLocation(key, newCalleeCompLoc);
            // System.out.println("4---key=" + key + "  callerCompLoc=" + callerCompLoc
            // + "  newCalleeCompLoc=" + newCalleeCompLoc);
            // System.out.println("-----caller=" + mdCaller + "    callee=" + mdCallee);

          }

        }

      }
    }

    System.out.println("#ASSIGN COMP LOC TO CALLEE PARAMS: callee=" + mdCallee + "  caller="
        + mdCaller);
    // If the location of an argument has a composite location
    // need to assign a proper composite location to the corresponding callee parameter
    Set<Integer> idxSet = mapIdxToArgTuple.keySet();
    for (Iterator iterator = idxSet.iterator(); iterator.hasNext();) {
      Integer idx = (Integer) iterator.next();

      if (idx == 0 && !min.getMethod().isStatic()) {
        continue;
      }

      NTuple<Descriptor> argTuple = mapIdxToArgTuple.get(idx);
      System.out.println("-argTuple=" + argTuple + "   idx=" + idx);
      if (argTuple.size() > 0) {
        // check if an arg tuple has been already assigned to a composite location
        NTuple<Location> argLocTuple = translateToLocTuple(mdCaller, argTuple);
        Location argLocalLoc = argLocTuple.get(0);

        // if (!isPrimitiveType(argTuple)) {
        if (callerMapLocToCompLoc.containsKey(argLocalLoc)) {

          CompositeLocation callerCompLoc = callerMapLocToCompLoc.get(argLocalLoc);
          for (int i = 1; i < argLocTuple.size(); i++) {
            callerCompLoc.addLocation(argLocTuple.get(i));
          }

          System.out.println("---callerCompLoc=" + callerCompLoc);

          // if (baseLocTuple != null && callerCompLoc.getTuple().startsWith(baseLocTuple)) {

          FlowNode calleeParamFlowNode = calleeFlowGraph.getParamFlowNode(idx);

          NTuple<Descriptor> calleeParamDescTuple = calleeParamFlowNode.getDescTuple();
          NTuple<Location> calleeParamLocTuple =
              translateToLocTuple(mdCallee, calleeParamDescTuple);

          int refParamIdx = getParamIdx(callerCompLoc, mapIdxToArgTuple);
          System.out.println("-----paramIdx=" + refParamIdx);
          if (refParamIdx == 0 && !mdCallee.isStatic()) {

            System.out.println("-------need to translate callerCompLoc=" + callerCompLoc
                + " with baseTuple=" + baseLocTuple + "   calleeParamLocTuple="
                + calleeParamLocTuple);

            CompositeLocation newCalleeCompLoc =
                translateCompositeLocationToCallee(callerCompLoc, baseLocTuple, mdCallee);

            calleeGlobalGraph.addMapLocationToInferCompositeLocation(calleeParamLocTuple.get(0),
                newCalleeCompLoc);

            System.out.println("---------key=" + calleeParamLocTuple.get(0) + "  callerCompLoc="
                + callerCompLoc + "  newCalleeCompLoc=" + newCalleeCompLoc);

          } else if (refParamIdx != -1) {
            // the first element of an argument composite location matches with one of paramtere
            // composite locations

            System.out.println("-------param match case=");

            NTuple<Descriptor> argTupleRef = mapIdxToArgTuple.get(refParamIdx);
            FlowNode refParamFlowNode = calleeFlowGraph.getParamFlowNode(refParamIdx);
            NTuple<Location> refParamLocTuple =
                translateToLocTuple(mdCallee, refParamFlowNode.getDescTuple());

            System.out.println("---------refParamLocTuple=" + refParamLocTuple
                + "  from argTupleRef=" + argTupleRef);

            CompositeLocation newCalleeCompLoc = new CompositeLocation();
            for (int i = 0; i < refParamLocTuple.size(); i++) {
              newCalleeCompLoc.addLocation(refParamLocTuple.get(i));
            }
            for (int i = argTupleRef.size(); i < callerCompLoc.getSize(); i++) {
              newCalleeCompLoc.addLocation(callerCompLoc.get(i));
            }

            calleeGlobalGraph.addMapLocationToInferCompositeLocation(calleeParamLocTuple.get(0),
                newCalleeCompLoc);

            calleeParamFlowNode.setCompositeLocation(newCalleeCompLoc);
            System.out.println("-----------key=" + calleeParamLocTuple.get(0) + "  callerCompLoc="
                + callerCompLoc + "  newCalleeCompLoc=" + newCalleeCompLoc);

          } else {
            CompositeLocation newCalleeCompLoc =
                calculateCompositeLocationFromSubGlobalGraph(mdCallee, calleeParamFlowNode);
            if (newCalleeCompLoc != null) {
              calleeGlobalGraph.addMapLocationToInferCompositeLocation(calleeParamLocTuple.get(0),
                  newCalleeCompLoc);
              calleeParamFlowNode.setCompositeLocation(newCalleeCompLoc);
            }
          }

          System.out.println("-----------------calleeParamFlowNode="
              + calleeParamFlowNode.getCompositeLocation());

          // }

        }
      }

    }

  }

  private CompositeLocation calculateCompositeLocationFromSubGlobalGraph(MethodDescriptor md,
      FlowNode paramNode) {

    System.out.println("#############################################################");
    System.out.println("calculateCompositeLocationFromSubGlobalGraph=" + paramNode);

    GlobalFlowGraph subGlobalFlowGraph = getSubGlobalFlowGraph(md);
    NTuple<Location> paramLocTuple = translateToLocTuple(md, paramNode.getDescTuple());
    GlobalFlowNode paramGlobalNode = subGlobalFlowGraph.getFlowNode(paramLocTuple);

    List<NTuple<Location>> prefixList = calculatePrefixList(subGlobalFlowGraph, paramGlobalNode);

    Location prefixLoc = paramLocTuple.get(0);

    Set<GlobalFlowNode> reachableNodeSet =
        subGlobalFlowGraph.getReachableNodeSetByPrefix(paramGlobalNode.getLocTuple().get(0));
    // Set<GlobalFlowNode> reachNodeSet = globalFlowGraph.getReachableNodeSetFrom(node);

    // System.out.println("node=" + node + "    prefixList=" + prefixList);

    for (int i = 0; i < prefixList.size(); i++) {
      NTuple<Location> curPrefix = prefixList.get(i);
      Set<NTuple<Location>> reachableCommonPrefixSet = new HashSet<NTuple<Location>>();

      for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
        GlobalFlowNode reachNode = (GlobalFlowNode) iterator2.next();
        if (reachNode.getLocTuple().startsWith(curPrefix)) {
          reachableCommonPrefixSet.add(reachNode.getLocTuple());
        }
      }
      // System.out.println("reachableCommonPrefixSet=" + reachableCommonPrefixSet);

      if (!reachableCommonPrefixSet.isEmpty()) {

        MethodDescriptor curPrefixFirstElementMethodDesc =
            (MethodDescriptor) curPrefix.get(0).getDescriptor();

        MethodDescriptor nodePrefixLocFirstElementMethodDesc =
            (MethodDescriptor) prefixLoc.getDescriptor();

        // System.out.println("curPrefixFirstElementMethodDesc=" +
        // curPrefixFirstElementMethodDesc);
        // System.out.println("nodePrefixLocFirstElementMethodDesc="
        // + nodePrefixLocFirstElementMethodDesc);

        if (curPrefixFirstElementMethodDesc.equals(nodePrefixLocFirstElementMethodDesc)
            || isTransitivelyCalledFrom(nodePrefixLocFirstElementMethodDesc,
                curPrefixFirstElementMethodDesc)) {

          // TODO
          // if (!node.getLocTuple().startsWith(curPrefix.get(0))) {

          Location curPrefixLocalLoc = curPrefix.get(0);
          if (subGlobalFlowGraph.mapLocationToInferCompositeLocation.containsKey(curPrefixLocalLoc)) {
            // in this case, the local variable of the current prefix has already got a composite
            // location
            // so we just ignore the current composite location.

            // System.out.println("HERE WE DO NOT ASSIGN A COMPOSITE LOCATION TO =" + node
            // + " DUE TO " + curPrefix);
            return null;
          }

          if (!needToGenerateCompositeLocation(paramGlobalNode, curPrefix)) {
            System.out.println("NO NEED TO GENERATE COMP LOC to " + paramGlobalNode
                + " with prefix=" + curPrefix);
            return null;
          }

          Location targetLocalLoc = paramGlobalNode.getLocTuple().get(0);
          CompositeLocation newCompLoc = generateCompositeLocation(curPrefix);
          System.out.println("NEED TO ASSIGN COMP LOC TO " + paramGlobalNode + " with prefix="
              + curPrefix);
          System.out.println("-targetLocalLoc=" + targetLocalLoc + "   - newCompLoc=" + newCompLoc);

          // makes sure that a newly generated location appears in the hierarchy graph
          for (int compIdx = 0; compIdx < newCompLoc.getSize(); compIdx++) {
            Location curLoc = newCompLoc.get(compIdx);
            getHierarchyGraph(curLoc.getDescriptor()).getHNode(curLoc.getLocDescriptor());
          }

          subGlobalFlowGraph.addMapLocationToInferCompositeLocation(targetLocalLoc, newCompLoc);

          return newCompLoc;

        }

      }

    }
    return null;
  }

  private int getParamIdx(CompositeLocation compLoc,
      Map<Integer, NTuple<Descriptor>> mapIdxToArgTuple) {

    // if the composite location is started with the argument descriptor
    // return the argument's index. o.t. return -1

    Set<Integer> keySet = mapIdxToArgTuple.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Integer key = (Integer) iterator.next();
      NTuple<Descriptor> argTuple = mapIdxToArgTuple.get(key);
      if (argTuple.size() > 0 && translateToDescTuple(compLoc.getTuple()).startsWith(argTuple)) {
        System.out.println("compLoc.getTuple=" + compLoc + " is started with " + argTuple);
        return key.intValue();
      }
    }

    return -1;
  }

  private boolean isPrimitiveType(NTuple<Descriptor> argTuple) {

    Descriptor lastDesc = argTuple.get(argTuple.size() - 1);

    if (lastDesc instanceof FieldDescriptor) {
      return ((FieldDescriptor) lastDesc).getType().isPrimitive();
    } else if (lastDesc instanceof VarDescriptor) {
      return ((VarDescriptor) lastDesc).getType().isPrimitive();
    } else if (lastDesc instanceof InterDescriptor) {
      return true;
    }

    return false;
  }

  private CompositeLocation translateCompositeLocationToCallee(CompositeLocation callerCompLoc,
      NTuple<Location> baseLocTuple, MethodDescriptor mdCallee) {

    CompositeLocation newCalleeCompLoc = new CompositeLocation();

    Location calleeThisLoc = new Location(mdCallee, mdCallee.getThis());
    newCalleeCompLoc.addLocation(calleeThisLoc);

    // remove the base tuple from the caller
    // ex; In the method invoation foo.bar.methodA(), the callee will have the composite location
    // ,which is relative to the 'this' variable, <THIS,...>
    for (int i = baseLocTuple.size(); i < callerCompLoc.getSize(); i++) {
      newCalleeCompLoc.addLocation(callerCompLoc.get(i));
    }

    return newCalleeCompLoc;

  }

  private void calculateGlobalValueFlowCompositeLocation() {

    System.out.println("SSJAVA: Calculate composite locations in the global value flow graph");
    MethodDescriptor methodDescEventLoop = ssjava.getMethodContainingSSJavaLoop();
    GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(methodDescEventLoop);

    Set<Location> calculatedPrefixSet = new HashSet<Location>();

    Set<GlobalFlowNode> nodeSet = globalFlowGraph.getNodeSet();

    next: for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      GlobalFlowNode node = (GlobalFlowNode) iterator.next();

      Location prefixLoc = node.getLocTuple().get(0);

      if (calculatedPrefixSet.contains(prefixLoc)) {
        // the prefix loc has been already assigned to a composite location
        continue;
      }

      calculatedPrefixSet.add(prefixLoc);

      // Set<GlobalFlowNode> incomingNodeSet = globalFlowGraph.getIncomingNodeSet(node);
      List<NTuple<Location>> prefixList = calculatePrefixList(globalFlowGraph, node);

      Set<GlobalFlowNode> reachableNodeSet =
          globalFlowGraph.getReachableNodeSetByPrefix(node.getLocTuple().get(0));
      // Set<GlobalFlowNode> reachNodeSet = globalFlowGraph.getReachableNodeSetFrom(node);

      // System.out.println("node=" + node + "    prefixList=" + prefixList);
      System.out.println("---prefixList=" + prefixList);

      nextprefix: for (int i = 0; i < prefixList.size(); i++) {
        NTuple<Location> curPrefix = prefixList.get(i);
        System.out.println("---curPrefix=" + curPrefix);
        Set<NTuple<Location>> reachableCommonPrefixSet = new HashSet<NTuple<Location>>();

        for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
          GlobalFlowNode reachNode = (GlobalFlowNode) iterator2.next();
          if (reachNode.getLocTuple().startsWith(curPrefix)) {
            reachableCommonPrefixSet.add(reachNode.getLocTuple());
          }
        }
        // System.out.println("reachableCommonPrefixSet=" + reachableCommonPrefixSet);

        if (!reachableCommonPrefixSet.isEmpty()) {

          MethodDescriptor curPrefixFirstElementMethodDesc =
              (MethodDescriptor) curPrefix.get(0).getDescriptor();

          MethodDescriptor nodePrefixLocFirstElementMethodDesc =
              (MethodDescriptor) prefixLoc.getDescriptor();

          // System.out.println("curPrefixFirstElementMethodDesc=" +
          // curPrefixFirstElementMethodDesc);
          // System.out.println("nodePrefixLocFirstElementMethodDesc="
          // + nodePrefixLocFirstElementMethodDesc);

          if (curPrefixFirstElementMethodDesc.equals(nodePrefixLocFirstElementMethodDesc)
              || isTransitivelyCalledFrom(nodePrefixLocFirstElementMethodDesc,
                  curPrefixFirstElementMethodDesc)) {

            // TODO
            // if (!node.getLocTuple().startsWith(curPrefix.get(0))) {

            Location curPrefixLocalLoc = curPrefix.get(0);
            if (globalFlowGraph.mapLocationToInferCompositeLocation.containsKey(curPrefixLocalLoc)) {
              // in this case, the local variable of the current prefix has already got a composite
              // location
              // so we just ignore the current composite location.

              // System.out.println("HERE WE DO NOT ASSIGN A COMPOSITE LOCATION TO =" + node
              // + " DUE TO " + curPrefix);

              continue next;
            }

            if (!needToGenerateCompositeLocation(node, curPrefix)) {
              System.out.println("NO NEED TO GENERATE COMP LOC to " + node + " with prefix="
                  + curPrefix);
              // System.out.println("prefixList=" + prefixList);
              // System.out.println("reachableNodeSet=" + reachableNodeSet);
              continue nextprefix;
            }

            Location targetLocalLoc = node.getLocTuple().get(0);
            CompositeLocation newCompLoc = generateCompositeLocation(curPrefix);
            System.out.println("NEED TO ASSIGN COMP LOC TO " + node + " with prefix=" + curPrefix);
            System.out.println("-targetLocalLoc=" + targetLocalLoc + "   - newCompLoc="
                + newCompLoc);
            globalFlowGraph.addMapLocationToInferCompositeLocation(targetLocalLoc, newCompLoc);
            // }

            continue next;
            // }

          }

        }

      }

    }
  }

  private boolean checkFlowNodeReturnThisField(MethodDescriptor md) {

    MethodDescriptor methodDescEventLoop = ssjava.getMethodContainingSSJavaLoop();
    GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(methodDescEventLoop);

    FlowGraph flowGraph = getFlowGraph(md);

    ClassDescriptor enclosingDesc = getClassTypeDescriptor(md.getThis());
    if (enclosingDesc == null) {
      return false;
    }

    int count = 0;
    Set<FlowNode> returnNodeSet = flowGraph.getReturnNodeSet();
    Set<GlobalFlowNode> globalReturnNodeSet = new HashSet<GlobalFlowNode>();
    for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
      FlowNode flowNode = (FlowNode) iterator.next();
      NTuple<Location> locTuple = translateToLocTuple(md, flowNode.getDescTuple());
      GlobalFlowNode globalReturnNode = globalFlowGraph.getFlowNode(locTuple);
      globalReturnNodeSet.add(globalReturnNode);

      List<NTuple<Location>> prefixList = calculatePrefixList(globalFlowGraph, globalReturnNode);
      for (int i = 0; i < prefixList.size(); i++) {
        NTuple<Location> curPrefix = prefixList.get(i);
        ClassDescriptor cd =
            getClassTypeDescriptor(curPrefix.get(curPrefix.size() - 1).getLocDescriptor());
        if (cd != null && cd.equals(enclosingDesc)) {
          count++;
          break;
        }
      }

    }

    if (count == returnNodeSet.size()) {
      // in this case, all return nodes in the method returns values coming from a location that
      // starts with "this"

      System.out.println("$$$SET RETURN LOC TRUE=" + md);
      mapMethodDescriptorToCompositeReturnCase.put(md, Boolean.TRUE);

      // NameDescriptor returnLocDesc = new NameDescriptor("RLOC" + (locSeed++));
      // NTuple<Descriptor> rDescTuple = new NTuple<Descriptor>();
      // rDescTuple.add(md.getThis());
      // rDescTuple.add(returnLocDesc);
      //
      // for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
      // FlowNode rnode = (FlowNode) iterator.next();
      // flowGraph.addValueFlowEdge(rnode.getDescTuple(), rDescTuple);
      // }
      //
      // getMethodSummary(md).setRETURNLoc(new CompositeLocation(translateToLocTuple(md,
      // rDescTuple)));

    } else {
      mapMethodDescriptorToCompositeReturnCase.put(md, Boolean.FALSE);
    }

    return mapMethodDescriptorToCompositeReturnCase.get(md).booleanValue();

  }

  private boolean needToGenerateCompositeLocation(GlobalFlowNode node, NTuple<Location> curPrefix) {
    // return true if there is a path between a node to which we want to give a composite location
    // and nodes which start with curPrefix

    System.out.println("---needToGenerateCompositeLocation curPrefix=" + curPrefix);

    Location targetLocalLoc = node.getLocTuple().get(0);

    MethodDescriptor md = (MethodDescriptor) targetLocalLoc.getDescriptor();
    FlowGraph flowGraph = getFlowGraph(md);

    FlowNode flowNode = flowGraph.getFlowNode(node.getDescTuple());
    Set<FlowNode> reachableSet = flowGraph.getReachFlowNodeSetFrom(flowNode);

    Set<FlowNode> paramNodeSet = flowGraph.getParamFlowNodeSet();
    for (Iterator iterator = paramNodeSet.iterator(); iterator.hasNext();) {
      FlowNode paramFlowNode = (FlowNode) iterator.next();
      if (curPrefix.startsWith(translateToLocTuple(md, paramFlowNode.getDescTuple()))) {
        return true;
      }
    }

    if (targetLocalLoc.getLocDescriptor() instanceof InterDescriptor) {
      Pair<MethodInvokeNode, Integer> pair =
          ((InterDescriptor) targetLocalLoc.getLocDescriptor()).getMethodArgIdxPair();

      if (pair != null) {
        System.out.println("$$$TARGETLOCALLOC HOLDER=" + targetLocalLoc);

        MethodInvokeNode min = pair.getFirst();
        Integer paramIdx = pair.getSecond();
        MethodDescriptor mdCallee = min.getMethod();

        FlowNode paramNode = getFlowGraph(mdCallee).getParamFlowNode(paramIdx);
        if (checkNodeReachToReturnNode(mdCallee, paramNode)) {
          return true;
        }

      }

    }

    GlobalFlowGraph subGlobalFlowGraph = getSubGlobalFlowGraph(md);
    Set<GlobalFlowNode> subGlobalReachableSet = subGlobalFlowGraph.getReachableNodeSetFrom(node);

    if (!md.isStatic()) {
      ClassDescriptor currentMethodThisType = getClassTypeDescriptor(md.getThis());
      for (int i = 0; i < curPrefix.size(); i++) {
        ClassDescriptor prefixType = getClassTypeDescriptor(curPrefix.get(i).getLocDescriptor());
        if (prefixType != null && prefixType.equals(currentMethodThisType)) {
          System.out.println("PREFIX TYPE MATCHES WITH=" + currentMethodThisType);

          if (mapMethodDescriptorToCompositeReturnCase.containsKey(md)) {
            boolean hasCompReturnLocWithThis =
                mapMethodDescriptorToCompositeReturnCase.get(md).booleanValue();
            if (hasCompReturnLocWithThis) {
              if (checkNodeReachToReturnNode(md, flowNode)) {
                return true;
              }
            }
          }

          for (Iterator iterator3 = subGlobalReachableSet.iterator(); iterator3.hasNext();) {
            GlobalFlowNode subGlobalReachalbeNode = (GlobalFlowNode) iterator3.next();
            if (subGlobalReachalbeNode.getLocTuple().get(0).getLocDescriptor().equals(md.getThis())) {
              System.out.println("PREFIX FOUND=" + subGlobalReachalbeNode);
              return true;
            }
          }
        }
      }
    }

    Location lastLocationOfPrefix = curPrefix.get(curPrefix.size() - 1);
    // check whether prefix appears in the list of parameters
    Set<MethodInvokeNode> minSet = mapMethodDescToMethodInvokeNodeSet.get(md);
    found: for (Iterator iterator = minSet.iterator(); iterator.hasNext();) {
      MethodInvokeNode min = (MethodInvokeNode) iterator.next();
      Map<Integer, NTuple<Descriptor>> map = mapMethodInvokeNodeToArgIdxMap.get(min);
      Set<Integer> keySet = map.keySet();
      // System.out.println("min=" + min.printNode(0));

      for (Iterator iterator2 = keySet.iterator(); iterator2.hasNext();) {
        Integer argIdx = (Integer) iterator2.next();
        NTuple<Descriptor> argTuple = map.get(argIdx);

        if (!(!md.isStatic() && argIdx == 0)) {
          // if the argTuple is empty, we don't need to do with anything(LITERAL CASE).
          if (argTuple.size() > 0
              && argTuple.get(argTuple.size() - 1).equals(lastLocationOfPrefix.getLocDescriptor())) {
            NTuple<Location> locTuple =
                translateToLocTuple(md, flowGraph.getParamFlowNode(argIdx).getDescTuple());
            lastLocationOfPrefix = locTuple.get(0);
            System.out.println("ARG CASE=" + locTuple);
            for (Iterator iterator3 = subGlobalReachableSet.iterator(); iterator3.hasNext();) {
              GlobalFlowNode subGlobalReachalbeNode = (GlobalFlowNode) iterator3.next();
              // NTuple<Location> locTuple = translateToLocTuple(md, reachalbeNode.getDescTuple());
              NTuple<Location> globalReachlocTuple = subGlobalReachalbeNode.getLocTuple();
              for (int i = 0; i < globalReachlocTuple.size(); i++) {
                if (globalReachlocTuple.get(i).equals(lastLocationOfPrefix)) {
                  System.out.println("ARG  " + argTuple + "  IS MATCHED WITH="
                      + lastLocationOfPrefix);
                  return true;
                }
              }
            }
          }
        }
      }
    }

    return false;
  }

  private boolean checkNodeReachToReturnNode(MethodDescriptor md, FlowNode node) {

    FlowGraph flowGraph = getFlowGraph(md);
    Set<FlowNode> reachableSet = flowGraph.getReachFlowNodeSetFrom(node);
    if (mapMethodDescriptorToCompositeReturnCase.containsKey(md)) {
      boolean hasCompReturnLocWithThis =
          mapMethodDescriptorToCompositeReturnCase.get(md).booleanValue();

      if (hasCompReturnLocWithThis) {
        for (Iterator iterator = flowGraph.getReturnNodeSet().iterator(); iterator.hasNext();) {
          FlowNode returnFlowNode = (FlowNode) iterator.next();
          if (reachableSet.contains(returnFlowNode)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void assignCompositeLocation(CompositeLocation compLocPrefix, GlobalFlowNode node) {
    CompositeLocation newCompLoc = compLocPrefix.clone();
    NTuple<Location> locTuple = node.getLocTuple();
    for (int i = 1; i < locTuple.size(); i++) {
      newCompLoc.addLocation(locTuple.get(i));
    }
    node.setInferCompositeLocation(newCompLoc);
  }

  private List<NTuple<Location>> calculatePrefixList(GlobalFlowGraph graph, GlobalFlowNode node) {

    System.out.println("\n##### calculatePrefixList node=" + node);

    Set<GlobalFlowNode> incomingNodeSetPrefix =
        graph.getIncomingNodeSetByPrefix(node.getLocTuple().get(0));
    // System.out.println("---incomingNodeSetPrefix=" + incomingNodeSetPrefix);

    Set<GlobalFlowNode> reachableNodeSetPrefix =
        graph.getReachableNodeSetByPrefix(node.getLocTuple().get(0));
    // System.out.println("---reachableNodeSetPrefix=" + reachableNodeSetPrefix);

    List<NTuple<Location>> prefixList = new ArrayList<NTuple<Location>>();

    for (Iterator iterator = incomingNodeSetPrefix.iterator(); iterator.hasNext();) {
      GlobalFlowNode inNode = (GlobalFlowNode) iterator.next();
      NTuple<Location> inNodeTuple = inNode.getLocTuple();

      if (inNodeTuple.get(0).getLocDescriptor() instanceof InterDescriptor
          || inNodeTuple.get(0).getLocDescriptor().equals(GLOBALDESC)) {
        continue;
      }

      for (int i = 1; i < inNodeTuple.size(); i++) {
        NTuple<Location> prefix = inNodeTuple.subList(0, i);
        if (!prefixList.contains(prefix)) {
          prefixList.add(prefix);
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

    return prefixList;

  }

  private CompositeLocation calculateCompositeLocationFromFlowGraph(MethodDescriptor md,
      FlowNode node) {

    System.out.println("#############################################################");
    System.out.println("calculateCompositeLocationFromFlowGraph=" + node);

    FlowGraph flowGraph = getFlowGraph(md);
    // NTuple<Location> paramLocTuple = translateToLocTuple(md, paramNode.getDescTuple());
    // GlobalFlowNode paramGlobalNode = subGlobalFlowGraph.getFlowNode(paramLocTuple);

    List<NTuple<Location>> prefixList = calculatePrefixListFlowGraph(flowGraph, node);

    // Set<GlobalFlowNode> reachableNodeSet =
    // subGlobalFlowGraph.getReachableNodeSetByPrefix(paramGlobalNode.getLocTuple().get(0));
    //
    Set<FlowNode> reachableNodeSet =
        flowGraph.getReachableSetFrom(node.getDescTuple().subList(0, 1));

    // Set<GlobalFlowNode> reachNodeSet = globalFlowGraph.getReachableNodeSetFrom(node);

    // System.out.println("node=" + node + "    prefixList=" + prefixList);

    for (int i = 0; i < prefixList.size(); i++) {
      NTuple<Location> curPrefix = prefixList.get(i);
      Set<NTuple<Location>> reachableCommonPrefixSet = new HashSet<NTuple<Location>>();

      for (Iterator iterator2 = reachableNodeSet.iterator(); iterator2.hasNext();) {
        FlowNode reachNode = (FlowNode) iterator2.next();
        NTuple<Location> reachLocTuple = translateToLocTuple(md, reachNode.getCurrentDescTuple());
        if (reachLocTuple.startsWith(curPrefix)) {
          reachableCommonPrefixSet.add(reachLocTuple);
        }
      }
      // System.out.println("reachableCommonPrefixSet=" + reachableCommonPrefixSet);

      if (!reachableCommonPrefixSet.isEmpty()) {

        MethodDescriptor curPrefixFirstElementMethodDesc =
            (MethodDescriptor) curPrefix.get(0).getDescriptor();

        Location curPrefixLocalLoc = curPrefix.get(0);

        Location targetLocalLoc = new Location(md, node.getDescTuple().get(0));
        // Location targetLocalLoc = paramGlobalNode.getLocTuple().get(0);

        CompositeLocation newCompLoc = generateCompositeLocation(curPrefix);
        System.out.println("NEED2ASSIGN COMP LOC TO " + node + " with prefix=" + curPrefix);
        System.out.println("-targetLocalLoc=" + targetLocalLoc + "   - newCompLoc=" + newCompLoc);

        node.setCompositeLocation(newCompLoc);

        return newCompLoc;

      }

    }
    return null;
  }

  private List<NTuple<Location>> calculatePrefixListFlowGraph(FlowGraph graph, FlowNode node) {

    System.out.println("\n##### calculatePrefixList node=" + node);

    MethodDescriptor md = graph.getMethodDescriptor();
    Set<FlowNode> incomingNodeSetPrefix =
        graph.getIncomingNodeSetByPrefix(node.getDescTuple().get(0));
    // System.out.println("---incomingNodeSetPrefix=" + incomingNodeSetPrefix);

    Set<FlowNode> reachableNodeSetPrefix =
        graph.getReachableSetFrom(node.getDescTuple().subList(0, 1));
    // System.out.println("---reachableNodeSetPrefix=" + reachableNodeSetPrefix);

    List<NTuple<Location>> prefixList = new ArrayList<NTuple<Location>>();

    for (Iterator iterator = incomingNodeSetPrefix.iterator(); iterator.hasNext();) {
      FlowNode inNode = (FlowNode) iterator.next();
      NTuple<Location> inNodeTuple = translateToLocTuple(md, inNode.getCurrentDescTuple());

      // if (inNodeTuple.get(0).getLocDescriptor() instanceof InterDescriptor
      // || inNodeTuple.get(0).getLocDescriptor().equals(GLOBALDESC)) {
      // continue;
      // }

      for (int i = 1; i < inNodeTuple.size(); i++) {
        NTuple<Location> prefix = inNodeTuple.subList(0, i);
        if (!prefixList.contains(prefix)) {
          prefixList.add(prefix);
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

    return prefixList;

  }

  private boolean containsClassDesc(ClassDescriptor cd, NTuple<Location> prefixLocTuple) {
    for (int i = 0; i < prefixLocTuple.size(); i++) {
      Location loc = prefixLocTuple.get(i);
      Descriptor locDesc = loc.getLocDescriptor();
      if (locDesc != null) {
        ClassDescriptor type = getClassTypeDescriptor(locDesc);
        if (type != null && type.equals(cd)) {
          return true;
        }
      }
    }
    return false;
  }

  private GlobalFlowGraph constructSubGlobalFlowGraph(FlowGraph flowGraph) {

    MethodDescriptor md = flowGraph.getMethodDescriptor();

    GlobalFlowGraph globalGraph = getSubGlobalFlowGraph(md);

    // Set<FlowNode> nodeSet = flowGraph.getNodeSet();
    Set<FlowEdge> edgeSet = flowGraph.getEdgeSet();

    for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {

      FlowEdge edge = (FlowEdge) iterator.next();
      NTuple<Descriptor> srcDescTuple = edge.getInitTuple();
      NTuple<Descriptor> dstDescTuple = edge.getEndTuple();

      if (flowGraph.getFlowNode(srcDescTuple) instanceof FlowReturnNode
          || flowGraph.getFlowNode(dstDescTuple) instanceof FlowReturnNode) {
        continue;
      }

      // here only keep the first element(method location) of the descriptor
      // tuple
      NTuple<Location> srcLocTuple = translateToLocTuple(md, srcDescTuple);
      NTuple<Location> dstLocTuple = translateToLocTuple(md, dstDescTuple);

      globalGraph.addValueFlowEdge(srcLocTuple, dstLocTuple);

    }

    return globalGraph;
  }

  private NTuple<Location> translateToLocTuple(MethodDescriptor md, NTuple<Descriptor> descTuple) {

    NTuple<Location> locTuple = new NTuple<Location>();

    Descriptor enclosingDesc = md;
    for (int i = 0; i < descTuple.size(); i++) {
      Descriptor desc = descTuple.get(i);

      Location loc = new Location(enclosingDesc, desc);
      locTuple.add(loc);

      if (desc instanceof VarDescriptor) {
        enclosingDesc = ((VarDescriptor) desc).getType().getClassDesc();
      } else if (desc instanceof FieldDescriptor) {
        enclosingDesc = ((FieldDescriptor) desc).getType().getClassDesc();
      } else {
        enclosingDesc = desc;
      }

    }

    return locTuple;

  }

  private void addValueFlowsFromCalleeSubGlobalFlowGraph(MethodDescriptor mdCaller) {

    // the transformation for a call site propagates flows through parameters
    // if the method is virtual, it also grab all relations from any possible
    // callees

    Set<MethodInvokeNode> setMethodInvokeNode = getMethodInvokeNodeSet(mdCaller);

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
        propagateValueFlowsToCallerFromSubGlobalFlowGraph(min, mdCaller, possibleMdCallee);
      }

    }

  }

  private void propagateValueFlowsToCallerFromSubGlobalFlowGraph(MethodInvokeNode min,
      MethodDescriptor mdCaller, MethodDescriptor possibleMdCallee) {

    System.out.println("---propagate from " + min.printNode(0) + " to caller=" + mdCaller);
    FlowGraph calleeFlowGraph = getFlowGraph(possibleMdCallee);
    Map<Integer, NTuple<Descriptor>> mapIdxToArg = mapMethodInvokeNodeToArgIdxMap.get(min);

    System.out.println("-----mapMethodInvokeNodeToArgIdxMap.get(min)="
        + mapMethodInvokeNodeToArgIdxMap.get(min));

    Set<Integer> keySet = mapIdxToArg.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Integer idx = (Integer) iterator.next();
      NTuple<Descriptor> argDescTuple = mapIdxToArg.get(idx);
      if (argDescTuple.size() > 0) {
        NTuple<Location> argLocTuple = translateToLocTuple(mdCaller, argDescTuple);
        NTuple<Descriptor> paramDescTuple = calleeFlowGraph.getParamFlowNode(idx).getDescTuple();
        NTuple<Location> paramLocTuple = translateToLocTuple(possibleMdCallee, paramDescTuple);
        System.out.println("-------paramDescTuple=" + paramDescTuple + "->argDescTuple="
            + argDescTuple);
        addMapCallerArgToCalleeParam(min, argDescTuple, paramDescTuple);
      }
    }

    // addValueFlowBetweenParametersToCaller(min, mdCaller, possibleMdCallee);

    NTuple<Descriptor> baseTuple = mapMethodInvokeNodeToBaseTuple.get(min);
    GlobalFlowGraph calleeSubGlobalGraph = getSubGlobalFlowGraph(possibleMdCallee);
    Set<GlobalFlowNode> calleeNodeSet = calleeSubGlobalGraph.getNodeSet();
    for (Iterator iterator = calleeNodeSet.iterator(); iterator.hasNext();) {
      GlobalFlowNode calleeNode = (GlobalFlowNode) iterator.next();
      addValueFlowFromCalleeNode(min, mdCaller, possibleMdCallee, calleeNode);
    }

    System.out.println("$$$GLOBAL PC LOC ADD=" + mdCaller);
    Set<NTuple<Location>> pcLocTupleSet = mapMethodInvokeNodeToPCLocTupleSet.get(min);
    System.out.println("---pcLocTupleSet=" + pcLocTupleSet);
    GlobalFlowGraph callerSubGlobalGraph = getSubGlobalFlowGraph(mdCaller);
    for (Iterator iterator = calleeNodeSet.iterator(); iterator.hasNext();) {
      GlobalFlowNode calleeNode = (GlobalFlowNode) iterator.next();
      if (calleeNode.isParamNodeWithIncomingFlows()) {
        System.out.println("calleeNode.getLocTuple()" + calleeNode.getLocTuple());
        NTuple<Location> callerSrcNodeLocTuple =
            translateToCallerLocTuple(min, possibleMdCallee, mdCaller, calleeNode.getLocTuple());
        System.out.println("---callerSrcNodeLocTuple=" + callerSrcNodeLocTuple);
        if (callerSrcNodeLocTuple != null && callerSrcNodeLocTuple.size() > 0) {
          for (Iterator iterator2 = pcLocTupleSet.iterator(); iterator2.hasNext();) {
            NTuple<Location> pcLocTuple = (NTuple<Location>) iterator2.next();

            callerSubGlobalGraph.addValueFlowEdge(pcLocTuple, callerSrcNodeLocTuple);
          }
        }
      }

    }

  }

  private void addValueFlowFromCalleeNode(MethodInvokeNode min, MethodDescriptor mdCaller,
      MethodDescriptor mdCallee, GlobalFlowNode calleeSrcNode) {

    GlobalFlowGraph calleeSubGlobalGraph = getSubGlobalFlowGraph(mdCallee);
    GlobalFlowGraph callerSubGlobalGraph = getSubGlobalFlowGraph(mdCaller);

    System.out.println("$addValueFlowFromCalleeNode calleeSrcNode=" + calleeSrcNode);

    NTuple<Location> callerSrcNodeLocTuple =
        translateToCallerLocTuple(min, mdCallee, mdCaller, calleeSrcNode.getLocTuple());
    System.out.println("---callerSrcNodeLocTuple=" + callerSrcNodeLocTuple);

    if (callerSrcNodeLocTuple != null && callerSrcNodeLocTuple.size() > 0) {

      Set<GlobalFlowNode> outNodeSet = calleeSubGlobalGraph.getOutNodeSet(calleeSrcNode);

      for (Iterator iterator = outNodeSet.iterator(); iterator.hasNext();) {
        GlobalFlowNode outNode = (GlobalFlowNode) iterator.next();
        NTuple<Location> callerDstNodeLocTuple =
            translateToCallerLocTuple(min, mdCallee, mdCaller, outNode.getLocTuple());
        // System.out.println("outNode=" + outNode + "   callerDstNodeLocTuple="
        // + callerDstNodeLocTuple);
        if (callerSrcNodeLocTuple != null && callerDstNodeLocTuple != null
            && callerSrcNodeLocTuple.size() > 0 && callerDstNodeLocTuple.size() > 0) {
          callerSubGlobalGraph.addValueFlowEdge(callerSrcNodeLocTuple, callerDstNodeLocTuple);
        }
      }
    }

  }

  private NTuple<Location> translateToCallerLocTuple(MethodInvokeNode min,
      MethodDescriptor mdCallee, MethodDescriptor mdCaller, NTuple<Location> nodeLocTuple) {
    // this method will return the same nodeLocTuple if the corresponding argument is literal
    // value.

    // System.out.println("translateToCallerLocTuple=" + nodeLocTuple);

    FlowGraph calleeFlowGraph = getFlowGraph(mdCallee);
    NTuple<Descriptor> nodeDescTuple = translateToDescTuple(nodeLocTuple);
    if (calleeFlowGraph.isParameter(nodeDescTuple)) {
      int paramIdx = calleeFlowGraph.getParamIdx(nodeDescTuple);
      NTuple<Descriptor> argDescTuple = mapMethodInvokeNodeToArgIdxMap.get(min).get(paramIdx);

      // if (isPrimitive(nodeLocTuple.get(0).getLocDescriptor())) {
      // // the type of argument is primitive.
      // return nodeLocTuple.clone();
      // }
      // System.out.println("paramIdx=" + paramIdx + "  argDescTuple=" + argDescTuple + " from min="
      // + min.printNode(0));
      NTuple<Location> argLocTuple = translateToLocTuple(mdCaller, argDescTuple);

      NTuple<Location> callerLocTuple = new NTuple<Location>();

      callerLocTuple.addAll(argLocTuple);
      for (int i = 1; i < nodeLocTuple.size(); i++) {
        callerLocTuple.add(nodeLocTuple.get(i));
      }
      return callerLocTuple;
    } else {
      return nodeLocTuple.clone();
    }

  }

  public static boolean isPrimitive(Descriptor desc) {

    if (desc instanceof FieldDescriptor) {
      return ((FieldDescriptor) desc).getType().isPrimitive();
    } else if (desc instanceof VarDescriptor) {
      return ((VarDescriptor) desc).getType().isPrimitive();
    } else if (desc instanceof InterDescriptor) {
      return true;
    }

    return false;
  }

  public static boolean isReference(Descriptor desc) {

    if (desc instanceof FieldDescriptor) {

      TypeDescriptor type = ((FieldDescriptor) desc).getType();
      if (type.isArray()) {
        return !type.isPrimitive();
      } else {
        return type.isPtr();
      }

    } else if (desc instanceof VarDescriptor) {
      TypeDescriptor type = ((VarDescriptor) desc).getType();
      if (type.isArray()) {
        return !type.isPrimitive();
      } else {
        return type.isPtr();
      }
    }

    return false;
  }

  private NTuple<Descriptor> translateToDescTuple(NTuple<Location> locTuple) {

    NTuple<Descriptor> descTuple = new NTuple<Descriptor>();
    for (int i = 0; i < locTuple.size(); i++) {
      descTuple.add(locTuple.get(i).getLocDescriptor());
    }
    return descTuple;

  }

  public LocationSummary getLocationSummary(Descriptor d) {
    if (!mapDescToLocationSummary.containsKey(d)) {
      if (d instanceof MethodDescriptor) {
        mapDescToLocationSummary.put(d, new MethodSummary((MethodDescriptor) d));
      } else if (d instanceof ClassDescriptor) {
        mapDescToLocationSummary.put(d, new FieldSummary());
      }
    }
    return mapDescToLocationSummary.get(d);
  }

  private void generateMethodSummary() {

    Set<MethodDescriptor> keySet = md2lattice.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();

      System.out.println("\nSSJAVA: generate method summary: " + md);

      FlowGraph flowGraph = getFlowGraph(md);
      MethodSummary methodSummary = getMethodSummary(md);

      HierarchyGraph scGraph = getSkeletonCombinationHierarchyGraph(md);

      // set the 'this' reference location
      if (!md.isStatic()) {
        System.out.println("setThisLocName=" + scGraph.getHNode(md.getThis()).getName());
        methodSummary.setThisLocName(scGraph.getHNode(md.getThis()).getName());
      }

      // set the 'global' reference location if needed
      if (methodSummary.hasGlobalAccess()) {
        methodSummary.setGlobalLocName(scGraph.getHNode(GLOBALDESC).getName());
      }

      // construct a parameter mapping that maps a parameter descriptor to an
      // inferred composite location
      for (int paramIdx = 0; paramIdx < flowGraph.getNumParameters(); paramIdx++) {
        FlowNode flowNode = flowGraph.getParamFlowNode(paramIdx);
        CompositeLocation inferredCompLoc =
            updateCompositeLocation(flowNode.getCompositeLocation());
        System.out.println("-paramIdx=" + paramIdx + "   infer=" + inferredCompLoc + " original="
            + flowNode.getCompositeLocation());

        Descriptor localVarDesc = flowNode.getDescTuple().get(0);
        methodSummary.addMapVarNameToInferCompLoc(localVarDesc, inferredCompLoc);
        methodSummary.addMapParamIdxToInferLoc(paramIdx, inferredCompLoc);
      }

    }

  }

  private boolean hasOrderingRelation(NTuple<Location> locTuple1, NTuple<Location> locTuple2) {

    int size = locTuple1.size() >= locTuple2.size() ? locTuple2.size() : locTuple1.size();

    for (int idx = 0; idx < size; idx++) {
      Location loc1 = locTuple1.get(idx);
      Location loc2 = locTuple2.get(idx);

      Descriptor desc1 = loc1.getDescriptor();
      Descriptor desc2 = loc2.getDescriptor();

      if (!desc1.equals(desc2)) {
        throw new Error("Fail to compare " + locTuple1 + " and " + locTuple2);
      }

      Descriptor locDesc1 = loc1.getLocDescriptor();
      Descriptor locDesc2 = loc2.getLocDescriptor();

      HierarchyGraph hierarchyGraph = getHierarchyGraph(desc1);

      HNode node1 = hierarchyGraph.getHNode(locDesc1);
      HNode node2 = hierarchyGraph.getHNode(locDesc2);

      System.out.println("---node1=" + node1 + "  node2=" + node2);
      System.out.println("---hierarchyGraph.getIncomingNodeSet(node2)="
          + hierarchyGraph.getIncomingNodeSet(node2));

      if (locDesc1.equals(locDesc2)) {
        continue;
      } else if (!hierarchyGraph.getIncomingNodeSet(node2).contains(node1)
          && !hierarchyGraph.getIncomingNodeSet(node1).contains(node2)) {
        return false;
      } else {
        return true;
      }

    }

    return false;

  }

  private boolean isHigherThan(NTuple<Location> locTuple1, NTuple<Location> locTuple2) {

    int size = locTuple1.size() >= locTuple2.size() ? locTuple2.size() : locTuple1.size();

    for (int idx = 0; idx < size; idx++) {
      Location loc1 = locTuple1.get(idx);
      Location loc2 = locTuple2.get(idx);

      Descriptor desc1 = loc1.getDescriptor();
      Descriptor desc2 = loc2.getDescriptor();

      if (!desc1.equals(desc2)) {
        throw new Error("Fail to compare " + locTuple1 + " and " + locTuple2);
      }

      Descriptor locDesc1 = loc1.getLocDescriptor();
      Descriptor locDesc2 = loc2.getLocDescriptor();

      HierarchyGraph hierarchyGraph = getHierarchyGraph(desc1);

      HNode node1 = hierarchyGraph.getHNode(locDesc1);
      HNode node2 = hierarchyGraph.getHNode(locDesc2);

      System.out.println("---node1=" + node1 + "  node2=" + node2);
      System.out.println("---hierarchyGraph.getIncomingNodeSet(node2)="
          + hierarchyGraph.getIncomingNodeSet(node2));

      if (locDesc1.equals(locDesc2)) {
        continue;
      } else if (hierarchyGraph.getIncomingNodeSet(node2).contains(node1)) {
        return true;
      } else {
        return false;
      }

    }

    return false;
  }

  private CompositeLocation translateCompositeLocation(CompositeLocation compLoc) {
    CompositeLocation newCompLoc = new CompositeLocation();

    // System.out.println("compLoc=" + compLoc);
    for (int i = 0; i < compLoc.getSize(); i++) {
      Location loc = compLoc.get(i);
      Descriptor enclosingDescriptor = loc.getDescriptor();
      Descriptor locDescriptor = loc.getLocDescriptor();

      HNode hnode = getHierarchyGraph(enclosingDescriptor).getHNode(locDescriptor);
      // System.out.println("-hnode=" + hnode + "    from=" + locDescriptor +
      // " enclosingDescriptor="
      // + enclosingDescriptor);
      // System.out.println("-getLocationSummary(enclosingDescriptor)="
      // + getLocationSummary(enclosingDescriptor));
      String locName = getLocationSummary(enclosingDescriptor).getLocationName(hnode.getName());
      // System.out.println("-locName=" + locName);
      Location newLoc = new Location(enclosingDescriptor, locName);
      newLoc.setLocDescriptor(locDescriptor);
      newCompLoc.addLocation(newLoc);
    }

    return newCompLoc;
  }

  private void debug_writeLattices() {

    Set<Descriptor> keySet = mapDescriptorToSimpleLattice.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor key = (Descriptor) iterator.next();
      SSJavaLattice<String> simpleLattice = mapDescriptorToSimpleLattice.get(key);
      // HierarchyGraph simpleHierarchyGraph = getSimpleHierarchyGraph(key);
      HierarchyGraph scHierarchyGraph = getSkeletonCombinationHierarchyGraph(key);
      if (key instanceof ClassDescriptor) {
        // writeInferredLatticeDotFile((ClassDescriptor) key, scHierarchyGraph, simpleLattice,
        // "_SIMPLE");
      } else if (key instanceof MethodDescriptor) {
        MethodDescriptor md = (MethodDescriptor) key;
        // writeInferredLatticeDotFile(md.getClassDesc(), md, scHierarchyGraph, simpleLattice,
        // "_SIMPLE");
      }

      LocationSummary ls = getLocationSummary(key);
      System.out.println("####LOC SUMMARY=" + key + "\n" + ls.getMapHNodeNameToLocationName());
    }

    Set<ClassDescriptor> cdKeySet = cd2lattice.keySet();
    for (Iterator iterator = cdKeySet.iterator(); iterator.hasNext();) {
      ClassDescriptor cd = (ClassDescriptor) iterator.next();
      writeInferredLatticeDotFile((ClassDescriptor) cd, getSkeletonCombinationHierarchyGraph(cd),
          cd2lattice.get(cd), "");
      COUNT += cd2lattice.get(cd).getKeySet().size();
    }

    Set<MethodDescriptor> mdKeySet = md2lattice.keySet();
    for (Iterator iterator = mdKeySet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      writeInferredLatticeDotFile(md.getClassDesc(), md, getSkeletonCombinationHierarchyGraph(md),
          md2lattice.get(md), "");
      COUNT += md2lattice.get(md).getKeySet().size();
    }
    System.out.println("###COUNT=" + COUNT);
  }

  private void buildLattice() {

    BuildLattice buildLattice = new BuildLattice(this);

    Set<Descriptor> keySet = mapDescriptorToCombineSkeletonHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();

      SSJavaLattice<String> simpleLattice = buildLattice.buildLattice(desc);

      addMapDescToSimpleLattice(desc, simpleLattice);

      HierarchyGraph simpleHierarchyGraph = getSimpleHierarchyGraph(desc);
      System.out.println("\n## insertIntermediateNodesToStraightLine:"
          + simpleHierarchyGraph.getName());
      SSJavaLattice<String> lattice =
          buildLattice.insertIntermediateNodesToStraightLine(desc, simpleLattice);
      lattice.removeRedundantEdges();

      if (desc instanceof ClassDescriptor) {
        // field lattice
        cd2lattice.put((ClassDescriptor) desc, lattice);
        // ssjava.writeLatticeDotFile((ClassDescriptor) desc, null, lattice);
      } else if (desc instanceof MethodDescriptor) {
        // method lattice
        md2lattice.put((MethodDescriptor) desc, lattice);
        MethodDescriptor md = (MethodDescriptor) desc;
        ClassDescriptor cd = md.getClassDesc();
        // ssjava.writeLatticeDotFile(cd, md, lattice);
      }

      // System.out.println("\nSSJAVA: Insering Combination Nodes:" + desc);
      // HierarchyGraph skeletonGraph = getSkeletonHierarchyGraph(desc);
      // HierarchyGraph skeletonGraphWithCombinationNode =
      // skeletonGraph.clone();
      // skeletonGraphWithCombinationNode.setName(desc + "_SC");
      //
      // HierarchyGraph simpleHierarchyGraph = getSimpleHierarchyGraph(desc);
      // System.out.println("Identifying Combination Nodes:");
      // skeletonGraphWithCombinationNode.insertCombinationNodesToGraph(simpleHierarchyGraph);
      // skeletonGraphWithCombinationNode.simplifySkeletonCombinationHierarchyGraph();
      // mapDescriptorToCombineSkeletonHierarchyGraph.put(desc,
      // skeletonGraphWithCombinationNode);
    }

  }

  public void addMapDescToSimpleLattice(Descriptor desc, SSJavaLattice<String> lattice) {
    mapDescriptorToSimpleLattice.put(desc, lattice);
  }

  public SSJavaLattice<String> getSimpleLattice(Descriptor desc) {
    return mapDescriptorToSimpleLattice.get(desc);
  }

  private void simplifyHierarchyGraph() {
    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      // System.out.println("SSJAVA: remove redundant edges: " + desc);
      HierarchyGraph simpleHierarchyGraph = getHierarchyGraph(desc).clone();
      simpleHierarchyGraph.setName(desc + "_SIMPLE");
      simpleHierarchyGraph.removeRedundantEdges();
      mapDescriptorToSimpleHierarchyGraph.put(desc, simpleHierarchyGraph);
    }
  }

  private void insertCombinationNodes() {
    Set<Descriptor> keySet = mapDescriptorToSkeletonHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      System.out.println("\nSSJAVA: Inserting Combination Nodes:" + desc);
      HierarchyGraph skeletonGraph = getSkeletonHierarchyGraph(desc);
      HierarchyGraph skeletonGraphWithCombinationNode = skeletonGraph.clone();
      skeletonGraphWithCombinationNode.setName(desc + "_SC");

      HierarchyGraph simpleHierarchyGraph = getSimpleHierarchyGraph(desc);
      skeletonGraphWithCombinationNode.insertCombinationNodesToGraph(simpleHierarchyGraph);
      // skeletonGraphWithCombinationNode.insertCombinationNodesToGraph(simpleHierarchyGraph,
      // skeletonGraph);
      // skeletonGraphWithCombinationNode.simplifySkeletonCombinationHierarchyGraph();
      skeletonGraphWithCombinationNode.removeRedundantEdges();
      mapDescriptorToCombineSkeletonHierarchyGraph.put(desc, skeletonGraphWithCombinationNode);
    }
  }

  private void constructSkeletonHierarchyGraph() {
    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      System.out.println("SSJAVA: Constructing Skeleton Hierarchy Graph: " + desc);
      HierarchyGraph simpleGraph = getSimpleHierarchyGraph(desc);
      HierarchyGraph skeletonGraph = simpleGraph.generateSkeletonGraph();
      skeletonGraph.setMapDescToHNode(simpleGraph.getMapDescToHNode());
      skeletonGraph.setMapHNodeToDescSet(simpleGraph.getMapHNodeToDescSet());
      skeletonGraph.simplifyHierarchyGraph();
      // skeletonGraph.combineRedundantNodes(false);
      // skeletonGraph.removeRedundantEdges();
      mapDescriptorToSkeletonHierarchyGraph.put(desc, skeletonGraph);
    }
  }

  private void debug_writeHierarchyDotFiles() {

    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      getHierarchyGraph(desc).writeGraph();
    }

  }

  private void debug_writeSimpleHierarchyDotFiles() {

    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      getHierarchyGraph(desc).writeGraph();
      getSimpleHierarchyGraph(desc).writeGraph();
      getSimpleHierarchyGraph(desc).writeGraph(true);
    }

  }

  private void debug_writeSkeletonHierarchyDotFiles() {

    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      getSkeletonHierarchyGraph(desc).writeGraph();
    }

  }

  private void debug_writeSkeletonCombinationHierarchyDotFiles() {

    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      getSkeletonCombinationHierarchyGraph(desc).writeGraph();
    }

  }

  public HierarchyGraph getSimpleHierarchyGraph(Descriptor d) {
    return mapDescriptorToSimpleHierarchyGraph.get(d);
  }

  private HierarchyGraph getSkeletonHierarchyGraph(Descriptor d) {
    if (!mapDescriptorToSkeletonHierarchyGraph.containsKey(d)) {
      mapDescriptorToSkeletonHierarchyGraph.put(d, new HierarchyGraph(d));
    }
    return mapDescriptorToSkeletonHierarchyGraph.get(d);
  }

  public HierarchyGraph getSkeletonCombinationHierarchyGraph(Descriptor d) {
    if (!mapDescriptorToCombineSkeletonHierarchyGraph.containsKey(d)) {
      mapDescriptorToCombineSkeletonHierarchyGraph.put(d, new HierarchyGraph(d));
    }
    return mapDescriptorToCombineSkeletonHierarchyGraph.get(d);
  }

  private void constructHierarchyGraph() {

    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();
      if (state.SSJAVADEBUG) {
        HierarchyGraph hierarchyGraph = new HierarchyGraph(md);
        System.out.println();
        System.out.println("SSJAVA: Construcing the hierarchy graph from " + md);
        constructHierarchyGraph(md, hierarchyGraph);
        mapDescriptorToHierarchyGraph.put(md, hierarchyGraph);

      }
    }

    setupToAnalyze();
    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();
      HierarchyGraph graph = getHierarchyGraph(cd);
      for (Iterator iter = cd.getFields(); iter.hasNext();) {
        FieldDescriptor fieldDesc = (FieldDescriptor) iter.next();
        if (!(fieldDesc.isStatic() && fieldDesc.isFinal())) {
          graph.getHNode(fieldDesc);
        }
      }
    }

    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor key = (Descriptor) iterator.next();
      HierarchyGraph graph = getHierarchyGraph(key);

      Set<HNode> nodeToBeConnected = new HashSet<HNode>();
      for (Iterator iterator2 = graph.getNodeSet().iterator(); iterator2.hasNext();) {
        HNode node = (HNode) iterator2.next();
        if (!node.isSkeleton() && !node.isCombinationNode()) {
          if (graph.getIncomingNodeSet(node).size() == 0) {
            nodeToBeConnected.add(node);
          }
        }
      }

      for (Iterator iterator2 = nodeToBeConnected.iterator(); iterator2.hasNext();) {
        HNode node = (HNode) iterator2.next();
        System.out.println("NEED TO BE CONNECTED TO TOP=" + node);
        graph.addEdge(graph.getHNode(TOPDESC), node);
      }

    }

  }

  private void constructHierarchyGraph2() {

    // do fixed-point analysis

    LinkedList<MethodDescriptor> descriptorListToAnalyze = ssjava.getSortedDescriptors();

    // Collections.sort(descriptorListToAnalyze, new
    // Comparator<MethodDescriptor>() {
    // public int compare(MethodDescriptor o1, MethodDescriptor o2) {
    // return o1.getSymbol().compareToIgnoreCase(o2.getSymbol());
    // }
    // });

    // current descriptors to visit in fixed-point interprocedural analysis,
    // prioritized by dependency in the call graph
    methodDescriptorsToVisitStack.clear();

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

      HierarchyGraph hierarchyGraph = new HierarchyGraph(md);
      // MethodSummary methodSummary = new MethodSummary(md);

      // MethodLocationInfo methodInfo = new MethodLocationInfo(md);
      // curMethodInfo = methodInfo;

      System.out.println();
      System.out.println("SSJAVA: Construcing the hierarchy graph from " + md);

      constructHierarchyGraph(md, hierarchyGraph);

      HierarchyGraph prevHierarchyGraph = getHierarchyGraph(md);
      // MethodSummary prevMethodSummary = getMethodSummary(md);

      if (!hierarchyGraph.equals(prevHierarchyGraph)) {

        mapDescriptorToHierarchyGraph.put(md, hierarchyGraph);
        // mapDescToLocationSummary.put(md, methodSummary);

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

    setupToAnalyze();
    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();
      HierarchyGraph graph = getHierarchyGraph(cd);
      for (Iterator iter = cd.getFields(); iter.hasNext();) {
        FieldDescriptor fieldDesc = (FieldDescriptor) iter.next();
        if (!(fieldDesc.isStatic() && fieldDesc.isFinal())) {
          graph.getHNode(fieldDesc);
        }
      }
    }

    Set<Descriptor> keySet = mapDescriptorToHierarchyGraph.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor key = (Descriptor) iterator.next();
      HierarchyGraph graph = getHierarchyGraph(key);

      Set<HNode> nodeToBeConnected = new HashSet<HNode>();
      for (Iterator iterator2 = graph.getNodeSet().iterator(); iterator2.hasNext();) {
        HNode node = (HNode) iterator2.next();
        if (!node.isSkeleton() && !node.isCombinationNode()) {
          if (graph.getIncomingNodeSet(node).size() == 0) {
            nodeToBeConnected.add(node);
          }
        }
      }

      for (Iterator iterator2 = nodeToBeConnected.iterator(); iterator2.hasNext();) {
        HNode node = (HNode) iterator2.next();
        System.out.println("NEED TO BE CONNECTED TO TOP=" + node);
        graph.addEdge(graph.getHNode(TOPDESC), node);
      }

    }

  }

  private HierarchyGraph getHierarchyGraph(Descriptor d) {
    if (!mapDescriptorToHierarchyGraph.containsKey(d)) {
      mapDescriptorToHierarchyGraph.put(d, new HierarchyGraph(d));
    }
    return mapDescriptorToHierarchyGraph.get(d);
  }

  private void constructHierarchyGraph(MethodDescriptor md, HierarchyGraph methodGraph) {

    // visit each node of method flow graph
    FlowGraph fg = getFlowGraph(md);
    // Set<FlowNode> nodeSet = fg.getNodeSet();

    Set<FlowEdge> edgeSet = fg.getEdgeSet();

    Set<Descriptor> paramDescSet = fg.getMapParamDescToIdx().keySet();
    for (Iterator iterator = paramDescSet.iterator(); iterator.hasNext();) {
      Descriptor desc = (Descriptor) iterator.next();
      methodGraph.getHNode(desc).setSkeleton(true);
    }

    // for the method lattice, we need to look at the first element of
    // NTuple<Descriptor>
    boolean hasGlobalAccess = false;
    // for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
    // FlowNode originalSrcNode = (FlowNode) iterator.next();
    for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
      FlowEdge edge = (FlowEdge) iterator.next();

      FlowNode originalSrcNode = fg.getFlowNode(edge.getInitTuple());
      Set<FlowNode> sourceNodeSet = new HashSet<FlowNode>();
      if (originalSrcNode instanceof FlowReturnNode) {
        FlowReturnNode rnode = (FlowReturnNode) originalSrcNode;
        System.out.println("rnode=" + rnode);
        Set<NTuple<Descriptor>> tupleSet = rnode.getReturnTupleSet();
        for (Iterator iterator2 = tupleSet.iterator(); iterator2.hasNext();) {
          NTuple<Descriptor> nTuple = (NTuple<Descriptor>) iterator2.next();
          sourceNodeSet.add(fg.getFlowNode(nTuple));
          System.out.println("&&&SOURCE fg.getFlowNode(nTuple)=" + fg.getFlowNode(nTuple));
        }
      } else {
        sourceNodeSet.add(originalSrcNode);
      }

      // System.out.println("---sourceNodeSet=" + sourceNodeSet + "  from originalSrcNode="
      // + originalSrcNode);

      for (Iterator iterator3 = sourceNodeSet.iterator(); iterator3.hasNext();) {
        FlowNode srcNode = (FlowNode) iterator3.next();

        NTuple<Descriptor> srcNodeTuple = srcNode.getDescTuple();
        Descriptor srcLocalDesc = srcNodeTuple.get(0);

        if (srcLocalDesc instanceof InterDescriptor
            && ((InterDescriptor) srcLocalDesc).getMethodArgIdxPair() != null) {

          if (srcNode.getCompositeLocation() == null) {
            continue;
          }
        }

        // if the srcNode is started with the global descriptor
        // need to set as a skeleton node
        if (!hasGlobalAccess && srcNode.getDescTuple().startsWith(GLOBALDESC)) {
          hasGlobalAccess = true;
        }

        // Set<FlowEdge> outEdgeSet = fg.getOutEdgeSet(originalSrcNode);
        // for (Iterator iterator2 = outEdgeSet.iterator(); iterator2.hasNext();) {
        // FlowEdge outEdge = (FlowEdge) iterator2.next();
        // FlowNode originalDstNode = outEdge.getDst();
        FlowNode originalDstNode = fg.getFlowNode(edge.getEndTuple());

        Set<FlowNode> dstNodeSet = new HashSet<FlowNode>();
        if (originalDstNode instanceof FlowReturnNode) {
          FlowReturnNode rnode = (FlowReturnNode) originalDstNode;
          // System.out.println("\n-returnNode=" + rnode);
          Set<NTuple<Descriptor>> tupleSet = rnode.getReturnTupleSet();
          for (Iterator iterator4 = tupleSet.iterator(); iterator4.hasNext();) {
            NTuple<Descriptor> nTuple = (NTuple<Descriptor>) iterator4.next();
            dstNodeSet.add(fg.getFlowNode(nTuple));
            System.out.println("&&&DST fg.getFlowNode(nTuple)=" + fg.getFlowNode(nTuple));
          }
        } else {
          dstNodeSet.add(originalDstNode);
        }
        // System.out.println("---dstNodeSet=" + dstNodeSet);
        for (Iterator iterator4 = dstNodeSet.iterator(); iterator4.hasNext();) {
          FlowNode dstNode = (FlowNode) iterator4.next();

          NTuple<Descriptor> dstNodeTuple = dstNode.getDescTuple();
          Descriptor dstLocalDesc = dstNodeTuple.get(0);

          if (dstLocalDesc instanceof InterDescriptor
              && ((InterDescriptor) dstLocalDesc).getMethodArgIdxPair() != null) {
            if (dstNode.getCompositeLocation() == null) {
              System.out.println("%%%%%%%%%%%%%SKIP=" + dstNode);
              continue;
            }
          }

          // if (outEdge.getInitTuple().equals(srcNodeTuple)
          // && outEdge.getEndTuple().equals(dstNodeTuple)) {

          NTuple<Descriptor> srcCurTuple = srcNode.getCurrentDescTuple();
          NTuple<Descriptor> dstCurTuple = dstNode.getCurrentDescTuple();

          System.out.println("-srcCurTuple=" + srcCurTuple + "  dstCurTuple=" + dstCurTuple
              + "  srcNode=" + srcNode + "   dstNode=" + dstNode);

          // srcCurTuple = translateBaseTuple(srcNode, srcCurTuple);
          // dstCurTuple = translateBaseTuple(dstNode, dstCurTuple);

          if ((srcCurTuple.size() > 1 && dstCurTuple.size() > 1)
              && srcCurTuple.get(0).equals(dstCurTuple.get(0))) {

            // value flows between fields
            Descriptor desc = srcCurTuple.get(0);
            ClassDescriptor classDesc;

            if (desc.equals(GLOBALDESC)) {
              classDesc = md.getClassDesc();
            } else {
              VarDescriptor varDesc = (VarDescriptor) srcCurTuple.get(0);
              classDesc = varDesc.getType().getClassDesc();
            }
            extractFlowsBetweenFields(classDesc, srcNode, dstNode, 1);

          } else if ((srcCurTuple.size() == 1 && dstCurTuple.size() == 1)
              || ((srcCurTuple.size() > 1 || dstCurTuple.size() > 1) && !srcCurTuple.get(0).equals(
                  dstCurTuple.get(0)))) {

            // value flow between a primitive local var - a primitive local var or local var -
            // field

            Descriptor srcDesc = srcCurTuple.get(0);
            Descriptor dstDesc = dstCurTuple.get(0);

            methodGraph.addEdge(srcDesc, dstDesc);

            if (fg.isParamDesc(srcDesc)) {
              methodGraph.setParamHNode(srcDesc);
            }
            if (fg.isParamDesc(dstDesc)) {
              methodGraph.setParamHNode(dstDesc);
            }

          }

          // }
          // }

        }

      }

    }

    // If the method accesses static fields
    // set hasGloabalAccess true in the method summary.
    if (hasGlobalAccess) {
      getMethodSummary(md).setHasGlobalAccess();
    }
    methodGraph.getHNode(GLOBALDESC).setSkeleton(true);

    if (ssjava.getMethodContainingSSJavaLoop().equals(md)) {
      // if the current method contains the event loop
      // we need to set all nodes of the hierarchy graph as a skeleton node
      Set<HNode> hnodeSet = methodGraph.getNodeSet();
      for (Iterator iterator = hnodeSet.iterator(); iterator.hasNext();) {
        HNode hnode = (HNode) iterator.next();
        hnode.setSkeleton(true);
      }
    }

  }

  private NTuple<Descriptor> translateBaseTuple(FlowNode flowNode, NTuple<Descriptor> inTuple) {

    if (flowNode.getBaseTuple() != null) {

      NTuple<Descriptor> translatedTuple = new NTuple<Descriptor>();

      NTuple<Descriptor> baseTuple = flowNode.getBaseTuple();

      for (int i = 0; i < baseTuple.size(); i++) {
        translatedTuple.add(baseTuple.get(i));
      }

      for (int i = 1; i < inTuple.size(); i++) {
        translatedTuple.add(inTuple.get(i));
      }

      System.out.println("------TRANSLATED " + inTuple + " -> " + translatedTuple);
      return translatedTuple;

    } else {
      return inTuple;
    }

  }

  private MethodSummary getMethodSummary(MethodDescriptor md) {
    if (!mapDescToLocationSummary.containsKey(md)) {
      mapDescToLocationSummary.put(md, new MethodSummary(md));
    }
    return (MethodSummary) mapDescToLocationSummary.get(md);
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

    if (desc instanceof MethodDescriptor) {
      System.out.println("#EXTRA LOC DECLARATION GEN=" + desc);

      MethodDescriptor md = (MethodDescriptor) desc;
      MethodSummary methodSummary = getMethodSummary(md);

      TypeDescriptor returnType = ((MethodDescriptor) desc).getReturnType();
      if (!ssjava.getMethodContainingSSJavaLoop().equals(desc) && returnType != null
          && (!returnType.isVoid())) {
        CompositeLocation returnLoc = methodSummary.getRETURNLoc();
        if (returnLoc.getSize() == 1) {
          String returnLocStr = generateLocationAnnoatation(methodSummary.getRETURNLoc());
          if (rtr.indexOf(returnLocStr) == -1) {
            rtr += "," + returnLocStr;
          }
        }
      }
      rtr += "\")";

      if (!ssjava.getMethodContainingSSJavaLoop().equals(desc)) {
        if (returnType != null && (!returnType.isVoid())) {
          rtr +=
              "\n@RETURNLOC(\"" + generateLocationAnnoatation(methodSummary.getRETURNLoc()) + "\")";
        }

        CompositeLocation pcLoc = methodSummary.getPCLoc();
        if ((pcLoc != null) && (!pcLoc.get(0).isTop())) {
          rtr += "\n@PCLOC(\"" + generateLocationAnnoatation(pcLoc) + "\")";
        }
      }

      if (!md.isStatic()) {
        rtr += "\n@THISLOC(\"" + methodSummary.getThisLocName() + "\")";
      }
      rtr += "\n@GLOBALLOC(\"" + methodSummary.getGlobalLocName() + "\")";

    } else {
      rtr += "\")";
    }

    return rtr;
  }

  private void generateAnnoatedCode() {

    readOriginalSourceFiles();

    setupToAnalyze();
    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);

      String sourceFileName = cd.getSourceFileName();

      if (cd.isInterface()) {
        continue;
      }

      int classDefLine = mapDescToDefinitionLine.get(cd);
      Vector<String> sourceVec = mapFileNameToLineVector.get(sourceFileName);

      LocationSummary fieldLocSummary = getLocationSummary(cd);

      String fieldLatticeDefStr = generateLatticeDefinition(cd);
      String annoatedSrc = fieldLatticeDefStr + newline + sourceVec.get(classDefLine);
      sourceVec.set(classDefLine, annoatedSrc);

      // generate annotations for field declarations
      // Map<Descriptor, CompositeLocation> inferLocMap = fieldLocInfo.getMapDescToInferLocation();
      Map<String, String> mapFieldNameToLocName = fieldLocSummary.getMapHNodeNameToLocationName();

      for (Iterator iter = cd.getFields(); iter.hasNext();) {
        FieldDescriptor fd = (FieldDescriptor) iter.next();

        String locAnnotationStr;
        // CompositeLocation inferLoc = inferLocMap.get(fd);
        String locName = mapFieldNameToLocName.get(fd.getSymbol());

        if (locName != null) {
          // infer loc is null if the corresponding field is static and final
          // locAnnotationStr = "@LOC(\"" + generateLocationAnnoatation(inferLoc) + "\")";
          locAnnotationStr = "@LOC(\"" + locName + "\")";
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

          // MethodLocationInfo methodLocInfo = getMethodLocationInfo(md);
          // Map<Descriptor, CompositeLocation> methodInferLocMap =
          // methodLocInfo.getMapDescToInferLocation();

          MethodSummary methodSummary = getMethodSummary(md);

          Map<Descriptor, CompositeLocation> mapVarDescToInferLoc =
              methodSummary.getMapVarDescToInferCompositeLocation();
          System.out.println("-----md=" + md);
          System.out.println("-----mapVarDescToInferLoc=" + mapVarDescToInferLoc);

          Set<Descriptor> localVarDescSet = mapVarDescToInferLoc.keySet();

          Set<String> localLocElementSet = methodLattice.getElementSet();

          for (Iterator iterator = localVarDescSet.iterator(); iterator.hasNext();) {
            Descriptor localVarDesc = (Descriptor) iterator.next();
            System.out.println("-------localVarDesc=" + localVarDesc);
            CompositeLocation inferLoc = mapVarDescToInferLoc.get(localVarDesc);

            String localLocIdentifier = inferLoc.get(0).getLocIdentifier();
            if (!localLocElementSet.contains(localLocIdentifier)) {
              methodLattice.put(localLocIdentifier);
            }

            String locAnnotationStr = "@LOC(\"" + generateLocationAnnoatation(inferLoc) + "\")";

            if (!isParameter(md, localVarDesc)) {
              if (mapDescToDefinitionLine.containsKey(localVarDesc)) {
                int varLineNum = mapDescToDefinitionLine.get(localVarDesc);
                String orgSourceLine = sourceVec.get(varLineNum);
                System.out.println("varLineNum=" + varLineNum + "  org src=" + orgSourceLine);
                int idx =
                    orgSourceLine.indexOf(generateVarDeclaration((VarDescriptor) localVarDesc));
                System.out.println("idx=" + idx
                    + "  generateVarDeclaration((VarDescriptor) localVarDesc)="
                    + generateVarDeclaration((VarDescriptor) localVarDesc));
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
              System.out.println("methodDefStr=" + methodDefStr + " localVarDesc=" + localVarDesc
                  + " idx=" + idx);
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
          // if (localLocElementSet.contains("this")) {
          // methodLattice.put("this");
          // }

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

  private void calculateExtraLocations() {

    LinkedList<MethodDescriptor> methodDescList = ssjava.getSortedDescriptors();
    for (Iterator iterator = methodDescList.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      if (!ssjava.getMethodContainingSSJavaLoop().equals(md)) {
        calculateExtraLocations(md);
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

  private void calculatePCLOC(MethodDescriptor md) {

    System.out.println("#CalculatePCLOC");
    MethodSummary methodSummary = getMethodSummary(md);
    FlowGraph fg = getFlowGraph(md);
    Map<Integer, CompositeLocation> mapParamToLoc = methodSummary.getMapParamIdxToInferLoc();

    // calculate the initial program counter location
    // PC location is higher than location types of parameters which has incoming flows.

    Set<NTuple<Location>> paramLocTupleHavingInFlowSet = new HashSet<NTuple<Location>>();
    Set<Descriptor> paramDescNOTHavingInFlowSet = new HashSet<Descriptor>();
    // Set<FlowNode> paramNodeNOThavingInFlowSet = new HashSet<FlowNode>();

    int numParams = fg.getNumParameters();
    for (int i = 0; i < numParams; i++) {
      FlowNode paramFlowNode = fg.getParamFlowNode(i);
      Descriptor prefix = paramFlowNode.getDescTuple().get(0);
      NTuple<Descriptor> paramDescTuple = paramFlowNode.getCurrentDescTuple();
      NTuple<Location> paramLocTuple = translateToLocTuple(md, paramDescTuple);

      Set<FlowNode> inNodeToParamSet = fg.getIncomingNodeSetByPrefix(prefix);
      if (inNodeToParamSet.size() > 0) {
        // parameter has in-value flows

        for (Iterator iterator = inNodeToParamSet.iterator(); iterator.hasNext();) {
          FlowNode inNode = (FlowNode) iterator.next();
          Set<FlowEdge> outEdgeSet = fg.getOutEdgeSet(inNode);
          for (Iterator iterator2 = outEdgeSet.iterator(); iterator2.hasNext();) {
            FlowEdge flowEdge = (FlowEdge) iterator2.next();
            if (flowEdge.getEndTuple().startsWith(prefix)) {
              NTuple<Location> paramLocTupleWithIncomingFlow =
                  translateToLocTuple(md, flowEdge.getEndTuple());
              paramLocTupleHavingInFlowSet.add(paramLocTupleWithIncomingFlow);
            }
          }
        }

        // paramLocTupleHavingInFlowSet.add(paramLocTuple);
      } else {
        // paramNodeNOThavingInFlowSet.add(fg.getFlowNode(paramDescTuple));
        paramDescNOTHavingInFlowSet.add(prefix);
      }
    }

    System.out.println("paramLocTupleHavingInFlowSet=" + paramLocTupleHavingInFlowSet);

    if (paramLocTupleHavingInFlowSet.size() > 0
        && !coversAllParamters(md, fg, paramLocTupleHavingInFlowSet)) {

      // Here, generates a location in the method lattice that is higher than the
      // paramLocTupleHavingInFlowSet
      NTuple<Location> pcLocTuple =
          generateLocTupleRelativeTo(md, paramLocTupleHavingInFlowSet, PCLOC);

      NTuple<Descriptor> pcDescTuple = translateToDescTuple(pcLocTuple);

      // System.out.println("pcLoc=" + pcLocTuple);

      CompositeLocation curPCLoc = methodSummary.getPCLoc();
      if (curPCLoc.get(0).isTop() || pcLocTuple.size() > curPCLoc.getSize()) {
        methodSummary.setPCLoc(new CompositeLocation(pcLocTuple));

        Set<FlowNode> flowNodeLowerthanPCLocSet = new HashSet<FlowNode>();
        GlobalFlowGraph subGlobalFlowGraph = getSubGlobalFlowGraph(md);
        // add ordering relations s.t. PCLOC is higher than all flow nodes except the set of
        // parameters that do not have incoming flows
        for (Iterator iterator = fg.getNodeSet().iterator(); iterator.hasNext();) {
          FlowNode node = (FlowNode) iterator.next();

          if (!(node instanceof FlowReturnNode)) {
            if (!paramDescNOTHavingInFlowSet.contains(node.getCurrentDescTuple().get(0))) {
              flowNodeLowerthanPCLocSet.add(node);
              fg.addValueFlowEdge(pcDescTuple, node.getDescTuple());

              subGlobalFlowGraph.addValueFlowEdge(pcLocTuple,
                  translateToLocTuple(md, node.getDescTuple()));
            }
          } else {
            System.out.println("***SKIP PCLOC -> RETURNLOC=" + node);
          }

        }
        fg.getFlowNode(translateToDescTuple(pcLocTuple)).setSkeleton(true);

        if (pcLocTuple.get(0).getLocDescriptor().equals(md.getThis())) {
          System.out.println("#########################################");
          for (Iterator iterator = flowNodeLowerthanPCLocSet.iterator(); iterator.hasNext();) {
            FlowNode lowerNode = (FlowNode) iterator.next();
            if (lowerNode.getDescTuple().size() == 1 && lowerNode.getCompositeLocation() == null) {
              NTuple<Location> lowerLocTuple = translateToLocTuple(md, lowerNode.getDescTuple());
              CompositeLocation newComp =
                  calculateCompositeLocationFromSubGlobalGraph(md, lowerNode);
              if (newComp != null) {
                subGlobalFlowGraph.addMapLocationToInferCompositeLocation(lowerLocTuple.get(0),
                    newComp);
                lowerNode.setCompositeLocation(newComp);
                System.out.println("NEW COMP LOC=" + newComp + "    to lowerNode=" + lowerNode);
              }

            }

          }
        }

      }

    }
  }

  private int countFirstDescriptorSetSize(Set<NTuple<Location>> set) {

    Set<Descriptor> descSet = new HashSet<Descriptor>();

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      NTuple<Location> locTuple = (NTuple<Location>) iterator.next();
      descSet.add(locTuple.get(0).getLocDescriptor());
    }

    return descSet.size();
  }

  private boolean coversAllParamters(MethodDescriptor md, FlowGraph fg,
      Set<NTuple<Location>> paramLocTupleHavingInFlowSet) {

    int numParam = fg.getNumParameters();
    // int size = paramLocTupleHavingInFlowSet.size();
    int size = countFirstDescriptorSetSize(paramLocTupleHavingInFlowSet);

    System.out.println("numParam=" + numParam + "     size=" + size);

    // if (!md.isStatic()) {
    //
    // // if the method is not static && there is a parameter composite location &&
    // // it is started with 'this',
    // // paramLocTupleHavingInFlowSet need to have 'this' parameter.
    //
    // FlowNode thisParamNode = fg.getParamFlowNode(0);
    // NTuple<Location> thisParamLocTuple =
    // translateToLocTuple(md, thisParamNode.getCurrentDescTuple());
    //
    // if (!paramLocTupleHavingInFlowSet.contains(thisParamLocTuple)) {
    //
    // for (Iterator iterator = paramLocTupleHavingInFlowSet.iterator(); iterator.hasNext();) {
    // NTuple<Location> paramTuple = (NTuple<Location>) iterator.next();
    // if (paramTuple.size() > 1 && paramTuple.get(0).getLocDescriptor().equals(md.getThis())) {
    // // paramLocTupleHavingInFlowSet.add(thisParamLocTuple);
    // // break;
    // size++;
    // }
    // }
    //
    // }
    // }

    if (size == numParam) {
      return true;
    } else {
      return false;
    }

  }

  private void calculateRETURNLOC(MethodDescriptor md) {

    System.out.println("#calculateRETURNLOC= " + md);

    // calculate a return location:
    // the return location type is lower than all parameters and the location of return values
    MethodSummary methodSummary = getMethodSummary(md);
    // if (methodSummary.getRETURNLoc() != null) {
    // System.out.println("$HERE?");
    // return;
    // }

    FlowGraph fg = getFlowGraph(md);
    Map<Integer, CompositeLocation> mapParamToLoc = methodSummary.getMapParamIdxToInferLoc();
    Set<Integer> paramIdxSet = mapParamToLoc.keySet();

    if (md.getReturnType() != null && !md.getReturnType().isVoid()) {
      // first, generate the set of return value location types that starts
      // with 'this' reference

      Set<FlowNode> paramFlowNodeFlowingToReturnValueSet = getParamNodeFlowingToReturnValue(md);
      // System.out.println("paramFlowNodeFlowingToReturnValueSet="
      // + paramFlowNodeFlowingToReturnValueSet);

      Set<NTuple<Location>> tupleToBeHigherThanReturnLocSet = new HashSet<NTuple<Location>>();
      for (Iterator iterator = paramFlowNodeFlowingToReturnValueSet.iterator(); iterator.hasNext();) {
        FlowNode fn = (FlowNode) iterator.next();
        NTuple<Descriptor> paramDescTuple = fn.getCurrentDescTuple();
        tupleToBeHigherThanReturnLocSet.add(translateToLocTuple(md, paramDescTuple));
      }

      Set<FlowNode> returnNodeSet = fg.getReturnNodeSet();
      for (Iterator iterator = returnNodeSet.iterator(); iterator.hasNext();) {
        FlowNode returnNode = (FlowNode) iterator.next();
        NTuple<Descriptor> returnDescTuple = returnNode.getCurrentDescTuple();
        tupleToBeHigherThanReturnLocSet.add(translateToLocTuple(md, returnDescTuple));
      }
      System.out.println("-flow graph's returnNodeSet=" + returnNodeSet);
      System.out.println("tupleSetToBeHigherThanReturnLoc=" + tupleToBeHigherThanReturnLocSet);

      // Here, generates a return location in the method lattice that is lower than the
      // locFlowingToReturnValueSet
      NTuple<Location> returnLocTuple =
          generateLocTupleRelativeTo(md, tupleToBeHigherThanReturnLocSet, RLOC);

      // System.out.println("returnLocTuple=" + returnLocTuple);
      NTuple<Descriptor> returnDescTuple = translateToDescTuple(returnLocTuple);
      CompositeLocation curReturnLoc = methodSummary.getRETURNLoc();
      if (curReturnLoc == null || returnDescTuple.size() > curReturnLoc.getSize()) {
        methodSummary.setRETURNLoc(new CompositeLocation(returnLocTuple));

        for (Iterator iterator = tupleToBeHigherThanReturnLocSet.iterator(); iterator.hasNext();) {
          NTuple<Location> higherTuple = (NTuple<Location>) iterator.next();
          fg.addValueFlowEdge(translateToDescTuple(higherTuple), returnDescTuple);
        }
        fg.getFlowNode(returnDescTuple).setSkeleton(true);

      }

      // makes sure that PCLOC is higher than RETURNLOC
      CompositeLocation pcLoc = methodSummary.getPCLoc();
      if (!pcLoc.get(0).isTop()) {
        NTuple<Descriptor> pcLocDescTuple = translateToDescTuple(pcLoc.getTuple());
        fg.addValueFlowEdge(pcLocDescTuple, returnDescTuple);
      }

    }

  }

  private void calculateExtraLocations(MethodDescriptor md) {
    // calcualte pcloc, returnloc,...

    System.out.println("\nSSJAVA:Calculate PCLOC/RETURNLOC locations: " + md);

    calculatePCLOC(md);
    calculateRETURNLOC(md);

  }

  private NTuple<Location> generateLocTupleRelativeTo(MethodDescriptor md,
      Set<NTuple<Location>> paramLocTupleHavingInFlowSet, String locNamePrefix) {

    // System.out.println("-generateLocTupleRelativeTo=" + paramLocTupleHavingInFlowSet);

    NTuple<Location> higherLocTuple = new NTuple<Location>();

    VarDescriptor thisVarDesc = md.getThis();
    // check if all paramter loc tuple is started with 'this' reference
    boolean hasParamNotStartedWithThisRef = false;

    int minSize = 0;

    Set<NTuple<Location>> paramLocTupleStartedWithThis = new HashSet<NTuple<Location>>();

    next: for (Iterator iterator = paramLocTupleHavingInFlowSet.iterator(); iterator.hasNext();) {
      NTuple<Location> paramLocTuple = (NTuple<Location>) iterator.next();
      Descriptor paramLocalDesc = paramLocTuple.get(0).getLocDescriptor();
      if (!paramLocalDesc.equals(thisVarDesc)) {

        Set<FlowNode> inNodeSet = getFlowGraph(md).getIncomingNodeSetByPrefix(paramLocalDesc);
        for (Iterator iterator2 = inNodeSet.iterator(); iterator2.hasNext();) {
          FlowNode flowNode = (FlowNode) iterator2.next();
          if (flowNode.getDescTuple().startsWith(thisVarDesc)) {
            // System.out.println("paramLocTuple=" + paramLocTuple + " is lower than THIS");
            continue next;
          }
        }
        hasParamNotStartedWithThisRef = true;

      } else if (paramLocTuple.size() > 1) {
        paramLocTupleStartedWithThis.add(paramLocTuple);
        if (minSize == 0 || minSize > paramLocTuple.size()) {
          minSize = paramLocTuple.size();
        }
      }
    }

    // System.out.println("---paramLocTupleStartedWithThis=" + paramLocTupleStartedWithThis);
    Descriptor enclosingDesc = md;
    if (hasParamNotStartedWithThisRef) {
      // in this case, PCLOC will be the local location
    } else {
      // all parameter is started with 'this', so PCLOC will be set relative to the composite
      // location started with 'this'.
      // for (int idx = 0; idx < minSize - 1; idx++) {
      for (int idx = 0; idx < 1; idx++) {
        Set<Descriptor> locDescSet = new HashSet<Descriptor>();
        Location curLoc = null;
        NTuple<Location> paramLocTuple = null;
        for (Iterator iterator = paramLocTupleStartedWithThis.iterator(); iterator.hasNext();) {
          paramLocTuple = (NTuple<Location>) iterator.next();
          // System.out.println("-----paramLocTuple=" + paramLocTuple + "  idx=" + idx);
          curLoc = paramLocTuple.get(idx);
          Descriptor locDesc = curLoc.getLocDescriptor();
          locDescSet.add(locDesc);
        }
        // System.out.println("-----locDescSet=" + locDescSet + " idx=" + idx);
        if (locDescSet.size() != 1) {
          break;
        }
        Location newLocElement = new Location(curLoc.getDescriptor(), curLoc.getLocDescriptor());
        System.out.println("newLocElement" + newLocElement);
        higherLocTuple.add(newLocElement);
        enclosingDesc = getClassTypeDescriptor(curLoc.getLocDescriptor());
      }

    }

    String locIdentifier = locNamePrefix + (locSeed++);
    NameDescriptor locDesc = new NameDescriptor(locIdentifier);
    Location newLoc = new Location(enclosingDesc, locDesc);
    higherLocTuple.add(newLoc);
    System.out.println("---new loc tuple=" + higherLocTuple);

    return higherLocTuple;

  }

  public ClassDescriptor getClassTypeDescriptor(Descriptor in) {

    if (in instanceof VarDescriptor) {
      return ((VarDescriptor) in).getType().getClassDesc();
    } else if (in instanceof FieldDescriptor) {
      return ((FieldDescriptor) in).getType().getClassDesc();
    }
    // else if (in instanceof LocationDescriptor) {
    // // here is the case that the descriptor 'in' is the last element of the assigned composite
    // // location
    // return ((VarDescriptor) locTuple.get(0).getLocDescriptor()).getType().getClassDesc();
    // }
    else {
      return null;
    }

  }

  private Set<NTuple<Location>> calculateHighestLocTupleSet(
      Set<NTuple<Location>> paramLocTupleHavingInFlowSet) {

    Set<NTuple<Location>> highestSet = new HashSet<NTuple<Location>>();

    Iterator<NTuple<Location>> iterator = paramLocTupleHavingInFlowSet.iterator();
    NTuple<Location> highest = iterator.next();

    for (; iterator.hasNext();) {
      NTuple<Location> curLocTuple = (NTuple<Location>) iterator.next();
      if (isHigherThan(curLocTuple, highest)) {
        // System.out.println(curLocTuple + " is greater than " + highest);
        highest = curLocTuple;
      }
    }

    highestSet.add(highest);

    MethodDescriptor md = (MethodDescriptor) highest.get(0).getDescriptor();
    VarDescriptor thisVarDesc = md.getThis();

    // System.out.println("highest=" + highest);

    for (Iterator<NTuple<Location>> iter = paramLocTupleHavingInFlowSet.iterator(); iter.hasNext();) {
      NTuple<Location> curLocTuple = iter.next();

      if (!curLocTuple.equals(highest) && !hasOrderingRelation(highest, curLocTuple)) {

        // System.out.println("add it to the highest set=" + curLocTuple);
        highestSet.add(curLocTuple);

      }
    }

    return highestSet;

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

  private GlobalFlowGraph getSubGlobalFlowGraph(MethodDescriptor md) {

    if (!mapMethodDescriptorToSubGlobalFlowGraph.containsKey(md)) {
      mapMethodDescriptorToSubGlobalFlowGraph.put(md, new GlobalFlowGraph(md));
    }
    return mapMethodDescriptorToSubGlobalFlowGraph.get(md);
  }

  private void propagateFlowsToCallerWithNoCompositeLocation(MethodInvokeNode min,
      MethodDescriptor mdCaller, MethodDescriptor mdCallee) {

    System.out.println("-propagateFlowsToCallerWithNoCompositeLocation=" + min.printNode(0));
    // if the parameter A reaches to the parameter B
    // then, add an edge the argument A -> the argument B to the caller's flow
    // graph

    FlowGraph calleeFlowGraph = getFlowGraph(mdCallee);
    FlowGraph callerFlowGraph = getFlowGraph(mdCaller);
    int numParam = calleeFlowGraph.getNumParameters();

    for (int i = 0; i < numParam; i++) {
      for (int k = 0; k < numParam; k++) {

        if (i != k) {

          FlowNode paramNode1 = calleeFlowGraph.getParamFlowNode(i);
          FlowNode paramNode2 = calleeFlowGraph.getParamFlowNode(k);

          NTuple<Descriptor> arg1Tuple = getNodeTupleByArgIdx(min, i);
          NTuple<Descriptor> arg2Tuple = getNodeTupleByArgIdx(min, k);

          // check if the callee propagates an ordering constraints through
          // parameters

          // Set<FlowNode> localReachSet = calleeFlowGraph.getLocalReachFlowNodeSetFrom(paramNode1);
          Set<FlowNode> localReachSet =
              calleeFlowGraph.getReachableSetFrom(paramNode1.getDescTuple());

          NTuple<Descriptor> paramDescTuple1 = paramNode1.getCurrentDescTuple();
          NTuple<Descriptor> paramDescTuple2 = paramNode2.getCurrentDescTuple();

          // System.out.println("-param1CurTuple=" + paramDescTuple1 + " param2CurTuple="
          // + paramDescTuple2);
          // System.out.println("-- localReachSet from param1=" + localReachSet);

          if (paramDescTuple1.get(0).equals(paramDescTuple2.get(0))) {
            // if two parameters share the same prefix
            // it already has been assigned to a composite location
            // so we don't need to add an additional ordering relation caused by these two
            // paramters.
            continue;
          }

          if (arg1Tuple.size() > 0 && arg2Tuple.size() > 0
              && containsPrefix(paramNode2.getDescTuple().get(0), localReachSet)) {
            // need to propagate an ordering relation s.t. arg1 is higher
            // than arg2
            // System.out.println("-param1=" + paramNode1 + " is higher than param2=" + paramNode2);

            // add a new flow between the corresponding arguments.
            callerFlowGraph.addValueFlowEdge(arg1Tuple, arg2Tuple);
            // System.out.println("arg1=" + arg1Tuple + "   arg2=" + arg2Tuple);

            // System.out
            // .println("-arg1Tuple=" + arg1Tuple + " is higher than arg2Tuple=" + arg2Tuple);

          }

          // System.out.println();
        }
      }
    }

    // if a parameter has a composite location and the first element of the parameter location
    // matches the callee's 'this'
    // we have a more specific constraint: the caller's corresponding argument is higher than the
    // parameter location which is translated into the caller

    for (int idx = 0; idx < numParam; idx++) {
      FlowNode paramNode = calleeFlowGraph.getParamFlowNode(idx);
      CompositeLocation compLoc = paramNode.getCompositeLocation();
      System.out.println("paramNode=" + paramNode + "   compLoc=" + compLoc);
      if (compLoc != null && compLoc.get(0).getLocDescriptor().equals(min.getMethod().getThis())) {
        System.out.println("$$$COMPLOC CASE=" + compLoc + "  idx=" + idx);

        NTuple<Descriptor> argTuple = getNodeTupleByArgIdx(min, idx);
        System.out.println("--- argTuple=" + argTuple + " current compLoc="
            + callerFlowGraph.getFlowNode(argTuple).getCompositeLocation());

        NTuple<Descriptor> translatedParamTuple =
            translateCompositeLocationToCaller(idx, min, compLoc);
        System.out.println("add a flow edge= " + argTuple + "->" + translatedParamTuple);
        callerFlowGraph.addValueFlowEdge(argTuple, translatedParamTuple);

        Set<NTuple<Location>> pcLocTupleSet = getPCLocTupleSet(min);
        for (Iterator iterator = pcLocTupleSet.iterator(); iterator.hasNext();) {
          NTuple<Location> pcLocTuple = (NTuple<Location>) iterator.next();
          callerFlowGraph.addValueFlowEdge(translateToDescTuple(pcLocTuple), translatedParamTuple);
        }

      }
    }

  }

  private boolean containsPrefix(Descriptor prefixDesc, Set<FlowNode> set) {

    for (Iterator iterator = set.iterator(); iterator.hasNext();) {
      FlowNode flowNode = (FlowNode) iterator.next();
      if (flowNode.getDescTuple().startsWith(prefixDesc)) {
        System.out.println("FOUND=" + flowNode);
        return true;
      }
    }
    return false;
  }

  private NTuple<Descriptor> translateCompositeLocationToCaller(int idx, MethodInvokeNode min,
      CompositeLocation compLocForParam1) {

    NTuple<Descriptor> baseTuple = mapMethodInvokeNodeToBaseTuple.get(min);

    NTuple<Descriptor> tuple = new NTuple<Descriptor>();
    for (int i = 0; i < baseTuple.size(); i++) {
      tuple.add(baseTuple.get(i));
    }

    for (int i = 1; i < compLocForParam1.getSize(); i++) {
      Location loc = compLocForParam1.get(i);
      tuple.add(loc.getLocDescriptor());
    }

    return tuple;
  }

  private CompositeLocation generateCompositeLocation(NTuple<Location> prefixLocTuple) {

    System.out.println("generateCompositeLocation=" + prefixLocTuple);

    CompositeLocation newCompLoc = new CompositeLocation();
    for (int i = 0; i < prefixLocTuple.size(); i++) {
      newCompLoc.addLocation(prefixLocTuple.get(i));
    }

    Descriptor lastDescOfPrefix = prefixLocTuple.get(prefixLocTuple.size() - 1).getLocDescriptor();

    ClassDescriptor enclosingDescriptor;
    if (lastDescOfPrefix instanceof FieldDescriptor) {
      enclosingDescriptor = ((FieldDescriptor) lastDescOfPrefix).getType().getClassDesc();
      // System.out.println("enclosingDescriptor0=" + enclosingDescriptor);
    } else if (lastDescOfPrefix.equals(GLOBALDESC)) {
      MethodDescriptor currentMethodDesc = (MethodDescriptor) prefixLocTuple.get(0).getDescriptor();
      enclosingDescriptor = currentMethodDesc.getClassDesc();
    } else {
      // var descriptor case
      enclosingDescriptor = ((VarDescriptor) lastDescOfPrefix).getType().getClassDesc();
    }
    // System.out.println("enclosingDescriptor=" + enclosingDescriptor);

    LocationDescriptor newLocDescriptor = generateNewLocationDescriptor();
    newLocDescriptor.setEnclosingClassDesc(enclosingDescriptor);

    Location newLoc = new Location(enclosingDescriptor, newLocDescriptor.getSymbol());
    newLoc.setLocDescriptor(newLocDescriptor);
    newCompLoc.addLocation(newLoc);

    // System.out.println("--newCompLoc=" + newCompLoc);
    return newCompLoc;
  }

  private CompositeLocation generateCompositeLocation(MethodDescriptor md,
      NTuple<Descriptor> paramPrefix) {

    System.out.println("generateCompositeLocation=" + paramPrefix);

    CompositeLocation newCompLoc = convertToCompositeLocation(md, paramPrefix);

    Descriptor lastDescOfPrefix = paramPrefix.get(paramPrefix.size() - 1);
    // System.out.println("lastDescOfPrefix=" + lastDescOfPrefix + "  kind="
    // + lastDescOfPrefix.getClass());
    ClassDescriptor enclosingDescriptor;
    if (lastDescOfPrefix instanceof FieldDescriptor) {
      enclosingDescriptor = ((FieldDescriptor) lastDescOfPrefix).getType().getClassDesc();
      // System.out.println("enclosingDescriptor0=" + enclosingDescriptor);
    } else {
      // var descriptor case
      enclosingDescriptor = ((VarDescriptor) lastDescOfPrefix).getType().getClassDesc();
    }
    // System.out.println("enclosingDescriptor=" + enclosingDescriptor);

    LocationDescriptor newLocDescriptor = generateNewLocationDescriptor();
    newLocDescriptor.setEnclosingClassDesc(enclosingDescriptor);

    Location newLoc = new Location(enclosingDescriptor, newLocDescriptor.getSymbol());
    newLoc.setLocDescriptor(newLocDescriptor);
    newCompLoc.addLocation(newLoc);

    // System.out.println("--newCompLoc=" + newCompLoc);
    return newCompLoc;
  }

  private List<NTuple<Descriptor>> translatePrefixListToCallee(Descriptor baseRef,
      MethodDescriptor mdCallee, List<NTuple<Descriptor>> callerPrefixList) {

    List<NTuple<Descriptor>> calleePrefixList = new ArrayList<NTuple<Descriptor>>();

    for (int i = 0; i < callerPrefixList.size(); i++) {
      NTuple<Descriptor> prefix = callerPrefixList.get(i);
      if (prefix.startsWith(baseRef)) {
        NTuple<Descriptor> calleePrefix = new NTuple<Descriptor>();
        calleePrefix.add(mdCallee.getThis());
        for (int k = 1; k < prefix.size(); k++) {
          calleePrefix.add(prefix.get(k));
        }
        calleePrefixList.add(calleePrefix);
      }
    }

    return calleePrefixList;

  }

  public CompositeLocation convertToCompositeLocation(MethodDescriptor md, NTuple<Descriptor> tuple) {

    CompositeLocation compLoc = new CompositeLocation();

    Descriptor enclosingDescriptor = md;

    for (int i = 0; i < tuple.size(); i++) {
      Descriptor curDescriptor = tuple.get(i);
      Location locElement = new Location(enclosingDescriptor, curDescriptor.getSymbol());
      locElement.setLocDescriptor(curDescriptor);
      compLoc.addLocation(locElement);

      if (curDescriptor instanceof VarDescriptor) {
        enclosingDescriptor = md.getClassDesc();
      } else if (curDescriptor instanceof FieldDescriptor) {
        enclosingDescriptor = ((FieldDescriptor) curDescriptor).getClassDescriptor();
      } else if (curDescriptor instanceof NameDescriptor) {
        // it is "GLOBAL LOC" case!
        enclosingDescriptor = GLOBALDESC;
      } else if (curDescriptor instanceof InterDescriptor) {
        enclosingDescriptor = getFlowGraph(md).getEnclosingDescriptor(curDescriptor);
      } else {
        enclosingDescriptor = null;
      }

    }

    return compLoc;
  }

  private LocationDescriptor generateNewLocationDescriptor() {
    return new LocationDescriptor("Loc" + (locSeed++));
  }

  private int getPrefixIndex(NTuple<Descriptor> tuple1, NTuple<Descriptor> tuple2) {

    // return the index where the prefix shared by tuple1 and tuple2 is ended
    // if there is no prefix shared by both of them, return -1

    int minSize = tuple1.size();
    if (minSize > tuple2.size()) {
      minSize = tuple2.size();
    }

    int idx = -1;
    for (int i = 0; i < minSize; i++) {
      if (!tuple1.get(i).equals(tuple2.get(i))) {
        break;
      } else {
        idx++;
      }
    }

    return idx;
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

  private void addPrefixMapping(Map<NTuple<Location>, Set<NTuple<Location>>> map,
      NTuple<Location> prefix, NTuple<Location> element) {

    if (!map.containsKey(prefix)) {
      map.put(prefix, new HashSet<NTuple<Location>>());
    }
    map.get(prefix).add(element);
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

  private void extractFlowsBetweenFields(ClassDescriptor cd, FlowNode srcNode, FlowNode dstNode,
      int idx) {

    NTuple<Descriptor> srcCurTuple = srcNode.getCurrentDescTuple();
    NTuple<Descriptor> dstCurTuple = dstNode.getCurrentDescTuple();

    if (srcCurTuple.get(idx).equals(dstCurTuple.get(idx)) && srcCurTuple.size() > (idx + 1)
        && dstCurTuple.size() > (idx + 1)) {
      // value flow between fields: we don't need to add a binary relation
      // for this case

      Descriptor desc = srcCurTuple.get(idx);
      ClassDescriptor classDesc;

      if (idx == 0) {
        classDesc = ((VarDescriptor) desc).getType().getClassDesc();
      } else {
        if (desc instanceof FieldDescriptor) {
          classDesc = ((FieldDescriptor) desc).getType().getClassDesc();
        } else {
          // this case is that the local variable has a composite location assignment
          // the following element after the composite location to the local variable
          // has the enclosing descriptor of the local variable
          Descriptor localDesc = srcNode.getDescTuple().get(0);
          classDesc = ((VarDescriptor) localDesc).getType().getClassDesc();
        }
      }
      extractFlowsBetweenFields(classDesc, srcNode, dstNode, idx + 1);

    } else {

      Descriptor srcFieldDesc = srcCurTuple.get(idx);
      Descriptor dstFieldDesc = dstCurTuple.get(idx);

      System.out.println("srcFieldDesc=" + srcFieldDesc + "  dstFieldDesc=" + dstFieldDesc
          + "   idx=" + idx);
      if (!srcFieldDesc.equals(dstFieldDesc)) {
        // add a new edge
        System.out.println("-ADD EDGE");
        getHierarchyGraph(cd).addEdge(srcFieldDesc, dstFieldDesc);
      } else if (!isReference(srcFieldDesc) && !isReference(dstFieldDesc)) {
        System.out.println("-ADD EDGE");
        getHierarchyGraph(cd).addEdge(srcFieldDesc, dstFieldDesc);
      }

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

      if (cd.getClassName().equals("Object")) {
        inheritanceTree = new InheritanceTree<ClassDescriptor>(cd);
      }

      setupToAnalazeMethod(cd);
      temp_toanalyzeMethodList.removeAll(visited);

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
                && (!ssjava.isSSJavaUtil(calleemd.getClassDesc()))
                && (!calleemd.getModifiers().isNative())) {
              if (!visited.contains(calleemd)) {
                temp_toanalyzeMethodList.add(calleemd);
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

  public boolean isTransitivelyCalledFrom(MethodDescriptor callee, MethodDescriptor caller) {
    // if the callee is transitively invoked from the caller
    // return true;

    int callerIdx = toanalyze_methodDescList.indexOf(caller);
    int calleeIdx = toanalyze_methodDescList.indexOf(callee);

    if (callerIdx < calleeIdx) {
      return true;
    }

    return false;

  }

  public void constructFlowGraph() {

    System.out.println("");
    toanalyze_methodDescList = computeMethodList();

    // hack... it seems that there is a problem with topological sorting.
    // so String.toString(Object o) is appeared too higher in the call chain.
    MethodDescriptor mdToString = null;
    for (Iterator iterator = toanalyze_methodDescList.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      if (md.toString().equals("public static String String.valueOf(Object o)")) {
        mdToString = md;
        break;
      }
    }
    if (mdToString != null) {
      toanalyze_methodDescList.remove(mdToString);
      toanalyze_methodDescList.addLast(mdToString);
    }

    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    System.out.println("@@@methodDescList=" + methodDescList);
    // System.exit(0);

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

        // System.out.println("##constructSubGlobalFlowGraph");
        // GlobalFlowGraph subGlobalFlowGraph = constructSubGlobalFlowGraph(fg);
        // mapMethodDescriptorToSubGlobalFlowGraph.put(md, subGlobalFlowGraph);
        //
        // // TODO
        // System.out.println("##addValueFlowsFromCalleeSubGlobalFlowGraph");
        // addValueFlowsFromCalleeSubGlobalFlowGraph(md, subGlobalFlowGraph);
        // subGlobalFlowGraph.writeGraph("_SUBGLOBAL");
        //
        // propagateFlowsFromCalleesWithNoCompositeLocation(md);
      }
    }
    // _debug_printGraph();

  }

  private void constructGlobalFlowGraph() {
    LinkedList<MethodDescriptor> methodDescList =
        (LinkedList<MethodDescriptor>) toanalyze_methodDescList.clone();

    while (!methodDescList.isEmpty()) {
      MethodDescriptor md = methodDescList.removeLast();
      if (state.SSJAVADEBUG) {
        System.out.println();
        System.out.println("SSJAVA: Constructing a sub global flow graph: " + md);

        constructSubGlobalFlowGraph(getFlowGraph(md));

        // TODO
        System.out.println("-add Value Flows From CalleeSubGlobalFlowGraph");
        addValueFlowsFromCalleeSubGlobalFlowGraph(md);
        // subGlobalFlowGraph.writeGraph("_SUBGLOBAL");

        // System.out.println("-propagate Flows From Callees With No CompositeLocation");
        // propagateFlowsFromCalleesWithNoCompositeLocation(md);

        // mark if a parameter has incoming flows
        checkParamNodesInSubGlobalFlowGraph(md);

      }
    }
  }

  private void checkParamNodesInSubGlobalFlowGraph(MethodDescriptor md) {
    GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
    FlowGraph flowGraph = getFlowGraph(md);

    Set<FlowNode> paramFlowNodeSet = flowGraph.getParamFlowNodeSet();
    for (Iterator iterator = paramFlowNodeSet.iterator(); iterator.hasNext();) {
      FlowNode paramFlowNode = (FlowNode) iterator.next();
      System.out.println("paramFlowNode=" + paramFlowNode);
      NTuple<Descriptor> paramDescTuple = paramFlowNode.getDescTuple();
      NTuple<Location> paramLocTuple = translateToLocTuple(md, paramDescTuple);
      GlobalFlowNode paramGlobalNode = globalFlowGraph.getFlowNode(paramLocTuple);

      Set<GlobalFlowNode> incomingNodeSet =
          globalFlowGraph.getIncomingNodeSetByPrefix(paramLocTuple.get(0));

      if (incomingNodeSet.size() > 0) {
        paramGlobalNode.setParamNodeWithIncomingFlows(true);
      }

    }
  }

  private Set<MethodInvokeNode> getMethodInvokeNodeSet(MethodDescriptor md) {
    if (!mapMethodDescriptorToMethodInvokeNodeSet.containsKey(md)) {
      mapMethodDescriptorToMethodInvokeNodeSet.put(md, new HashSet<MethodInvokeNode>());
    }
    return mapMethodDescriptorToMethodInvokeNodeSet.get(md);
  }

  private void propagateFlowsFromCalleesWithNoCompositeLocation(MethodDescriptor mdCaller) {

    // the transformation for a call site propagates flows through parameters
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
          propagateFlowsToCallerWithNoCompositeLocation(min, mdCaller, possibleMdCallee);
        }

      }
    }

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
      analyzeSwitchStatementNode(md, nametable, (SwitchStatementNode) bsn, implicitFlowTupleSet);
      break;

    }

  }

  private void analyzeSwitchBlockNode(MethodDescriptor md, SymbolTable nametable,
      SwitchBlockNode sbn, NodeTupleSet implicitFlowTupleSet) {

    analyzeFlowBlockNode(md, nametable, sbn.getSwitchBlockStatement(), implicitFlowTupleSet);

  }

  private void analyzeSwitchStatementNode(MethodDescriptor md, SymbolTable nametable,
      SwitchStatementNode ssn, NodeTupleSet implicitFlowTupleSet) {

    NodeTupleSet condTupleNode = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, ssn.getCondition(), condTupleNode, null,
        implicitFlowTupleSet, false);

    NodeTupleSet newImplicitTupleSet = new NodeTupleSet();

    newImplicitTupleSet.addTupleSet(implicitFlowTupleSet);
    newImplicitTupleSet.addTupleSet(condTupleNode);

    if (needToGenerateInterLoc(newImplicitTupleSet)) {
      // need to create an intermediate node for the GLB of conditional
      // locations & implicit flows
      System.out.println("10");

      NTuple<Descriptor> interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
      for (Iterator<NTuple<Descriptor>> idxIter = newImplicitTupleSet.iterator(); idxIter.hasNext();) {
        NTuple<Descriptor> tuple = idxIter.next();
        addFlowGraphEdge(md, tuple, interTuple);
      }
      newImplicitTupleSet.clear();
      newImplicitTupleSet.addTuple(interTuple);
    }

    BlockNode sbn = ssn.getSwitchBody();
    for (int i = 0; i < sbn.size(); i++) {
      analyzeSwitchBlockNode(md, nametable, (SwitchBlockNode) sbn.get(i), newImplicitTupleSet);
    }

  }

  private void analyzeFlowSubBlockNode(MethodDescriptor md, SymbolTable nametable,
      SubBlockNode sbn, NodeTupleSet implicitFlowTupleSet) {
    analyzeFlowBlockNode(md, nametable, sbn.getBlockNode(), implicitFlowTupleSet);
  }

  private void analyzeFlowReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode rn,
      NodeTupleSet implicitFlowTupleSet) {

    // System.out.println("-analyzeFlowReturnNode=" + rn.printNode(0));
    ExpressionNode returnExp = rn.getReturnExpression();

    if (returnExp != null) {
      NodeTupleSet nodeSet = new NodeTupleSet();
      // if a return expression returns a literal value, nodeSet is empty
      analyzeFlowExpressionNode(md, nametable, returnExp, nodeSet, false);
      FlowGraph fg = getFlowGraph(md);

      // if (implicitFlowTupleSet.size() == 1
      // &&
      // fg.getFlowNode(implicitFlowTupleSet.iterator().next()).isIntermediate())
      // {
      //
      // // since there is already an intermediate node for the GLB of implicit
      // flows
      // // we don't need to create another intermediate node.
      // // just re-use the intermediate node for implicit flows.
      //
      // FlowNode meetNode =
      // fg.getFlowNode(implicitFlowTupleSet.iterator().next());
      //
      // for (Iterator iterator = nodeSet.iterator(); iterator.hasNext();) {
      // NTuple<Descriptor> returnNodeTuple = (NTuple<Descriptor>)
      // iterator.next();
      // fg.addValueFlowEdge(returnNodeTuple, meetNode.getDescTuple());
      // }
      //
      // }

      NodeTupleSet currentFlowTupleSet = new NodeTupleSet();

      // add tuples from return node
      currentFlowTupleSet.addTupleSet(nodeSet);

      // add tuples corresponding to the current implicit flows
      currentFlowTupleSet.addTupleSet(implicitFlowTupleSet);

      // System.out.println("---currentFlowTupleSet=" + currentFlowTupleSet);

      if (needToGenerateInterLoc(currentFlowTupleSet)) {
        System.out.println("9");

        FlowNode meetNode = fg.createIntermediateNode();
        for (Iterator iterator = currentFlowTupleSet.iterator(); iterator.hasNext();) {
          NTuple<Descriptor> currentFlowTuple = (NTuple<Descriptor>) iterator.next();
          fg.addValueFlowEdge(currentFlowTuple, meetNode.getDescTuple());
        }
        fg.addReturnFlowNode(meetNode.getDescTuple());
      } else {
        // currentFlowTupleSet = removeLiteralTuple(currentFlowTupleSet);
        for (Iterator iterator = currentFlowTupleSet.iterator(); iterator.hasNext();) {
          NTuple<Descriptor> currentFlowTuple = (NTuple<Descriptor>) iterator.next();
          fg.addReturnFlowNode(currentFlowTuple);
        }
      }

    }

  }

  private NodeTupleSet removeLiteralTuple(NodeTupleSet inSet) {
    NodeTupleSet tupleSet = new NodeTupleSet();
    for (Iterator<NTuple<Descriptor>> iter = inSet.iterator(); iter.hasNext();) {
      NTuple<Descriptor> tuple = iter.next();
      if (!tuple.get(0).equals(LITERALDESC)) {
        tupleSet.addTuple(tuple);
      }
    }
    return tupleSet;
  }

  private boolean needToGenerateInterLoc(NodeTupleSet tupleSet) {
    int size = 0;
    for (Iterator<NTuple<Descriptor>> iter = tupleSet.iterator(); iter.hasNext();) {
      NTuple<Descriptor> descTuple = iter.next();
      if (!descTuple.get(0).equals(LITERALDESC)) {
        size++;
      }
    }
    if (size > 1) {
      System.out.println("needToGenerateInterLoc=" + tupleSet + "  size=" + size);
      return true;
    } else {
      return false;
    }
  }

  private void analyzeFlowLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln,
      NodeTupleSet implicitFlowTupleSet) {

    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {

      NodeTupleSet condTupleNode = new NodeTupleSet();
      analyzeFlowExpressionNode(md, nametable, ln.getCondition(), condTupleNode, null,
          implicitFlowTupleSet, false);

      NodeTupleSet newImplicitTupleSet = new NodeTupleSet();

      newImplicitTupleSet.addTupleSet(implicitFlowTupleSet);
      newImplicitTupleSet.addTupleSet(condTupleNode);

      newImplicitTupleSet.addGlobalFlowTupleSet(implicitFlowTupleSet.getGlobalLocTupleSet());
      newImplicitTupleSet.addGlobalFlowTupleSet(condTupleNode.getGlobalLocTupleSet());

      if (needToGenerateInterLoc(newImplicitTupleSet)) {
        // need to create an intermediate node for the GLB of conditional
        // locations & implicit flows
        System.out.println("6");

        NTuple<Descriptor> interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
        for (Iterator<NTuple<Descriptor>> idxIter = newImplicitTupleSet.iterator(); idxIter
            .hasNext();) {
          NTuple<Descriptor> tuple = idxIter.next();
          addFlowGraphEdge(md, tuple, interTuple);
        }
        newImplicitTupleSet.clear();
        newImplicitTupleSet.addTuple(interTuple);

      }

      // ///////////
      // System.out.println("condTupleNode="+condTupleNode);
      // NTuple<Descriptor> interTuple =
      // getFlowGraph(md).createIntermediateNode().getDescTuple();
      //
      // for (Iterator<NTuple<Descriptor>> idxIter = condTupleNode.iterator();
      // idxIter.hasNext();) {
      // NTuple<Descriptor> tuple = idxIter.next();
      // addFlowGraphEdge(md, tuple, interTuple);
      // }

      // for (Iterator<NTuple<Descriptor>> idxIter =
      // implicitFlowTupleSet.iterator(); idxIter
      // .hasNext();) {
      // NTuple<Descriptor> tuple = idxIter.next();
      // addFlowGraphEdge(md, tuple, interTuple);
      // }

      // NodeTupleSet newImplicitSet = new NodeTupleSet();
      // newImplicitSet.addTuple(interTuple);
      analyzeFlowBlockNode(md, nametable, ln.getBody(), newImplicitTupleSet);
      // ///////////

      // condTupleNode.addTupleSet(implicitFlowTupleSet);

      // add edges from condNodeTupleSet to all nodes of conditional nodes
      // analyzeFlowBlockNode(md, nametable, ln.getBody(), condTupleNode);

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

      NodeTupleSet newImplicitTupleSet = new NodeTupleSet();
      newImplicitTupleSet.addTupleSet(implicitFlowTupleSet);
      newImplicitTupleSet.addTupleSet(condTupleNode);

      if (needToGenerateInterLoc(newImplicitTupleSet)) {
        // need to create an intermediate node for the GLB of conditional
        // locations & implicit flows
        System.out.println("7");

        NTuple<Descriptor> interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
        for (Iterator<NTuple<Descriptor>> idxIter = newImplicitTupleSet.iterator(); idxIter
            .hasNext();) {
          NTuple<Descriptor> tuple = idxIter.next();
          addFlowGraphEdge(md, tuple, interTuple);
        }
        newImplicitTupleSet.clear();
        newImplicitTupleSet.addTuple(interTuple);

      }

      // ///////////
      // NTuple<Descriptor> interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
      //
      // for (Iterator<NTuple<Descriptor>> idxIter = condTupleNode.iterator(); idxIter.hasNext();) {
      // NTuple<Descriptor> tuple = idxIter.next();
      // addFlowGraphEdge(md, tuple, interTuple);
      // }
      //
      // for (Iterator<NTuple<Descriptor>> idxIter = implicitFlowTupleSet.iterator(); idxIter
      // .hasNext();) {
      // NTuple<Descriptor> tuple = idxIter.next();
      // addFlowGraphEdge(md, tuple, interTuple);
      // }
      //
      // NodeTupleSet newImplicitSet = new NodeTupleSet();
      // newImplicitSet.addTuple(interTuple);
      analyzeFlowBlockNode(md, bn.getVarTable(), ln.getUpdate(), newImplicitTupleSet);
      analyzeFlowBlockNode(md, bn.getVarTable(), ln.getBody(), newImplicitTupleSet);
      // ///////////

      // condTupleNode.addTupleSet(implicitFlowTupleSet);
      //
      // analyzeFlowBlockNode(md, bn.getVarTable(), ln.getUpdate(),
      // condTupleNode);
      // analyzeFlowBlockNode(md, bn.getVarTable(), ln.getBody(),
      // condTupleNode);

    }

  }

  private void analyzeFlowIfStatementNode(MethodDescriptor md, SymbolTable nametable,
      IfStatementNode isn, NodeTupleSet implicitFlowTupleSet) {

    // System.out.println("analyzeFlowIfStatementNode=" + isn.printNode(0));

    NodeTupleSet condTupleNode = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, isn.getCondition(), condTupleNode, null,
        implicitFlowTupleSet, false);

    NodeTupleSet newImplicitTupleSet = new NodeTupleSet();

    newImplicitTupleSet.addTupleSet(implicitFlowTupleSet);
    newImplicitTupleSet.addTupleSet(condTupleNode);

    // System.out.println("$$$GGGcondTupleNode=" + condTupleNode.getGlobalLocTupleSet());
    // System.out.println("-condTupleNode=" + condTupleNode);
    // System.out.println("-implicitFlowTupleSet=" + implicitFlowTupleSet);
    // System.out.println("-newImplicitTupleSet=" + newImplicitTupleSet);

    if (needToGenerateInterLoc(newImplicitTupleSet)) {
      System.out.println("5");

      // need to create an intermediate node for the GLB of conditional locations & implicit flows
      NTuple<Descriptor> interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
      for (Iterator<NTuple<Descriptor>> idxIter = newImplicitTupleSet.iterator(); idxIter.hasNext();) {
        NTuple<Descriptor> tuple = idxIter.next();
        addFlowGraphEdge(md, tuple, interTuple);
      }
      newImplicitTupleSet.clear();
      newImplicitTupleSet.addTuple(interTuple);
    }

    // GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
    // for (Iterator<NTuple<Location>> iterator = condTupleNode.globalIterator();
    // iterator.hasNext();) {
    // NTuple<Location> calleeReturnLocTuple = iterator.next();
    // for (Iterator<NTuple<Descriptor>> iter2 = newImplicitTupleSet.iterator(); iter2.hasNext();) {
    // NTuple<Descriptor> callerImplicitTuple = iter2.next();
    // globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple,
    // translateToLocTuple(md, callerImplicitTuple));
    // }
    // }
    newImplicitTupleSet.addGlobalFlowTupleSet(condTupleNode.getGlobalLocTupleSet());

    analyzeFlowBlockNode(md, nametable, isn.getTrueBlock(), newImplicitTupleSet);

    if (isn.getFalseBlock() != null) {
      analyzeFlowBlockNode(md, nametable, isn.getFalseBlock(), newImplicitTupleSet);
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
      System.out.println("-analyzeFlowDeclarationNode=" + dn.printNode(0));

      NodeTupleSet nodeSetRHS = new NodeTupleSet();
      analyzeFlowExpressionNode(md, nametable, dn.getExpression(), nodeSetRHS, null,
          implicitFlowTupleSet, false);

      // creates edges from RHS to LHS
      NTuple<Descriptor> interTuple = null;
      if (needToGenerateInterLoc(nodeSetRHS)) {
        System.out.println("3");
        interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
      }

      for (Iterator<NTuple<Descriptor>> iter = nodeSetRHS.iterator(); iter.hasNext();) {
        NTuple<Descriptor> fromTuple = iter.next();
        System.out.println("fromTuple=" + fromTuple + "  interTuple=" + interTuple + " tupleLSH="
            + tupleLHS);
        addFlowGraphEdge(md, fromTuple, interTuple, tupleLHS);
      }

      // creates edges from implicitFlowTupleSet to LHS
      for (Iterator<NTuple<Descriptor>> iter = implicitFlowTupleSet.iterator(); iter.hasNext();) {
        NTuple<Descriptor> implicitTuple = iter.next();
        addFlowGraphEdge(md, implicitTuple, tupleLHS);
      }

      GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
      for (Iterator<NTuple<Location>> iterator = nodeSetRHS.globalIterator(); iterator.hasNext();) {
        NTuple<Location> calleeReturnLocTuple = iterator.next();

        globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple, translateToLocTuple(md, tupleLHS));
      }

      for (Iterator<NTuple<Location>> iterator = implicitFlowTupleSet.globalIterator(); iterator
          .hasNext();) {
        NTuple<Location> implicitGlobalTuple = iterator.next();

        globalFlowGraph.addValueFlowEdge(implicitGlobalTuple, translateToLocTuple(md, tupleLHS));
      }

      System.out.println("-nodeSetRHS=" + nodeSetRHS);
      System.out.println("-implicitFlowTupleSet=" + implicitFlowTupleSet);

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

    // System.out.println("en=" + en.printNode(0) + "   class=" + en.getClass());

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
      analyzeFlowLiteralNode(md, nametable, (LiteralNode) en, nodeSet);
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

    // System.out.println("analyzeFlowTertiaryNode=" + tn.printNode(0));

    NodeTupleSet tertiaryTupleNode = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, tn.getCond(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    NodeTupleSet newImplicitTupleSet = new NodeTupleSet();
    newImplicitTupleSet.addTupleSet(implicitFlowTupleSet);
    newImplicitTupleSet.addTupleSet(tertiaryTupleNode);

    // System.out.println("$$$GGGcondTupleNode=" + tertiaryTupleNode.getGlobalLocTupleSet());
    // System.out.println("-tertiaryTupleNode=" + tertiaryTupleNode);
    // System.out.println("-implicitFlowTupleSet=" + implicitFlowTupleSet);
    // System.out.println("-newImplicitTupleSet=" + newImplicitTupleSet);

    if (needToGenerateInterLoc(newImplicitTupleSet)) {
      System.out.println("15");
      // need to create an intermediate node for the GLB of conditional locations & implicit flows
      NTuple<Descriptor> interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
      for (Iterator<NTuple<Descriptor>> idxIter = newImplicitTupleSet.iterator(); idxIter.hasNext();) {
        NTuple<Descriptor> tuple = idxIter.next();
        addFlowGraphEdge(md, tuple, interTuple);
      }
      newImplicitTupleSet.clear();
      newImplicitTupleSet.addTuple(interTuple);
    }

    newImplicitTupleSet.addGlobalFlowTupleSet(tertiaryTupleNode.getGlobalLocTupleSet());

    System.out.println("---------newImplicitTupleSet=" + newImplicitTupleSet);
    // add edges from tertiaryTupleNode to all nodes of conditional nodes
    // tertiaryTupleNode.addTupleSet(implicitFlowTupleSet);
    analyzeFlowExpressionNode(md, nametable, tn.getTrueExpr(), tertiaryTupleNode, null,
        newImplicitTupleSet, false);

    analyzeFlowExpressionNode(md, nametable, tn.getFalseExpr(), tertiaryTupleNode, null,
        newImplicitTupleSet, false);

    nodeSet.addGlobalFlowTupleSet(tertiaryTupleNode.getGlobalLocTupleSet());
    nodeSet.addTupleSet(tertiaryTupleNode);

    System.out.println("#tertiary node set=" + nodeSet);
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

    if (!mapMethodDescToParamNodeFlowsToReturnValue.containsKey(md)) {
      mapMethodDescToParamNodeFlowsToReturnValue.put(md, new HashSet<FlowNode>());
    }

    return mapMethodDescToParamNodeFlowsToReturnValue.get(md);
  }

  private Set<NTuple<Location>> getPCLocTupleSet(MethodInvokeNode min) {
    if (!mapMethodInvokeNodeToPCLocTupleSet.containsKey(min)) {
      mapMethodInvokeNodeToPCLocTupleSet.put(min, new HashSet<NTuple<Location>>());
    }
    return mapMethodInvokeNodeToPCLocTupleSet.get(min);
  }

  private void analyzeFlowMethodInvokeNode(MethodDescriptor mdCaller, SymbolTable nametable,
      MethodInvokeNode min, NodeTupleSet nodeSet, NodeTupleSet implicitFlowTupleSet) {

    System.out.println("analyzeFlowMethodInvokeNode=" + min.printNode(0));

    if (!toanalyze_methodDescList.contains(min.getMethod())) {
      return;
    }

    addMapMethodDescToMethodInvokeNodeSet(min);

    Set<NTuple<Location>> pcLocTupleSet = getPCLocTupleSet(min);
    for (Iterator iterator = implicitFlowTupleSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> pcDescTuple = (NTuple<Descriptor>) iterator.next();
      if (!pcDescTuple.get(0).equals(LITERALDESC)) {
        // here we don't need to add the literal value as a PC location
        pcLocTupleSet.add(translateToLocTuple(mdCaller, pcDescTuple));
      }
    }

    mapMethodInvokeNodeToArgIdxMap.put(min, new HashMap<Integer, NTuple<Descriptor>>());

    if (nodeSet == null) {
      nodeSet = new NodeTupleSet();
    }

    MethodDescriptor mdCallee = min.getMethod();

    NameDescriptor baseName = min.getBaseName();
    boolean isSystemout = false;
    if (baseName != null) {
      isSystemout = baseName.getSymbol().equals("System.out");
    }

    if (!ssjava.isSSJavaUtil(mdCallee.getClassDesc()) && !ssjava.isTrustMethod(mdCallee)
        && !isSystemout) {

      addMapCallerMethodDescToMethodInvokeNodeSet(mdCaller, min);

      FlowGraph calleeFlowGraph = getFlowGraph(mdCallee);
      System.out.println("mdCallee=" + mdCallee + " calleeFlowGraph=" + calleeFlowGraph);
      Set<FlowNode> calleeReturnSet = calleeFlowGraph.getReturnNodeSet();

      System.out.println("---calleeReturnSet=" + calleeReturnSet);

      NodeTupleSet tupleSet = new NodeTupleSet();

      if (min.getExpression() != null) {

        NodeTupleSet baseNodeSet = new NodeTupleSet();
        analyzeFlowExpressionNode(mdCaller, nametable, min.getExpression(), baseNodeSet, null,
            implicitFlowTupleSet, false);
        System.out.println("baseNodeSet=" + baseNodeSet);

        assert (baseNodeSet.size() == 1);
        NTuple<Descriptor> baseTuple = baseNodeSet.iterator().next();
        if (baseTuple.get(0) instanceof InterDescriptor) {
          if (baseTuple.size() > 1) {
            throw new Error();
          }
          FlowNode interNode = getFlowGraph(mdCaller).getFlowNode(baseTuple);
          baseTuple = translateBaseTuple(interNode, baseTuple);
        }
        mapMethodInvokeNodeToBaseTuple.put(min, baseTuple);

        if (!min.getMethod().isStatic()) {
          addArgIdxMap(min, 0, baseTuple);

          for (Iterator iterator = calleeReturnSet.iterator(); iterator.hasNext();) {
            FlowNode returnNode = (FlowNode) iterator.next();
            NTuple<Descriptor> returnDescTuple = returnNode.getDescTuple();
            if (returnDescTuple.startsWith(mdCallee.getThis())) {
              // the location type of the return value is started with 'this'
              // reference
              NTuple<Descriptor> inFlowTuple = new NTuple<Descriptor>(baseTuple.getList());

              if (inFlowTuple.get(0) instanceof InterDescriptor) {
                // min.getExpression()
              } else {

              }

              inFlowTuple.addAll(returnDescTuple.subList(1, returnDescTuple.size()));
              // nodeSet.addTuple(inFlowTuple);
              System.out.println("1CREATE A NEW TUPLE=" + inFlowTuple + "  from="
                  + mdCallee.getThis());
              // tupleSet.addTuple(inFlowTuple);
              tupleSet.addTuple(baseTuple);
            } else {
              // TODO
              System.out.println("returnNode=" + returnNode);
              Set<FlowNode> inFlowSet = calleeFlowGraph.getIncomingFlowNodeSet(returnNode);
              // System.out.println("inFlowSet=" + inFlowSet + "   from retrunNode=" + returnNode);
              for (Iterator iterator2 = inFlowSet.iterator(); iterator2.hasNext();) {
                FlowNode inFlowNode = (FlowNode) iterator2.next();
                if (inFlowNode.getDescTuple().startsWith(mdCallee.getThis())) {
                  // nodeSet.addTupleSet(baseNodeSet);
                  System.out.println("2CREATE A NEW TUPLE=" + baseNodeSet + "  from="
                      + mdCallee.getThis());
                  tupleSet.addTupleSet(baseNodeSet);
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
          analyzeFlowExpressionNode(mdCaller, nametable, en, argTupleSet, false);
          // if argument is liternal node, argTuple is set to NULL
          System.out.println("---arg idx=" + idx + "   argTupleSet=" + argTupleSet);
          NTuple<Descriptor> argTuple = generateArgTuple(mdCaller, argTupleSet);

          // if an argument is literal value,
          // we need to create an itermediate node so that we could assign a composite location to
          // that node if needed
          if (argTuple.size() > 0
              && (argTuple.get(0).equals(GLOBALDESC) || argTuple.get(0).equals(LITERALDESC))) {
            /*
             * System.out.println("***GLOBAL ARG TUPLE CASE=" + argTuple); System.out.println("8");
             * 
             * NTuple<Descriptor> interTuple =
             * getFlowGraph(mdCaller).createIntermediateNode().getDescTuple(); ((InterDescriptor)
             * interTuple.get(0)).setMethodArgIdxPair(min, idx); addFlowGraphEdge(mdCaller,
             * argTuple, interTuple); argTuple = interTuple; addArgIdxMap(min, idx, argTuple);
             * System.out.println("new min mapping i=" + idx + "  ->" + argTuple);
             */
            argTuple = new NTuple<Descriptor>();
          }

          addArgIdxMap(min, idx, argTuple);

          FlowNode paramNode = calleeFlowGraph.getParamFlowNode(idx);

          // check whether a param node in the callee graph has incoming flows
          // if it has incoming flows, the corresponding arg should be lower than the current PC
          // Descriptor prefix = paramNode.getDescTuple().get(0);
          // if (calleeFlowGraph.getIncomingNodeSetByPrefix(prefix).size() > 0) {
          // for (Iterator<NTuple<Descriptor>> iterator = implicitFlowTupleSet.iterator(); iterator
          // .hasNext();) {
          // NTuple<Descriptor> pcTuple = iterator.next();
          // System.out.println("add edge pcTuple =" + pcTuple + " -> " + argTuple);
          // addFlowGraphEdge(md, pcTuple, argTuple);
          // }
          // }

          System.out.println("paramNode=" + paramNode + "  calleeReturnSet=" + calleeReturnSet);
          if (hasInFlowTo(calleeFlowGraph, paramNode, calleeReturnSet)
              || mdCallee.getModifiers().isNative()) {
            addParamNodeFlowingToReturnValue(mdCallee, paramNode);
            // nodeSet.addTupleSet(argTupleSet);
            System.out.println("3CREATE A NEW TUPLE=" + argTupleSet + "  from=" + paramNode);
            tupleSet.addTupleSet(argTupleSet);
          }
        }

      }

      if (mdCallee.getReturnType() != null && !mdCallee.getReturnType().isVoid()) {
        FlowReturnNode returnHolderNode = getFlowGraph(mdCaller).createReturnNode(min);

        if (needToGenerateInterLoc(tupleSet)) {
          System.out.println("20");
          FlowGraph fg = getFlowGraph(mdCaller);
          FlowNode interNode = fg.createIntermediateNode();
          interNode.setFormHolder(true);

          NTuple<Descriptor> interTuple = interNode.getDescTuple();

          for (Iterator iterator = tupleSet.iterator(); iterator.hasNext();) {
            NTuple<Descriptor> tuple = (NTuple<Descriptor>) iterator.next();

            Set<NTuple<Descriptor>> addSet = new HashSet<NTuple<Descriptor>>();
            FlowNode node = fg.getFlowNode(tuple);
            if (node instanceof FlowReturnNode) {
              addSet.addAll(fg.getReturnTupleSet(((FlowReturnNode) node).getReturnTupleSet()));
            } else {
              addSet.add(tuple);
            }
            for (Iterator iterator2 = addSet.iterator(); iterator2.hasNext();) {
              NTuple<Descriptor> higher = (NTuple<Descriptor>) iterator2.next();
              addFlowGraphEdge(mdCaller, higher, interTuple);
            }
          }

          returnHolderNode.addTuple(interTuple);

          nodeSet.addTuple(interTuple);
          System.out.println("ADD TUPLESET=" + interTuple + " to returnnode=" + returnHolderNode);

        } else {
          returnHolderNode.addTupleSet(tupleSet);
          System.out.println("ADD TUPLESET=" + tupleSet + " to returnnode=" + returnHolderNode);
        }
        // setNode.addTupleSet(tupleSet);
        // NodeTupleSet setFromReturnNode=new NodeTupleSet();
        // setFromReturnNode.addTuple(tuple);

        NodeTupleSet holderTupleSet =
            getNodeTupleSetFromReturnNode(getFlowGraph(mdCaller), returnHolderNode);

        System.out.println("HOLDER TUPLe SET=" + holderTupleSet);
        nodeSet.addTupleSet(holderTupleSet);

        nodeSet.addTuple(returnHolderNode.getDescTuple());
      }

      // propagateFlowsFromCallee(min, md, min.getMethod());

      // when generating the global flow graph,
      // we need to add ordering relations from the set of callee return loc tuple to LHS of the
      // caller assignment
      for (Iterator iterator = calleeReturnSet.iterator(); iterator.hasNext();) {
        FlowNode calleeReturnNode = (FlowNode) iterator.next();
        NTuple<Location> calleeReturnLocTuple =
            translateToLocTuple(mdCallee, calleeReturnNode.getDescTuple());
        System.out.println("calleeReturnLocTuple=" + calleeReturnLocTuple);
        NTuple<Location> transaltedToCaller =
            translateToCallerLocTuple(min, mdCallee, mdCaller, calleeReturnLocTuple);
        // System.out.println("translateToCallerLocTuple="
        // + translateToCallerLocTuple(min, mdCallee, mdCaller, calleeReturnLocTuple));
        if (transaltedToCaller.size() > 0) {
          nodeSet.addGlobalFlowTuple(translateToCallerLocTuple(min, mdCallee, mdCaller,
              calleeReturnLocTuple));
        }
      }

      System.out.println("min nodeSet=" + nodeSet);

    }

  }

  private NodeTupleSet getNodeTupleSetFromReturnNode(FlowGraph fg, FlowReturnNode node) {
    NodeTupleSet nts = new NodeTupleSet();

    Set<NTuple<Descriptor>> returnSet = node.getReturnTupleSet();

    for (Iterator iterator = returnSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> tuple = (NTuple<Descriptor>) iterator.next();
      FlowNode flowNode = fg.getFlowNode(tuple);
      if (flowNode instanceof FlowReturnNode) {
        returnSet.addAll(recurGetNode(fg, (FlowReturnNode) flowNode));
      } else {
        returnSet.add(tuple);
      }
    }

    for (Iterator iterator = returnSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> nTuple = (NTuple<Descriptor>) iterator.next();
      nts.addTuple(nTuple);
    }

    return nts;

  }

  private Set<NTuple<Descriptor>> recurGetNode(FlowGraph fg, FlowReturnNode rnode) {

    Set<NTuple<Descriptor>> tupleSet = new HashSet<NTuple<Descriptor>>();

    Set<NTuple<Descriptor>> returnSet = rnode.getReturnTupleSet();
    for (Iterator iterator = returnSet.iterator(); iterator.hasNext();) {
      NTuple<Descriptor> tuple = (NTuple<Descriptor>) iterator.next();
      FlowNode flowNode = fg.getFlowNode(tuple);
      if (flowNode instanceof FlowReturnNode) {
        tupleSet.addAll(recurGetNode(fg, (FlowReturnNode) flowNode));
      }
      tupleSet.add(tuple);
    }

    return tupleSet;
  }

  private NTuple<Descriptor> generateArgTuple(MethodDescriptor mdCaller, NodeTupleSet argTupleSet) {

    int size = 0;

    // if argTupleSet is empty, it comes from the top location
    if (argTupleSet.size() == 0) {
      NTuple<Descriptor> descTuple = new NTuple<Descriptor>();
      descTuple.add(LITERALDESC);
      return descTuple;
    }

    Set<NTuple<Descriptor>> argTupleSetNonLiteral = new HashSet<NTuple<Descriptor>>();

    for (Iterator<NTuple<Descriptor>> iter = argTupleSet.iterator(); iter.hasNext();) {
      NTuple<Descriptor> descTuple = iter.next();
      if (!descTuple.get(0).equals(LITERALDESC)) {
        argTupleSetNonLiteral.add(descTuple);
      }
    }

    if (argTupleSetNonLiteral.size() > 1) {
      System.out.println("11");

      NTuple<Descriptor> interTuple =
          getFlowGraph(mdCaller).createIntermediateNode().getDescTuple();
      for (Iterator<NTuple<Descriptor>> idxIter = argTupleSet.iterator(); idxIter.hasNext();) {
        NTuple<Descriptor> tuple = idxIter.next();
        addFlowGraphEdge(mdCaller, tuple, interTuple);
      }
      return interTuple;
    } else if (argTupleSetNonLiteral.size() == 1) {
      return argTupleSetNonLiteral.iterator().next();
    } else {
      return argTupleSet.iterator().next();
    }

  }

  private boolean hasInFlowTo(FlowGraph fg, FlowNode inNode, Set<FlowNode> nodeSet) {
    // return true if inNode has in-flows to nodeSet

    if (nodeSet.contains(inNode)) {
      // in this case, the method directly returns a parameter variable.
      return true;
    }
    // Set<FlowNode> reachableSet = fg.getReachFlowNodeSetFrom(inNode);
    Set<FlowNode> reachableSet = fg.getReachableSetFrom(inNode.getDescTuple());
    // System.out.println("inNode=" + inNode + "  reachalbeSet=" + reachableSet);

    for (Iterator iterator = reachableSet.iterator(); iterator.hasNext();) {
      FlowNode fn = (FlowNode) iterator.next();
      if (nodeSet.contains(fn)) {
        return true;
      }
    }
    return false;
  }

  private NTuple<Descriptor> getNodeTupleByArgIdx(MethodInvokeNode min, int idx) {
    return mapMethodInvokeNodeToArgIdxMap.get(min).get(new Integer(idx));
  }

  private void addArgIdxMap(MethodInvokeNode min, int idx, NTuple<Descriptor> argTuple /*
                                                                                        * NodeTupleSet
                                                                                        * tupleSet
                                                                                        */) {
    Map<Integer, NTuple<Descriptor>> mapIdxToTuple = mapMethodInvokeNodeToArgIdxMap.get(min);
    if (mapIdxToTuple == null) {
      mapIdxToTuple = new HashMap<Integer, NTuple<Descriptor>>();
      mapMethodInvokeNodeToArgIdxMap.put(min, mapIdxToTuple);
    }
    mapIdxToTuple.put(new Integer(idx), argTuple);
  }

  private void analyzeFlowLiteralNode(MethodDescriptor md, SymbolTable nametable, LiteralNode en,
      NodeTupleSet nodeSet) {
    NTuple<Descriptor> tuple = new NTuple<Descriptor>();
    tuple.add(LITERALDESC);
    nodeSet.addTuple(tuple);
  }

  private void analyzeFlowArrayAccessNode(MethodDescriptor md, SymbolTable nametable,
      ArrayAccessNode aan, NodeTupleSet nodeSet, boolean isLHS) {

    System.out.println("analyzeFlowArrayAccessNode aan=" + aan.printNode(0));
    String currentArrayAccessNodeExpStr = aan.printNode(0);
    arrayAccessNodeStack.push(aan.printNode(0));

    NodeTupleSet expNodeTupleSet = new NodeTupleSet();
    NTuple<Descriptor> base =
        analyzeFlowExpressionNode(md, nametable, aan.getExpression(), expNodeTupleSet, isLHS);
    System.out.println("-base=" + base);

    nodeSet.setMethodInvokeBaseDescTuple(base);
    NodeTupleSet idxNodeTupleSet = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, aan.getIndex(), idxNodeTupleSet, isLHS);

    arrayAccessNodeStack.pop();

    if (isLHS) {
      // need to create an edge from idx to array
      for (Iterator<NTuple<Descriptor>> idxIter = idxNodeTupleSet.iterator(); idxIter.hasNext();) {
        NTuple<Descriptor> idxTuple = idxIter.next();
        for (Iterator<NTuple<Descriptor>> arrIter = expNodeTupleSet.iterator(); arrIter.hasNext();) {
          NTuple<Descriptor> arrTuple = arrIter.next();
          getFlowGraph(md).addValueFlowEdge(idxTuple, arrTuple);
        }
      }

      GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
      for (Iterator<NTuple<Location>> iterator = idxNodeTupleSet.globalIterator(); iterator
          .hasNext();) {
        NTuple<Location> calleeReturnLocTuple = iterator.next();
        for (Iterator<NTuple<Descriptor>> arrIter = expNodeTupleSet.iterator(); arrIter.hasNext();) {
          NTuple<Descriptor> arrTuple = arrIter.next();

          globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple, translateToLocTuple(md, arrTuple));
        }
      }

      nodeSet.addTupleSet(expNodeTupleSet);
    } else {

      NodeTupleSet nodeSetArrayAccessExp = new NodeTupleSet();

      nodeSetArrayAccessExp.addTupleSet(expNodeTupleSet);
      nodeSetArrayAccessExp.addTupleSet(idxNodeTupleSet);

      if (arrayAccessNodeStack.isEmpty()
          || !arrayAccessNodeStack.peek().startsWith(currentArrayAccessNodeExpStr)) {

        if (needToGenerateInterLoc(nodeSetArrayAccessExp)) {
          System.out.println("1");
          FlowNode interNode = getFlowGraph(md).createIntermediateNode();
          NTuple<Descriptor> interTuple = interNode.getDescTuple();

          for (Iterator<NTuple<Descriptor>> iter = nodeSetArrayAccessExp.iterator(); iter.hasNext();) {
            NTuple<Descriptor> higherTuple = iter.next();
            addFlowGraphEdge(md, higherTuple, interTuple);
          }
          nodeSetArrayAccessExp.clear();
          nodeSetArrayAccessExp.addTuple(interTuple);
          FlowGraph fg = getFlowGraph(md);

          System.out.println("base=" + base);
          if (base != null) {
            fg.addMapInterLocNodeToEnclosingDescriptor(interTuple.get(0),
                getClassTypeDescriptor(base.get(base.size() - 1)));
            interNode.setBaseTuple(base);
          }
        }
      }

      nodeSet.addGlobalFlowTupleSet(idxNodeTupleSet.getGlobalLocTupleSet());
      nodeSet.addTupleSet(nodeSetArrayAccessExp);

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

    System.out.println("analyzeFlowOpNode=" + on.printNode(0));

    // left operand
    analyzeFlowExpressionNode(md, nametable, on.getLeft(), leftOpSet, null, implicitFlowTupleSet,
        false);
    System.out.println("--leftOpSet=" + leftOpSet);

    if (on.getRight() != null) {
      // right operand
      analyzeFlowExpressionNode(md, nametable, on.getRight(), rightOpSet, null,
          implicitFlowTupleSet, false);
    }
    System.out.println("--rightOpSet=" + rightOpSet);

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

      nodeSet.addGlobalFlowTupleSet(leftOpSet.getGlobalLocTupleSet());
      nodeSet.addGlobalFlowTupleSet(rightOpSet.getGlobalLocTupleSet());

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
        base.add(nn.getField());
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
    // System.out.println("analyzeFlowFieldAccessNode=" + fan.printNode(0));

    String currentArrayAccessNodeExpStr = null;
    ExpressionNode left = fan.getExpression();
    TypeDescriptor ltd = left.getType();
    FieldDescriptor fd = fan.getField();
    ArrayAccessNode aan = null;

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

    boolean isArrayCase = false;
    if (left instanceof ArrayAccessNode) {

      isArrayCase = true;
      aan = (ArrayAccessNode) left;

      currentArrayAccessNodeExpStr = aan.printNode(0);
      arrayAccessNodeStack.push(currentArrayAccessNodeExpStr);

      left = aan.getExpression();
      analyzeFlowExpressionNode(md, nametable, aan.getIndex(), idxNodeTupleSet, base,
          implicitFlowTupleSet, isLHS);

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

        GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
        for (Iterator<NTuple<Location>> iterator = idxNodeTupleSet.globalIterator(); iterator
            .hasNext();) {
          NTuple<Location> calleeReturnLocTuple = iterator.next();

          globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple,
              translateToLocTuple(md, flowFieldTuple));
        }

      } else {
        nodeSet.addTupleSet(idxNodeTupleSet);

        // if it is the array case and not the LHS case
        if (isArrayCase) {
          arrayAccessNodeStack.pop();

          if (arrayAccessNodeStack.isEmpty()
              || !arrayAccessNodeStack.peek().startsWith(currentArrayAccessNodeExpStr)) {
            NodeTupleSet nodeSetArrayAccessExp = new NodeTupleSet();

            nodeSetArrayAccessExp.addTuple(flowFieldTuple);
            nodeSetArrayAccessExp.addTupleSet(idxNodeTupleSet);
            nodeSetArrayAccessExp.addTupleSet(nodeSet);

            if (needToGenerateInterLoc(nodeSetArrayAccessExp)) {
              System.out.println("4");
              System.out.println("nodeSetArrayAccessExp=" + nodeSetArrayAccessExp);
              // System.out.println("idxNodeTupleSet.getGlobalLocTupleSet()="
              // + idxNodeTupleSet.getGlobalLocTupleSet());

              NTuple<Descriptor> interTuple =
                  getFlowGraph(md).createIntermediateNode().getDescTuple();

              for (Iterator<NTuple<Descriptor>> iter = nodeSetArrayAccessExp.iterator(); iter
                  .hasNext();) {
                NTuple<Descriptor> higherTuple = iter.next();
                addFlowGraphEdge(md, higherTuple, interTuple);
              }

              FlowGraph fg = getFlowGraph(md);
              fg.addMapInterLocNodeToEnclosingDescriptor(interTuple.get(0),
                  getClassTypeDescriptor(base.get(base.size() - 1)));

              nodeSet.clear();
              flowFieldTuple = interTuple;
            }
            nodeSet.addGlobalFlowTupleSet(idxNodeTupleSet.getGlobalLocTupleSet());
          }

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

      System.out.println("-analyzeFlowAssignmentNode=" + an.printNode(0));
      System.out.println("-nodeSetLHS=" + nodeSetLHS);
      System.out.println("-nodeSetRHS=" + nodeSetRHS);
      System.out.println("-implicitFlowTupleSet=" + implicitFlowTupleSet);
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
      NTuple<Descriptor> interTuple = null;
      if (needToGenerateInterLoc(nodeSetRHS)) {
        System.out.println("2");
        interTuple = getFlowGraph(md).createIntermediateNode().getDescTuple();
      }

      for (Iterator<NTuple<Descriptor>> iter = nodeSetRHS.iterator(); iter.hasNext();) {
        NTuple<Descriptor> fromTuple = iter.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> toTuple = iter2.next();
          addFlowGraphEdge(md, fromTuple, interTuple, toTuple);
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

      // create global flow edges if the callee gives return value flows to the caller
      GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
      for (Iterator<NTuple<Location>> iterator = nodeSetRHS.globalIterator(); iterator.hasNext();) {
        NTuple<Location> calleeReturnLocTuple = iterator.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> callerLHSTuple = iter2.next();
          System.out.println("$$$ GLOBAL FLOW ADD=" + calleeReturnLocTuple + " -> "
              + translateToLocTuple(md, callerLHSTuple));

          globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple,
              translateToLocTuple(md, callerLHSTuple));
        }
      }

      for (Iterator<NTuple<Location>> iterator = implicitFlowTupleSet.globalIterator(); iterator
          .hasNext();) {
        NTuple<Location> calleeReturnLocTuple = iterator.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> callerLHSTuple = iter2.next();

          globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple,
              translateToLocTuple(md, callerLHSTuple));
          System.out.println("$$$ GLOBAL FLOW PCLOC ADD=" + calleeReturnLocTuple + " -> "
              + translateToLocTuple(md, callerLHSTuple));
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

      GlobalFlowGraph globalFlowGraph = getSubGlobalFlowGraph(md);
      for (Iterator<NTuple<Location>> iterator = implicitFlowTupleSet.globalIterator(); iterator
          .hasNext();) {
        NTuple<Location> calleeReturnLocTuple = iterator.next();
        for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
          NTuple<Descriptor> callerLHSTuple = iter2.next();
          globalFlowGraph.addValueFlowEdge(calleeReturnLocTuple,
              translateToLocTuple(md, callerLHSTuple));
          System.out.println("$$$ GLOBAL FLOW PC ADD=" + calleeReturnLocTuple + " -> "
              + translateToLocTuple(md, callerLHSTuple));
        }
      }

    }

    if (nodeSet != null) {
      nodeSet.addTupleSet(nodeSetLHS);
      nodeSet.addGlobalFlowTupleSet(nodeSetLHS.getGlobalLocTupleSet());
    }
  }

  public FlowGraph getFlowGraph(MethodDescriptor md) {
    return mapMethodDescriptorToFlowGraph.get(md);
  }

  private boolean addFlowGraphEdge(MethodDescriptor md, NTuple<Descriptor> from,
      NTuple<Descriptor> to) {
    FlowGraph graph = getFlowGraph(md);
    graph.addValueFlowEdge(from, to);
    return true;
  }

  private void addFlowGraphEdge(MethodDescriptor md, NTuple<Descriptor> from,
      NTuple<Descriptor> inter, NTuple<Descriptor> to) {

    FlowGraph graph = getFlowGraph(md);

    if (inter != null) {
      graph.addValueFlowEdge(from, inter);
      graph.addValueFlowEdge(inter, to);
    } else {
      graph.addValueFlowEdge(from, to);
    }

  }

  public void writeInferredLatticeDotFile(ClassDescriptor cd, HierarchyGraph simpleHierarchyGraph,
      SSJavaLattice<String> locOrder, String nameSuffix) {
    System.out.println("@cd=" + cd);
    System.out.println("@sharedLoc=" + locOrder.getSharedLocSet());
    writeInferredLatticeDotFile(cd, null, simpleHierarchyGraph, locOrder, nameSuffix);
  }

  public void writeInferredLatticeDotFile(ClassDescriptor cd, MethodDescriptor md,
      HierarchyGraph simpleHierarchyGraph, SSJavaLattice<String> locOrder, String nameSuffix) {

    String fileName = "lattice_";
    if (md != null) {
      fileName +=
          cd.getSymbol().replaceAll("[\\W_]", "") + "_" + md.toString().replaceAll("[\\W_]", "");
    } else {
      fileName += cd.getSymbol().replaceAll("[\\W_]", "");
    }

    fileName += nameSuffix;

    System.out.println("***lattice=" + fileName + "    setsize=" + locOrder.getKeySet().size());

    Set<Pair<String, String>> pairSet = locOrder.getOrderingPairSet();

    Set<String> addedLocSet = new HashSet<String>();

    if (pairSet.size() > 0) {
      try {
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName + ".dot"));

        bw.write("digraph " + fileName + " {\n");

        for (Iterator iterator = pairSet.iterator(); iterator.hasNext();) {
          // pair is in the form of <higher, lower>
          Pair<String, String> pair = (Pair<String, String>) iterator.next();

          String highLocId = pair.getFirst();
          String lowLocId = pair.getSecond();
          if (!addedLocSet.contains(highLocId)) {
            addedLocSet.add(highLocId);
            drawNode(bw, locOrder, simpleHierarchyGraph, highLocId);
          }

          if (!addedLocSet.contains(lowLocId)) {
            addedLocSet.add(lowLocId);
            drawNode(bw, locOrder, simpleHierarchyGraph, lowLocId);
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

  private String convertMergeSetToString(HierarchyGraph graph, Set<HNode> mergeSet) {
    String str = "";
    for (Iterator iterator = mergeSet.iterator(); iterator.hasNext();) {
      HNode merged = (HNode) iterator.next();
      if (merged.isMergeNode()) {
        str += convertMergeSetToString(graph, graph.getMapHNodetoMergeSet().get(merged));
      } else {
        str += " " + merged.getName();
      }
    }
    return str;
  }

  private void drawNode(BufferedWriter bw, SSJavaLattice<String> lattice, HierarchyGraph graph,
      String locName) throws IOException {

    String prettyStr;
    if (lattice.isSharedLoc(locName)) {
      prettyStr = locName + "*";
    } else {
      prettyStr = locName;
    }
    // HNode node = graph.getHNode(locName);
    // if (node != null && node.isMergeNode()) {
    // Set<HNode> mergeSet = graph.getMapHNodetoMergeSet().get(node);
    // prettyStr += ":" + convertMergeSetToString(graph, mergeSet);
    // }
    bw.write(locName + " [label=\"" + prettyStr + "\"]" + ";\n");
  }

  public void _debug_writeFlowGraph() {
    Set<MethodDescriptor> keySet = mapMethodDescriptorToFlowGraph.keySet();

    for (Iterator<MethodDescriptor> iterator = keySet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      FlowGraph fg = mapMethodDescriptorToFlowGraph.get(md);
      GlobalFlowGraph subGlobalFlowGraph = getSubGlobalFlowGraph(md);
      try {
        fg.writeGraph();
        subGlobalFlowGraph.writeGraph("_SUBGLOBAL");
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }

}

class CyclicFlowException extends Exception {

}

class InterDescriptor extends Descriptor {

  Pair<MethodInvokeNode, Integer> minArgIdxPair;

  public InterDescriptor(String name) {
    super(name);
  }

  public void setMethodArgIdxPair(MethodInvokeNode min, int idx) {
    minArgIdxPair = new Pair<MethodInvokeNode, Integer>(min, new Integer(idx));
  }

  public Pair<MethodInvokeNode, Integer> getMethodArgIdxPair() {
    return minArgIdxPair;
  }

}
