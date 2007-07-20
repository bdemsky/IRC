package Analysis.Locality;
import IR.MethodDescriptor;

public class LocalityBinding {
    private MethodDescriptor md;
    private Integer[] isglobal;
    private boolean istransaction;
    private Integer isglobalreturn;
    private Integer isglobalthis;

    public LocalityBinding(MethodDescriptor md, boolean transaction) {
	this.md=md;
	isglobal=new boolean[md.numParameters()];
	istransaction=transaction;
    }

    public String toString() {
	String st=md.toString()+" ";
	for(int i=0;i<isglobal.length;i++)
	    if (isglobal[i])
		st+="global ";
	    else
		st+="local ";
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

    public boolean isTransaction() {
	return istransaction;
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
	    return !istransaction.equals(lb.istransaction);
	}
	return false;
    }

    public int hashCode() {
	int hashcode=md.hashCode();
	for(int i=0;i<isglobal.length;i++) {
	    hashcode=hashcode*31+(isglobal[i]?0:1);
	}
	hashcode=hashcode*31+(istransaction?0:1);
	return hashcode;
    }
}
