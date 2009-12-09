package Analysis.Disjoint;

public class CanonicalWrapper {
    Canonical a;
    public Canonical b;
    
    public CanonicalWrapper(Canonical a) {
	assert a.canonicalvalue!=0;
	this.a=a;
    }
    public int hashCode() {
	return a.canonicalvalue;
    }
    public boolean equals(Object o) {
	CanonicalWrapper ro=(CanonicalWrapper)o;
	return ro.a.canonicalvalue==a.canonicalvalue;
    }
}