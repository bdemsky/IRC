package Main;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;

import IR.Tree.ParseNode;
import IR.Tree.BuildIR;
import IR.Tree.SemanticCheck;
import IR.Flat.BuildFlat;
import IR.Flat.BuildCode;
import IR.State;
import IR.TypeUtil;
import Analysis.Scheduling.ScheduleAnalysis;
import Analysis.Scheduling.ScheduleEdge;
import Analysis.TaskStateAnalysis.TaskAnalysis;
import Analysis.TaskStateAnalysis.TaskTagAnalysis;
import Analysis.TaskStateAnalysis.TaskGraph;
import Analysis.CallGraph.CallGraph;
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

      for(int i=0;i<args.length;i++) {
	  String option=args[i];
	  if (option.equals("-precise"))
	      IR.Flat.BuildCode.GENERATEPRECISEGC=true;
	  else if (option.equals("-prefetch"))
	      state.PREFETCH=true;
	  else if (option.equals("-dir"))
	      IR.Flat.BuildCode.PREFIX=args[++i]+"/";
	  else if (option.equals("-selfloop"))
	      state.selfloops.add(args[++i]);
	  else if (option.equals("-classlibrary"))
	      ClassLibraryPrefix=args[++i]+"/";
	  else if (option.equals("-mainclass"))
	      state.main=args[++i];
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
	  }
	  else if (option.equals("-flatirusermethods")) {
	      state.FLATIRGRAPH=true;
	      state.FLATIRGRAPHUSERMETHODS=true;
	  }
	  else if (option.equals("-flatirlibmethods")) {
	      state.FLATIRGRAPH=true;
	      state.FLATIRGRAPHLIBMETHODS=true;
	  }
	  else if (option.equals("-ownership"))
	      state.OWNERSHIP=true;
	  else if (option.equals("-optional"))
	      state.OPTIONAL=true;
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
	      System.out.println("-instructionfailures -- insert code for instruction level failures");
	      System.out.println("-taskstate -- do task state analysis");
	      System.out.println("-flatirtasks -- create dot files for flat IR graphs of tasks");
	      System.out.println("-flatirusermethods -- create dot files for flat IR graphs of user methods");
	      System.out.println("-flatirlibmethods -- create dot files for flat IR graphs of library class methods");
	      System.out.println("  note: -flatirusermethods or -flatirlibmethods currently generate all class method flat IR graphs");
	      System.out.println("-ownership -- do ownership analysis");
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
      readSourceFile(state, ClassLibraryPrefix+"FileInputStream.java");
      readSourceFile(state, ClassLibraryPrefix+"InputStream.java");
      readSourceFile(state, ClassLibraryPrefix+"OutputStream.java");
      readSourceFile(state, ClassLibraryPrefix+"FileOutputStream.java");
      readSourceFile(state, ClassLibraryPrefix+"File.java");
      readSourceFile(state, ClassLibraryPrefix+"Math.java");
      readSourceFile(state, ClassLibraryPrefix+"InetAddress.java");
      readSourceFile(state, ClassLibraryPrefix+"SocketInputStream.java");
      readSourceFile(state, ClassLibraryPrefix+"SocketOutputStream.java");


      if (state.TASK) {
	  readSourceFile(state, ClassLibraryPrefix+"Object.java");
	  readSourceFile(state, ClassLibraryPrefix+"TagDescriptor.java");
      } else if (state.DSM) {
	  readSourceFile(state, ClassLibraryPrefix+"ThreadDSM.java");
	  readSourceFile(state, ClassLibraryPrefix+"ObjectJavaDSM.java");
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
		  ScheduleAnalysis scheduleAnalysis = new ScheduleAnalysis(state, ta);
		  scheduleAnalysis.preSchedule();
		  
		  // Randomly set the newRate and probability of ScheduleEdges
		  /*Vector<ScheduleEdge> sedges = scheduleAnalysis.getSEdges4Test();
		  java.util.Random r=new java.util.Random();
		  for(int i = 0; i < sedges.size(); i++) {
			  ScheduleEdge temp = sedges.elementAt(i);
			  int tint = 0;
			  do {
				  tint = r.nextInt()%100;
			  }while(tint <= 0);
			  temp.setProbability(tint);
			  do {
				  tint = r.nextInt()%10;
			  } while(tint <= 0);
			  temp.setNewRate(tint);
			  //temp.setNewRate((i+1)%2+1);
		  }
		  //sedges.elementAt(3).setNewRate(2);*/
		  scheduleAnalysis.printScheduleGraph("scheduling_ori.dot");
		  scheduleAnalysis.scheduleAnalysis();
		  scheduleAnalysis.printScheduleGraph("scheduling.dot");
	  }
	  
      }

      if (state.DSM) {
	  CallGraph callgraph=new CallGraph(state);
	  if (state.PREFETCH) {
	      PrefetchAnalysis pa=new PrefetchAnalysis(state, callgraph, tu);
	  }
	  LocalityAnalysis la=new LocalityAnalysis(state, callgraph, tu);
	  GenerateConversions gc=new GenerateConversions(la, state);
	  BuildCode bc=new BuildCode(state, bf.getMap(), tu, la);
	  bc.buildCode();
      } else {
	  BuildCode bc=new BuildCode(state, bf.getMap(), tu, sa);
	  bc.buildCode();
      }

      if (state.FLATIRGRAPH) {
	  FlatIRGraph firg = new FlatIRGraph(state,
					     state.FLATIRGRAPHTASKS,
					     state.FLATIRGRAPHUSERMETHODS,
					     state.FLATIRGRAPHLIBMETHODS);
      }

      if (state.OWNERSHIP) {
	  //	  OwnershipAnalysis oa = new OwnershipAnalysis(state);
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
    }
}
