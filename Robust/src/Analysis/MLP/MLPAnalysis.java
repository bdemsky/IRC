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


  private Stack<FlatSESEEnterNode> seseStack;
  private Set<FlatSESEEnterNode>   seseRoots;


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
    seseStack = new Stack  <FlatSESEEnterNode>();
    seseRoots = new HashSet<FlatSESEEnterNode>();

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

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fm );

    Set<FlatNode> visited = new HashSet<FlatNode>();

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );
      visited.add( fn );

      //System.out.println( "  "+fn );

      analyzeFlatNode( fn, true );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );
	}
      }
    }      
  }


  private void computeReadAndWriteSetBackward( FlatSESEEnterNode fsen ) {
    
  }


  private void analyzeFlatNode( FlatNode fn, boolean buildForest ) {

    TempDescriptor lhs;
    TempDescriptor rhs;
    FieldDescriptor fld;

    // use node type to decide what alterations to make
    // to the ownership graph
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      if( buildForest ) {
	if( seseStack.empty() ) {
	  seseRoots.add( fsen );
	} else {
	  seseStack.peek().addChild( fsen );
	}
	seseStack.push( fsen );
      }
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode  fsexn = (FlatSESEExitNode) fn;

      if( buildForest ) {
	assert !seseStack.empty();
	seseStack.pop();
      }
	
      //FlatSESEEnterNode fsen  = fsexn.getFlatEnter();
      //assert fsen == seseStack.pop();
      //seseStack.peek().addInVarSet ( fsen.getInVarSet()  );
      //seseStack.peek().addOutVarSet( fsen.getOutVarSet() );
    } break;

      /*
    case FKind.FlatMethod: {
      FlatMethod fm = (FlatMethod) fn;
    } break;

    case FKind.FlatOpNode: {
      FlatOpNode fon = (FlatOpNode) fn;
      if( fon.getOp().getOp() == Operation.ASSIGN ) {
	lhs = fon.getDest();
	rhs = fon.getLeft();

      }
    } break;

    case FKind.FlatCastNode: {
      FlatCastNode fcn = (FlatCastNode) fn;
      lhs = fcn.getDst();
      rhs = fcn.getSrc();

      TypeDescriptor td = fcn.getType();
      assert td != null;
      
    } break;

    case FKind.FlatFieldNode: {
      FlatFieldNode ffn = (FlatFieldNode) fn;
      lhs = ffn.getDst();
      rhs = ffn.getSrc();
      fld = ffn.getField();
      if( !fld.getType().isImmutable() || fld.getType().isArray() ) {

      }
    } break;

    case FKind.FlatSetFieldNode: {
      FlatSetFieldNode fsfn = (FlatSetFieldNode) fn;
      lhs = fsfn.getDst();
      fld = fsfn.getField();
      rhs = fsfn.getSrc();
      if( !fld.getType().isImmutable() || fld.getType().isArray() ) {

      }
    } break;

    case FKind.FlatElementNode: {
      FlatElementNode fen = (FlatElementNode) fn;
      lhs = fen.getDst();
      rhs = fen.getSrc();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {

	assert rhs.getType() != null;
	assert rhs.getType().isArray();
	
	TypeDescriptor  tdElement = rhs.getType().dereference();
	//FieldDescriptor fdElement = getArrayField( tdElement );
  
      }
    } break;

    case FKind.FlatSetElementNode: {
      FlatSetElementNode fsen = (FlatSetElementNode) fn;
      lhs = fsen.getDst();
      rhs = fsen.getSrc();
      if( !rhs.getType().isImmutable() || rhs.getType().isArray() ) {

	assert lhs.getType() != null;
	assert lhs.getType().isArray();
	
	TypeDescriptor  tdElement = lhs.getType().dereference();
	//FieldDescriptor fdElement = getArrayField( tdElement );
      }
    } break;

    case FKind.FlatNew: {
      FlatNew fnn = (FlatNew) fn;
      lhs = fnn.getDst();
      if( !lhs.getType().isImmutable() || lhs.getType().isArray() ) {
	//AllocationSite as = getAllocationSiteFromFlatNewPRIVATE( fnn );
      }
    } break;

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

    case FKind.FlatReturnNode: {
      FlatReturnNode frn = (FlatReturnNode) fn;
      rhs = frn.getReturnTemp();
      if( rhs != null && !rhs.getType().isImmutable() ) {

      }
      if( !seseStack.empty() ) {
	throw new Error( "Error: return statement enclosed within SESE "+seseStack.peek() );
      }
    } break;
      */
    } // end switch
  }
}
