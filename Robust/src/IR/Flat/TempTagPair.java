package IR.Flat;
import IR.TagVarDescriptor;

public class TempTagPair {
    TagVarDescriptor tvd;
    TempDescriptor td;

    public TempTagPair(TempDescriptor td, TagVarDescriptor tvd) {
	this.tvd=tvd;
	this.td=td;
    }
    public int hashCode() {
	if (tvd!=null)
	    return tvd.hashCode()^td.hashCode();
	else
	    return td.hashCode();
    }

    public TempDescriptor getTemp() {
	return td;
    }

    public TagVarDescriptor getTag() {
	return tvd;
    }

    public boolean equals(Object o) {
	if (!(o instanceof TempTagPair))
	    return false;
	TempTagPair ttp=(TempTagPair)o;
	return ttp.tvd==tvd&&(ttp.td==td);
    }

    public String toString() {
	return "<"+tvd+","+td+">";
    }
}
