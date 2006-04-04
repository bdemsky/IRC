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
      if (args.length<1) {
	  System.out.println("Must input source file");
	  System.exit(-1);
      }
      for(int i=1;i<args.length;i++) {
	  String option=args[i];
	  if (option.equals("-precise"))
	      IR.Flat.BuildCode.GENERATEPRECISEGC=true;
	  else if (option.equals("-dir"))
	      IR.Flat.BuildCode.PREFIX=args[++i]+"/";
	  else if (option.equals("-help")) {
	      System.out.println("-dir outputdirectory -- output code in outputdirectory");
	      System.out.println("-precise -- use precise garbage collection");
	      System.out.println("-help -- print out help");
	      System.exit(0);
	  }
      }
      Reader fr = new BufferedReader(new FileReader(args[0]));
      Lex.Lexer l = new Lex.Lexer(fr);
      java_cup.runtime.lr_parser g;
      g = new Parse.Parser(l);
      ParseNode p=(ParseNode) g./*debug_*/parse().value;
      State state=new State(p);
      
      BuildIR bir=new BuildIR(state);
      bir.buildtree();
      
      TypeUtil tu=new TypeUtil(state);
      
      SemanticCheck sc=new SemanticCheck(state,tu);
      sc.semanticCheck();
      
      BuildFlat bf=new BuildFlat(state);
      bf.buildFlat();
      
      BuildCode bc=new BuildCode(state, bf.getMap(), tu);
      bc.buildCode();
      
      System.exit(l.numErrors());
  }
}
