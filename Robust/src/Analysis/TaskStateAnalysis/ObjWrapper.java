package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class ObjWrapper {
    FlagState fs;
    Vector<TagWrapper> tags;

    public ObjWrapper(FlagState fs) {
	this.fs=fs;
	tags=new Vector<TagWrapper>();
    }

}
