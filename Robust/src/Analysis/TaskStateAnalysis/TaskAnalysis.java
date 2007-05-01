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
    Queue<FlagState> q_main;
    Hashtable map;
    TempDescriptor temp;
    
    /** 
     * Class Constructor
     *
     * @param state a flattened State object
     * @see State
     * @param map Hashtable containing the temp to var mapping
     */
    public TaskAnalysis(State state,Hashtable map)
    {
	this.state=state;
	this.map=map;
    }
    
    /** This function builds a table of flags for each class **/

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
	Hashtable<FlagState,Vector> Adj_List_temp;
	
	getFlagsfromClasses();
	
	int externs;
	q_main=new LinkedList<FlagState>();
	
	for(Iterator it_classes=(Iterator)flags.keys();it_classes.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor)it_classes.next();
	    externs=((Integer)extern_flags.get(cd)).intValue();
	    FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);

	    //Debug block
	    System.out.println("Inside taskAnalysis;\n Class:"+ cd.getSymbol());
	    System.out.println("No of externs " + externs);
	    System.out.println("No of flags: "+fd.length);
	    //Debug block
	    
	   Adj_List.put(cd,new Hashtable<FlagState,Vector>());
	}	
	
	TypeUtil typeutil=new TypeUtil(state);
	ClassDescriptor startupobject=typeutil.getClass(TypeUtil.StartupClass);
	Adj_List_temp=(Hashtable<FlagState,Vector>)Adj_List.get(startupobject);
	
	FlagState fsstartup=new FlagState(startupobject);
	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(startupobject);
		    
	FlagState fstemp=fsstartup.setFlag(fd[0],true);
	Vector vtemp=new Vector();
	Edge estartup=new Edge(fstemp,"Runtime");
	vtemp.add(estartup);
		    
	Adj_List_temp.put(fsstartup,vtemp);
		    	    
	Queue<FlagState> q_temp=analyseTasks(fstemp);

	if ( q_temp != null) {
		q_main.addAll(q_temp);
	}
	
	while (q_main.size() > 0) {
	    // ****debug block********
	    
	    System.out.println("/***********contents of main q before pop**********/");
	    for (Iterator it_qm=q_main.iterator();it_qm.hasNext();)
		{
		    
		    FlagState fs_qm=(FlagState)it_qm.next();
		    
		    System.out.println("FS : "+fs_qm.getClassDescriptor().toString()+" : "+fs_qm.toString((FlagDescriptor [])flags.get(fs_qm.getClassDescriptor())));
		} 
	    System.out.println("/*********************************/");
	    // ****debug block********
	    FlagState trigger=q_main.poll();
	    
	   
	    q_temp=createPossibleRuntimeStates(trigger);
	    
	    if ( q_temp != null){
		    q_main.addAll(q_temp);
		    
		// ****debug block********
	    
	    System.out.println("/***********contents of main q**********/");
	    for (Iterator it_qm=q_main.iterator();it_qm.hasNext();)
		{
		    
		    FlagState fs_qm=(FlagState)it_qm.next();
		    
		    System.out.println("FS : "+fs_qm.getClassDescriptor().toString()+" : "+fs_qm.toString((FlagDescriptor [])flags.get(fs_qm.getClassDescriptor())));
		} 
	    System.out.println("/*********************************/");
	    // ****debug block********
	    
	    q_temp=analyseTasks(trigger);
	    
	    if ( q_temp != null) 
			q_main.addAll(q_temp);
			
		// ****debug block********
	    
	    System.out.println("/***********contents of main q after analyse tasks**********/");
	    for (Iterator it_qm=q_main.iterator();it_qm.hasNext();)
		{
		    
		    FlagState fs_qm=(FlagState)it_qm.next();
		    
		    System.out.println("FS : "+fs_qm.getClassDescriptor().toString()+" : "+fs_qm.toString((FlagDescriptor [])flags.get(fs_qm.getClassDescriptor())));
		} 
	    System.out.println("/*********************************/");
	    // ****debug block********
	    
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


    public Queue<FlagState> analyseTasks(FlagState fs) throws java.io.IOException {
	
	
	Hashtable Adj_List_temp;
	Queue<FlagState> q_retval;
	
	
	
	ClassDescriptor cd=fs.getClassDescriptor();
	
	Adj_List_temp=(Hashtable)Adj_List.get(cd);
	
	int externs=((Integer)extern_flags.get(cd)).intValue();
	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);

	q_retval=new LinkedList<FlagState>();
	//***Debug Block***

	//while (q.size() != 0) {
	    System.out.println("inside while loop in analysetasks \n");
	    
	    //***Debug Block***
	    //FlagDescriptor[] ftemp=(FlagDescriptor[])flags.get(cd);
	    //System.out.println("Processing state: "+cd.getSymbol()+" " + fsworking.toString(ftemp));
	    //***Debug Block***

	    	    
	    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();it_tasks.hasNext();) {
		TaskDescriptor td = (TaskDescriptor)it_tasks.next();
		boolean taskistriggered=false;
		int trigger_ctr=0;
		String taskname=getTaskName(td);
		
		

		//***Debug Block***
		
		System.out.println("Method: AnalyseTasks");
		System.out.println(taskname);
		System.out.println();
		
		//***Debug Block***
		
		
		
		for(int i=0; i < td.numParameters(); i++) {
		    FlagExpressionNode fen=td.getFlag(td.getParameter(i));
		    //if ( (td.getParamType(i).equals(cd))&&(isTaskTrigger(fen,fs))){
			if ((isParamOfSameClass(td.getParamType(i),cd)) && (isTaskTrigger(fen,fs))){
				taskistriggered = true;
				System.out.println(td.getParamType(i).toString()+"   "+cd.toString());
				temp=(TempDescriptor)map.get(td.getParameter(i));
				trigger_ctr++;
			}
		}
		
		if (trigger_ctr>1)
			throw new Error("Illegal Operation: A single flagstate cannot satisfy more than one parameter of a task.");

		if (taskistriggered) {
		    //***Debug Block***
		    //
		    System.out.println("inside taskistriggered");
		    
		    //***Debug Block***
		    
		    taskistriggered=false;
			Adj_List_temp.put(fs,new Vector());
					
			//Iterating through the nodes
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
				    
				    q_retval.offer(evalNewObjNode(nn));
				    
				    
				    
				    // ****debug block********
				//    System.out.println("/***********contents of q ret **********/");
				   /* for (Iterator it_qret=q_retval.iterator();it_qret.hasNext();) {
					TriggerState ts_qret=(TriggerState)it_qret.next();
					FlagState fs_qret=ts_qret.getState();
					
					System.out.println("FS : "+fs_qret.toString((FlagDescriptor [])flags.get(ts_qret.getClassDescriptor())));
				    }*/
				   // ****debug block********
									
				}
				if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.TASKEXIT) {
				    //***Debug Block***
				    //
				    System.out.println("TaskExit");
				    //***Debug Block***
				    FlagState fs_taskexit=evalTaskExitNode(nn,cd,fs);
				   	
				    
				    
				    if (!edgeexists(Adj_List_temp,fs,fs_taskexit,taskname)) {
					((Vector)Adj_List_temp.get(fs)).add(new Edge(fs_taskexit,taskname));
				    }
				    if ((!wasFlagStateProcessed(Adj_List_temp,fs_taskexit)) && (!existsInQMain(fs_taskexit)) && (!existsInQ(q_retval,fs_taskexit))){
					q_retval.offer(fs_taskexit);
				    }
				}
			    }
			    
			    if (!visited.contains(nn) && !tovisit.contains(nn)) {
				tovisit.add(nn);
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
    
    private boolean isParamOfSameClass(TypeDescriptor typedesc, ClassDescriptor classdesc){
	   	if (typedesc.getSafeSymbol().equals(classdesc.getSafeSymbol()))
	   		return true;
	   	else
	   		return false;
	}
    
    
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
		return fstemp;
	}
	
	private FlagState evalTaskExitNode(FlatNode nn,ClassDescriptor cd,FlagState fs){
		FlagState fstemp=fs;
				    
		for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();) {
			TempFlagPair tfp=(TempFlagPair)it_tfp.next();
			if (temp.toString().equals(tfp.getTemp().toString()))
				fstemp=fstemp.setFlag(tfp.getFlag(),((FlatFlagActionNode)nn).getFlagChange(tfp));
		}
		return fstemp;
	}		
	    

    private boolean wasFlagStateProcessed(Hashtable Adj_List,FlagState fs) {
	Enumeration e=Adj_List.keys();
	
	while(e.hasMoreElements()) {
	    FlagState fsv = (FlagState)(e.nextElement());

	    if (fsv.equals(fs))
		return true;
	}
	return false;
    }

   /* private boolean existsInQueue(TriggerState ts) {
	throw new Error("Use hashcode/contains of set method to find...no linear search allowed");
    }*/

    private boolean existsInQMain(FlagState fs) {
		if (q_main.contains(fs))
			return true;
		else
			return false;    
    }
    
    private boolean existsInQ(Queue q,FlagState fs) {
		if (q.contains(fs))
			return true;
		else
			return false;    
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
	
	//***debug***
	FlagDescriptor[] flg=(FlagDescriptor [])flags.get(cd);
	for(int i = 0; i < flg.length ; i++)
	{
		dotwriter.write(flg[i].toString()+"\n");
	}

	//*** debug***	
	Enumeration e=((Hashtable)Adj_List.get(cd)).keys();
	while(e.hasMoreElements()) {
	    FlagState fsv = (FlagState)(e.nextElement());
	    System.out.println(fsv.toString());
	    Hashtable test=(Hashtable)Adj_List.get(cd);
	    Vector edges=(Vector)test.get(fsv);
	    for(int i=0;i < edges.size();i++) {
		dotwriter.write(fsv.toString((FlagDescriptor [])flags.get(cd))+" -> "+((Edge)edges.get(i)).getTarget().toString((FlagDescriptor [])flags.get(cd))+"[label=\""+((Edge)edges.get(i)).getLabel()+"\"];\n");
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

    private boolean edgeexists(Hashtable Adj_List_local,FlagState v1, FlagState v2,String label) {
	Vector edges=(Vector)Adj_List_local.get(v1);
	
	if (edges != null) {
	    for(int i=0;i < edges.size();i++) {
		FlagState fs=((Edge)edges.get(i)).getTarget();
		if (fs.equals(v2) && (label.compareTo(((Edge)edges.get(i)).getLabel())==0))
		    return true;
	    }
	}
	return false;
    }

    private Queue createPossibleRuntimeStates(FlagState fs) throws java.io.IOException {
	
	int noOfIterations, externs;
	Hashtable Adj_List_temp, Adj_List_local;
	
	
	System.out.println("Inside CreatePossible runtime states");
	
	ClassDescriptor cd = fs.getClassDescriptor();
	
	Adj_List_temp=(Hashtable)Adj_List.get(cd);
	FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);	
	externs=((Integer)extern_flags.get(cd)).intValue();
	//System.out.println("No of externs:"+externs);
	

	Queue<FlagState>  q_ret=new LinkedList<FlagState>();

	
	    noOfIterations=(1<<externs) - 1;
	   // System.out.println("No of iterations: "+noOfIterations);
	    boolean BoolValTable[]=new boolean[externs];

	    for(int i=0; i < externs ; i++) {
		System.out.println(fd[i].getSymbol());
		BoolValTable[i]=fs.get(fd[i]);
	    }

	   /* if (! wasFlagStateProcessed(Adj_List_temp,fs)) {
			Adj_List_temp.put(fs,new Vector());
	    }
	    */
	    if (externs > 0){
	    Adj_List_local=new Hashtable();
		Adj_List_local.put(fs, new Vector());
	    
	    
	    for(int k=0; k<noOfIterations; k++) {
			for(int j=0; j < externs ;j++) {
		   	    if ((k% (1<<j)) == 0)
					BoolValTable[j]=(!BoolValTable[j]);
			}

			FlagState fstemp=fs;
		
			for(int i=0; i < externs;i++) {
			    fstemp=fstemp.setFlag(fd[i],BoolValTable[i]);
			}
			Adj_List_local.put(fstemp,new Vector());
			
			if (!existsInQMain(fstemp) && ! wasFlagStateProcessed(Adj_List_temp,fs)){
				q_ret.add(fstemp);
			}
		
			for (Enumeration en=Adj_List_local.keys();en.hasMoreElements();){
				FlagState fs_local=(FlagState)en.nextElement();
				System.out.println(fs_local.toString(fd)+" : "+fstemp.toString(fd));
				if (fstemp.equals(fs_local))
				{
				    System.out.print(" : equal");
					continue;
				}
				else{
					//if (!edgeexists(Adj_List_local,fstemp,fs_local,"Runtime"))
						((Vector)Adj_List_local.get(fstemp)).add(new Edge(fs_local,"Runtime"));
						//System.out.println(fstemp.toString(fd)+" : "+fs_local.toString(fd));

					//if (!edgeexists(Adj_List_local,fs_local,fstemp,"Runtime"))
						((Vector)Adj_List_local.get(fs_local)).add(new Edge(fstemp,"Runtime"));
						//System.out.println(fs_local.toString(fd)+" : "+fstemp.toString(fd));

				}
			}
		}
		
		
		//***debug
		for (Enumeration en=Adj_List_local.keys();en.hasMoreElements();){
			FlagState fs_local=(FlagState)en.nextElement();
			System.out.print("Source FS: "+fs_local.toString(fd)+" -> ");
			Vector edges=(Vector)Adj_List_local.get(fs_local);
					if (edges != null) {
						for(int i=0;i < edges.size();i++) {
							Edge edge=(Edge)edges.get(i);
							System.out.print("("+edge.getTarget().toString(fd)+" "+edge.getLabel()+")\n");
						}
					}
		}
		//***debug
		for (Enumeration en=Adj_List_local.keys();en.hasMoreElements();){
				FlagState fs_local=(FlagState)en.nextElement();
				if (wasFlagStateProcessed(Adj_List_temp,fs_local)){
					System.out.println("FS: "+fs_local.toString(fd)+" processed already");
					//Add edges that don't exist already.
					Vector edges=(Vector)Adj_List_local.get(fs_local);
					if (edges != null) {
	   					for(int i=0;i < edges.size();i++) {
							Edge edge=(Edge)edges.get(i);
								if (! ((Vector)Adj_List_temp.get(fs_local)).contains(edge))
		   					 		((Vector)Adj_List_temp.get(fs_local)).add(edge);	
	    				}
					}
					//((Vector)Adj_List_temp.get(fs_local)).addAll((Vector)Adj_List_local.get(fs_local));
				}
				else{
					System.out.println("FS: "+fs_local.toString(fd)+" not processed already");
					Adj_List_temp.put(fs_local,(Vector)Adj_List_local.get(fs_local));
				}		
		} 
		
		//***debug
		for (Enumeration en=Adj_List_temp.keys();en.hasMoreElements();){
			FlagState fs_local=(FlagState)en.nextElement();
			System.out.print("Source FS: "+fs_local.toString(fd)+" -> ");
			Vector edges=(Vector)Adj_List_local.get(fs_local);
					if (edges != null) {
						for(int i=0;i < edges.size();i++) {
							Edge edge=(Edge)edges.get(i);
							System.out.print("("+edge.getTarget().toString(fd)+" "+edge.getLabel()+")\n");
						}
					}
		}
		//***debug 
		}
		
		  
	
	    
	    return q_ret;
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
