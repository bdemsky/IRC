package Analysis.TaskStateAnalysis;
import Util.*;

public class TaskNodeNamer extends Namer{
	public TaskNodeNamer(){}

	public String nodeLabel(GraphNode gn){
		return "";
	}

	public String nodeOption(GraphNode gn){
		return "URL=\""+gn.getName()+".html\"";
	}

}	
