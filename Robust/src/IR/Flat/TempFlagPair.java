package IR.Flat;
import IR.FlagDescriptor;

public class TempFlagPair {
    FlagDescriptor fd;
    TempDescriptor td;

    public TempFlagPair(TempDescriptor td, FlagDescriptor fd) {
	this.fd=fd;
	this.td=td;
    }
    public int hashCode() {
	return fd.hashCode()^td.hashCode();
    }

    public TempDescriptor getTemp() {
	return td;
    }

    public boolean equals(Object o) {
	if (!(o instanceof TempFlagPair))
	    return false;
	TempFlagPair tfp=(TempFlagPair)o;
	return (tfp.fd==fd)&&(tfp.td==td);
    }

    public String toString() {
	return "<"+fd+","+td+">";
    }
}
