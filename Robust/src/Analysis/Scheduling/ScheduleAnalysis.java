package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.*;
import IR.*;

import java.util.*;

/** This class holds flag transition diagram(s) can be put on one core.
 */
public class ScheduleAnalysis {
    
    State state;
    TaskAnalysis taskanalysis;
    Vector<ScheduleNode> scheduleNodes;
    Vector<ClassNode> classNodes;
    Vector<ScheduleEdge> scheduleEdges;
    Hashtable<ClassDescriptor, ClassNode> cd2ClassNode;
    boolean sorted = false;

    int transThreshold;
    
    int coreNum;
    Vector<Vector<ScheduleNode>> scheduleGraphs;
    Vector<Vector<Schedule>> schedulings;

    public ScheduleAnalysis(State state, TaskAnalysis taskanalysis) {
	this.state = state;
	this.taskanalysis = taskanalysis;
	this.scheduleNodes = new Vector<ScheduleNode>();
	this.classNodes = new Vector<ClassNode>();
	this.scheduleEdges = new Vector<ScheduleEdge>();
	this.cd2ClassNode = new Hashtable<ClassDescriptor, ClassNode>();
	this.transThreshold = 45;
	this.coreNum = -1;
	this.scheduleGraphs = null;
	this.schedulings = null;
    } 
    
    public void setTransThreshold(int tt) {
    	this.transThreshold = tt;
    }
    
    public int getCoreNum() {
        return coreNum;
    }

    public void setCoreNum(int coreNum) {
	this.coreNum = coreNum;
    }
    
    public Iterator getScheduleGraphs() {
	return this.scheduleGraphs.iterator();
    }
    
    public Iterator getSchedulingsIter() {
	return this.schedulings.iterator();
    }
    
    public Vector<Vector<Schedule>> getSchedulings() {
	return this.schedulings;
    }
    
    // for test
    public Vector<ScheduleEdge> getSEdges4Test() {
    	return scheduleEdges;
    }
    
    public void preSchedule() {
    	Hashtable<ClassDescriptor, ClassNode> cdToCNodes = new Hashtable<ClassDescriptor, ClassNode>();
    	// Build the combined flag transition diagram
    	// First, for each class create a ClassNode
    	for(Iterator it_classes = state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext(); ) {
    	    ClassDescriptor cd = (ClassDescriptor) it_classes.next();
    	    Set<FlagState> fStates = taskanalysis.getFlagStates(cd);
    	    
    	    //Sort flagState nodes inside this ClassNode
    	    Vector<FlagState> sFStates = FlagState.DFS.topology(fStates, null);
    	    
    	    Vector rootnodes  = taskanalysis.getRootNodes(cd);
    	    if(((rootnodes != null) && (rootnodes.size() > 0)) || (cd.getSymbol().equals("StartupObject"))) {
    	    	ClassNode cNode = new ClassNode(cd, sFStates);
    	    	cNode.setSorted(true);
    	    	classNodes.add(cNode);
    	    	cd2ClassNode.put(cd, cNode);
    	    	cdToCNodes.put(cd, cNode);
    	    	cNode.calExeTime();
    	    	
    	    	// for test
    	    	if(cd.getSymbol().equals("C")) {
    	    	    cNode.setTransTime(45);
    	    	}
	    }
    	    fStates = null;
    	    sFStates = null;
    	}

    	// For each ClassNode create a ScheduleNode containing it
    	int i = 0;
    	for(i = 0; i < classNodes.size(); i++) {
	    ScheduleNode sn = new ScheduleNode(classNodes.elementAt(i), 0);
	    classNodes.elementAt(i).setScheduleNode(sn);
	    scheduleNodes.add(sn);
	    try {
	    	sn.calExeTime();
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
    	}
    	
    	// Create 'new' edges between the ScheduleNodes.
	Vector<ScheduleEdge> toBreakDown = new Vector<ScheduleEdge>();
    	for(i = 0; i < classNodes.size(); i++) {
	    ClassNode cNode = classNodes.elementAt(i);
	    ClassDescriptor cd = cNode.getClassDescriptor();
	    Vector rootnodes  = taskanalysis.getRootNodes(cd);   	    
	    if(rootnodes != null) {
    	    	for(int h = 0; h < rootnodes.size(); h++){
		    FlagState root=(FlagState)rootnodes.elementAt(h);
		    Vector allocatingTasks = root.getAllocatingTasks();
		    if(allocatingTasks != null) {
		    	for(int k = 0; k < allocatingTasks.size(); k++) {
			    TaskDescriptor td = (TaskDescriptor)allocatingTasks.elementAt(k);
			    Vector<FEdge> fev = (Vector<FEdge>)taskanalysis.getFEdgesFromTD(td);
			    int numEdges = fev.size();
			    ScheduleNode sNode = cNode.getScheduleNode();
			    for(int j = 0; j < numEdges; j++) {
    	    			FEdge pfe = fev.elementAt(j);
    	    			FEdge.NewObjInfo noi = pfe.getNewObjInfo(cd);
    	    			if ((noi == null) || (noi.getNewRate() == 0) || (noi.getProbability() == 0)) {
				    // fake creating edge, do not need to create corresponding 'new' edge
				    continue;
    	    			}
    	    			if(noi.getRoot() == null) {
    	    			    // set root FlagState
    	    			    noi.setRoot(root);
    	    			}
    	    			FlagState pfs = (FlagState)pfe.getTarget();
    	    			ClassDescriptor pcd = pfs.getClassDescriptor();
    	    			ClassNode pcNode = cdToCNodes.get(pcd);
				
        		    	ScheduleEdge sEdge = new ScheduleEdge(sNode, "new", root, 0);//new ScheduleEdge(sNode, "new", cd, 0);
        		    	sEdge.setFEdge(pfe);
        		    	sEdge.setSourceCNode(pcNode);
        		    	sEdge.setTargetCNode(cNode);
        		   	sEdge.setTargetFState(root);
        		   	sEdge.setNewRate(noi.getNewRate());
        		   	sEdge.setProbability(noi.getProbability());
        		    	pcNode.getScheduleNode().addEdge(sEdge);
        		    	scheduleEdges.add(sEdge);
				if((j !=0 ) || (k != 0) || (h != 0)) {
				    toBreakDown.add(sEdge);
				}
			    }
			    fev = null;
    	    		}
    	    		allocatingTasks = null;
		    }
    	    	}
    	    	rootnodes = null;
	    }
    	}
    	cdToCNodes = null;
    	
    	// Do topology sort of the ClassNodes and ScheduleEdges.
    	Vector<ScheduleEdge> ssev = new Vector<ScheduleEdge>();
    	Vector<ScheduleNode> tempSNodes = ClassNode.DFS.topology(scheduleNodes, ssev);
    	scheduleNodes.removeAllElements();
    	scheduleNodes = tempSNodes;
    	tempSNodes = null;
    	scheduleEdges.removeAllElements();
    	scheduleEdges = ssev;
    	ssev = null;
    	sorted = true;

	// Break down the 'cycle's
	for(i = 0; i < toBreakDown.size(); i++ ) {
	    cloneSNodeList(toBreakDown.elementAt(i), false);
	}
	toBreakDown = null;
	
	// Remove fake 'new' edges
	for(i = 0; i < scheduleEdges.size(); i++) {
	    ScheduleEdge se = scheduleEdges.elementAt(i);
	    if((0 == se.getNewRate()) || (0 == se.getProbability())) {
		scheduleEdges.removeElement(se);
		scheduleNodes.removeElement(se.getTarget());
	    }
	}
	
	SchedulingUtil.printScheduleGraph("scheduling_ori.dot", this.scheduleNodes);
    }
    
    public void scheduleAnalysis() {
    	// First iteration
    	int i = 0; 
    	//Access the ScheduleEdges in reverse topology order
    	Hashtable<FEdge, Vector<ScheduleEdge>> fe2ses = new Hashtable<FEdge, Vector<ScheduleEdge>>();
    	Hashtable<ScheduleNode, Vector<FEdge>> sn2fes = new Hashtable<ScheduleNode, Vector<FEdge>>();
    	ScheduleNode preSNode = null;
    	for(i = scheduleEdges.size(); i > 0; i--) {
	    ScheduleEdge se = scheduleEdges.elementAt(i-1);
	    if(preSNode == null) {
		preSNode = (ScheduleNode)se.getSource();
	    }
	    if(se.getIsNew()) {
		boolean split = false;
		FEdge fe = se.getFEdge();
    	    	if(fe.getSource() == fe.getTarget()) {
    	    	    // back edge
    	    	    int repeat = (int)Math.ceil(se.getNewRate() * se.getProbability() / 100);
    	    	    int rate = 0;
    	    	    if(repeat > 1){
    	    		for(int j = 1; j< repeat; j++ ) {
    	    		    cloneSNodeList(se, true);
    	    		}
    	    		se.setNewRate(1);
    	    		se.setProbability(100);
    	    	    }  
    	    	    try {
    	    		rate = (int)Math.ceil(se.getListExeTime()/ calInExeTime(se.getSourceFState()));
    	    	    } catch (Exception e) {
    	    		e.printStackTrace();
    	    	    }
    	    	    for(int j = rate - 1; j > 0; j--) {
    	    		for(int k = repeat; k > 0; k--) {
    	    		    cloneSNodeList(se, true);
    	    		}
    	    	    }
    	    	} else {
    	    	    // if preSNode is not the same as se's source ScheduleNode
    	    	    // handle any ScheduleEdges previously put into fe2ses whose source ScheduleNode is preSNode
    	    	    boolean same = (preSNode == se.getSource());
    	    	    if(!same) {
    	    		if(preSNode.getFinishingTime() < se.getSource().getFinishingTime()) {
    	    		    if(sn2fes.containsKey(preSNode)) {
    	    			Vector<FEdge> fes = sn2fes.remove(preSNode);
    	    			for(int j = 0; j < fes.size(); j++) {
    	    			    FEdge tempfe = fes.elementAt(j);
    	    			    Vector<ScheduleEdge> ses = fe2ses.get(tempfe);
    	    			    ScheduleEdge tempse = ses.elementAt(0);
    	    			    int temptime = tempse.getListExeTime();
    	    			    // find out the ScheduleEdge with least exeTime
    	    			    for(int k = 1; k < ses.size(); k++) {
    	    				int ttemp = ses.elementAt(k).getListExeTime();
    	    				if(ttemp < temptime) {
    	    				    tempse = ses.elementAt(k);
    	    				    temptime = ttemp;
    	    				}
    	    			    }
    	    			    // handle the tempse
    	    			    handleScheduleEdge(tempse, true);
    	    			    ses.removeElement(tempse);
    	    			    // handle other ScheduleEdges
    	    			    for(int k = 0; k < ses.size(); k++) {
    	    				handleScheduleEdge(ses.elementAt(k), false);
    	    			    }
    	    			    ses = null;
    	    			    fe2ses.remove(tempfe);
    	    			}
    	    			fes = null;
    	    		    }
    	    		}
    	    		preSNode = (ScheduleNode)se.getSource();
    	    	    }
    	    	    
    	    	    // if fe is the last task inside this ClassNode, delay the expanding and merging until we find all such 'nmew' edges
    	    	    // associated with a last task inside this ClassNode
    	    	    if(!fe.getTarget().edges().hasNext()) {
    	    		if(fe2ses.get(fe) == null) {
    	    		    fe2ses.put(fe, new Vector<ScheduleEdge>());
    	    		}
    	    		if(sn2fes.get((ScheduleNode)se.getSource()) == null) {
    	    		    sn2fes.put((ScheduleNode)se.getSource(), new Vector<FEdge>());
    	    		}
    	    		fe2ses.get(fe).add(se);
    	    		sn2fes.get((ScheduleNode)se.getSource()).add(fe);
    	    	    } else {
    	    		// As this is not a last task, first handle available ScheduleEdges previously put into fe2ses
    	    		if((same) && (sn2fes.containsKey(preSNode))) {
    	    		    Vector<FEdge> fes = sn2fes.remove(preSNode);
    	    		    for(int j = 0; j < fes.size(); j++) {
    	    			FEdge tempfe = fes.elementAt(j);
    	    			Vector<ScheduleEdge> ses = fe2ses.get(tempfe);
    	    			ScheduleEdge tempse = ses.elementAt(0);
    	    			int temptime = tempse.getListExeTime();
    	    			// find out the ScheduleEdge with least exeTime
    	    			for(int k = 1; k < ses.size(); k++) {
    	    			    int ttemp = ses.elementAt(k).getListExeTime();
    	    			    if(ttemp < temptime) {
    	    				tempse = ses.elementAt(k);
    	    				temptime = ttemp;
    	    			    }
    	    			}
    	    			// handle the tempse
    	    			handleScheduleEdge(tempse, true);
    	    			ses.removeElement(tempse);
    	    			// handle other ScheduleEdges
    	    			for(int k = 0; k < ses.size(); k++) {
    	    			    handleScheduleEdge(ses.elementAt(k), false);
    	    			}
    	    			ses = null;
    	    			fe2ses.remove(tempfe);
    	    		    }
    	    		    fes = null;
    	    		}
    	    		
    	    		if((!(se.getTransTime() < this.transThreshold)) && (se.getSourceCNode().getTransTime() < se.getTransTime())) {
    	    		    split = true;
    	    		    splitSNode(se, true);
    	    		} else {
    	    		    // handle this ScheduleEdge
    	    		    handleScheduleEdge(se, true);
    	    		}
    	    	    }    		
    	    	}
	    }
    	}
    	if(!fe2ses.isEmpty()) {
    	    Set<FEdge> keys = fe2ses.keySet();
    	    Iterator it_keys = keys.iterator();
    	    while(it_keys.hasNext()) {
    		FEdge tempfe = (FEdge)it_keys.next();
    		Vector<ScheduleEdge> ses = fe2ses.get(tempfe);
    		ScheduleEdge tempse = ses.elementAt(0);
    		int temptime = tempse.getListExeTime();
    		// find out the ScheduleEdge with least exeTime
    		for(int k = 1; k < ses.size(); k++) {
    		    int ttemp = ses.elementAt(k).getListExeTime();
    		    if(ttemp < temptime) {
    			tempse = ses.elementAt(k);
    			temptime = ttemp;
    		    }
    		}
    		// handle the tempse
    		handleScheduleEdge(tempse, true);
    		ses.removeElement(tempse);
    		// handle other ScheduleEdges
    		for(int k = 0; k < ses.size(); k++) {
    		    handleScheduleEdge(ses.elementAt(k), false);
    		}
    		ses = null;
    	    }
    	    keys = null;
    	    fe2ses.clear();
    	    sn2fes.clear();
    	}
    	fe2ses = null;
    	sn2fes = null;
    	
    	SchedulingUtil.printScheduleGraph("scheduling_extend.dot", this.scheduleNodes);
    }
    
    private void handleScheduleEdge(ScheduleEdge se, boolean merge) {
	int rate = 0;
	int repeat = (int)Math.ceil(se.getNewRate() * se.getProbability() / 100);
	if(merge) {
	    try {
		rate = (int)Math.ceil((se.getTransTime() - calInExeTime(se.getSourceFState()))/ se.getListExeTime());
		if(rate < 0 ) {
		    rate = 0;
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	    if(0 == rate) {
		// clone the whole ScheduleNode lists starting with se's target
		for(int j = 1; j < repeat; j++ ) {
		    cloneSNodeList(se, true);
		}
		se.setNewRate(1);
		se.setProbability(100);
	    } else {
		repeat -= rate;
		if(repeat > 0){
		    // clone the whole ScheduleNode lists starting with se's target
		    for(int j = 0; j < repeat; j++ ) {
			cloneSNodeList(se, true);
		    }
		    se.setNewRate(rate);
		    se.setProbability(100);
		}
	    }
	    // merge the original ScheduleNode to the source ScheduleNode
	    ((ScheduleNode)se.getSource()).mergeSEdge(se);
	    scheduleNodes.remove(se.getTarget());
	    scheduleEdges.remove(se);
	    // As se has been changed into an internal edge inside a ScheduleNode, 
	    // change the source and target of se from original ScheduleNodes into ClassNodes.
	    se.setTarget(se.getTargetCNode());
	    se.setSource(se.getSourceCNode());
	} else {
	    // clone the whole ScheduleNode lists starting with se's target
	    for(int j = 1; j < repeat; j++ ) {
		cloneSNodeList(se, true);
	    }
	    se.setNewRate(1);
	    se.setProbability(100);
	}
    }
    
    private void cloneSNodeList(ScheduleEdge sEdge, boolean copyIE) {
    	Hashtable<ClassNode, ClassNode> cn2cn = new Hashtable<ClassNode, ClassNode>();
    	ScheduleNode csNode = (ScheduleNode)((ScheduleNode)sEdge.getTarget()).clone(cn2cn, 0);
    	scheduleNodes.add(csNode);
	
	// Clone all the external in ScheduleEdges
	int i;  
	if(copyIE) {
	    Vector inedges = sEdge.getTarget().getInedgeVector();
	    for(i = 0; i < inedges.size(); i++) {
		ScheduleEdge tse = (ScheduleEdge)inedges.elementAt(i);
		ScheduleEdge se;
		if(tse.getIsNew()) {
		    se = new ScheduleEdge(csNode, "new", tse.getFstate(), tse.getIsNew(), 0); //new ScheduleEdge(csNode, "new", tse.getClassDescriptor(), tse.getIsNew(), 0);
		    se.setProbability(100);
		    se.setNewRate(1);
		} else {
		    se = new ScheduleEdge(csNode, "transmit", tse.getFstate(), false, 0);//new ScheduleEdge(csNode, "transmit", tse.getClassDescriptor(), false, 0);
		}
		se.setSourceCNode(tse.getSourceCNode());
		se.setTargetCNode(cn2cn.get(tse.getTargetCNode()));
		se.setFEdge(tse.getFEdge());
		se.setTargetFState(tse.getTargetFState());
		se.setIsclone(true);
		tse.getSource().addEdge(se);
		scheduleEdges.add(se);
	    }
	    inedges = null;
	} else {
	    sEdge.getTarget().removeInedge(sEdge);
	    sEdge.setTarget(csNode);
	    csNode.getInedgeVector().add(sEdge);
	    sEdge.setTargetCNode(cn2cn.get(sEdge.getTargetCNode()));
	    //sEdge.setTargetFState(null);
	    sEdge.setIsclone(true);
	}
    	
    	Queue<ScheduleNode> toClone = new LinkedList<ScheduleNode>();
    	Queue<ScheduleNode> clone = new LinkedList<ScheduleNode>();
    	Queue<Hashtable> qcn2cn = new LinkedList<Hashtable>();
    	clone.add(csNode);
    	toClone.add((ScheduleNode)sEdge.getTarget());
    	qcn2cn.add(cn2cn);
    	while(!toClone.isEmpty()) {
	    Hashtable<ClassNode, ClassNode> tocn2cn = new Hashtable<ClassNode, ClassNode>();
	    csNode = clone.poll();
	    ScheduleNode osNode = toClone.poll();
	    cn2cn = qcn2cn.poll();
	    // Clone all the external ScheduleEdges and the following ScheduleNodes
	    Vector edges = osNode.getEdgeVector();
	    for(i = 0; i < edges.size(); i++) {
	    	ScheduleEdge tse = (ScheduleEdge)edges.elementAt(i);
	    	ScheduleNode tSNode = (ScheduleNode)((ScheduleNode)tse.getTarget()).clone(tocn2cn, 0);
	    	scheduleNodes.add(tSNode);
	    	clone.add(tSNode);
	    	toClone.add((ScheduleNode)tse.getTarget());
	    	qcn2cn.add(tocn2cn);
	    	ScheduleEdge se = null;
	    	if(tse.getIsNew()) {
	    	    se = new ScheduleEdge(tSNode, "new", tse.getFstate(), tse.getIsNew(), 0);//new ScheduleEdge(tSNode, "new", tse.getClassDescriptor(), tse.getIsNew(), 0);
	    	    se.setProbability(tse.getProbability());
	    	    se.setNewRate(tse.getNewRate());
	    	} else {
	    	    se = new ScheduleEdge(tSNode, "transmit", tse.getFstate(), false, 0);//new ScheduleEdge(tSNode, "transmit", tse.getClassDescriptor(), false, 0);
	    	}
	    	se.setSourceCNode(cn2cn.get(tse.getSourceCNode()));
	    	se.setTargetCNode(tocn2cn.get(tse.getTargetCNode()));
	    	se.setFEdge(tse.getFEdge());
	    	se.setTargetFState(tse.getTargetFState());
	    	se.setIsclone(true);
	    	csNode.addEdge(se);
	    	scheduleEdges.add(se);
	    }
	    tocn2cn = null;
	    edges = null;
    	}
    	toClone = null;
    	clone = null;
    	qcn2cn = null;
    	cn2cn = null;
    }
    
    private int calInExeTime(FlagState fs) throws Exception {
    	int exeTime = 0;
    	ClassDescriptor cd = fs.getClassDescriptor();
    	ClassNode cNode = cd2ClassNode.get(cd);
    	exeTime = cNode.getFlagStates().elementAt(0).getExeTime() - fs.getExeTime();
    	while(true) {
	    Vector inedges = cNode.getInedgeVector();
	    if(inedges.size() > 1) {
		throw new Exception("Error: ClassNode's inedges more than one!");
	    }
	    if(inedges.size() > 0) {
		ScheduleEdge sEdge = (ScheduleEdge)inedges.elementAt(0);
		cNode = (ClassNode)sEdge.getSource();
		exeTime += cNode.getFlagStates().elementAt(0).getExeTime();
	    }else {
		break;
	    }
	    inedges = null;
    	}
    	exeTime = cNode.getScheduleNode().getExeTime() - exeTime;
    	return exeTime;
    }
    
    private ScheduleNode splitSNode(ScheduleEdge se, boolean copy) {
	FEdge fe = se.getFEdge();
	FlagState fs = (FlagState)fe.getTarget();
	FlagState nfs = (FlagState)fs.clone();
	fs.getEdgeVector().removeAllElements();
	nfs.getInedgeVector().removeAllElements();
	ClassNode sCNode = se.getSourceCNode();
	
	// split the subtree whose root is nfs from the whole flag transition tree
	Vector<FlagState> sfss = sCNode.getFlagStates();
	Vector<FlagState> fStates = new Vector<FlagState>();
	Queue<FlagState> toiterate = new LinkedList<FlagState>();
	toiterate.add(nfs);
	fStates.add(nfs);
	while(!toiterate.isEmpty()){
	    FlagState tfs = toiterate.poll();
	    Iterator it_edges = tfs.edges();
	    while(it_edges.hasNext()) {
		FlagState temp = (FlagState)((FEdge)it_edges.next()).getTarget();
		if(!fStates.contains(temp)) {
		    fStates.add(temp);
		    toiterate.add(temp);
		    sfss.removeElement(temp);
		}
	    }
	}
	sfss = null;
	Vector<FlagState> sFStates = FlagState.DFS.topology(fStates, null);
	fStates = null;
	// create a ClassNode and ScheduleNode for this subtree
	ClassNode cNode = new ClassNode(sCNode.getClassDescriptor(), sFStates);
	ScheduleNode sNode = new ScheduleNode(cNode, 0);
	cNode.setScheduleNode(sNode);
	cNode.setSorted(true);
	cNode.setTransTime(sCNode.getTransTime());
	classNodes.add(cNode);
	scheduleNodes.add(sNode);
	try {
	    sNode.calExeTime();
	} catch (Exception e) {
	    e.printStackTrace();
	}
	// flush the exeTime of fs and its ancestors
	fs.setExeTime(0);
	toiterate.add(fs);
	while(!toiterate.isEmpty()) {
	    FlagState tfs = toiterate.poll();
	    int ttime = tfs.getExeTime();
	    Iterator it_inedges = tfs.inedges();
	    while(it_inedges.hasNext()) {
		FEdge fEdge = (FEdge)it_inedges.next();
		FlagState temp = (FlagState)fEdge.getSource();
		int time = fEdge.getExeTime() + ttime;
		if(temp.getExeTime() > time) {
		    temp.setExeTime(time);
		    toiterate.add(temp);
		}
	    }
	}
	toiterate = null;
	
	// create a 'trans' ScheudleEdge between this new ScheduleNode and se's source ScheduleNode
	ScheduleEdge sEdge = new ScheduleEdge(sNode, "transmit", (FlagState)fe.getTarget(), false, 0);//new ScheduleEdge(sNode, "transmit", cNode.getClassDescriptor(), false, 0);
	sEdge.setTargetFState(fs);
	sEdge.setFEdge(fe);
	sEdge.setSourceCNode(sCNode);
	sEdge.setTargetCNode(cNode);
	sEdge.setTargetFState(nfs);
	// todo
	// Add calculation codes for calculating transmit time of an object 
	sEdge.setTransTime(cNode.getTransTime());
	se.getSource().addEdge(sEdge);
	scheduleEdges.add(sEdge);
	// remove the ClassNodes and internal ScheduleEdges out of this subtree to the new ScheduleNode
	ScheduleNode oldSNode = (ScheduleNode)se.getSource();
	Iterator it_isEdges = oldSNode.getScheduleEdgesIterator();
	Vector<ScheduleEdge> toremove = new Vector<ScheduleEdge>();
	Vector<ClassNode> rCNodes = new Vector<ClassNode>();
	rCNodes.addElement(sCNode);
	if(it_isEdges != null){
	    while(it_isEdges.hasNext()) {
		ScheduleEdge tse = (ScheduleEdge)it_isEdges.next();
		if(rCNodes.contains(tse.getSourceCNode())) {
		    if(sCNode == tse.getSourceCNode()) {
			if ((tse.getSourceFState() != fs) && (sFStates.contains(tse.getSourceFState()))) {
			    tse.setSource(cNode);
			    tse.setSourceCNode(cNode);
			} else {
			    continue;
			}
		    }
		    sNode.getScheduleEdges().addElement(tse);
		    sNode.getClassNodes().addElement(tse.getTargetCNode());
		    rCNodes.addElement(tse.getTargetCNode());
		    oldSNode.getClassNodes().removeElement(tse.getTargetCNode());
		    toremove.addElement(tse);
		}
	    }
	}
	oldSNode.getScheduleEdges().removeAll(toremove);
	toremove.clear();
	// redirect ScheudleEdges out of this subtree to the new ScheduleNode
	Iterator it_sEdges = se.getSource().edges();
	//Vector<ScheduleEdge> toremove = new Vector<ScheduleEdge>();
	while(it_sEdges.hasNext()) {
	    ScheduleEdge tse = (ScheduleEdge)it_sEdges.next();
	    if((tse != se) && (tse != sEdge) && (tse.getSourceCNode() == sCNode)) {
		if((tse.getSourceFState() != fs) && (sFStates.contains(tse.getSourceFState()))) {
		    tse.setSource(sNode);
		    tse.setSourceCNode(cNode);
		    sNode.getEdgeVector().addElement(tse);
		    toremove.add(tse);
		}
	    }
	}
	se.getSource().getEdgeVector().removeAll(toremove);
	toremove = null;
	sFStates = null;
	
	if(!copy) {
	    //merge se into its source ScheduleNode
	    ((ScheduleNode)se.getSource()).mergeSEdge(se);
	    scheduleNodes.remove(se.getTarget());
	    scheduleEdges.removeElement(se);
	    // As se has been changed into an internal edge inside a ScheduleNode, 
	    // change the source and target of se from original ScheduleNodes into ClassNodes.
	    se.setTarget(se.getTargetCNode());
	    se.setSource(se.getSourceCNode());
	} else {
	    handleScheduleEdge(se, true);
	}
	
	return sNode;
    }
    
    public void schedule() throws Exception {
	if(this.coreNum == -1) {
	    throw new Exception("Error: un-initialized coreNum when doing scheduling.");
	}
	
	if(this.scheduleGraphs == null) {
	    this.scheduleGraphs = new Vector<Vector<ScheduleNode>>();
	}
	
	int reduceNum = this.scheduleNodes.size() - this.coreNum;
	
	// Enough cores, no need to merge more ScheduleEdge
	if(!(reduceNum > 0)) {
	    this.scheduleGraphs.addElement(this.scheduleNodes);
	} else {
	    // sort the ScheduleEdges in dececending order of transmittime
	    Vector<ScheduleEdge> sEdges = new Vector<ScheduleEdge>();
	    sEdges.addElement(this.scheduleEdges.elementAt(0));
	    for(int i = 1; i < this.scheduleEdges.size(); i++) {
		ScheduleEdge temp = this.scheduleEdges.elementAt(i);
		int j = sEdges.size() - 1;
		do {
		    if(temp.getTransTime() > sEdges.elementAt(j--).getTransTime()) {
			break;
		    }
		} while(j >= 0);
		sEdges.add(j+1, temp);
	    }

	    int temp = sEdges.elementAt(reduceNum - 1).getTransTime();
	    for(int i = sEdges.size() - 1; i > reduceNum - 1; i--) {
		if(sEdges.elementAt(i).getTransTime() != temp) {
		    sEdges.removeElementAt(i);
		} else {
		    break;
		}
	    }
	    int start = reduceNum - 2;
	    for(; start >= 0; start--) {
		if(sEdges.elementAt(start).getTransTime() != temp) {
		    start++;
		    reduceNum -= start;
		    break;
		} 
	    }
	    if(start < 0) {
		start = 0;
	    }

	    // generate scheduling
	    Vector candidates = new Vector();
	    for(int i = start; i < sEdges.size(); i++) {
		candidates.addElement(Integer.valueOf(i));
	    }
	    Combination combGen = new Combination(candidates, reduceNum);
	    int gid = 1;
	    do {
		Vector toreduce = combGen.next();
		if(toreduce != null) {
		    Vector<ScheduleEdge> reduceEdges = new Vector<ScheduleEdge>();
		    for(int i = 0; i < start; i++) {
			reduceEdges.add(sEdges.elementAt(i));
		    }
		    for(int i = 0; i < toreduce.size(); i++) {
			reduceEdges.add(sEdges.elementAt(((Integer)toreduce.elementAt(i)).intValue()));
		    }
		    Vector<ScheduleNode> sNodes = generateScheduling(reduceEdges, gid++);
		    this.scheduleGraphs.add(sNodes);
		    reduceEdges = null;
		    sNodes = null;
		} else {
		    break;
		}
		toreduce = null;
	    }while(true);
	    sEdges = null;
	    candidates = null;

	}
	
	if(this.schedulings == null) {
	    this.schedulings = new Vector<Vector<Schedule>>();
	}
	
	for(int i = 0; i < this.scheduleGraphs.size(); i++) {
	    Vector<ScheduleNode> scheduleGraph = this.scheduleGraphs.elementAt(i);
	    Vector<Schedule> scheduling = new Vector<Schedule>(scheduleGraph.size());
	    // for each ScheduleNode create a schedule node representing a core
	    Hashtable<ScheduleNode, Integer> sn2coreNum = new Hashtable<ScheduleNode, Integer>();
	    int j = 0;
	    for(j = 0; j < scheduleGraph.size(); j++) {
		sn2coreNum.put(scheduleGraph.elementAt(j), j);
	    }
	    for(j = 0; j < scheduleGraph.size(); j++) {
		Schedule tmpSchedule = new Schedule(j);
		ScheduleNode sn = scheduleGraph.elementAt(j);
		
		Vector<ClassNode> cNodes = sn.getClassNodes();
	    	for(int k = 0; k < cNodes.size(); k++) {
		    Iterator it_flags = cNodes.elementAt(k).getFlags();
		    while(it_flags.hasNext()) {
			FlagState fs = (FlagState)it_flags.next();
			Iterator it_edges = fs.edges();
			while(it_edges.hasNext()) {
			    TaskDescriptor td = ((FEdge)it_edges.next()).getTask();
			    tmpSchedule.addTask(td);
			}
		    }
	    	}

		// For each of the ScheduleEdge out of this ScheduleNode, add the target ScheduleNode into the queue inside sn
		Iterator it_edges = sn.edges();
		while(it_edges.hasNext()) {
		    ScheduleEdge se = (ScheduleEdge)it_edges.next();
		    ScheduleNode target = (ScheduleNode)se.getTarget();
		    if(se.getIsNew()) {
			for(int k = 0; k < se.getNewRate(); k++) {
			    tmpSchedule.addTargetCore(se.getFstate(), sn2coreNum.get(target));
			}
		    } else {
			// 'transmit' edge
			tmpSchedule.addTargetCore(se.getFstate(), sn2coreNum.get(target), se.getTargetFState());
		    }
		}
		it_edges = sn.getScheduleEdgesIterator();
		while(it_edges.hasNext()) {
		    ScheduleEdge se = (ScheduleEdge)it_edges.next();
		    if(se.getIsNew()) {
			for(int k = 0; k < se.getNewRate(); k++) {
			    tmpSchedule.addTargetCore(se.getFstate(), j);
			}
		    } else {
			// 'transmit' edge
			tmpSchedule.addTargetCore(se.getFstate(), j, se.getTargetFState());
		    }
		}
		scheduling.add(tmpSchedule);
	    }
	    this.schedulings.add(scheduling);
	}
	
    }
    
    public Vector<ScheduleNode> generateScheduling(Vector<ScheduleEdge> reduceEdges, int gid) {
	Vector<ScheduleNode> result = new Vector<ScheduleNode>();

	// clone the ScheduleNodes
	Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>> sn2hash = new Hashtable<ScheduleNode, Hashtable<ClassNode, ClassNode>>();
	Hashtable<ScheduleNode, ScheduleNode> sn2sn = new Hashtable<ScheduleNode, ScheduleNode>();
	for(int i = 0; i < this.scheduleNodes.size(); i++) {
	    Hashtable<ClassNode, ClassNode> cn2cn = new Hashtable<ClassNode, ClassNode>();
	    ScheduleNode tocopy = this.scheduleNodes.elementAt(i);
	    ScheduleNode temp = (ScheduleNode)tocopy.clone(cn2cn, gid);
	    result.add(i, temp);
	    sn2hash.put(temp, cn2cn);
	    sn2sn.put(tocopy, temp);
	    cn2cn = null;
	}
	// clone the ScheduleEdges and merge those in reduceEdges at the same time
	Vector<ScheduleEdge> toMerge = new Vector<ScheduleEdge>();
	for(int i = 0; i < this.scheduleEdges.size(); i++) {
	    ScheduleEdge sse = this.scheduleEdges.elementAt(i);
	    ScheduleNode csource = sn2sn.get(sse.getSource());
	    ScheduleNode ctarget = sn2sn.get(sse.getTarget());
	    Hashtable<ClassNode, ClassNode> sourcecn2cn = sn2hash.get(csource);
	    Hashtable<ClassNode, ClassNode> targetcn2cn = sn2hash.get(ctarget);
	    ScheduleEdge se;
	    if(sse.getIsNew()) {
		se = new ScheduleEdge(ctarget, "new", sse.getFstate(), sse.getIsNew(), gid);//new ScheduleEdge(ctarget, "new", sse.getClassDescriptor(), sse.getIsNew(), gid);
		se.setProbability(sse.getProbability());
		se.setNewRate(sse.getNewRate());
	    } else {
		se = new ScheduleEdge(ctarget, "transmit", sse.getFstate(), false, gid);//new ScheduleEdge(ctarget, "transmit", sse.getClassDescriptor(), false, gid);
	    }
	    se.setSourceCNode(sourcecn2cn.get(sse.getSourceCNode()));
	    se.setTargetCNode(targetcn2cn.get(sse.getTargetCNode()));
	    se.setFEdge(sse.getFEdge());
	    se.setTargetFState(sse.getTargetFState());
	    se.setIsclone(true);
	    csource.addEdge(se);
	    if(reduceEdges.contains(sse)) {
		toMerge.add(se);
	    } 
	    sourcecn2cn = null;
	    targetcn2cn = null;
	}
	sn2hash = null;
	sn2sn = null;
	
	for(int i = 0; i < toMerge.size(); i++) {
	    ScheduleEdge sEdge = toMerge.elementAt(i);
	    // merge this edge
	    ((ScheduleNode)sEdge.getSource()).mergeSEdge(sEdge);
	    result.removeElement(sEdge.getTarget());
	    // As se has been changed into an internal edge inside a ScheduleNode, 
	    // change the source and target of se from original ScheduleNodes into ClassNodes.
	    sEdge.setTarget(sEdge.getTargetCNode());
	    sEdge.setSource(sEdge.getSourceCNode());
	}
	toMerge = null;
	
	String path = "scheduling_" + gid + ".dot";
	SchedulingUtil.printScheduleGraph(path, result);
	
	return result;
    }
    
    class Combination{
	Combination tail;
	Object head;
	Vector factors;
	int selectNum;
	int resultNum;
	
	public Combination(Vector factors, int selectNum) throws Exception{
	    this.factors = factors;
	    if(factors.size() < selectNum) {
		throw new Exception("Error: selectNum > candidates' number in combination.");
	    }
	    if(factors.size() == selectNum) {
		this.resultNum = 1;
		head = null;
		tail = null;
		return;
	    } 
	    this.head = this.factors.remove(0);
	    if(selectNum == 1) {
		this.resultNum = this.factors.size() + 1;
		this.tail = null;
		return;
	    }	
	    this.tail = new Combination((Vector)this.factors.clone(), selectNum - 1);
	    this.selectNum = selectNum;
	    this.resultNum = 1;
	    for(int i = factors.size(); i > selectNum; i--) {
		this.resultNum *= i;
	    }
	    for(int i = factors.size() - selectNum; i > 0; i--) {
		this.resultNum /= i;
	    }
	}
	
	public Vector next() {
	    if(resultNum == 0) {
		return null;
	    }
	    if(head == null) {
		resultNum--;
		Vector result = this.factors;
		return result;
	    }
	    if(this.tail == null) {
		resultNum--;
		Vector result = new Vector();
		result.add(this.head);
		if(resultNum != 0) {
		    this.head = this.factors.remove(0);
		}
		return result;
	    }
	    Vector result = this.tail.next();
	    if(result == null) {
		if(this.factors.size() == this.selectNum) {
		    this.head = null;
		    this.tail = null;
		    result = this.factors;
		    this.resultNum--;
		    return result;
		}
		this.head = this.factors.remove(0);
		try {
		    this.tail = new Combination((Vector)this.factors.clone(), selectNum - 1);
		    result = this.tail.next();
		} catch(Exception e) {
		    e.printStackTrace();
		}
		
	    }
	    result.add(0, head);
	    resultNum--;
	    return result;
	}
    }
}
