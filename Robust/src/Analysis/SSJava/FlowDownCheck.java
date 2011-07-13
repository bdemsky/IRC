package Analysis.SSJava;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

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
import IR.Tree.TertiaryNode;
import IR.Tree.TreeNode;
import Util.Pair;

public class FlowDownCheck {

  State state;
  static SSJavaAnalysis ssjava;

  HashSet toanalyze;

  // mapping from 'descriptor' to 'composite location'
  Hashtable<Descriptor, CompositeLocation> d2loc;

  Hashtable<MethodDescriptor, CompositeLocation> md2ReturnLoc;
  Hashtable<MethodDescriptor, ReturnLocGenerator> md2ReturnLocGen;

  // mapping from 'locID' to 'class descriptor'
  Hashtable<String, ClassDescriptor> fieldLocName2cd;

  public FlowDownCheck(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.toanalyze = new HashSet();
    this.d2loc = new Hashtable<Descriptor, CompositeLocation>();
    this.fieldLocName2cd = new Hashtable<String, ClassDescriptor>();
    this.md2ReturnLoc = new Hashtable<MethodDescriptor, CompositeLocation>();
    this.md2ReturnLocGen = new Hashtable<MethodDescriptor, ReturnLocGenerator>();
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

  public void flowDownCheck() {
    SymbolTable classtable = state.getClassSymbolTable();

    // phase 1 : checking declaration node and creating mapping of 'type
    // desciptor' & 'location'
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      ClassDescriptor cd = (ClassDescriptor) obj;
      toanalyze.remove(cd);

      if (ssjava.needToBeAnnoated(cd) && (!cd.isInterface())) {
        
        ClassDescriptor superDesc = cd.getSuperDesc();
        if (superDesc != null && (!superDesc.isInterface())
            && (!superDesc.getSymbol().equals("Object"))) {
          checkOrderingInheritance(superDesc, cd);
        }

        checkDeclarationInClass(cd);
        for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) method_it.next();
          if (ssjava.needTobeAnnotated(md)) {
            checkDeclarationInMethodBody(cd, md);
          }
        }
      }

    }

    // phase2 : checking assignments
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      ClassDescriptor cd = (ClassDescriptor) obj;
      toanalyze.remove(cd);

      checkClass(cd);
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        if (ssjava.needTobeAnnotated(md)) {
          checkMethodBody(cd, md);
        }
      }
    }

  }

  private void checkOrderingInheritance(ClassDescriptor superCd, ClassDescriptor cd) {
    // here, we're going to check that sub class keeps same relative orderings
    // in respect to super class

    SSJavaLattice<String> superLattice = ssjava.getClassLattice(superCd);
    SSJavaLattice<String> subLattice = ssjava.getClassLattice(cd);

    if (superLattice != null) {

      if (subLattice == null) {
        throw new Error("If a parent class '" + superCd
            + "' has a ordering lattice, its subclass '" + cd + "' should have one.");
      }

      Set<Pair<String, String>> superPairSet = superLattice.getOrderingPairSet();
      Set<Pair<String, String>> subPairSet = subLattice.getOrderingPairSet();

      for (Iterator iterator = superPairSet.iterator(); iterator.hasNext();) {
        Pair<String, String> pair = (Pair<String, String>) iterator.next();

        if (!subPairSet.contains(pair)) {
          throw new Error("Subclass '" + cd + "' does not have the relative ordering '"
              + pair.getSecond() + " < " + pair.getFirst()
              + "' that is defined by its superclass '" + superCd + "'.");
        }
      }

    }
    // if super class doesn't define lattice, then we don't need to check its
    // subclass

  }

  public Hashtable getMap() {
    return d2loc;
  }

  private void checkDeclarationInMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);

    // parsing returnloc annotation
    if (ssjava.needTobeAnnotated(md)) {

      Vector<AnnotationDescriptor> methodAnnotations = md.getModifiers().getAnnotations();
      if (methodAnnotations != null) {
        for (int i = 0; i < methodAnnotations.size(); i++) {
          AnnotationDescriptor an = methodAnnotations.elementAt(i);
          if (an.getMarker().equals(ssjava.RETURNLOC)) {
            // developer explicitly defines method lattice
            String returnLocDeclaration = an.getValue();
            CompositeLocation returnLocComp =
                parseLocationDeclaration(md, null, returnLocDeclaration);
            md2ReturnLoc.put(md, returnLocComp);
          }
        }

        if (!md.getReturnType().isVoid() && !md2ReturnLoc.containsKey(md)) {
          throw new Error("Return location is not specified for the method " + md + " at "
              + cd.getSourceFileName());
        }

      }
    }

    List<CompositeLocation> paramList = new ArrayList<CompositeLocation>();

    boolean hasReturnValue = (!md.getReturnType().isVoid());
    if (hasReturnValue) {
      MethodLattice<String> methodLattice = ssjava.getMethodLattice(md);
      String thisLocId = methodLattice.getThisLoc();
      CompositeLocation thisLoc = new CompositeLocation(new Location(md, thisLocId));
      paramList.add(thisLoc);
    }

    for (int i = 0; i < md.numParameters(); i++) {
      // process annotations on method parameters
      VarDescriptor vd = (VarDescriptor) md.getParameter(i);
      assignLocationOfVarDescriptor(vd, md, md.getParameterTable(), bn);
      if (hasReturnValue) {
        paramList.add(d2loc.get(vd));
      }
    }

    if (hasReturnValue) {
      md2ReturnLocGen.put(md, new ReturnLocGenerator(md2ReturnLoc.get(md), paramList));
    }

    checkDeclarationInBlockNode(md, md.getParameterTable(), bn);
  }

  private void checkDeclarationInBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {
    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      checkDeclarationInBlockStatementNode(md, bn.getVarTable(), bsn);
    }
  }

  private void checkDeclarationInBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    switch (bsn.kind()) {
    case Kind.SubBlockNode:
      checkDeclarationInSubBlockNode(md, nametable, (SubBlockNode) bsn);
      return;

    case Kind.DeclarationNode:
      checkDeclarationNode(md, nametable, (DeclarationNode) bsn);
      break;

    case Kind.LoopNode:
      checkDeclarationInLoopNode(md, nametable, (LoopNode) bsn);
      break;
    }
  }

  private void checkDeclarationInLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln) {

    if (ln.getType() == LoopNode.FORLOOP) {
      // check for loop case
      ClassDescriptor cd = md.getClassDesc();
      BlockNode bn = ln.getInitializer();
      for (int i = 0; i < bn.size(); i++) {
        BlockStatementNode bsn = bn.get(i);
        checkDeclarationInBlockStatementNode(md, nametable, bsn);
      }
    }

    // check loop body
    checkDeclarationInBlockNode(md, nametable, ln.getBody());
  }

  private void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    checkLocationFromBlockNode(md, md.getParameterTable(), bn);
  }

  private CompositeLocation checkLocationFromBlockNode(MethodDescriptor md, SymbolTable nametable,
      BlockNode bn) {

    bn.getVarTable().setParent(nametable);
    // it will return the lowest location in the block node
    CompositeLocation lowestLoc = null;

    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      CompositeLocation bLoc = checkLocationFromBlockStatementNode(md, bn.getVarTable(), bsn);
      if (!bLoc.isEmpty()) {
        if (lowestLoc == null) {
          lowestLoc = bLoc;
        } else {
          if (CompositeLattice.isGreaterThan(lowestLoc, bLoc)) {
            lowestLoc = bLoc;
          }
        }
      }

    }

    if (lowestLoc == null) {
      lowestLoc = new CompositeLocation(Location.createBottomLocation(md));
    }

    return lowestLoc;
  }

  private CompositeLocation checkLocationFromBlockStatementNode(MethodDescriptor md,
      SymbolTable nametable, BlockStatementNode bsn) {

    CompositeLocation compLoc = null;
    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      compLoc = checkLocationFromBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
      break;

    case Kind.DeclarationNode:
      compLoc = checkLocationFromDeclarationNode(md, nametable, (DeclarationNode) bsn);
      break;

    case Kind.IfStatementNode:
      compLoc = checkLocationFromIfStatementNode(md, nametable, (IfStatementNode) bsn);
      break;

    case Kind.LoopNode:
      compLoc = checkLocationFromLoopNode(md, nametable, (LoopNode) bsn);
      break;

    case Kind.ReturnNode:
      compLoc = checkLocationFromReturnNode(md, nametable, (ReturnNode) bsn);
      break;

    case Kind.SubBlockNode:
      compLoc = checkLocationFromSubBlockNode(md, nametable, (SubBlockNode) bsn);
      break;

    case Kind.ContinueBreakNode:
      compLoc = new CompositeLocation();
      break;

    }
    return compLoc;
  }

  private CompositeLocation checkLocationFromReturnNode(MethodDescriptor md, SymbolTable nametable,
      ReturnNode rn) {

    ExpressionNode returnExp = rn.getReturnExpression();

    CompositeLocation expLoc;
    if (returnExp != null) {
      expLoc = checkLocationFromExpressionNode(md, nametable, returnExp, new CompositeLocation());
      // check if return value is equal or higher than RETRUNLOC of method
      // declaration annotation
      CompositeLocation returnLocAt = md2ReturnLoc.get(md);

      if (CompositeLattice.isGreaterThan(returnLocAt, expLoc)) {
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

  private CompositeLocation checkLocationFromLoopNode(MethodDescriptor md, SymbolTable nametable,
      LoopNode ln) {

    ClassDescriptor cd = md.getClassDesc();
    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {

      CompositeLocation condLoc =
          checkLocationFromExpressionNode(md, nametable, ln.getCondition(), new CompositeLocation());
      addLocationType(ln.getCondition().getType(), (condLoc));

      CompositeLocation bodyLoc = checkLocationFromBlockNode(md, nametable, ln.getBody());

      if (!CompositeLattice.isGreaterThan(condLoc, bodyLoc)) {
        // loop condition should be higher than loop body
        throw new Error(
            "The location of the while-condition statement is lower than the loop body at "
                + cd.getSourceFileName() + ":" + ln.getCondition().getNumLine());
      }

      return bodyLoc;

    } else {
      // check for loop case
      BlockNode bn = ln.getInitializer();
      bn.getVarTable().setParent(nametable);

      // calculate glb location of condition and update statements
      CompositeLocation condLoc =
          checkLocationFromExpressionNode(md, bn.getVarTable(), ln.getCondition(),
              new CompositeLocation());
      addLocationType(ln.getCondition().getType(), condLoc);

      CompositeLocation updateLoc =
          checkLocationFromBlockNode(md, bn.getVarTable(), ln.getUpdate());

      Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();
      glbInputSet.add(condLoc);
      // glbInputSet.add(updateLoc);

      CompositeLocation glbLocOfForLoopCond = CompositeLattice.calculateGLB(glbInputSet);

      // check location of 'forloop' body
      CompositeLocation blockLoc = checkLocationFromBlockNode(md, bn.getVarTable(), ln.getBody());

      // compute glb of body including loop body and update statement
      glbInputSet.clear();

      if (blockLoc == null) {
        // when there is no statement in the loop body

        if (updateLoc == null) {
          // also there is no update statement in the loop body
          return glbLocOfForLoopCond;
        }
        glbInputSet.add(updateLoc);

      } else {
        glbInputSet.add(blockLoc);
        glbInputSet.add(updateLoc);
      }

      CompositeLocation loopBodyLoc = CompositeLattice.calculateGLB(glbInputSet);

      if (!CompositeLattice.isGreaterThan(glbLocOfForLoopCond, loopBodyLoc)) {
        throw new Error(
            "The location of the for-condition statement is lower than the for-loop body at "
                + cd.getSourceFileName() + ":" + ln.getCondition().getNumLine());
      }
      return blockLoc;
    }

  }

  private CompositeLocation checkLocationFromSubBlockNode(MethodDescriptor md,
      SymbolTable nametable, SubBlockNode sbn) {
    CompositeLocation compLoc = checkLocationFromBlockNode(md, nametable, sbn.getBlockNode());
    return compLoc;
  }

  private CompositeLocation checkLocationFromIfStatementNode(MethodDescriptor md,
      SymbolTable nametable, IfStatementNode isn) {

    ClassDescriptor localCD = md.getClassDesc();
    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();

    CompositeLocation condLoc =
        checkLocationFromExpressionNode(md, nametable, isn.getCondition(), new CompositeLocation());

    addLocationType(isn.getCondition().getType(), condLoc);
    glbInputSet.add(condLoc);

    CompositeLocation locTrueBlock = checkLocationFromBlockNode(md, nametable, isn.getTrueBlock());
    if (locTrueBlock != null) {
      glbInputSet.add(locTrueBlock);
      // here, the location of conditional block should be higher than the
      // location of true/false blocks
      if (locTrueBlock != null && !CompositeLattice.isGreaterThan(condLoc, locTrueBlock)) {
        // error
        throw new Error(
            "The location of the if-condition statement is lower than the conditional block at "
                + localCD.getSourceFileName() + ":" + isn.getCondition().getNumLine());
      }
    }

    if (isn.getFalseBlock() != null) {
      CompositeLocation locFalseBlock =
          checkLocationFromBlockNode(md, nametable, isn.getFalseBlock());

      if (locFalseBlock != null) {
        glbInputSet.add(locFalseBlock);

        if (!CompositeLattice.isGreaterThan(condLoc, locFalseBlock)) {
          // error
          throw new Error(
              "The location of the if-condition statement is lower than the conditional block at "
                  + localCD.getSourceFileName() + ":" + isn.getCondition().getNumLine());
        }
      }

    }

    // return GLB location of condition, true, and false block
    CompositeLocation glbLoc = CompositeLattice.calculateGLB(glbInputSet);

    return glbLoc;
  }

  private CompositeLocation checkLocationFromDeclarationNode(MethodDescriptor md,
      SymbolTable nametable, DeclarationNode dn) {

    VarDescriptor vd = dn.getVarDescriptor();

    CompositeLocation destLoc = d2loc.get(vd);

    if (dn.getExpression() != null) {
      CompositeLocation expressionLoc =
          checkLocationFromExpressionNode(md, nametable, dn.getExpression(),
              new CompositeLocation());
      // addTypeLocation(dn.getExpression().getType(), expressionLoc);

      if (expressionLoc != null) {
        // checking location order
        if (!CompositeLattice.isGreaterThan(expressionLoc, destLoc)) {
          throw new Error("The value flow from " + expressionLoc + " to " + destLoc
              + " does not respect location hierarchy on the assignment " + dn.printNode(0)
              + " at " + md.getClassDesc().getSourceFileName() + "::" + dn.getNumLine());
        }
      }
      return expressionLoc;

    } else {

      return new CompositeLocation();

    }

  }

  private void checkDeclarationInSubBlockNode(MethodDescriptor md, SymbolTable nametable,
      SubBlockNode sbn) {
    checkDeclarationInBlockNode(md, nametable.getParent(), sbn.getBlockNode());
  }

  private CompositeLocation checkLocationFromBlockExpressionNode(MethodDescriptor md,
      SymbolTable nametable, BlockExpressionNode ben) {
    CompositeLocation compLoc =
        checkLocationFromExpressionNode(md, nametable, ben.getExpression(), null);
    // addTypeLocation(ben.getExpression().getType(), compLoc);
    return compLoc;
  }

  private CompositeLocation checkLocationFromExpressionNode(MethodDescriptor md,
      SymbolTable nametable, ExpressionNode en, CompositeLocation loc) {

    CompositeLocation compLoc = null;
    switch (en.kind()) {

    case Kind.AssignmentNode:
      compLoc = checkLocationFromAssignmentNode(md, nametable, (AssignmentNode) en, loc);
      break;

    case Kind.FieldAccessNode:
      compLoc = checkLocationFromFieldAccessNode(md, nametable, (FieldAccessNode) en, loc);
      break;

    case Kind.NameNode:
      compLoc = checkLocationFromNameNode(md, nametable, (NameNode) en, loc);
      break;

    case Kind.OpNode:
      compLoc = checkLocationFromOpNode(md, nametable, (OpNode) en);
      break;

    case Kind.CreateObjectNode:
      compLoc = checkLocationFromCreateObjectNode(md, nametable, (CreateObjectNode) en);
      break;

    case Kind.ArrayAccessNode:
      compLoc = checkLocationFromArrayAccessNode(md, nametable, (ArrayAccessNode) en);
      break;

    case Kind.LiteralNode:
      compLoc = checkLocationFromLiteralNode(md, nametable, (LiteralNode) en, loc);
      break;

    case Kind.MethodInvokeNode:
      compLoc = checkLocationFromMethodInvokeNode(md, nametable, (MethodInvokeNode) en, loc);
      break;

    case Kind.TertiaryNode:
      compLoc = checkLocationFromTertiaryNode(md, nametable, (TertiaryNode) en);
      break;

    case Kind.CastNode:
      compLoc = checkLocationFromCastNode(md, nametable, (CastNode) en);
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

    default:
      return null;

    }
    // addTypeLocation(en.getType(), compLoc);
    return compLoc;

  }

  private CompositeLocation checkLocationFromCastNode(MethodDescriptor md, SymbolTable nametable,
      CastNode cn) {

    ExpressionNode en = cn.getExpression();
    return checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation());

  }

  private CompositeLocation checkLocationFromTertiaryNode(MethodDescriptor md,
      SymbolTable nametable, TertiaryNode tn) {
    ClassDescriptor cd = md.getClassDesc();

    CompositeLocation condLoc =
        checkLocationFromExpressionNode(md, nametable, tn.getCond(), new CompositeLocation());
    addLocationType(tn.getCond().getType(), condLoc);
    CompositeLocation trueLoc =
        checkLocationFromExpressionNode(md, nametable, tn.getTrueExpr(), new CompositeLocation());
    addLocationType(tn.getTrueExpr().getType(), trueLoc);
    CompositeLocation falseLoc =
        checkLocationFromExpressionNode(md, nametable, tn.getFalseExpr(), new CompositeLocation());
    addLocationType(tn.getFalseExpr().getType(), falseLoc);

    // check if condLoc is higher than trueLoc & falseLoc
    if (!CompositeLattice.isGreaterThan(condLoc, trueLoc)) {
      throw new Error(
          "The location of the condition expression is lower than the true expression at "
              + cd.getSourceFileName() + ":" + tn.getCond().getNumLine());
    }

    if (!CompositeLattice.isGreaterThan(condLoc, falseLoc)) {
      throw new Error(
          "The location of the condition expression is lower than the true expression at "
              + cd.getSourceFileName() + ":" + tn.getCond().getNumLine());
    }

    // then, return glb of trueLoc & falseLoc
    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();
    glbInputSet.add(trueLoc);
    glbInputSet.add(falseLoc);

    return CompositeLattice.calculateGLB(glbInputSet);
  }

  private CompositeLocation checkLocationFromMethodInvokeNode(MethodDescriptor md,
      SymbolTable nametable, MethodInvokeNode min, CompositeLocation loc) {

    checkCalleeConstraints(md, nametable, min);

    CompositeLocation baseLocation = null;
    if (min.getExpression() != null) {
      baseLocation =
          checkLocationFromExpressionNode(md, nametable, min.getExpression(),
              new CompositeLocation());
    } else {
      String thisLocId = ssjava.getMethodLattice(md).getThisLoc();
      baseLocation = new CompositeLocation(new Location(md, thisLocId));
    }

    if (!min.getMethod().getReturnType().isVoid()) {
      // If method has a return value, compute the highest possible return
      // location in the caller's perspective
      CompositeLocation ceilingLoc =
          computeCeilingLocationForCaller(md, nametable, min, baseLocation);
      return ceilingLoc;
    }

    return new CompositeLocation();

  }

  private CompositeLocation computeCeilingLocationForCaller(MethodDescriptor md,
      SymbolTable nametable, MethodInvokeNode min, CompositeLocation baseLocation) {
    List<CompositeLocation> argList = new ArrayList<CompositeLocation>();

    // by default, method has a THIS parameter
    argList.add(baseLocation);

    for (int i = 0; i < min.numArgs(); i++) {
      ExpressionNode en = min.getArg(i);
      CompositeLocation callerArg =
          checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation());
      argList.add(callerArg);
    }

    return md2ReturnLocGen.get(min.getMethod()).computeReturnLocation(argList);

  }

  private void checkCalleeConstraints(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min) {

    if (min.numArgs() > 1) {
      // caller needs to guarantee that it passes arguments in regarding to
      // callee's hierarchy
      for (int i = 0; i < min.numArgs(); i++) {
        ExpressionNode en = min.getArg(i);
        CompositeLocation callerArg1 =
            checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation());

        ClassDescriptor calleecd = min.getMethod().getClassDesc();
        VarDescriptor calleevd = (VarDescriptor) min.getMethod().getParameter(i);
        CompositeLocation calleeLoc1 = d2loc.get(calleevd);

        if (!callerArg1.get(0).isTop()) {
          // here, check if ordering relations among caller's args respect
          // ordering relations in-between callee's args
          for (int currentIdx = 0; currentIdx < min.numArgs(); currentIdx++) {
            if (currentIdx != i) { // skip itself
              ExpressionNode argExp = min.getArg(currentIdx);

              CompositeLocation callerArg2 =
                  checkLocationFromExpressionNode(md, nametable, argExp, new CompositeLocation());

              VarDescriptor calleevd2 = (VarDescriptor) min.getMethod().getParameter(currentIdx);
              CompositeLocation calleeLoc2 = d2loc.get(calleevd2);

              int callerResult = CompositeLattice.compare(callerArg1, callerArg2);
              int calleeResult = CompositeLattice.compare(calleeLoc1, calleeLoc2);
              if (calleeResult == ComparisonResult.GREATER
                  && callerResult != ComparisonResult.GREATER) {
                // If calleeLoc1 is higher than calleeLoc2
                // then, caller should have same ordering relation in-bet
                // callerLoc1 & callerLoc2

                throw new Error("Caller doesn't respect ordering relations among method arguments:"
                    + md.getClassDesc().getSourceFileName() + ":" + min.getNumLine());
              }

            }
          }
        }

      }

    }

  }

  private CompositeLocation checkLocationFromArrayAccessNode(MethodDescriptor md,
      SymbolTable nametable, ArrayAccessNode aan) {

    // return glb location of array itself and index

    ClassDescriptor cd = md.getClassDesc();

    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();

    CompositeLocation arrayLoc =
        checkLocationFromExpressionNode(md, nametable, aan.getExpression(), new CompositeLocation());
    // addTypeLocation(aan.getExpression().getType(), arrayLoc);
    glbInputSet.add(arrayLoc);
    CompositeLocation indexLoc =
        checkLocationFromExpressionNode(md, nametable, aan.getIndex(), new CompositeLocation());
    glbInputSet.add(indexLoc);
    // addTypeLocation(aan.getIndex().getType(), indexLoc);

    CompositeLocation glbLoc = CompositeLattice.calculateGLB(glbInputSet);
    return glbLoc;
  }

  private CompositeLocation checkLocationFromCreateObjectNode(MethodDescriptor md,
      SymbolTable nametable, CreateObjectNode con) {

    ClassDescriptor cd = md.getClassDesc();

    // check arguments
    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();
    for (int i = 0; i < con.numArgs(); i++) {
      ExpressionNode en = con.getArg(i);
      CompositeLocation argLoc =
          checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation());
      glbInputSet.add(argLoc);
      addLocationType(en.getType(), argLoc);
    }

    // check array initializers
    // if ((con.getArrayInitializer() != null)) {
    // checkLocationFromArrayInitializerNode(md, nametable,
    // con.getArrayInitializer());
    // }

    if (glbInputSet.size() > 0) {
      return CompositeLattice.calculateGLB(glbInputSet);
    }

    CompositeLocation compLoc = new CompositeLocation();
    compLoc.addLocation(Location.createTopLocation(md));
    return compLoc;

  }

  private CompositeLocation checkLocationFromOpNode(MethodDescriptor md, SymbolTable nametable,
      OpNode on) {

    ClassDescriptor cd = md.getClassDesc();
    CompositeLocation leftLoc = new CompositeLocation();
    leftLoc = checkLocationFromExpressionNode(md, nametable, on.getLeft(), leftLoc);
    // addTypeLocation(on.getLeft().getType(), leftLoc);

    CompositeLocation rightLoc = new CompositeLocation();
    if (on.getRight() != null) {
      rightLoc = checkLocationFromExpressionNode(md, nametable, on.getRight(), rightLoc);
      // addTypeLocation(on.getRight().getType(), rightLoc);
    }

    // System.out.println("checking op node=" + on.printNode(0));
    // System.out.println("left loc=" + leftLoc + " from " +
    // on.getLeft().getClass());
    // System.out.println("right loc=" + rightLoc + " from " +
    // on.getRight().getClass());

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
      CompositeLocation glbCompLoc = CompositeLattice.calculateGLB(inputSet);
      return glbCompLoc;

    default:
      throw new Error(op.toString());
    }

  }

  private CompositeLocation checkLocationFromLiteralNode(MethodDescriptor md,
      SymbolTable nametable, LiteralNode en, CompositeLocation loc) {

    // literal value has the top location so that value can be flowed into any
    // location
    Location literalLoc = Location.createTopLocation(md);
    loc.addLocation(literalLoc);
    return loc;

  }

  private CompositeLocation checkLocationFromNameNode(MethodDescriptor md, SymbolTable nametable,
      NameNode nn, CompositeLocation loc) {

    NameDescriptor nd = nn.getName();
    if (nd.getBase() != null) {

      loc = checkLocationFromExpressionNode(md, nametable, nn.getExpression(), loc);
      // addTypeLocation(nn.getExpression().getType(), loc);
    } else {
      String varname = nd.toString();

      if (varname.equals("this")) {
        // 'this' itself!
        MethodLattice<String> methodLattice = ssjava.getMethodLattice(md);
        String thisLocId = methodLattice.getThisLoc();
        if (thisLocId == null) {
          throw new Error("The location for 'this' is not defined at "
              + md.getClassDesc().getSourceFileName() + "::" + nn.getNumLine());
        }
        Location locElement = new Location(md, thisLocId);
        loc.addLocation(locElement);
        return loc;
      }
      Descriptor d = (Descriptor) nametable.get(varname);

      // CompositeLocation localLoc = null;
      if (d instanceof VarDescriptor) {
        VarDescriptor vd = (VarDescriptor) d;
        // localLoc = d2loc.get(vd);
        // the type of var descriptor has a composite location!
        loc = ((CompositeLocation) vd.getType().getExtension()).clone();
      } else if (d instanceof FieldDescriptor) {
        // the type of field descriptor has a location!
        FieldDescriptor fd = (FieldDescriptor) d;

        if (fd.isStatic()) {
          if (fd.isFinal()) {
            // if it is 'static final', the location has TOP since no one can
            // change its value
            loc.addLocation(Location.createTopLocation(md));
          } else {
            // if 'static', the location has pre-assigned global loc
            MethodLattice<String> localLattice = ssjava.getMethodLattice(md);
            String globalLocId = localLattice.getGlobalLoc();
            if (globalLocId == null) {
              throw new Error("Global location element is not defined in the method " + md);
            }
            Location globalLoc = new Location(md, globalLocId);

            loc.addLocation(globalLoc);
          }
        } else {
          // the location of field access starts from this, followed by field
          // location
          MethodLattice<String> localLattice = ssjava.getMethodLattice(md);
          Location thisLoc = new Location(md, localLattice.getThisLoc());
          loc.addLocation(thisLoc);
        }

        Location fieldLoc = (Location) fd.getType().getExtension();
        loc.addLocation(fieldLoc);
      }
    }
    return loc;
  }

  private CompositeLocation checkLocationFromFieldAccessNode(MethodDescriptor md,
      SymbolTable nametable, FieldAccessNode fan, CompositeLocation loc) {

    ExpressionNode left = fan.getExpression();
    loc = checkLocationFromExpressionNode(md, nametable, left, loc);

    if (!left.getType().isPrimitive()) {
      FieldDescriptor fd = fan.getField();
      Location fieldLoc = (Location) fd.getType().getExtension();
      loc.addLocation(fieldLoc);
    }

    return loc;
  }

  private CompositeLocation checkLocationFromAssignmentNode(MethodDescriptor md,
      SymbolTable nametable, AssignmentNode an, CompositeLocation loc) {

    ClassDescriptor cd = md.getClassDesc();

    boolean postinc = true;
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC))
      postinc = false;

    CompositeLocation destLocation =
        checkLocationFromExpressionNode(md, nametable, an.getDest(), new CompositeLocation());

    CompositeLocation srcLocation = new CompositeLocation();

    if (!postinc) {
      if (hasOnlyLiteralValue(an.getSrc())) {
        // if source is literal value, src location is TOP. so do not need to
        // compare!
        return destLocation;
      }
      srcLocation = new CompositeLocation();
      srcLocation = checkLocationFromExpressionNode(md, nametable, an.getSrc(), srcLocation);
      // System.out.println(" an= " + an.printNode(0) + " an.getSrc()=" +
      // an.getSrc().getClass()
      // + " at " + cd.getSourceFileName() + "::" + an.getNumLine());
      // System.out.println("srcLocation=" + srcLocation);
      // System.out.println("dstLocation=" + destLocation);
      if (!CompositeLattice.isGreaterThan(srcLocation, destLocation)) {
        throw new Error("The value flow from " + srcLocation + " to " + destLocation
            + " does not respect location hierarchy on the assignment " + an.printNode(0) + " at "
            + cd.getSourceFileName() + "::" + an.getNumLine());
      }
    } else {
      destLocation =
          srcLocation = checkLocationFromExpressionNode(md, nametable, an.getDest(), srcLocation);

      if (!CompositeLattice.isGreaterThan(srcLocation, destLocation)) {
        throw new Error("Location " + destLocation
            + " is not allowed to have the value flow that moves within the same location at "
            + cd.getSourceFileName() + "::" + an.getNumLine());
      }

    }

    return destLocation;
  }

  private void assignLocationOfVarDescriptor(VarDescriptor vd, MethodDescriptor md,
      SymbolTable nametable, TreeNode n) {

    ClassDescriptor cd = md.getClassDesc();
    Vector<AnnotationDescriptor> annotationVec = vd.getType().getAnnotationMarkers();

    if (!md.getModifiers().isAbstract()) {
      // currently enforce every variable to have corresponding location
      if (annotationVec.size() == 0) {
        throw new Error("Location is not assigned to variable " + vd.getSymbol()
            + " in the method " + md.getSymbol() + " of the class " + cd.getSymbol());
      }

      if (annotationVec.size() > 1) { // variable can have at most one location
        throw new Error(vd.getSymbol() + " has more than one location.");
      }

      AnnotationDescriptor ad = annotationVec.elementAt(0);

      if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {

        if (ad.getMarker().equals(SSJavaAnalysis.LOC)) {
          String locDec = ad.getValue(); // check if location is defined

          if (locDec.startsWith(SSJavaAnalysis.DELTA)) {
            DeltaLocation deltaLoc = parseDeltaDeclaration(md, n, locDec);
            d2loc.put(vd, deltaLoc);
            addLocationType(vd.getType(), deltaLoc);
          } else {
            CompositeLocation compLoc = parseLocationDeclaration(md, n, locDec);

            Location lastElement = compLoc.get(compLoc.getSize() - 1);
            if (ssjava.isSharedLocation(lastElement)) {
              ssjava.mapSharedLocation2Descriptor(lastElement, vd);
            }

            d2loc.put(vd, compLoc);
            addLocationType(vd.getType(), compLoc);
          }

        }
      }
    }

  }

  private DeltaLocation parseDeltaDeclaration(MethodDescriptor md, TreeNode n, String locDec) {

    int deltaCount = 0;
    int dIdx = locDec.indexOf(SSJavaAnalysis.DELTA);
    while (dIdx >= 0) {
      deltaCount++;
      int beginIdx = dIdx + 6;
      locDec = locDec.substring(beginIdx, locDec.length() - 1);
      dIdx = locDec.indexOf(SSJavaAnalysis.DELTA);
    }

    CompositeLocation compLoc = parseLocationDeclaration(md, n, locDec);
    DeltaLocation deltaLoc = new DeltaLocation(compLoc, deltaCount);

    return deltaLoc;
  }

  private Location parseFieldLocDeclaraton(String decl) {

    int idx = decl.indexOf(".");
    String className = decl.substring(0, idx);
    String fieldName = decl.substring(idx + 1);

    Descriptor d = state.getClassSymbolTable().get(className);

    assert (d instanceof ClassDescriptor);
    SSJavaLattice<String> lattice = ssjava.getClassLattice((ClassDescriptor) d);
    if (!lattice.containsKey(fieldName)) {
      throw new Error("The location " + fieldName + " is not defined in the field lattice of '"
          + className + "'.");
    }

    return new Location(d, fieldName);
  }

  private CompositeLocation parseLocationDeclaration(MethodDescriptor md, TreeNode n, String locDec) {

    CompositeLocation compLoc = new CompositeLocation();

    StringTokenizer tokenizer = new StringTokenizer(locDec, ",");
    List<String> locIdList = new ArrayList<String>();
    while (tokenizer.hasMoreTokens()) {
      String locId = tokenizer.nextToken();
      locIdList.add(locId);
    }

    // at least,one location element needs to be here!
    assert (locIdList.size() > 0);

    // assume that loc with idx 0 comes from the local lattice
    // loc with idx 1 comes from the field lattice

    String localLocId = locIdList.get(0);
    SSJavaLattice<String> localLattice = CompositeLattice.getLatticeByDescriptor(md);
    Location localLoc = new Location(md, localLocId);
    if (localLattice == null || (!localLattice.containsKey(localLocId))) {
      throw new Error("Location " + localLocId
          + " is not defined in the local variable lattice at "
          + md.getClassDesc().getSourceFileName() + "::" + (n != null ? n.getNumLine() : "") + ".");
    }
    compLoc.addLocation(localLoc);

    for (int i = 1; i < locIdList.size(); i++) {
      String locName = locIdList.get(i);

      Location fieldLoc = parseFieldLocDeclaraton(locName);
      // ClassDescriptor cd = fieldLocName2cd.get(locName);
      // SSJavaLattice<String> fieldLattice =
      // CompositeLattice.getLatticeByDescriptor(cd);
      //
      // if (fieldLattice == null || (!fieldLattice.containsKey(locName))) {
      // throw new Error("Location " + locName +
      // " is not defined in the field lattice at "
      // + cd.getSourceFileName() + ".");
      // }
      // Location fieldLoc = new Location(cd, locName);
      compLoc.addLocation(fieldLoc);
    }

    return compLoc;

  }

  private void checkDeclarationNode(MethodDescriptor md, SymbolTable nametable, DeclarationNode dn) {
    VarDescriptor vd = dn.getVarDescriptor();
    assignLocationOfVarDescriptor(vd, md, nametable, dn);
  }

  private void checkClass(ClassDescriptor cd) {
    // Check to see that methods respects ss property
    for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) method_it.next();
      checkMethodDeclaration(cd, md);
    }
  }

  private void checkDeclarationInClass(ClassDescriptor cd) {
    // Check to see that fields are okay
    for (Iterator field_it = cd.getFields(); field_it.hasNext();) {
      FieldDescriptor fd = (FieldDescriptor) field_it.next();

      if (!(fd.isFinal() && fd.isStatic())) {
        checkFieldDeclaration(cd, fd);
      }
    }
  }

  private void checkMethodDeclaration(ClassDescriptor cd, MethodDescriptor md) {
    // TODO
  }

  private void checkFieldDeclaration(ClassDescriptor cd, FieldDescriptor fd) {

    Vector<AnnotationDescriptor> annotationVec = fd.getType().getAnnotationMarkers();

    // currently enforce every field to have corresponding location
    if (annotationVec.size() == 0) {
      throw new Error("Location is not assigned to the field '" + fd.getSymbol()
          + "' of the class " + cd.getSymbol() + " at " + cd.getSourceFileName());
    }

    if (annotationVec.size() > 1) {
      // variable can have at most one location
      throw new Error("Field " + fd.getSymbol() + " of class " + cd
          + " has more than one location.");
    }

    AnnotationDescriptor ad = annotationVec.elementAt(0);

    if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {

      if (ad.getMarker().equals(SSJavaAnalysis.LOC)) {
        String locationID = ad.getValue();
        // check if location is defined
        SSJavaLattice<String> lattice = ssjava.getClassLattice(cd);
        if (lattice == null || (!lattice.containsKey(locationID))) {
          throw new Error("Location " + locationID
              + " is not defined in the field lattice of class " + cd.getSymbol() + " at"
              + cd.getSourceFileName() + ".");
        }
        Location loc = new Location(cd, locationID);

        if (ssjava.isSharedLocation(loc)) {
          ssjava.mapSharedLocation2Descriptor(loc, fd);
        }

        addLocationType(fd.getType(), loc);

      }
    }

  }

  private void addLocationType(TypeDescriptor type, CompositeLocation loc) {
    if (type != null) {
      type.setExtension(loc);
    }
  }

  private void addLocationType(TypeDescriptor type, Location loc) {
    if (type != null) {
      type.setExtension(loc);
    }
  }

  static class CompositeLattice {

    public static boolean isGreaterThan(CompositeLocation loc1, CompositeLocation loc2) {

      int baseCompareResult = compareBaseLocationSet(loc1, loc2, true);
      if (baseCompareResult == ComparisonResult.EQUAL) {
        if (compareDelta(loc1, loc2) == ComparisonResult.GREATER) {
          return true;
        } else {
          return false;
        }
      } else if (baseCompareResult == ComparisonResult.GREATER) {
        return true;
      } else {
        return false;
      }

    }

    public static int compare(CompositeLocation loc1, CompositeLocation loc2) {

      // System.out.println("compare=" + loc1 + " " + loc2);
      int baseCompareResult = compareBaseLocationSet(loc1, loc2, false);

      if (baseCompareResult == ComparisonResult.EQUAL) {
        return compareDelta(loc1, loc2);
      } else {
        return baseCompareResult;
      }

    }

    private static int compareDelta(CompositeLocation dLoc1, CompositeLocation dLoc2) {

      int deltaCount1 = 0;
      int deltaCount2 = 0;
      if (dLoc1 instanceof DeltaLocation) {
        deltaCount1 = ((DeltaLocation) dLoc1).getNumDelta();
      }

      if (dLoc2 instanceof DeltaLocation) {
        deltaCount2 = ((DeltaLocation) dLoc2).getNumDelta();
      }
      if (deltaCount1 < deltaCount2) {
        return ComparisonResult.GREATER;
      } else if (deltaCount1 == deltaCount2) {
        return ComparisonResult.EQUAL;
      } else {
        return ComparisonResult.LESS;
      }

    }

    private static int compareBaseLocationSet(CompositeLocation compLoc1,
        CompositeLocation compLoc2, boolean awareSharedLoc) {

      // if compLoc1 is greater than compLoc2, return true
      // else return false;

      // compare one by one in according to the order of the tuple
      int numOfTie = 0;
      for (int i = 0; i < compLoc1.getSize(); i++) {
        Location loc1 = compLoc1.get(i);
        if (i >= compLoc2.getSize()) {
          throw new Error("Failed to compare two locations of " + compLoc1 + " and " + compLoc2
              + " because they are not comparable.");
        }
        Location loc2 = compLoc2.get(i);

        if (!loc1.getDescriptor().equals(loc2.getDescriptor())) {
          throw new Error("Failed to compare two locations of " + compLoc1 + " and " + compLoc2
              + " because they are not comparable.");
        }

        Descriptor d1 = loc1.getDescriptor();
        Descriptor d2 = loc2.getDescriptor();

        SSJavaLattice<String> lattice1 = getLatticeByDescriptor(d1);
        SSJavaLattice<String> lattice2 = getLatticeByDescriptor(d2);

        // check if the spin location is appeared only at the end of the
        // composite location
        if (lattice1.getSpinLocSet().contains(loc1.getLocIdentifier())) {
          if (i != (compLoc1.getSize() - 1)) {
            throw new Error("The spin location " + loc1.getLocIdentifier()
                + " cannot be appeared in the middle of composite location.");
          }
        }

        if (lattice2.getSpinLocSet().contains(loc2.getLocIdentifier())) {
          if (i != (compLoc2.getSize() - 1)) {
            throw new Error("The spin location " + loc2.getLocIdentifier()
                + " cannot be appeared in the middle of composite location.");
          }
        }

        if (!lattice1.equals(lattice2)) {
          throw new Error("Failed to compare two locations of " + compLoc1 + " and " + compLoc2
              + " because they are not comparable.");
        }

        if (loc1.getLocIdentifier().equals(loc2.getLocIdentifier())) {
          numOfTie++;
          // check if the current location is the spinning location
          // note that the spinning location only can be appeared in the last
          // part of the composite location
          if (awareSharedLoc && numOfTie == compLoc1.getSize()
              && lattice1.getSpinLocSet().contains(loc1.getLocIdentifier())) {
            return ComparisonResult.GREATER;
          }
          continue;
        } else if (lattice1.isGreaterThan(loc1.getLocIdentifier(), loc2.getLocIdentifier())) {
          return ComparisonResult.GREATER;
        } else {
          return ComparisonResult.LESS;
        }

      }

      if (numOfTie == compLoc1.getSize()) {

        if (numOfTie != compLoc2.getSize()) {
          throw new Error("Failed to compare two locations of " + compLoc1 + " and " + compLoc2
              + " because they are not comparable.");
        }

        return ComparisonResult.EQUAL;
      }

      return ComparisonResult.LESS;

    }

    public static CompositeLocation calculateGLB(Set<CompositeLocation> inputSet) {

      // System.out.println("Calculating GLB=" + inputSet);
      CompositeLocation glbCompLoc = new CompositeLocation();

      // calculate GLB of the first(priority) element
      Set<String> priorityLocIdentifierSet = new HashSet<String>();
      Descriptor priorityDescriptor = null;

      Hashtable<String, Set<CompositeLocation>> locId2CompLocSet =
          new Hashtable<String, Set<CompositeLocation>>();
      // mapping from the priority loc ID to its full representation by the
      // composite location

      int maxTupleSize = 0;
      CompositeLocation maxCompLoc = null;

      for (Iterator iterator = inputSet.iterator(); iterator.hasNext();) {
        CompositeLocation compLoc = (CompositeLocation) iterator.next();
        if (compLoc.getSize() > maxTupleSize) {
          maxTupleSize = compLoc.getSize();
          maxCompLoc = compLoc;
        }
        Location priorityLoc = compLoc.get(0);
        String priorityLocId = priorityLoc.getLocIdentifier();
        priorityLocIdentifierSet.add(priorityLocId);

        if (locId2CompLocSet.containsKey(priorityLocId)) {
          locId2CompLocSet.get(priorityLocId).add(compLoc);
        } else {
          Set<CompositeLocation> newSet = new HashSet<CompositeLocation>();
          newSet.add(compLoc);
          locId2CompLocSet.put(priorityLocId, newSet);
        }

        // check if priority location are coming from the same lattice
        if (priorityDescriptor == null) {
          priorityDescriptor = priorityLoc.getDescriptor();
        } else if (!priorityDescriptor.equals(priorityLoc.getDescriptor())) {
          throw new Error("Failed to calculate GLB of " + inputSet
              + " because they are from different lattices.");
        }
      }

      SSJavaLattice<String> locOrder = getLatticeByDescriptor(priorityDescriptor);
      String glbOfPriorityLoc = locOrder.getGLB(priorityLocIdentifierSet);

      glbCompLoc.addLocation(new Location(priorityDescriptor, glbOfPriorityLoc));
      Set<CompositeLocation> compSet = locId2CompLocSet.get(glbOfPriorityLoc);

      // here find out composite location that has a maximum length tuple
      // if we have three input set: [A], [A,B], [A,B,C]
      // maximum length tuple will be [A,B,C]
      int max = 0;
      CompositeLocation maxFromCompSet = null;
      for (Iterator iterator = compSet.iterator(); iterator.hasNext();) {
        CompositeLocation c = (CompositeLocation) iterator.next();
        if (c.getSize() > max) {
          max = c.getSize();
          maxFromCompSet = c;
        }
      }

      if (compSet == null) {
        // when GLB(x1,x2)!=x1 and !=x2 : GLB case 4
        // mean that the result is already lower than <x1,y1> and <x2,y2>
        // assign TOP to the rest of the location elements

        // in this case, do not take care about delta
        // CompositeLocation inputComp = inputSet.iterator().next();
        CompositeLocation inputComp = maxCompLoc;
        for (int i = 1; i < inputComp.getSize(); i++) {
          glbCompLoc.addLocation(Location.createTopLocation(inputComp.get(i).getDescriptor()));
        }
      } else {
        if (compSet.size() == 1) {

          // if GLB(x1,x2)==x1 or x2 : GLB case 2,3
          CompositeLocation comp = compSet.iterator().next();
          for (int i = 1; i < comp.getSize(); i++) {
            glbCompLoc.addLocation(comp.get(i));
          }

          // if input location corresponding to glb is a delta, need to apply
          // delta to glb result
          if (comp instanceof DeltaLocation) {
            glbCompLoc = new DeltaLocation(glbCompLoc, 1);
          }

        } else {
          // when GLB(x1,x2)==x1 and x2 : GLB case 1
          // if more than one location shares the same priority GLB
          // need to calculate the rest of GLB loc

          // int compositeLocSize = compSet.iterator().next().getSize();
          int compositeLocSize = maxFromCompSet.getSize();

          Set<String> glbInputSet = new HashSet<String>();
          Descriptor currentD = null;
          for (int i = 1; i < compositeLocSize; i++) {
            for (Iterator iterator = compSet.iterator(); iterator.hasNext();) {
              CompositeLocation compositeLocation = (CompositeLocation) iterator.next();
              if (compositeLocation.getSize() > i) {
                Location currentLoc = compositeLocation.get(i);
                currentD = currentLoc.getDescriptor();
                // making set of the current location sharing the same idx
                glbInputSet.add(currentLoc.getLocIdentifier());
              }
            }
            // calculate glb for the current lattice

            SSJavaLattice<String> currentLattice = getLatticeByDescriptor(currentD);
            String currentGLBLocId = currentLattice.getGLB(glbInputSet);
            glbCompLoc.addLocation(new Location(currentD, currentGLBLocId));
          }

          // if input location corresponding to glb is a delta, need to apply
          // delta to glb result

          for (Iterator iterator = compSet.iterator(); iterator.hasNext();) {
            CompositeLocation compLoc = (CompositeLocation) iterator.next();
            if (compLoc instanceof DeltaLocation) {
              if (glbCompLoc.equals(compLoc)) {
                glbCompLoc = new DeltaLocation(glbCompLoc, 1);
                break;
              }
            }
          }

        }
      }

      return glbCompLoc;

    }

    static SSJavaLattice<String> getLatticeByDescriptor(Descriptor d) {

      SSJavaLattice<String> lattice = null;

      if (d instanceof ClassDescriptor) {
        lattice = ssjava.getCd2lattice().get(d);
      } else if (d instanceof MethodDescriptor) {
        if (ssjava.getMd2lattice().containsKey(d)) {
          lattice = ssjava.getMd2lattice().get(d);
        } else {
          // use default lattice for the method
          lattice = ssjava.getCd2methodDefault().get(((MethodDescriptor) d).getClassDesc());
        }
      }

      return lattice;
    }

  }

  class ComparisonResult {

    public static final int GREATER = 0;
    public static final int EQUAL = 1;
    public static final int LESS = 2;
    public static final int INCOMPARABLE = 3;
    int result;

  }

}

class ReturnLocGenerator {

  public static final int PARAMISHIGHER = 0;
  public static final int PARAMISSAME = 1;
  public static final int IGNORE = 2;

  Hashtable<Integer, Integer> paramIdx2paramType;

  public ReturnLocGenerator(CompositeLocation returnLoc, List<CompositeLocation> params) {
    // creating mappings

    paramIdx2paramType = new Hashtable<Integer, Integer>();
    for (int i = 0; i < params.size(); i++) {
      CompositeLocation param = params.get(i);
      int compareResult = CompositeLattice.compare(param, returnLoc);

      int type;
      if (compareResult == ComparisonResult.GREATER) {
        type = 0;
      } else if (compareResult == ComparisonResult.EQUAL) {
        type = 1;
      } else {
        type = 2;
      }
      paramIdx2paramType.put(new Integer(i), new Integer(type));
    }

  }

  public CompositeLocation computeReturnLocation(List<CompositeLocation> args) {

    // compute the highest possible location in caller's side
    assert paramIdx2paramType.keySet().size() == args.size();

    Set<CompositeLocation> inputGLB = new HashSet<CompositeLocation>();
    for (int i = 0; i < args.size(); i++) {
      int type = (paramIdx2paramType.get(new Integer(i))).intValue();
      CompositeLocation argLoc = args.get(i);
      if (type == PARAMISHIGHER) {
        // return loc is lower than param
        DeltaLocation delta = new DeltaLocation(argLoc, 1);
        inputGLB.add(delta);
      } else if (type == PARAMISSAME) {
        // return loc is equal or lower than param
        inputGLB.add(argLoc);
      }
    }

    // compute GLB of arguments subset that are same or higher than return
    // location
    CompositeLocation glb = CompositeLattice.calculateGLB(inputGLB);
    return glb;
  }
}
