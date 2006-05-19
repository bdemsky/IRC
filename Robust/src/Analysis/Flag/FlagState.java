package Analysis.Flag;
import java.util.*;
import IR.FlagDescriptor;

public class FlagState {
    HashSet flags;

    public FlagState() {
	flags=new HashSet();
    }

    public void setFlag(FlagDescriptor fd, boolean status) {
	if (status)
	    flags.add(fd);
	else 
	    flags.remove(fd);
    }

    public boolean getFlagState(FlagDescriptor fd) {
	return flags.contains(fd);
    }
    
    public Set getFlags() {
	return flags;
    }

    public boolean equals(Object o) {
	if (!(o instanceof FlagState))
	    return false;
	FlagState fs=(FlagState)o;
	return fs.flags.equals(flags);
    }

    public int hashCode() {
	return flags.hashCode();
    }
}
