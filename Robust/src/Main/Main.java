package Main;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import IR.Tree.ParseNode;
import IR.Tree.BuildIR;
import IR.Tree.SemanticCheck;
import IR.Flat.BuildFlat;
import IR.Flat.BuildCode;
import IR.State;
import IR.TypeUtil;
import Analysis.TaskStateAnalysis.TaskAnalysis;
import Analysis.TaskStateAnalysis.TaskGraph;
import Analysis.CallGraph.CallGraph;
import Analysis.TaskStateAnalysis.TagAnalysis;
import Analysis.TaskStateAnalysis.GarbageAnalysis;
import Analysis.TaskStateAnalysis.ExecutionGraph;
import Analysis.TaskStateAnalysis.SafetyAnalysis;
import Analysis.Locality.LocalityAnalysis;
import Analysis.Locality.GenerateConversions;
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
	  else if (option.equals("-dir"))
	      IR.Flat.BuildCode.PREFIX=args[++i]+"/";
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
	  else if (option.equals("-optional"))
	      state.OPTIONAL=true;
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
      readSourceFile(state, ClassLibraryPrefix+"FileOutputStream.java");
      readSourceFile(state, ClassLibraryPrefix+"File.java");
      readSourceFile(state, ClassLibraryPrefix+"InetAddress.java");



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
	      SafetyAnalysis sa = new SafetyAnalysis(et.getExecutionGraph(), state, ta);
	      sa.buildPath();
	      state.storeAnalysisResult(sa.getResult());
	      state.storeOptionalTaskDescriptors(sa.getOptionalTaskDescriptors());
	  }
	  
	  if (state.WEBINTERFACE) {
	      GarbageAnalysis ga=new GarbageAnalysis(state, ta);
	      WebInterface wi=new WebInterface(state, ta, tg, ga, taganalysis);
	      JhttpServer serve=new JhttpServer(8000,wi);
	      serve.run();
	  }
	  
	  
      }

      if (state.DSM) {
	  CallGraph callgraph=new CallGraph(state);
	  LocalityAnalysis la=new LocalityAnalysis(state, callgraph, tu);
	  GenerateConversions gc=new GenerateConversions(la, state);
	  BuildCode bc=new BuildCode(state, bf.getMap(), tu, la);
	  bc.buildCode();
      } else {
	  BuildCode bc=new BuildCode(state, bf.getMap(), tu);
	  bc.buildCode();
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
