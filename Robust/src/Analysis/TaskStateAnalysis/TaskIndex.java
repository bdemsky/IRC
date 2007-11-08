package Analysis.TaskStateAnalysis;
import IR.TaskDescriptor;

public class TaskIndex {
    TaskDescriptor td;
    int index;
    boolean runtime;
    public TaskIndex(TaskDescriptor td, int index) {
	this.td=td;
	this.index=index;
	runtime=false;
    }

    public TaskIndex() {
	runtime=true;
    }

    public boolean isRuntime() {
	return runtime;
    }

    public int hashCode() {
	if (runtime)
	    return 71;
	return td.hashCode()^index;
    }

    public boolean equals(Object o) {
	if (o instanceof TaskIndex) {
	    TaskIndex ti=(TaskIndex) o;
	    if (ti.runtime==runtime)
		return true;

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
