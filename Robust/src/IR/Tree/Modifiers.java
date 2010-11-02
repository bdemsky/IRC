package IR.Tree;

public class Modifiers {
  public static final int PUBLIC=1;
  public static final int PROTECTED=2;
  public static final int PRIVATE=4;
  public static final int STATIC=8;
  public static final int ABSTRACT=16;
  public static final int FINAL=32;
  public static final int NATIVE=64;
  public static final int SYNCHRONIZED=128;
//	TRANSIENT=256
  public static final int VOLATILE=512;
//	STRICTFP=1024
  public static final int ATOMIC=2048;


  private int value;

  public Modifiers() {
    value=0;
  }

  public Modifiers(int v) {
    value=v;
  }

  public void addModifier(int mod) {
    value|=mod;
    if (isSynchronized()&&isNative())
      throw new Error("Synchronized native methods are not supported");
  }

  public boolean isAtomic() {
    return ((value&ATOMIC)!=0);
  }
  
  public boolean isAbstract() {
    return ((value&ABSTRACT)!=0);
  }

  public boolean isSynchronized() {
    return ((value&SYNCHRONIZED)!=0);
  }

  public boolean isStatic() {
    return ((value&STATIC)!=0);
  }

  public boolean isNative() {
    return ((value&NATIVE)!=0);
  }

  public boolean isFinal() {
    return ((value&FINAL)!=0);
  }
  
  public boolean isVolatile() {
    return ((value&VOLATILE)!= 0);
  }

  public String toString() {
    String st="";
    if ((value&PUBLIC)!=0)
      st+="public ";
    if ((value&PROTECTED)!=0)
      st+="protected ";
    if ((value&PRIVATE)!=0)
      st+="private ";
    if ((value&STATIC)!=0)
      st+="static ";
    if ((value&FINAL)!=0)
      st+="final ";
    if ((value&NATIVE)!=0)
      st+="native ";
    if ((value&SYNCHRONIZED)!=0)
      st+="synchronized ";
    if ((value&ATOMIC)!=0)
      st+="atomic ";
    if ((value&ABSTRACT)!=0)
      st+="abstract ";
    if((value&VOLATILE)!=0)
      st += "volatile ";
    return st;
  }
}
