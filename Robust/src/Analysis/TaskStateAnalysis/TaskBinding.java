package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class TaskBinding {
    TaskQueueIterator tqi;
    Vector<Integer> decisions;
    Hashtable<TempDescriptor, TagWrapper> temptotag;
    ObjWrapper[] parameterbindings;
    boolean increment;

    public TaskBinding(TaskQueueIterator tqi) {
	this.tqi=tqi;
	this.decisions=new Vector<Integer>();
	int numobjs=tqi.ftsarray.length;
	int numtags=tqi.tq.tags.size();
	this.parameterbindings=new ObjWrapper[numobjs];
	for(int i=0;i<(numtags+numobjs);i++) {
	    decisions.add(new Integer(0));
	}
    }

    public ObjWrapper getParameter(int i) {
	return parameterbindings[i];
    }
    
    public TagWrapper getTag(TempDescriptor tmp) {
	return temptotag.get(tmp);
    }
    
    public void next() {
	increment=true;
    }

    public boolean hasNext() {
	Vector<TempDescriptor> tagv=tqi.tq.tags;
	int numtags=tagv.size();
	int numobjs=tqi.ftsarray.length;
	int incrementlevel=numtags+numobjs;
	if (increment) {
	    //actually do increment
	    incrementlevel--;
	    increment=false;
	}
	
	mainloop:
	while(true) {
	    Hashtable<TagState, Vector<TagWrapper>> ttable=new Hashtable<TagState, Vector<TagWrapper>>();
	    temptotag=new Hashtable<TempDescriptor, TagWrapper>();
	    //build current state
	    for(int i=0;i<(numtags+numobjs);i++) {
		TagState tag=null;
		TagWrapper tw=null;
		if (i>=numtags) {
		    int objindex=i-numtags;
		    tag=tqi.ftsarray[objindex].ts;
		} else {
		    TempDescriptor tmp=tagv.get(i);
		    tag=tqi.getTS(tmp);
		}
		int index=decisions.get(i).intValue();
		int currentsize=ttable.get(tag).size();
		if (i==incrementlevel) {
		    if (index==currentsize) {
			if (incrementlevel==0)
			    return false;
			incrementlevel--;
			continue mainloop;
		    } else {
			index++;
			decisions.set(i, new Integer(index));
		    }
		} else if (i>incrementlevel) {
		    index=0;
		    decisions.set(i, new Integer(index));		    
		}
		if (index>currentsize) {
		    tw=new TagWrapper(tag);
		    if (!ttable.containsKey(tag)) {
			ttable.put(tag, new Vector<TagWrapper>());
		    }
		    ttable.get(tag).add(tw);
		} else {
		    //use old instance
		    tw=ttable.get(tag).get(index);
		}
		if (i>=numtags) {
		    int objindex=i-numtags;
		    FlagTagState fts=tqi.ftsarray[objindex];
		    ObjWrapper ow=new ObjWrapper(fts.fs);
		    Hashtable <TagState,Set<TagWrapper>> ctable=new Hashtable<TagState, Set<TagWrapper>>();
		    ctable.put(tw.ts, new HashSet<TagWrapper>());
		    ctable.get(tw.ts).add(tw);
		    ow.tags.add(tw);
		    TagExpressionList tel=tqi.tq.task.getTag(tqi.tq.task.getParameter(i));
		    for(int j=0;j<tel.numTags();j++) {
			TempDescriptor tagtmp=tel.getTemp(j);
			TagWrapper twtmp=temptotag.get(tagtmp);
			if (!ctable.containsKey(twtmp.ts))
			    ctable.put(twtmp.ts, new HashSet<TagWrapper>());
			ctable.get(twtmp.ts).add(twtmp);
			ow.tags.add(twtmp);
			int tagcount=ctable.get(twtmp.ts).size();
			int fstagcount=fts.fs.getTagCount(twtmp.ts.getTag());
			if (fstagcount>=0&&(tagcount>fstagcount)) {
			    //Too many unique tags of this type bound to object wrapper
			    incrementlevel=i;
			    continue mainloop;
			}
		    }
		    parameterbindings[objindex]=ow;
		} else {
		    TempDescriptor tmp=tagv.get(i);
		    temptotag.put(tmp, tw);
		}
	    }
	    return true;
	}
    }
}
