package Analysis.TaskStateAnalysis;
import IR.TaskDescriptor;

public class TaskIndex {
    TaskDescriptor td;
    int index;
    public TaskIndex(TaskDescriptor td, int index) {
	this.td=td;
	this.index=index;
    }

    public int hashCode() {
	return td.hashCode()^index;
    }

    public boolean equals(Object o) {
	if (o instanceof TaskIndex) {
	    TaskIndex ti=(TaskIndex) o;
	    if (ti.index==index && ti.td==td)
		return true;
	}
	return false;
    }

    public TaskDescriptor getTask() {
	return td;
    }
    public int getIndex() {
	return index;
    }
}
