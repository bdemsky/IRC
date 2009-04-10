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

  private Hashtable< FlatNode, Set<VariableSourceToken> > pointResults;


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
    pointResults = new Hashtable< FlatNode, Set<VariableSourceToken> >();


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

      //System.out.println( "  considering "+fn );

      /*
      // only analyze sese exit nodes when all the nodes between
      // it and its matching enter have been analyzed
      if( !seseStack.empty() &&
	  fn.equals( seseStack.peek().getFlatExit() ) &&
	  flatNodesToVisit.size() != 1 ) {
	// not ready for this exit node yet, just grab another
	fn = fnItr.next();
      }
      */

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      //System.out.println( "    visiting "+fn );

      analyzeFlatNodeForward( fn, seseStack );

      // initialize for backward computation in next step
      pointResults.put( fn, new HashSet<VariableSourceToken>() );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );

	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );

	  seseStacks.put( nn, (Stack<FlatSESEEnterNode>)seseStack.clone() );
	}
      }
    }      
  }


  private void computeReadAndWriteSetBackward( FlatSESEEnterNode fsen ) {

    // start from an SESE exit, visit nodes in reverse up to
    // SESE enter in a fixed-point scheme, where children SESEs
    // should already be analyzed and therefore can be skipped 
    // because child SESE enter node has all necessary info
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fsen.getFlatExit() );

    while( !flatNodesToVisit.isEmpty() ) {
      FlatNode fn = (FlatNode) flatNodesToVisit.iterator().next();
      flatNodesToVisit.remove( fn );      

      Set<VariableSourceToken> prev = pointResults.get( fn );

      // merge sets from control flow joins
      Set<VariableSourceToken> merge = new HashSet<VariableSourceToken>();
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );	 
	merge = mergeVSTsets( merge, pointResults.get( nn ) );
      }

      Set<VariableSourceToken> curr = analyzeFlatNodeBackward( fn, merge, fsen );

      // if a new result, schedule backward nodes for analysis
      if( !prev.equals( curr ) ) {

	//System.out.println( "  "+fn+":" );
	//System.out.println( "    prev ="+prev  );
	//System.out.println( "    merge="+merge );
	//System.out.println( "    curr ="+curr  );
	//System.out.println( "" );

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

    if( state.MLPDEBUG ) {
      System.out.println( "SESE "+fsen.getPrettyIdentifier()+" has in-set:" );
      Iterator<VariableSourceToken> tItr = pointResults.get( fsen ).iterator();
      while( tItr.hasNext() ) {
	System.out.println( "  "+tItr.next() );
      }
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
      //System.out.println( "  pushed "+fsen );
    } break;

    case FKind.FlatSESEExitNode: {
      FlatSESEExitNode fsexn = (FlatSESEExitNode) fn;

      assert !seseStack.empty();
      FlatSESEEnterNode fsen = seseStack.pop();
      //System.out.println( "  popped "+fsen );
    } break;

    case FKind.FlatReturnNode: {
      FlatReturnNode frn = (FlatReturnNode) fn;
      if( !seseStack.empty() ) {
	throw new Error( "Error: return statement enclosed within "+seseStack.peek() );
      }
    } break;
      
    }
  }


  private Set<VariableSourceToken> analyzeFlatNodeBackward( FlatNode fn, 
							    Set<VariableSourceToken> vstSet,
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

    case FKind.FlatOpNode: 
    case FKind.FlatCastNode:
    case FKind.FlatFieldNode:
    case FKind.FlatSetFieldNode: 
    case FKind.FlatElementNode:
    case FKind.FlatSetElementNode: {

      // handle effects of statement in reverse, writes then reads
      TempDescriptor [] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
	vstSet = killTemp( vstSet, writeTemps[i] );
      }

      TempDescriptor [] readTemps = fn.readsTemps();
      for( int i = 0; i < readTemps.length; ++i ) {
	Set<VariableSourceToken> vstNew = new HashSet<VariableSourceToken>();
	vstNew.add( new VariableSourceToken( currentSESE, 
					     readTemps[i],
					     new Integer( 0 ) ) );
	vstSet = mergeVSTsets( vstSet, vstNew );
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


    return vstSet;
  }


  private Set<VariableSourceToken> killTemp( Set<VariableSourceToken> s,
					     TempDescriptor t ) {
    Set<VariableSourceToken> out = new HashSet<VariableSourceToken>();

    Iterator<VariableSourceToken> vstitr = s.iterator();
    while( vstitr.hasNext() ) {
      VariableSourceToken vst = vstitr.next();    

      if( !vst.getVar().equals( t ) ) {
	out.add( vst );
      }
    }

    return out;
  }


  private Set<VariableSourceToken> mergeVSTsets( Set<VariableSourceToken> s1,
						 Set<VariableSourceToken> s2 ) {
    
    Set<VariableSourceToken> out = new HashSet<VariableSourceToken>();

    Iterator<VariableSourceToken> vst1itr = s1.iterator();
    while( vst1itr.hasNext() ) {
      VariableSourceToken vst1 = vst1itr.next();

      int changeAge = -1;
      
      Iterator<VariableSourceToken> vst2itr = s2.iterator();
      while( vst2itr.hasNext() ) {
	VariableSourceToken vst2 = vst2itr.next();

	if( vst1.getSESE().equals( vst2.getSESE() ) &&
	    vst1.getVar() .equals( vst2.getVar()  )    ) {
	  changeAge = vst1.getAge();
	  int a = vst2.getAge();
	  if( a < changeAge ) {
	    changeAge = a;
	  }
	  break;
	}
      }

      if( changeAge < 0 ) {
	out.add( vst1 );
      } else {
	out.add( new VariableSourceToken( vst1.getSESE(),
					  vst1.getVar(),
					  new Integer( changeAge ) ) );
      }
    }


    Iterator<VariableSourceToken> vst2itr = s2.iterator();
    while( vst2itr.hasNext() ) {
      VariableSourceToken vst2 = vst2itr.next();           

      boolean matchSESEandVar = false;

      vst1itr = s1.iterator();
      while( vst1itr.hasNext() ) {
	VariableSourceToken vst1 = vst1itr.next();

	if( vst1.getSESE().equals( vst2.getSESE() ) &&
	    vst1.getVar() .equals( vst2.getVar()  )    ) {
	  matchSESEandVar = true;
	  break;
	}
      }

      if( !matchSESEandVar ) {
	out.add( vst2 );
      }
    }
    

    return out;
  }
}


