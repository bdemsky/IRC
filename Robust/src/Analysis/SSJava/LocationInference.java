package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  boolean debug = true;

  public LocationInference(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.toanalyzeList = new ArrayList<ClassDescriptor>();
    this.toanalyzeMethodList = new ArrayList<MethodDescriptor>();
    this.mapMethodDescriptorToFlowGraph = new HashMap<MethodDescriptor, FlowGraph>();
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

    // 2) construct value flow graph

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

  }

  private void analyzeMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    analyzeBlockNode(md, md.getParameterTable(), bn);
  }

  private void analyzeBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {

    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      analyzeBlockStatementNode(md, bn.getVarTable(), bsn);
    }

  }

  private void analyzeBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      analyzeBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
      break;

    case Kind.DeclarationNode:
      analyzeFlowDeclarationNode(md, nametable, (DeclarationNode) bsn);
      break;

    case Kind.IfStatementNode:
      analyzeIfStatementNode(md, nametable, (IfStatementNode) bsn);
      break;

    case Kind.LoopNode:
      analyzeLoopNode(md, nametable, (LoopNode) bsn);
      break;

    case Kind.ReturnNode:
      analyzeReturnNode(md, nametable, (ReturnNode) bsn);
      break;

    case Kind.SubBlockNode:
      analyzeSubBlockNode(md, nametable, (SubBlockNode) bsn);
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

  private void analyzeSubBlockNode(MethodDescriptor md, SymbolTable nametable, SubBlockNode bsn) {
    // TODO Auto-generated method stub

  }

  private void analyzeReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode bsn) {
    // TODO Auto-generated method stub

  }

  private void analyzeLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode bsn) {
    // TODO Auto-generated method stub

  }

  private void analyzeIfStatementNode(MethodDescriptor md, SymbolTable nametable,
      IfStatementNode bsn) {
    // TODO Auto-generated method stub

  }

  private void analyzeFlowDeclarationNode(MethodDescriptor md, SymbolTable nametable,
      DeclarationNode dn) {

    VarDescriptor vd = dn.getVarDescriptor();
    NTuple<Descriptor> tupleLHS = new NTuple<Descriptor>();
    tupleLHS.add(vd);
    getFlowGraph(md).createNewFlowNode(tupleLHS);

    if (dn.getExpression() != null) {

      NodeTupleSet tupleSetRHS = new NodeTupleSet();
      analyzeFlowExpressionNode(md, nametable, dn.getExpression(), tupleSetRHS,
          new NTuple<Descriptor>());

      // add a new flow edge from rhs to lhs
      for (Iterator<NTuple<Descriptor>> iter = tupleSetRHS.iterator(); iter.hasNext();) {
        NTuple<Descriptor> from = iter.next();
        addFlowGraphEdge(md, from, tupleLHS);
      }

    }

  }

  private void analyzeBlockExpressionNode(MethodDescriptor md, SymbolTable nametable,
      BlockExpressionNode ben) {
    analyzeFlowExpressionNode(md, nametable, ben.getExpression(), null, null);
  }

  private NTuple<Descriptor> analyzeFlowExpressionNode(MethodDescriptor md, SymbolTable nametable,
      ExpressionNode en, NodeTupleSet nodeSet, NTuple<Descriptor> base) {

    // note that expression node can create more than one flow node
    // nodeSet contains of flow nodes

    NTuple<Descriptor> flowTuple;

    switch (en.kind()) {

    case Kind.AssignmentNode:
      analyzeFlowAssignmentNode(md, nametable, (AssignmentNode) en, base);
      break;

    case Kind.FieldAccessNode:
      flowTuple = analyzeFlowFieldAccessNode(md, nametable, (FieldAccessNode) en, nodeSet, base);
      nodeSet.addTuple(flowTuple);
      return flowTuple;

    case Kind.NameNode:
      NodeTupleSet nameNodeSet = new NodeTupleSet();
      flowTuple = analyzeFlowNameNode(md, nametable, (NameNode) en, nameNodeSet, base);
      nodeSet.addTuple(flowTuple);
      return flowTuple;

    case Kind.OpNode:
      // return analyzeOpNode(md, nametable, (OpNode) en, new
      // HashSet<FlowNode>());
      break;

    case Kind.CreateObjectNode:
      analyzeCreateObjectNode(md, nametable, (CreateObjectNode) en);
      break;

    case Kind.ArrayAccessNode:
      analyzeArrayAccessNode(md, nametable, (ArrayAccessNode) en);
      break;

    case Kind.LiteralNode:
      analyzeLiteralNode(md, nametable, (LiteralNode) en);
      break;

    case Kind.MethodInvokeNode:
      analyzeMethodInvokeNode(md, nametable, (MethodInvokeNode) en);
      break;

    case Kind.TertiaryNode:
      analyzeTertiaryNode(md, nametable, (TertiaryNode) en);
      break;

    case Kind.CastNode:
      analyzeCastNode(md, nametable, (CastNode) en);
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

  private void analyzeCastNode(MethodDescriptor md, SymbolTable nametable, CastNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeTertiaryNode(MethodDescriptor md, SymbolTable nametable, TertiaryNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeMethodInvokeNode(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeLiteralNode(MethodDescriptor md, SymbolTable nametable, LiteralNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeArrayAccessNode(MethodDescriptor md, SymbolTable nametable, ArrayAccessNode en) {
    // TODO Auto-generated method stub

  }

  private void analyzeCreateObjectNode(MethodDescriptor md, SymbolTable nametable,
      CreateObjectNode en) {
    // TODO Auto-generated method stub

  }

  private Set<FlowNode> analyzeOpNode(MethodDescriptor md, SymbolTable nametable, OpNode on,
      Set<FlowNode> nodeSet) {

    ClassDescriptor cd = md.getClassDesc();

    // left operand
    // NTuple<Descriptor> leftOpTuple =
    // analyzeFlowExpressionNode(md, nametable, on.getLeft(), new
    // NTuple<Descriptor>());

    if (on.getRight() != null) {
      // right operand
      // NTuple<Descriptor> rightOpTuple =
      // analyzeFlowExpressionNode(md, nametable, on.getRight(), new
      // NTuple<Descriptor>());
    }

    Operation op = on.getOp();

    switch (op.getOp()) {

    case Operation.UNARYPLUS:
    case Operation.UNARYMINUS:
    case Operation.LOGIC_NOT:
      // single operand
      // return leftLoc;

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

      Set<CompositeLocation> inputSet = new HashSet<CompositeLocation>();
      // inputSet.add(leftLoc);
      // inputSet.add(rightLoc);
      // CompositeLocation glbCompLoc =
      // CompositeLattice.calculateGLB(inputSet, generateErrorMessage(cd, on));
      // return glbCompLoc;

    default:
      throw new Error(op.toString());
    }
  }

  private NTuple<Descriptor> analyzeFlowNameNode(MethodDescriptor md, SymbolTable nametable,
      NameNode nn, NodeTupleSet nodeSet, NTuple<Descriptor> base) {

    if (base == null) {
      base = new NTuple<Descriptor>();
    }

    NameDescriptor nd = nn.getName();
    if (nd.getBase() != null) {
      analyzeFlowExpressionNode(md, nametable, nn.getExpression(), nodeSet, base);
    } else {
      String varname = nd.toString();
      if (varname.equals("this")) {
        // 'this' itself!
        base.add(md.getThis());
        return base;
      }

      Descriptor d = (Descriptor) nametable.get(varname);

      // CompositeLocation localLoc = null;
      if (d instanceof VarDescriptor) {
        VarDescriptor vd = (VarDescriptor) d;
        // localLoc = d2loc.get(vd);
        // the type of var descriptor has a composite location!
        // loc = ((SSJavaType)
        // vd.getType().getExtension()).getCompLoc().clone();
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
      FieldAccessNode fan, NodeTupleSet nodeSet, NTuple<Descriptor> base) {

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
    base = analyzeFlowExpressionNode(md, nametable, left, nodeSet, base);

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
      AssignmentNode an, NTuple<Descriptor> base) {

    System.out.println("analyzeFlowAssignmentNode=" + an);

    ClassDescriptor cd = md.getClassDesc();

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
    analyzeFlowExpressionNode(md, nametable, an.getDest(), nodeSetLHS, base);
    System.out.println("ASSIGNMENT NODE nodeSetLHS=" + nodeSetLHS);
    // NTuple<Descriptor> lhsDescTuple = analyzeFlowExpressionNode(md,
    // nametable, an.getDest(), base);

    if (!postinc) {
      // analyze value flows of rhs expression
      analyzeFlowExpressionNode(md, nametable, an.getSrc(), nodeSetRHS, null);
      System.out.println("ASSIGNMENT NODE nodeSetRHS=" + nodeSetRHS);

    } else {

      // postinc case
      // src & dest are same

    }

    // creates edges from RHS to LHS
    for (Iterator<NTuple<Descriptor>> iter = nodeSetRHS.iterator(); iter.hasNext();) {
      NTuple<Descriptor> fromTuple = iter.next();
      for (Iterator<NTuple<Descriptor>> iter2 = nodeSetLHS.iterator(); iter2.hasNext();) {
        NTuple<Descriptor> toTuple = iter2.next();
        addFlowGraphEdge(md, fromTuple, toTuple);
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

}
