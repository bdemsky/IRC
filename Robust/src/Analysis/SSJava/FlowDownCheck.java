package Analysis.SSJava;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import IR.AnnotationDescriptor;
import IR.ClassDescriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.SymbolTable;
import IR.TypeDescriptor;
import IR.VarDescriptor;
import IR.Tree.AssignmentNode;
import IR.Tree.BlockExpressionNode;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.DeclarationNode;
import IR.Tree.ExpressionNode;
import IR.Tree.Kind;
import IR.Tree.OpNode;
import Util.Lattice;

public class FlowDownCheck {

  State state;
  HashSet toanalyze;

  public FlowDownCheck(State state) {
    this.state = state;
    this.toanalyze = new HashSet();
  }

  public void flowDownCheck() {

    SymbolTable classtable = state.getClassSymbolTable();
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());

    // Do methods next
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      ClassDescriptor cd = (ClassDescriptor) obj;
      toanalyze.remove(cd);
      if (cd.isClassLibrary()) {
        // doesn't care about class libraries now
        continue;
      }

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

  public void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    ClassDescriptor superdesc = cd.getSuperDesc();
    if (superdesc != null) {
      Set possiblematches = superdesc.getMethodTable().getSet(md.getSymbol());
      for (Iterator methodit = possiblematches.iterator(); methodit.hasNext();) {
        MethodDescriptor matchmd = (MethodDescriptor) methodit.next();
        if (md.matches(matchmd)) {
          if (matchmd.getModifiers().isFinal()) {
            throw new Error("Try to override final method in method:" + md + " declared in  " + cd);
          }
        }
      }
    }
    BlockNode bn = state.getMethodBody(md);
    checkBlockNode(md, md.getParameterTable(), bn);
  }

  public void checkBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {
    /* Link in the naming environment */
    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      checkBlockStatementNode(md, bn.getVarTable(), bsn);
    }
  }

  public void checkBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      checkBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
      return;

    case Kind.DeclarationNode:
      checkDeclarationNode(md, (DeclarationNode) bsn);
      return;
    }
    /*
     * switch (bsn.kind()) { case Kind.BlockExpressionNode:
     * checkBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
     * return;
     * 
     * case Kind.DeclarationNode: checkDeclarationNode(md, nametable,
     * (DeclarationNode) bsn); return;
     * 
     * case Kind.TagDeclarationNode: checkTagDeclarationNode(md, nametable,
     * (TagDeclarationNode) bsn); return;
     * 
     * case Kind.IfStatementNode: checkIfStatementNode(md, nametable,
     * (IfStatementNode) bsn); return;
     * 
     * case Kind.SwitchStatementNode: checkSwitchStatementNode(md, nametable,
     * (SwitchStatementNode) bsn); return;
     * 
     * case Kind.LoopNode: checkLoopNode(md, nametable, (LoopNode) bsn); return;
     * 
     * case Kind.ReturnNode: checkReturnNode(md, nametable, (ReturnNode) bsn);
     * return;
     * 
     * case Kind.TaskExitNode: checkTaskExitNode(md, nametable, (TaskExitNode)
     * bsn); return;
     * 
     * case Kind.SubBlockNode: checkSubBlockNode(md, nametable, (SubBlockNode)
     * bsn); return;
     * 
     * case Kind.AtomicNode: checkAtomicNode(md, nametable, (AtomicNode) bsn);
     * return;
     * 
     * case Kind.SynchronizedNode: checkSynchronizedNode(md, nametable,
     * (SynchronizedNode) bsn); return;
     * 
     * case Kind.ContinueBreakNode: checkContinueBreakNode(md, nametable,
     * (ContinueBreakNode) bsn); return;
     * 
     * case Kind.SESENode: case Kind.GenReachNode: // do nothing, no semantic
     * check for SESEs return; }
     */
    // throw new Error();
  }

  void checkBlockExpressionNode(MethodDescriptor md, SymbolTable nametable, BlockExpressionNode ben) {
    checkExpressionNode(md, nametable, ben.getExpression(), null);
  }

  void checkExpressionNode(MethodDescriptor md, SymbolTable nametable, ExpressionNode en,
      TypeDescriptor td) {

    switch (en.kind()) {
    case Kind.AssignmentNode:
      checkAssignmentNode(md, nametable, (AssignmentNode) en, td);
      return;

    case Kind.OpNode:
      checkOpNode(md, nametable, (OpNode) en, td);
      return;
    }
    /*
     * switch(en.kind()) { case Kind.AssignmentNode:
     * checkAssignmentNode(md,nametable,(AssignmentNode)en,td); return;
     * 
     * case Kind.CastNode: checkCastNode(md,nametable,(CastNode)en,td); return;
     * 
     * case Kind.CreateObjectNode:
     * checkCreateObjectNode(md,nametable,(CreateObjectNode)en,td); return;
     * 
     * case Kind.FieldAccessNode:
     * checkFieldAccessNode(md,nametable,(FieldAccessNode)en,td); return;
     * 
     * case Kind.ArrayAccessNode:
     * checkArrayAccessNode(md,nametable,(ArrayAccessNode)en,td); return;
     * 
     * case Kind.LiteralNode: checkLiteralNode(md,nametable,(LiteralNode)en,td);
     * return;
     * 
     * case Kind.MethodInvokeNode:
     * checkMethodInvokeNode(md,nametable,(MethodInvokeNode)en,td); return;
     * 
     * case Kind.NameNode: checkNameNode(md,nametable,(NameNode)en,td); return;
     * 
     * case Kind.OpNode: checkOpNode(md,nametable,(OpNode)en,td); return;
     * 
     * case Kind.OffsetNode: checkOffsetNode(md, nametable, (OffsetNode)en, td);
     * return;
     * 
     * case Kind.TertiaryNode: checkTertiaryNode(md, nametable,
     * (TertiaryNode)en, td); return;
     * 
     * case Kind.InstanceOfNode: checkInstanceOfNode(md, nametable,
     * (InstanceOfNode) en, td); return;
     * 
     * case Kind.ArrayInitializerNode: checkArrayInitializerNode(md, nametable,
     * (ArrayInitializerNode) en, td); return;
     * 
     * case Kind.ClassTypeNode: checkClassTypeNode(md, nametable,
     * (ClassTypeNode) en, td); return; }
     */
  }

  void checkOpNode(MethodDescriptor md, SymbolTable nametable, OpNode on, TypeDescriptor td) {

    Lattice<String> locOrder = (Lattice<String>) state.getCd2LocationOrder().get(md.getClassDesc());

    checkExpressionNode(md, nametable, on.getLeft(), null);
    if (on.getRight() != null)
      checkExpressionNode(md, nametable, on.getRight(), null);

    TypeDescriptor ltd = on.getLeft().getType();
    TypeDescriptor rtd = on.getRight() != null ? on.getRight().getType() : null;

    if (ltd.getAnnotationMarkers().size() == 0) {
      // constant value
      // TODO
      // ltd.addAnnotationMarker(new AnnotationDescriptor(Lattice.TOP));
    }
    if (rtd != null && rtd.getAnnotationMarkers().size() == 0) {
      // constant value
      // TODO
      // rtd.addAnnotationMarker(new AnnotationDescriptor(Lattice.TOP));
    }

    System.out.println("checking op node");
    System.out.println("td=" + td);
    System.out.println("ltd=" + ltd);
    System.out.println("rtd=" + rtd);

    Operation op = on.getOp();

    switch (op.getOp()) {

    case Operation.UNARYPLUS:
    case Operation.UNARYMINUS:
    case Operation.LOGIC_NOT:
      // single operand
      on.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
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

      Set<String> operandSet = new HashSet<String>();
      String leftLoc = ltd.getAnnotationMarkers().get(0).getMarker();
      String rightLoc = rtd.getAnnotationMarkers().get(0).getMarker();

      operandSet.add(leftLoc);
      operandSet.add(rightLoc);

      // TODO
      // String glbLoc = locOrder.getGLB(operandSet);
      // on.getType().addAnnotationMarker(new AnnotationDescriptor(glbLoc));
      // System.out.println(glbLoc + "<-" + leftLoc + " " + rightLoc);

      break;

    default:
      throw new Error(op.toString());
    }

    if (td != null) {
      String lhsLoc = td.getAnnotationMarkers().get(0).getMarker();
      if (locOrder.isGreaterThan(lhsLoc, on.getType().getAnnotationMarkers().get(0).getMarker())) {
        throw new Error("The location of LHS is higher than RHS: " + on.printNode(0));
      }
    }

  }

  void checkAssignmentNode(MethodDescriptor md, SymbolTable nametable, AssignmentNode an,
      TypeDescriptor td) {

    boolean postinc = true;
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC))
      postinc = false;
    if (!postinc)
      checkExpressionNode(md, nametable, an.getSrc(), td);

    ClassDescriptor cd = md.getClassDesc();
    Lattice<String> locOrder = (Lattice<String>) state.getCd2LocationOrder().get(cd);

    System.out.println("an=" + an.printNode(0));
    String destLocation = an.getDest().getType().getAnnotationMarkers().elementAt(0).getMarker();
    String srcLocation = an.getSrc().getType().getAnnotationMarkers().elementAt(0).getMarker();

    if (!locOrder.isGreaterThan(srcLocation, destLocation)) {
      throw new Error("The value flow from " + srcLocation + " to " + destLocation
          + " does not respect location hierarchy.");
    }

  }

  void checkDeclarationNode(MethodDescriptor md, DeclarationNode dn) {

    ClassDescriptor cd = md.getClassDesc();
    VarDescriptor vd = dn.getVarDescriptor();
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

    if (ad.getType() == AnnotationDescriptor.MARKER_ANNOTATION) {

      // check if location is defined
      String locationID = ad.getMarker();
      Lattice<String> lattice = (Lattice<String>) state.getCd2LocationOrder().get(cd);


      if (lattice == null || (!lattice.containsKey(locationID))) {
        throw new Error("Location " + locationID
            + " is not defined in the location hierarchy of class " + cd.getSymbol() + ".");
      }

    } else if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {
      if (ad.getMarker().equals(SSJavaAnalysis.DELTA)) {

        if (ad.getData().length() == 0) {
          throw new Error("Delta function of " + vd.getSymbol() + " does not have any locations: "
              + cd.getSymbol() + ".");
        }

        StringTokenizer token = new StringTokenizer(ad.getData(), ",");
        while (token.hasMoreTokens()) {
          String deltaOperand = token.nextToken();

        }

      }
    }

  }

  private void checkClass(ClassDescriptor cd) {
    // Check to see that fields are okay
    for (Iterator field_it = cd.getFields(); field_it.hasNext();) {
      FieldDescriptor fd = (FieldDescriptor) field_it.next();
      checkFieldDeclaration(cd, fd);
    }

    // Check to see that methods respects ss property
    for (Iterator method_it = cd.getMethods(); method_it.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) method_it.next();
      checkMethodDeclaration(cd, md);
    }
  }

  private void checkMethodDeclaration(ClassDescriptor cd, MethodDescriptor md) {

  }

  private void checkFieldDeclaration(ClassDescriptor cd, FieldDescriptor fd) {

    Vector<AnnotationDescriptor> annotationVec = fd.getType().getAnnotationMarkers();

    // currently enforce every variable to have corresponding location
    if (annotationVec.size() == 0) {
      throw new Error("Location is not assigned to the field " + fd.getSymbol() + " of the class "
          + cd.getSymbol());
    }

    if (annotationVec.size() > 1) {
      // variable can have at most one location
      throw new Error("Field " + fd.getSymbol() + " of class " + cd
          + " has more than one location.");
    }

    // check if location is defined
    AnnotationDescriptor ad = annotationVec.elementAt(0);
    if (ad.getType() == AnnotationDescriptor.MARKER_ANNOTATION) {
      String locationID = annotationVec.elementAt(0).getMarker();
      Lattice<String> lattice = (Lattice<String>) state.getCd2LocationOrder().get(cd);


      if (lattice == null || (!lattice.containsKey(locationID))) {
        throw new Error("Location " + locationID
            + " is not defined in the location hierarchy of class " + cd.getSymbol() + ".");
      }
    } else if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {
      if (ad.getMarker().equals(SSJavaAnalysis.DELTA)) {

        if (ad.getData().length() == 0) {
          throw new Error("Delta function of " + fd.getSymbol() + " does not have any locations: "
              + cd.getSymbol() + ".");
        }

        StringTokenizer token = new StringTokenizer(ad.getData(), ",");
        while (token.hasMoreTokens()) {
          String deltaOperand = token.nextToken();
          // TODO: set delta operand to corresponding type descriptor
        }
      }
    }

  }
}
