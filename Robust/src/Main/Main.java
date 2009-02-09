package Main;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
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
import Util.GraphNode;
import Util.GraphNode.DFS;
import Util.GraphNode.SCC;

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
      else if (option.equals("-fastcheck"))
	state.FASTCHECK=true;
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
      else if (option.equals("-printscheduling"))
	State.PRINTSCHEDULING=true;
      else if (option.equals("-printschedulesim"))
	State.PRINTSCHEDULESIM=true;
      else if (option.equals("-struct"))
	state.structfile=args[++i];
      else if (option.equals("-conscheck"))
	state.CONSCHECK=true;
      else if (option.equals("-task"))
	state.TASK=true;
      else if (option.equals("-abortreaders"))
	state.ABORTREADERS=true;
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
      else if (option.equals("-useprofile"))
	state.USEPROFILE=true;
      else if (option.equals("-thread"))
	state.THREAD=true;
      else if (option.equals("-dsm"))
	state.DSM=true;
      else if (option.equals("-webinterface"))
	state.WEBINTERFACE=true;
      else if (option.equals("-instructionfailures"))
	state.INSTRUCTIONFAILURE=true;
      else if (option.equals("-abcclose"))
	state.ARRAYBOUNDARYCHECK=false;
      else if (option.equals("-help")) {
	System.out.println("-classlibrary classlibrarydirectory -- directory where classlibrary is located");
	System.out.println("-selfloop task -- this task doesn't self loop its parameters forever");
	System.out.println("-dir outputdirectory -- output code in outputdirectory");
	System.out.println("-struct structfile -- output structure declarations for repair tool");
	System.out.println("-mainclass -- main function to call");
	System.out.println("-dsm -- distributed shared memory support");
	System.out.println("-abortreaders -- abort readers");
	System.out.println("-precise -- use precise garbage collection");
	System.out.println("-conscheck -- turn on consistency checking");
	System.out.println("-task -- compiler for tasks");
	System.out.println("-fastcheck -- fastcheckpointing for Bristlecone");
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
	System.out.println("-abcclose close the array boundary check");
	System.out.println("-scheduling do task scheduling");
	System.out.println("-multicore generate multi-core version binary");
	System.out.println("-numcore set the number of cores (should be used together with -multicore), defaultly set as 1");
	System.out.println("-raw generate raw version binary (should be used together with -multicore)");
	System.out.println("-interrupt generate raw version binary with interruption (should be used togethere with -raw)");
	System.out.println("-rawconfig config raw simulator as 4xn (should be used together with -raw)");
	System.out.println("-rawpath print out execute path information for raw version (should be used together with -raw)");
	System.out.println("-useprofile use profiling data for scheduling (should be used together with -raw)");
	System.out.println("-threadsimulate generate multi-thread simulate version binary");
	System.out.println("-rawuseio use standard io to output profiling data (should be used together with -raw and -profile), it only works with single core version");
	System.out.println("-printscheduling -- print out scheduling graphs");
	System.out.println("-printschedulesim -- print out scheduling simulation result graphs");
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
      if (state.FASTCHECK)
	readSourceFile(state, ClassLibraryPrefix+"ObjectFC.java");
      else
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
	// Use ownership analysis to get alias information
	CallGraph callGraph = new CallGraph(state);
	OwnershipAnalysis oa = new OwnershipAnalysis(state,
	                                             tu,
	                                             callGraph,
	                                             state.OWNERSHIPALLOCDEPTH,
	                                             state.OWNERSHIPWRITEDOTS,
	                                             state.OWNERSHIPWRITEALL,
	                                             state.OWNERSHIPALIASFILE);

	// generate multiple schedulings
	ScheduleAnalysis scheduleAnalysis = new ScheduleAnalysis(state,
	                                                         ta);
	// necessary preparation such as read profile info etc.
	scheduleAnalysis.preparation();
	scheduleAnalysis.preSchedule();
	scheduleAnalysis.scheduleAnalysis();
	//scheduleAnalysis.setCoreNum(scheduleAnalysis.getSEdges4Test().size());
	scheduleAnalysis.setCoreNum(state.CORENUM);
	scheduleAnalysis.schedule();

	//simulate these schedulings
	ScheduleSimulator scheduleSimulator = new ScheduleSimulator(scheduleAnalysis.getCoreNum(),
	                                                            state,
	                                                            ta);
	Vector<Vector<Schedule>> schedulings = scheduleAnalysis.getSchedulings();
	Vector<Integer> selectedScheduling = scheduleSimulator.simulate(schedulings);

	if(state.MULTICORE) {
	  Vector<Schedule> scheduling = scheduleAnalysis.getSchedulings().elementAt(selectedScheduling.firstElement());
	  BuildCodeMultiCore bcm=new BuildCodeMultiCore(state,
	                                                bf.getMap(),
	                                                tu,
	                                                sa,
	                                                scheduling,
	                                                scheduleAnalysis.getCoreNum(),
	                                                pa);
	  bcm.setOwnershipAnalysis(oa);
	  bcm.buildCode();
	  scheduling = null;
	}
	schedulings = null;
	selectedScheduling = null;
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
