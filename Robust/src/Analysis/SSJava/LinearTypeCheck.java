package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import Analysis.Liveness;
import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.NameDescriptor;
import IR.Operation;
import IR.State;
import IR.SymbolTable;
import IR.TypeDescriptor;
import IR.VarDescriptor;
import IR.Flat.FKind;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.TempDescriptor;
import IR.Tree.ArrayAccessNode;
import IR.Tree.ArrayInitializerNode;
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
import IR.Tree.LoopNode;
import IR.Tree.MethodInvokeNode;
import IR.Tree.NameNode;
import IR.Tree.OffsetNode;
import IR.Tree.OpNode;
import IR.Tree.ReturnNode;
import IR.Tree.SubBlockNode;
import IR.Tree.SwitchBlockNode;
import IR.Tree.SwitchStatementNode;
import IR.Tree.SynchronizedNode;
import IR.Tree.TertiaryNode;
import IR.Tree.TreeNode;

public class LinearTypeCheck {

  State state;
  SSJavaAnalysis ssjava;
  String needToNullify = null;
  AssignmentNode prevAssignNode;

  Hashtable<MethodDescriptor, Set<VarDescriptor>> md2DelegateParamSet;

  Set<TreeNode> linearTypeCheckSet;

  Hashtable<TreeNode, FlatMethod> mapTreeNode2FlatMethod;

  Liveness liveness;

  public LinearTypeCheck(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.md2DelegateParamSet = new Hashtable<MethodDescriptor, Set<VarDescriptor>>();
    this.linearTypeCheckSet = new HashSet<TreeNode>();
    this.mapTreeNode2FlatMethod = new Hashtable<TreeNode, FlatMethod>();
    this.liveness = new Liveness();
  }

  public void linearTypeCheck() {

    // first, parsing DELEGATE annotation from method declarations
    Iterator it = state.getClassSymbolTable().getDescriptorsIterator();
    while (it.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor) it.next();
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        parseAnnotations(md);
      }
    }

    // second, check the linear type
    it = state.getClassSymbolTable().getDescriptorsIterator();
    while (it.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor) it.next();
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        checkMethodBody(cd, md);
      }
    }

    // third, check if original references are destroyed after creating new
    // alias

    for (Iterator<TreeNode> iterator = linearTypeCheckSet.iterator(); iterator.hasNext();) {
      TreeNode tn = iterator.next();
      Set<FlatNode> fnSet = ssjava.getBuildFlat().getFlatNodeSet(tn);
      if (fnSet != null) {
        for (Iterator iterator2 = fnSet.iterator(); iterator2.hasNext();) {
          FlatNode fn = (FlatNode) iterator2.next();
          if (isLiveOut(tn, fn)) {
            throw new Error(
                "Local variable '"
                    + tn.printNode(0)
                    + "', which is read by a method, should be destroyed after introducing new alias at "
                    + mapTreeNode2FlatMethod.get(tn).getMethod().getClassDesc().getSourceFileName()
                    + "::" + tn.getNumLine());
          }

        }
      }

    }

  }

  private boolean isLiveOut(TreeNode tn, FlatNode fn) {
    Set<TempDescriptor> liveOutTemp = liveness.getLiveOutTemps(mapTreeNode2FlatMethod.get(tn), fn);
    if (fn.kind() == FKind.FlatOpNode) {
      FlatOpNode fon = (FlatOpNode) fn;
      return liveOutTemp.contains(fon.getLeft());
    }
    return false;
  }

  private void parseAnnotations(MethodDescriptor md) {

    for (int i = 0; i < md.numParameters(); i++) {
      // process annotations on method parameters
      VarDescriptor vd = (VarDescriptor) md.getParameter(i);

      Vector<AnnotationDescriptor> annotationVec = vd.getType().getAnnotationMarkers();

      for (int anIdx = 0; anIdx < annotationVec.size(); anIdx++) {
        AnnotationDescriptor ad = annotationVec.elementAt(anIdx);
        if (ad.getMarker().equals(SSJavaAnalysis.DELEGATE)) {

          Set<VarDescriptor> delegateSet = md2DelegateParamSet.get(md);
          if (delegateSet == null) {
            delegateSet = new HashSet<VarDescriptor>();
            md2DelegateParamSet.put(md, delegateSet);
          }
          delegateSet.add(vd);
        }
      }

    }

  }

  private void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    checkBlockNode(md, md.getParameterTable(), bn);
  }

  private void checkBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      checkBlockStatementNode(md, bn.getVarTable(), bsn);
    }
  }

  private void checkBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    if (needToNullify != null) {
      if (!checkNullifying(bsn)) {
        throw new Error(
            "Reference field '"
                + needToNullify
                + "', which is read by a method, should be assigned to null before executing any following statement of the reference copy statement at "
                + md.getClassDesc().getSourceFileName() + "::" + prevAssignNode.getNumLine());
      }
    }

    switch (bsn.kind()) {

    case Kind.BlockExpressionNode:
      checkBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
      return;

    case Kind.DeclarationNode:
      checkDeclarationNode(md, nametable, (DeclarationNode) bsn);
      return;

    case Kind.IfStatementNode:
      checkIfStatementNode(md, nametable, (IfStatementNode) bsn);
      return;

    case Kind.SwitchStatementNode:
      checkSwitchStatementNode(md, nametable, (SwitchStatementNode) bsn);
      return;

    case Kind.LoopNode:
      checkLoopNode(md, nametable, (LoopNode) bsn);
      return;

    case Kind.ReturnNode:
      checkReturnNode(md, nametable, (ReturnNode) bsn);
      return;

    case Kind.SubBlockNode:
      checkSubBlockNode(md, nametable, (SubBlockNode) bsn);
      return;

    case Kind.SynchronizedNode:
      checkSynchronizedNode(md, nametable, (SynchronizedNode) bsn);
      return;
    }

  }

  private void checkSynchronizedNode(MethodDescriptor md, SymbolTable nametable,
      SynchronizedNode sbn) {
    checkBlockNode(md, nametable, sbn.getBlockNode());
    // todo this could be Object
    checkExpressionNode(md, nametable, sbn.getExpr());
  }

  private void checkReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode rn) {
    if (rn.getReturnExpression() != null) {
      checkExpressionNode(md, nametable, rn.getReturnExpression());
    }
  }

  private void checkSubBlockNode(MethodDescriptor md, SymbolTable nametable, SubBlockNode sbn) {
    checkBlockNode(md, nametable, sbn.getBlockNode());
  }

  private void checkIfStatementNode(MethodDescriptor md, SymbolTable nametable, IfStatementNode isn) {
    checkExpressionNode(md, nametable, isn.getCondition());
    checkBlockNode(md, nametable, isn.getTrueBlock());
    if (isn.getFalseBlock() != null)
      checkBlockNode(md, nametable, isn.getFalseBlock());
  }

  private void checkSwitchStatementNode(MethodDescriptor md, SymbolTable nametable,
      SwitchStatementNode ssn) {

    checkExpressionNode(md, nametable, ssn.getCondition());

    BlockNode sbn = ssn.getSwitchBody();
    for (int i = 0; i < sbn.size(); i++) {
      checkSwitchBlockNode(md, nametable, (SwitchBlockNode) sbn.get(i));
    }
  }

  private void checkSwitchBlockNode(MethodDescriptor md, SymbolTable nametable, SwitchBlockNode sbn) {
    checkBlockNode(md, nametable, sbn.getSwitchBlockStatement());
  }

  private void checkBlockExpressionNode(MethodDescriptor md, SymbolTable nametable,
      BlockExpressionNode ben) {
    checkExpressionNode(md, nametable, ben.getExpression());
  }

  private void checkExpressionNode(MethodDescriptor md, SymbolTable nametable, ExpressionNode en) {
    switch (en.kind()) {
    case Kind.AssignmentNode:
      checkAssignmentNode(md, nametable, (AssignmentNode) en);
      return;

    case Kind.CastNode:
      checkCastNode(md, nametable, (CastNode) en);
      return;

    case Kind.CreateObjectNode:
      checkCreateObjectNode(md, nametable, (CreateObjectNode) en);
      return;

    case Kind.FieldAccessNode:
      checkFieldAccessNode(md, nametable, (FieldAccessNode) en);
      return;

    case Kind.ArrayAccessNode:
      checkArrayAccessNode(md, nametable, (ArrayAccessNode) en);
      return;

      // case Kind.LiteralNode:
      // checkLiteralNode(md, nametable, (LiteralNode) en);
      // return;

    case Kind.MethodInvokeNode:
      checkMethodInvokeNode(md, nametable, (MethodInvokeNode) en);
      return;

    case Kind.NameNode:
      checkNameNode(md, nametable, (NameNode) en);
      return;

    case Kind.OpNode:
      checkOpNode(md, nametable, (OpNode) en);
      return;

    case Kind.OffsetNode:
      checkOffsetNode(md, nametable, (OffsetNode) en);
      return;

    case Kind.TertiaryNode:
      checkTertiaryNode(md, nametable, (TertiaryNode) en);
      return;

      // case Kind.InstanceOfNode:
      // checkInstanceOfNode(md, nametable, (InstanceOfNode) en);
      // return;

      // case Kind.ArrayInitializerNode:
      // checkArrayInitializerNode(md, nametable, (ArrayInitializerNode) en);
      // return;

      // case Kind.ClassTypeNode:
      // checkClassTypeNode(md, nametable, (ClassTypeNode) ens);
      // return;
    }
  }

  private void checkTertiaryNode(MethodDescriptor md, SymbolTable nametable, TertiaryNode en) {
    // TODO Auto-generated method stub

  }

  private void checkOffsetNode(MethodDescriptor md, SymbolTable nametable, OffsetNode en) {
    // TODO Auto-generated method stub

  }

  private void checkOpNode(MethodDescriptor md, SymbolTable nametable, OpNode en) {
    // TODO Auto-generated method stub

  }

  private void checkNameNode(MethodDescriptor md, SymbolTable nametable, NameNode en) {
    // TODO Auto-generated method stub

  }

  private void checkMethodInvokeNode(MethodDescriptor md, SymbolTable nametable, MethodInvokeNode en) {
    // TODO Auto-generated method stub

  }

  private void checkArrayAccessNode(MethodDescriptor md, SymbolTable nametable, ArrayAccessNode en) {
    // TODO Auto-generated method stub

  }

  private void checkFieldAccessNode(MethodDescriptor md, SymbolTable nametable, FieldAccessNode fan) {

  }

  private void checkCreateObjectNode(MethodDescriptor md, SymbolTable nametable,
      CreateObjectNode con) {

    TypeDescriptor[] tdarray = new TypeDescriptor[con.numArgs()];
    for (int i = 0; i < con.numArgs(); i++) {
      ExpressionNode en = con.getArg(i);
      checkExpressionNode(md, nametable, en);
      tdarray[i] = en.getType();
    }

    if ((con.getArrayInitializer() != null)) {
      checkArrayInitializerNode(md, nametable, con.getArrayInitializer());
    }

  }

  private void checkArrayInitializerNode(MethodDescriptor md, SymbolTable nametable,
      ArrayInitializerNode arrayInitializer) {
    // TODO Auto-generated method stub

  }

  private void checkCastNode(MethodDescriptor md, SymbolTable nametable, CastNode cn) {
    ExpressionNode en = cn.getExpression();
    checkExpressionNode(md, nametable, en);
  }

  private boolean checkNullifying(BlockStatementNode bsn) {

    if (bsn.kind() == Kind.BlockExpressionNode) {
      ExpressionNode en = ((BlockExpressionNode) bsn).getExpression();
      if (en.kind() == Kind.AssignmentNode) {
        AssignmentNode an = (AssignmentNode) en;

        String destName = an.getDest().printNode(0);
        if (destName.startsWith("this.")) {
          destName = destName.substring(5);
        }

        if (an.getSrc().getType().isNull() && destName.equals(needToNullify)) {
          needToNullify = null;
          return true;
        }
      }
    }

    return false;
  }

  private void checkLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln) {
    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {
      checkExpressionNode(md, nametable, ln.getCondition());
      checkBlockNode(md, nametable, ln.getBody());
    } else {
      // For loop case
      /* Link in the initializer naming environment */
      BlockNode bn = ln.getInitializer();
      for (int i = 0; i < bn.size(); i++) {
        BlockStatementNode bsn = bn.get(i);
        checkBlockStatementNode(md, bn.getVarTable(), bsn);
      }
      // check the condition
      checkExpressionNode(md, bn.getVarTable(), ln.getCondition());
      checkBlockNode(md, bn.getVarTable(), ln.getBody());
      checkBlockNode(md, bn.getVarTable(), ln.getUpdate());
    }
  }

  private void checkAssignmentNode(MethodDescriptor md, SymbolTable nametable, AssignmentNode an) {

    boolean postinc = true;
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC))
      postinc = false;

    if (!postinc) {
      if (!an.getSrc().getType().isImmutable()) {
        if (an.getSrc().kind() == Kind.NameNode) {

          NameNode nn = (NameNode) an.getSrc();

          if (nn.getField() != null) {
            needToNullify = nn.getField().getSymbol();
            prevAssignNode = an;
          } else if (nn.getExpression() != null) {
            if (nn.getExpression() instanceof FieldAccessNode) {
              FieldAccessNode fan = (FieldAccessNode) nn.getExpression();
              needToNullify = fan.printNode(0);
              prevAssignNode = an;

            }

          } else {
            // local variable case
            linearTypeCheckSet.add(an.getSrc());
            mapTreeNode2FlatMethod.put(an.getSrc(), state.getMethodFlat(md));
          }
        } else if (an.getSrc().kind() == Kind.FieldAccessNode) {
          FieldAccessNode fan = (FieldAccessNode) an.getSrc();
          needToNullify = fan.printNode(0);
          if (needToNullify.startsWith("this.")) {
            needToNullify = needToNullify.substring(5);
          }
          prevAssignNode = an;
        }

      }
    }

    // needToNullify(an.getSrc());
  }

  private String getVarNameFromNameNode(NameNode nn) {
    NameDescriptor nd = nn.getName();
    String varName = nd.toString();
    return varName;
  }

  private void checkDeclarationNode(MethodDescriptor md, SymbolTable nametable, DeclarationNode dn) {
    if (dn.getExpression() != null) {
      checkExpressionNode(md, nametable, dn.getExpression());
    }

    // needToNullify(dn.getExpression());
  }

  private void needToNullify(ExpressionNode en) {

    if (en != null && en.getType().isPtr() && !en.getType().isString()) {
      if (en.kind() != Kind.CreateObjectNode && en.kind() != Kind.LiteralNode) {
        if (en.kind() == Kind.CastNode) {
          needToNullify = ((CastNode) en).getExpression().printNode(0);
        } else {
          needToNullify = en.printNode(0);
        }
      }
    }

  }

}
