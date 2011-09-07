package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

import Analysis.SSJava.FlowDownCheck.ComparisonResult;
import Analysis.SSJava.FlowDownCheck.CompositeLattice;
import IR.AnnotationDescriptor;
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
import IR.Tree.SynchronizedNode;
import IR.Tree.TertiaryNode;
import IR.Tree.TreeNode;
import Util.Pair;

public class SSJavaInferenceEngine {

  State state;
  static SSJavaAnalysis ssjava;

  Set<ClassDescriptor> toanalyze;
  List<ClassDescriptor> toanalyzeList;

  Set<MethodDescriptor> toanalyzeMethod;
  List<MethodDescriptor> toanalyzeMethodList;

  // mapping from 'descriptor' to 'composite location'
  Hashtable<Descriptor, CompositeLocation> d2loc;

  Hashtable<MethodDescriptor, CompositeLocation> md2ReturnLoc;
  Hashtable<MethodDescriptor, ReturnLocGenerator> md2ReturnLocGen;

  // mapping from 'locID' to 'class descriptor'
  Hashtable<String, ClassDescriptor> fieldLocName2cd;

    private Set<ImplicitTuple> implicitFlowSet;  /*should maybe be hashtable<ExpressionNode,Set<VarID>>*/
  private RelationSet rSet;

  boolean deterministic = true;

  public SSJavaInferenceEngine(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    if (deterministic) {
      this.toanalyzeList = new ArrayList<ClassDescriptor>();
    } else {
      this.toanalyze = new HashSet<ClassDescriptor>();
    }
    if (deterministic) {
      this.toanalyzeMethodList = new ArrayList<MethodDescriptor>();
    } else {
      this.toanalyzeMethod = new HashSet<MethodDescriptor>();
    }
    this.d2loc = new Hashtable<Descriptor, CompositeLocation>();
    this.fieldLocName2cd = new Hashtable<String, ClassDescriptor>();
    this.md2ReturnLoc = new Hashtable<MethodDescriptor, CompositeLocation>();
    this.md2ReturnLocGen = new Hashtable<MethodDescriptor, ReturnLocGenerator>();
    this.implicitFlowSet = new HashSet<ImplicitTuple>();
    this.rSet = new RelationSet();
  }

  public void init() {

    // construct mapping from the location name to the class descriptor
    // assume that the location name is unique through the whole program

    Set<ClassDescriptor> cdSet = ssjava.getCd2lattice().keySet();
    for (Iterator iterator = cdSet.iterator(); iterator.hasNext();) {
      ClassDescriptor cd = (ClassDescriptor) iterator.next();
      SSJavaLattice<String> lattice = ssjava.getCd2lattice().get(cd);
      Set<String> fieldLocNameSet = lattice.getKeySet();

      for (Iterator iterator2 = fieldLocNameSet.iterator(); iterator2.hasNext();) {
        String fieldLocName = (String) iterator2.next();
        fieldLocName2cd.put(fieldLocName, cd);
      }

    }

  }

  public boolean toAnalyzeIsEmpty() {
    if (deterministic) {
      return toanalyzeList.isEmpty();
    } else {
      return toanalyze.isEmpty();
    }
  }

  public ClassDescriptor toAnalyzeNext() {
    if (deterministic) {
      return toanalyzeList.remove(0);
    } else {
      ClassDescriptor cd = toanalyze.iterator().next();
      toanalyze.remove(cd);
      return cd;
    }
  }

  public void setupToAnalyze() {
    SymbolTable classtable = state.getClassSymbolTable();
    if (deterministic) {
      toanalyzeList.clear();
      toanalyzeList.addAll(classtable.getValueSet());
      Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
        public int compare(ClassDescriptor o1, ClassDescriptor o2) {
          return o1.getClassName().compareTo(o2.getClassName());
        }
      });
    } else {
      toanalyze.clear();
      toanalyze.addAll(classtable.getValueSet());
    }
  }

  public void setupToAnalazeMethod(ClassDescriptor cd) {

    SymbolTable methodtable = cd.getMethodTable();
    if (deterministic) {
      toanalyzeMethodList.clear();
      toanalyzeMethodList.addAll(methodtable.getValueSet());
      Collections.sort(toanalyzeMethodList, new Comparator<MethodDescriptor>() {
        public int compare(MethodDescriptor o1, MethodDescriptor o2) {
          return o1.getSymbol().compareTo(o2.getSymbol());
        }
      });
    } else {
      toanalyzeMethod.clear();
      toanalyzeMethod.addAll(methodtable.getValueSet());
    }
  }

  public boolean toAnalyzeMethodIsEmpty() {
    if (deterministic) {
      return toanalyzeMethodList.isEmpty();
    } else {
      return toanalyzeMethod.isEmpty();
    }
  }

  public MethodDescriptor toAnalyzeMethodNext() {
    if (deterministic) {
      return toanalyzeMethodList.remove(0);
    } else {
      MethodDescriptor md = toanalyzeMethod.iterator().next();
      toanalyzeMethod.remove(md);
      return md;
    }
  }

  public void inference() {
    FileWriter latticeFile;
    PrintWriter latticeOut;
    setupToAnalyze();
    
    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();
      try{
      latticeFile = new FileWriter(cd.getClassName()+".lat");
      } catch(IOException e){
	  System.out.println("File Fail");
	  return;
      }
      latticeOut = new PrintWriter(latticeFile);
      if (ssjava.needToBeAnnoated(cd)) {
        setupToAnalazeMethod(cd);
        while (!toAnalyzeMethodIsEmpty()) {
          MethodDescriptor md = toAnalyzeMethodNext();
          if (ssjava.needTobeAnnotated(md)) {
	    inferRelationsFromBlockNode(md, md.getParameterTable(), state.getMethodBody(md));
	    latticeOut.println(md.getClassMethodName() + "\n");
	    latticeOut.println(rSet.toString());
	    rSet = new RelationSet();
          }
        }
      }
      latticeOut.flush();
      latticeOut.close();
    }
  }

  private void inferRelationsFromBlockNode(MethodDescriptor md, SymbolTable nametable,
      BlockNode bn) {

    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      inferRelationsFromBlockStatementNode(md, bn.getVarTable(), bsn);
    }

  }

  private void inferRelationsFromBlockStatementNode(MethodDescriptor md,
      SymbolTable nametable, BlockStatementNode bsn) {

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      inferRelationsFromBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
      break;

    case Kind.DeclarationNode:
      inferRelationsFromDeclarationNode(md, nametable, (DeclarationNode) bsn);
      break;
      
    case Kind.IfStatementNode:
      inferRelationsFromIfStatementNode(md, nametable, (IfStatementNode) bsn);
      break;
      /*
    case Kind.LoopNode:
      inferRelationsFromLoopNode(md, nametable, (LoopNode) bsn, constraint);
      break;

    case Kind.ReturnNode:
      inferRelationsFromReturnNode(md, nametable, (ReturnNode) bsn, constraint);
      break;
      */
    case Kind.SubBlockNode:
      inferRelationsFromSubBlockNode(md, nametable, (SubBlockNode) bsn);
      break;
      /*
    case Kind.ContinueBreakNode:
      compLoc = new CompositeLocation();
      break;

    case Kind.SwitchStatementNode:
      inferRelationsFromSwitchStatementNode(md, nametable, (SwitchStatementNode) bsn, constraint);
      break;
*/
    default:
	System.out.println(bsn.kind() + " not handled...");
      break;
    }
  }

    /*private CompositeLocation inferRelationsFromSwitchStatementNode(MethodDescriptor md,
      SymbolTable nametable, SwitchStatementNode ssn, CompositeLocation constraint) {

    ClassDescriptor cd = md.getClassDesc();
    CompositeLocation condLoc =
        inferRelationsFromExpressionNode(md, nametable, ssn.getCondition(), new CompositeLocation(),
            constraint, false);
    BlockNode sbn = ssn.getSwitchBody();

    constraint = generateNewConstraint(constraint, condLoc);

    for (int i = 0; i < sbn.size(); i++) {
      inferRelationsFromSwitchBlockNode(md, nametable, (SwitchBlockNode) sbn.get(i), constraint);
    }
    return new CompositeLocation();
  }

  private CompositeLocation inferRelationsFromSwitchBlockNode(MethodDescriptor md,
      SymbolTable nametable, SwitchBlockNode sbn, CompositeLocation constraint) {

    CompositeLocation blockLoc =
        inferRelationsFromBlockNode(md, nametable, sbn.getSwitchBlockStatement(), constraint);

    return blockLoc;

  }

  private CompositeLocation inferRelationsFromReturnNode(MethodDescriptor md, SymbolTable nametable,
      ReturnNode rn, CompositeLocation constraint) {

    ExpressionNode returnExp = rn.getReturnExpression();

    CompositeLocation returnValueLoc;
    if (returnExp != null) {
      returnValueLoc =
          inferRelationsFromExpressionNode(md, nametable, returnExp, new CompositeLocation(),
              constraint, false);

      // if this return statement is inside branch, return value has an implicit
      // flow from conditional location
      if (constraint != null) {
        Set<CompositeLocation> inputGLB = new HashSet<CompositeLocation>();
        inputGLB.add(returnValueLoc);
        inputGLB.add(constraint);
        returnValueLoc =
            CompositeLattice.calculateGLB(inputGLB, generateErrorMessage(md.getClassDesc(), rn));
      }

      // check if return value is equal or higher than RETRUNLOC of method
      // declaration annotation
      CompositeLocation declaredReturnLoc = md2ReturnLoc.get(md);

      int compareResult =
          CompositeLattice.compare(returnValueLoc, declaredReturnLoc, false,
              generateErrorMessage(md.getClassDesc(), rn));

      if (compareResult == ComparisonResult.LESS || compareResult == ComparisonResult.INCOMPARABLE) {
        throw new Error(
            "Return value location is not equal or higher than the declaraed return location at "
                + md.getClassDesc().getSourceFileName() + "::" + rn.getNumLine());
      }
    }

    return new CompositeLocation();
  }

  private boolean hasOnlyLiteralValue(ExpressionNode en) {
    if (en.kind() == Kind.LiteralNode) {
      return true;
    } else {
      return false;
    }
  }

  private CompositeLocation inferRelationsFromLoopNode(MethodDescriptor md, SymbolTable nametable,
      LoopNode ln, CompositeLocation constraint) {

    ClassDescriptor cd = md.getClassDesc();
    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {

      CompositeLocation condLoc =
          inferRelationsFromExpressionNode(md, nametable, ln.getCondition(),
              new CompositeLocation(), constraint, false);
      // addLocationType(ln.getCondition().getType(), (condLoc));

      constraint = generateNewConstraint(constraint, condLoc);
      inferRelationsFromBlockNode(md, nametable, ln.getBody(), constraint);

      return new CompositeLocation();

    } else {
      // check 'for loop' case
      BlockNode bn = ln.getInitializer();
      bn.getVarTable().setParent(nametable);

      // calculate glb location of condition and update statements
      CompositeLocation condLoc =
          inferRelationsFromExpressionNode(md, bn.getVarTable(), ln.getCondition(),
              new CompositeLocation(), constraint, false);
      // addLocationType(ln.getCondition().getType(), condLoc);

      constraint = generateNewConstraint(constraint, condLoc);

      inferRelationsFromBlockNode(md, bn.getVarTable(), ln.getUpdate(), constraint);
      inferRelationsFromBlockNode(md, bn.getVarTable(), ln.getBody(), constraint);

      return new CompositeLocation();

    }

  }
    */
  private void inferRelationsFromSubBlockNode(MethodDescriptor md,
      SymbolTable nametable, SubBlockNode sbn) {
     inferRelationsFromBlockNode(md, nametable, sbn.getBlockNode());
  }

  
  private void inferRelationsFromIfStatementNode(MethodDescriptor md,
      SymbolTable nametable, IfStatementNode isn) {

      inferRelationsFromExpressionNode(md, nametable, isn.getCondition(), null, isn, false);

    inferRelationsFromBlockNode(md, nametable, isn.getTrueBlock());

    if (isn.getFalseBlock() != null) {
      inferRelationsFromBlockNode(md, nametable, isn.getFalseBlock());
    }

    for(ImplicitTuple tuple: implicitFlowSet){
      if(tuple.isFromBranch(isn)){
        implicitFlowSet.remove(tuple);
      }
    }
  }

  private void inferRelationsFromDeclarationNode(MethodDescriptor md,
      SymbolTable nametable, DeclarationNode dn) {
  }

    /*private void inferRelationsFromSubBlockNode(MethodDescriptor md, SymbolTable nametable,
      SubBlockNode sbn) {
    inferRelationsFromBlockNode(md, nametable.getParent(), sbn.getBlockNode());
    }*/

  private void inferRelationsFromBlockExpressionNode(MethodDescriptor md,
      SymbolTable nametable, BlockExpressionNode ben) {
      inferRelationsFromExpressionNode(md, nametable, ben.getExpression(), null, null, false);
    // addTypeLocation(ben.getExpression().getType(), compLoc);
  }

  private VarID inferRelationsFromExpressionNode(MethodDescriptor md,
     SymbolTable nametable, ExpressionNode en, VarID flowTo, BlockStatementNode implicitTag, boolean isLHS) {

    VarID var = null;
    switch (en.kind()) {

    case Kind.AssignmentNode:
      var =
          inferRelationsFromAssignmentNode(md, nametable, (AssignmentNode) en, flowTo, implicitTag);
      break;

      //   case Kind.FieldAccessNode:
      // var =
      //    inferRelationsFromFieldAccessNode(md, nametable, (FieldAccessNode) en, flowTo);
      // break;

    case Kind.NameNode:
	var = inferRelationsFromNameNode(md, nametable, (NameNode) en, flowTo, implicitTag);
      break;

      /* case Kind.OpNode:
	var = inferRelationsFromOpNode(md, nametable, (OpNode) en, flowTo);
      break;

    case Kind.CreateObjectNode:
      var = inferRelationsFromCreateObjectNode(md, nametable, (CreateObjectNode) en);
      break;

    case Kind.ArrayAccessNode:
      var =
          inferRelationsFromArrayAccessNode(md, nametable, (ArrayAccessNode) en, flowTo, isLHS);
      break;
      */
    case Kind.LiteralNode:
	var = inferRelationsFromLiteralNode(md, nametable, (LiteralNode) en);
      break;
      /*
     case Kind.MethodInvokeNode:
      var =
          inferRelationsFromMethodInvokeNode(md, nametable, (MethodInvokeNode) en, flowTo);
      break;

    case Kind.TertiaryNode:
      var = inferRelationsFromTertiaryNode(md, nametable, (TertiaryNode) en);
      break;

    case Kind.CastNode:
      var = inferRelationsFromCastNode(md, nametable, (CastNode) en);
      break;
      */
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

    default:
	System.out.println("expressionnode not handled...");
      return null;

    }
    // addTypeLocation(en.getType(), compLoc);
    return var;

  }
    /*
 private CompositeLocation inferRelationsFromCastNode(MethodDescriptor md, SymbolTable nametable,
      CastNode cn, CompositeLocation constraint) {

    ExpressionNode en = cn.getExpression();
    return inferRelationsFromExpressionNode(md, nametable, en, new CompositeLocation(), constraint,
        false);

  }

  private CompositeLocation inferRelationsFromTertiaryNode(MethodDescriptor md,
      SymbolTable nametable, TertiaryNode tn, CompositeLocation constraint) {
    ClassDescriptor cd = md.getClassDesc();

    CompositeLocation condLoc =
        inferRelationsFromExpressionNode(md, nametable, tn.getCond(), new CompositeLocation(),
            constraint, false);
    // addLocationType(tn.getCond().getType(), condLoc);
    CompositeLocation trueLoc =
        inferRelationsFromExpressionNode(md, nametable, tn.getTrueExpr(), new CompositeLocation(),
            constraint, false);
    // addLocationType(tn.getTrueExpr().getType(), trueLoc);
    CompositeLocation falseLoc =
        inferRelationsFromExpressionNode(md, nametable, tn.getFalseExpr(), new CompositeLocation(),
            constraint, false);
    // addLocationType(tn.getFalseExpr().getType(), falseLoc);

    // locations from true/false branches can be TOP when there are only literal
    // values
    // in this case, we don't need to check flow down rule!

    // check if condLoc is higher than trueLoc & falseLoc
    if (!trueLoc.get(0).isTop()
        && !CompositeLattice.isGreaterThan(condLoc, trueLoc, generateErrorMessage(cd, tn))) {
      throw new Error(
          "The location of the condition expression is lower than the true expression at "
              + cd.getSourceFileName() + ":" + tn.getCond().getNumLine());
    }

    if (!falseLoc.get(0).isTop()
        && !CompositeLattice.isGreaterThan(condLoc, falseLoc,
            generateErrorMessage(cd, tn.getCond()))) {
      throw new Error(
          "The location of the condition expression is lower than the true expression at "
              + cd.getSourceFileName() + ":" + tn.getCond().getNumLine());
    }

    // then, return glb of trueLoc & falseLoc
    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();
    glbInputSet.add(trueLoc);
    glbInputSet.add(falseLoc);

    return CompositeLattice.calculateGLB(glbInputSet, generateErrorMessage(cd, tn));
  }

  private CompositeLocation inferRelationsFromMethodInvokeNode(MethodDescriptor md,
      SymbolTable nametable, MethodInvokeNode min, CompositeLocation loc,
      CompositeLocation constraint) {

    CompositeLocation baseLocation = null;
    if (min.getExpression() != null) {
      baseLocation =
          inferRelationsFromExpressionNode(md, nametable, min.getExpression(),
              new CompositeLocation(), constraint, false);
    } else {

      if (min.getMethod().isStatic()) {
        String globalLocId = ssjava.getMethodLattice(md).getGlobalLoc();
        if (globalLocId == null) {
          throw new Error("Method lattice does not define global variable location at "
              + generateErrorMessage(md.getClassDesc(), min));
        }
        baseLocation = new CompositeLocation(new Location(md, globalLocId));
      } else {
        String thisLocId = ssjava.getMethodLattice(md).getThisLoc();
        baseLocation = new CompositeLocation(new Location(md, thisLocId));
      }
    }

    checkCalleeConstraints(md, nametable, min, baseLocation, constraint);

    checkCallerArgumentLocationConstraints(md, nametable, min, baseLocation, constraint);

    if (!min.getMethod().getReturnType().isVoid()) {
      // If method has a return value, compute the highest possible return
      // location in the caller's perspective
      CompositeLocation ceilingLoc =
          computeCeilingLocationForCaller(md, nametable, min, baseLocation, constraint);
      return ceilingLoc;
    }

    return new CompositeLocation();

  }

  private void checkCallerArgumentLocationConstraints(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min, CompositeLocation callerBaseLoc, CompositeLocation constraint) {
    // if parameter location consists of THIS and FIELD location,
    // caller should pass an argument that is comparable to the declared
    // parameter location
    // and is not lower than the declared parameter location in the field
    // lattice.

    MethodDescriptor calleemd = min.getMethod();

    List<CompositeLocation> callerArgList = new ArrayList<CompositeLocation>();
    List<CompositeLocation> calleeParamList = new ArrayList<CompositeLocation>();

    MethodLattice<String> calleeLattice = ssjava.getMethodLattice(calleemd);
    Location calleeThisLoc = new Location(calleemd, calleeLattice.getThisLoc());

    for (int i = 0; i < min.numArgs(); i++) {
      ExpressionNode en = min.getArg(i);
      CompositeLocation callerArgLoc =
          inferRelationsFromExpressionNode(md, nametable, en, new CompositeLocation(), constraint,
              false);
      callerArgList.add(callerArgLoc);
    }

    // setup callee params set
    for (int i = 0; i < calleemd.numParameters(); i++) {
      VarDescriptor calleevd = (VarDescriptor) calleemd.getParameter(i);
      CompositeLocation calleeLoc = d2loc.get(calleevd);
      calleeParamList.add(calleeLoc);
    }

    String errorMsg = generateErrorMessage(md.getClassDesc(), min);

    System.out.println("checkCallerArgumentLocationConstraints=" + min.printNode(0));
    System.out.println("base location=" + callerBaseLoc);

    for (int i = 0; i < calleeParamList.size(); i++) {
      CompositeLocation calleeParamLoc = calleeParamList.get(i);
      if (calleeParamLoc.get(0).equals(calleeThisLoc) && calleeParamLoc.getSize() > 1) {

        // callee parameter location has field information
        CompositeLocation callerArgLoc = callerArgList.get(i);

        CompositeLocation paramLocation =
            translateCalleeParamLocToCaller(md, calleeParamLoc, callerBaseLoc, errorMsg);

        Set<CompositeLocation> inputGLBSet = new HashSet<CompositeLocation>();
        if (constraint != null) {
          inputGLBSet.add(callerArgLoc);
          inputGLBSet.add(constraint);
          callerArgLoc =
              CompositeLattice.calculateGLB(inputGLBSet,
                  generateErrorMessage(md.getClassDesc(), min));
        }

        if (!CompositeLattice.isGreaterThan(callerArgLoc, paramLocation, errorMsg)) {
          throw new Error("Caller argument '" + min.getArg(i).printNode(0) + " : " + callerArgLoc
              + "' should be higher than corresponding callee's parameter : " + paramLocation
              + " at " + errorMsg);
        }

      }
    }

  }

  private CompositeLocation translateCalleeParamLocToCaller(MethodDescriptor md,
      CompositeLocation calleeParamLoc, CompositeLocation callerBaseLocation, String errorMsg) {

    CompositeLocation translate = new CompositeLocation();

    for (int i = 0; i < callerBaseLocation.getSize(); i++) {
      translate.addLocation(callerBaseLocation.get(i));
    }

    for (int i = 1; i < calleeParamLoc.getSize(); i++) {
      translate.addLocation(calleeParamLoc.get(i));
    }

    System.out.println("TRANSLATED=" + translate + " from calleeParamLoc=" + calleeParamLoc);

    return translate;
  }

  private CompositeLocation computeCeilingLocationForCaller(MethodDescriptor md,
      SymbolTable nametable, MethodInvokeNode min, CompositeLocation baseLocation,
      CompositeLocation constraint) {
    List<CompositeLocation> argList = new ArrayList<CompositeLocation>();

    // by default, method has a THIS parameter
    argList.add(baseLocation);

    for (int i = 0; i < min.numArgs(); i++) {
      ExpressionNode en = min.getArg(i);
      CompositeLocation callerArg =
          inferRelationsFromExpressionNode(md, nametable, en, new CompositeLocation(), constraint,
              false);
      argList.add(callerArg);
    }

    System.out.println("\n## computeReturnLocation=" + min.getMethod() + " argList=" + argList);
    CompositeLocation compLoc = md2ReturnLocGen.get(min.getMethod()).computeReturnLocation(argList);
    DeltaLocation delta = new DeltaLocation(compLoc, 1);
    System.out.println("##computeReturnLocation=" + delta);

    return delta;

  }

  private void checkCalleeConstraints(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min, CompositeLocation callerBaseLoc, CompositeLocation constraint) {
    
    System.out.println("checkCalleeConstraints="+min.printNode(0));

    MethodDescriptor calleemd = min.getMethod();

    MethodLattice<String> calleeLattice = ssjava.getMethodLattice(calleemd);
    CompositeLocation calleeThisLoc =
        new CompositeLocation(new Location(calleemd, calleeLattice.getThisLoc()));

    List<CompositeLocation> callerArgList = new ArrayList<CompositeLocation>();
    List<CompositeLocation> calleeParamList = new ArrayList<CompositeLocation>();

    if (min.numArgs() > 0) {
      // caller needs to guarantee that it passes arguments in regarding to
      // callee's hierarchy

      // setup caller args set
      // first, add caller's base(this) location
      callerArgList.add(callerBaseLoc);
      // second, add caller's arguments
      for (int i = 0; i < min.numArgs(); i++) {
        ExpressionNode en = min.getArg(i);
        CompositeLocation callerArgLoc =
            inferRelationsFromExpressionNode(md, nametable, en, new CompositeLocation(), constraint,
                false);
        callerArgList.add(callerArgLoc);
      }

      // setup callee params set
      // first, add callee's this location
      calleeParamList.add(calleeThisLoc);
      // second, add callee's parameters
      for (int i = 0; i < calleemd.numParameters(); i++) {
        VarDescriptor calleevd = (VarDescriptor) calleemd.getParameter(i);
        CompositeLocation calleeLoc = d2loc.get(calleevd);
        System.out.println("calleevd="+calleevd+" loc="+calleeLoc);
        calleeParamList.add(calleeLoc);
      }

      // here, check if ordering relations among caller's args respect
      // ordering relations in-between callee's args
      CHECK: for (int i = 0; i < calleeParamList.size(); i++) {
        CompositeLocation calleeLoc1 = calleeParamList.get(i);
        CompositeLocation callerLoc1 = callerArgList.get(i);

        for (int j = 0; j < calleeParamList.size(); j++) {
          if (i != j) {
            CompositeLocation calleeLoc2 = calleeParamList.get(j);
            CompositeLocation callerLoc2 = callerArgList.get(j);

            if (callerLoc1.get(callerLoc1.getSize() - 1).isTop()
                || callerLoc2.get(callerLoc2.getSize() - 1).isTop()) {
              continue CHECK;
            }
            
            System.out.println("calleeLoc1="+calleeLoc1);
            System.out.println("calleeLoc2="+calleeLoc2+"calleeParamList="+calleeParamList);

            int callerResult =
                CompositeLattice.compare(callerLoc1, callerLoc2, true,
                    generateErrorMessage(md.getClassDesc(), min));
            int calleeResult =
                CompositeLattice.compare(calleeLoc1, calleeLoc2, true,
                    generateErrorMessage(md.getClassDesc(), min));

            if (calleeResult == ComparisonResult.GREATER
                && callerResult != ComparisonResult.GREATER) {
              // If calleeLoc1 is higher than calleeLoc2
              // then, caller should have same ordering relation in-bet
              // callerLoc1 & callerLoc2

              String paramName1, paramName2;

              if (i == 0) {
                paramName1 = "'THIS'";
              } else {
                paramName1 = "'parameter " + calleemd.getParamName(i - 1) + "'";
              }

              if (j == 0) {
                paramName2 = "'THIS'";
              } else {
                paramName2 = "'parameter " + calleemd.getParamName(j - 1) + "'";
              }

              throw new Error(
                  "Caller doesn't respect an ordering relation among method arguments: callee expects that "
                      + paramName1 + " should be higher than " + paramName2 + " in " + calleemd
                      + " at " + md.getClassDesc().getSourceFileName() + ":" + min.getNumLine());
            }
          }

        }
      }

    }

  }

  private CompositeLocation inferRelationsFromArrayAccessNode(MethodDescriptor md,
      SymbolTable nametable, ArrayAccessNode aan, CompositeLocation constraint, boolean isLHS) {

    ClassDescriptor cd = md.getClassDesc();

    CompositeLocation arrayLoc =
        inferRelationsFromExpressionNode(md, nametable, aan.getExpression(),
            new CompositeLocation(), constraint, isLHS);
    // addTypeLocation(aan.getExpression().getType(), arrayLoc);
    CompositeLocation indexLoc =
        inferRelationsFromExpressionNode(md, nametable, aan.getIndex(), new CompositeLocation(),
            constraint, isLHS);
    // addTypeLocation(aan.getIndex().getType(), indexLoc);

    if (isLHS) {
      if (!CompositeLattice.isGreaterThan(indexLoc, arrayLoc, generateErrorMessage(cd, aan))) {
        throw new Error("Array index value is not higher than array location at "
            + generateErrorMessage(cd, aan));
      }
      return arrayLoc;
    } else {
      Set<CompositeLocation> inputGLB = new HashSet<CompositeLocation>();
      inputGLB.add(arrayLoc);
      inputGLB.add(indexLoc);
      return CompositeLattice.calculateGLB(inputGLB, generateErrorMessage(cd, aan));
    }

  }

  private CompositeLocation inferRelationsFromCreateObjectNode(MethodDescriptor md,
      SymbolTable nametable, CreateObjectNode con) {

    ClassDescriptor cd = md.getClassDesc();

    CompositeLocation compLoc = new CompositeLocation();
    compLoc.addLocation(Location.createTopLocation(md));
    return compLoc;

  }

  private CompositeLocation inferRelationsFromOpNode(MethodDescriptor md, SymbolTable nametable,
      OpNode on, CompositeLocation constraint) {

    ClassDescriptor cd = md.getClassDesc();
    CompositeLocation leftLoc = new CompositeLocation();
    leftLoc =
        inferRelationsFromExpressionNode(md, nametable, on.getLeft(), leftLoc, constraint, false);
    // addTypeLocation(on.getLeft().getType(), leftLoc);

    CompositeLocation rightLoc = new CompositeLocation();
    if (on.getRight() != null) {
      rightLoc =
          inferRelationsFromExpressionNode(md, nametable, on.getRight(), rightLoc, constraint, false);
      // addTypeLocation(on.getRight().getType(), rightLoc);
    }

    System.out.println("\n# OP NODE=" + on.printNode(0));
    System.out.println("# left loc=" + leftLoc + " from " + on.getLeft().getClass());
    if (on.getRight() != null) {
      System.out.println("# right loc=" + rightLoc + " from " + on.getRight().getClass());
    }

    Operation op = on.getOp();

    switch (op.getOp()) {

    case Operation.UNARYPLUS:
    case Operation.UNARYMINUS:
    case Operation.LOGIC_NOT:
      // single operand
      return leftLoc;

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
      inputSet.add(leftLoc);
      inputSet.add(rightLoc);
      CompositeLocation glbCompLoc =
          CompositeLattice.calculateGLB(inputSet, generateErrorMessage(cd, on));
      System.out.println("# glbCompLoc=" + glbCompLoc);
      return glbCompLoc;

    default:
      throw new Error(op.toString());
    }

  }
    */
  private VarID inferRelationsFromLiteralNode(MethodDescriptor md,
  SymbolTable nametable, LiteralNode ln) {
      //literal data flow does not matter
    return null;

  }

  private VarID inferRelationsFromNameNode(MethodDescriptor md, SymbolTable nametable,
					   NameNode nn, VarID flowTo, BlockStatementNode implicitTag) {
    VarID var = null;
    NameDescriptor nd = nn.getName();
    if (nd.getBase() != null) {
      var =
          inferRelationsFromExpressionNode(md, nametable, nn.getExpression(), flowTo, implicitTag, false);
    } else {
      String varname = nd.toString();
      if (varname.equals("this")) {
	var = new VarID(nd);
	if(flowTo != null){
	    rSet.addRelation(new BinaryRelation(var,flowTo));
	}
	if(implicitTag != null){
	    implicitFlowSet.add(new ImplicitTuple(var,implicitTag));
	}
	var.setThis();
        return var;
      }

      Descriptor d = (Descriptor) nametable.get(varname);

      if (d instanceof VarDescriptor) {
        var = new VarID(nd);
	if(flowTo != null){
	  rSet.addRelation(new BinaryRelation(var,flowTo));
	}		   
	if(implicitTag != null){
	    implicitFlowSet.add(new ImplicitTuple(var,implicitTag));
	}
      } else if (d instanceof FieldDescriptor) {
        FieldDescriptor fd = (FieldDescriptor) d;
        if (fd.isStatic()) {
          if (fd.isFinal()) {
            var = new VarID(nd);
	    if(flowTo != null){
	      rSet.addRelation(new BinaryRelation(var,flowTo));
	    }
	    if(implicitTag != null){
		implicitFlowSet.add(new ImplicitTuple(var,implicitTag));
	    }
	    var.setTop();
            return var;
          } else {
            var = new VarID(nd);
	    if(flowTo != null){
	      rSet.addRelation(new BinaryRelation(var,flowTo));
	    }
	    if(implicitTag != null){
		implicitFlowSet.add(new ImplicitTuple(var,implicitTag));
	    }
	    var.setGlobal();
          }
        } else {
            var = new VarID(nd);
	    if(flowTo != null){
	      rSet.addRelation(new BinaryRelation(var,flowTo));
	    }
	    if(implicitTag != null){
		implicitFlowSet.add(new ImplicitTuple(var,implicitTag));
	    }
	    var.setThis();
        }
      } else if (d == null) {
        var = new VarID(nd);
	if(flowTo != null){
	  rSet.addRelation(new BinaryRelation(var,flowTo));
	}
	if(implicitTag != null){
	    implicitFlowSet.add(new ImplicitTuple(var,implicitTag));
	}
	var.setGlobal();
        return var;
      }
    }
    return var;
  }
  /*
  private CompositeLocation inferRelationsFromFieldAccessNode(MethodDescriptor md,
      SymbolTable nametable, FieldAccessNode fan, CompositeLocation loc,
      CompositeLocation constraint) {

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
        loc.addLocation(Location.createTopLocation(md));
        return loc;
      }
    }

    loc = inferRelationsFromExpressionNode(md, nametable, left, loc, constraint, false);
    System.out.println("### inferRelationsFromFieldAccessNode=" + fan.printNode(0));
    System.out.println("### left=" + left.printNode(0));
    if (!left.getType().isPrimitive()) {
      Location fieldLoc = getFieldLocation(fd);
      loc.addLocation(fieldLoc);
    }

    return loc;
  }

  private Location getFieldLocation(FieldDescriptor fd) {

    System.out.println("### getFieldLocation=" + fd);
    System.out.println("### fd.getType().getExtension()=" + fd.getType().getExtension());

    Location fieldLoc = (Location) fd.getType().getExtension();

    // handle the case that method annotation checking skips checking field
    // declaration
    if (fieldLoc == null) {
      fieldLoc = checkFieldDeclaration(fd.getClassDescriptor(), fd);
    }

    return fieldLoc;

  }*/

  private VarID inferRelationsFromAssignmentNode(MethodDescriptor md,
	 SymbolTable nametable, AssignmentNode an, VarID flowTo, BlockStatementNode implicitTag) {
    ClassDescriptor cd = md.getClassDesc();
    boolean postinc = true;
    
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC))
      postinc = false;
    //get ID for leftside
    VarID destID = inferRelationsFromExpressionNode(md, nametable, an.getDest(), flowTo, implicitTag, true);

    if (!postinc) {
      //recursively add relations from RHS to LHS
	inferRelationsFromExpressionNode(md, nametable, an.getSrc(), destID, null, false);
     
    } else {
	//add relation to self
	destID = inferRelationsFromExpressionNode(md, nametable, an.getDest(), destID, null, false);
    }
    
    //checks if flow to anythin and adds relation
    if(flowTo != null){
	rSet.addRelation(new BinaryRelation(destID, flowTo));
    }

    //add relations for implicit flow
    for(ImplicitTuple it: implicitFlowSet){
	rSet.addRelation(new BinaryRelation(it.getVar(),destID));
    }

    return destID;
  }
}