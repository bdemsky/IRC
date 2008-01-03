package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.*;
import IR.*;
import java.util.*;

import Util.GraphNode;

/** This class holds a flag diagram for one class.
 */
public class ClassNode extends GraphNode implements Cloneable{
    
    private int uid;
    private static int nodeID=0;

    private final ClassDescriptor cd;
    private ScheduleNode sn;
    private Vector<FlagState> flagStates;
    private boolean sorted = false;

    /** Class constructor
     *	@param cd ClassDescriptor
     *  @param fStates
     */
    public ClassNode(ClassDescriptor cd, Vector<FlagState> fStates) {
	this.cd=cd;
	this.flagStates = fStates;
	this.sn = null;
	this.uid=ClassNode.nodeID++;
    }
   
    public int getuid() {
    	return uid;
    }
    
    public ScheduleNode getScheduleNode() {
    	return this.sn;
    }
    
    public void setScheduleNode(ScheduleNode sn) {
    	this.sn = sn;
    }
    
    public boolean isSorted() {
    	return sorted;
    }
    
    public void setSorted(boolean sorted) {
    	this.sorted = sorted;
    }
    
    public Vector<FlagState> getFlagStates() {
    	return flagStates;
    }
    
    public String toString() {
    	return cd.toString()+getTextLabel();
    }

    /** @return Iterator over the flags in the flagstate.
     */
     
    public Iterator getFlags() {
    	return flagStates.iterator();
    }

    public int numFlags(){
    	return flagStates.size();
    }
    
    /** Accessor method
     *  @return returns the classdescriptor of the flagstate.
     */
	 
    public ClassDescriptor getClassDescriptor(){
    	return cd;
    }
    
    /** Tests for equality of two flagstate objects.
    */
    
    public boolean equals(Object o) {
        if (o instanceof ClassNode) {
	    ClassNode fs=(ClassNode)o;
            if ((fs.getClassDescriptor()!= cd) || 
		(fs.getScheduleNode() != sn) || 
		(fs.isSorted() != sorted)) {
                return false;
            }
	    return (fs.getFlagStates().equals(flagStates));
        }
        return false;
    }

    public int hashCode() {
        return cd.hashCode()^flagStates.hashCode();
    }

    public String getLabel() {
    	return "N_"+uid;
    }
    
    public String getClusterLabel() {
    	return "cluster_"+uid;
    }

    public String getTextLabel() {
	String label=null;
	label = "Class " + this.cd.getSymbol();
	
	if (label==null)
	    return " ";
	return label;
    }
    
    public Object clone() {
    	ClassNode o = null;
    	try {
	    o = (ClassNode)super.clone();
    	} catch(CloneNotSupportedException e){
	    e.printStackTrace();
    	}
    	o.uid = ClassNode.nodeID++;
    	return o;
    }
}
