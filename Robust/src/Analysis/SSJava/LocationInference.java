package Analysis.SSJava;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import Analysis.SSJava.FlowDownCheck.ComparisonResult;
import Analysis.SSJava.FlowDownCheck.CompositeLattice;
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
import IR.Flat.FlatMethod;
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

  // keep current descriptors to visit in fixed-point interprocedural analysis,
  private Stack<MethodDescriptor> methodDescriptorsToVisitStack;

  // map a class descriptor to a field lattice
  private Map<ClassDescriptor, SSJavaLattice<String>> cd2lattice;

  // map a method descriptor to a method lattice
  private Map<MethodDescriptor, SSJavaLattice<String>> md2lattice;

  // map a method descriptor to a lattice mapping
  private Map<MethodDescriptor, Map<VarDescriptor, String>> md2LatticeMapping;

  // map a method descriptor to a lattice mapping
  private Map<MethodDescriptor, Map<FieldDescriptor, String>> cd2LatticeMapping;

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
    this.md2LatticeMapping = new HashMap<MethodDescriptor, Map<VarDescriptor, String>>();
    this.cd2LatticeMapping = new HashMap<MethodDescriptor, Map<FieldDescriptor, String>>();
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

  }

  private void debug_writeLatticeDotFile() {
    // generate lattice dot file

    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);

      SSJavaLattice<String> classLattice = cd2lattice.get(cd);
      if (classLattice != null) {
        ssjava.writeLatticeDotFile(cd, classLattice);
      }

      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        if (ssjava.needTobeAnnotated(md)) {
          SSJavaLattice<String> methodLattice = md2lattice.get(md);
          if (methodLattice != null) {
            ssjava.writeLatticeDotFile(cd, methodLattice);
          }
        }
      }
    }

  }

  private void inferLattices() {

    // do fixed-point analysis

    // perform method READ/OVERWRITE analysis
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
      FlatMethod fm = state.getMethodFlat(md);

      SSJavaLattice<String> methodLattice =
          new SSJavaLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM);

      analyzeMethodLattice(md, methodLattice);

      SSJavaLattice<String> prevMethodLattice = md2lattice.get(md);

      if (!methodLattice.equals(prevMethodLattice)) {
        md2lattice.put(md, methodLattice);

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

  private String getSymbol(int idx, FlowNode node) {
    Descriptor desc = node.getDescTuple().get(idx);
    return desc.getSymbol();
  }

  private void addMappingDescriptorToLocationIdentifer(MethodDescriptor methodDesc,
      VarDescriptor varDesc, String identifier) {
    if (!md2LatticeMapping.containsKey(methodDesc)) {
      md2LatticeMapping.put(methodDesc, new HashMap<VarDescriptor, String>());
    }

  }

  private void analyzeMethodLattice(MethodDescriptor md, SSJavaLattice<String> methodLattice) {

    System.out.println("# Create the method lattice for " + md);

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

        if ((srcNode.getDescTuple().size() > 1 && dstNode.getDescTuple().size() > 1)
            && srcNode.getDescTuple().get(0).equals(dstNode.getDescTuple().get(0))) {
          // value flow between fields: we don't need to add a binary relation
          // for this case

          VarDescriptor varDesc = (VarDescriptor) srcNode.getDescTuple().get(0);
          ClassDescriptor varClassDesc = varDesc.getType().getClassDesc();

          extractRelationFromFieldFlows(varClassDesc, srcNode, dstNode, 1);
          continue;
        }

        // add a new binary relation of dstNode < srcNode

        String srcSymbol = getSymbol(0, srcNode);
        String dstSymbol = getSymbol(0, dstNode);

        methodLattice.addRelationHigherToLower(srcSymbol, dstSymbol);

      }

    }

  }

  private void extractRelationFromFieldFlows(ClassDescriptor cd, FlowNode srcNode,
      FlowNode dstNode, int idx) {

    if (srcNode.getDescTuple().get(idx).equals(dstNode.getDescTuple().get(idx))) {
      // value flow between fields: we don't need to add a binary relation
      // for this case
      VarDescriptor varDesc = (VarDescriptor) srcNode.getDescTuple().get(idx);
      ClassDescriptor varClassDesc = varDesc.getType().getClassDesc();
      extractRelationFromFieldFlows(varClassDesc, srcNode, dstNode, idx + 1);
    } else {

      Descriptor srcFieldDesc = srcNode.getDescTuple().get(idx);
      Descriptor dstFieldDesc = dstNode.getDescTuple().get(idx);

      // add a new binary relation of dstNode < srcNode

      SSJavaLattice<String> fieldLattice = getFieldLattice(cd);
      fieldLattice.addRelationHigherToLower(srcFieldDesc.getSymbol(), dstFieldDesc.getSymbol());

    }

  }

  public SSJavaLattice<String> getFieldLattice(ClassDescriptor cd) {
    if (!cd2lattice.containsKey(cd)) {
      cd2lattice.put(cd, new SSJavaLattice<String>(SSJavaLattice.TOP, SSJavaLattice.BOTTOM));
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
            System.out.println("SSJAVA: Constructing a flow graph: " + md);
          }
          FlowGraph fg = new FlowGraph(md);
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
      analyzeReturnNode(md, nametable, (ReturnNode) bsn);
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

  private void analyzeReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode bsn) {
    // TODO Auto-generated method stub

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
    // base is always assigned to null except name node case!

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

    System.out.println("### analyzeFlowTertiaryNode=" + tn.printNode(0));

    NodeTupleSet tertiaryTupleNode = new NodeTupleSet();
    analyzeFlowExpressionNode(md, nametable, tn.getCond(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    // add edges from tertiaryTupleNode to all nodes of conditional nodes
    tertiaryTupleNode.addTupleSet(implicitFlowTupleSet);
    System.out.println("### TertiarayNode's condition=" + tertiaryTupleNode);
    analyzeFlowExpressionNode(md, nametable, tn.getTrueExpr(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    analyzeFlowExpressionNode(md, nametable, tn.getFalseExpr(), tertiaryTupleNode, null,
        implicitFlowTupleSet, false);

    nodeSet.addTupleSet(tertiaryTupleNode);

  }

  private void analyzeFlowMethodInvokeNode(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min, NodeTupleSet implicitFlowTupleSet) {

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
        System.out.println("Analyzing base of method=" + min.getExpression());
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
    System.out.println("Analyzing left op=" + on.getLeft().printNode(0) + "::"
        + on.getLeft().getClass());
    analyzeFlowExpressionNode(md, nametable, on.getLeft(), leftOpSet, null, implicitFlowTupleSet,
        false);
    System.out.println("leftOpSet=" + leftOpSet);

    if (on.getRight() != null) {
      // right operand
      analyzeFlowExpressionNode(md, nametable, on.getRight(), rightOpSet, null,
          implicitFlowTupleSet, false);
      System.out.println("rightOpSet=" + rightOpSet);
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

    return base;

  }

  private void analyzeFlowAssignmentNode(MethodDescriptor md, SymbolTable nametable,
      AssignmentNode an, NTuple<Descriptor> base, NodeTupleSet implicitFlowTupleSet) {

    System.out.println("analyzeFlowAssignmentNode=" + an);

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
    System.out.println("ASSIGNMENT NODE nodeSetLHS=" + nodeSetLHS);

    if (!postinc) {
      // analyze value flows of rhs expression
      analyzeFlowExpressionNode(md, nametable, an.getSrc(), nodeSetRHS, null, implicitFlowTupleSet,
          false);
      System.out.println("ASSIGNMENT NODE nodeSetRHS=" + nodeSetRHS);

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

  public void addFlowGraphEdge(MethodDescriptor md, NTuple<Descriptor> from, NTuple<Descriptor> to) {
    FlowGraph graph = getFlowGraph(md);
    graph.addValueFlowEdge(from, to);
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
