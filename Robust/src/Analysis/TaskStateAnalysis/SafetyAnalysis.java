package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.io.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import Util.Edge;

public class SafetyAnalysis {
    
    private Hashtable executiongraph;
    private Hashtable<ClassDescriptor, Hashtable<FlagState, HashSet>> safeexecution; //to use to build code
    private static final int OR = 0;
    private static final int AND = 1;
    private Hashtable reducedgraph;
    private String classname;
    private State state;
    private TaskAnalysis taskanalysis;
    private Hashtable<ClassDescriptor, Hashtable> optionaltaskdescriptors;

    private ClassDescriptor processedclass;
   
    
    public Hashtable<ClassDescriptor, Hashtable<FlagState, HashSet>> getResult(){
	return safeexecution;
    }

    public Hashtable<ClassDescriptor, Hashtable> getOptionalTaskDescriptors(){
	return optionaltaskdescriptors;
    }

    /*Structure that stores a possible optional
      task which would be safe to execute and 
      the possible flagstates the object could
      be in before executing the task during an
      execution without failure*/
         
    /*Constructor*/
    public SafetyAnalysis(Hashtable executiongraph, State state, TaskAnalysis taskanalysis){
	this.executiongraph = executiongraph;
	this.safeexecution = new Hashtable();
	this.reducedgraph = new Hashtable();
	this.state = state;
	this.taskanalysis = taskanalysis;
        this.optionaltaskdescriptors = new Hashtable();
    }
    
    /*finds the the source node in the execution graph*/
    private EGTaskNode findSourceNode(Vector nodes){
	for(Iterator it = nodes.iterator(); it.hasNext();){
	    EGTaskNode tn = (EGTaskNode)it.next();
	    if(tn.isSource()){
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
        
    /*Actual method used by the compiler.
      It computes the analysis for every
      possible flagstates of every classes*/
    public void buildPath() throws java.io.IOException {
	/*Explore the taskanalysis structure*/
	System.out.println("------- ANALYSING OPTIONAL TASKS -------");
	Enumeration e=taskanalysis.flagstates.keys();
	
	while (e.hasMoreElements()) {
	    System.out.println("\nAnalysing class :");
	    processedclass=(ClassDescriptor)e.nextElement();
	    classname = processedclass.getSymbol();
	    Hashtable newhashtable = new Hashtable();
	    optionaltaskdescriptors.put(processedclass, newhashtable);
	    Hashtable cdhashtable = new Hashtable();

	    System.out.println("\t"+classname+ "\n");
	    //get the graph result of executiongraph class
	    Vector nodes = new Vector();
	    nodes = getConcernedClass(classname);
	    if(nodes==null) {
		System.out.println("Impossible to find "+classname+". Unexpected.");
		continue;
	    } else if (nodes.size()==0) {
		System.out.println("Nothing to do");
		continue;
	    }
	    
	    //mark the graph
	    EGTaskNode sourcenode = findSourceNode(nodes);
	    doGraphMarking(sourcenode);
	    createDOTFile( classname );
	    reducedgraph.clear();
	    
	    Collection fses = ((Hashtable)taskanalysis.flagstates.get(processedclass)).values();
	    Iterator itfses = fses.iterator();
	    while (itfses.hasNext()) {
		FlagState fs = (FlagState)itfses.next();
		Hashtable fsresult = new Hashtable();
		//get the tasknodes possible to execute with the flagstate before failure
		HashSet tempnodes = new HashSet();
		Vector tns = new Vector();
		System.out.println("Analysing "+fs.getTextLabel());
		tns = findEGTaskNode(fs.getTextLabel(), nodes);
		if(tns==null) {
		    System.out.println("\tNo task corresponding, terminal FS");
		    continue;
		}
		System.out.println("\tProcessing...");
		
		//compute the result for all the nodes contained in tns.
		//return the intersection of tns that are the same task and union for others.
		
		HashSet availabletasks = new HashSet();
		availabletasks = computeTns(tns);
		
		//removeDoubles(availabletasks);
				
		for(Iterator it = availabletasks.iterator(); it.hasNext();){
		    OptionalTaskDescriptor otd = (OptionalTaskDescriptor)it.next();
		    resultingFS(otd, classname);
		}
		
		cdhashtable.put(fs, availabletasks);
	    }
	    
	    safeexecution.put(processedclass, cdhashtable);
			       
	}
	putinoptionaltaskdescriptors();
	printTEST();

	
    }

    private void putinoptionaltaskdescriptors(){
	Enumeration e = safeexecution.keys();
	while (e.hasMoreElements()) {
	    ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	    optionaltaskdescriptors.get(cdtemp).clear();
	    Hashtable hashtbtemp = safeexecution.get(cdtemp);
	    Enumeration fses = hashtbtemp.keys();
	    while(fses.hasMoreElements()){
		FlagState fs = (FlagState)fses.nextElement();
		HashSet availabletasks = (HashSet)hashtbtemp.get(fs);
		for(Iterator otd_it = availabletasks.iterator(); otd_it.hasNext();){
		    OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
		    optionaltaskdescriptors.get(cdtemp).put(otd, otd);
		}
	    }
	}
    }

    private void printTEST(){
	Enumeration e = safeexecution.keys();
	while (e.hasMoreElements()) {
	    ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	    System.out.println("\nTesting class : "+cdtemp.getSymbol()+"\n");
	    Hashtable hashtbtemp = safeexecution.get(cdtemp);
	    Enumeration fses = hashtbtemp.keys();
	    while(fses.hasMoreElements()){
		FlagState fs = (FlagState)fses.nextElement();
		System.out.println("\t"+fs.getTextLabel()+"\n\tSafe tasks to execute :\n");
		HashSet availabletasks = (HashSet)hashtbtemp.get(fs);
		for(Iterator otd_it = availabletasks.iterator(); otd_it.hasNext();){
		    OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
		    System.out.println("\t\tTASK "+otd.td.getSymbol()+" UID : "+otd.getuid()+"\n");
		    System.out.println("\t\tDepth : "+otd.depth);
		    System.out.println("\t\twith flags :");
		    for(Iterator myfses = otd.flagstates.iterator(); myfses.hasNext();){
			System.out.println("\t\t\t"+((FlagState)myfses.next()).getTextLabel());
		    }
		    System.out.println("\t\tand exitflags :");
		    for(Iterator fseshash = otd.exitfses.iterator(); fseshash.hasNext();){
			HashSet temphs = (HashSet)fseshash.next();
			System.out.println("");
			for(Iterator exfses = temphs.iterator(); exfses.hasNext();){
			    System.out.println("\t\t\t"+((FlagState)exfses.next()).getTextLabel());
			}
		    }
		    Predicate predicate = otd.predicate;
		    System.out.println("\t\tPredicate constains :");
		    Collection c = predicate.vardescriptors.values();
		    for(Iterator varit = c.iterator(); varit.hasNext();){
			VarDescriptor vard = (VarDescriptor)varit.next();
			System.out.println("\t\t\tClass "+vard.getType().getClassDesc().getSymbol());
		    }
		    System.out.println("\t\t------------");
		}
	    }
	
	    System.out.println("\n\n\n\tOptionaltaskdescriptors contains : ");
	    Collection c_otd = optionaltaskdescriptors.get(cdtemp).values();
	    for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext();){
		OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
		System.out.println("\t\tTASK "+otd.td.getSymbol()+" UID : "+otd.getuid()+"\n");
		System.out.println("\t\tDepth : "+otd.depth);
		System.out.println("\t\twith flags :");
		for(Iterator myfses = otd.flagstates.iterator(); myfses.hasNext();){
		    System.out.println("\t\t\t"+((FlagState)myfses.next()).getTextLabel());
		}
		System.out.println("\t\tand exitflags :");
		    for(Iterator fseshash = otd.exitfses.iterator(); fseshash.hasNext();){
			HashSet temphs = (HashSet)fseshash.next();
			System.out.println("");
			for(Iterator exfses = temphs.iterator(); exfses.hasNext();){
			    System.out.println("\t\t\t"+((FlagState)exfses.next()).getTextLabel());
			}
		    }
		    Predicate predicate = otd.predicate;
		    System.out.println("\t\tPredicate contains :");
		    Collection c = predicate.vardescriptors.values();
		    for(Iterator varit = c.iterator(); varit.hasNext();){
			VarDescriptor vard = (VarDescriptor)varit.next();
			System.out.println("\t\t\tClass "+vard.getType().getClassDesc().getSymbol());
			HashSet temphash = predicate.flags.get(vard.getName());
			if(temphash == null) System.out.println("null hashset");
			else System.out.println("\t\t\t"+temphash.size()+" flag(s)");
			
		    }
		    System.out.println("\t\t------------");
	    }
	}

	    
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
	} else {
	    //do the marking
	    process(extremity);
	    reducedgraph.put(extremity.getuid(), extremity);
	    extremity.mark();
	    //calls doGraphMarking recursively with the next nodes as
	    //params
	    for( Iterator it = extremity.edges(); it.hasNext(); ){
		EGEdge edge = (EGEdge)it.next();
		doGraphMarking((EGTaskNode)edge.getTarget());
	    }
	}
    }
    
    private void process(EGTaskNode tn){
	testIfOptional(tn);
	testIfAND(tn);
	testIfNextIsSelfLoop(tn);
	testIfRuntime(tn);
	testIfMultiple(tn);
    }
    
    private void testIfOptional(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    EGEdge edge = (EGEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if (nexttn.getTD()!=null)
		if(nexttn.getTD().isOptional(classname))
		    nexttn.setOptional();
	}
    }
    
    private void testIfMultiple(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    EGEdge edge = (EGEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if (nexttn.getTD() == null ) return;//to be fixed
	    if( nexttn.getTD().numParameters() > 1 ){
		nexttn.setMultipleParams();
	    }
	}	
    }
    
    //maybe a little bug to fix 
    private void testIfRuntime(EGTaskNode tn){
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    EGEdge edge = (EGEdge)edges.next();
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
    private void testIfAND(EGTaskNode tn){
	Vector vtemp = new Vector();
	Vector tomark = new Vector();
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    EGEdge edge = (EGEdge)edges.next();
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
	    EGEdge edge = (EGEdge)edges.next();
	    EGTaskNode nexttn = (EGTaskNode)edge.getTarget();
	    if(nexttn.isSelfLoop()) nexttn.setAND();
	}
    }
    

    /*recursive method that returns a set of OptionalTaskDescriptors
      The computation basically consist in returning the
      intersection or union of sets depending on the nature
      of the node : OR -> UNION
                    AND -> INTERSECTION
      The method also looks for tag changes.
    */
    private HashSet determineIfIsSafe(EGTaskNode tn, int depth, HashSet visited, Predicate predicate){
	Predicate temppredicate = new Predicate();
	if(tn == null) return null;
	if(!tagChange(tn)){
	    if(tn.isOptional()){
		HashSet temp = new HashSet();
		if( tn.isMultipleParams() ){
		    if( goodMultiple(tn) ){			
			temppredicate = combinePredicates(predicate, returnPredicate(tn));
			System.out.println("Good multiple, Optional "+tn.getName());
		    }
		    else return temp;
		}
		else temppredicate = combinePredicates(temppredicate, predicate);
		//if the tn is optional and there is no more nodes/presence of a loop
		//create the OptionalTaskDescriptor and return it as a singleton. 
		if( !((Iterator)tn.edges()).hasNext() || tn.isSelfLoop()){
		    HashSet fstemp = new HashSet();
		    fstemp.add(tn.getFS());
		    OptionalTaskDescriptor otd = new OptionalTaskDescriptor(tn.getTD(), fstemp, depth, temppredicate);
		    if(optionaltaskdescriptors.get(processedclass).get(otd)!=null){
			otd = (OptionalTaskDescriptor)((Hashtable)optionaltaskdescriptors.get(processedclass)).get(otd);
		    }
		    else optionaltaskdescriptors.get(processedclass).put(otd, otd);
		    temp.add(otd);
		    return temp;
		}
		else if(visited.contains(tn)){
		    return temp;
		}			
		//else compute the edges, create the OptionalTaskDescriptor and add it to the set.
		else{
		    int newdepth = depth + 1;
		    visited.add(tn);
		    HashSet newhashset = new HashSet(visited);
		    HashSet fstemp = new HashSet();
		    fstemp.add(tn.getFS());
		    OptionalTaskDescriptor otd = new OptionalTaskDescriptor(tn.getTD(), fstemp, depth, temppredicate);
		    if(optionaltaskdescriptors.get(processedclass).get(otd)!=null){
			otd = (OptionalTaskDescriptor)((Hashtable)optionaltaskdescriptors.get(processedclass)).get(otd);
		    }
		    else optionaltaskdescriptors.get(processedclass).put(otd, otd);
		    temp = computeEdges(tn, newdepth, newhashset, temppredicate);
		    temp.add(otd);
		    return temp;
		}
	    }
	    else{
		HashSet temp = new HashSet();
		if( tn.isMultipleParams() ){
		    if( goodMultiple(tn) ){			
			temppredicate = combinePredicates(predicate, returnPredicate(tn));
			System.out.println("Good multiple, not Optional "+tn.getName());
		    }
		    else{
			System.out.println("Bad multiple, not Optional "+tn.getName());
			return temp;
		    }
		}
		else temppredicate = combinePredicates(temppredicate, predicate);
		//if not optional but terminal just return an empty set.
		if( !((Iterator)tn.edges()).hasNext() ||  visited.contains(tn) || tn.isSelfLoop()){
		    return temp;
		}
		//if not terminal return the computation of the edges.
		else{
		    int newdepth = depth + 1;
		    visited.add(tn);
		    HashSet newhashset = new HashSet(visited);
		    return computeEdges(tn, newdepth, newhashset, temppredicate);
		}
	    }
	}
	//if there has been a tag change return an empty set.
	else{
	    HashSet temp = new HashSet();
	    return temp;
	}
    }

    private boolean goodMultiple(EGTaskNode tn){
	TaskDescriptor td = tn.getTD();
	HashSet classes = new HashSet();
	for(int i = 0 ; i<td.numParameters(); i++){
	    ClassDescriptor cd = td.getParamType(i).getClassDesc();
	    if(cd.getSymbol().compareTo(classname)!=0)
		classes.add(cd);
	}

	
	    Stack stack = new Stack();
	    FlatMethod fm = state.getMethodFlat(td);
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
			for(Iterator it_tfp=ffan.getTempFlagPairs();it_tfp.hasNext();) {
			    TempFlagPair tfp=(TempFlagPair)it_tfp.next();
			    TempDescriptor tempd = tfp.getTemp();
			    if (classes.contains((ClassDescriptor)((TypeDescriptor)tempd.getType()).getClassDesc()))
				return false;//return false if a taskexit modifies one of the other parameters
			}
			continue; // avoid queueing the return node if reachable
		    }
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
	    return true;
	
    }    
    
    private Predicate returnPredicate(EGTaskNode tn){
	Predicate result = new Predicate();
	TaskDescriptor td = tn.getTD();
	for(int i=0; i<td.numParameters(); i++){
	    TypeDescriptor typed = td.getParamType(i);
	    if(((ClassDescriptor)typed.getClassDesc()).getSymbol().compareTo(classname)!=0){
		VarDescriptor vd = td.getParameter(i);
		result.vardescriptors.put(vd.getName(), vd);
		HashSet flaglist = new HashSet();
		flaglist.add((FlagExpressionNode)td.getFlag(vd));
		result.flags.put( vd.getName(), flaglist);
		if((TagExpressionList)td.getTag(vd) != null)
		    result.tags.put( vd.getName(), (TagExpressionList)td.getTag(vd));
	    }
	}
	return result;
    }
    
    /*check if there has been a tag Change*/
    private boolean tagChange(EGTaskNode tn){
	if(tn.getTD() == null) return false;//to be fixed
	FlatMethod fm = state.getMethodFlat(tn.getTD());
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
		    Iterator it_ttp=ffan.getTempTagPairs();
		    if(it_ttp.hasNext()){
			System.out.println("Tag change detected in Task "+tn.getName());
			return true;
		    }
		    else continue; // avoid queueing the return node if reachable
		}
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
	return false;
    }

    
    private HashSet computeEdges(EGTaskNode tn, int depth, HashSet visited, Predicate predicate){
	Hashtable andlist = new Hashtable();
	Vector orlist = new Vector();
	for(Iterator edges = tn.edges(); edges.hasNext();){
	    EGTaskNode tntemp = (EGTaskNode)((EGEdge)edges.next()).getTarget();
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
	
	return (createUnion(computeOrVector(orlist, depth, visited, predicate), computeAndList(andlist, depth, visited, predicate)));
    }

    private HashSet computeTns(Vector tns){
	Hashtable andlist = new Hashtable();
	Vector orlist = new Vector();
	for(Iterator nodes = tns.iterator(); nodes.hasNext();){
	    EGTaskNode tntemp = (EGTaskNode)nodes.next();
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
	
	return (createUnion(computeOrVector(orlist, 0), computeAndList(andlist, 0)));	

    }
    
    private  HashSet computeOrVector( Vector orlist, int depth, HashSet visited, Predicate predicate){
	if(orlist.isEmpty()){
	    HashSet temp = new HashSet();
	    return temp;
	}
	else{
	    HashSet temp = new HashSet();
	    for(Iterator tns = orlist.iterator(); tns.hasNext();){
		EGTaskNode tn = (EGTaskNode)tns.next();
		temp = createUnion(determineIfIsSafe(tn, depth, visited, predicate), temp);
	    }
	    return temp;
	}
	
    }
    
    private  HashSet computeOrVector( Vector orlist, int depth){
	if(orlist.isEmpty()){
	    HashSet temp = new HashSet();
	    return temp;
	}
	else{
	    HashSet temp = new HashSet();
	    for(Iterator tns = orlist.iterator(); tns.hasNext();){
		EGTaskNode tn = (EGTaskNode)tns.next();
		HashSet visited = new HashSet();
		Predicate predicate = new Predicate();
		temp = createUnion(determineIfIsSafe(tn, depth, visited, predicate), temp);
	    }
	    return temp;
	}
	
    }

    private  HashSet computeAndList(Hashtable andlist, int depth, HashSet visited, Predicate predicate){
	if( andlist.isEmpty()){
	    HashSet temp = new HashSet();
	    return temp;
	}
	else{
	    HashSet temp = new HashSet();
	    Collection c = andlist.values();
	    for(Iterator vectors = c.iterator(); vectors.hasNext();){
		Vector vector = (Vector)vectors.next();
		temp = createUnion(computeAndVector(vector, depth, visited, predicate), temp);
	    }
	    return temp;
	}
	
    }
   
    private  HashSet computeAndList(Hashtable andlist, int depth){
	if( andlist.isEmpty()){
	    HashSet temp = new HashSet();
	    return temp;
	}
	else{
	    HashSet temp = new HashSet();
	    Collection c = andlist.values();
	    for(Iterator vectors = c.iterator(); vectors.hasNext();){
		Vector vector = (Vector)vectors.next();
		temp = createUnion(computeAndVector(vector, depth), temp);
	    }
	    return temp;
	}
	
    }

    private  HashSet computeAndVector(Vector vector, int depth, HashSet visited, Predicate predicate){
	HashSet temp = new HashSet();
	boolean init = true;
	for(Iterator tns = vector.iterator(); tns.hasNext();){
	    EGTaskNode tn = (EGTaskNode)tns.next();
	    if (init){ 
		init = false;
		temp = determineIfIsSafe(tn, depth, visited, predicate);
	    }
	    else{
		temp = createIntersection(determineIfIsSafe(tn, depth, visited, predicate), temp);
	    }
	}
	return temp;
    }		
    
    private  HashSet computeAndVector(Vector vector, int depth){
	HashSet temp = new HashSet();
	boolean init = true;
	for(Iterator tns = vector.iterator(); tns.hasNext();){
	    EGTaskNode tn = (EGTaskNode)tns.next();
	    if (init){ 
		init = false;
		HashSet visited = new HashSet();
		Predicate predicate = new Predicate();
		temp = determineIfIsSafe(tn, depth, visited, predicate);
	    }
	    else{
		HashSet visited = new HashSet();
		Predicate predicate = new Predicate();
		temp = createIntersection(determineIfIsSafe(tn, depth, visited, predicate), temp);
	    }
	}
	return temp;
    }		

    private HashSet createUnion( HashSet A, HashSet B){
	A.addAll(B);
	
	return A;
    }

    
    private HashSet createIntersection( HashSet A, HashSet B){
	HashSet result = new HashSet();
	for(Iterator b_it = B.iterator(); b_it.hasNext();){
	    OptionalTaskDescriptor otd_b = (OptionalTaskDescriptor)b_it.next();
	    for(Iterator a_it = A.iterator(); a_it.hasNext();){
		OptionalTaskDescriptor otd_a = (OptionalTaskDescriptor)a_it.next();
		if(((String)otd_a.td.getSymbol()).compareTo((String)otd_b.td.getSymbol())==0){
		    HashSet newfs = new HashSet();
		    newfs.addAll(otd_a.flagstates);
		    newfs.addAll(otd_b.flagstates);
		    int newdepth = (otd_a.depth < otd_b.depth) ? otd_a.depth : otd_b.depth;
		    OptionalTaskDescriptor newotd = new OptionalTaskDescriptor(otd_b.td, newfs, newdepth, combinePredicates(otd_a.predicate, otd_b.predicate));
		    if(optionaltaskdescriptors.get(processedclass).get(newotd)!=null){
			newotd = (OptionalTaskDescriptor)((Hashtable)optionaltaskdescriptors.get(processedclass)).get(newotd);
		    }
		    else optionaltaskdescriptors.get(processedclass).put(newotd, newotd);
		    result.add(newotd);
		}
	    }
	}
	
	return result;
    }

    private Predicate combinePredicates(Predicate A, Predicate B){
	Predicate result = new Predicate();
	result.vardescriptors.putAll(A.vardescriptors);
	result.flags.putAll(A.flags);
	result.tags.putAll(A.tags);
	Collection c = B.vardescriptors.values();
	for(Iterator  varit = c.iterator(); varit.hasNext();){//maybe change that
	    VarDescriptor vd = (VarDescriptor)varit.next();
	    if(result.vardescriptors.containsKey(vd.getName())) System.out.println("Already in ");
	    else {
		result.vardescriptors.put(vd.getName(), vd);
	    }
	}
	Collection vardesc = result.vardescriptors.values();
	for(Iterator varit = vardesc.iterator(); varit.hasNext();){
	    VarDescriptor vd = (VarDescriptor)varit.next();
	    HashSet bflags = B.flags.get(vd.getName());
	    if( bflags == null ){
		continue;
	    }
	    else{
		if (result.flags.containsKey(vd.getName())) ((HashSet)result.flags.get(vd.getName())).addAll(bflags);
		else result.flags.put(vd.getName(), bflags);
	    }
	    TagExpressionList btags = B.tags.get(vd.getName());
	    if( btags != null ){
		if (result.tags.containsKey(vd.getName())) System.out.println("Tag found but there should be nothing to do because same tag");
		else result.tags.put(vd.getName(), btags);
	    }
	}
	return result;
    }
    
    /////////DEBUG
    /*Thoose two tasks create the dot file named markedgraph.dot */
    
    private void createDOTFile(String classname) throws java.io.IOException {
	Collection v = reducedgraph.values();
	java.io.PrintWriter output;
	File dotfile_flagstates= new File("markedgraph_"+classname+".dot");
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
    private void resultingFS(OptionalTaskDescriptor otd, String classname){
	Stack stack = new Stack();
	HashSet result = new HashSet();
	FlatMethod fm = state.getMethodFlat((TaskDescriptor)otd.td);
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
		    HashSet tempset = new HashSet();
		    for(Iterator it_fs = otd.flagstates.iterator(); it_fs.hasNext();){
			FlagState fstemp = (FlagState)it_fs.next();
			for(Iterator it_tfp=ffan.getTempFlagPairs();it_tfp.hasNext();) {
			    TempFlagPair tfp=(TempFlagPair)it_tfp.next();
			    TempDescriptor td = tfp.getTemp();
			    if (((String)((ClassDescriptor)((TypeDescriptor)td.getType()).getClassDesc()).getSymbol()).compareTo(classname)==0){
				fstemp=fstemp.setFlag(tfp.getFlag(),ffan.getFlagChange(tfp));
			    }
			}
			tempset.add(fstemp);
		    }
		    result.add(tempset);
		    continue; // avoid queueing the return node if reachable
		}
	    }else if (fn1.kind()==FKind.FlatReturnNode) {
		result.add(otd.flagstates);
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
	otd.exitfses=result;
    }
}


