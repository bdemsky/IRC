package Analysis.Scheduling;

import java.util.Iterator;

import Analysis.TaskStateAnalysis.*;
import Util.Edge;
import Util.GraphNode;

/* Edge *****************/
public class ScheduleEdge extends Edge {

  public final static int NEWEDGE = 0;
  public final static int TRANSEDGE = 1;

  protected int uid;
  protected int gid;
  protected static int nodeID=0;

  protected String label;
  protected final FlagState fstate;
  protected int type;   // 0--new edge: indicate creating new objects
                        // 1--transmit edge: indicate transimitting an existing object

  protected FlagState targetFState;

  private ClassNode sourceCNode;
  private ClassNode targetCNode;

  private int probability;
  private int transTime;
  private int listExeTime;

  private FEdge fedge;
  private int newRate;

  private boolean isclone;

  /** Class Constructor
   *
   */
  public ScheduleEdge(ScheduleNode target, String label, FlagState fstate, int type, int gid) {
    super(target);
    this.uid = ScheduleEdge.nodeID++;
    this.gid = gid;
    this.fedge = null;
    this.targetFState = null;
    this.sourceCNode = null;
    this.targetCNode = null;
    this.label = label;
    this.fstate = fstate;
    this.newRate = -1;
    this.probability = 100;
    this.transTime = -1;
    this.listExeTime = -1;
    this.isclone = false;
    this.type = type;
  }

  public boolean isclone() {
    return isclone;
  }

  public void setIsclone(boolean isclone) {
    this.isclone = isclone;
  }

  public void setTarget(GraphNode sn) {
    this.target = sn;
  }

  public int getType() {
    return type;
  }

  public String getLabel() {
    String completeLabel = label;
    if(ScheduleEdge.NEWEDGE == this.type) {
      completeLabel += ":" + Integer.toString(this.newRate);
    }
    completeLabel += ":(" + Integer.toString(this.probability) + "%)" + ":[" + Integer.toString(this.transTime) + "]";
    return completeLabel;
  }

  public FlagState getFstate() {
    return fstate;
  }

  public FEdge getFEdge() {
    return this.fedge;
  }

  public void setFEdge(FEdge fEdge) {
    this.fedge = fEdge;
  }

  public FlagState getSourceFState() {
    if(this.fedge == null) {
      return null;
    }
    return (FlagState) this.fedge.getTarget();
  }

  public void setTargetFState(FlagState targetFState) {
    this.targetFState = targetFState;
  }

  public FlagState getTargetFState() {
    return this.targetFState;
  }

  public int getProbability() {
    return this.probability;
  }

  public int getNewRate() {
    return this.newRate;
  }

  public ClassNode getSourceCNode() {
    return this.sourceCNode;
  }

  public void setSourceCNode(ClassNode sourceCNode) {
    this.sourceCNode = sourceCNode;
  }

  public ClassNode getTargetCNode() {
    return this.targetCNode;
  }

  public void setTargetCNode(ClassNode targetCNode) {
    this.targetCNode = targetCNode;
    this.transTime = targetCNode.getTransTime();
  }

  public boolean equals(Object o) {
    if (o instanceof ScheduleEdge) {
      ScheduleEdge e=(ScheduleEdge)o;
      if(e.gid == this.gid) {
	if(e.uid != this.uid) {
	  return false;
	}
      }
      if ((e.label.equals(label))&&
          (e.target.equals(target))&&
          (e.source.equals(source)) &&
          (e.fstate.equals(fstate)) &&
          (e.sourceCNode.equals(sourceCNode)) &&
          (e.targetCNode.equals(targetCNode)) &&
          (e.newRate == newRate) &&
          (e.probability == probability) &&
          (e.type == type) &&
          (e.transTime == transTime) &&
          (e.listExeTime == listExeTime))
	if(e.targetFState != null) {
	  if(!e.targetFState.equals(targetFState)) {
	    return false;
	  }
	} else if(this.targetFState != null) {
	  return false;
	}
      if(e.fedge != null) {
	return e.fedge.equals(fedge);
      } else if(this.fedge == null) {
	return true;
      }
    }
    return false;
  }

  public int hashCode() {
    int hashcode = gid^uid^label.hashCode()^target.hashCode()^source.hashCode()^fstate.hashCode()^
                   sourceCNode.hashCode()^targetCNode.hashCode()^newRate^probability^
                   type^transTime^listExeTime;
    if(targetFState != null) {
      hashcode ^= targetFState.hashCode();
    }
    if(fedge != null) {
      hashcode ^= fedge.hashCode();
    }
    return hashcode;
  }

  public void setProbability(int prob) {
    this.probability = prob;
  }

  public void setNewRate(int nr) {
    this.newRate = nr;
  }

  public int getTransTime() {
    return this.transTime;
  }

  public void setTransTime(int transTime) {
    this.transTime = transTime;
  }

  public int getListExeTime() {
    if(listExeTime == -1) {
      // calculate the lisExeTime
      listExeTime = ((ScheduleNode) this.getTarget()).getExeTime() + this.getTransTime() * this.getNewRate();
      Iterator it_edges = this.getTarget().edges();
      int temp = 0;
      if(it_edges.hasNext()) {
	temp = ((ScheduleEdge)it_edges.next()).getListExeTime();
      }
      while(it_edges.hasNext()) {
	int tetime = ((ScheduleEdge)it_edges.next()).getListExeTime();
	if(temp < tetime) {
	  temp = tetime;
	}
      }
      listExeTime += temp;
    }
    return this.listExeTime;
  }

  public void resetListExeTime() {
    this.listExeTime = -1;
  }
}
