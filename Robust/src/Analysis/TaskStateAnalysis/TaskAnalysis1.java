package TaskStateAnalysis;
import TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.File;
import java.io.FileWriter;

public class TaskAnalysis
{
	State state;
//	Vector vFlags;   //Vector holding the FlagDescriptors from all the classes used
//	FlagDescriptor flags[];
	Hashtable Adj_List;
	Hashtable flags;

		
	public TaskAnalysis(State state)
	{
		this.state=state;
	}
	private Hashtable getFlagsfromClasses()  //This function returns the number of external flags amongst the other things it does.
	{
	//	int ctr=0; //Counter to keep track of the number of external flags
		flags=new Hashtable();
		Hashtable externs = new Hashtable();

		for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator();it_classes.hasNext();)
		{
			
			ClassDescriptor cd = (ClassDescriptor)it_classes.next();
			System.out.println(cd.getSymbol());
			Vector vFlags=new Vector();
			FlagDescriptor flag[];
			int ctr=0;

			for(Iterator it_cflags=cd.getFlags();it_cflags.hasNext();)
			{
				FlagDescriptor fd = (FlagDescriptor)it_cflags.next();
				System.out.println(fd.toString());
				vFlags.add(fd);
			}
			if (vFlags.size()!=0)	
			{
				flag=new FlagDescriptor[vFlags.size()];

				for(int i=0;i < vFlags.size() ; i++)
				{
					if (((FlagDescriptor)vFlags.get(i)).getExternal())
					{
						flag[ctr]=(FlagDescriptor)vFlags.get(i);
						vFlags.remove(flag[ctr]);
						ctr++;
					}
				}
				for(int i=0;i < vFlags.size() ; i++)
				{
					flag[i+ctr]=(FlagDescriptor)vFlags.get(i);
				}
				externs.put(cd,new Integer(ctr));
				flags.put(cd,flag);
			
			}
		}
		return externs;
	}

	public void taskAnalysis() throws java.io.IOException
	{
		Adj_List=new Hashtable();
		Queue<TriggerState> q_main;
		Hashtable extern_flags=getFlagsfromClasses();
		int externs;

		q_main=new LinkedList<TriggerState>();

	//	for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator();it_classes.hasNext();)
		for(Iterator it_classes=(Iterator)flags.keys();it_classes.hasNext();)
		{
			ClassDescriptor cd=(ClassDescriptor)it_classes.next();

			externs=((Integer)extern_flags.get(cd)).intValue();

		//	no_of_externs=getFlagsfromClasses();

			FlagDescriptor[] fd=(FlagDescriptor[])flags.get(cd);
			//Debug block
				System.out.println("taskAnalysis: "+ cd.getSymbol());
				System.out.println("externs " + externs);
				System.out.println("fd len "+fd.length);
			//Debug block
			if (fd.length == externs)
			{
				System.out.println("extern called");
				boolean onlyExterns=true;
				processExterns(true,cd);
			}
			else
			{
				if ((fd.length == 1) && (fd[0].getSymbol().compareTo("initialstate")==0))
				{
					FlagState fstemp=new FlagState(fd);
					Hashtable Adj_List_temp=new Hashtable();

					fstemp.put(fd[0],new Boolean(true));
					Vector vtemp=new Vector();
					vtemp.add(new Edge(fstemp,"Runtime"));
					Adj_List_temp.put(new FlagState(fd),vtemp);
					Adj_List.put(cd,Adj_List_temp);

					Queue<TriggerState> q_temp=analyseTasks(new TriggerState(cd,fstemp));
					

					if ( q_temp != null)
					{
						q_main.addAll(q_temp);
					}
					
				}
			}
		}
		while (q_main.size() > 0)
		{
			analyseTasks(q_main.poll());
		}
			
	}

	
	public Queue<TriggerState> analyseTasks(TriggerState ts) throws java.io.IOException
	{
		Queue<FlagState> q; 
		Hashtable pretasks;	
		Hashtable Adj_List_temp;	
		Queue<TriggerState> q_retval;

		ClassDescriptor cd=ts.getClassDescriptor();
		
		if (Adj_List.containsKey(cd))
		{
			Adj_List_temp=(Hashtable)Adj_List.get(cd);
		}
		else
		{
			Adj_List_temp=new Hashtable();
			Adj_List.put(cd,Adj_List_temp);
		}

		q = new LinkedList<FlagState>();
		q_retval=new LinkedList<TriggerState>();
		pretasks = new Hashtable();

		q.offer(ts.getState()); 
		
		Vector vinitial=new Vector();
	//	vinitial.add(new Edge(new FlagState(fsinitial.getStateTable()),"Runtime"));

	//	Adj_List_temp.put(q.peek(),vinitial);

	      
	      //  q.offer(fsinitial);
		



		//***Debug Block***
		//
		/*Enumeration eFlags=fsinitial.getStateTable().keys();
		
		while(eFlags.hasMoreElements())
		{
			System.out.println(((FlagDescriptor)eFlags.nextElement()).toString());
		} */
		//***Debug Block***
		
		while (q.size() != 0 /* && debug_ctr<10*/ )
		{
			System.out.println("inside while loop in analysetasks \n");
			
			FlagState fsworking=new FlagState(q.poll().getStateTable());
	//		System.out.println((q.poll()).isEqual(fsworking));
			
			

			if (!wasFlagStateProcessed(Adj_List_temp,fsworking))
			{
			Adj_List_temp.put(fsworking,new Vector());
			printAdjList(cd);
			}
			else
			{
				continue;
			}
		//	debug_ctr++;

			//***Debug Block***

	//		System.out.println("Processing state: " + fsworking.toString(flags));

			//***Debug Block***


			for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator();it_tasks.hasNext();)
			{
				TaskDescriptor td = (TaskDescriptor)it_tasks.next();
				boolean taskistriggered=false;
				int ctr=0;
				String taskname;

				taskname=getTaskName(td);

				//***Debug Block***

				System.out.println();
				System.out.println("Method: AnalyseTasks");
				System.out.println(taskname);
				System.out.println();

				//***Debug Block***


				for(int i=0; i < td.numParameters(); i++)
				{
					System.out.println("num parms"+td.numParameters());

					FlagExpressionNode fen=td.getFlag(td.getParameter(i));
					if (isTaskTrigger(fen,fsworking))
						ctr++;

				}

				//***Debug Block***

				System.out.println("xxx "+ctr);

				//***Debug Block***

				if (ctr == td.numParameters())
				{
					taskistriggered = true;
				}
				if (taskistriggered)
				{
					//***Debug Block***
					//
					System.out.println("inside taskistriggered");

					//***Debug Block***
					
			//		Adj_List.put(fsworking,new Integer(2));
			//		System.out.println(td.toString());
			//		printAdjList(cd);
					

					FlatMethod fm = state.getMethodFlat(td);
					FlatNode fn=fm.methodEntryNode();
				
					HashSet tovisit= new HashSet();
					HashSet visited= new HashSet();
			
                	       		tovisit.add(fn);
	                		while(!tovisit.isEmpty())
	                		{
					        FlatNode fn1 = (FlatNode)tovisit.iterator().next();
		                		tovisit.remove(fn1);
		                		visited.add(fn1);
						for(int i = 0; i < fn1.numNext(); i++)
						{
							FlatNode nn=fn1.getNext(i);
						 	if (nn.kind()==13)
						 	{	
								//***Debug Block***
								// System.out.println();
								 
								
								
								//***Debug Block***

								if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.PRE)
								{
									
									//***Debug Block***

									System.out.println("PRE");

									//***Debug Block***

									FlagState fstemp=new FlagState(fsworking.getStateTable());

									for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();)
								        {	
						 				TempFlagPair tfp=(TempFlagPair)it_tfp.next();
										//	System.out.println(tfp.getTemp()+"  " +tfp.getFlag()+"   "+((FlatFlagActionNode)nn).getFlagChange(tfp));
										fstemp.put(tfp.getFlag(),new Boolean(((FlatFlagActionNode)nn).getFlagChange(tfp)));			
									}
									if (! edgeexists(Adj_List_temp,fsworking,fstemp,taskname))	
									{
										((Vector)Adj_List_temp.get(fsworking)).add(new Edge(fstemp,getTaskName(td)));
									}
									if (!wasFlagStateProcessed(Adj_List_temp,fstemp))
									{
										q.offer(fstemp);
										pretasks.put(td,fstemp);
									}
									fstemp=null;
							
								}			
								if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.NEWOBJECT)
								{

									//***Debug Block***

									System.out.println("NEWObject");
									//***Debug Block***
									TempDescriptor[] tdArray = ((FlatFlagActionNode)nn).readsTemps();
									//Under the safe assumption that all the temps in FFAN.NewObject node are of the same type(class)
									ClassDescriptor cd_new=tdArray[0].getType().getClassDesc();
										
									FlagState fstemp=new FlagState((FlagDescriptor[])flags.get(cd_new));

									for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();)
									{
										TempFlagPair tfp=(TempFlagPair)it_tfp.next();
										fstemp.put(tfp.getFlag(),new Boolean(((FlatFlagActionNode)nn).getFlagChange(tfp)));
		
									}
										//***Debug Block***

									/*	System.out.println();
										System.out.println(fstemp.toString(flags));
										System.out.println();
									*/
										//***Debug Block***
											
									q_retval.offer(new TriggerState(cd_new,fstemp));
									
									fstemp=null;
								}	
								if (((FlatFlagActionNode)nn).getFFANType() == FlatFlagActionNode.TASKEXIT)
								{
									//***Debug Block***
									//
									System.out.println("TaskExit");
									//***Debug Block***


									FlagState fstemp=new FlagState(fsworking.getStateTable());

									for(Iterator it_tfp=((FlatFlagActionNode)nn).getTempFlagPairs();it_tfp.hasNext();)
									{
										TempFlagPair tfp=(TempFlagPair)it_tfp.next();
										fstemp.put(tfp.getFlag(),new Boolean(((FlatFlagActionNode)nn).getFlagChange(tfp)));
									}
										//***Debug Block***

								/*		System.out.println();
										System.out.println(fstemp.toString((FlagDescriptor [])flags.get(cd)));
										System.out.println();

										System.out.println("taskexit fsworking "+fsworking.toString(flags));
										System.out.println("taskexit fstemp "+fstemp.toString(flags)); */

										//***Debug Block***
									if (!edgeexists(Adj_List_temp,fsworking,fstemp,taskname))	
									{
										((Vector)Adj_List_temp.get(fsworking)).add(new Edge(fstemp,getTaskName(td)));
									}
									if (!wasFlagStateProcessed(Adj_List_temp,fstemp))
									{
										q.offer(fstemp);
									}
									 fstemp=null;

								}
							}
							if (!visited.contains(nn) && !tovisit.contains(nn))
							{
								tovisit.add(nn);
							}	
					 	}		
					}
				}
			}
			
		}
		createDOTfile(ts.getClassDescriptor());
		
		if (q_retval.size()==0)
		{
			return null;
		}
		else
		{
			return q_retval;
		}
	}

	private boolean isTaskTrigger(FlagExpressionNode fen,FlagState fs)
	{
		if (fen instanceof FlagNode)
		{
			return fs.get(((FlagNode)fen).getFlag());
		}
		else
		{
			switch (((FlagOpNode)fen).getOp().getOp())
			{
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
	
	}

	private boolean wasFlagStateProcessed(Hashtable Adj_List,FlagState fs)
	{
		Enumeration e=Adj_List.keys();
	 
		while(e.hasMoreElements())
		{
			FlagState fsv = (FlagState)(e.nextElement());
			
			if (fsv.isEqual(fs))
			{
				return true;
			}
		}
		return false;
	}

	public void printAdjList(ClassDescriptor cd)
	{
		Enumeration e=((Hashtable)Adj_List.get(cd)).keys();
		//System.out.println(Adj_List.size());

		 while(e.hasMoreElements())
		 {
		 	FlagState fsv = (FlagState)(e.nextElement());
	//		System.out.println("fsv val: "+Adj_List.get(fsv));
	//		System.out.println(fsv.toString(flags));
		}
	}
	public void createDOTfile(ClassDescriptor cd) throws java.io.IOException
	{
		
		File dotfile= new File("graph"+cd.getSymbol()+".dot");
		
		FileWriter dotwriter=new FileWriter(dotfile,true);

		dotwriter.write("digraph G{ \n");
		
		dotwriter.write("center=true;\norientation=landscape;\n");
		

		Enumeration e=((Hashtable)Adj_List.get(cd)).keys();
		while(e.hasMoreElements())
		{
			FlagState fsv = (FlagState)(e.nextElement());
			System.out.println(fsv.toString());
			Hashtable test=(Hashtable)Adj_List.get(cd);
			Vector edges=(Vector)test.get(fsv);
			for(int i=0;i < edges.size();i++)
			{
				dotwriter.write(fsv.toString((FlagDescriptor [])flags.get(cd))+" -> "+((Edge)edges.get(i)).getState().toString((FlagDescriptor [])flags.get(cd))+"[label=\""+((Edge)edges.get(i)).getName()+"\"];\n");
			}

		}	
		dotwriter.write("}\n");
		dotwriter.flush();
		dotwriter.close();
	}

	private String getTaskName(TaskDescriptor td)
	{
		StringTokenizer st = new StringTokenizer(td.toString(),"(");
		return st.nextToken();
	}
	private boolean edgeexists(Hashtable Adj_List,FlagState v1, FlagState v2,String name)
	{
		Vector edges=(Vector)Adj_List.get(v1);
		for(int i=0;i < edges.size();i++)
		{
			FlagState fs=((Edge)edges.get(i)).getState();
			if (fs.isEqual(v2) && (name.compareTo(((Edge)edges.get(i)).getName())==0))
				return true;
		}
	        return false;
	}
	private void processExterns(boolean onlyExterns,ClassDescriptor cd) throws java.io.IOException
	{
		int noOfIterations;
		Hashtable Adj_List_temp;
		if (Adj_List.containsKey(cd))
		{
			Adj_List_temp=(Hashtable)Adj_List.get(cd);
		}
		else
		{
			Adj_List_temp=new Hashtable();
			Adj_List.put(cd,Adj_List_temp);
		}

			
		if (onlyExterns)
		{
			FlagDescriptor [] fd=(FlagDescriptor [])flags.get(cd);
			System.out.println("onlyExterns"+fd.length);
			noOfIterations=(int)Math.pow(2.0,fd.length);
			boolean BoolValTable[]=new boolean[fd.length];

			for(int i=0; i < fd.length ; i++)
			{
				System.out.println(fd[i].getSymbol());
				BoolValTable[i]=false;
			}
			Adj_List_temp.put(new FlagState(fd),new Vector());

			for(int k=1; k<noOfIterations; k++)
			{
				for(int j=0; j< fd.length ;j++)
				{
					if (k% (int)Math.pow(2.0,(double)j) == 0)
						BoolValTable[j]=(!BoolValTable[j]);
				}

				FlagState fstemp=new FlagState(fd);
				int i=0;
				for(Enumeration e=fstemp.getStateTable().keys(); e.hasMoreElements() && i < fd.length;i++)
				{
					fstemp.put((FlagDescriptor)e.nextElement(),new Boolean(BoolValTable[i]));
				}
				Adj_List_temp.put(fstemp,new Vector());
			}

			Enumeration e=Adj_List_temp.keys();
			while(e.hasMoreElements())
			{
				FlagState fstemp=(FlagState)e.nextElement();
				Enumeration en=Adj_List_temp.keys();
				while (en.hasMoreElements())
				{
					FlagState fs=(FlagState)en.nextElement();
					if(fstemp == fs)
					{
						continue;
					}
					else
					{
						((Vector)Adj_List_temp.get(fstemp)).add(new Edge(fs,"Runtime"));
					}
				}
			}
		}
		createDOTfile(cd);
		
			
	}

	private void processTasksWithPost(ClassDescriptor cd, Hashtable pre)
	{
		//
	}


}


