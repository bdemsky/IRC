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
      if (args.length<1) {
	  System.out.println("Must input source file");
	  System.exit(-1);
      }
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
	  else if (option.equals("-help")) {
	      System.out.println("-classlibrary classlibrarydirectory -- directory where classlibrary is located");
	      System.out.println("-dir outputdirectory -- output code in outputdirectory");
	      System.out.println("-mainclass -- main function to call");
	      System.out.println("-precise -- use precise garbage collection");

	      System.out.println("-help -- print out help");
	      System.exit(0);
	  } else {
	      readSourceFile(state, args[i]);
	  }
      }
      
      readSourceFile(state, ClassLibraryPrefix+"Object.java");

      BuildIR bir=new BuildIR(state);
      bir.buildtree();
      
      TypeUtil tu=new TypeUtil(state);
      
      SemanticCheck sc=new SemanticCheck(state,tu);
      sc.semanticCheck();
      
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
