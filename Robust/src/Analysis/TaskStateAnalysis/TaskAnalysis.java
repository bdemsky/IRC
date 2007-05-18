package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;





public class TaskAnalysis {
    State state;
    Hashtable flagstates;
    Hashtable flags;
    Hashtable extern_flags;
    Queue<FlagState> toprocess;
    TypeUtil typeutil;
    
    /** 
     * Class Constructor
     *
     * @param state a flattened State object
     * @see State
     */
    public TaskAnalysis(State state)
    {
	this.state=state;
	this.typeutil=new TypeUtil(state);
    }
    
    /** Builds a table of flags for each class in the Bristlecone program.  
     *	It creates two hashtables: one which holds the ClassDescriptors and arrays of
     *  FlagDescriptors as key-value pairs; the other holds the ClassDescriptor and the 
     *  number of external flags for that specific class.
     */

    private void getFlagsfromClasses() {
	flags=new Hashtable();
	extern_flags = new Hashtable();
	
	/** Iterate through the classes used in the program to build the table of flags
	 */
	for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator();it_classes.hasNext();) {
		
	    ClassDescriptor cd = (ClassDescriptor)it_classes.next();
	    System.out.println(cd.getSymbol());
	    Vector vFlags=new Vector();
	    FlagDescriptor flag[];
	    int ctr=0;
	    
	    
	    /* Adding the flags of the super class */
	    if (cd.getSuper()!=null) {
		ClassDescriptor superdesc=cd.getSuperDesc();
		
		for(Iterator it_cflags=superdesc.getFlags();it_cflags.hasNext();) {	
		    FlagDescriptor fd = (FlagDescriptor)it_cflags.next();
		    System.out.println(fd.toString());
		    vFlags.add(fd);
		}
	    }

	    for(Iterator it_cflags=cd.getFlags();it_cflags.hasNext();) {
		FlagDescriptor fd = (FlagDescriptor)it_cflags.next();
		vFlags.add(fd);
	    }

	    if (vFlags.size()!=0) {
		flag=new FlagDescriptor[vFlags.size()];
		
		for(int i=0;i < vFlags.size() ; i++) {
		    if (((FlagDescriptor)vFlags.get(i)).getExternal()) {
			flag[ctr]=(FlagDescriptor)vFlags.get(i);
			vFlags.remove(flag[ctr]);
			ctr++;
		    }
		}
		for(int i=0;i < vFlags.size() ; i++) {
		    flag[i+ctr]=(FlagDescriptor)vFlags.get(i);
		}
		extern_flags.put(cd,new Integer(ctr));
		flags.put(cd,flag);
		
	    }
	}
    }
    /** Method which starts up the analysis  
     *  
    */
    
    public void taskAnalysis() throws java.io.IOException {
	flagstates=new Hashtable();
	Hashtable<FlagState,FlagState> sourcenodes;
	
	
	getFlagsfromClasses();
	
	int externs;
	toprocess=new LinkedList<FlagState>();
	
	for(Iterator it_classes=(Iterator)flags.keys();it_classes.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor)it_classes.next();
	    externs=((Integer)extern_flags.get(cd)).intValue();
	    FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);

	    //Debug block
	    System.out.println("Inside taskAnalysis;\n Class:"+ cd.getSymbol());
	    System.out.println("No of externs " + externs);
	    System.out.println("No of flags: "+fd.length);
	    //Debug block
	    
	    flagstates.put(cd,new Hashtable<FlagState,FlagState>());
	}	
	
	
	ClassDescriptor startupobject=typeutil.getClass(TypeUtil.StartupClass);
	
	sourcenodes=(Hashtable<FlagState,FlagState>)flagstates.get(startupobject);
	
	FlagState fsstartup=new FlagState(startupobject);
	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(startupobject);
	
	fsstartup=fsstartup.setFlag(fd[0],true);
	
	sourcenodes.put(fsstartup,fsstartup);
	toprocess.add(fsstartup);
	
	/** Looping through the flagstates in the toprocess queue to perform the state analysis */
	while (!toprocess.isEmpty()) {
	    FlagState trigger=toprocess.poll();
	    createPossibleRuntimeStates(trigger);
	    analyseTasks(trigger);
	}
	
	/** Creating DOT files */
	Enumeration e=flagstates.keys();
	
	while (e.hasMoreElements()) {
	    System.out.println("creating dot file");
	    ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	    System.out.println((cdtemp.getSymbol()));
	    createDOTfile(cdtemp);
	}
    }
    
    
    /** Analyses the set of tasks based on the given flagstate, checking
     *  to see which tasks are triggered and what new flagstates are created
     *  from the base flagstate.
     *  @param fs A FlagState object which is used to analyse the task
     *  @see FlagState
     */

    private void analyseTasks(FlagState fs) {
    ClassDescriptor cd=fs.getClassDescriptor();
    Hashtable<FlagState,FlagState> sourcenodes=(Hashtable<FlagState,FlagState>)flagstates.get(cd);
    
    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();it_tasks.hasNext();) {
	TaskDescriptor td = (TaskDescriptor)it_tasks.next();
	String taskname=td.getSymbol();
	/** counter to keep track of the number of parameters (of the task being analyzed) that 
	 *  are satisfied by the flagstate.
	 */
	int trigger_ctr=0;
	TempDescriptor temp=null;
	FlatMethod fm = state.getMethodFlat(td);	

	for(int i=0; i < td.numParameters(); i++) {
	    FlagExpressionNode fen=td.getFlag(td.getParameter(i));
	    TagExpressionList tel=td.getTag(td.getParameter(i));

	    /** Checking to see if the parameter is of the same type/class as the 
	     *  flagstate's and also if the flagstate fs triggers the given task*/
	    if (typeutil.isSuperorType(td.getParamType(i).getClassDesc(),cd)
		&& isTaskTrigger_flag(fen,fs)
		&& isTaskTrigger_tag(tel,fs)) {
		temp=fm.getParameter(i);
		trigger_ctr++;
	    }
	}
	
	if (trigger_ctr==0) //Look at next task
	    continue;
	
	if (trigger_ctr>1)
	    throw new Error("Illegal Operation: A single flagstate cannot satisfy more than one parameter of a task.");
	
	//Iterating through the nodes
	FlatNode fn=fm.methodEntryNode();
	
	HashSet tovisit= new HashSet();
	HashSet visited= new HashSet();
	
	tovisit.add(fn);
	while(!tovisit.isEmpty()) {
	    FlatNode fn1 = (FlatNode)tovisit.iterator().next();
	    tovisit.remove(fn1);
	    visited.add(fn1);
	    // Queue all of the next nodes
	    for(int i = 0; i < fn1.numNext(); i++) {
		FlatNode nn=fn1.getNext(i);
		if (!visited.contains(nn))
		    tovisit.add(nn);
	    }
	    if (fn1.kind()==FKind.FlatFlagActionNode) {
		FlatFlagActionNode ffan=(FlatFlagActionNode)fn1;
		if (ffan.getTaskType() == FlatFlagActionNode.PRE) {
		    if (ffan.getTempFlagPairs().hasNext()||ffan.getTempTagPairs().hasNext())
			throw new Error("PRE FlagActions not supported");
		} else if (ffan.getTaskType() == FlatFlagActionNode.NEWOBJECT) {
		    FlagState fsnew=evalNewObjNode(ffan);
		    //Have we seen this node yet
		    if (!sourcenodes.containsKey(fsnew)) {
			sourcenodes.put(fsnew, fsnew);
			toprocess.add(fsnew);
		    }
		} else if (ffan.getTaskType() == FlatFlagActionNode.TASKEXIT) {
		    Vector<FlagState> fsv_taskexit=evalTaskExitNode(ffan,cd,fs,temp);
		    
		    for(Enumeration en=fsv_taskexit.elements();en.hasMoreElements();){
			    FlagState fs_taskexit=(FlagState)en.nextElement();
		    	if (!sourcenodes.containsKey(fs_taskexit)) {
					toprocess.add(fs_taskexit);
		    	}
		    	//seen this node already
		    	fs_taskexit=canonicalizeFlagState(sourcenodes,fs_taskexit);
		    	Edge newedge=new Edge(fs_taskexit,taskname);
		    	fs.addEdge(newedge);
	    	}
		}
	    }
	}
    }
}

/** Determines whether the given flagstate satisfies a 
 *  single parameter in the given task.
 *  @param fen FlagExpressionNode
 *  @see FlagExpressionNode
 *  @param fs  FlagState
 *  @see FlagState
 *  @return <CODE>true</CODE> if fs satisfies the boolean expression
    denoted by fen else <CODE>false</CODE>.
 */


private boolean isTaskTrigger_flag(FlagExpressionNode fen,FlagState fs) {
    if (fen instanceof FlagNode)
	return fs.get(((FlagNode)fen).getFlag());
    else
	switch (((FlagOpNode)fen).getOp().getOp()) {
	case Operation.LOGIC_AND:
	    return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) && (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));
	case Operation.LOGIC_OR:
	    return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) || (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));
	case Operation.LOGIC_NOT:
	    return !(isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs));
	default:
	    return false;
	}
}

private boolean isTaskTrigger_tag(TagExpressionList tel, FlagState fs){
	
	
	for (int i=0;i<tel.numTags() ; i++){
		switch (fs.getTagCount(tel.getType(i))){
			case FlagState.ONETAG:
			case FlagState.MULTITAGS:
				break;
			case FlagState.NOTAGS:
				return false;
		}
		
	}
	return true;
}

/*private int tagTypeCount(TagExpressionList tel, String tagtype){
	int ctr=0;
	for(int i=0;i<tel.numTags() ; i++){
		if (tel.getType(i).equals(tagtype))
			ctr++;
	}
	return ctr;
} */

/** Evaluates a NewObject Node and returns the newly created 
 *  flagstate to add to the process queue.
 *	@param nn FlatNode
 *  @return FlagState
 *  @see FlatNode
 *  @see FlagState
 */
    
    private FlagState evalNewObjNode(FlatNode nn){
	    TempDescriptor[] tdArray = ((FlatFlagActionNode)nn).readsTemps();
				    
		//Under the safe assumption that all the temps in FFAN.NewObject node are of the same type(class)
		ClassDescriptor cd_new=tdArray[0].getType().getClassDesc();
				    
		FlagState fstemp=new FlagState(cd_new);
				    
		for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();) {
			TempFlagPair tfp=(TempFlagPair)it_tfp.next();
			if (! (tfp.getFlag()==null))// condition checks if the new object was created without any flag setting
			{					
			   	fstemp=fstemp.setFlag(tfp.getFlag(),((FlatFlagActionNode)nn).getFlagChange(tfp));
			}
		
			else
				break;
		}
		for(Iterator it_ttp=((FlatFlagActionNode)nn).getTempTagPairs();it_ttp.hasNext();) {
			TempTagPair ttp=(TempTagPair)it_ttp.next();
			if (! (ttp.getTag()==null)){
				fstemp=fstemp.setTag(ttp.getTag());
			}
			else
				break;	
		
		}
		return fstemp;
}
	
	private Vector<FlagState> evalTaskExitNode(FlatNode nn,ClassDescriptor cd,FlagState fs, TempDescriptor temp){
		FlagState fstemp=fs;
		//FlagState[] fstemparray=new FlagState[3];
		Vector<FlagState> inprocess=new Vector<FlagState>();
		Vector<FlagState> processed=new Vector<FlagState>();
			    
		for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();) {
			TempFlagPair tfp=(TempFlagPair)it_tfp.next();
			if (temp==tfp.getTemp())
			    fstemp=fstemp.setFlag(tfp.getFlag(),((FlatFlagActionNode)nn).getFlagChange(tfp));
		}
		
		inprocess.add(fstemp);
		processed.add(fstemp);
		
		for(Iterator it_ttp=((FlatFlagActionNode)nn).getTempTagPairs();it_ttp.hasNext();) {
			TempTagPair ttp=(TempTagPair)it_ttp.next();
			
			if (temp==ttp.getTemp()){	
				processed=new Vector<FlagState>();			
				for (Enumeration en=inprocess.elements();en.hasMoreElements();){
					FlagState fsworking=(FlagState)en.nextElement();
					if (((FlatFlagActionNode)nn).getTagChange(ttp)){
						fsworking=fsworking.setTag(ttp.getTag());
						processed.add(fsworking);
					}
					else
					{	
						processed.addAll(Arrays.asList(fsworking.clearTag(ttp.getTag())));
					}
				}
				inprocess=processed;
		}
		}
		return processed;
	
}		
	    

    private FlagState canonicalizeFlagState(Hashtable sourcenodes, FlagState fs){
	if (sourcenodes.containsKey(fs))
	    return (FlagState)sourcenodes.get(fs);
	else{
	    sourcenodes.put(fs,fs);
	    return fs;
	}
    }

   /** Creates a DOT file using the flagstates for a given class
    *  @param cd ClassDescriptor of the class
    *  @throws java.io.IOException
    *  @see ClassDescriptor
    */
    
   public void createDOTfile(ClassDescriptor cd) throws java.io.IOException {
	File dotfile= new File("graph"+cd.getSymbol()+".dot");
	FileOutputStream dotstream=new FileOutputStream(dotfile,true);
	FlagState.DOTVisitor.visit(dotstream,((Hashtable)flagstates.get(cd)).values());
   }
	

    private String getTaskName(TaskDescriptor td) {
	StringTokenizer st = new StringTokenizer(td.toString(),"(");
	return st.nextToken();
    }

	private void createPossibleRuntimeStates(FlagState fs) {
    ClassDescriptor cd = fs.getClassDescriptor();
    Hashtable<FlagState,FlagState> sourcenodes=(Hashtable<FlagState,FlagState>)flagstates.get(cd);
    FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);	
    int externs=((Integer)extern_flags.get(cd)).intValue();
    if(externs==0)
	return;

    int noOfIterations=(1<<externs) - 1;
    boolean BoolValTable[]=new boolean[externs];


    for(int i=0; i < externs ; i++) {
	BoolValTable[i]=fs.get(fd[i]);
    }

	for(int k=0; k<noOfIterations; k++) {
	for(int j=0; j < externs ;j++) {
	    if ((k% (1<<j)) == 0)
		BoolValTable[j]=(!BoolValTable[j]);
	}
	
	FlagState fstemp=fs;
	
	for(int i=0; i < externs;i++) {
	    fstemp=fstemp.setFlag(fd[i],BoolValTable[i]);
	}
	if (!sourcenodes.containsKey(fstemp))
	    toprocess.add(fstemp);

	fstemp=canonicalizeFlagState(sourcenodes,fstemp);
	fs.addEdge(new Edge(fstemp,"Runtime"));
    }
	}
} 

