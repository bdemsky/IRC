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
//import IR.PrintTree;
import Analysis.TaskStateAnalysis.TaskAnalysis;

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
	  else if (option.equals("-thread"))
	      state.THREAD=true;
	  else if (option.equals("-instructionfailures"))
	      state.INSTRUCTIONFAILURE=true;
	  else if (option.equals("-help")) {
	      System.out.println("-classlibrary classlibrarydirectory -- directory where classlibrary is located");
	      System.out.println("-dir outputdirectory -- output code in outputdirectory");
	      System.out.println("-struct structfile -- output structure declarations for repair tool");
	      System.out.println("-mainclass -- main function to call");
	      System.out.println("-precise -- use precise garbage collection");

	      System.out.println("-conscheck -- turn on consistency checking");
	      System.out.println("-task -- compiler for tasks");
	      System.out.println("-thread -- threads");
	      System.out.println("-instructionfailures -- insert code for instruction level failures");
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

      if (state.THREAD) {
	  readSourceFile(state, ClassLibraryPrefix+"Thread.java");
	  readSourceFile(state, ClassLibraryPrefix+"ObjectJava.java");
      } else
	  readSourceFile(state, ClassLibraryPrefix+"Object.java");

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
      
//      PrintTree ptree=new PrintTree(state);
 //     ptree.buildtree();

      TypeUtil tu=new TypeUtil(state);
      
      SemanticCheck sc=new SemanticCheck(state,tu);
      sc.semanticCheck();
      tu.createFullTable();

      BuildFlat bf=new BuildFlat(state,tu);
      bf.buildFlat();

//      System.out.println("Flat");
//    PrintTree ptree1=new PrintTree(state);
//  ptree1.buildtree();

	TaskAnalysis ta=new TaskAnalysis(state,bf.getMap());
	ta.taskAnalysis();
//	ta.printAdjList();



      BuildCode bc=new BuildCode(state, bf.getMap(), tu);
      bc.buildCode();
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
