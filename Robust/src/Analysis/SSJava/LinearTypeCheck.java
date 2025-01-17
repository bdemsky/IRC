package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import Analysis.Liveness;
import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
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
  TreeNode prevAssignNode;

  Set<TreeNode> linearTypeCheckSet;

  Hashtable<TreeNode, FlatMethod> mapTreeNode2FlatMethod;

  Set<MethodDescriptor> delegateThisMethodSet;

  Liveness liveness;

  boolean deterministic = true;

  public LinearTypeCheck(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.linearTypeCheckSet = new HashSet<TreeNode>();
    this.mapTreeNode2FlatMethod = new Hashtable<TreeNode, FlatMethod>();
    this.delegateThisMethodSet = new HashSet<MethodDescriptor>();
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
    if (deterministic) {

      SymbolTable classtable = state.getClassSymbolTable();

      List<ClassDescriptor> toanalyzeList = new ArrayList<ClassDescriptor>();
      List<MethodDescriptor> toanalyzeMethodList = new ArrayList<MethodDescriptor>();

      toanalyzeList.addAll(classtable.getValueSet());
      Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
        public int compare(ClassDescriptor o1, ClassDescriptor o2) {
          return o1.getClassName().compareTo(o2.getClassName());
        }
      });

      for (int i = 0; i < toanalyzeList.size(); i++) {
        ClassDescriptor cd = toanalyzeList.get(i);

        SymbolTable methodtable = cd.getMethodTable();
        toanalyzeMethodList.clear();
        toanalyzeMethodList.addAll(methodtable.getValueSet());
        Collections.sort(toanalyzeMethodList, new Comparator<MethodDescriptor>() {
          public int compare(MethodDescriptor o1, MethodDescriptor o2) {
            return o1.getSymbol().compareTo(o2.getSymbol());
          }
        });

        for (int mdIdx = 0; mdIdx < toanalyzeMethodList.size(); mdIdx++) {
          MethodDescriptor md = toanalyzeMethodList.get(mdIdx);
          if (ssjava.needToCheckLinearType(md)) {
            checkMethodBody(cd, md);
          }
        }

      }

    } else {
      it = state.getClassSymbolTable().getDescriptorsIterator();
      while (it.hasNext()) {
        ClassDescriptor cd = (ClassDescriptor) it.next();
        for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
          MethodDescriptor md = (MethodDescriptor) method_it.next();
          checkMethodBody(cd, md);
        }
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

    // method annotation parsing
    Vector<AnnotationDescriptor> methodAnnotations = md.getModifiers().getAnnotations();
    if (methodAnnotations != null) {
      for (int i = 0; i < methodAnnotations.size(); i++) {
        AnnotationDescriptor an = methodAnnotations.elementAt(i);
        if (an.getMarker().equals(ssjava.DELEGATETHIS)) {
          delegateThisMethodSet.add(md);
          md.getThis().getType().setExtension(new SSJavaType(true));
        }
      }
    }

    // paramter annotation parsing
    for (int i = 0; i < md.numParameters(); i++) {
      // process annotations on method parameters
      VarDescriptor vd = (VarDescriptor) md.getParameter(i);

      Vector<AnnotationDescriptor> annotationVec = vd.getType().getAnnotationMarkers();

      for (int anIdx = 0; anIdx < annotationVec.size(); anIdx++) {
        AnnotationDescriptor ad = annotationVec.elementAt(anIdx);
        if (ad.getMarker().equals(SSJavaAnalysis.DELEGATE)) {
          SSJavaType locationType = new SSJavaType(true);
          vd.getType().setExtension(locationType);
        }
      }
    }
  }

  private void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    if (state.SSJAVADEBUG) {
      System.out.println("SSJAVA: Linear Type Checking: " + md);
    }
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

  private void checkTertiaryNode(MethodDescriptor md, SymbolTable nametable, TertiaryNode tn) {
    checkExpressionNode(md, nametable, tn.getCond());
    checkExpressionNode(md, nametable, tn.getTrueExpr());
    checkExpressionNode(md, nametable, tn.getFalseExpr());
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

  private boolean isOwned(VarDescriptor varDesc) {
    if (varDesc.getType().getExtension() != null) {
      SSJavaType locationType = (SSJavaType) varDesc.getType().getExtension();
      return locationType.isOwned();
    }
    return false;
  }

  private void checkMethodInvokeNode(MethodDescriptor md, SymbolTable nametable,
      MethodInvokeNode min) {

    MethodDescriptor calleeMethodDesc = min.getMethod();

    // check delegate_this annotation
    // only method that owns itself 'THIS' can call method with delegate_this
    // annotation

    if (delegateThisMethodSet.contains(calleeMethodDesc)) {

      if (min.getBaseName() == null) {
        if (!delegateThisMethodSet.contains(md)) {
          throw new Error("Caller does not own the 'THIS' argument at " + md.getClassDesc() + "::"
              + min.getNumLine());
        }
      } else {
        VarDescriptor baseVar = (VarDescriptor) nametable.get(min.getBaseName().getIdentifier());
        if (!isOwned(baseVar)) {
          throw new Error("Caller does not own the 'THIS' argument at " + md.getClassDesc() + "::"
              + min.getNumLine());
        }
      }
    }

    // check delegate parameter annotation
    for (int i = 0; i < min.numArgs(); i++) {
      ExpressionNode argNode = min.getArg(i);

      TypeDescriptor paramType = calleeMethodDesc.getParamType(i);

      if (isReference(argNode.getType()) && !argNode.getType().isNull()) {

        boolean isParamOwnedByCallee = false;
        if (paramType.getExtension() != null) {
          SSJavaType locationType = (SSJavaType) paramType.getExtension();
          isParamOwnedByCallee = locationType.isOwned();
        }

        TypeDescriptor argType = getTypeDescriptor(argNode);

        if (isParamOwnedByCallee) {

          // cannot pass field reference through ownership transition
          if (isField(argNode)) {
            throw new Error("Caller cannot transfer its ownership of the field reference at "
                + md.getClassDesc() + "::" + min.getNumLine());
          }

          // method expects that argument is owned by caller
          SSJavaType locationType = (SSJavaType) argType.getExtension();

          if (locationType == null || !locationType.isOwned()) {
            throw new Error("Caller passes an argument not owned by itself at " + md.getClassDesc()
                + "::" + min.getNumLine());
          }

          // delegated arg is no longer to be available from here
          linearTypeCheckSet.add(argNode);
          mapTreeNode2FlatMethod.put(argNode, state.getMethodFlat(md));
        }

      }
    }

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

    // the current method owns a instance that it makes inside
    SSJavaType locationType = new SSJavaType(true);
    con.getType().setExtension(locationType);

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

        boolean postinc = isPostIncAssignment(an);

        if (!postinc) {
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

  public boolean isPostIncAssignment(AssignmentNode an) {
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC)) {
      return false;
    } else {
      return true;
    }
  }

  private void checkAssignmentNode(MethodDescriptor md, SymbolTable nametable, AssignmentNode an) {

    boolean postinc = isPostIncAssignment(an);

    if (!postinc) {
      checkExpressionNode(md, nametable, an.getSrc());
      if (isReference(an.getSrc().getType()) && isReference(an.getDest().getType())) {
        checkAlias(md, an, an.getSrc());
      }

    }

  }

  private boolean isFieldOfClass(ClassDescriptor classDesc, String varName) {
    return classDesc.getFieldTable().contains(varName);
  }

  private boolean isCreatingAlias(ExpressionNode en) {

    int kind = en.kind();
    if (kind == Kind.NameNode || kind == Kind.ArrayAccessNode || kind == Kind.FieldAccessNode) {
      return true;
    }
    return false;

  }

  private TypeDescriptor getTypeDescriptor(ExpressionNode en) {
    if (en.kind() == Kind.NameNode) {
      NameNode nn = (NameNode) en;
      if (nn.getField() != null) {
        return nn.getVar().getType();
      } else if (nn.getVar() != null) {
        return nn.getVar().getType();
      } else {
        return getTypeDescriptor(nn.getExpression());
      }
    } else if (en.kind() == Kind.FieldAccessNode) {
      FieldAccessNode fan = (FieldAccessNode) en;
      return getTypeDescriptor(fan.getExpression());
    } else if (en.kind() == Kind.CreateObjectNode) {
      CreateObjectNode con = (CreateObjectNode) en;
      return con.getType();
    } else if (en.kind() == Kind.ArrayAccessNode) {
      ArrayAccessNode aan = (ArrayAccessNode) en;
      return aan.getExpression().getType();
    }

    return null;
  }

  private FieldDescriptor getFieldDescriptorFromExpressionNode(ExpressionNode en) {

    if (en.kind() == Kind.NameNode) {
      NameNode nn = (NameNode) en;
      if (nn.getField() != null) {
        return nn.getField();
      }

      if (nn.getName() != null && nn.getName().getBase() != null) {
        return getFieldDescriptorFromExpressionNode(nn.getExpression());
      }

    } else if (en.kind() == Kind.FieldAccessNode) {
      FieldAccessNode fan = (FieldAccessNode) en;
      return fan.getField();
    }

    return null;
  }

  private boolean isField(ExpressionNode en) {

    if (en.kind() == Kind.NameNode) {
      NameNode nn = (NameNode) en;
      if (nn.getField() != null) {
        return true;
      }

      if (nn.getName() != null && nn.getName().getBase() != null) {
        return true;
      }

    } else if (en.kind() == Kind.FieldAccessNode) {
      return true;
    }
    return false;
  }

  private void checkAlias(MethodDescriptor md, TreeNode node, ExpressionNode src) {

    if (src.kind() == Kind.NameNode) {

      NameNode nn = (NameNode) src;

      if (nn.getField() != null) {
        needToNullify = nn.getField().getSymbol();
        prevAssignNode = node;
      } else if (nn.getExpression() != null) {
        if (nn.getExpression() instanceof FieldAccessNode) {
          FieldAccessNode fan = (FieldAccessNode) nn.getExpression();
          needToNullify = fan.printNode(0);
          prevAssignNode = node;
        }
      } else {
        // local variable case
        linearTypeCheckSet.add(src);
        mapTreeNode2FlatMethod.put(src, state.getMethodFlat(md));
      }
    } else if (src.kind() == Kind.FieldAccessNode) {
      FieldAccessNode fan = (FieldAccessNode) src;
      needToNullify = fan.printNode(0);
      if (needToNullify.startsWith("this.")) {
        needToNullify = needToNullify.substring(5);
      }
      prevAssignNode = node;
    } else if (src.kind() == Kind.ArrayAccessNode) {
      ArrayAccessNode aan = (ArrayAccessNode) src;
      TypeDescriptor srcType = src.getType();
      if (srcType.isPtr() && srcType.getArrayCount() > 0) {
        throw new Error(
            "Not allowed to create an alias to the middle of the multidimensional array at "
                + md.getClassDesc().getSourceFileName() + "::" + node.getNumLine());
      } else {
        needToNullify = aan.printNode(0);
        prevAssignNode = node;
      }
    } else if (src.kind() == Kind.CreateObjectNode || src.kind() == Kind.MethodInvokeNode
        || src.kind() == Kind.ArrayInitializerNode || src.kind() == Kind.LiteralNode) {
      if (node.kind() == Kind.DeclarationNode) {
        DeclarationNode dn = (DeclarationNode) node;
        dn.getVarDescriptor().getType().setExtension(new SSJavaType(true));
      }
    } else {
      throw new Error("Not allowed this type of assignment at "
          + md.getClassDesc().getSourceFileName() + "::" + node.getNumLine());
    }

    if (isCreatingAlias(src)) {

      TypeDescriptor srcType = getTypeDescriptor(src);
      boolean isSourceOwned = false;

      if (srcType.getExtension() != null) {
        SSJavaType srcLocationType = (SSJavaType) srcType.getExtension();
        isSourceOwned = srcLocationType.isOwned();

        if (isSourceOwned) {
          if (isField(src)) {
            ssjava.setFieldOnwership(md, getFieldDescriptorFromExpressionNode(src));
          }
        }

      } else if (md.isConstructor() && isFieldOfClass(md.getClassDesc(), src.printNode(0))) {
        isSourceOwned = true;
        ssjava.setFieldOnwership(md, getFieldDescriptorFromExpressionNode(src));
      }

      if (node.kind() == Kind.AssignmentNode) {
        AssignmentNode an = (AssignmentNode) node;
        if (isField(an.getDest())) {
          // if instance is not owned by the method, not able to store
          // instance into field
          if (!isSourceOwned) {
            throw new Error(
                "Method is not allowed to store an instance not owned by itself into a field at "
                    + md.getClassDesc().getSourceFileName() + "::" + node.getNumLine());
          }
        } else {
          if (isSourceOwned) {
            // here, transfer ownership from LHS to RHS when it creates alias
            TypeDescriptor destType = getTypeDescriptor(an.getDest());
            destType.setExtension(new SSJavaType(isSourceOwned));
          }
        }
      }

    }

  }

  private void checkDeclarationNode(MethodDescriptor md, SymbolTable nametable, DeclarationNode dn) {
    if (dn.getExpression() != null) {
      ExpressionNode src = dn.getExpression();
      if (isReference(src.getType())) {
        checkAlias(md, dn, src);
      }
    }
  }

  private boolean isReference(TypeDescriptor td) {
    if (td.isPtr() && (!td.isString())) {
      return true;
    }
    return false;
  }

}
