package IR.Flat;
import IR.TagDescriptor;

public class TempTagPair {
    TagDescriptor tagd;
    TempDescriptor td;
    TempDescriptor tagt;

    public TempTagPair(TempDescriptor td, TagDescriptor tagd, TempDescriptor tagt) {
	this.tagd=tagd;
	this.tagt=tagt;
	this.td=td;
    }
    public int hashCode() {
	return tagd.hashCode()^td.hashCode()^tagt.hashCode();
    }
    
    public TempDescriptor getTemp() {
	return td;
    }

    public TagDescriptor getTag() {
	return tagd;
    }

    public TempDescriptor getTagTemp() {
	return tagt;
    }

    public boolean equals(Object o) {
	if (!(o instanceof TempTagPair))
	    return false;
	TempTagPair ttp=(TempTagPair)o;
	return ttp.tagd.equals(tagd)&&ttp.tagt==tagt&&ttp.td==td;
    }

    public String toString() {
	return "<"+td+","+tagd+","+tagt+">";
    }
}
