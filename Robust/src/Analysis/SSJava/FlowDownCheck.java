package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
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
import IR.Flat.FlatFieldNode;
import IR.Tree.ArrayAccessNode;
import IR.Tree.ArrayInitializerNode;
import IR.Tree.AssignmentNode;
import IR.Tree.BlockExpressionNode;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.CastNode;
import IR.Tree.ClassTypeNode;
import IR.Tree.CreateObjectNode;
import IR.Tree.DeclarationNode;
import IR.Tree.ExpressionNode;
import IR.Tree.FieldAccessNode;
import IR.Tree.IfStatementNode;
import IR.Tree.InstanceOfNode;
import IR.Tree.Kind;
import IR.Tree.LiteralNode;
import IR.Tree.MethodInvokeNode;
import IR.Tree.NameNode;
import IR.Tree.OffsetNode;
import IR.Tree.OpNode;
import IR.Tree.SubBlockNode;
import IR.Tree.TertiaryNode;
import Util.Lattice;

public class FlowDownCheck {

  static State state;
  HashSet toanalyze;
  Hashtable<TypeDescriptor, Location> td2loc; // mapping from 'type descriptor'
  // to 'location'
  Hashtable<String, ClassDescriptor> id2cd; // mapping from 'locID' to 'class

  // descriptor'

  public FlowDownCheck(State state) {
    this.state = state;
    this.toanalyze = new HashSet();
    this.td2loc = new Hashtable<TypeDescriptor, Location>();
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
      if (cd.isClassLibrary()) {
        // doesn't care about class libraries now
        continue;
      }
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

    // post-processing for delta location
    // for a nested delta location, assigning a concrete reference to delta
    // operand
    Set<TypeDescriptor> tdSet = td2loc.keySet();
    for (Iterator iterator = tdSet.iterator(); iterator.hasNext();) {
      TypeDescriptor td = (TypeDescriptor) iterator.next();
      Location loc = td2loc.get(td);

      if (loc.getType() == Location.DELTA) {
        // if it contains delta reference pointing to another location element
        CompositeLocation compLoc = (CompositeLocation) loc;

        Location locElement = compLoc.getTuple().at(0);
        assert (locElement instanceof DeltaLocation);

        DeltaLocation delta = (DeltaLocation) locElement;
        TypeDescriptor refType = delta.getRefLocationId();
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

  private void checkDeclarationInMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
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
    }
  }

  private void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    checkBlockNode(md, md.getParameterTable(), bn);
  }

  private void checkBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {
    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      checkBlockStatementNode(md, bn.getVarTable(), bsn);
    }
  }

  private void checkBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      checkLocationFromBlockExpressionNode(md, nametable, (BlockExpressionNode) bsn);
      return;

    case Kind.DeclarationNode:
      checkLocationFromDeclarationNode(md, nametable, (DeclarationNode) bsn);
      return;

    case Kind.IfStatementNode:
      // checkLocationFromIfStatementNode(md, nametable, (IfStatementNode) bsn);
      return;

    case Kind.LoopNode:
      // checkLocationFromLoopNode(md, nametable, (LoopNode) bsn);
      return;

    case Kind.ReturnNode:
      // checkLocationFromReturnNode(md, nametable, (ReturnNode) bsn);
      return;

    case Kind.SubBlockNode:
      // checkLocationFromSubBlockNode(md, nametable, (SubBlockNode) bsn);
      return;

    case Kind.ContinueBreakNode:
      // checkLocationFromContinueBreakNode(md, nametable,(ContinueBreakNode)
      // bsn);
      return;
    }
  }

  private void checkLocationFromDeclarationNode(MethodDescriptor md, SymbolTable nametable,
      DeclarationNode dn) {
    VarDescriptor vd = dn.getVarDescriptor();

    Location destLoc = td2loc.get(vd.getType());

    ClassDescriptor localCD = md.getClassDesc();
    if (dn.getExpression() != null) {
      CompositeLocation expressionLoc = new CompositeLocation(localCD);
      expressionLoc =
          checkLocationFromExpressionNode(md, nametable, dn.getExpression(), expressionLoc);

      if (expressionLoc != null) {
        System.out.println("expressionLoc=" + expressionLoc + " and destLoc=" + destLoc);

        // checking location order

        if (!CompositeLattice.isGreaterThan(expressionLoc, destLoc, localCD)) {
          throw new Error("The value flow from " + expressionLoc + " to " + destLoc
              + " does not respect location hierarchy on the assignment " + dn.printNode(0));
        }

      }

    }

  }

  private void checkDeclarationInSubBlockNode(MethodDescriptor md, SymbolTable nametable,
      SubBlockNode sbn) {
    checkDeclarationInBlockNode(md, nametable, sbn.getBlockNode());
  }

  private void checkLocationFromBlockExpressionNode(MethodDescriptor md, SymbolTable nametable,
      BlockExpressionNode ben) {
    checkLocationFromExpressionNode(md, nametable, ben.getExpression(), null);
  }

  private CompositeLocation checkLocationFromExpressionNode(MethodDescriptor md,
      SymbolTable nametable, ExpressionNode en, CompositeLocation loc) {

    switch (en.kind()) {

    case Kind.AssignmentNode:
      return checkLocationFromAssignmentNode(md, nametable, (AssignmentNode) en, loc);

    case Kind.FieldAccessNode:
      return checkLocationFromFieldAccessNode(md, nametable, (FieldAccessNode) en, loc);

    case Kind.NameNode:
      return checkLocationFromNameNode(md, nametable, (NameNode) en, loc);

    case Kind.OpNode:
      return checkLocationFromOpNode(md, nametable, (OpNode) en);

    case Kind.CreateObjectNode:
      // checkCreateObjectNode(md, nametable, (CreateObjectNode) en, td);
      return null;

    case Kind.ArrayAccessNode:
      // checkArrayAccessNode(md, nametable, (ArrayAccessNode) en, td);
      return null;

    case Kind.LiteralNode:
      return checkLocationFromLiteralNode(md, nametable, (LiteralNode) en, loc);

    case Kind.MethodInvokeNode:
      // checkMethodInvokeNode(md,nametable,(MethodInvokeNode)en,td);
      return null;

    case Kind.OffsetNode:
      // checkOffsetNode(md, nametable, (OffsetNode)en, td);
      return null;

    case Kind.TertiaryNode:
      // checkTertiaryNode(md, nametable, (TertiaryNode)en, td);
      return null;

    case Kind.InstanceOfNode:
      // checkInstanceOfNode(md, nametable, (InstanceOfNode) en, td);
      return null;

    case Kind.ArrayInitializerNode:
      // checkArrayInitializerNode(md, nametable, (ArrayInitializerNode) en,
      // td);
      return null;

    case Kind.ClassTypeNode:
      // checkClassTypeNode(md, nametable, (ClassTypeNode) en, td);
      return null;

    default:
      return null;

    }

  }

  private CompositeLocation checkLocationFromOpNode(MethodDescriptor md, SymbolTable nametable,
      OpNode on) {

    Lattice<String> locOrder = (Lattice<String>) state.getCd2LocationOrder().get(md.getClassDesc());

    ClassDescriptor cd = md.getClassDesc();
    CompositeLocation leftLoc = new CompositeLocation(cd);
    leftLoc = checkLocationFromExpressionNode(md, nametable, on.getLeft(), leftLoc);

    CompositeLocation rightLoc = new CompositeLocation(cd);
    if (on.getRight() != null) {
      rightLoc = checkLocationFromExpressionNode(md, nametable, on.getRight(), rightLoc);
    }

    System.out.println("checking op node");
    System.out.println("left loc=" + leftLoc);
    System.out.println("right loc=" + rightLoc);

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

      Set<CompositeLocation> inputSet = new HashSet<CompositeLocation>();
      inputSet.add(leftLoc);
      inputSet.add(rightLoc);
      CompositeLocation glbCompLoc = CompositeLattice.calculateGLB(cd, inputSet, cd);
      return glbCompLoc;

    default:
      throw new Error(op.toString());
    }

    return null;

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
    } else {

      String varname = nd.toString();
      Descriptor d = (Descriptor) nametable.get(varname);

      Location localLoc = null;
      if (d instanceof VarDescriptor) {
        VarDescriptor vd = (VarDescriptor) d;
        localLoc = td2loc.get(vd.getType());
      } else if (d instanceof FieldDescriptor) {
        FieldDescriptor fd = (FieldDescriptor) d;
        localLoc = td2loc.get(fd.getType());
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
    FieldDescriptor fd = fan.getField();
    Location fieldLoc = td2loc.get(fd.getType());
    loc.addLocation(fieldLoc);

    ExpressionNode left = fan.getExpression();
    return checkLocationFromExpressionNode(md, nametable, left, loc);
  }

  private CompositeLocation checkLocationFromAssignmentNode(MethodDescriptor md,
      SymbolTable nametable, AssignmentNode an, CompositeLocation loc) {
    System.out.println("checkAssignmentNode=" + an.printNode(0));

    ClassDescriptor localCD = md.getClassDesc();
    CompositeLocation srcLocation = new CompositeLocation(md.getClassDesc());
    CompositeLocation destLocation = new CompositeLocation(md.getClassDesc());

    boolean postinc = true;
    if (an.getOperation().getBaseOp() == null
        || (an.getOperation().getBaseOp().getOp() != Operation.POSTINC && an.getOperation()
            .getBaseOp().getOp() != Operation.POSTDEC))
      postinc = false;
    if (!postinc) {
      srcLocation = checkLocationFromExpressionNode(md, nametable, an.getSrc(), srcLocation);
    }

    destLocation = checkLocationFromExpressionNode(md, nametable, an.getDest(), destLocation);

    if (!CompositeLattice.isGreaterThan(srcLocation, destLocation, localCD)) {
      throw new Error("The value flow from " + srcLocation + " to " + destLocation
          + " does not respect location hierarchy on the assignment " + an.printNode(0));
    }

    return destLocation;
  }

  private Location createCompositeLocation(FieldAccessNode fan, Location loc) {

    FieldDescriptor field = fan.getField();
    ClassDescriptor fieldCD = field.getClassDescriptor();

    assert (field.getType().getAnnotationMarkers().size() == 1);

    AnnotationDescriptor locAnnotation = field.getType().getAnnotationMarkers().elementAt(0);
    if (locAnnotation.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {
      // single location

    } else {
      // delta location
    }

    // Location localLoc=new Location(field.getClassDescriptor(),field.get)

    // System.out.println("creatingComposite's field="+field+" localLoc="+localLoc);
    ExpressionNode leftNode = fan.getExpression();
    System.out.println("creatingComposite's leftnode=" + leftNode.printNode(0));

    return loc;
  }

  private void checkDeclarationNode(MethodDescriptor md, SymbolTable nametable, DeclarationNode dn) {

    System.out.println("*** Check declarationNode=" + dn.printNode(0));

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
    System.out.println("its ad=" + ad);

    if (ad.getType() == AnnotationDescriptor.MARKER_ANNOTATION) {

      // check if location is defined
      String locationID = ad.getMarker();
      Lattice<String> lattice = (Lattice<String>) state.getCd2LocationOrder().get(cd);

      if (lattice == null || (!lattice.containsKey(locationID))) {
        throw new Error("Location " + locationID
            + " is not defined in the location hierarchy of class " + cd.getSymbol() + ".");
      }

      Location loc = new Location(cd, locationID);
      td2loc.put(vd.getType(), loc);

    } else if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {
      if (ad.getMarker().equals(SSJavaAnalysis.DELTA)) {

        CompositeLocation compLoc = new CompositeLocation(cd);

        if (ad.getData().length() == 0) {
          throw new Error("Delta function of " + vd.getSymbol() + " does not have any locations: "
              + cd.getSymbol() + ".");
        }

        String deltaStr = ad.getData();
        if (deltaStr.startsWith("LOC(")) {

          if (!deltaStr.endsWith(")")) {
            throw new Error("The declaration of the delta location is wrong at "
                + cd.getSourceFileName() + ":" + dn.getNumLine());
          }
          String locationOperand = deltaStr.substring(4, deltaStr.length() - 1);

          nametable.get(locationOperand);
          Descriptor d = (Descriptor) nametable.get(locationOperand);

          if (d instanceof VarDescriptor) {
            VarDescriptor varDescriptor = (VarDescriptor) d;
            DeltaLocation deltaLoc = new DeltaLocation(cd, varDescriptor.getType());
            // td2loc.put(vd.getType(), compLoc);
            compLoc.addLocation(deltaLoc);
          } else if (d instanceof FieldDescriptor) {
            throw new Error("Applying delta operation to the field " + locationOperand
                + " is not allowed at " + cd.getSourceFileName() + ":" + dn.getNumLine());
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

        td2loc.put(vd.getType(), compLoc);
        System.out.println("vd=" + vd + " is assigned by " + compLoc);

      }
    }

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

      Location localLoc = new Location(cd, locationID);
      td2loc.put(fd.getType(), localLoc);

    } else if (ad.getType() == AnnotationDescriptor.SINGLE_ANNOTATION) {
      if (ad.getMarker().equals(SSJavaAnalysis.DELTA)) {

        if (ad.getData().length() == 0) {
          throw new Error("Delta function of " + fd.getSymbol() + " does not have any locations: "
              + cd.getSymbol() + ".");
        }

        CompositeLocation compLoc = new CompositeLocation(cd);
        DeltaLocation deltaLoc = new DeltaLocation(cd);

        StringTokenizer token = new StringTokenizer(ad.getData(), ",");
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
        td2loc.put(fd.getType(), compLoc);

      }
    }

  }

  static class CompositeLattice {

    public static boolean isGreaterThan(Location loc1, Location loc2, ClassDescriptor priorityCD) {

      System.out.println("isGreaterThan=" + loc1 + " ? " + loc2);
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
      System.out.println("compare base location=" + compLoc1 + " ? " + compLoc2);

      int baseCompareResult = compareBaseLocationSet(compLoc1, compLoc2);
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

    private static int compareBaseLocationSet(CompositeLocation compLoc1, CompositeLocation compLoc2) {

      // if compLoc1 is greater than compLoc2, return true
      // else return false;

      Map<ClassDescriptor, Location> cd2loc1 = compLoc1.getCd2Loc();
      Map<ClassDescriptor, Location> cd2loc2 = compLoc2.getCd2Loc();

      // start to compare the first item of tuples:
      // assumes that the first item has priority than other items.

      NTuple<Location> locTuple1 = compLoc1.getBaseLocationTuple();
      NTuple<Location> locTuple2 = compLoc2.getBaseLocationTuple();

      Location priorityLoc1 = locTuple1.at(0);
      Location priorityLoc2 = locTuple2.at(0);

      assert (priorityLoc1.getClassDescriptor().equals(priorityLoc2.getClassDescriptor()));

      ClassDescriptor cd = priorityLoc1.getClassDescriptor();
      Lattice<String> locationOrder = (Lattice<String>) state.getCd2LocationOrder().get(cd);

      if (priorityLoc1.getLocIdentifier().equals(priorityLoc2.getLocIdentifier())) {
        // have the same level of local hierarchy
      } else if (locationOrder.isGreaterThan(priorityLoc1.getLocIdentifier(), priorityLoc2
          .getLocIdentifier())) {
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

      if (numEqualLoc == compLoc1.getBaseLocationSize()) {
        System.out.println(compLoc1 + " == " + compLoc2);
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
