package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class TagWrapper implements Wrapper {
    TagState initts;
    Vector<TagState> ts;

    public TagWrapper(TagState ts) {
	this.initts=ts;
	this.ts=new Vector<TagState>();
	this.ts.addAll(ts);
    }

    public TagWrapper clone() {
	TagWrapper tw=new TagWrapper();
	tw.initts=initts;
	tw.ts=ts.clone();
	return tw;
    }
}
