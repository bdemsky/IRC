package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.Descriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.SymbolTable;
import IR.TaskDescriptor;
import IR.TypeDescriptor;
import IR.VarDescriptor;
import IR.Tree.ArrayAccessNode;
import IR.Tree.ArrayInitializerNode;
import IR.Tree.AssignmentNode;
import IR.Tree.AtomicNode;
import IR.Tree.BlockExpressionNode;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.CastNode;
import IR.Tree.ClassTypeNode;
import IR.Tree.ContinueBreakNode;
import IR.Tree.CreateObjectNode;
import IR.Tree.DeclarationNode;
import IR.Tree.ExpressionNode;
import IR.Tree.FieldAccessNode;
import IR.Tree.IfStatementNode;
import IR.Tree.InstanceOfNode;
import IR.Tree.Kind;
import IR.Tree.LiteralNode;
import IR.Tree.LoopNode;
import IR.Tree.MethodInvokeNode;
import IR.Tree.NameNode;
import IR.Tree.OffsetNode;
import IR.Tree.OpNode;
import IR.Tree.ReturnNode;
import IR.Tree.SubBlockNode;
import IR.Tree.SwitchBlockNode;
import IR.Tree.SwitchLabelNode;
import IR.Tree.SwitchStatementNode;
import IR.Tree.SynchronizedNode;
import IR.Tree.TagDeclarationNode;
import IR.Tree.TaskExitNode;
import IR.Tree.TertiaryNode;

public class LinearTypeCheck {

  State state;
  SSJavaAnalysis ssjava;
  String needToNullify = null;

  Hashtable<MethodDescriptor, Set<VarDescriptor>> md2DelegateParamSet;

  public LinearTypeCheck(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    md2DelegateParamSet = new Hashtable<MethodDescriptor, Set<VarDescriptor>>();
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
    System.out.println("###");
    System.out.println("md2DelegateParamSet=" + md2DelegateParamSet);

    // second, check the linear type
    it = state.getClassSymbolTable().getDescriptorsIterator();
    while (it.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor) it.next();
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        if (ssjava.needTobeAnnotated(md)) {
          checkMethodBody(cd, md);
        }
      }
    }

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
            "Reference field, which is read by a method, should be assigned to null before executing any following statement of the reference copy statement at "
                + md.getClassDesc().getSourceFileName() + "::" + bsn.getNumLine());
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

    throw new Error();
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
    throw new Error();
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

        if (an.getSrc().getType().isNull() && an.getDest().printNode(0).equals(needToNullify)) {
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

  private void checkAssignmentNode(Descriptor md, SymbolTable nametable, AssignmentNode an) {
    needToNullify(an.getSrc());
  }

  private void checkDeclarationNode(Descriptor md, SymbolTable nametable, DeclarationNode dn) {
    needToNullify(dn.getExpression());
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
