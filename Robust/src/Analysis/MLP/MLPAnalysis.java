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

  private Set<FlatSESEEnterNode>   seseRoots;

  private Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks;
  private Hashtable< FlatNode, VarSrcTokTable           > pointResults;


  public MLPAnalysis( State state,
		      TypeUtil tu,
		      CallGraph callGraph,
		      OwnershipAnalysis ownAnalysis
		      ) {

    double timeStartAnalysis = (double) System.nanoTime();

    this.state       = state;
    this.typeUtil    = tu;
    this.callGraph   = callGraph;
    this.ownAnalysis = ownAnalysis;

    // initialize analysis data structures
    seseRoots    = new HashSet<FlatSESEEnterNode>();
    seseStacks   = new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >();
    pointResults = new Hashtable< FlatNode, VarSrcTokTable           >();


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
    }

    Iterator<FlatSESEEnterNode> seseItr = seseRoots.iterator();
    while( seseItr.hasNext() ) {
      FlatSESEEnterNode fsen = seseItr.next();

      // do a post-order traversal of the forest so that
      // a child is analyzed before a parent.  Start from
      // SESE's exit and do a backward data-flow analysis
      // for the source of variables
      computeReadAndWriteSetBackward( fsen );
    }

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
    seseStacks.put( fm, seseStackFirst );

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      Stack<FlatSESEEnterNode> seseStack = seseStacks.get( fn );
      assert seseStack != null;      

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      analyzeFlatNodeForward( fn, seseStack );

      // initialize for backward computation in next step
      //pointResults.put( fn, new VarSrcTokTable() );

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


  private void computeReadAndWriteSetBackward( FlatSESEEnterNode fsen ) {

    // post-order traversal, so do children first
    Iterator<FlatSESEEnterNode> childItr = fsen.getChildren().iterator();
    while( childItr.hasNext() ) {
      FlatSESEEnterNode fsenChild = childItr.next();
      computeReadAndWriteSetBackward( fsenChild );
    }
    

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped 
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fsen.getFlatExit() );

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      VarSrcTokTable prev = pointResults.get( fn );

      // merge sets from control flow joins
      VarSrcTokTable inUnion = new VarSrcTokTable();
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );
	inUnion.merge( pointResults.get( nn ) );
      }

      VarSrcTokTable curr = analyzeFlatNodeBackward( fn, inUnion, fsen );

      // if a new result, schedule backward nodes for analysis
      if( !curr.equals( prev ) ) {

	pointResults.put( fn, curr );

	// don't flow backwards past SESE enter
	if( !fn.equals( fsen ) ) {	
	  for( int i = 0; i < fn.numPrev(); i++ ) {
	    FlatNode nn = fn.getPrev( i );	 
	    flatNodesToVisit.add( nn );	 
	  }
	}
      }
    }
    
    fsen.addInVarSet( pointResults.get( fsen ).get() );

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
  }


  private void analyzeFlatNodeForward( FlatNode fn, 							   
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


  private VarSrcTokTable analyzeFlatNodeBackward( FlatNode fn, 
						  VarSrcTokTable vstTable,
						  FlatSESEEnterNode currentSESE ) {
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;      
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;
	
      //FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      //assert fsen == seseStack.pop();
      //seseStack.peek().addInVarSet ( fsen.getInVarSet()  );
      //seseStack.peek().addOutVarSet( fsen.getOutVarSet() );
    } break;

    /*  
    case FKind.FlatMethod: {
      FlatMethod fm = (FlatMethod) fn;
    } break;
    */

      /*
    case FKind.FlatOpNode: 
    case FKind.FlatCastNode:
    case FKind.FlatFieldNode:
    case FKind.FlatSetFieldNode: 
    case FKind.FlatElementNode:
    case FKind.FlatSetElementNode:
      */

    default: {

      // handle effects of statement in reverse, writes then reads
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
	vstTable.remove( writeTemps[i] );
	currentSESE.addOutVar( new VariableSourceToken( currentSESE, 
							writeTemps[i],
							new Integer( 0 ) ) );
      }

      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; ++i ) {
	vstTable.add( new VariableSourceToken( currentSESE, 
					       readTemps[i],
					       new Integer( 0 ) ) );
      }
    } break;

    /*
    case FKind.FlatNew: {
      FlatNew fnn = (FlatNew) fn;
      lhs = fnn.getDst();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {
	//AllocationSite as = getAllocationSiteFromFlatNewPRIVATE( fnn );
      }
    } break;
    */

    /*
    case FKind.FlatCall: {
      FlatCall fc = (FlatCall) fn;
      MethodDescriptor md = fc.getMethod();
      FlatMethod flatm = state.getMethodFlat( md );


      if( md.isStatic() ) {

      } else {
	// if the method descriptor is virtual, then there could be a
	// set of possible methods that will actually be invoked, so
	// find all of them and merge all of their results together
	TypeDescriptor typeDesc = fc.getThis().getType();
	Set possibleCallees = callGraph.getMethods( md, typeDesc );

	Iterator i = possibleCallees.iterator();
	while( i.hasNext() ) {
	  MethodDescriptor possibleMd = (MethodDescriptor) i.next();
	  FlatMethod pflatm = state.getMethodFlat( possibleMd );

	}
      }

    } break;
    */

    } // end switch


    return vstTable;
  }
}
