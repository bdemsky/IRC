package Analysis.Scheduling;

import java.util.Vector;

import Util.GraphNode;

public class SimExecutionNode extends GraphNode {
    
    private int nid;
    private static int nodeID=0;
    
    private int coreNum;
    private long timepoint;
    public Vector<Integer> spareCores;
    
    public SimExecutionNode(int corenum,
	                    long timepoint) {
	this.nid = SimExecutionNode.nodeID++;
	this.coreNum = corenum;
	this.timepoint = timepoint;
	this.spareCores = null;
    }

    public int getNid() {
        return nid;
    }

    public long getTimepoint() {
        return timepoint;
    }

    public int getCoreNum() {
        return coreNum;
    }

    public Vector<Integer> getSpareCores() {
        return spareCores;
    }

    public void setSpareCores(Vector<Integer> spareCores) {
        this.spareCores = spareCores;
    }
    
    public String getLabel() {
	return "N" + this.nid;
    }
}
