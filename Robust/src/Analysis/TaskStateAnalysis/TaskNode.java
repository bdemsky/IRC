package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;
import Util.GraphNode;

public class TaskNode extends GraphNode {
	
    private final String name;
    protected int uid;
    private static int nodeid=0;
    // private int loopmarker=0;
    //private boolean multipleparams=false;
    /**Class Constructor
     * Creates a new TaskNode using the TaskDescriptor.
     * @param tasknode TaskDescriptor
     */
    public TaskNode(String name){
	    this.name=name;
	    this.uid=TaskNode.nodeid++;
    }
     
    /**Returns the string representation of the node 
     * @return string representation of the tasknode (e.g "Task foo")
     */
    public String getTextLabel() {
		return "Task "+name;
 	}
 	
 	public String getLabel() {
	return "N"+uid;
    }

    public String getName(){
	    return name;
    }

    // public int getuid(){
    //return uid;
    //}
	
 	
 	/**toString method.
 	 * @return  string representation of the tasknode (e.g "Task foo")
 	 */
 	public String toString(){
	 	return getTextLabel();
 	}
 	
 	public int hashCode(){
	 	return name.hashCode();
	 	
 	}
 	
 	public boolean equals(Object o) {
        if (o instanceof TaskNode) {
           TaskNode tn=(TaskNode)o;
           return (tn.name.equals(name));
        }
        return false;
    }
     
    public boolean edgeExists(TEdge newedge){
	    if(edges.isEmpty())
	    	return false;
	    else
	        return edges.contains(newedge);
    }
    
}
	
     
     
