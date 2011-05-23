package Analysis.SSJava;

import java.util.Iterator;

import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.Tree.AssignmentNode;
import IR.Tree.BlockExpressionNode;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.CastNode;
import IR.Tree.CreateObjectNode;
import IR.Tree.DeclarationNode;
import IR.Tree.ExpressionNode;
import IR.Tree.Kind;
import IR.Tree.LoopNode;
import IR.Tree.SubBlockNode;

public class SingleReferenceCheck {

  static State state;
  String needToNullify = null;

  public SingleReferenceCheck(State state) {
    this.state = state;
  }

  public void singleReferenceCheck() {
    Iterator it = state.getClassSymbolTable().getDescriptorsIterator();
    while (it.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor) it.next();
      for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
        MethodDescriptor md = (MethodDescriptor) method_it.next();
        checkMethodBody(cd, md);
      }
    }
  }

  private void checkMethodBody(ClassDescriptor cd, MethodDescriptor fm) {
    BlockNode bn = state.getMethodBody(fm);
    for (int i = 0; i < bn.size(); i++) {
      checkBlockStatementNode(cd, bn.get(i));
    }

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

  private void checkBlockStatementNode(ClassDescriptor cd, BlockStatementNode bsn) {

    if (needToNullify != null) {
      if (!checkNullifying(bsn)) {
        throw new Error(
            "Reference field, which is read by a method, should be assigned to null before executing any following statement of the reference copy statement at "
                + cd.getSourceFileName() + "::" + bsn.getNumLine());
      }
    }

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      checkExpressionNode(((BlockExpressionNode) bsn).getExpression());
      break;

    case Kind.DeclarationNode:
      checkDeclarationNode((DeclarationNode) bsn);
      break;

    case Kind.SubBlockNode:
      checkSubBlockNode(cd, (SubBlockNode) bsn);
      return;

    case Kind.LoopNode:
      checkLoopNode(cd, (LoopNode) bsn);
      break;
    }

  }

  private void checkLoopNode(ClassDescriptor cd, LoopNode ln) {
    if (ln.getType() == LoopNode.FORLOOP) {
      checkBlockNode(cd, ln.getInitializer());
    }
    checkBlockNode(cd, ln.getBody());
  }

  private void checkSubBlockNode(ClassDescriptor cd, SubBlockNode sbn) {
    checkBlockNode(cd, sbn.getBlockNode());
  }

  private void checkBlockNode(ClassDescriptor cd, BlockNode bn) {
    for (int i = 0; i < bn.size(); i++) {
      checkBlockStatementNode(cd, bn.get(i));
    }
  }

  private void checkExpressionNode(ExpressionNode en) {

    switch (en.kind()) {
    case Kind.AssignmentNode:
      checkAssignmentNode((AssignmentNode) en);
      break;
    }

  }

  private void checkAssignmentNode(AssignmentNode an) {

    if (an.getSrc() != null) {
      if (an.getSrc().getType().isPtr() && (!an.getSrc().getType().isNull())
          && !(an.getSrc() instanceof CreateObjectNode)) {
        if (an.getSrc() instanceof CastNode) {
          needToNullify = ((CastNode) an.getSrc()).getExpression().printNode(0);
        } else {
          needToNullify = an.getSrc().printNode(0);
        }
      }

    }
  }

  private void checkDeclarationNode(DeclarationNode dn) {

    if (dn.getExpression() != null) {
      if (dn.getExpression().getType().isPtr() && !(dn.getExpression() instanceof CreateObjectNode)) {

        if (dn.getExpression() instanceof CastNode) {
          needToNullify = ((CastNode) dn.getExpression()).getExpression().printNode(0);
        } else {
          needToNullify = dn.getExpression().printNode(0);
        }

      }
    }
  }

}
