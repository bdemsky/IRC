package Analysis.Scheduling;

import java.util.*;

import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Util.GraphNode;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class ScheduleNode extends GraphNode implements Cloneable {

  private int uid;
  private int gid;
  private int cid;
  private static int nodeID=0;
  public static int colorID = 0;

  private Vector<ClassNode> classNodes;
  private Vector<ScheduleEdge> scheduleEdges;

  private long executionTime;

  private int hashcid;

  /** Class constructor
   *	@param cd ClassDescriptor
   *  @param fStates
   */
  public ScheduleNode(int gid) {
    this.uid = ScheduleNode.nodeID++;
    this.gid = gid;
    this.cid = -1;
    this.executionTime = -1;
    this.classNodes = null;
    this.scheduleEdges = null;
    this.hashcid = 0;
  }

  public ScheduleNode(ClassNode cn, int gid) {
    this.uid = ScheduleNode.nodeID++;
    this.gid = gid;
    this.cid = -1;
    this.classNodes = new Vector<ClassNode>();
    this.scheduleEdges = new Vector<ScheduleEdge>();
    this.classNodes.add(cn);
    this.addEdge(cn.getEdgeVector());
    this.executionTime = -1;
    this.hashcid = 0;
  }

  public int getGid() {
    return gid;
  }

  public int getuid() {
    return uid;
  }

  public int getCid() {
    return cid;
  }

  public void setCid(int cid) {
    this.cid = cid;
  }

  public String toString() {
    String temp = new String("");
    for(int i = 0; i < classNodes.size(); i++) {
      temp += classNodes.elementAt(i).getClassDescriptor().toString() + ", ";
    }
    temp += getTextLabel();
    return temp;
  }

  public Vector getClassNodes() {
    return classNodes;
  }

  public Iterator getClassNodesIterator() {
    if(classNodes == null) {
      return null;
    }
    return classNodes.iterator();
  }

  public void resetClassNodes() {
    classNodes = null;
  }

  public Vector getScheduleEdges() {
    return scheduleEdges;
  }

  public Iterator getScheduleEdgesIterator() {
    if(scheduleEdges == null) {
      return null;
    }
    return scheduleEdges.iterator();
  }

  public void resetScheduleEdges() {
    scheduleEdges = null;
  }

  public int getHashcid() {
    return hashcid;
  }

  public void computeHashcid() {
    this.hashcid = 0;
    /*if(this.mergedcids != null) {
        for(int i = 0; i < this.mergedcids.size(); i++) {
            this.hashcid = this.hashcid * 31 + this.mergedcids.elementAt(i);
        }
       }*/
    Vector<Integer> mergedcids = new Vector<Integer>();
    for(int i = 0; i < this.classNodes.size(); i++) {
      int tomerge = this.classNodes.elementAt(i).getCid();
      mergedcids.add(tomerge);
      // insert tomerge in accent order
      int j = mergedcids.size() - 1;
      for(; j > 0; j--) {
	int tmp = mergedcids.elementAt(j-1);
	if(tmp > tomerge) {
	  mergedcids.setElementAt(tmp, j);
	} else {
	  break;
	}
      }
      mergedcids.setElementAt(tomerge, j);
    }
    for(int i = 0; i < mergedcids.size(); i++) {
      this.hashcid = this.hashcid * 31 + mergedcids.elementAt(i);
    }
    mergedcids = null;
  }

  public long getExeTime() {
    if(this.executionTime == -1) {
      try {
	calExeTime();
      } catch (Exception e) {
	e.printStackTrace();
      }
    }
    return this.executionTime;
  }

  public void calExeTime() throws Exception {
    if(this.classNodes.size() != 1) {
      throw new Exception("Error: there are multiple ClassNodes inside the ScheduleNode when calculating executionTime");
    }
    ClassNode cn = this.classNodes.elementAt(0);
    if(!cn.isSorted()) {
      throw new Error("Error: Non-sorted ClassNode!");
    }
    this.executionTime = cn.getFlagStates().elementAt(0).getExeTime();
  }

  /** Tests for equality of two flagstate objects.
   */

  public boolean equals(Object o) {
    if (o instanceof ScheduleNode) {
      ScheduleNode fs=(ScheduleNode)o;
      if(fs.gid == this.gid) {
	if(fs.uid != this.uid) {
	  return false;
	}
	if(fs.cid != this.cid) {
	  return false;
	}
      }
      if ((fs.executionTime != this.executionTime)) {
	return false;
      }
      if(fs.classNodes != null) {
	if(!fs.classNodes.equals(classNodes)) {
	  return false;
	}
      } else if(classNodes != null) {
	return false;
      }
      return true;
    }
    return false;
  }

  public int hashCode() {
    int hashcode = gid^uid^cid^(int)executionTime;
    if(this.classNodes != null) {
      hashcode ^= classNodes.hashCode();
    }
    return hashcode;
  }

  public String getLabel() {
    return "cluster" + uid;
  }

  public String getTextLabel() {
    String label=null;

    if (label==null)
      return " ";
    return label;
  }

  public Object clone(Hashtable<ClassNode, ClassNode> cn2cn,
                      int gid) {
    ScheduleNode o = null;
    try {
      o = (ScheduleNode) super.clone();
    } catch(CloneNotSupportedException e) {
      e.printStackTrace();
    }
    o.uid = ScheduleNode.nodeID++;
    o.gid = gid;
    o.cid = this.cid;
    // Clone all the internal ClassNodes and ScheduleEdges
    Vector<ClassNode> tcns = new Vector<ClassNode>();
    Vector<ScheduleEdge> tses = new Vector<ScheduleEdge>();
    int i = 0;
    for(i = 0; i < this.classNodes.size(); i++) {
      ClassNode tcn = this.classNodes.elementAt(i);
      ClassNode cn = (ClassNode)tcn.clone();
      cn.setScheduleNode(o);
      tcns.add(cn);
      cn2cn.put(tcn, cn);
    }
    for(i = 0; i < this.scheduleEdges.size(); i++) {
      ScheduleEdge temp = this.scheduleEdges.elementAt(i);
      ScheduleEdge se = null;
      switch(temp.getType()) {
      case ScheduleEdge.NEWEDGE: {
	se = new ScheduleEdge(o,
	                      "new",
	                      temp.getFstate(),
	                      ScheduleEdge.NEWEDGE,
	                      gid);
	se.setProbability(temp.getProbability());
	se.setNewRate(temp.getNewRate());
	break;
      }

      case ScheduleEdge.TRANSEDGE: {
	se = new ScheduleEdge(o,
	                      "transmit",
	                      temp.getFstate(),
	                      ScheduleEdge.TRANSEDGE,
	                      gid);
	se.setProbability(temp.getProbability());
	se.setNewRate(temp.getNewRate());
	break;
      }
      }
      se.setSourceCNode(cn2cn.get(temp.getSourceCNode()));
      se.setTargetCNode(cn2cn.get(temp.getTargetCNode()));
      se.setTransTime(temp.getTransTime());
      se.setFEdge(temp.getFEdge());
      se.setTargetFState(temp.getTargetFState());
      se.setIsclone(true);
      tses.add(se);
      // internal edge, it's source and target have been redirected to ClassNodes
      se.setTarget(se.getTargetCNode());
      se.getSourceCNode().addEdge(se);
    }
    o.classNodes = tcns;
    o.scheduleEdges = tses;
    tcns = null;
    tses = null;

    o.inedges = new Vector();
    o.edges = new Vector();
    return o;
  }

  public void mergeSEdge(ScheduleEdge se) throws Exception {
    ScheduleNode sn = (ScheduleNode)se.getTarget();
    Vector<ClassNode> targetCNodes = (Vector<ClassNode>)sn.getClassNodes();
    Vector<ScheduleEdge> targetSEdges = (Vector<ScheduleEdge>)sn.getScheduleEdges();

    for(int i = 0; i <  targetCNodes.size(); i++) {
      targetCNodes.elementAt(i).setScheduleNode(this);
    }

    if(classNodes == null) {
      classNodes = targetCNodes;
      scheduleEdges = targetSEdges;
    } else {
      if(targetCNodes.size() != 0) {
	classNodes.addAll(targetCNodes);
      }
      if(targetSEdges.size() != 0) {
	scheduleEdges.addAll(targetSEdges);
      }
    }
    targetCNodes = null;

    if(ScheduleEdge.TRANSEDGE == se.getType()) {
      sn.removeInedge(se);
      this.removeEdge(se);

      // merge the split ClassNode of same class
      FlagState sfs = se.getFstate();
      FlagState tfs = se.getTargetFState();
      ClassNode scn = se.getSourceCNode();
      ClassNode tcn = se.getTargetCNode();
      sfs.getEdgeVector().addAll(tfs.getEdgeVector());
      // merge the subtree whose root is nfs from the whole flag transition tree
      Vector<FlagState> sfss = scn.getFlagStates();
      sfss.addAll(tcn.getFlagStates());
      sfss.removeElement(tfs);
      sfss = null;
      classNodes.removeElement(tcn);

      // flush the exeTime of fs and its ancestors
      sfs.setExeTime(0);
      Queue<FlagState> toiterate = new LinkedList<FlagState>();
      toiterate.add(sfs);
      while(!toiterate.isEmpty()) {
	FlagState tmpfs = toiterate.poll();
	long ttime = tmpfs.getExeTime();
	Iterator it_inedges = tmpfs.inedges();
	while(it_inedges.hasNext()) {
	  FEdge fEdge = (FEdge)it_inedges.next();
	  FlagState temp = (FlagState)fEdge.getSource();
	  long time = fEdge.getExeTime() + ttime;
	  if(temp.getExeTime() > time) {
	    temp.setExeTime(time);
	    toiterate.add(temp);
	  }
	}
	it_inedges = null;
      }
      toiterate = null;

      // redirct internal ScheduleEdge from tcn to scn
      for(int i = 0; i < targetSEdges.size(); ++i) {
	ScheduleEdge tmpse = targetSEdges.elementAt(i);
	if(tmpse.getSourceCNode().equals(tcn)) {
	  tmpse.setSourceCNode(scn);
	}
      }

      // redirect external ScheduleEdges to this ScheduleNode
      // and scn if it is originally from tcn
      Iterator it_edges = sn.edges();
      while(it_edges.hasNext()) {
	ScheduleEdge tse = (ScheduleEdge)it_edges.next();
	tse.setSource(this);
	if(tse.getSourceCNode().equals(tcn)) {
	  tse.setSourceCNode(scn);
	}
	this.edges.addElement(tse);
      }
      it_edges = null;

      targetSEdges = null;

      // As all tasks inside one ScheduleNode are executed sequentially,
      // simply add the execution time of all the ClassNodes inside one ScheduleNode.
      if(this.executionTime == -1) {
	throw new Exception("Error: ScheduleNode without initiate execution time when analysising.");
      }
      if(this.executionTime < sn.getExeTime()) {
	this.executionTime = sn.getExeTime();
      }
    } else if(ScheduleEdge.NEWEDGE == se.getType()) {
      targetSEdges = null;

      scheduleEdges.add(se);
      se.resetListExeTime();
      sn.removeInedge(se);
      this.removeEdge(se);
      Iterator it_edges = sn.edges();
      while(it_edges.hasNext()) {
	ScheduleEdge tse = (ScheduleEdge)it_edges.next();
	tse.setSource(this);
	this.edges.addElement(tse);
      }
      it_edges = null;

      // As all tasks inside one ScheduleNode are executed sequentially,
      // simply add the execution time of all the ClassNodes inside one ScheduleNode.
      if(this.executionTime == -1) {
	this.executionTime = 0;
      }
      this.executionTime += ((ScheduleNode)se.getTarget()).getExeTime();
    }
  }

  public void mergeSNode(ScheduleNode sn) {
    Vector<ClassNode> targetCNodes = (Vector<ClassNode>)sn.getClassNodes();
    Vector<ScheduleEdge> targetSEdges = (Vector<ScheduleEdge>)sn.getScheduleEdges();

    for(int i = 0; i <  targetCNodes.size(); i++) {
      targetCNodes.elementAt(i).setScheduleNode(this);
    }

    if(classNodes == null) {
      classNodes = targetCNodes;
      scheduleEdges = targetSEdges;
    } else {
      if(targetCNodes.size() != 0) {
	classNodes.addAll(targetCNodes);
      }
      if(targetSEdges.size() != 0) {
	scheduleEdges.addAll(targetSEdges);
      }
    }
    targetCNodes = null;
    targetSEdges = null;

    // redirect external ScheduleEdges to this ScheduleNode
    Iterator it_edges = sn.edges();
    while(it_edges.hasNext()) {
      ScheduleEdge tse = (ScheduleEdge)it_edges.next();
      tse.setSource(this);
      this.edges.addElement(tse);
    }

    it_edges = sn.inedges();
    while(it_edges.hasNext()) {
      ScheduleEdge tse = (ScheduleEdge)it_edges.next();
      tse.setTarget(this);
      this.inedges.addElement(tse);
    }
    it_edges = null;

    // As all tasks inside one ScheduleNode are executed sequentially,
    // simply add the execution time of all the ClassNodes inside one ScheduleNode.
    if(this.executionTime == -1) {
      this.executionTime = 0;
    }
    this.executionTime += sn.getExeTime();
  }

  public ScheduleNode spliteClassNode(ClassNode cd) {
    ScheduleNode sNode = new ScheduleNode(cd, this.gid);
    // clean all inedges and edges
    sNode.edges.clear();
    sNode.inedges.clear();

    this.classNodes.remove(cd);
    cd.setScheduleNode(sNode);
    try {
      sNode.calExeTime();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // redirect all corresponding internal ScheduleEdge to the new snode
    Iterator it_innersEdges = this.scheduleEdges.iterator();
    Vector<ScheduleEdge> toremove = new Vector<ScheduleEdge>();
    if(it_innersEdges != null) {
      while(it_innersEdges.hasNext()) {
	ScheduleEdge tse = (ScheduleEdge)it_innersEdges.next();
	if((cd.equals(tse.getSourceCNode())) || (cd.equals(tse.getTargetCNode()))) {
	  // related edge
	  toremove.addElement(tse);
	}
      }
    }
    it_innersEdges = null;
    this.scheduleEdges.removeAll(toremove);
    for(int i = 0; i < toremove.size(); i++) {
      ScheduleEdge tse = toremove.elementAt(i);
      if(cd.equals(tse.getSourceCNode())) {
	// outedge
	tse.setTarget(this);
	sNode.addEdge(tse);
      } else if(cd.equals(tse.getTargetCNode())) {
	// inedge
	tse.setTarget(sNode);
	this.addEdge(tse);
      }
    }
    toremove.clear();

    // redirect external ScheudleEdges out of this cd to the new ScheduleNode
    Iterator it_exsEdges = this.edges();
    while(it_exsEdges.hasNext()) {
      ScheduleEdge tse = (ScheduleEdge)it_exsEdges.next();
      if(tse.getSourceCNode().equals(cd)) {
	toremove.add(tse);
	//this.removeEdge(tse);
	//sNode.addEdge(tse);
	tse.setSource(sNode);
	sNode.edges.addElement(tse);
      }
    }
    this.edges.removeAll(toremove);
    toremove.clear();

    it_exsEdges = null;
    // redirect inedges whose target is this Classnode to new ScheduleNode
    Iterator it_insEdges = this.inedges();
    while(it_insEdges.hasNext()) {
      ScheduleEdge tse = (ScheduleEdge)it_insEdges.next();
      if(tse.getTargetCNode().equals(cd)) {
	toremove.add(tse);
	tse.setTarget(sNode);
	sNode.inedges.addElement(tse);
      }
    }
    it_insEdges = null;
    this.inedges.removeAll(toremove);
    toremove.clear();
    toremove = null;

    // As all tasks inside one ScheduleNode are executed sequentially,
    // simply subtract the execution time of the ClassNode .
    assert(this.executionTime != -1);
    this.executionTime -= sNode.getExeTime();

    return sNode;
  }
}
