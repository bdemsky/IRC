package Analysis.MLP;

import Analysis.CallGraph.*;
import Analysis.OwnershipAnalysis.*;
import IR.*;
import IR.Flat.*;
import IR.Tree.*;
import java.util.*;
import java.io.*;


public class MLPAnalysis {

  // data from the compiler
  private State state;
  private TypeUtil typeUtil;
  private CallGraph callGraph;
  private OwnershipAnalysis ownAnalysis;

  private Set<FlatSESEEnterNode> seseRoots;
  private SESENode          rootTree;
  private FlatSESEEnterNode rootSESE;
  private FlatSESEExitNode  rootExit;

  private Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks;
  private Hashtable< FlatNode, Set<TempDescriptor>      > livenessResults;
  private Hashtable< FlatNode, VarSrcTokTable           > variableResults;


  public MLPAnalysis( State             state,
		      TypeUtil          tu,
		      CallGraph         callGraph,
		      OwnershipAnalysis ownAnalysis
		      ) {

    double timeStartAnalysis = (double) System.nanoTime();

    this.state       = state;
    this.typeUtil    = tu;
    this.callGraph   = callGraph;
    this.ownAnalysis = ownAnalysis;

    // initialize analysis data structures
    seseRoots       = new HashSet<FlatSESEEnterNode>();
    seseStacks      = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();
    livenessResults = new Hashtable< FlatNode, Set<TempDescriptor>      >();
    variableResults = new Hashtable< FlatNode, VarSrcTokTable           >();

    // build an implicit root SESE to wrap contents of main method
    /*
    rootTree = new SESENode( "root" );
    rootSESE = new FlatSESEEnterNode( rootTree );
    rootExit = new FlatSESEExitNode ( rootTree );
    rootSESE.setFlatExit ( rootExit );
    rootExit.setFlatEnter( rootSESE );
    seseRoots.add( rootSESE );
    */

    // run analysis on each method that is actually called
    // reachability analysis already computed this so reuse
    Iterator<Descriptor> methItr = ownAnalysis.descriptorsToAnalyze.iterator();
    while( methItr.hasNext() ) {
      Descriptor d = methItr.next();
      
      FlatMethod fm;
      if( d instanceof MethodDescriptor ) {
	fm = state.getMethodFlat( (MethodDescriptor) d);
      } else {
	assert d instanceof TaskDescriptor;
	fm = state.getMethodFlat( (TaskDescriptor) d);
      }

      // find every SESE from methods that may be called
      // and organize them into roots and children
      buildForestForward( fm );

      if( state.MLPDEBUG ) { 
	printSESEForest();
      }
    }

    Iterator<FlatSESEEnterNode> seseItr = seseRoots.iterator();
    while( seseItr.hasNext() ) {
      FlatSESEEnterNode fsen = seseItr.next();

      // do a post-order traversal of the forest so that
      // a child is analyzed before a parent.  Start from
      // SESE's exit and do a backward data-flow analysis
      // for the source of variables
      livenessAnalysisBackward( fsen );
    }

    /*
    seseItr = seseRoots.iterator();
    while( seseItr.hasNext() ) {
      FlatSESEEnterNode fsen = seseItr.next();

      // starting from roots do a forward, fixed-point
      // variable analysis for refinement and stalls
      variableAnalysisForward( fsen );
    }
    */

    double timeEndAnalysis = (double) System.nanoTime();
    double dt = (timeEndAnalysis - timeStartAnalysis)/(Math.pow( 10.0, 9.0 ) );
    String treport = String.format( "The mlp analysis took %.3f sec.", dt );
    System.out.println( treport );
  }

  private void buildForestForward( FlatMethod fm ) {
    
    // start from flat method top, visit every node in
    // method exactly once, find SESEs and remember
    // roots and child relationships
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );

    Set<FlatNode> visited = new HashSet<FlatNode>();    

    Stack<FlatSESEEnterNode> seseStackFirst = new Stack<FlatSESEEnterNode>();
    //seseStackFirst.push( rootSESE );
    seseStacks.put( fm, seseStackFirst );

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      buildForest_nodeActions( fn, seseStack );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );

	  // clone stack and send along each analysis path
	  seseStacks.put( nn, (Stack<FlatSESEEnterNode>)seseStack.clone() );
	}
      }
    }      
  }

  private void buildForest_nodeActions( FlatNode fn, 							   
					Stack<FlatSESEEnterNode> seseStack ) {
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      if( seseStack.empty() ) {
	seseRoots.add( fsen );
      } else {
	seseStack.peek().addChild( fsen );
      }
      seseStack.push( fsen );
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;

      assert !seseStack.empty();
      FlatSESEEnterNode fsen = seseStack.pop();
    } break;

    case FKind.FlatReturnNode: {
      FlatReturnNode frn = (FlatReturnNode) fn;
      if( !seseStack.empty() ) {
	throw new Error( "Error: return statement enclosed within "+seseStack.peek() );
      }
    } break;
      
    }
  }

  private void printSESEForest() {
    // we are assuming an implicit root SESE in the main method
    // so assert that our forest is actually a tree
    assert seseRoots.size() == 1;

    System.out.println( "SESE Forest:" );      
    Iterator<FlatSESEEnterNode> seseItr = seseRoots.iterator();
    while( seseItr.hasNext() ) {
      FlatSESEEnterNode fsen = seseItr.next();
      printSESETree( fsen, 0 );
      System.out.println( "" );
    }
  }

  private void printSESETree( FlatSESEEnterNode fsen, int depth ) {
    for( int i = 0; i < depth; ++i ) {
      System.out.print( "  " );
    }
    System.out.println( fsen.getPrettyIdentifier() );

    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      printSESETree( fsenChild, depth + 1 );
    }
  }


  private void livenessAnalysisBackward( FlatSESEEnterNode fsen ) {
    
    // post-order traversal, so do children first
    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      livenessAnalysisBackward( fsenChild );
    }

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped 
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    FlatSESEExitNode fsexn = fsen.getFlatExit();
    flatNodesToVisit.add( fsexn );
    /*
    for( int i = 0; i < fsexn.numPrev(); i++ ) {
      FlatNode nn = fsexn.getPrev( i );	 
      flatNodesToVisit.add( nn );	 
    }
    */

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      /*
      if( fn.kind() == FKind.FlatSESEExitNode ) {
	fn = ((FlatSESEExitNode)fn).getFlatEnter();
      }
      */
      
      Set<TempDescriptor> prev = livenessResults.get( fn );

      // merge sets from control flow joins
      Set<TempDescriptor> u = new HashSet<TempDescriptor>();
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );
        Set<TempDescriptor> s = livenessResults.get( nn );
        if( s != null ) {
          u.addAll( s );
        }
      }

      Set<TempDescriptor> curr = liveness_nodeActions( fn, u, fsen );

      // if a new result, schedule backward nodes for analysis
      if( !curr.equals( prev ) ) {

	livenessResults.put( fn, curr );

	// don't flow backwards past current SESE enter
	if( !fn.equals( fsen ) ) {	
	  for( int i = 0; i < fn.numPrev(); i++ ) {
	    FlatNode nn = fn.getPrev( i );	 
	    flatNodesToVisit.add( nn );	 
	  }
	}
      }
    }
    
    Set<TempDescriptor> s = livenessResults.get( fsen );
    if( s != null ) {
      fsen.addInVarSet( s );
    }
    
    if( state.MLPDEBUG ) { 
      System.out.println( "SESE "+fsen.getPrettyIdentifier()+" has in-set:" );
      Iterator<TempDescriptor> tItr = fsen.getInVarSet().iterator();
      while( tItr.hasNext() ) {
	System.out.println( "  "+tItr.next() );
      }
      /*
      System.out.println( "and out-set:" );
      tItr = fsen.getOutVarSet().iterator();
      while( tItr.hasNext() ) {
	System.out.println( "  "+tItr.next() );
      }
      */
      System.out.println( "" );
    }
  }

  private Set<TempDescriptor> liveness_nodeActions( FlatNode fn, 
                                                    Set<TempDescriptor> liveIn,
                                                    FlatSESEEnterNode currentSESE ) {
    switch( fn.kind() ) {
      
    default: {
      // handle effects of statement in reverse, writes then reads
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
	liveIn.remove( writeTemps[i] );
      }

      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; ++i ) {
	liveIn.add( readTemps[i] );
      }
    } break;

    } // end switch

    return liveIn;
  }


  private void variableAnalysisForward( FlatSESEEnterNode fsen ) {
    /*
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fsen );	 

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      VarSrcTokTable prev = variableResults.get( fn );

      // merge sets from control flow joins
      VarSrcTokTable inUnion = new VarSrcTokTable();
      for( int i = 0; i < fn.numPrev(); i++ ) {
	FlatNode nn = fn.getPrev( i );
	inUnion.merge( variableResults.get( nn ) );
      }

      VarSrcTokTable curr = variable_nodeActions( fn, inUnion, fsen );

      // if a new result, schedule backward nodes for analysis
      if( !curr.equals( prev ) ) {

	variableResults.put( fn, curr );

	// don't flow backwards past SESE enter
	if( !fn.equals( fsen ) ) {	
	  for( int i = 0; i < fn.numPrev(); i++ ) {
	    FlatNode nn = fn.getPrev( i );	 
	    flatNodesToVisit.add( nn );	 
	  }
	}
      }
    }
    
    fsen.addInVarSet( variableResults.get( fsen ).get() );

    if( state.MLPDEBUG ) { 
      System.out.println( "SESE "+fsen.getPrettyIdentifier()+" has in-set:" );
      Iterator<VariableSourceToken> tItr = fsen.getInVarSet().iterator();
      while( tItr.hasNext() ) {
	System.out.println( "  "+tItr.next() );
      }
      System.out.println( "and out-set:" );
      tItr = fsen.getOutVarSet().iterator();
      while( tItr.hasNext() ) {
	System.out.println( "  "+tItr.next() );
      }
      System.out.println( "" );
    }
    */
  }

  private VarSrcTokTable variable_nodeActions( FlatNode fn, 
					       VarSrcTokTable vstTable,
					       FlatSESEEnterNode currentSESE ) {
    switch( fn.kind() ) {


    default: {
    } break;

    } // end switch

    return vstTable;
  }
}
