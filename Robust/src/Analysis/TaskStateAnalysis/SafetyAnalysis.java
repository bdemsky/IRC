package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Flat.*;
import java.io.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import Util.Edge;

public class SafetyAnalysis {
    
    private Hashtable executiongraph;
    private TreeMap safeexecution;
    private static final int OR = 0;
    private static final int AND = 1;
    private Hashtable reducedgraph;
    private String classname;
    private State state;
    

    /*Structure that stores a possible optional
      task which would be safe to execute and 
      the possible flagstates the object could
      be in before executing the task during an
      execution without failure*/
    
    public class  MyOptional{
	public TaskDescriptor td;
	public HashSet flagstates;
	
	protected MyOptional(TaskDescriptor td, HashSet flagstates){
	    this.td = td;
	    this.flagstates = flagstates;
	}

	public boolean equal(MyOptional myo){
	    if (this.td.getSymbol().compareTo(myo.td.getSymbol())==0)
		if(this.flagstates.equals(myo.flagstates))
		    return true;
	    return false;
	}
    }
    
    /*Constructor*/
    public SafetyAnalysis(Hashtable executiongraph, State state){
	this.executiongraph = executiongraph;
	this.safeexecution = new TreeMap();
	this.reducedgraph = new Hashtable();
	this.state = state;
    }
    
    
    public void unMarkProcessed(Vector nodes){
	for(Iterator it = nodes.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    tn.unMark();
	}	
    }

    /*returns a hashset of tags used during the analysis */
    private HashSet createNodeTags(EGTaskNode tn){
	HashSet nodetags = new HashSet();
	String flagstate = tn.getFSName();
	String word = new String();
	StringTokenizer st = new StringTokenizer(flagstate);
	while (st.hasMoreTokens()){
	    word = st.nextToken();
	    if (word.compareTo("Tag")==0)
		nodetags.add(st.nextToken());
	}
	for(Iterator it = nodetags.iterator(); it.hasNext();){
	    System.out.println("nodetag :"+it.next());
	}
	return nodetags;
    }
    
    /*finds the the source node in the execution graph*/
    private EGTaskNode findSourceNode(Vector nodes){
	for(Iterator it = nodes.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    if(tn.isSource()){
		System.out.println("Found Source Node !!");
		return tn;
	    }
	}
	return null;
    }
    
    /*returns the nodes corresponding to the tasks
      that can fire with the object in flagstate
      previousflagstate*/
    private Vector findEGTaskNode(String previousflagstate, Vector nodes){
	Vector tns = new Vector();
	for(Iterator it = nodes.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    if(tn.getFSName().compareTo(previousflagstate)==0)
		tns.add(tn);
	}
	if(tns.size() == 0)
	    return null;
	else if (tns.size() > 1){
	    for(Iterator it = tns.iterator(); it.hasNext();){
		EGTaskNode tn = (EGTaskNode)it.next();
		tn.setAND();
	    }
	}
	return tns; 	    
    }
    
    /*returns the executiongraph corresponding to the classname*/
    private Vector getConcernedClass( String classname ){
	Enumeration e = executiongraph.keys();
	while( e.hasMoreElements() ){
	    ClassDescriptor cd = (ClassDescriptor)e.nextElement();
	    if (classname.compareTo(cd.getSymbol())==0)
		return (Vector)executiongraph.get(cd);
	}
	return null;
    }
    
    /*Method used for debugging*/
    public void buildPath() throws java.io.IOException {
	
	byte[] b = new byte[100] ;
	HashSet safetasks = new HashSet();
	System.out.println("Enter the Class of the object concerned :");
	int k = System.in.read(b);
	classname = new String(b,0,k-1);
	System.out.println("Enter the flagstate :");
	k = System.in.read(b);
	String previousflagstate = new String(b,0,k-1);
			
	//get the graph result of executiongraph class
	Vector nodes = new Vector();
	nodes = getConcernedClass( classname );
	if(nodes==null) {
	    System.out.println("Impossible to find "+classname+". Maybe not declared in the source code.");
	    return;
	}
	
	//mark the graph
	EGTaskNode sourcenode = findSourceNode(nodes);
	doGraphMarking(sourcenode);
	createDOTFile();
	//get the tasknodes possible to execute with the flagstate before failure
	HashSet tempnodes = new HashSet();
	Vector tns = new Vector();
	tns = findEGTaskNode(previousflagstate, nodes);
	if(tns==null) {
	    System.out.println("No task corresponding");
	    return;
	}
	
	//compute the result for all the nodes contained in tns.
	//return the intersection. (because it is not possible to choose)
	int counter = 0;
	HashSet availabletasks = new HashSet();
	for(Iterator it = tns.iterator(); it.hasNext();){
	    counter++;
	    unMarkProcessed(nodes);
	    EGTaskNode tn = (EGTaskNode)it.next();
	    HashSet nodetags = createNodeTags(tn);
	    availabletasks = createUnion(determineIfIsSafe(tn, nodetags), availabletasks);
	    	//DEBUG
		System.out.println("-----------------------------");
		for(Iterator it2 = availabletasks.iterator(); it2.hasNext();){
		MyOptional mm = (MyOptional)it2.next();
		System.out.println("\t"+mm.td.getSymbol());
		System.out.println("with flags :");
		for(Iterator it3 = mm.flagstates.iterator(); it3.hasNext();){
		System.out.println("\t"+((FlagState)it3.next()).getTextLabel());
		}
		resultingFS(mm, classname);
		System.out.println("-----------------------------");
		}
	    if(counter == 1) safetasks = availabletasks;
	    else safetasks = createIntersection(availabletasks, safetasks);
	}
	//DEBUG
	  System.out.println("----------FINAL--------------");
	  for(Iterator it2 = safetasks.iterator(); it2.hasNext();){
	  MyOptional mm = (MyOptional)it2.next();
	  System.out.println("\t"+mm.td.getSymbol());
	  System.out.println("with flags :");
	  for(Iterator it3 = mm.flagstates.iterator(); it3.hasNext();){
	  System.out.println("\t"+((FlagState)it3.next()).getTextLabel());
	  }
	  resultingFS(mm, classname);
	  System.out.println("-----");
	  }
	  System.out.println("----------FINAL--------------");
    }
    
    /*Actual method used by the compiler.
      It computes the analysis for every
      possible flagstates of every classes*/
    public void buildPath(Vector flagstates, ClassDescriptor cd) throws java.io.IOException {
    }
    

    /*Marks the executiongraph :
          -optionals
	  -multiple
	  -AND and OR nodes
    */
    private void doGraphMarking(EGTaskNode extremity) throws java.io.IOException{
	//detects if there is a loop or no more nodes to explore
	if (extremity.isMarked() || !((Iterator)extremity.edges()).hasNext()){
	    if (!((Iterator)extremity.edges()).hasNext()) extremity.mark();
	    reducedgraph.put(extremity.getuid(), extremity);
	}
    	else {
	    //do the marking
	    process(extremity);
	    reducedgraph.put(extremity.getuid(), extremity);
	    extremity.mark();
	    //calls doGraphMarking recursively with the next nodes as params
	    for( Iterator it = extremity.edges(); it.hasNext(); ){
		TEdge edge = (TEdge)it.next();
		doGraphMarking((EGTaskNode)edge.getTarget());
	    }
	}
    }
    
    private void process(EGTaskNode tn){
	testIfOptional(tn);
	testIfSameTask(tn);
	testIfNextIsSelfLoop(tn);
	testIfRuntime(tn);
	testIfMultiple(tn);
    }
    
    private void testIfOptional(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if (nexttn.getTD()!=null)
		if(nexttn.getTD().isOptional(classname))
		    nexttn.setOptional();
	}
    }
    
    private void testIfMultiple(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if( nexttn.getTD().numParameters() > 1 ){
		nexttn.setMultipleParams();
	    }
	}	
    }
    
    //maybe a little bug to fix 
    private void testIfRuntime(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if( ((String)nexttn.getName()).compareTo("Runtime") == 0 )
		nexttn.setAND();
	}
    }
    
    /*That correspond to the case where it is
      not possible for us to choose a path of
      execution. The optional task has to be
      present in all the possible executions
      at this point. So we mark the node as an
      AND node.*/
    private void testIfSameTask(EGTaskNode tn){
	Vector vtemp = new Vector();
	Vector tomark = new Vector();
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    int contains = 0;
	    for (Iterator it = vtemp.iterator(); it.hasNext();){
		EGTaskNode nexttn2 = (EGTaskNode)it.next();
		if (nexttn.getName()==nexttn2.getName()){
		    contains = 1;
		    tomark.add(nexttn);
		    tomark.add(nexttn2);
		}
	    }
	    if (contains == 0) vtemp.add(nexttn);	    
	}
	
	for(Iterator it2 = tomark.iterator(); it2.hasNext();)
	    ((EGTaskNode)it2.next()).setAND();
    }
    
    //maybe little bug to fix
    private void testIfNextIsSelfLoop(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if(nexttn.isSelfLoop()) nexttn.setAND();
	}
    }
    

    /*recursive method that returns a set of MyOptionals
      The computation basically consist in returning the
      intersection or union of sets depending on the nature
      of the node : OR -> UNION
                    AND -> INTERSECTION
      The method also looks for tag changes.
    */
    private HashSet determineIfIsSafe(EGTaskNode tn, HashSet nodetags){
	if(tn == null) return null;
	if(!tagChange(tn, nodetags)){
	    if(tn.isOptional()){
		HashSet temp = new HashSet();
		//if the tn is optional and there is no more nodes/presence of a loop
		//create the MyOptional and return it as a singleton. 
		if( !((Iterator)tn.edges()).hasNext() || tn.isMarked() || tn.isSelfLoop()){
		    HashSet fstemp = new HashSet();
		    fstemp.add(tn.getFS());
		    MyOptional mo = new MyOptional(tn.getTD(), fstemp); 
		    temp.add(mo);
		    return temp;
		}
		//else compute the edges, create the MyOptional and add it to the set.
		else{
		    tn.mark();
		    temp = computeEdges(tn, nodetags);
		    HashSet fstemp = new HashSet();
		    fstemp.add(tn.getFS());
		    MyOptional mo = new MyOptional(tn.getTD(), fstemp); 
		    temp.add(mo);
		    return temp;
		}
	    }
	    else{
		//if not optional but terminal just return an empty set.
		if( !((Iterator)tn.edges()).hasNext() || tn.isMarked() || tn.isSelfLoop()){
		    HashSet temp = new HashSet();
		    return temp;
		}
		//if not terminal return the computation of the edges.
		else{
		    tn.mark();
		    return computeEdges(tn, nodetags);
		}
	    }
	}
	//if there has been a tag change return an empty set.
	else{
	    HashSet temp = new HashSet();
	    return temp;
	}
    }
    
    /*check if there has been a tag Change*/
    private boolean tagChange(EGTaskNode tn, HashSet nodetags){
	HashSet tags = new HashSet();
	String flagstate = tn.getFSName();
	String word = new String();
	StringTokenizer st = new StringTokenizer(flagstate);
	while (st.hasMoreTokens()){
	    word = st.nextToken();
	    if (word.compareTo("Tag")==0)
		tags.add(st.nextToken());
	}
	//compare the tag needed now to the tag of the initial node
	for(Iterator it = tags.iterator(); it.hasNext();){
	    String tag = (String)it.next();
	    if( !nodetags.contains(tag)){
		System.out.println("Tag Change :"+tag);
		return true;
	    }
	}
	
	return false;
    }

    
    private HashSet computeEdges(EGTaskNode tn, HashSet nodetags){
	Hashtable andlist = new Hashtable();
	Vector orlist = new Vector();
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    EGTaskNode tntemp = (EGTaskNode)((TEdge)edges.next()).getTarget();
	    if(tntemp.type() == OR) orlist.add(tntemp);
	    else if(tntemp.type() == AND){
		if(andlist.containsKey(tntemp.getName())){
		    ((Vector)andlist.get(tntemp.getName())).add(tntemp);}
		else{
		    Vector vector = new Vector();
		    vector.add(tntemp);
		    andlist.put(tntemp.getName(), vector);
		}
	    }
	}
	
	return (createUnion(computeOrVector(orlist, nodetags), computeAndList(andlist, nodetags)));
    }

    private  HashSet computeOrVector( Vector orlist, HashSet nodetags){
	if(orlist.isEmpty()){
	    HashSet temp = new HashSet();
	    return temp;
	}
	else{
	    HashSet temp = new HashSet();
	    for(Iterator tns = orlist.iterator(); tns.hasNext();){
		EGTaskNode tn = (EGTaskNode)tns.next();
		temp = createUnion(determineIfIsSafe(tn, nodetags), temp);
	    }
	    return temp;
	}
	
    }
    
    private  HashSet computeAndList(Hashtable andlist, HashSet nodetags){
	if( andlist.isEmpty()){
	    HashSet temp = new HashSet();
	    return temp;
	}
	else{
	    HashSet temp = new HashSet();
	    Collection c = andlist.values();
	    for(Iterator vectors = c.iterator(); vectors.hasNext();){
		Vector vector = (Vector)vectors.next();
		temp = createUnion(computeAndVector(vector, nodetags), temp);
	    }
	    return temp;
	}
	
    }
   
    private  HashSet computeAndVector(Vector vector, HashSet nodetags){
	HashSet temp = new HashSet();
	boolean init = true;
	for(Iterator tns = vector.iterator(); tns.hasNext();){
	    EGTaskNode tn = (EGTaskNode)tns.next();
	    if (init){ 
		init = false;
		temp = determineIfIsSafe(tn, nodetags);
	    }
	    else{
		temp = createIntersection(determineIfIsSafe(tn, nodetags), temp);
	    }
	}
	return temp;
    }		
    
    private HashSet createUnion( HashSet A, HashSet B){
	A.addAll(B);
	
	//remove duplicated MyOptionals (might happend)
	Vector toremove = new Vector();
	System.out.println("A contains "+A.size()+" elements");
	int i = 0;
	for(Iterator itA = A.iterator(); itA.hasNext();){
	    MyOptional myA = (MyOptional)itA.next();
	    i++;
	    System.out.println("myA = "+myA.td.getSymbol());
	    Iterator itA2 = A.iterator();
	    for(int j = 0; j<i; j++){
		itA2.next();
	    }
	    for(Iterator itA3 = itA2; itA3.hasNext();){
		MyOptional myA2 = (MyOptional)itA3.next();
		System.out.println("myA2 = "+myA2.td.getSymbol());
		if(myA2.equal(myA)){
		    toremove.add(myA2);
		    System.out.println("removed!");
		}
	    }
	}
	for( Iterator it = toremove.iterator(); it.hasNext();)
	    A.remove(it.next());
	
	return A;
    }
    
    private HashSet createIntersection( HashSet A, HashSet B){
	HashSet result = new HashSet();
	for(Iterator itB = B.iterator(); itB.hasNext();){
	    MyOptional myB = (MyOptional)itB.next();
	    for(Iterator itA = A.iterator(); itA.hasNext();){
		MyOptional myA = (MyOptional)itA.next();
		if(((String)myA.td.getSymbol()).compareTo((String)myB.td.getSymbol())==0){
		   	HashSet newfs = new HashSet();
			newfs.addAll(myA.flagstates);
			newfs.addAll(myB.flagstates);
			MyOptional newmy = new MyOptional(myB.td, newfs);
			result.add(newmy);
		}
	    }
	}
	return result;
    }
    
    /////////DEBUG
    /*Thoose two tasks create the dot file named markedgraph.dot */
    
    private void createDOTFile() throws java.io.IOException {
	Collection v = reducedgraph.values();
	java.io.PrintWriter output;
	File dotfile_flagstates= new File("markedgraph.dot");
	FileOutputStream dotstream=new FileOutputStream(dotfile_flagstates,true);
	output = new java.io.PrintWriter(dotstream, true);
	output.println("digraph dotvisitor {");
	output.println("\tnode [fontsize=10,height=\"0.1\", width=\"0.1\"];");
	output.println("\tedge [fontsize=6];");
	traverse(output, v);
	output.println("}\n");
    }
    
    private void traverse(java.io.PrintWriter output, Collection v) {
	EGTaskNode tn;
	
	for(Iterator it1 = v.iterator(); it1.hasNext();){
	    tn = (EGTaskNode)it1.next();
	    output.println("\t"+tn.getLabel()+" [label=\""+tn.getTextLabel()+"\"");
	    if (tn.isOptional()){
		if (tn.isMultipleParams()) output.println(", shape = tripleoctagon");
		else output.println(", shape=doubleoctagon");
	    }
	    else if (tn.isMultipleParams()) output.println(", shape=octagon");
	    if (tn.type()==AND) output.println(", color=blue");
	    output.println("];");
	    
	    for(Iterator it2 = tn.edges();it2.hasNext();){
		EGTaskNode tn2 = (EGTaskNode)((Edge)it2.next()).getTarget();
		output.println("\t"+tn.getLabel()+" -> "+tn2.getLabel()+";");
	    }
	}
    }
    
    ////////////////////
    /* returns a set of the possible sets of flagstates
       resulting from the execution of the optional task.
       To do it with have to look for TaskExit FlatNodes
       in the IR.
    */
    private HashSet resultingFS(MyOptional mo, ClassDescriptor cd){
	return resultingFS(mo, cd.getSymbol());
    }
    
    private HashSet resultingFS(MyOptional mo, String classname){
	Stack stack = new Stack();
	HashSet result = new HashSet();
	FlatMethod fm = state.getMethodFlat((TaskDescriptor)mo.td);
	FlatNode fn = (FlatNode)fm;
	
	Stack nodestack=new Stack();
	HashSet discovered=new HashSet();
	nodestack.push(fm);
	discovered.add(fm);
	
	//Iterating through the nodes
	while(!nodestack.isEmpty()) {
	    FlatNode fn1 = (FlatNode) nodestack.pop();
	    if (fn1.kind()==FKind.FlatFlagActionNode) {
		FlatFlagActionNode ffan=(FlatFlagActionNode)fn1;
		if (ffan.getTaskType() == FlatFlagActionNode.TASKEXIT) {
		    //***
		    System.out.println("TASKEXIT");
		    //***
		    HashSet tempset = new HashSet();
		    for(Iterator it_fs = mo.flagstates.iterator(); it_fs.hasNext();){
			FlagState fstemp = (FlagState)it_fs.next();
			for(Iterator it_tfp=ffan.getTempFlagPairs();it_tfp.hasNext();) {
			    TempFlagPair tfp=(TempFlagPair)it_tfp.next();
			    TempDescriptor td = tfp.getTemp();
			    if (((String)((ClassDescriptor)((TypeDescriptor)td.getType()).getClassDesc()).getSymbol()).compareTo(classname)==0){
				fstemp=fstemp.setFlag(tfp.getFlag(),ffan.getFlagChange(tfp));
			    }
			}
			System.out.println("new flag : "+fstemp.getTextLabel());
			tempset.add(fstemp);
		    }
		    result.add(tempset);
		    continue; // avoid queueing the return node if reachable
		}
	    }else if (fn1.kind()==FKind.FlatReturnNode) {
		//***
		System.out.println("RETURN NODE REACHABLE WITHOUT TASKEXITS");
		//***
		result.add(mo.flagstates);
	    }
	    
	    /* Queue other nodes past this one */
	    for(int i=0;i<fn1.numNext();i++) {
		FlatNode fnext=fn1.getNext(i);
		if (!discovered.contains(fnext)) {
		    discovered.add(fnext);
		    nodestack.push(fnext);
		}
	    }
	}
	return result;
    }
        
}


