package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class FlagTagState {
    TagState ts;
    FlagState fs;

    public FlagTagState(TagState ts, FlagState fs) {
	this.ts=ts;
	this.fs=fs;
    }

    public boolean equals(Object o) {
	if (o instanceof FlagTagState) {
	    FlagTagState fts=(FlagTagState) o;
	    return ts.equals(fts.ts)&&fs.equals(fts.fs);
	}
	return false;
    }

    public int hashCode() {
	return ts.hashCode()^fs.hashCode();
    }
}
