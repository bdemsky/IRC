package Analysis.SSJava;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

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
import Util.Lattice;

public class FlowDownCheck {

  static State state;
  HashSet toanalyze;
  Hashtable<Descriptor, Location> td2loc; // mapping from 'type descriptor'
                                          // to 'location'
  Hashtable<String, ClassDescriptor> id2cd; // mapping from 'locID' to 'class
                                            // descriptor'

  public FlowDownCheck(State state) {
    this.state = state;
    this.toanalyze = new HashSet();
    this.td2loc = new Hashtable<Descriptor, Location>();
    init();
  }

  public void init() {
    id2cd = new Hashtable<String, ClassDescriptor>();
    Hashtable cd2lattice = state.getCd2LocationOrder();

    Set cdSet = cd2lattice.keySet();
    for (Iterator iterator = cdSet.iterator(); iterator.hasNext();) {
      ClassDescriptor cd = (ClassDescriptor) iterator.next();
      Lattice<String> lattice = (Lattice<String>) cd2lattice.get(cd);

      Set<String> locIdSet = lattice.getKeySet();
      for (Iterator iterator2 = locIdSet.iterator(); iterator2.hasNext();) {
        String locID = (String) iterator2.next();
        id2cd.put(locID, cd);
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

      if (!cd.isInterface()) {
        checkDeclarationInClass(cd);
        for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) method_it.next();
          try {
            checkDeclarationInMethodBody(cd, md);
          } catch (Error e) {
            System.out.println("Error in " + md);
            throw e;
          }
        }
      }

    }

    // post-processing for delta location
    // for a nested delta location, assigning a concrete reference to delta
    // operand
    Set<Descriptor> tdSet = td2loc.keySet();
    for (Iterator iterator = tdSet.iterator(); iterator.hasNext();) {
      Descriptor td = (Descriptor) iterator.next();
      Location loc = td2loc.get(td);

      if (loc.getType() == Location.DELTA) {
        // if it contains delta reference pointing to another location element
        CompositeLocation compLoc = (CompositeLocation) loc;

        Location locElement = compLoc.getTuple().at(0);
        assert (locElement instanceof DeltaLocation);

        DeltaLocation delta = (DeltaLocation) locElement;
        Descriptor refType = delta.getRefLocationId();
        if (refType != null) {
          Location refLoc = td2loc.get(refType);

          assert (refLoc instanceof CompositeLocation);
          CompositeLocation refCompLoc = (CompositeLocation) refLoc;

          assert (refCompLoc.getTuple().at(0) instanceof DeltaLocation);
          DeltaLocation refDelta = (DeltaLocation) refCompLoc.getTuple().at(0);

          delta.addDeltaOperand(refDelta);
          // compLoc.addLocation(refDelta);
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
        try {
          checkMethodBody(cd, md);
        } catch (Error e) {
          System.out.println("Error in " + md);
          throw e;
        }
      }
    }

  }

  public Hashtable getMap() {
    return td2loc;
  }

  private void checkDeclarationInMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    for (int i = 0; i < md.numParameters(); i++) {
      // process annotations on method parameters
      VarDescriptor vd = (VarDescriptor) md.getParameter(i);
      assignLocationOfVarDescriptor(vd, md, md.getParameterTable(), bn);
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

      if (lowestLoc == null) {
        lowestLoc = bLoc;
      } else {
        if (CompositeLattice.isGreaterThan(lowestLoc, bLoc, md.getClassDesc())) {
          lowestLoc = bLoc;
        }
      }
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

    // case Kind.ContinueBreakNode:
    // checkLocationFromContinueBreakNode(md, nametable,(ContinueBreakNode)
    // bsn);
    // return null;
    }
    return compLoc;
  }

  private CompositeLocation checkLocationFromReturnNode(MethodDescriptor md, SymbolTable nametable,
      ReturnNode rn) {
    ClassDescriptor cd = md.getClassDesc();
    CompositeLocation loc = new CompositeLocation(cd);

    ExpressionNode returnExp = rn.getReturnExpression();

    if (rn == null || hasOnlyLiteralValue(returnExp)) {
      // when it returns literal value, return node has "bottom" location no
      // matter what it is going to return.
      loc.addLocation(Location.createBottomLocation(cd));
    } else {
      loc = checkLocationFromExpressionNode(md, nametable, returnExp, loc);
    }
    return loc;
  }

  private boolean hasOnlyLiteralValue(ExpressionNode returnExp) {
    if (returnExp.kind() == Kind.LiteralNode) {
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
          checkLocationFromExpressionNode(md, nametable, ln.getCondition(), new CompositeLocation(
              cd));
      addTypeLocation(ln.getCondition().getType(), (condLoc));

      CompositeLocation bodyLoc = checkLocationFromBlockNode(md, nametable, ln.getBody());

      if (!CompositeLattice.isGreaterThan(condLoc, bodyLoc, cd)) {
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
              new CompositeLocation(cd));
      addTypeLocation(ln.getCondition().getType(), condLoc);

      CompositeLocation updateLoc =
          checkLocationFromBlockNode(md, bn.getVarTable(), ln.getUpdate());

      Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();
      glbInputSet.add(condLoc);
      glbInputSet.add(updateLoc);

      CompositeLocation glbLocOfForLoopCond = CompositeLattice.calculateGLB(cd, glbInputSet, cd);

      // check location of 'forloop' body
      CompositeLocation blockLoc = checkLocationFromBlockNode(md, bn.getVarTable(), ln.getBody());

      if (blockLoc == null) {
        // when there is no statement in the loop body
        return glbLocOfForLoopCond;
      }

      if (!CompositeLattice.isGreaterThan(glbLocOfForLoopCond, blockLoc, cd)) {
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
        checkLocationFromExpressionNode(md, nametable, isn.getCondition(), new CompositeLocation(
            localCD));
    addTypeLocation(isn.getCondition().getType(), condLoc);
    glbInputSet.add(condLoc);

    CompositeLocation locTrueBlock = checkLocationFromBlockNode(md, nametable, isn.getTrueBlock());
    glbInputSet.add(locTrueBlock);

    // here, the location of conditional block should be higher than the
    // location of true/false blocks

    if (!CompositeLattice.isGreaterThan(condLoc, locTrueBlock, localCD)) {
      // error
      throw new Error(
          "The location of the if-condition statement is lower than the conditional block at "
              + localCD.getSourceFileName() + ":" + isn.getCondition().getNumLine());
    }

    if (isn.getFalseBlock() != null) {
      CompositeLocation locFalseBlock =
          checkLocationFromBlockNode(md, nametable, isn.getFalseBlock());
      glbInputSet.add(locFalseBlock);

      if (!CompositeLattice.isGreaterThan(condLoc, locFalseBlock, localCD)) {
        // error
        throw new Error(
            "The location of the if-condition statement is lower than the conditional block at "
                + localCD.getSourceFileName() + ":" + isn.getCondition().getNumLine());
      }

    }

    // return GLB location of condition, true, and false block
    CompositeLocation glbLoc = CompositeLattice.calculateGLB(localCD, glbInputSet, localCD);

    return glbLoc;
  }

  private CompositeLocation checkLocationFromDeclarationNode(MethodDescriptor md,
      SymbolTable nametable, DeclarationNode dn) {
    VarDescriptor vd = dn.getVarDescriptor();

    Location destLoc = td2loc.get(vd);

    ClassDescriptor localCD = md.getClassDesc();
    if (dn.getExpression() != null) {
      CompositeLocation expressionLoc =
          checkLocationFromExpressionNode(md, nametable, dn.getExpression(), new CompositeLocation(
              localCD));
      addTypeLocation(dn.getExpression().getType(), expressionLoc);

      if (expressionLoc != null) {
        // checking location order
        if (!CompositeLattice.isGreaterThan(expressionLoc, destLoc, localCD)) {
          throw new Error("The value flow from " + expressionLoc + " to " + destLoc
              + " does not respect location hierarchy on the assignment " + dn.printNode(0)
              + " at " + md.getClassDesc().getSourceFileName() + "::" + dn.getNumLine());
        }
      }
      return expressionLoc;

    } else {

      if (destLoc instanceof Location) {
        CompositeLocation comp = new CompositeLocation(localCD);
        comp.addLocation(destLoc);
        return comp;
      } else {
        return (CompositeLocation) destLoc;
      }

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
    addTypeLocation(ben.getExpression().getType(), compLoc);
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
      compLoc = checkLocationFromMethodInvokeNode(md, nametable, (MethodInvokeNode) en);
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

    addTypeLocation(en.getType(), compLoc);
    return compLoc;

  }

  private CompositeLocation checkLocationFromCastNode(MethodDescriptor md, SymbolTable nametable,
      CastNode cn) {

    ExpressionNode en = cn.getExpression();
    return checkLocationFromExpressionNode(md, nametable, en,
        new CompositeLocation(md.getClassDesc()));

  }

  private CompositeLocation checkLocationFromTertiaryNode(MethodDescriptor md,
      SymbolTable nametable, TertiaryNode tn) {
    ClassDescriptor cd = md.getClassDesc();

    CompositeLocation condLoc =
        checkLocationFromExpressionNode(md, nametable, tn.getCond(), new CompositeLocation(cd));
    addTypeLocation(tn.getCond().getType(), condLoc);
    CompositeLocation trueLoc =
        checkLocationFromExpressionNode(md, nametable, tn.getTrueExpr(), new CompositeLocation(cd));
    addTypeLocation(tn.getTrueExpr().getType(), trueLoc);
    CompositeLocation falseLoc =
        checkLocationFromExpressionNode(md, nametable, tn.getFalseExpr(), new CompositeLocation(cd));
    addTypeLocation(tn.getFalseExpr().getType(), falseLoc);

    // check if condLoc is higher than trueLoc & falseLoc
    if (!CompositeLattice.isGreaterThan(condLoc, trueLoc, cd)) {
      throw new Error(
          "The location of the condition expression is lower than the true expression at "
              + cd.getSourceFileName() + ":" + tn.getCond().getNumLine());
    }

    if (!CompositeLattice.isGreaterThan(condLoc, falseLoc, cd)) {
      throw new Error(
          "The location of the condition expression is lower than the true expression at "
              + cd.getSourceFileName() + ":" + tn.getCond().getNumLine());
    }

    // then, return glb of trueLoc & falseLoc
    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();
    glbInputSet.add(trueLoc);
    glbInputSet.add(falseLoc);

    return CompositeLattice.calculateGLB(cd, glbInputSet, cd);
  }

  private CompositeLocation convertCompositeLocation(Location l, ClassDescriptor c) {
    if (l instanceof CompositeLocation) {
      return (CompositeLocation) l;
    } else {
      CompositeLocation returnLoc = new CompositeLocation(c);
      returnLoc.addLocation(l);
      return returnLoc;
    }
  }

  private CompositeLocation checkLocationFromMethodInvokeNode(MethodDescriptor md,
      SymbolTable nametable, MethodInvokeNode min) {

    ClassDescriptor cd = md.getClassDesc();

    CompositeLocation baseLoc = null;
    if (min.getBaseName() != null) {
      if (nametable.contains(min.getBaseName().getSymbol())) {
        Location loc = td2loc.get(nametable.get(min.getBaseName().getSymbol()));
        if (loc != null) {
          baseLoc = convertCompositeLocation(loc, cd);
        }
      }
    }

    Set<CompositeLocation> inputGLBSet = new HashSet<CompositeLocation>();
    for (int i = 0; i < min.numArgs(); i++) {
      ExpressionNode en = min.getArg(i);
      CompositeLocation callerArg =
          checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation(cd));
      inputGLBSet.add(callerArg);
    }

    // ex) base.method(arg1,arg2,arg3) -> the location of base should be lower
    // than
    // GLB(arg1,arg2,arg3)
    CompositeLocation argGLBLoc = null;
    if (inputGLBSet.size() > 0) {
      argGLBLoc = CompositeLattice.calculateGLB(cd, inputGLBSet, cd);
      if (baseLoc != null) {
        if (!CompositeLattice.isGreaterThan(argGLBLoc, baseLoc, cd)) {
          throw new Error("The base location of method invocation " + min.printNode(0)
              + " is higher than its argument's location at " + cd.getSourceFileName() + "::"
              + min.getNumLine());
        }
      }
    }

    if (min.numArgs() > 1) {

      // caller needs to guarantee that it passes arguments in regarding to
      // callee's hierarchy

      for (int i = 0; i < min.numArgs(); i++) {
        ExpressionNode en = min.getArg(i);
        CompositeLocation callerArg1 =
            checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation(cd));

        ClassDescriptor calleecd = min.getMethod().getClassDesc();
        VarDescriptor calleevd = (VarDescriptor) min.getMethod().getParameter(i);
        Location calleeLoc1 = td2loc.get(calleevd);

        if (!callerArg1.getLocation(cd).isTop()) {
          // here, check if ordering relations among caller's args respect
          // ordering relations in-between callee's args
          for (int currentIdx = 0; currentIdx < min.numArgs(); currentIdx++) {
            if (currentIdx != i) { // skip itself
              ExpressionNode argExp = min.getArg(currentIdx);
              CompositeLocation callerArg2 =
                  checkLocationFromExpressionNode(md, nametable, argExp, new CompositeLocation(cd));

              VarDescriptor calleevd2 = (VarDescriptor) min.getMethod().getParameter(currentIdx);
              Location calleeLoc2 = td2loc.get(calleevd2);
              boolean callerResult = CompositeLattice.isGreaterThan(callerArg1, callerArg2, cd);
              boolean calleeResult =
                  CompositeLattice.isGreaterThan(calleeLoc1, calleeLoc2, calleecd);

              if (calleeResult && !callerResult) {
                // in callee, calleeLoc1 is higher than calleeLoc2
                // then, caller should have same ordering relation in-bet
                // callerLoc1 & callerLoc2

                throw new Error("Caller doesn't respect ordering relations among method arguments:"
                    + cd.getSourceFileName() + ":" + min.getNumLine());
              }

            }
          }
        }

      }

    }

    // all arguments should be higher than the location of return value

    if (inputGLBSet.size() > 0) {
      if (baseLoc != null) {
        inputGLBSet.add(baseLoc);
        return CompositeLattice.calculateGLB(cd, inputGLBSet, cd);
      } else {
        return argGLBLoc;
      }
    } else {
      // if there are no arguments,
      if (baseLoc != null) {
        return baseLoc;
      } else {
        // method invocation from the same class
        CompositeLocation returnLoc = new CompositeLocation(cd);
        returnLoc.addLocation(Location.createTopLocation(cd));
        return returnLoc;
      }
    }
  }

  private CompositeLocation checkLocationFromArrayAccessNode(MethodDescriptor md,
      SymbolTable nametable, ArrayAccessNode aan) {

    // return glb location of array itself and index

    ClassDescriptor cd = md.getClassDesc();

    Set<CompositeLocation> glbInputSet = new HashSet<CompositeLocation>();

    CompositeLocation arrayLoc =
        checkLocationFromExpressionNode(md, nametable, aan.getExpression(), new CompositeLocation(
            cd));
    addTypeLocation(aan.getExpression().getType(), arrayLoc);
    glbInputSet.add(arrayLoc);
    CompositeLocation indexLoc =
        checkLocationFromExpressionNode(md, nametable, aan.getIndex(), new CompositeLocation(cd));
    glbInputSet.add(indexLoc);
    addTypeLocation(aan.getIndex().getType(), indexLoc);

    CompositeLocation glbLoc = CompositeLattice.calculateGLB(cd, glbInputSet, cd);
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
          checkLocationFromExpressionNode(md, nametable, en, new CompositeLocation(cd));
      glbInputSet.add(argLoc);
      addTypeLocation(en.getType(), argLoc);
    }

    // check array initializers
    // if ((con.getArrayInitializer() != null)) {
    // checkLocationFromArrayInitializerNode(md, nametable,
    // con.getArrayInitializer());
    // }

    if (glbInputSet.size() > 0) {
      return CompositeLattice.calculateGLB(cd, glbInputSet, cd);
    }

    CompositeLocation compLoc = new CompositeLocation(cd);
    compLoc.addLocation(Location.createTopLocation(cd));
    return compLoc;

  }

  private CompositeLocation checkLocationFromOpNode(MethodDescriptor md, SymbolTable nametable,
      OpNode on) {

    ClassDescriptor cd = md.getClassDesc();
    CompositeLocation leftLoc = new CompositeLocation(cd);
    leftLoc = checkLocationFromExpressionNode(md, nametable, on.getLeft(), leftLoc);
    addTypeLocation(on.getLeft().getType(), leftLoc);

    CompositeLocation rightLoc = new CompositeLocation(cd);
    if (on.getRight() != null) {
      rightLoc = checkLocationFromExpressionNode(md, nametable, on.getRight(), rightLoc);
      addTypeLocation(on.getRight().getType(), rightLoc);
    }

    // System.out.println("checking op node=" + on.printNode(0));
    // System.out.println("left loc=" + leftLoc + " from " +
    // on.getLeft().getClass());
    // System.out.println("right loc=" + rightLoc + " from " +
    // on.getLeft().getClass());

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
      CompositeLocation glbCompLoc = CompositeLattice.calculateGLB(cd, inputSet, cd);
      return glbCompLoc;

    default:
      throw new Error(op.toString());
    }

  }

  private CompositeLocation checkLocationFromLiteralNode(MethodDescriptor md,
      SymbolTable nametable, LiteralNode en, CompositeLocation loc) {

    // literal value has the top location so that value can be flowed into any
    // location
    Location literalLoc = Location.createTopLocation(md.getClassDesc());
    loc.addLocation(literalLoc);
    return loc;

  }

  private CompositeLocation checkLocationFromNameNode(MethodDescriptor md, SymbolTable nametable,
      NameNode nn, CompositeLocation loc) {

    NameDescriptor nd = nn.getName();
    if (nd.getBase() != null) {
      loc = checkLocationFromExpressionNode(md, nametable, nn.getExpression(), loc);
      addTypeLocation(nn.getExpression().getType(), loc);
    } else {

      String varname = nd.toString();
      if (varname.equals("this")) {
        // 'this' itself is top location in the local hierarchy
        loc.addLocation(Location.createTopLocation(md.getClassDesc()));
        return loc;
      }
      Descriptor d = (Descriptor) nametable.get(varname);

      Location localLoc = null;
      if (d instanceof VarDescriptor) {
        VarDescriptor vd = (VarDescriptor) d;
        localLoc = td2loc.get(vd);
      } else if (d instanceof FieldDescriptor) {
        FieldDescriptor fd = (FieldDescriptor) d;
        localLoc = td2loc.get(fd);
      }
      assert (localLoc != null);

      if (localLoc instanceof CompositeLocation) {
        loc = (CompositeLocation) localLoc;
      } else {
        loc.addLocation(localLoc);
      }
    }

    return loc;
  }

  private CompositeLocation checkLocationFromFieldAccessNode(MethodDescriptor md,
      SymbolTable nametable, FieldAccessNode fan, CompositeLocation loc) {

    ExpressionNode left = fan.getExpression();
    loc = checkLocationFromExpressionNode(md, nametable, left, loc);
    addTypeLocation(left.getType(), loc);

    if (!left.getType().isPrimitive()) {
      FieldDescriptor fd = fan.getField();
      Location fieldLoc = td2loc.get(fd);

      // in the case of "this.field", need to get rid of 'this' location from
      // the composite location
      if (loc.getCd2Loc().containsKey(md.getClassDesc())) {
        loc.removieLocation(md.getClassDesc());
      }
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
        checkLocationFromExpressionNode(md, nametable, an.getDest(), new CompositeLocation(cd));

    CompositeLocation srcLocation = new CompositeLocation(cd);
    if (!postinc) {
      srcLocation = new CompositeLocation(cd);
      srcLocation = checkLocationFromExpressionNode(md, nametable, an.getSrc(), srcLocation);
      // System.out.println("an=" + an.printNode(0) + " an.getSrc()=" +
      // an.getSrc().getClass()
      // + " at " + cd.getSourceFileName() + "::" + an.getNumLine());
      if (!CompositeLattice.isGreaterThan(srcLocation, destLocation, cd)) {
        throw new Error("The value flow from " + srcLocation + " to " + destLocation
            + " does not respect location hierarchy on the assignment " + an.printNode(0) + "at "
            + cd.getSourceFileName() + "::" + an.getNumLine());
      }
    } else {
      destLocation =
          srcLocation = checkLocationFromExpressionNode(md, nametable, an.getDest(), srcLocation);

      if (!((Set<String>) state.getCd2LocationPropertyMap().get(cd)).contains(destLocation
          .getLocation(cd).getLocIdentifier())) {
        throw new Error("Location " + destLocation + " is not allowed to have spinning values at "
            + cd.getSourceFileName() + ":" + an.getNumLine());
      }

    }
    if (an.getSrc() != null) {
      addTypeLocation(an.getSrc().getType(), srcLocation);
    }
    addTypeLocation(an.getDest().getType(), destLocation);

    return destLocation;
  }

  private void assignLocationOfVarDescriptor(VarDescriptor vd, MethodDescriptor md,
      SymbolTable nametable, TreeNode n) {

    ClassDescriptor cd = md.getClassDesc();
    Vector<AnnotationDescriptor> annotationVec = vd.getType().getAnnotationMarkers();

    // currently enforce every variable to have corresponding location
    if (annotationVec.size() == 0) {
      throw new Error("Location is not assigned to variable " + vd.getSymbol() + " in the method "
          + md.getSymbol() + " of the class " + cd.getSymbol());
    }

    if (annotationVec.size() > 1) {
      // variable can have at most one location
      throw new Error(vd.getSymbol() + " has more than one location.");
    }

    AnnotationDescriptor ad = annotationVec.elementAt(0);

    if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {

      if (ad.getMarker().equals(SSJavaAnalysis.LOC)) {
        String locationID = ad.getValue();
        // check if location is defined
        Lattice<String> lattice = (Lattice<String>) state.getCd2LocationOrder().get(cd);
        if (lattice == null || (!lattice.containsKey(locationID))) {
          throw new Error("Location " + locationID
              + " is not defined in the location hierarchy of class " + cd.getSymbol() + ".");
        }
        Location loc = new Location(cd, locationID);
        td2loc.put(vd, loc);
        addTypeLocation(vd.getType(), loc);

      } else if (ad.getMarker().equals(SSJavaAnalysis.DELTA)) {

        CompositeLocation compLoc = new CompositeLocation(cd);

        if (ad.getValue().length() == 0) {
          throw new Error("Delta function of " + vd.getSymbol() + " does not have any locations: "
              + cd.getSymbol() + ".");
        }

        String deltaStr = ad.getValue();
        if (deltaStr.startsWith("LOC(")) {

          if (!deltaStr.endsWith(")")) {
            throw new Error("The declaration of the delta location is wrong at "
                + cd.getSourceFileName() + ":" + n.getNumLine());
          }
          String locationOperand = deltaStr.substring(4, deltaStr.length() - 1);

          nametable.get(locationOperand);
          Descriptor d = (Descriptor) nametable.get(locationOperand);

          if (d instanceof VarDescriptor) {
            VarDescriptor varDescriptor = (VarDescriptor) d;
            DeltaLocation deltaLoc = new DeltaLocation(cd, varDescriptor);
            // td2loc.put(vd.getType(), compLoc);
            compLoc.addLocation(deltaLoc);
          } else if (d instanceof FieldDescriptor) {
            throw new Error("Applying delta operation to the field " + locationOperand
                + " is not allowed at " + cd.getSourceFileName() + ":" + n.getNumLine());
          }
        } else {
          StringTokenizer token = new StringTokenizer(deltaStr, ",");
          DeltaLocation deltaLoc = new DeltaLocation(cd);

          while (token.hasMoreTokens()) {
            String deltaOperand = token.nextToken();
            ClassDescriptor deltaCD = id2cd.get(deltaOperand);
            if (deltaCD == null) {
              // delta operand is not defined in the location hierarchy
              throw new Error("Delta operand '" + deltaOperand + "' of declaration node '" + vd
                  + "' is not defined by location hierarchies.");
            }

            Location loc = new Location(deltaCD, deltaOperand);
            deltaLoc.addDeltaOperand(loc);
          }
          compLoc.addLocation(deltaLoc);

        }

        td2loc.put(vd, compLoc);
        addTypeLocation(vd.getType(), compLoc);

      }
    }

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
      checkFieldDeclaration(cd, fd);
    }
  }

  private void checkMethodDeclaration(ClassDescriptor cd, MethodDescriptor md) {
    // TODO
  }

  private void checkFieldDeclaration(ClassDescriptor cd, FieldDescriptor fd) {

    Vector<AnnotationDescriptor> annotationVec = fd.getType().getAnnotationMarkers();

    // currently enforce every field to have corresponding location
    if (annotationVec.size() == 0) {
      throw new Error("Location is not assigned to the field " + fd.getSymbol() + " of the class "
          + cd.getSymbol());
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
        Lattice<String> lattice = (Lattice<String>) state.getCd2LocationOrder().get(cd);
        if (lattice == null || (!lattice.containsKey(locationID))) {
          throw new Error("Location " + locationID
              + " is not defined in the location hierarchy of class " + cd.getSymbol() + ".");
        }
        Location loc = new Location(cd, locationID);
        td2loc.put(fd, loc);
        addTypeLocation(fd.getType(), loc);

      } else if (ad.getMarker().equals(SSJavaAnalysis.DELTA)) {

        if (ad.getValue().length() == 0) {
          throw new Error("Delta function of " + fd.getSymbol() + " does not have any locations: "
              + cd.getSymbol() + ".");
        }

        CompositeLocation compLoc = new CompositeLocation(cd);
        DeltaLocation deltaLoc = new DeltaLocation(cd);

        StringTokenizer token = new StringTokenizer(ad.getValue(), ",");
        while (token.hasMoreTokens()) {
          String deltaOperand = token.nextToken();
          ClassDescriptor deltaCD = id2cd.get(deltaOperand);
          if (deltaCD == null) {
            // delta operand is not defined in the location hierarchy
            throw new Error("Delta operand '" + deltaOperand + "' of field node '" + fd
                + "' is not defined by location hierarchies.");
          }

          Location loc = new Location(deltaCD, deltaOperand);
          deltaLoc.addDeltaOperand(loc);
        }
        compLoc.addLocation(deltaLoc);
        td2loc.put(fd, compLoc);
        addTypeLocation(fd.getType(), compLoc);

      }
    }

  }

  private void addTypeLocation(TypeDescriptor type, Location loc) {
    if (type != null) {
      type.setExtension(loc);
    }
  }

  static class CompositeLattice {

    public static boolean isGreaterThan(Location loc1, Location loc2, ClassDescriptor priorityCD) {

      // System.out.println("\nisGreaterThan=" + loc1 + " ? " + loc2);
      CompositeLocation compLoc1;
      CompositeLocation compLoc2;

      if (loc1 instanceof CompositeLocation) {
        compLoc1 = (CompositeLocation) loc1;
      } else {
        // create a bogus composite location for a single location
        compLoc1 = new CompositeLocation(loc1.getClassDescriptor());
        compLoc1.addLocation(loc1);
      }

      if (loc2 instanceof CompositeLocation) {
        compLoc2 = (CompositeLocation) loc2;
      } else {
        // create a bogus composite location for a single location
        compLoc2 = new CompositeLocation(loc2.getClassDescriptor());
        compLoc2.addLocation(loc2);
      }

      // comparing two composite locations
      // System.out.println("compare base location=" + compLoc1 + " ? " +
      // compLoc2);

      int baseCompareResult = compareBaseLocationSet(compLoc1, compLoc2, priorityCD);
      if (baseCompareResult == ComparisonResult.EQUAL) {
        if (compareDelta(compLoc1, compLoc2) == ComparisonResult.GREATER) {
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

    private static int compareDelta(CompositeLocation compLoc1, CompositeLocation compLoc2) {
      if (compLoc1.getNumofDelta() < compLoc2.getNumofDelta()) {
        return ComparisonResult.GREATER;
      } else {
        return ComparisonResult.LESS;
      }
    }

    private static int compareBaseLocationSet(CompositeLocation compLoc1,
        CompositeLocation compLoc2, ClassDescriptor priorityCD) {

      // if compLoc1 is greater than compLoc2, return true
      // else return false;

      Map<ClassDescriptor, Location> cd2loc1 = compLoc1.getCd2Loc();
      Map<ClassDescriptor, Location> cd2loc2 = compLoc2.getCd2Loc();

      // compare first the priority loc elements
      Location priorityLoc1 = cd2loc1.get(priorityCD);
      Location priorityLoc2 = cd2loc2.get(priorityCD);

      assert (priorityLoc1.getClassDescriptor().equals(priorityLoc2.getClassDescriptor()));

      ClassDescriptor cd = priorityLoc1.getClassDescriptor();
      Lattice<String> locationOrder = (Lattice<String>) state.getCd2LocationOrder().get(cd);

      if (priorityLoc1.getLocIdentifier().equals(priorityLoc2.getLocIdentifier())) {
        // have the same level of local hierarchy

        Set<String> spinSet = (Set<String>) state.getCd2LocationPropertyMap().get(cd);
        if (spinSet != null && spinSet.contains(priorityLoc1.getLocIdentifier())) {
          // this location can be spinning
          return ComparisonResult.GREATER;
        }

      } else if (locationOrder.isGreaterThan(priorityLoc1.getLocIdentifier(),
          priorityLoc2.getLocIdentifier())) {
        // if priority loc of compLoc1 is higher than compLoc2
        // then, compLoc 1 is higher than compLoc2
        return ComparisonResult.GREATER;
      } else {
        // if priority loc of compLoc1 is NOT higher than compLoc2
        // then, compLoc 1 is NOT higher than compLoc2
        return ComparisonResult.LESS;
      }

      // compare base locations except priority by class descriptor
      Set<ClassDescriptor> keySet1 = cd2loc1.keySet();
      int numEqualLoc = 0;

      for (Iterator iterator = keySet1.iterator(); iterator.hasNext();) {
        ClassDescriptor cd1 = (ClassDescriptor) iterator.next();

        Location loc1 = cd2loc1.get(cd1);
        Location loc2 = cd2loc2.get(cd1);

        if (priorityLoc1.equals(loc1)) {
          continue;
        }

        if (loc2 == null) {
          // if comploc2 doesn't have corresponding location,
          // then we determines that comploc1 is lower than comploc 2
          return ComparisonResult.LESS;
        }

        System.out.println("lattice comparison:" + loc1.getLocIdentifier() + " ? "
            + loc2.getLocIdentifier());
        locationOrder = (Lattice<String>) state.getCd2LocationOrder().get(cd1);
        if (loc1.getLocIdentifier().equals(loc2.getLocIdentifier())) {
          // have the same level of local hierarchy
          numEqualLoc++;
        } else if (!locationOrder.isGreaterThan(loc1.getLocIdentifier(), loc2.getLocIdentifier())) {
          // if one element of composite location 1 is not higher than composite
          // location 2
          // then, composite loc 1 is not higher than composite loc 2

          System.out.println(compLoc1 + " < " + compLoc2);
          return ComparisonResult.LESS;
        }

      }

      if (numEqualLoc == (compLoc1.getBaseLocationSize() - 1)) {
        return ComparisonResult.EQUAL;
      }

      System.out.println(compLoc1 + " > " + compLoc2);
      return ComparisonResult.GREATER;
    }

    public static CompositeLocation calculateGLB(ClassDescriptor cd,
        Set<CompositeLocation> inputSet, ClassDescriptor priorityCD) {

      CompositeLocation glbCompLoc = new CompositeLocation(cd);
      int maxDeltaFunction = 0;

      // calculate GLB of priority element first

      Hashtable<ClassDescriptor, Set<Location>> cd2locSet =
          new Hashtable<ClassDescriptor, Set<Location>>();

      // creating mapping from class to set of locations
      for (Iterator iterator = inputSet.iterator(); iterator.hasNext();) {
        CompositeLocation compLoc = (CompositeLocation) iterator.next();

        int numOfDelta = compLoc.getNumofDelta();
        if (numOfDelta > maxDeltaFunction) {
          maxDeltaFunction = numOfDelta;
        }

        Set<Location> baseLocationSet = compLoc.getBaseLocationSet();
        for (Iterator iterator2 = baseLocationSet.iterator(); iterator2.hasNext();) {
          Location locElement = (Location) iterator2.next();
          ClassDescriptor locCD = locElement.getClassDescriptor();

          Set<Location> locSet = cd2locSet.get(locCD);
          if (locSet == null) {
            locSet = new HashSet<Location>();
          }
          locSet.add(locElement);

          cd2locSet.put(locCD, locSet);

        }
      }

      Set<Location> locSetofClass = cd2locSet.get(priorityCD);
      Set<String> locIdentifierSet = new HashSet<String>();

      for (Iterator<Location> locIterator = locSetofClass.iterator(); locIterator.hasNext();) {
        Location locElement = locIterator.next();
        locIdentifierSet.add(locElement.getLocIdentifier());
      }

      Lattice<String> locOrder = (Lattice<String>) state.getCd2LocationOrder().get(priorityCD);
      String glbLocIdentifer = locOrder.getGLB(locIdentifierSet);

      Location priorityGLB = new Location(priorityCD, glbLocIdentifer);

      Set<CompositeLocation> sameGLBLoc = new HashSet<CompositeLocation>();

      for (Iterator<CompositeLocation> iterator = inputSet.iterator(); iterator.hasNext();) {
        CompositeLocation inputComploc = iterator.next();
        Location locElement = inputComploc.getLocation(priorityCD);

        if (locElement.equals(priorityGLB)) {
          sameGLBLoc.add(inputComploc);
        }
      }
      glbCompLoc.addLocation(priorityGLB);
      if (sameGLBLoc.size() > 0) {
        // if more than one location shares the same priority GLB
        // need to calculate the rest of GLB loc

        Set<Location> glbElementSet = new HashSet<Location>();

        for (Iterator<ClassDescriptor> iterator = cd2locSet.keySet().iterator(); iterator.hasNext();) {
          ClassDescriptor localCD = iterator.next();
          if (!localCD.equals(priorityCD)) {
            Set<Location> localLocSet = cd2locSet.get(localCD);
            Set<String> LocalLocIdSet = new HashSet<String>();

            for (Iterator<Location> locIterator = localLocSet.iterator(); locIterator.hasNext();) {
              Location locElement = locIterator.next();
              LocalLocIdSet.add(locElement.getLocIdentifier());
            }

            Lattice<String> localOrder = (Lattice<String>) state.getCd2LocationOrder().get(localCD);
            Location localGLBLoc = new Location(localCD, localOrder.getGLB(LocalLocIdSet));
            glbCompLoc.addLocation(localGLBLoc);
          }
        }
      } else {
        // if priority glb loc is lower than all of input loc
        // assign top location to the rest of loc element

        for (Iterator<ClassDescriptor> iterator = cd2locSet.keySet().iterator(); iterator.hasNext();) {
          ClassDescriptor localCD = iterator.next();
          if (!localCD.equals(priorityCD)) {
            Location localGLBLoc = Location.createTopLocation(localCD);
            glbCompLoc.addLocation(localGLBLoc);
          }

        }

      }

      return glbCompLoc;

    }

  }

  class ComparisonResult {

    public static final int GREATER = 0;
    public static final int EQUAL = 1;
    public static final int LESS = 2;
    int result;

  }

}
