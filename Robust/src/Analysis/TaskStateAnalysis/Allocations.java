package Analysis.TaskStateAnalysis;
import Util.*;

public class Allocations extends Namer {
    public Allocations() {}

    public String nodeLabel(GraphNode gn) {
	return "";
    }
    
    public String nodeOption(GraphNode gn) {
	FlagState fs=(FlagState)gn;
	if (fs.isSourceNode())
	    return "peripheries=2";
	else
	    return "";
    }

    public String edgeLabel(Edge e) {
	return "";
    }

    public String edgeOption(Edge e) {
	return "";
    }
}
