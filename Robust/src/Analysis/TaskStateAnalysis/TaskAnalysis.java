package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.File;
import java.io.FileWriter;

public class TaskAnalysis {
    State state;
    Hashtable Adj_List;
    Hashtable flags;
    Hashtable extern_flags;
    Queue<TriggerState> q_main;
    
    /** 
     * Class Constructor
     *
     * @param state a flattened State object
     * @see State
     */
    public TaskAnalysis(State state)
    {
	this.state=state;
    }
    
    /** This function returns the number of external flags, etc */

    private void getFlagsfromClasses() {
	flags=new Hashtable();
	extern_flags = new Hashtable();
	
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
		System.out.println(fd.toString());
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

    public void taskAnalysis() throws java.io.IOException {
	Adj_List=new Hashtable();
	
	getFlagsfromClasses();
	
	int externs;
	q_main=new LinkedList<TriggerState>();
	
	for(Iterator it_classes=(Iterator)flags.keys();it_classes.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor)it_classes.next();
	    
	    externs=((Integer)extern_flags.get(cd)).intValue();
	    
	    FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);
	    //Debug block
	    System.out.println("Inside taskAnalysis;\n Class:"+ cd.getSymbol());
	    System.out.println("No of externs " + externs);
	    System.out.println("No of flags: "+fd.length);
	    //Debug block
	    if (fd.length == externs) {
		System.out.println("extern called");
		boolean onlyExterns=true;
	    } else {
		if ((fd.length == 1) 
		    && (fd[0].getSymbol().compareTo("initialstate")==0)) {
		    FlagState fstemp=new FlagState(fd, cd);
		    Hashtable Adj_List_temp=new Hashtable();
		    
		    fstemp.put(fd[0],new Boolean(true));
		    Vector vtemp=new Vector();
		    vtemp.add(new Edge(fstemp,"Runtime"));
		    Adj_List_temp.put(new FlagState(fd,cd),vtemp);
		    Adj_List.put(cd,Adj_List_temp);
		    
		    Queue<TriggerState> q_temp=analyseTasks(new TriggerState(cd,fstemp));

		    
		    if ( q_temp != null) {
			q_main.addAll(q_temp);
		    }

		}
	    }
	}
	while (q_main.size() > 0) {
	    // ****debug block********
	    for (Iterator it_qm=q_main.iterator();it_qm.hasNext();)
		{
		    TriggerState ts_qm=(TriggerState)it_qm.next();
		    FlagState fs_qm=ts_qm.getState();
		    System.out.println("/***********contents of main q**********/");
		    System.out.println("FS : "+fs_qm.toString((FlagDescriptor [])flags.get(ts_qm.getClassDescriptor())));
		    
		    
		}
	    System.out.println("/*********************************/");
	    // ****debug block********
	    Queue<TriggerState> q_temp=analyseTasks(q_main.poll());
	    if ( q_temp != null) {
		q_main.addAll(q_temp);
	    }
	}
	
	//Creating DOT files
	Enumeration e=Adj_List.keys();

	while (e.hasMoreElements()) {
	    System.out.println("creating dot file");
	    ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	    System.out.println((cdtemp.getSymbol()));
	    createDOTfile(cdtemp);
	}
	
    }


    public Queue<TriggerState> analyseTasks(TriggerState ts) throws java.io.IOException {
	Queue<FlagState> q;
	Queue<FlagState> qft;
	
	Hashtable Adj_List_temp;
	Queue<TriggerState> q_retval;
	
	ClassDescriptor cd=ts.getClassDescriptor();
	
	if (Adj_List.containsKey(cd)) {
	    //Debug block
	    System.out.println("Inside analyseTasks;\n Checking if adj_list contains the class desc:"+ cd.getSymbol());
	    //Debug block
	    
	    Adj_List_temp=(Hashtable)Adj_List.get(cd);
	} else {
	    Adj_List_temp=new Hashtable();
	    Adj_List.put(cd,Adj_List_temp);
	}


	int externs=((Integer)extern_flags.get(cd)).intValue();
	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);

	q = new LinkedList<FlagState>();
	q_retval=new LinkedList<TriggerState>();
	q.offer(ts.getState());

	//***Debug Block***

	while (q.size() != 0) {
	    System.out.println("inside while loop in analysetasks \n");
	    
	    FlagState fsworking=q.poll();
	    
	    //***Debug Block***
	    FlagDescriptor[] ftemp=(FlagDescriptor[])flags.get(cd);
	    System.out.println("Processing state: "+cd.getSymbol()+" " + fsworking.toString(ftemp));

	    //***Debug Block***

	    
	    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();it_tasks.hasNext();) {
		TaskDescriptor td = (TaskDescriptor)it_tasks.next();
		boolean taskistriggered=false;
		int ctr=0;
		String taskname=getTaskName(td);
		
		

		//***Debug Block***
		
		System.out.println();
		System.out.println("Method: AnalyseTasks");
		System.out.println(taskname);
		System.out.println();
		
		//***Debug Block***

		
		for(int i=0; i < td.numParameters(); i++) {
		    FlagExpressionNode fen=td.getFlag(td.getParameter(i));
		    if (isTaskTrigger(fen,fsworking))
			taskistriggered = true;
		}

		if (taskistriggered) {
		    //***Debug Block***
		    //
		    System.out.println("inside taskistriggered");
		    
		    //***Debug Block***
		    if (wasFlagStateProcessed(Adj_List_temp,fsworking)) {
			if (! (fd.length == externs))	
			    continue;
		    } else
			Adj_List_temp.put(fsworking,new Vector());
					
		    FlatMethod fm = state.getMethodFlat(td);
		    FlatNode fn=fm.methodEntryNode();
		    
		    HashSet tovisit= new HashSet();
		    HashSet visited= new HashSet();
		    
		    tovisit.add(fn);
		    while(!tovisit.isEmpty()) {
			FlatNode fn1 = (FlatNode)tovisit.iterator().next();
			tovisit.remove(fn1);
			visited.add(fn1);
			for(int i = 0; i < fn1.numNext(); i++) {
			    FlatNode nn=fn1.getNext(i);
			    if (nn.kind()==13) {
				//***Debug Block***
				if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.PRE) {
				    throw new Error("PRE FlagActions not supported");
				} else if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.NEWOBJECT) {
				    //***Debug Block***
				    System.out.println("NEWObject");
				    //***Debug Block***
				    
				    
				    TempDescriptor[] tdArray = ((FlatFlagActionNode)nn).readsTemps();
				    
				    //Under the safe assumption that all the temps in FFAN.NewObject node are of the same type(class)
				    ClassDescriptor cd_new=tdArray[0].getType().getClassDesc();
				    
				    System.out.println("Class: "+cd_new.getSymbol());	
				    
				    FlagState fstemp=new FlagState((FlagDescriptor[])flags.get(cd_new), cd_new);
				    
				    for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();) {
					TempFlagPair tfp=(TempFlagPair)it_tfp.next();
					if (tfp.getFlag()==null) {
					    System.out.println("test1");
					    q_retval.addAll(createPossibleRuntimeStates(new TriggerState(cd_new,new FlagState((FlagDescriptor[])flags.get(cd_new), cd_new))));
					    // ****debug block********
					    System.out.println("/***********contents of q ret**********/");
					    for (Iterator it_qret=q_retval.iterator();it_qret.hasNext();) {
						TriggerState ts_qret=(TriggerState)it_qret.next();
						FlagState fs_qret=ts_qret.getState();
						
						System.out.println("FS : "+fs_qret.toString((FlagDescriptor [])flags.get(ts_qret.getClassDescriptor())));
					    }
					    System.out.println("/*********************************/");
					    // ****debug block********
					} else
					    fstemp.put(tfp.getFlag(),new Boolean(((FlatFlagActionNode)nn).getFlagChange(tfp)));
					
					
				    }
				    
				    //***Debug Block***
				    System.out.println("test2");
				    System.out.println("Newobj fsworking "+fsworking.toString((FlagDescriptor [])flags.get(cd_new)));
				    System.out.println("Newobj fstemp "+fstemp.toString((FlagDescriptor [])flags.get(cd_new))); 
				    
				    //***Debug Block***
				    
				    q_retval.offer(new TriggerState(cd_new,fstemp));
				    
				    //make this a function containsExterns()
				    int extrns=((Integer)extern_flags.get(cd_new)).intValue();
				    
				    if ((extrns >0) && (extrns!=((FlagDescriptor[])flags.get(cd_new)).length))
					q_retval.addAll(createPossibleRuntimeStates(new TriggerState(cd_new,fstemp)));
				    
				    fstemp=null;
				    // ****debug block********
				    System.out.println("/***********contents of q ret 1**********/");
				    for (Iterator it_qret=q_retval.iterator();it_qret.hasNext();) {
					TriggerState ts_qret=(TriggerState)it_qret.next();
					FlagState fs_qret=ts_qret.getState();
					
					System.out.println("FS : "+fs_qret.toString((FlagDescriptor [])flags.get(ts_qret.getClassDescriptor())));
				    }
				    System.out.println("/*********************************/");
				    // ****debug block********
									
				}
				if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.TASKEXIT) {
				    //***Debug Block***
				    //
				    System.out.println("TaskExit");
				    //***Debug Block***
				    
				    FlagState fstemp=new FlagState(fsworking.getStateTable(), null); //WHAT CLASS IS THIS
				    
				    for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();) {
					TempFlagPair tfp=(TempFlagPair)it_tfp.next();
					fstemp.put(tfp.getFlag(),new Boolean(((FlatFlagActionNode)nn).getFlagChange(tfp)));
				    }
				    //***Debug Block***
				    System.out.println();
				    System.out.println(fstemp.toString((FlagDescriptor [])flags.get(cd)));
				    System.out.println();
				    System.out.println("taskexit fsworking "+fsworking.toString((FlagDescriptor [])flags.get(cd)));
				    System.out.println("taskexit fstemp "+fstemp.toString((FlagDescriptor [])flags.get(cd))); 
				    
				    //***Debug Block***
				    if (!edgeexists(Adj_List_temp,fsworking,fstemp,taskname)) {
					((Vector)Adj_List_temp.get(fsworking)).add(new Edge(fstemp,taskname));
				    }
				    if (!wasFlagStateProcessed(Adj_List_temp,fstemp)) {
					q.offer(fstemp);
				    }
				    fstemp=null;
				    
				}
			    }
			    
			    if (!visited.contains(nn) && !tovisit.contains(nn)) {
				tovisit.add(nn);
			    }
			}
		    }
		}
	    }
	}
	if (q_retval.size()==0)
	    return null;
	else
	    return q_retval;
    }

    private boolean isTaskTrigger(FlagExpressionNode fen,FlagState fs) {
	if (fen instanceof FlagNode)
	    return fs.get(((FlagNode)fen).getFlag());
	else
	    switch (((FlagOpNode)fen).getOp().getOp()) {
	    case Operation.LOGIC_AND:
		return ((isTaskTrigger(((FlagOpNode)fen).getLeft(),fs)) && (isTaskTrigger(((FlagOpNode)fen).getRight(),fs)));
	    case Operation.LOGIC_OR:
		return ((isTaskTrigger(((FlagOpNode)fen).getLeft(),fs)) || (isTaskTrigger(((FlagOpNode)fen).getRight(),fs)));
	    case Operation.LOGIC_NOT:
		return !(isTaskTrigger(((FlagOpNode)fen).getLeft(),fs));
	    default:
		return false;
	    }
    }

    private boolean wasFlagStateProcessed(Hashtable Adj_List,FlagState fs) {
	Enumeration e=Adj_List.keys();
	
	while(e.hasMoreElements()) {
	    FlagState fsv = (FlagState)(e.nextElement());

	    if (fsv.isEqual(fs))
		return true;
	}
	return false;
    }

    private boolean existsInQueue(TriggerState ts) {
	throw new Error("Use hashcode/contains of set method to find...no linear search allowed");
    }

    private boolean existsInFSQueue(Queue q,FlagState fs) {
	throw new Error("Use hashcode/contains of set method to find...no linear search allowed");
    }

    public void printAdjList(ClassDescriptor cd) {
	Enumeration e=((Hashtable)Adj_List.get(cd)).keys();
	while(e.hasMoreElements()) {
	    FlagState fsv = (FlagState)(e.nextElement());
	    System.out.println(fsv.toString((FlagDescriptor [])flags.get(cd)));
	}
    }

    public void createDOTfile(ClassDescriptor cd) throws java.io.IOException {
	File dotfile= new File("graph"+cd.getSymbol()+".dot");

	FileWriter dotwriter=new FileWriter(dotfile,true);

	dotwriter.write("digraph G{ \n");
	dotwriter.write("center=true;\norientation=landscape;\n");

	Enumeration e=((Hashtable)Adj_List.get(cd)).keys();
	while(e.hasMoreElements()) {
	    FlagState fsv = (FlagState)(e.nextElement());
	    System.out.println(fsv.toString());
	    Hashtable test=(Hashtable)Adj_List.get(cd);
	    Vector edges=(Vector)test.get(fsv);
	    for(int i=0;i < edges.size();i++) {
		dotwriter.write(fsv.toString((FlagDescriptor [])flags.get(cd))+" -> "+((Edge)edges.get(i)).getState().toString((FlagDescriptor [])flags.get(cd))+"[label=\""+((Edge)edges.get(i)).getName()+"\"];\n");
	    }

	}
	dotwriter.write("}\n");
	dotwriter.flush();
	dotwriter.close();
    }

    private String getTaskName(TaskDescriptor td) {
	StringTokenizer st = new StringTokenizer(td.toString(),"(");
	return st.nextToken();
    }

    private boolean edgeexists(Hashtable Adj_List_local,FlagState v1, FlagState v2,String name) {
	Vector edges=(Vector)Adj_List_local.get(v1);
	
	if (edges == null) {
	    System.out.println("no edges");
	} else {
	    for(int i=0;i < edges.size();i++) {
		FlagState fs=((Edge)edges.get(i)).getState();
		if (fs.isEqual(v2) && (name.compareTo(((Edge)edges.get(i)).getName())==0))
		    return true;
	    }
	}
	return false;
    }

    private void processExterns(boolean onlyExterns,ClassDescriptor cd) throws java.io.IOException {
	int noOfIterations;
	Hashtable Adj_List_temp;
	if (Adj_List.containsKey(cd)) {
	    Adj_List_temp=(Hashtable)Adj_List.get(cd);
	} else {
	    Adj_List_temp=new Hashtable();
	    Adj_List.put(cd,Adj_List_temp);
	}


	if (onlyExterns) {
	    FlagDescriptor [] fd=(FlagDescriptor [])flags.get(cd);
	    System.out.println("onlyExterns"+fd.length);
	    noOfIterations=1<<fd.length;
	    boolean BoolValTable[]=new boolean[fd.length];
	    
	    for(int i=0; i < fd.length ; i++) {
		System.out.println(fd[i].getSymbol());
		BoolValTable[i]=false;
	    }
	    Adj_List_temp.put(new FlagState(fd,cd),new Vector());

	    for(int k=1; k<noOfIterations; k++) {
		for(int j=0; j< fd.length ;j++) {
		    if ((k% (1<<j)) == 0)
			BoolValTable[j]=(!BoolValTable[j]);
		}

		FlagState fstemp=new FlagState(fd,cd);
		int i=0;
		for(Enumeration e=fstemp.getStateTable().keys(); e.hasMoreElements() && i < fd.length;i++) {
		    fstemp.put((FlagDescriptor)e.nextElement(),new Boolean(BoolValTable[i]));
		}
		Adj_List_temp.put(fstemp,new Vector());
	    }

	    Enumeration e=Adj_List_temp.keys();
	    while(e.hasMoreElements()) {
		FlagState fstemp=(FlagState)e.nextElement();
		Enumeration en=Adj_List_temp.keys();
		while (en.hasMoreElements()) {
		    FlagState fs=(FlagState)en.nextElement();
		    if(fstemp == fs) {
			continue;
		    } else {
			((Vector)Adj_List_temp.get(fstemp)).add(new Edge(fs,"Runtime"));
		    }
		}
	    }
	}
    } 

    private Queue createPossibleRuntimeStates(TriggerState ts) throws java.io.IOException {
	int noOfIterations, externs;
	Hashtable Adj_List_temp;
	boolean onlyExterns;
	
	System.out.println("Inside CreatePossible runtime states");
	
	ClassDescriptor cd = ts.getClassDescriptor();
	FlagState fs= ts.getState();
	if (Adj_List.containsKey(cd)) {
	    Adj_List_temp=(Hashtable)Adj_List.get(cd);
	} else {
	    Adj_List_temp=new Hashtable();
	    Adj_List.put(cd,Adj_List_temp);
	}

	externs=((Integer)extern_flags.get(cd)).intValue();

	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);

	onlyExterns= (fd.length==externs);

	Queue  q_ret=new LinkedList();

	if (onlyExterns) {
	    noOfIterations=1<<fd.length;
	    boolean BoolValTable[]=new boolean[fd.length];

	    for(int i=0; i < fd.length ; i++) {
		System.out.println(fd[i].getSymbol());
		BoolValTable[i]=false;
	    }

	    if (! wasFlagStateProcessed(Adj_List_temp,fs)) {
		TriggerState ts_local=new TriggerState(cd,fs);
		if (!existsInQueue(ts_local)) {
		    q_ret.offer(ts_local);
		}
		Adj_List_temp.put(fs,new Vector());
	    }

	    for(int k=1; k<noOfIterations; k++) {
		for(int j=0; j< fd.length ;j++) {
		    if ((k% (1<<j))==0)
			BoolValTable[j]=(!BoolValTable[j]);
		}

		FlagState fstemp=new FlagState(fs.getStateTable(),cd);
		int i=0;
		for(Enumeration e=fstemp.getStateTable().keys(); e.hasMoreElements() && i < fd.length;i++) {
		    fstemp.put((FlagDescriptor)e.nextElement(),new Boolean(BoolValTable[i]));
		}
		
		if (wasFlagStateProcessed(Adj_List_temp,fstemp)) {
		    continue;
		} else {
		    TriggerState ts_local=new TriggerState(cd,fstemp);
		    if (!existsInQueue(ts_local)) {
			q_ret.offer(ts_local);
		    }

					
		    Adj_List_temp.put(fstemp,new Vector());
		}

	    }
	    
	    Enumeration e=Adj_List_temp.keys();
	    while(e.hasMoreElements()) {
		FlagState fstemp=(FlagState)e.nextElement();
		Enumeration en=Adj_List_temp.keys();
		while (en.hasMoreElements()) {
		    FlagState fs_local=(FlagState)en.nextElement();
		    if(fstemp == fs_local)
			continue;
		    else 
			((Vector)Adj_List_temp.get(fstemp)).add(new Edge(fs_local,"Runtime"));
		}
	    }

	    return q_ret;
	} else {
	    System.out.println("inside else part");
	    noOfIterations=1<<externs;
	    boolean BoolValTable[]=new boolean[externs];
	    Hashtable Adj_List_local;
	    
	    
	    Adj_List_local=new Hashtable();
	    
	    for(int i=0; i < externs ; i++) {
		System.out.println(fd[i].getSymbol());
		BoolValTable[i]=fs.get(fd[i]);
	    }

	    for(int k=1; k<noOfIterations; k++) {
		for(int j=0; j< fd.length ;j++) {
		    if ((k% (1<<j))==0)
			BoolValTable[j]=(!BoolValTable[j]);
		}
		
		FlagState fstemp=new FlagState(fs.getStateTable(),cd);
		FlagDescriptor fdtemp[]=(FlagDescriptor [])flags.get(cd);
		
		for(int i=0; i < externs;i++)
		    {
			fstemp.put(fdtemp[i],new Boolean(BoolValTable[i]));
		    }
		
		q_ret.offer(new TriggerState(cd,fstemp));
	    }

	    return q_ret;
	    
	}
    }

    private Queue createPossibleRuntimeStates(ClassDescriptor cd,FlagState fs) throws java.io.IOException {
	int noOfIterations, externs;
	Hashtable Adj_List_temp;
	boolean onlyExterns;
	
	System.out.println("Inside CreatePossible runtime states(flagstates)");

	if (Adj_List.containsKey(cd))
	    Adj_List_temp=(Hashtable)Adj_List.get(cd);
	else {
	    Adj_List_temp=new Hashtable();
	    Adj_List.put(cd,Adj_List_temp);
	}

	externs=((Integer)extern_flags.get(cd)).intValue();

	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);

	onlyExterns=fd.length==externs;
	
	Queue  q_ret;

	q_ret=new LinkedList();
	
	if (onlyExterns) {
	    noOfIterations=1<<fd.length;
	    boolean BoolValTable[]=new boolean[fd.length];

	    for(int i=0; i < fd.length ; i++) {
		System.out.println(fd[i].getSymbol());
		BoolValTable[i]=false;
	    }
	    
	    return q_ret;
	} else {
	    System.out.println("inside else part(fs)");
	    noOfIterations=1<<externs;
	    boolean BoolValTable[]=new boolean[externs];
	    Hashtable Adj_List_local;
	    
	    
	    Adj_List_local=new Hashtable();
	    
	    for(int i=0; i < externs ; i++) {
		System.out.println(fd[i].getSymbol());
		BoolValTable[i]=fs.get(fd[i]);
	    }

	    if (! wasFlagStateProcessed(Adj_List_temp,fs)) {
		Adj_List_temp.put(fs,new Vector());
	    }
	    for(int k=1; k<noOfIterations; k++) {
		for(int j=0; j< fd.length ;j++) {
		    if ((k% (1<<j))==0)
			BoolValTable[j]=(!BoolValTable[j]);
		}
		
		FlagState fstemp=new FlagState(fs.getStateTable(),cd);
		FlagDescriptor fdtemp[]=(FlagDescriptor [])flags.get(cd);
		
		for(int i=0; i < externs;i++) {
		    fstemp.put(fdtemp[i],new Boolean(BoolValTable[i]));
		}
		
		q_ret.offer(fstemp);
		((Vector)(Adj_List_temp.get(fs))).add(new Edge(fstemp,"Runtime"));
		
	    }
	    return q_ret;
	    
	}
    }


    private void processTasksWithPost(ClassDescriptor cd, Hashtable pre) {
    }

    private ClassDescriptor processFlatNew(FlatNode fn) {
	if (! (fn.getNext(0).kind() == 13)) {
	    return (((FlatNew)fn).getType().getClassDesc());
	}
	return null;
    }

}
