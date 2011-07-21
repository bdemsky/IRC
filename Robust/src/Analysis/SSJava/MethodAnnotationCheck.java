package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.SymbolTable;
import IR.TypeUtil;
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
import IR.Tree.InstanceOfNode;
import IR.Tree.Kind;
import IR.Tree.LoopNode;
import IR.Tree.MethodInvokeNode;
import IR.Tree.OpNode;
import IR.Tree.ReturnNode;
import IR.Tree.SubBlockNode;
import IR.Tree.TertiaryNode;
import Util.Pair;

public class MethodAnnotationCheck {

  State state;
  SSJavaAnalysis ssjava;
  TypeUtil tu;

  Set<MethodDescriptor> annotatedMDSet;
  Hashtable<MethodDescriptor, Set<MethodDescriptor>> caller2calleeSet;

  public MethodAnnotationCheck(SSJavaAnalysis ssjava, State state, TypeUtil tu) {
    this.ssjava = ssjava;
    this.state = state;
    this.tu = tu;
    caller2calleeSet = new Hashtable<MethodDescriptor, Set<MethodDescriptor>>();
    annotatedMDSet = new HashSet<MethodDescriptor>();
  }

  public void methodAnnoatationCheck() {
    SymbolTable classtable = state.getClassSymbolTable();
    HashSet toanalyze = new HashSet();
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      ClassDescriptor cd = (ClassDescriptor) obj;
      toanalyze.remove(cd);

      if (!cd.isInterface()) {
        for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) method_it.next();
          checkMethodBody(cd, md);
        }
      }

    }

    for (Iterator iterator = annotatedMDSet.iterator(); iterator.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) iterator.next();
      ssjava.addAnnotationRequire(md);
    }

    Set<Pair> visited = new HashSet<Pair>();
    Set<MethodDescriptor> tovisit = new HashSet<MethodDescriptor>();
    tovisit.addAll(annotatedMDSet);

    while (!tovisit.isEmpty()) {
      MethodDescriptor callerMD = tovisit.iterator().next();
      tovisit.remove(callerMD);

      Set<MethodDescriptor> calleeSet = caller2calleeSet.get(callerMD);
      if (calleeSet != null) {
        for (Iterator iterator = calleeSet.iterator(); iterator.hasNext();) {
          MethodDescriptor calleeMD = (MethodDescriptor) iterator.next();
          Pair p = new Pair(callerMD, calleeMD);
          if (!visited.contains(p)) {
            visited.add(p);

            tovisit.add(calleeMD);

            Set<MethodDescriptor> possibleCalleeSet =
                (Set<MethodDescriptor>) ssjava.getCallGraph().getMethods(calleeMD);

            for (Iterator iterator2 = possibleCalleeSet.iterator(); iterator2.hasNext();) {
              MethodDescriptor possibleCallee = (MethodDescriptor) iterator2.next();

              if (!possibleCallee.isAbstract()) {
                ssjava.addAnnotationRequire(possibleCallee);
                tovisit.add(possibleCallee);
              }

            }

          }
        }
      }
    }

  }

  public void methodAnnoataionInheritanceCheck() {
    // check If a method is annotated, any method that overrides it should
    // be annotated.

    Set<MethodDescriptor> tovisit = new HashSet<MethodDescriptor>();
    tovisit.addAll(ssjava.getAnnotationRequireSet());

    while (!tovisit.isEmpty()) {
      MethodDescriptor md = tovisit.iterator().next();
      tovisit.remove(md);

      ClassDescriptor cd = md.getClassDesc();

      Set subClassSet = tu.getSubClasses(cd);
      if (subClassSet != null) {
        for (Iterator iterator2 = subClassSet.iterator(); iterator2.hasNext();) {
          ClassDescriptor subCD = (ClassDescriptor) iterator2.next();
          Set possiblematches = subCD.getMethodTable().getSet(md.getSymbol());
          for (Iterator methodit = possiblematches.iterator(); methodit.hasNext();) {
            MethodDescriptor matchmd = (MethodDescriptor) methodit.next();
            if (md.matches(matchmd)) {
              if (matchmd.getClassDesc().equals(subCD)) {
                ssjava.addAnnotationRequire(matchmd);
              }
            }
          }
        }
      }

      // need to check super classess if the current method is inherited from
      // them, all of ancestor method should be annoated
      ClassDescriptor currentCd = cd;
      ClassDescriptor superCd = tu.getSuper(currentCd);
      while (!superCd.getSymbol().equals("Object")) {
        Set possiblematches = superCd.getMethodTable().getSet(md.getSymbol());
        for (Iterator methodit = possiblematches.iterator(); methodit.hasNext();) {
          MethodDescriptor matchmd = (MethodDescriptor) methodit.next();
          if (md.matches(matchmd)) {
            ssjava.addAnnotationRequire(matchmd);
          }
        }
        currentCd = superCd;
        superCd = tu.getSuper(currentCd);
      }

      Set<ClassDescriptor> superIFSet = tu.getSuperIFs(cd);
      for (Iterator iterator = superIFSet.iterator(); iterator.hasNext();) {
        ClassDescriptor parentInterface = (ClassDescriptor) iterator.next();
        Set possiblematches = parentInterface.getMethodTable().getSet(md.getSymbol());
        for (Iterator methodit = possiblematches.iterator(); methodit.hasNext();) {
          MethodDescriptor matchmd = (MethodDescriptor) methodit.next();
          if (md.matches(matchmd)) {
            ssjava.addAnnotationRequire(matchmd);
          }
        }
      }

    }

  }

  private void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    checkBlockNode(md, md.getParameterTable(), bn, false);
  }

  private void checkBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn, boolean flag) {
    bn.getVarTable().setParent(nametable);
    String label = bn.getLabel();
    boolean isSSJavaLoop = flag;
    if (label != null && label.equals(ssjava.SSJAVA)) {
      if (isSSJavaLoop) {
        throw new Error("Only outermost loop can be the self-stabilizing loop.");
      } else {
        annotatedMDSet.add(md);
        isSSJavaLoop = true;
      }
    }

    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      checkBlockStatementNode(md, bn.getVarTable(), bsn, isSSJavaLoop);
    }

  }

  private void checkBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn, boolean flag) {

    switch (bsn.kind()) {
    case Kind.SubBlockNode:
      checkSubBlockNode(md, nametable, (SubBlockNode) bsn, flag);
      return;

    case Kind.BlockExpressionNode:
      checkBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn, flag);
      break;

    case Kind.DeclarationNode:
      checkDeclarationNode(md, nametable, (DeclarationNode) bsn, flag);
      break;

    case Kind.IfStatementNode:
      checkIfStatementNode(md, nametable, (IfStatementNode) bsn, flag);
      break;

    case Kind.LoopNode:
      checkLoopNode(md, nametable, (LoopNode) bsn, flag);
      break;

    case Kind.ReturnNode:
      checkReturnNode(md, nametable, (ReturnNode) bsn, flag);
      break;

    }
  }

  private void checkDeclarationNode(MethodDescriptor md, SymbolTable nametable, DeclarationNode dn,
      boolean flag) {
    if (dn.getExpression() != null) {
      checkExpressionNode(md, nametable, dn.getExpression(), flag);
    }
  }

  private void checkReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode rn,
      boolean flag) {
    if (rn.getReturnExpression() != null) {
      if (md.getReturnType() != null) {
        checkExpressionNode(md, nametable, rn.getReturnExpression(), flag);
      }
    }
  }

  private void checkLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln, boolean flag) {
    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {
      checkExpressionNode(md, nametable, ln.getCondition(), flag);
      checkBlockNode(md, nametable, ln.getBody(), flag);
    } else {
      // For loop case
      /* Link in the initializer naming environment */
      BlockNode bn = ln.getInitializer();
      bn.getVarTable().setParent(nametable);
      for (int i = 0; i < bn.size(); i++) {
        BlockStatementNode bsn = bn.get(i);
        checkBlockStatementNode(md, bn.getVarTable(), bsn, flag);
      }
      // check the condition
      checkExpressionNode(md, bn.getVarTable(), ln.getCondition(), flag);
      checkBlockNode(md, bn.getVarTable(), ln.getBody(), flag);
      checkBlockNode(md, bn.getVarTable(), ln.getUpdate(), flag);
    }
  }

  private void checkIfStatementNode(MethodDescriptor md, SymbolTable nametable,
      IfStatementNode isn, boolean flag) {
    checkExpressionNode(md, nametable, isn.getCondition(), flag);
    checkBlockNode(md, nametable, isn.getTrueBlock(), flag);
    if (isn.getFalseBlock() != null) {
      checkBlockNode(md, nametable, isn.getFalseBlock(), flag);
    }
  }

  private void checkSubBlockNode(MethodDescriptor md, SymbolTable nametable, SubBlockNode sbn,
      boolean flag) {
    checkBlockNode(md, nametable.getParent(), sbn.getBlockNode(), flag);
  }

  private void checkBlockExpressionNode(MethodDescriptor md, SymbolTable nametable,
      BlockExpressionNode ben, boolean flag) {
    checkExpressionNode(md, nametable, ben.getExpression(), flag);
  }

  private void checkExpressionNode(MethodDescriptor md, SymbolTable nametable, ExpressionNode en,
      boolean flag) {
    switch (en.kind()) {
    case Kind.AssignmentNode:
      checkAssignmentNode(md, nametable, (AssignmentNode) en, flag);
      return;

    case Kind.CastNode:
      checkCastNode(md, nametable, (CastNode) en, flag);
      return;

    case Kind.CreateObjectNode:
      checkCreateObjectNode(md, nametable, (CreateObjectNode) en, flag);
      return;

    case Kind.FieldAccessNode:
      checkFieldAccessNode(md, nametable, (FieldAccessNode) en, flag);
      return;

    case Kind.ArrayAccessNode:
      checkArrayAccessNode(md, nametable, (ArrayAccessNode) en, flag);
      return;

      // case Kind.LiteralNode:
      // checkLiteralNode(md, nametable, (LiteralNode) en, flag);
      // return;

    case Kind.MethodInvokeNode:
      checkMethodInvokeNode(md, nametable, (MethodInvokeNode) en, flag);
      return;

      // case Kind.NameNode:
      // checkNameNode(md, nametable, (NameNode) en, flag);
      // return;

    case Kind.OpNode:
      checkOpNode(md, nametable, (OpNode) en, flag);
      return;

      // case Kind.OffsetNode:
      // checkOffsetNode(md, nametable, (OffsetNode) en, flag);
      // return;

    case Kind.TertiaryNode:
      checkTertiaryNode(md, nametable, (TertiaryNode) en, flag);
      return;

    case Kind.InstanceOfNode:
      checkInstanceOfNode(md, nametable, (InstanceOfNode) en, flag);
      return;

    case Kind.ArrayInitializerNode:
      checkArrayInitializerNode(md, nametable, (ArrayInitializerNode) en, flag);
      return;

      // case Kind.ClassTypeNode:
      // checkClassTypeNode(md, nametable, (ClassTypeNode) en, flag);
      // return;
    }
  }

  private void checkArrayInitializerNode(MethodDescriptor md, SymbolTable nametable,
      ArrayInitializerNode ain, boolean flag) {

    for (int i = 0; i < ain.numVarInitializers(); ++i) {
      checkExpressionNode(md, nametable, ain.getVarInitializer(i), flag);
    }

  }

  private void checkInstanceOfNode(MethodDescriptor md, SymbolTable nametable, InstanceOfNode tn,
      boolean flag) {
    checkExpressionNode(md, nametable, tn.getExpr(), flag);
  }

  private void checkTertiaryNode(MethodDescriptor md, SymbolTable nametable, TertiaryNode tn,
      boolean flag) {
    checkExpressionNode(md, nametable, tn.getCond(), flag);
    checkExpressionNode(md, nametable, tn.getTrueExpr(), flag);
    checkExpressionNode(md, nametable, tn.getFalseExpr(), flag);
  }

  private void checkOpNode(MethodDescriptor md, SymbolTable nametable, OpNode on, boolean flag) {

    checkExpressionNode(md, nametable, on.getLeft(), flag);
    if (on.getRight() != null) {
      checkExpressionNode(md, nametable, on.getRight(), flag);
    }

  }

  private void checkMethodInvokeNode(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min, boolean flag) {
    for (int i = 0; i < min.numArgs(); i++) {
      ExpressionNode en = min.getArg(i);
      checkExpressionNode(md, nametable, en, flag);
    }

    if (min.getExpression() != null) {
      checkExpressionNode(md, nametable, min.getExpression(), flag);
    }

    if (flag) {
      annotatedMDSet.add(min.getMethod());
    }

    Set<MethodDescriptor> mdSet = caller2calleeSet.get(md);
    if (mdSet == null) {
      mdSet = new HashSet<MethodDescriptor>();
      caller2calleeSet.put(md, mdSet);
    }
    mdSet.add(min.getMethod());

  }

  private void checkArrayAccessNode(MethodDescriptor md, SymbolTable nametable,
      ArrayAccessNode aan, boolean flag) {

    ExpressionNode left = aan.getExpression();
    checkExpressionNode(md, nametable, left, flag);
    checkExpressionNode(md, nametable, aan.getIndex(), flag);

  }

  private void checkFieldAccessNode(MethodDescriptor md, SymbolTable nametable,
      FieldAccessNode fan, boolean flag) {
    ExpressionNode left = fan.getExpression();
    checkExpressionNode(md, nametable, left, flag);
  }

  private void checkCreateObjectNode(MethodDescriptor md, SymbolTable nametable,
      CreateObjectNode con, boolean flag) {

    for (int i = 0; i < con.numArgs(); i++) {
      ExpressionNode en = con.getArg(i);
      checkExpressionNode(md, nametable, en, flag);
    }

  }

  private void checkCastNode(MethodDescriptor md, SymbolTable nametable, CastNode cn, boolean flag) {
    ExpressionNode en = cn.getExpression();
    checkExpressionNode(md, nametable, en, flag);
  }

  private void checkAssignmentNode(MethodDescriptor md, SymbolTable nametable, AssignmentNode an,
      boolean flag) {
    boolean postinc = true;

    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC))
      postinc = false;

    if (!postinc) {
      checkExpressionNode(md, nametable, an.getSrc(), flag);
    }

    checkExpressionNode(md, nametable, an.getDest(), flag);

  }

}
