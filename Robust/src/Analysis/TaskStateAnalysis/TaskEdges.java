package Analysis.TaskStateAnalysis;
import Util.*;

public class TaskEdges extends Namer{
	public TaskEdges(){}

	public String nodeLabel(GraphNode gn) {
	return "";
    }
    
    public String nodeOption(GraphNode gn) {
	return "";
    }
	
	
	public String edgeLabel(Edge edge){
		return "";
	}
	

	public String edgeOption(Edge edge){
		return "URL=\""+edge.getLabel()+".html\"";
	}

}	
