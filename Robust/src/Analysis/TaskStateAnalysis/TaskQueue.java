package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class TaskQueue {
    protected TaskDescriptor task;
    protected HashSet<FlagTagState> []parameterset;
    protected Vector<TempDescriptor> tags;
    protected Hashtable <FlagState, Vector<FlagTagState>> map;

    public int numParameters() {
	return parameterset.length;
    }

    public TaskDescriptor getTask() {
	return task;
    }

    public TaskQueue(TaskDescriptor td) {
	this.task=td;
	this.parameterset=(HashSet<FlagTagState>[])new HashSet[task.numParameters()];
	this.map=new Hashtable<FlagState, Vector<FlagTagState>>();
	this.tags=new Vector<TempDescriptor>();
	for(int i=0;i<task.numParameters();i++) {
	    this.parameterset[i]=new HashSet<FlagTagState>();
	    TagExpressionList tel=td.getTag(td.getParameter(i));
	    if (tel!=null)
		for(int j=0;j<tel.numTags();j++) {
		    TempDescriptor tagtmp=tel.getTemp(j);
		    if (!tags.contains(tagtmp))
			tags.add(tagtmp);
		}
	}
    }
    
    public TaskQueueIterator enqueue(int index, FlagTagState fts) {
	parameterset[index].add(fts);
	if (!map.containsKey(fts.fs)) {
	    map.put(fts.fs, new Vector<FlagTagState>());
	}
	map.get(fts.fs).add(fts);
	return new TaskQueueIterator(this, index, fts);
    }
}
