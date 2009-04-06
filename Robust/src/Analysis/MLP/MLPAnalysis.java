package Analysis.MLP;

import Analysis.CallGraph.*;
import Analysis.OwnershipAnalysis.*;
import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class MLPAnalysis {

  // data from the compiler
  private State state;
  private TypeUtil typeUtil;
  private CallGraph callGraph;
  private OwnershipAnalysis ownAnalysis;

  public MLPAnalysis(State state,
		     TypeUtil tu,
		     CallGraph callGraph,
		     OwnershipAnalysis ownAnalysis
		     ) {

    double timeStartAnalysis = (double) System.nanoTime();

    this.state       = state;
    this.typeUtil    = tu;
    this.callGraph   = callGraph;
    this.ownAnalysis = ownAnalysis;


    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
    String treport = String.format( "The mlp analysis took %.3f sec.", dt );
    System.out.println( treport );
  }


  Stack<FlatSESEEnterNode> seseStack;

  protected void analyze() {
    seseStack = new Stack<FlatSESEEnterNode>();

    /*
      if( !seseStack.empty() ) {
      seseStack.peek().addInVar( tmp );
      seseStack.peek().addOutVar( out_temp );
      }
    */

    /*
      if( !seseStack.empty() ) {
      throw new Error("Error: return statement enclosed within SESE "+seseStack.peek());
      }
    */

    /*
    if( sn.isStart() ) {
      FlatSESEEnterNode fsen=new FlatSESEEnterNode(sn);
      sn.setFlatEnter(fsen);
      seseStack.push(fsen);
      return new NodePair(fsen, fsen);
    }

    FlatSESEExitNode fsexn=new FlatSESEExitNode(sn);
    sn.setFlatExit(fsexn);
    FlatSESEEnterNode fsen=sn.getStart().getFlatEnter();
    fsexn.setFlatEnter(fsen);    
    sn.getStart().getFlatEnter().setFlatExit( fsexn );
    assert !seseStack.empty();
    assert fsen == seseStack.pop();
    if( !seseStack.empty() ) {
      seseStack.peek().addInVarSet ( fsen.getInVarSet()  );
      seseStack.peek().addOutVarSet( fsen.getOutVarSet() );
    }
    */
  }
}
