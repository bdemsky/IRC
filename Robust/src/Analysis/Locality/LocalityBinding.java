package Analysis.Locality;
import IR.MethodDescriptor;

public class LocalityBinding {
    private MethodDescriptor md;
    private Integer[] isglobal;
    private boolean isatomic;
    private Integer isglobalreturn;
    private Integer isglobalthis;
    private LocalityBinding parent;

    public LocalityBinding(MethodDescriptor md, boolean atomic) {
	this.md=md;
	isglobal=new Integer[md.numParameters()];
	isatomic=atomic;
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
	for(int i=0;i<isglobal.length;i++)
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

    public boolean equals(Object o) {
	if (o instanceof LocalityBinding) {
	    LocalityBinding lb=(LocalityBinding)o;
	    if (md!=lb.md)
		return false;
	    for(int i=0;i<isglobal.length;i++)
		if (!isglobal[i].equals(lb.isglobal[i]))
		    return false;
	    if (!isglobalthis.equals(lb.isglobalthis))
		return false;
	    return (isatomic==lb.isatomic);
	}
	return false;
    }

    public int hashCode() {
	int hashcode=md.hashCode();
	for(int i=0;i<isglobal.length;i++) {
	    hashcode=hashcode*31+(isglobal[i].intValue());
	}
	hashcode=hashcode*31+(isatomic?1:0);
	return hashcode;
    }
}
