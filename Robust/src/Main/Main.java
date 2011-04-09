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
import IR.Flat.BuildCodeMGC;
import IR.Flat.BuildFlat;
import IR.Flat.BuildCode;
import IR.Flat.BuildCodeTran;
import IR.Flat.BuildOoOJavaCode;
import IR.Flat.Inliner;
import IR.ClassDescriptor;
import IR.State;
import IR.TaskDescriptor;
import IR.TypeUtil;
import Analysis.SSJava.SSJavaAnalysis;
import Analysis.Scheduling.MCImplSynthesis;
import Analysis.Scheduling.Schedule;
import Analysis.Scheduling.ScheduleAnalysis;
import Analysis.Scheduling.ScheduleSimulator;
import Analysis.TaskStateAnalysis.TaskAnalysis;
import Analysis.TaskStateAnalysis.TaskTagAnalysis;
import Analysis.TaskStateAnalysis.TaskGraph;
import Analysis.CallGraph.CallGraph;
import Analysis.CallGraph.JavaCallGraph;
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
import Analysis.Disjoint.DisjointAnalysis;
import Analysis.OoOJava.OoOJavaAnalysis;
import Analysis.Loops.*;
import Analysis.Liveness;
import Analysis.ArrayReferencees;
import Analysis.Pointer.Pointer;
import IR.MethodDescriptor;
import IR.Flat.FlatMethod;
import Interface.*;
import Util.GraphNode;
import Util.GraphNode.DFS;
import Util.GraphNode.SCC;

public class Main {

  /** Main method for the compiler.  */

  public static void main(String args[]) throws Exception {
    String ClassLibraryPrefix="./ClassLibrary/";
    State state=new State();
    Vector sourcefiles=new Vector();
    State.initTimer();
    state.classpath.add(".");

    String outputdir = null;
    boolean isDistributeInfo = false;
    boolean isDisAll = false;
    int startnum = 0;
    

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
	  else if (option.equals("-outputdir"))
	state.outputdir = args[++i];
      else if (option.equals("-excprefetch"))
	state.excprefetch.add(args[++i]);
      else if (option.equals("-classlibrary"))
	state.classpath.add(args[++i]);
      else if (option.equals("-inlineatomic")) {
	state.INLINEATOMIC=true;
	state.inlineatomicdepth=Integer.parseInt(args[++i]);
      } else if(option.equals("-numcore")) {
	++i;
	state.CORENUM = Integer.parseInt(args[i]);
      } else if(option.equals("-numcore4gc")) {
        ++i;
        state.CORENUM4GC = Integer.parseInt(args[i]);
      } else if (option.equals("-mainclass"))
	state.main=args[++i];
      else if (option.equals("-trueprob")) {
	state.TRUEPROB=Double.parseDouble(args[++i]);
      } else if (option.equals("-printflat"))
	State.PRINTFLAT=true;
      else if (option.equals("-printscheduling"))
	State.PRINTSCHEDULING=true;
      else if (option.equals("-minimize"))
	state.MINIMIZE=true;
      else if (option.equals("-printschedulesim"))
	State.PRINTSCHEDULESIM=true;
      else if (option.equals("-printcriticalpath"))
	  State.PRINTCRITICALPATH=true;
      else if (option.equals("-struct"))
	state.structfile=args[++i];
      else if (option.equals("-conscheck"))
	state.CONSCHECK=true;
      else if (option.equals("-task"))
	state.TASK=true;
      else if (option.equals("-abortreaders"))
	state.ABORTREADERS=true;
      else if (option.equals("-sandbox"))
	state.SANDBOX=true;
      else if (option.equals("-taskstate"))
	state.TASKSTATE=true;
      else if (option.equals("-tagstate"))
	state.TAGSTATE=true;
      else if (option.equals("-stmarray"))
	state.STMARRAY=true;
      else if (option.equals("-eventmonitor"))
	state.EVENTMONITOR=true;
      else if (option.equals("-dualview"))
	state.DUALVIEW=true;
      else if (option.equals("-hybrid"))
	state.HYBRID=true;
      else if (option.equals("-flatirtasks")) {
	state.FLATIRGRAPH=true;
	state.FLATIRGRAPHTASKS=true;
      } else if (option.equals("-flatirusermethods")) {
	state.FLATIRGRAPH=true;
	state.FLATIRGRAPHUSERMETHODS=true;
      } else if (option.equals("-flatirlibmethods")) {
	state.FLATIRGRAPH=true;
	state.FLATIRGRAPHLIBMETHODS=true;
      } else if (option.equals("-bamboocompiletime")) {
        state.BAMBOOCOMPILETIME = true;
      } else if (option.equals("-multicore"))
	state.MULTICORE=true;
      else if (option.equals("-multicoregc"))
        state.MULTICOREGC=true;
      else if (option.equals("-mgc")) {
        state.MGC = true;
      } else if (option.equals("-objectlockdebug")) {
        state.OBJECTLOCKDEBUG = true;
      } else if (option.equals("-ownership"))
	state.OWNERSHIP=true;
      else if (option.equals("-ownallocdepth")) {
	state.OWNERSHIPALLOCDEPTH=Integer.parseInt(args[++i]);
      } else if (option.equals("-ownwritedots")) {
	state.OWNERSHIPWRITEDOTS=true;
	if (args[++i].equals("all")) {
	  state.OWNERSHIPWRITEALL=true;
	}
      } else if (option.equals("-ownaliasfile")) {
	state.OWNERSHIPALIASFILE=args[++i];
      } else if (option.equals("-ownaliasfiletab")) {
	state.OWNERSHIPALIASFILE=args[++i];
        state.OWNERSHIPALIASTAB=true;      
      } else if (option.equals("-owndebugcallee")) {
	state.OWNERSHIPDEBUGCALLEE=args[++i];
      } else if (option.equals("-owndebugcaller")) {
	state.OWNERSHIPDEBUGCALLER=args[++i];
      } else if (option.equals("-owndebugcallcount")) {
	state.OWNERSHIPDEBUGCALLCOUNT=Integer.parseInt(args[++i]);
      } else if (option.equals("-pointer")) {
	state.POINTER=true;
      } else if (option.equals("-disjoint"))
	state.DISJOINT=true;
      else if (option.equals("-disjoint-k")) {
	state.DISJOINTALLOCDEPTH=Integer.parseInt(args[++i]);

      } else if (option.equals("-disjoint-write-dots")) {
	state.DISJOINTWRITEDOTS = true;
        String arg = args[++i];
	if( arg.equals("all") ) {
	  state.DISJOINTWRITEALL = true;
	} else if( arg.equals("final") ) {
          state.DISJOINTWRITEALL = false;
        } else {
          throw new Error("disjoint-write-dots requires argument <all/final>");
        }

      } else if (option.equals("-disjoint-write-initial-contexts")) {
	state.DISJOINTWRITEINITCONTEXTS = true;

      } else if (option.equals("-disjoint-write-ihms")) {
	state.DISJOINTWRITEIHMS = true;

      } else if (option.equals("-disjoint-alias-file")) {
	state.DISJOINTALIASFILE = args[++i];
        String arg = args[++i];
	if( arg.equals("normal") ) {
	  state.DISJOINTALIASTAB = false;
	} else if( arg.equals("tabbed") ) {
          state.DISJOINTALIASTAB = true;
        } else {
          throw new Error("disjoint-alias-file requires arguments: <filename> <normal/tabbed>");
        }

      } else if (option.equals("-disjoint-debug-callsite")) {
	state.DISJOINTDEBUGCALLEE=args[++i];
	state.DISJOINTDEBUGCALLER=args[++i];
	state.DISJOINTDEBUGCALLVISITTOSTART=Integer.parseInt(args[++i]);
	state.DISJOINTDEBUGCALLNUMVISITS=Integer.parseInt(args[++i]);
        String arg = args[++i];
	if( arg.equals("true") ) {
	  state.DISJOINTDEBUGCALLSTOPAFTER = true;
	} else if( arg.equals("false") ) {
          state.DISJOINTDEBUGCALLSTOPAFTER = false;
        } else {
          throw new Error("disjoint-debug-callsite requires arguments:\n"+
                          "  <callee symbol> <caller symbol> <# visit to start> <# visits to capture> <T/F stop after>");
        }
      
      } else if (option.equals("-disjoint-debug-snap-method")) {
	state.DISJOINTSNAPSYMBOL=args[++i];
        state.DISJOINTSNAPVISITTOSTART=Integer.parseInt(args[++i]);
	state.DISJOINTSNAPNUMVISITS=Integer.parseInt(args[++i]);
        String arg = args[++i];
	if( arg.equals("true") ) {
	  state.DISJOINTSNAPSTOPAFTER = true;
	} else if( arg.equals("false") ) {
          state.DISJOINTSNAPSTOPAFTER = false;
        } else {
          throw new Error("disjoint-debug-snap-method requires arguments:\n"+
                          "  <method symbol> <# visit to start> <# visits to snap> <T/F stop after>");
        }

      } else if( option.equals( "-disjoint-release-mode" ) ) {
        state.DISJOINTRELEASEMODE = true;        

      } else if( option.equals( "-disjoint-dvisit-stack" ) ) {
        state.DISJOINTDVISITSTACK         = true;      
        state.DISJOINTDVISITPQUE          = false;
        state.DISJOINTDVISITSTACKEESONTOP = false;

      } else if( option.equals( "-disjoint-dvisit-pqueue" ) ) {
        state.DISJOINTDVISITPQUE          = true;
        state.DISJOINTDVISITSTACK         = false;
        state.DISJOINTDVISITSTACKEESONTOP = false;

      } else if( option.equals( "-disjoint-dvisit-stack-callees-on-top" ) ) {
        state.DISJOINTDVISITSTACKEESONTOP = true;
        state.DISJOINTDVISITPQUE          = false;
        state.DISJOINTDVISITSTACK         = false;      

      } else if( option.equals( "-disjoint-desire-determinism" ) ) {
        state.DISJOINTDETERMINISM = true;

        // when asking analysis for a deterministic result, force
        // a stack-based visiting scheme, because the priority queue
        // requires a non-deterministic topological sort
        state.DISJOINTDVISITSTACKEESONTOP = true;
        state.DISJOINTDVISITPQUE          = false;
        state.DISJOINTDVISITSTACK         = false;


      } else if( option.equals( "-disjoint-debug-scheduling" ) ) {
        state.DISJOINTDEBUGSCHEDULING = true;
      }
      

      else if (option.equals("-optional"))
	state.OPTIONAL=true;
      else if (option.equals("-optimize"))
	state.OPTIMIZE=true;
      else if (option.equals("-noloop"))
	state.NOLOOP=true;
      else if (option.equals("-dcopts"))
	state.DCOPTS=true;
      else if (option.equals("-arraypad"))
	state.ARRAYPAD=true;
      else if (option.equals("-delaycomp"))
	state.DELAYCOMP=true;
      else if (option.equals("-raw"))
	state.RAW=true;
      else if (option.equals("-scheduling"))
	state.SCHEDULING=true;
      else if (option.equals("-distributioninfo"))
	isDistributeInfo=true;
      else if (option.equals("-disall"))
        isDisAll=true;
      else if (option.equals("-disstart"))
        startnum = Integer.parseInt(args[++i]);
      else if (option.equals("-useprofile")) {
	state.USEPROFILE=true;
	state.profilename = args[++i];
      }
      else if (option.equals("-thread"))
	state.THREAD=true;
      else if (option.equals("-dsm"))
	state.DSM=true;
      else if (option.equals("-recoverystats"))
	state.DSMRECOVERYSTATS=true;
      else if (option.equals("-dsmtask"))
	state.DSMTASK=true;
      else if (option.equals("-singleTM"))
	state.SINGLETM=true;
      else if (option.equals("-readset"))
	state.READSET=true;
      else if (option.equals("-webinterface"))
	state.WEBINTERFACE=true;
      else if (option.equals("-instructionfailures"))
	state.INSTRUCTIONFAILURE=true;
      else if (option.equals("-abcclose"))
	state.ARRAYBOUNDARYCHECK=false;

      else if (option.equals("-methodeffects")) {
	state.METHODEFFECTS=true;
	
      } else if (option.equals("-coreprof")) {
	state.COREPROF=true;

      } else if (option.equals("-ooojava")) {
	state.OOOJAVA  = true;
	state.DISJOINT = true;
	state.OOO_NUMCORES   = Integer.parseInt( args[++i] );
	state.OOO_MAXSESEAGE = Integer.parseInt( args[++i] );

      } else if (option.equals("-ooodebug") ){ 
	state.OOODEBUG  = true;
      } else if (option.equals("-rcr")){      
	state.RCR = true;
	state.KEEP_RG_FOR_ALL_PROGRAM_POINTS=true;
      } else if (option.equals("-rcr_debug")){
	state.RCR_DEBUG = true;
	state.KEEP_RG_FOR_ALL_PROGRAM_POINTS=true;
      } else if (option.equals("-rcr_debug_verbose")){
	state.RCR_DEBUG_VERBOSE = true;
	state.KEEP_RG_FOR_ALL_PROGRAM_POINTS=true;
      } else if (option.equals("-nostalltr")){
	state.NOSTALLTR = true;     
      } else if (option.equals("-ssjava")){
	state.SSJAVA = true;     
      } else if (option.equals("-printlinenum")){
	state.LINENUM=true;
      }else if (option.equals("-help")) {      
	System.out.println("-classlibrary classlibrarydirectory -- directory where classlibrary is located");
	System.out.println("-selfloop task -- this task doesn't self loop its parameters forever");
	System.out.println("-dir outputdirectory -- output code in outputdirectory");
	System.out.println("-struct structfile -- output structure declarations for repair tool");
	System.out.println("-mainclass -- main function to call");
	System.out.println("-dsm -- distributed shared memory support");
	System.out.println("-singleTM -- single machine committing transactions");
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
	System.out.println("-optimize -- enable optimizations");
	System.out.println("-noloop -- disable loop optimizations");
	System.out.println("-optional -- enable optional arguments");
	System.out.println("-abcclose close the array boundary check");
	System.out.println("-scheduling do task scheduling");
	System.out.println("-mlp <num cores> <max sese age> build mlp code");
	System.out.println("-mlpdebug if mlp, report progress and interim results");
	System.out.println("-multicore generate multi-core version binary");
	System.out.println("-numcore set the number of cores (should be used together with -multicore), defaultly set as 1");
	System.out.println("-interrupt generate raw version binary with interruption (should be used togethere with -raw)");
	System.out.println("-rawconfig config raw simulator as 4xn (should be used together with -raw)");
	System.out.println("-rawpath print out execute path information for raw version (should be used together with -raw)");
	System.out.println("-useprofile use profiling data for scheduling (should be used together with -raw)");
	System.out.println("-threadsimulate generate multi-thread simulate version binary");
	System.out.println("-rawuseio use standard io to output profiling data (should be used together with -raw and -profile), it only works with single core version");
	System.out.println("-printscheduling -- print out scheduling graphs");
	System.out.println("-printschedulesim -- print out scheduling simulation result graphs");
	System.out.println("-webinterface -- enable web interface");
	System.out.println("-linenum print out line numbers in generated C codes");
	System.out.println("-help -- print out help");
	System.exit(0);
      } else {
	sourcefiles.add(args[i]);
      }
    }
    
    //add default classpath
    if (state.classpath.size()==1)
      state.classpath.add(ClassLibraryPrefix);

    State.logEvent("Done Parsing Commands");

    SSJavaAnalysis ssjava=new SSJavaAnalysis(state);
    BuildIR bir=new BuildIR(state);
    TypeUtil tu=new TypeUtil(state, bir);

    SemanticCheck sc=new SemanticCheck(state,tu);

    for(int i=0;i<sourcefiles.size();i++)
      loadClass(state, bir,(String)sourcefiles.get(i));

    //Stuff the runtime wants to see
    sc.getClass("String");
    sc.getClass("Math");
    sc.getClass("File");
    sc.getClass("Socket");
    sc.getClass("ServerSocket");
    sc.getClass("FileInputStream");
    sc.getClass("FileOutputStream");
    if (state.TASK) {
      sc.getClass("TagDescriptor");
    }
    if (state.THREAD||state.DSM||state.SINGLETM||state.MGC) {
      sc.getClass("Thread");
    }


    sc.semanticCheck();
    State.logEvent("Done Semantic Checking");

    tu.createFullTable();
    State.logEvent("Done Creating TypeUtil");

    // SSJava
    if(state.SSJAVA){
      ssjava.doCheck();
    }
    


    BuildFlat bf=new BuildFlat(state,tu);
    bf.buildFlat();
    State.logEvent("Done Building Flat");

    SafetyAnalysis sa=null;
    PrefetchAnalysis pa=null;
    OoOJavaAnalysis  oooa=null;
    if (state.INLINEATOMIC) {
      Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
      while(classit.hasNext()) {
        ClassDescriptor cn=(ClassDescriptor)classit.next();
        Iterator methodit=cn.getMethods();
        while(methodit.hasNext()) {
	  // do inlining
          MethodDescriptor md=(MethodDescriptor)methodit.next();
          FlatMethod fm=state.getMethodFlat(md);
	  Inliner.inlineAtomic(state, tu, fm, state.inlineatomicdepth);
	}
      }
    }

    CallGraph callgraph=state.TASK?new CallGraph(state, tu):new JavaCallGraph(state, tu);

    if (state.OPTIMIZE) {
      CopyPropagation cp=new CopyPropagation();
      DeadCode dc=new DeadCode();
      GlobalFieldType gft=new GlobalFieldType(callgraph, state, tu.getMain());
      CSE cse=new CSE(gft, tu);
      localCSE lcse=new localCSE(gft, tu);
      LoopOptimize lo=null;
      if (!state.NOLOOP)
	  lo=new LoopOptimize(gft, tu);
      Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
      while(classit.hasNext()) {
        ClassDescriptor cn=(ClassDescriptor)classit.next();
        Iterator methodit=cn.getMethods();
        while(methodit.hasNext()) {
          /* Classify parameters */
          MethodDescriptor md=(MethodDescriptor)methodit.next();
          FlatMethod fm=state.getMethodFlat(md);
	  cp.optimize(fm);
	  dc.optimize(fm);
	  if (!state.NOLOOP)
	      lo.optimize(fm);
	  cp.optimize(fm);
	  dc.optimize(fm);
	  lcse.doAnalysis(fm);
	  cse.doAnalysis(fm);
	  cp.optimize(fm);
	  dc.optimize(fm);
	  cp.optimize(fm);
	  dc.optimize(fm);
        }
      }
      State.logEvent("Done Optimizing");
    }

    if (state.FLATIRGRAPH) {
      FlatIRGraph firg = new FlatIRGraph(state,
                                         state.FLATIRGRAPHTASKS,
                                         state.FLATIRGRAPHUSERMETHODS,
                                         state.FLATIRGRAPHLIBMETHODS);
    }
    
    if (state.OWNERSHIP) {
      Liveness liveness = new Liveness();
      ArrayReferencees ar = new ArrayReferencees(state);
      OwnershipAnalysis oa = new OwnershipAnalysis(state,
                                                   tu,
                                                   callgraph,
						   liveness,
                                                   ar,
                                                   state.OWNERSHIPALLOCDEPTH,
                                                   state.OWNERSHIPWRITEDOTS,
                                                   state.OWNERSHIPWRITEALL,
                                                   state.OWNERSHIPALIASFILE,
                                                   state.METHODEFFECTS);
    }

    if (state.DISJOINT && !state.OOOJAVA) {
      Liveness         l  = new Liveness();
      ArrayReferencees ar = new ArrayReferencees(state);
      DisjointAnalysis da = new DisjointAnalysis(state, tu, callgraph, l, ar, null, null);
    }

    if (state.OOOJAVA) {
      Liveness         l   = new Liveness();
      ArrayReferencees ar  = new ArrayReferencees(state);
      oooa = new OoOJavaAnalysis(state, tu, callgraph, l, ar);
    }


    if (state.TAGSTATE) {
      TagAnalysis taganalysis=new TagAnalysis(state, callgraph);
      TaskTagAnalysis tta=new TaskTagAnalysis(state, taganalysis, tu);
    }

    if (state.TASKSTATE) {
      TagAnalysis taganalysis=new TagAnalysis(state, callgraph);
      TaskAnalysis ta=new TaskAnalysis(state, taganalysis, tu);
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
	Liveness liveness = new Liveness();
        ArrayReferencees ar = new ArrayReferencees(state);
	OwnershipAnalysis oa = null;/*new OwnershipAnalysis(state,
	                                             tu,
	                                             callGraph,
                                                 liveness,
                                                 ar,
	                                             state.OWNERSHIPALLOCDEPTH,
	                                             state.OWNERSHIPWRITEDOTS,
	                                             state.OWNERSHIPWRITEALL,
	                                             state.OWNERSHIPALIASFILE);*/
	
	// synthesis a layout according to target multicore processor
	MCImplSynthesis mcImplSynthesis = new MCImplSynthesis(state,
		                                              ta,
		                                              oa);
	if(isDistributeInfo) {
	    mcImplSynthesis.distribution(isDisAll, startnum);
	} else {
	    double timeStartAnalysis = (double) System.nanoTime();
	    mcImplSynthesis.setScheduleThreshold(20);
	    mcImplSynthesis.setProbThreshold(0);
	    mcImplSynthesis.setGenerateThreshold(30);
	    Vector<Schedule> scheduling = mcImplSynthesis.synthesis();
	    
	    double timeEndAnalysis = (double) System.nanoTime();
        if(state.BAMBOOCOMPILETIME) {
          double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
          System.err.println("The analysis took" + dt +  "sec.");
          System.exit(0);
        }

	// generate multicore codes
	if(state.MULTICORE) {
	  BuildCodeMultiCore bcm=new BuildCodeMultiCore(state,
							bf.getMap(),
							tu,
							sa,
							scheduling,
							mcImplSynthesis.getCoreNum(),
							state.CORENUM4GC, callgraph);
	  bcm.setOwnershipAnalysis(oa);
	  bcm.buildCode();
	}
	scheduling.clear();
	scheduling = null;
	}
      }
    }
    
    if (state.MGC) {
      // generate multicore codes
      if(state.MULTICORE) {
        BuildCodeMGC bcmgc=new BuildCodeMGC(state,
                                            bf.getMap(),
                                            tu,
                                            sa,
                                            state.CORENUM,
                                            state.CORENUM,
                                            state.CORENUM4GC, callgraph);
        bcmgc.buildCode();
      }
    }
  
    if(!state.MULTICORE) {
      BuildCode bc;

      if (state.DSM||state.SINGLETM) {
	if (state.PREFETCH) {
	  //speed up prefetch generation using locality analysis results
	  LocalityAnalysis la=new LocalityAnalysis(state, callgraph, tu);
	  pa=new PrefetchAnalysis(state, callgraph, tu, la);
	}
	LocalityAnalysis la=new LocalityAnalysis(state, callgraph, tu);
	GenerateConversions gc=new GenerateConversions(la, state);
	bc=new BuildCodeTran(state, bf.getMap(), tu, la, pa, callgraph);
      } else {
        if( state.OOOJAVA ) {
          bc=new BuildOoOJavaCode(state, bf.getMap(), tu, sa, oooa, callgraph);
        } else {
          bc=new BuildCode(state, bf.getMap(), tu, sa, callgraph);
        }
      }

      bc.buildCode();
      State.logEvent("Done With BuildCode");
	
    }

    System.out.println("Lines="+state.lines);
    System.exit(0);
  }

  public static void loadClass(State state, BuildIR bir, String sourcefile) {
    try {
      ParseNode pn=readSourceFile(state, sourcefile);
      bir.buildtree(pn, null,sourcefile);
    } catch (Exception e) {
      System.out.println("Error in sourcefile:"+sourcefile);
      e.printStackTrace();
      System.exit(-1);
    } catch (Error e) {
      System.out.println("Error in sourcefile:"+sourcefile);
      e.printStackTrace();
      System.exit(-1);
    }
  }

  /** Reads in a source file and adds the parse tree to the state object. */

  public static ParseNode readSourceFile(State state, String sourcefile) {
    try {
      Reader fr= new BufferedReader(new FileReader(sourcefile));
      Lex.Lexer l = new Lex.Lexer(fr);
      java_cup.runtime.lr_parser g;
      g = new Parse.Parser(l);
      ParseNode p=null;
      try {
	p=(ParseNode) g./*debug_*/parse().value;
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
      state.lines+=l.line_num;
      return p;

    } catch (Exception e) {
      throw new Error(e);
    }
  }
}
