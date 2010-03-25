package Analysis.Disjoint;

// a CanonicalOperation defines an operation on 
// Canonical objects.  The Canonical class maps
// an op to its result, so when you ask the
// Canonical static methods to do an op that is
// non-trivial, it will generate one of these
// first and do a result look-up
public class CanonicalOp {

  public static final int REACHSTATE_ATTACH_EXISTPREDSET       = 0x8358;
  public static final int REACHSTATE_UNION_REACHSTATE          = 0x5678;
  public static final int REACHSTATE_ADD_REACHTUPLE            = 0x32b6;
  public static final int REACHSTATE_UNIONUPARITY_REACHSTATE   = 0x9152;
  public static final int REACHSTATE_REMOVE_REACHTUPLE         = 0x8173;
  public static final int REACHSTATE_AGETUPLESFROM_ALLOCSITE   = 0x4f65;
  public static final int REACHSET_UNIONORPREDS_REACHSET       = 0x2131;
  public static final int REACHSET_INTERSECTION_REACHSET       = 0x3361;
  public static final int REACHSET_REMOVE_REACHSTATE           = 0x9391;
  public static final int REACHSET_APPLY_CHANGESET             = 0x1d55;
  public static final int REACHSET_UNIONTOCHANGESET_REACHSET   = 0x46a9;
  public static final int REACHSET_AGETUPLESFROM_ALLOCSITE     = 0x22bb;
  public static final int REACHSET_PRUNEBY_REACHSET            = 0xd774;
  public static final int CHANGESET_UNION_CHANGESET            = 0x53b3;
  public static final int CHANGESET_UNION_CHANGETUPLE          = 0x9ee4;
  public static final int EXISTPREDSET_JOIN_EXISTPREDSET       = 0x8a21;
  public static final int EXISTPREDSET_ADD_EXISTPRED           = 0xba5f;
  public static final int PRIM_OP_UNUSED                       = 0xef01;
  public static final int REACHSET_TOCALLEECONTEXT_ALLOCSITE   = 0x56f6;
  public static final int REACHSTATE_TOCALLEECONTEXT_ALLOCSITE = 0x7faf;
  public static final int REACHSET_TOCALLERCONTEXT_ALLOCSITE   = 0x2f6a;
  public static final int REACHSTATE_TOCALLERCONTEXT_ALLOCSITE = 0xb2b1;
  public static final int REACHSET_UNSHADOW_ALLOCSITE          = 0x1049;
  public static final int REACHSTATE_UNSHADOW_ALLOCSITE        = 0x08ef;
  public static final int REACHSTATE_MAKEPREDSTRUE             = 0x0b9c;
  public static final int REACHSET_MAKEPREDSTRUE               = 0xdead;


  protected int opCode;
  protected Canonical operand1;
  protected Canonical operand2;
  protected int       operand3;

  public CanonicalOp( int       opc,
                      Canonical op1, 
                      Canonical op2 ) {
    this( opc, op1, op2, PRIM_OP_UNUSED );
  }

  public CanonicalOp( int       opc,
                      Canonical op1, 
                      Canonical op2,
                      int       op3 ) {
    assert op1.isCanonical();
    assert op2.isCanonical();
    opCode   = opc;
    operand1 = op1;
    operand2 = op2;
    operand3 = op3;
  }
  
  public int hashCode() {
    return opCode ^
      (operand1.getCanonicalValue() << 2) ^
      (operand2.getCanonicalValue() << 1) ^
      (operand3 << 3);
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    CanonicalOp co = (CanonicalOp) o;
    return opCode == co.opCode &&
      (operand1.getCanonicalValue() == co.operand1.getCanonicalValue()) &&
      (operand2.getCanonicalValue() == co.operand2.getCanonicalValue()) &&
      operand3 == co.operand3;
  }
}
