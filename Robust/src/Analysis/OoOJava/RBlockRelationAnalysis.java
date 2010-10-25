package Analysis.OoOJava;

import IR.State;
import IR.TypeUtil;
import Analysis.CallGraph.CallGraph;
import IR.MethodDescriptor;
import IR.Flat.*;
import java.util.*;

// This analysis computes relations between rblocks
// and identifies important rblocks.

public class RBlockRelationAnalysis {

  // compiler data
  State     state;
  TypeUtil  typeUtil;
  CallGraph callGraph;

  // an implicit SESE is automatically spliced into
  // the IR graph around the C main before this analysis--it
  // is nothing special except that we can make assumptions
  // about it, such as the whole program ends when it ends
  protected FlatSESEEnterNode mainSESE;

  // SESEs that are the root of an SESE tree belong to this
  // set--the main SESE is always a root, statically SESEs
  // inside methods are a root because we don't know how they
  // will fit into the runtime tree of SESEs
  protected Set<FlatSESEEnterNode> rootSESEs;

  // simply a set of every reachable SESE in the program, not
  // including caller placeholder SESEs
  protected Set<FlatSESEEnterNode> allSESEs;

  // per method-per node-rblock stacks
  protected Hashtable< FlatMethod, 
                       Hashtable< FlatNode, 
                                  Stack<FlatSESEEnterNode> 
                                >
                     > fm2relmap;

  // to support calculation of leaf SESEs (no children even
  // through method calls) for optimization during code gen
  protected Set<MethodDescriptor> methodsContainingSESEs;


  public RBlockRelationAnalysis( State     state,
                                 TypeUtil  typeUtil,
                                 CallGraph callGraph ) {
    this.state     = state;
    this.typeUtil  = typeUtil;
    this.callGraph = callGraph;

    rootSESEs = new HashSet<FlatSESEEnterNode>();
    allSESEs  = new HashSet<FlatSESEEnterNode>();

    methodsContainingSESEs = new HashSet<MethodDescriptor>();

    fm2relmap = 
      new Hashtable< FlatMethod, Hashtable< FlatNode, Stack<FlatSESEEnterNode> > >();

    
    MethodDescriptor mdSourceEntry = typeUtil.getMain();
    FlatMethod       fmMain        = state.getMethodFlat( mdSourceEntry );

    mainSESE = (FlatSESEEnterNode) fmMain.getNext( 0 );
    mainSESE.setfmEnclosing( fmMain );
    mainSESE.setmdEnclosing( fmMain.getMethod() );
    mainSESE.setcdEnclosing( fmMain.getMethod().getClassDesc() );

    // add all methods transitively reachable from the
    // source's main to set for analysis    
    Set<MethodDescriptor> descriptorsToAnalyze = 
      callGraph.getAllMethods( mdSourceEntry );
    
    descriptorsToAnalyze.add( mdSourceEntry );

    analyzeMethods( descriptorsToAnalyze );

    computeLeafSESEs();
  }


  public FlatSESEEnterNode getMainSESE() {
    return mainSESE;
  }

  public Set<FlatSESEEnterNode> getRootSESEs() {
    return rootSESEs;
  }

  public Set<FlatSESEEnterNode> getAllSESEs() {
    return allSESEs;
  }
  
  public Stack<FlatSESEEnterNode> getRBlockStacks( FlatMethod fm, 
                                                   FlatNode   fn ) {
    if( !fm2relmap.containsKey( fm ) ) {
      fm2relmap.put( fm, computeRBlockRelations( fm ) );
    }
    return fm2relmap.get( fm ).get( fn );
  }


  protected void analyzeMethods( Set<MethodDescriptor> descriptorsToAnalyze ) {

    Iterator<MethodDescriptor> mdItr = descriptorsToAnalyze.iterator();
    while( mdItr.hasNext() ) {
      FlatMethod fm = state.getMethodFlat( mdItr.next() );
        
      Hashtable< FlatNode, Stack<FlatSESEEnterNode> > relmap =
        computeRBlockRelations( fm );

      fm2relmap.put( fm, relmap );
    }
  }
  
  public Hashtable< FlatNode, Stack<FlatSESEEnterNode> >
    computeRBlockRelations( FlatMethod fm ) {
    
    Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks =
      new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >(); 
    
    // start from flat method top, visit every node in
    // method exactly once, find SESE stack on every
    // control path
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
      
      nodeActions( fn, seseStack, fm );

      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );
        
	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );

	  // clone stack and send along each control path
	  seseStacks.put( nn, (Stack<FlatSESEEnterNode>)seseStack.clone() );
	}
      }
    }      

    return seseStacks;
  }
  
  protected void nodeActions( FlatNode fn,
                              Stack<FlatSESEEnterNode> seseStack,
                              FlatMethod fm ) {
    switch( fn.kind() ) {

    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      if( !fsen.getIsCallerSESEplaceholder() ) {
        allSESEs.add( fsen );
        methodsContainingSESEs.add( fm.getMethod() );
      }

      fsen.setfmEnclosing( fm );
      fsen.setmdEnclosing( fm.getMethod() );
      fsen.setcdEnclosing( fm.getMethod().getClassDesc() );

      if( seseStack.empty() ) {
        rootSESEs.add( fsen );
        fsen.setParent( null );
      } else {
	seseStack.peek().addChild( fsen );
	fsen.setParent( seseStack.peek() );
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
      if( !seseStack.empty() &&
	  !seseStack.peek().getIsCallerSESEplaceholder() 
          ) {
	throw new Error( "Error: return statement enclosed within SESE "+
			 seseStack.peek().getPrettyIdentifier() );
      }
    } break;
      
    }
  }


  protected void computeLeafSESEs() {
    for( Iterator<FlatSESEEnterNode> itr = allSESEs.iterator();
         itr.hasNext();
         ) {
      FlatSESEEnterNode fsen = itr.next();

      boolean hasNoNestedChildren = fsen.getChildren().isEmpty();
      boolean hasNoChildrenByCall = !hasChildrenByCall( fsen );

      fsen.setIsLeafSESE( hasNoNestedChildren &&
                          hasNoChildrenByCall );
    }
  }


  protected boolean hasChildrenByCall( FlatSESEEnterNode fsen ) {

    // visit every flat node in SESE body, find method calls that
    // may transitively call methods with SESEs enclosed
    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add( fsen );
    
    Set<FlatNode> visited = new HashSet<FlatNode>();    

    while( !flatNodesToVisit.isEmpty() ) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      flatNodesToVisit.remove( fn );
      visited.add( fn );      

      if( fn.kind() == FKind.FlatCall ) {
        FlatCall         fc        = (FlatCall) fn;
        MethodDescriptor mdCallee  = fc.getMethod();
        Set              reachable = new HashSet();

        reachable.add( mdCallee );
        reachable.addAll( callGraph.getAllMethods( mdCallee ) );

        reachable.retainAll( methodsContainingSESEs );
        
        if( !reachable.isEmpty() ) {
          return true;
        }
      }

      if( fn == fsen.getFlatExit() ) {
        // don't enqueue any futher nodes
        continue;
      }
      
      for( int i = 0; i < fn.numNext(); i++ ) {
	FlatNode nn = fn.getNext( i );
        
	if( !visited.contains( nn ) ) {
	  flatNodesToVisit.add( nn );
	}
      }
    }      

    return false;
  }

}
