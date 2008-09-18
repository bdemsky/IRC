package Main;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.Vector;

import IR.Tree.ParseNode;
import IR.Tree.BuildIR;
import IR.Tree.SemanticCheck;
import IR.Flat.BuildCodeMultiCore;
import IR.Flat.BuildFlat;
import IR.Flat.BuildCode;
import IR.ClassDescriptor;
import IR.State;
import IR.TaskDescriptor;
import IR.TypeUtil;
import Analysis.Scheduling.Schedule;
import Analysis.Scheduling.ScheduleAnalysis;
import Analysis.Scheduling.ScheduleEdge;
import Analysis.Scheduling.ScheduleNode;
import Analysis.Scheduling.ScheduleSimulator;
import Analysis.TaskStateAnalysis.TaskAnalysis;
import Analysis.TaskStateAnalysis.TaskTagAnalysis;
import Analysis.TaskStateAnalysis.TaskGraph;
import Analysis.CallGraph.CallGraph;
import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.TagAnalysis;
import Analysis.TaskStateAnalysis.GarbageAnalysis;
import Analysis.TaskStateAnalysis.ExecutionGraph;
import Analysis.TaskStateAnalysis.SafetyAnalysis;
import Analysis.Locality.LocalityAnalysis;
import Analysis.Locality.GenerateConversions;
import Analysis.Prefetch.PrefetchAnalysis;
import Analysis.FlatIRGraph.FlatIRGraph;
import Analysis.OwnershipAnalysis.OwnershipAnalysis;
import Interface.*;

public class Main {

  /** Main method for the compiler.  */

  public static void main(String args[]) throws Exception {
    String ClassLibraryPrefix="./ClassLibrary/";
    State state=new State();

    for(int i=0; i<args.length; i++) {
      String option=args[i];
      if (option.equals("-precise"))
	IR.Flat.BuildCode.GENERATEPRECISEGC=true;
      else if (option.equals("-prefetch"))
	state.PREFETCH=true;
      else if (option.equals("-dir"))
	IR.Flat.BuildCode.PREFIX=args[++i]+"/";
      else if (option.equals("-selfloop"))
	state.selfloops.add(args[++i]);
      else if (option.equals("-excprefetch"))
	state.excprefetch.add(args[++i]);
      else if (option.equals("-classlibrary"))
	ClassLibraryPrefix=args[++i]+"/";
      else if(option.equals("-numcore")) {
	++i;
	state.CORENUM = Integer.parseInt(args[i]);
      } else if (option.equals("-mainclass"))
	state.main=args[++i];
      else if (option.equals("-trueprob")) {
	state.TRUEPROB=Double.parseDouble(args[++i]);
      } else if (option.equals("-printflat"))
	State.PRINTFLAT=true;
      else if (option.equals("-struct"))
	state.structfile=args[++i];
      else if (option.equals("-conscheck"))
	state.CONSCHECK=true;
      else if (option.equals("-task"))
	state.TASK=true;
      else if (option.equals("-taskstate"))
	state.TASKSTATE=true;
      else if (option.equals("-tagstate"))
	state.TAGSTATE=true;
      else if (option.equals("-flatirtasks")) {
	state.FLATIRGRAPH=true;
	state.FLATIRGRAPHTASKS=true;
      } else if (option.equals("-flatirusermethods")) {
	state.FLATIRGRAPH=true;
	state.FLATIRGRAPHUSERMETHODS=true;
      } else if (option.equals("-flatirlibmethods")) {
	state.FLATIRGRAPH=true;
	state.FLATIRGRAPHLIBMETHODS=true;
      } else if (option.equals("-multicore"))
	state.MULTICORE=true;
      else if (option.equals("-ownership"))
	state.OWNERSHIP=true;
      else if (option.equals("-ownallocdepth")) {
	state.OWNERSHIPALLOCDEPTH=Integer.parseInt(args[++i]);
      } else if (option.equals("-ownwritedots")) {
	state.OWNERSHIPWRITEDOTS=true;
	if (args[++i].equals("all")) {
	  state.OWNERSHIPWRITEALL=true;
	}
      } else if (option.equals("-ownaliasfile"))
	state.OWNERSHIPALIASFILE=args[++i];
      else if (option.equals("-optional"))
	state.OPTIONAL=true;
      else if (option.equals("-raw"))
	state.RAW=true;
      else if (option.equals("-scheduling"))
	state.SCHEDULING=true;
      else if (option.equals("-thread"))
	state.THREAD=true;
      else if (option.equals("-dsm"))
	state.DSM=true;
      else if (option.equals("-webinterface"))
	state.WEBINTERFACE=true;
      else if (option.equals("-instructionfailures"))
	state.INSTRUCTIONFAILURE=true;
      else if (option.equals("-help")) {
	System.out.println("-classlibrary classlibrarydirectory -- directory where classlibrary is located");
	System.out.println("-selfloop task -- this task doesn't self loop its parameters forever");
	System.out.println("-dir outputdirectory -- output code in outputdirectory");
	System.out.println("-struct structfile -- output structure declarations for repair tool");
	System.out.println("-mainclass -- main function to call");
	System.out.println("-dsm -- distributed shared memory support");
	System.out.println("-precise -- use precise garbage collection");
	System.out.println("-conscheck -- turn on consistency checking");
	System.out.println("-task -- compiler for tasks");
	System.out.println("-thread -- threads");
	System.out.println("-trueprob <d> -- probability of true branch");
	System.out.println("-printflat -- print out flat representation");
	System.out.println("-instructionfailures -- insert code for instruction level failures");
	System.out.println("-taskstate -- do task state analysis");
	System.out.println("-flatirtasks -- create dot files for flat IR graphs of tasks");
	System.out.println("-flatirusermethods -- create dot files for flat IR graphs of user methods");
	System.out.println("-flatirlibmethods -- create dot files for flat IR graphs of library class methods");
	System.out.println("  note: -flatirusermethods or -flatirlibmethods currently generate all class method flat IR graphs");
	System.out.println("-ownership -- do ownership analysis");
	System.out.println("-ownallocdepth <d> -- set allocation depth for ownership analysis");
	System.out.println("-ownwritedots <all/final> -- write ownership graphs; can be all results or just final results");
	System.out.println("-ownaliasfile <filename> -- write a text file showing all detected aliases in program tasks");
	System.out.println("-optional -- enable optional arguments");
	System.out.println("-webinterface -- enable web interface");
	System.out.println("-help -- print out help");
	System.exit(0);
      } else {
	readSourceFile(state, args[i]);
      }
    }


    readSourceFile(state, ClassLibraryPrefix+"System.java");
    readSourceFile(state, ClassLibraryPrefix+"String.java");
    readSourceFile(state, ClassLibraryPrefix+"HashSet.java");
    readSourceFile(state, ClassLibraryPrefix+"HashMap.java");
    readSourceFile(state, ClassLibraryPrefix+"HashMapIterator.java");
    readSourceFile(state, ClassLibraryPrefix+"HashEntry.java");
    readSourceFile(state, ClassLibraryPrefix+"Integer.java");
    readSourceFile(state, ClassLibraryPrefix+"StringBuffer.java");
    //if(!state.RAW) {
    readSourceFile(state, ClassLibraryPrefix+"FileInputStream.java");
    readSourceFile(state, ClassLibraryPrefix+"InputStream.java");
    readSourceFile(state, ClassLibraryPrefix+"OutputStream.java");
    readSourceFile(state, ClassLibraryPrefix+"FileOutputStream.java");
    readSourceFile(state, ClassLibraryPrefix+"File.java");
    readSourceFile(state, ClassLibraryPrefix+"InetAddress.java");
    readSourceFile(state, ClassLibraryPrefix+"SocketInputStream.java");
    readSourceFile(state, ClassLibraryPrefix+"SocketOutputStream.java");
    //}
    readSourceFile(state, ClassLibraryPrefix+"Math.java");
    readSourceFile(state, ClassLibraryPrefix+"gnu/Random.java");
    readSourceFile(state, ClassLibraryPrefix+"Vector.java");
    readSourceFile(state, ClassLibraryPrefix+"Enumeration.java");
    readSourceFile(state, ClassLibraryPrefix+"Dictionary.java");
    readSourceFile(state, ClassLibraryPrefix+"Writer.java");
    readSourceFile(state, ClassLibraryPrefix+"BufferedWriter.java");
    readSourceFile(state, ClassLibraryPrefix+"OutputStreamWriter.java");
    readSourceFile(state, ClassLibraryPrefix+"FileWriter.java");
    readSourceFile(state, ClassLibraryPrefix+"Date.java");

    if (state.TASK) {
      readSourceFile(state, ClassLibraryPrefix+"Object.java");
      readSourceFile(state, ClassLibraryPrefix+"TagDescriptor.java");
    } else if (state.DSM) {
      readSourceFile(state, ClassLibraryPrefix+"ThreadDSM.java");
      readSourceFile(state, ClassLibraryPrefix+"ObjectJavaDSM.java");
      readSourceFile(state, ClassLibraryPrefix+"Barrier.java");
    } else {
      if (state.THREAD) {
	readSourceFile(state, ClassLibraryPrefix+"Thread.java");
	readSourceFile(state, ClassLibraryPrefix+"ObjectJava.java");
      } else
	readSourceFile(state, ClassLibraryPrefix+"ObjectJavaNT.java");
    }

    if (state.TASK) {
      readSourceFile(state, ClassLibraryPrefix+"StartupObject.java");
      readSourceFile(state, ClassLibraryPrefix+"Socket.java");
      readSourceFile(state, ClassLibraryPrefix+"ServerSocket.java");
    } else {
      readSourceFile(state, ClassLibraryPrefix+"SocketJava.java");
      readSourceFile(state, ClassLibraryPrefix+"ServerSocketJava.java");
    }

    BuildIR bir=new BuildIR(state);
    bir.buildtree();

    TypeUtil tu=new TypeUtil(state);

    SemanticCheck sc=new SemanticCheck(state,tu);
    sc.semanticCheck();
    tu.createFullTable();

    BuildFlat bf=new BuildFlat(state,tu);
    bf.buildFlat();
    SafetyAnalysis sa=null;
    PrefetchAnalysis pa=null;

    if (state.TAGSTATE) {
      CallGraph callgraph=new CallGraph(state);
      TagAnalysis taganalysis=new TagAnalysis(state, callgraph);
      TaskTagAnalysis tta=new TaskTagAnalysis(state, taganalysis);
    }

    if (state.TASKSTATE) {
      CallGraph callgraph=new CallGraph(state);
      TagAnalysis taganalysis=new TagAnalysis(state, callgraph);
      TaskAnalysis ta=new TaskAnalysis(state, taganalysis);
      ta.taskAnalysis();
      TaskGraph tg=new TaskGraph(state, ta);
      tg.createDOTfiles();

      if (state.OPTIONAL) {
	ExecutionGraph et=new ExecutionGraph(state, ta);
	et.createExecutionGraph();
	sa = new SafetyAnalysis(et.getExecutionGraph(), state, ta);
	sa.doAnalysis();
	state.storeAnalysisResult(sa.getResult());
	state.storeOptionalTaskDescriptors(sa.getOptionalTaskDescriptors());
      }

      if (state.WEBINTERFACE) {
	GarbageAnalysis ga=new GarbageAnalysis(state, ta);
	WebInterface wi=new WebInterface(state, ta, tg, ga, taganalysis);
	JhttpServer serve=new JhttpServer(8000,wi);
	serve.run();
      }

      if (state.SCHEDULING) {
	// Save the current standard input, output, and error streams
	// for later restoration.
	PrintStream origOut = System.out;

	// Create a new output stream for the standard output.
	PrintStream stdout  = null;
	try {
	  stdout = new PrintStream(new FileOutputStream("SimulatorResult.out"));
	} catch (Exception e) {
	  // Sigh.  Couldn't open the file.
	  System.out.println("Redirect:  Unable to open output file!");
	  System.exit(1);
	}

	// Print stuff to the original output and error streams.
	// On most systems all of this will end up on your console when you
	// run this application.
	//origOut.println ("\nRedirect:  Round #1");
	//System.out.println ("Test output via 'System.out'.");
	//origOut.println ("Test output via 'origOut' reference.");

	// Set the System out and err streams to use our replacements.
	System.setOut(stdout);

	// Print stuff to the original output and error streams.
	// The stuff printed through the 'origOut' and 'origErr' references
	// should go to the console on most systems while the messages
	// printed through the 'System.out' and 'System.err' will end up in
	// the files we created for them.
	//origOut.println ("\nRedirect:  Round #2");
	//System.out.println ("Test output via 'SimulatorResult.out'.");
	//origOut.println ("Test output via 'origOut' reference.");

	// for test
	// Randomly set the newRate and probability of FEdges
	java.util.Random r=new java.util.Random();
	int tint = 0;
	for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext();) {
	  ClassDescriptor cd=(ClassDescriptor) it_classes.next();
	  if(cd.hasFlags()) {
	    Vector rootnodes=ta.getRootNodes(cd);
	    if(rootnodes!=null)
	      for(Iterator it_rootnodes=rootnodes.iterator(); it_rootnodes.hasNext();) {
		FlagState root=(FlagState)it_rootnodes.next();
		Vector allocatingTasks = root.getAllocatingTasks();
		if(allocatingTasks != null) {
		  for(int k = 0; k < allocatingTasks.size(); k++) {
		    TaskDescriptor td = (TaskDescriptor)allocatingTasks.elementAt(k);
		    Vector<FEdge> fev = (Vector<FEdge>)ta.getFEdgesFromTD(td);
		    int numEdges = fev.size();
		    int total = 100;
		    for(int j = 0; j < numEdges; j++) {
		      FEdge pfe = fev.elementAt(j);
		      if(numEdges - j == 1) {
			pfe.setProbability(total);
		      } else {
			if((total != 0) && (total != 1)) {
			  do {
			    tint = r.nextInt()%total;
			  } while(tint <= 0);
			}
			pfe.setProbability(tint);
			total -= tint;
		      }
		      /*do {
		          tint = r.nextInt()%10;
		         } while(tint <= 0);*/
		      //int newRate = tint;
		      //int newRate = (j+1)%2+1;
		      int newRate = 1;
		      String cdname = cd.getSymbol();
		      if((cdname.equals("SeriesRunner")) ||
		         (cdname.equals("MDRunner")) ||
		         (cdname.equals("Stage")) ||
		         (cdname.equals("AppDemoRunner")) ||
		         (cdname.equals("FilterBankAtom"))) {
			newRate = 16;
		      } else if(cdname.equals("SentenceParser")) {
			newRate = 4;
		      }
		      /*do {
		          tint = r.nextInt()%100;
		         } while(tint <= 0);
		         int probability = tint;*/
		      int probability = 100;
		      pfe.addNewObjInfo(cd, newRate, probability);
		    }
		  }
		}
	      }

	    Iterator it_flags = ta.getFlagStates(cd).iterator();
	    while(it_flags.hasNext()) {
	      FlagState fs = (FlagState)it_flags.next();
	      Iterator it_edges = fs.edges();
	      while(it_edges.hasNext()) {
		/*do {
		    tint = r.nextInt()%10;
		   } while(tint <= 0);*/
		tint = 3;
		((FEdge)it_edges.next()).setExeTime(tint);
	      }
	    }
	  }
	}

	// generate multiple schedulings
	ScheduleAnalysis scheduleAnalysis = new ScheduleAnalysis(state, ta);
	scheduleAnalysis.preSchedule();
	scheduleAnalysis.scheduleAnalysis();
	//scheduleAnalysis.setCoreNum(scheduleAnalysis.getSEdges4Test().size());
	scheduleAnalysis.setCoreNum(state.CORENUM);
	scheduleAnalysis.schedule();

	//simulate these schedulings
	ScheduleSimulator scheduleSimulator = new ScheduleSimulator(scheduleAnalysis.getCoreNum(), state, ta);
	Iterator it_scheduling = scheduleAnalysis.getSchedulingsIter();
	int index = 0;
	Vector<Integer> selectedScheduling = new Vector<Integer>();
	int processTime = Integer.MAX_VALUE;
	while(it_scheduling.hasNext()) {
	  Vector<Schedule> scheduling = (Vector<Schedule>)it_scheduling.next();
	  scheduleSimulator.setScheduling(scheduling);
	  int tmpTime = scheduleSimulator.process();
	  if(tmpTime < processTime) {
	    selectedScheduling.clear();
	    selectedScheduling.add(index);
	    processTime = tmpTime;
	  } else if(tmpTime == processTime) {
	    selectedScheduling.add(index);
	  }
	  index++;
	}
	System.out.print("Selected schedulings with least exectution time " + processTime + ": \n\t");
	for(int i = 0; i < selectedScheduling.size(); i++) {
	  System.out.print((selectedScheduling.elementAt(i) + 1) + ", ");
	}
	System.out.println();

	/*ScheduleSimulator scheduleSimulator = new ScheduleSimulator(4, state, ta);
	   Vector<Schedule> scheduling = new Vector<Schedule>();
	   for(int i = 0; i < 4; i++) {
	    Schedule schedule = new Schedule(i);
	    scheduling.add(schedule);
	   }
	   Iterator it_tasks = state.getTaskSymbolTable().getAllDescriptorsIterator();
	   while(it_tasks.hasNext()) {
	    TaskDescriptor td = (TaskDescriptor)it_tasks.next();
	    if(td.getSymbol().equals("t10")) {
	        scheduling.elementAt(1).addTask(td);
	    } else {
	        scheduling.elementAt(0).addTask(td);
	    }
	   }
	   ClassDescriptor cd = (ClassDescriptor)state.getClassSymbolTable().get("E");
	   scheduling.elementAt(0).addTargetCore(cd, 1);
	   scheduleSimulator.setScheduling(scheduling);
	   scheduleSimulator.process();

	   Vector<Schedule> scheduling1 = new Vector<Schedule>();
	   for(int i = 0; i < 4; i++) {
	    Schedule schedule = new Schedule(i);
	    scheduling1.add(schedule);
	   }
	   Iterator it_tasks1 = state.getTaskSymbolTable().getAllDescriptorsIterator();
	   while(it_tasks1.hasNext()) {
	    TaskDescriptor td = (TaskDescriptor)it_tasks1.next();
	    scheduling1.elementAt(0).addTask(td);
	   }
	   scheduleSimulator.setScheduling(scheduling1);
	   scheduleSimulator.process();*/

	// Close the streams.
	try {
	  stdout.close();
	  System.setOut(origOut);
	} catch (Exception e) {
	  origOut.println("Redirect:  Unable to close files!");
	}

	if(state.MULTICORE) {
	  //it_scheduling = scheduleAnalysis.getSchedulingsIter();
	  //Vector<Schedule> scheduling = (Vector<Schedule>)it_scheduling.next();
	  Vector<Schedule> scheduling = scheduleAnalysis.getSchedulings().elementAt(selectedScheduling.lastElement());
	  BuildCodeMultiCore bcm=new BuildCodeMultiCore(state, bf.getMap(), tu, sa, scheduling, scheduleAnalysis.getCoreNum(), pa);
	  bcm.buildCode();
	}
      }

    }

    if(!state.MULTICORE) {
      if (state.DSM) {
	CallGraph callgraph=new CallGraph(state);
	if (state.PREFETCH) {
	  //speed up prefetch generation using locality analysis results
	  LocalityAnalysis la=new LocalityAnalysis(state, callgraph, tu);
	  pa=new PrefetchAnalysis(state, callgraph, tu, la);
	}

	LocalityAnalysis la=new LocalityAnalysis(state, callgraph, tu);
	GenerateConversions gc=new GenerateConversions(la, state);
	BuildCode bc=new BuildCode(state, bf.getMap(), tu, la, pa);
	bc.buildCode();
      } else {
	BuildCode bc=new BuildCode(state, bf.getMap(), tu, sa, pa);
	bc.buildCode();
      }
    }

    if (state.FLATIRGRAPH) {
      FlatIRGraph firg = new FlatIRGraph(state,
                                         state.FLATIRGRAPHTASKS,
                                         state.FLATIRGRAPHUSERMETHODS,
                                         state.FLATIRGRAPHLIBMETHODS);
    }

    if (state.OWNERSHIP) {
      CallGraph callGraph = new CallGraph(state);
      OwnershipAnalysis oa = new OwnershipAnalysis(state,
                                                   tu,
                                                   callGraph,
                                                   state.OWNERSHIPALLOCDEPTH,
                                                   state.OWNERSHIPWRITEDOTS,
                                                   state.OWNERSHIPWRITEALL,
                                                   state.OWNERSHIPALIASFILE);
    }

    System.exit(0);
  }

  /** Reads in a source file and adds the parse tree to the state object. */

  private static void readSourceFile(State state, String sourcefile) throws Exception {
    Reader fr = new BufferedReader(new FileReader(sourcefile));
    Lex.Lexer l = new Lex.Lexer(fr);
    java_cup.runtime.lr_parser g;
    g = new Parse.Parser(l);
    ParseNode p=null;
    try {
      p=(ParseNode) g./*debug_*/ parse().value;
    } catch (Exception e) {
      System.err.println("Error parsing file:"+sourcefile);
      e.printStackTrace();
      System.exit(-1);
    }
    state.addParseNode(p);
    if (l.numErrors()!=0) {
      System.out.println("Error parsing "+sourcefile);
      System.exit(l.numErrors());
    }
  }
}
