package Analysis.Flag;
import IR.State;
import Analysis.CallGraph.CallGraph;

public class FlagAnalysis {
    State state;
    CallGraph cg;

    public FlagAnalysis(State state, CallGraph cg) {
	this.state=state;
	this.cg=cg;
    }

    /** Need: Map from tasks -> flag transitions task -> new
	objects/flags */

    public void doAnalysis() {
	
    }
    
}
