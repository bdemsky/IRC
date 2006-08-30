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

public class Main {

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
	  else if (option.equals("-task"))
	      state.TASK=true;
	  else if (option.equals("-help")) {
	      System.out.println("-classlibrary classlibrarydirectory -- directory where classlibrary is located");
	      System.out.println("-dir outputdirectory -- output code in outputdirectory");
	      System.out.println("-struct structfile -- output structure declarations for repair tool");
	      System.out.println("-mainclass -- main function to call");
	      System.out.println("-precise -- use precise garbage collection");

	      System.out.println("-help -- print out help");
	      System.exit(0);
	  } else {
	      readSourceFile(state, args[i]);
	  }
      }
      
      readSourceFile(state, ClassLibraryPrefix+"Object.java");
      readSourceFile(state, ClassLibraryPrefix+"System.java");
      readSourceFile(state, ClassLibraryPrefix+"String.java");
      if (state.TASK)      
	  readSourceFile(state, ClassLibraryPrefix+"StartupObject.java");

      BuildIR bir=new BuildIR(state);
      bir.buildtree();
      
      TypeUtil tu=new TypeUtil(state);
      
      SemanticCheck sc=new SemanticCheck(state,tu);
      sc.semanticCheck();
      tu.createFullTable();

      BuildFlat bf=new BuildFlat(state);
      bf.buildFlat();
      
      BuildCode bc=new BuildCode(state, bf.getMap(), tu);
      bc.buildCode();
      System.exit(0);
  }
    
    private static void readSourceFile(State state, String sourcefile) throws Exception {
	Reader fr = new BufferedReader(new FileReader(sourcefile));
	Lex.Lexer l = new Lex.Lexer(fr);
	java_cup.runtime.lr_parser g;
	g = new Parse.Parser(l);
	ParseNode p=(ParseNode) g./*debug_*/parse().value;
	state.addParseNode(p);
	if (l.numErrors()!=0) {
	    System.out.println("Error parsing Object.java");
	    System.exit(l.numErrors());
	}
    }
}
