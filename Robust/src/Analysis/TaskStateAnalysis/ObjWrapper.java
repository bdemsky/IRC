package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class ObjWrapper implements Wrapper{
    Flagstate initfs;
    HashSet<FlagState> fs;
    HashSet<TagWrapper> tags;

    public ObjWrapper(FlagState fs) {
	this.initfs=fs;
	this.fs=new HashSet<FlagState>();
	this.fs.add(fs);
	tags=new HashSet<TagWrapper>();
    }

}
