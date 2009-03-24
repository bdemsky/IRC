package Analysis.OwnershipAnalysis;

public class ReachOperation {
    Canonical a;
    Canonical b;
    public Canonical c;

    public ReachOperation(Canonical a, Canonical b) {
	assert a.canonicalvalue!=0;
	assert b.canonicalvalue!=0;
	this.a=a;
	this.b=b;
    }
    
    public int hashCode() {
	return a.canonicalvalue^(b.canonicalvalue<<1);
    }
    public boolean equals(Object o) {
	ReachOperation ro=(ReachOperation)o;
	return ro.a.canonicalvalue==a.canonicalvalue&&
	    ro.b.canonicalvalue==b.canonicalvalue;
    }
}