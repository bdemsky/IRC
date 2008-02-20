package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import IR.ClassDescriptor;

public class ObjectSimulator {
    ClassDescriptor cd;
    FlagState currentFS;
    boolean changed;
    
    public ObjectSimulator(ClassDescriptor cd, FlagState currentFS) {
	super();
	this.cd = cd;
	this.currentFS = currentFS;
	this.changed = true;
    }
    
    public void applyEdge(FEdge fedge) {
	if(!currentFS.equals((FlagState)fedge.getTarget())) {
	    this.changed = true;
	    currentFS = (FlagState)fedge.getTarget();
	} else {
	    this.changed = false;
	}
    }

    public ClassDescriptor getCd() {
        return cd;
    }

    public FlagState getCurrentFS() {
        return currentFS;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setCurrentFS(FlagState currentFS) {
        /*if(!this.currentFS.equals(currentFS)) {
            changed = true;
            this.currentFS = currentFS;
        } else {
            changed = false;
        }*/
	changed = true;
        this.currentFS = currentFS;
    }
    
}