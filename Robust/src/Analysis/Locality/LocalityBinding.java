package Analysis.Locality;
import IR.MethodDescriptor;

public class LocalityBinding {
  private MethodDescriptor md;
  private Integer[] isglobal;
  private boolean isatomic;
  private Integer isglobalreturn;
  private Integer isglobalthis;
  private LocalityBinding parent;
  private boolean hasatomic;

  public LocalityBinding(MethodDescriptor md, boolean atomic) {
    this.md=md;
    isglobal=new Integer[md.numParameters()];
    isatomic=atomic;
  }

  public void setHasAtomic() {
    hasatomic=true;
  }

  public boolean getHasAtomic() {
    return hasatomic;
  }

  private static String globalToString(Integer g) {
    if (g==null)
      return "";
    else if (g==LocalityAnalysis.GLOBAL)
      return "G";
    else if (g==LocalityAnalysis.LOCAL)
      return "L";
    else if (g==LocalityAnalysis.EITHER)
      return "E";
    else if (g==LocalityAnalysis.CONFLICT)
      return "C";
    else throw new Error();
  }

  public String getSignature() {
    if (md.getModifiers().isNative())
      return "";
    String st="_";
    if (isatomic) {
      st+="A";
    } else
      st+="N";
    if (isglobalthis==null)
      st+="N";
    else
      st+=globalToString(isglobalthis);
    for(int i=0; i<isglobal.length; i++) {
      st+=globalToString(isglobal[i]);
    }
    st+="_";
    return st;
  }

  /* Use this for an explanation */
  public void setParent(LocalityBinding lb) {
    parent=lb;
  }

  public String getExplanation() {
    if (parent==null)
      return toString();
    else
      return parent.getExplanation()+"\n"+toString();
  }

  public String toString() {
    String st=md.toString()+" ";
    if (isglobalthis==null) {
      st+="[static] ";
    } else {
      if (isglobalthis.equals(LocalityAnalysis.LOCAL))
	st+="[local] ";
      else if (isglobalthis.equals(LocalityAnalysis.GLOBAL))
	st+="[global] ";
      else if (isglobalthis.equals(LocalityAnalysis.EITHER))
	st+="[either] ";
      else if (isglobalthis.equals(LocalityAnalysis.CONFLICT))
	st+="[conflict] ";
    }
    for(int i=0; i<isglobal.length; i++)
      if (isglobal[i].equals(LocalityAnalysis.LOCAL))
	st+="local ";
      else if (isglobal[i].equals(LocalityAnalysis.GLOBAL))
	st+="global ";
      else if (isglobal[i].equals(LocalityAnalysis.EITHER))
	st+="either ";
      else if (isglobal[i].equals(LocalityAnalysis.CONFLICT))
	st+="conflict ";
    return st;
  }

  public void setGlobal(int i, Integer global) {
    isglobal[i]=global;
  }

  public Integer isGlobal(int i) {
    return isglobal[i];
  }

  public void setGlobalReturn(Integer global) {
    isglobalreturn=global;
  }

  public Integer getGlobalReturn() {
    return isglobalreturn;
  }

  public void setGlobalThis(Integer global) {
    isglobalthis=global;
  }

  public Integer getGlobalThis() {
    return isglobalthis;
  }

  public MethodDescriptor getMethod() {
    return md;
  }

  public boolean isAtomic() {
    return isatomic;
  }

  public boolean contextMatches(LocalityBinding lb) {
    if (isglobal.length!=lb.isglobal.length)
      return false;
    for(int i=0; i<isglobal.length; i++)
      if (!equiv(isglobal[i],lb.isglobal[i]))
	return false;

    if (!equiv(isglobalthis, lb.isglobalthis))
      return false;
    return (isatomic==lb.isatomic);
  }

  public static boolean equiv(Integer a, Integer b) {
    if (a==null) {
      return b==null;
    } else if (b==null) {
      //a is not null
      return false;
    } else return a.equals(b);
  }

  public boolean equals(Object o) {
    if (o instanceof LocalityBinding) {
      LocalityBinding lb=(LocalityBinding)o;
      if (md!=lb.md)
	return false;

      for(int i=0; i<isglobal.length; i++)
	if (!equiv(isglobal[i], lb.isglobal[i]))
	  return false;

      if (!equiv(isglobalthis, lb.isglobalthis))
	return false;
      return (isatomic==lb.isatomic);
    }
    return false;
  }

  public int hashCode() {
    int hashcode=md.hashCode();
    for(int i=0; i<isglobal.length; i++) {
      if (isglobal[i]!=null)
	hashcode=hashcode*31+(isglobal[i].intValue());
    }
    hashcode=hashcode*31+(isatomic ? 1 : 0);
    return hashcode;
  }
}
