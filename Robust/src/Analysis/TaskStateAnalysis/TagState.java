package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Flat.*;
import java.util.*;
import Util.GraphNode;


public class TagState extends GraphNode {
    private TagDescriptor tag;
    private Hashtable<FlagState, Integer> flags;
    public static final int KLIMIT=2;

    public TagState() {
	this(null);
    }

    public TagState(TagDescriptor tag) {
	this.tag=tag;
	this.flags=new Hashtable<FlagState, Integer>();
    }

    public TagDescriptor getTag() {
	return tag;
    }

    public TagState[] addnewFS(FlagState fs) {
	int num=0;
	if (flags.containsKey(fs))
	    num=flags.get(fs).intValue();
	if (num<KLIMIT)
	    num++;

	TagState ts=new TagState(tag);
	ts.flags.putAll(flags);
	ts.flags.put(fs, new Integer(num));
	return new TagState[] {ts};
    }

    public TagState[] addFS(FlagState fs) {
	int num=0;
	if (flags.containsKey(fs))
	    num=flags.get(fs).intValue();
	if (num<KLIMIT)
	    num++;
	
	TagState ts=new TagState(tag);
	ts.flags.putAll(flags);
	ts.flags.put(fs, new Integer(num));
	if (num==1)
	    return new TagState[] {ts};
	else
	    return new TagState[] {this, ts};
    }

    public boolean containsFS(FlagState fs) {
	return flags.containsKey(fs);
    }

    public Set<FlagState> getFS() {
	return flags.keySet();
    }

    public int hashCode() {
	int hashcode=flags.hashCode();
	if (tag!=null)
	    hashcode^=tag.hashCode();
	return hashcode;
    }
  
    public boolean equals(Object o) {
	if (o instanceof TagState) {
	    TagState t=(TagState)o;
	    if ((tag==null&&t.tag==null)||
		(tag!=null&&t.tag!=null&&tag.equals(t.tag))) {
		return flags.equals(t.flags);
	    }
	}
	return false;
    }
}
