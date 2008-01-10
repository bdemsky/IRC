package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class TagWrapper implements Wrapper {
    TagState initts;
    HashSet<TagState> ts;

    public TagWrapper(TagState ts) {
	this.initts=ts;
	this.ts=new HashSet<TagState>();
	this.ts.add(ts);
    }

    private TagWrapper() {
    }

    public TagWrapper clone() {
	TagWrapper tw=new TagWrapper();
	tw.initts=initts;
	tw.ts=(HashSet<TagState>)ts.clone();
	return tw;
    }
}
