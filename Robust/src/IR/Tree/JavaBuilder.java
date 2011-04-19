package IR.Tree;

import IR.*;

public class JavaBuilder {
  State state;

  public JavaBuilder(State state) {
    this.state=state;
  }


  public void build(Vector<String> sourcefiles) {
    BuildIR bir=new BuildIR(state);
    TypeUtil tu=new TypeUtil(state, bir);

    for(int i=0;i<sourcefiles.size();i++)
      loadClass(bir, sourcefiles.get(i));

    

    BuildFlat bf=new BuildFlat(state,tu);
    bf.buildFlat();

  }

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

  public static void loadClass(BuildIR bir, String sourcefile) {
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
}