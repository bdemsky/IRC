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
    private static final int UNIONFS = 1;
    private static final int NOUNION = 0;
    private Hashtable reducedgraph;
    private String classname;
    private State state;
        
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
    
    public SafetyAnalysis(Hashtable executiongraph, State state){
	this.executiongraph = executiongraph;
	this.safeexecution = new TreeMap();
	this.reducedgraph = new Hashtable();
	this.state = state;
    }
    
    public void unMark(Vector nodes){
	for(Iterator it = nodes.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    tn.unMark();
	}	
    }

    public void buildPath() throws java.io.IOException {
	
	byte[] b = new byte[100] ;
	Vector flagstates = new Vector();
	HashSet safetasks = new HashSet();
	System.out.println("Enter the Class of the object concerned :");
	int k = System.in.read(b);
	classname = new String(b,0,k-1);
	//classname =new String("Test");
	for (int i = 0 ; i<2 ; i++){
	System.out.println("Enter the possible flagstates :");
	k = System.in.read(b);
	String previousflagstate = new String(b,0,k-1);
	flagstates.add(previousflagstate);
	}
		
	Vector nodes = new Vector();
	nodes = getConcernedClass( classname );
	if(nodes==null) {
	    System.out.println("Impossible to find "+classname+". Maybe not declared in the source code.");
	    return;
	}
		
	HashSet tempnodes = new HashSet();
	for(Iterator it = flagstates.iterator(); it.hasNext();){
	    Vector tns = new Vector();
	    String flagstate = (String)it.next();
	    tns = findEGTaskNode(flagstate, nodes);
	    if(tns==null) {
		System.out.println("No task corresponding");
		return;
	    }
	    else{
		tempnodes.add(tns);
	    }
	}
	
	EGTaskNode sourcenode = findSourceNode(nodes);
	buildSafeExecutions(sourcenode);
	
	createDOTFile();
	
	int counter = 0;	
	for(Iterator nodesit = tempnodes.iterator(); nodesit.hasNext();){
	    Vector tns = (Vector)nodesit.next();
	    HashSet availabletasks = new HashSet();
	    for(Iterator it = tns.iterator(); it.hasNext();){
		counter++;
		unMark(nodes);
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
		    System.out.println("-----------------------------");
		}
		//     
		if(counter == 1) safetasks = availabletasks;
		else safetasks = createIntersection(availabletasks, safetasks, NOUNION);
	    }
	}
	    
	/////DEBUG
	System.out.println("\n\n\n\nSAFE TASKS : ");
	for(Iterator it2 = safetasks.iterator(); it2.hasNext();){
	    MyOptional mm = (MyOptional)it2.next();
	    System.out.println("\t"+mm.td.getSymbol());
	    System.out.println("with flags :");
	    for(Iterator it3 = mm.flagstates.iterator(); it3.hasNext();){
		System.out.println("\t"+((FlagState)it3.next()).getTextLabel());
	    }
	    resultingFS(mm, classname);
	}
	//////
    }
    
    public HashSet buildPath(Vector flagstates, ClassDescriptor cd) throws java.io.IOException {
	HashSet safetasks = new HashSet();
	Vector nodes = new Vector();
	classname = cd.getSymbol();
	nodes = getConcernedClass( classname );
	if(nodes==null) {
	    System.out.println("Impossible to find "+classname+". Maybe not declared in the source code.");
	    return null;
	}
		
	HashSet tempnodes = new HashSet();
	for(Iterator it = flagstates.iterator(); it.hasNext();){
	    Vector tns = new Vector();
	    String flagstate = (String)it.next();
	    tns = findEGTaskNode(flagstate, nodes);
	    if(tns==null) {
		System.out.println("No task corresponding");
		return null;
	    }
	    else{
		tempnodes.add(tns);
	    }
	}
	
	EGTaskNode sourcenode = findSourceNode(nodes);
	buildSafeExecutions(sourcenode);
	
	createDOTFile();
	
	int counter = 0;	
	for(Iterator nodesit = tempnodes.iterator(); nodesit.hasNext();){
	    Vector tns = (Vector)nodesit.next();
	    HashSet availabletasks = new HashSet();
	    for(Iterator it = tns.iterator(); it.hasNext();){
		counter++;
		unMark(nodes);
		EGTaskNode tn = (EGTaskNode)it.next();
		HashSet nodetags = createNodeTags(tn);
		availabletasks = createUnion(determineIfIsSafe(tn, nodetags), availabletasks);
		if(counter == 1) safetasks = availabletasks;
		else safetasks = createIntersection(availabletasks, safetasks, NOUNION);
	    }
	}
	
	return safetasks;
	
    }
    
    private void buildSafeExecutions(EGTaskNode extremity) throws java.io.IOException{
	
	if (extremity.isMarked() || !((Iterator)extremity.edges()).hasNext()){
	    if (!((Iterator)extremity.edges()).hasNext()) extremity.mark();
	    reducedgraph.put(extremity.getuid(), extremity);
	}
    	else {
	    process(extremity);
	    reducedgraph.put(extremity.getuid(), extremity);
	    extremity.mark();
	    for( Iterator it = extremity.edges(); it.hasNext(); ){
		TEdge edge = (TEdge)it.next();
		buildSafeExecutions((EGTaskNode)edge.getTarget());
	    }
	}
    }
    
    private void process(EGTaskNode tn){
	testIfOptional(tn);
	testIfSameTask(tn);
	testIfNextIsSelfLoop(tn);
	//testIfLoop(tn);
	testIfRuntime(tn);
	testIfMultiple(tn);

	
    }
    
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
	for(Iterator it = tags.iterator(); it.hasNext();){
	    String tag = (String)it.next();
	    if( !nodetags.contains(tag)){
		System.out.println("Tag Change :"+tag);
		return true;
	    }
	}
	
	return false;
	
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
		System.out.println("Multiple found");
		nexttn.setMultipleParams();
	    }
	}	
    }
    
    private void testIfRuntime(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if( ((String)nexttn.getName()).compareTo("Runtime") == 0 )
		nexttn.setAND();
	}
    }
    
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
    
    private void testIfNextIsSelfLoop(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if(nexttn.isSelfLoop()) nexttn.setAND();
	}
    }
    
    /*private void testIfLoop(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    TEdge edge = (TEdge)edges.next();
	    if (((EGTaskNode)edge.getTarget()).isMarked()){
		((EGTaskNode)edge.getTarget()).doLoopMarking();
	    }
	}
	}*/
   
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
    
    private Vector getConcernedClass( String classname ){
	Enumeration e = executiongraph.keys();
	while( e.hasMoreElements() ){
	    ClassDescriptor cd = (ClassDescriptor)e.nextElement();
	    
	    if (classname.compareTo(cd.getSymbol())==0)
		return (Vector)executiongraph.get(cd);
	}
	return null;
    }
    
    private HashSet determineIfIsSafe(EGTaskNode tn, HashSet nodetags){
	if(tn == null) return null;
	if(!tagChange(tn, nodetags)){
	    if(tn.isOptional()){
		HashSet temp = new HashSet();
		if( !((Iterator)tn.edges()).hasNext() || tn.isMarked() || tn.isSelfLoop()){
		    HashSet fstemp = new HashSet();
		    fstemp.add(tn.getFS());
		    MyOptional mo = new MyOptional(tn.getTD(), fstemp); 
		    temp.add(mo);
		    return temp;
		}
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
		if( !((Iterator)tn.edges()).hasNext() || tn.isMarked() || tn.isSelfLoop()){
		    HashSet temp = new HashSet();
		    return temp;
		}
		else{
		    tn.mark();
		    return computeEdges(tn, nodetags);
		}
	    }
	}
	else{
	    HashSet temp = new HashSet();
	    return temp;
	}
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
		//DEBUG
		System.out.println("first and vector : ");
		for(Iterator it = temp.iterator(); it.hasNext();){
		    MyOptional mm = (MyOptional)it.next();
		    System.out.println(mm.td.getSymbol());
		    System.out.println("with flag :");
		    for(Iterator it3 = mm.flagstates.iterator(); it3.hasNext();){
			System.out.println(((FlagState)it3.next()).getTextLabel());
		    } 
		}
	    }
	    else{
		temp = createIntersection(determineIfIsSafe(tn, nodetags), temp, UNIONFS);
		//DEBUG
		System.out.println("another and vector : ");
		for(Iterator it = temp.iterator(); it.hasNext();){
		    MyOptional mm = (MyOptional)it.next();
		    System.out.println(mm.td.getSymbol());
		    System.out.println("with flag :");
		    for(Iterator it3 = mm.flagstates.iterator(); it3.hasNext();){
			System.out.println(((FlagState)it3.next()).getTextLabel());
		    } 
		}
	    }
	}
	// DEBUG
	System.out.println("Computation of and vector : ");
	for(Iterator it = temp.iterator(); it.hasNext();){
	    System.out.println("\t"+(String)((MyOptional)it.next()).td.getSymbol());
	    }
	
	return temp;
    }		
    
    private HashSet createUnion( HashSet A, HashSet B){
	A.addAll(B);
	//remove duplicates (might happend)
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
		    A.remove(myA2);
		    System.out.println("");
		}
	    }
	}
	return A;
    }
	
    private HashSet createIntersection( HashSet A, HashSet B, int option){
	HashSet result = new HashSet();
	for(Iterator itB = B.iterator(); itB.hasNext();){
	    MyOptional myB = (MyOptional)itB.next();
	    for(Iterator itA = A.iterator(); itA.hasNext();){
		MyOptional myA = (MyOptional)itA.next();
		if(((String)myA.td.getSymbol()).compareTo((String)myB.td.getSymbol())==0){
		    if(option==UNIONFS){
			HashSet newfs = new HashSet();
			newfs.addAll(myA.flagstates);
			newfs.addAll(myB.flagstates);
			MyOptional newmy = new MyOptional(myB.td, newfs);
			result.add(newmy);
		    }
		    else{//to do : don't duplicate tasks with same fses
			result.add(myA);
			result.add(myB);
		    }
		}
	    }
	}
	return result;
    }
    
    /////////DEBUG
    
    private void createDOTFile() throws java.io.IOException {
	Collection v = reducedgraph.values();
	java.io.PrintWriter output;
	File dotfile_flagstates= new File("reducedtree.dot");
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
		System.out.println("RETURN NODE REACHABLE WITHOUT TASKEXITS");
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


