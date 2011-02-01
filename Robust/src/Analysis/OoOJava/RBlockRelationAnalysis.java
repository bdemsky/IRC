package Analysis.OoOJava;

import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.TypeDescriptor;
import IR.Flat.*;
import Analysis.CallGraph.CallGraph;
import java.util.*;


// This analysis finds all reachable rblocks in the
// program and computes parent/child relations 
// between those rblocks

// SPECIAL NOTE!
// There is a distict between parent/child and
// local parent/local child!  "Local" means defined
// and nested within a single method context.
// Otherwise, SESE/rblocks/tasks may have many
// parents and many non-method-context-local
// children considering the call graph

// Also this analysis should identify "critical regions"
// in the context of interprocedural sese/rblock/task relations
// where a statement may conflict with some previously executing
// child task, even if it is in another method context.
//
// Ex:
//
// void main() {
//   task a {
//     Foo f = new Foo();
//     task achild1 {
//       f.z = 1;
//     }
//     doSomething( f );
//   }
// }
//
// void doSomething( Foo f ) {
//   f.z++;     <-------- These two statements are in the critical
//   f.z--;     <-------- region of 'a' after 'c1' and before 'c2'
//   task achild2 {
//     f.z--;
//   }
// }


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

  // this is a special task object, it is not in any IR graph
  // and it does not appear to have any children or parents.
  // It is a stand-in for whichever task is running when a
  // method context starts such that intraprocedural task
  // analyses have one static name for "the task who invoked
  // this method" to attach facts to.  It GREATLY simplifies
  // the OoOJava variable analysis, for instance
  protected FlatSESEEnterNode callerProxySESE;

  // simply the set of every reachable SESE in the program
  protected Set<FlatSESEEnterNode> allSESEs;
  
  // to support calculation of leaf SESEs (no children even
  // through method calls) for optimization during code gen
  protected Set<MethodDescriptor> methodsContainingSESEs;
  
  // maps method descriptor to SESE defined inside of it
  // only contains local root SESE definitions in corresponding method
  // (has no parent in the local method context)
  protected Hashtable< MethodDescriptor, Set<FlatSESEEnterNode> > md2localRootSESEs;

  // the set of every local root SESE in the program (SESE that
  // has no parent in the local method context)
  protected Set<FlatSESEEnterNode> allLocalRootSESEs;

  // if you want to know which rblocks might be executing a given flat
  // node it will be in this set
  protected Hashtable< FlatNode, Set<FlatSESEEnterNode> > fn2currentSESEs;

  // if you want to know the method-local, inner-most nested task that
  // is executing a flat node, it is either here or null.
  //
  // ex:
  // void foo() {
  //   task a {
  //     bar();  <-- here 'a' is the localInnerSESE
  //   }
  // void bar() {
  //   baz();  <-- here there is no locally-defined SESE, would be null
  // }
  protected Hashtable<FlatNode, FlatSESEEnterNode> fn2localInnerSESE;
  
  // indicates whether this statement might occur in a task and
  // after some child task definition such that, without looking at
  // the flat node itself, the parent might have to stall for child
  protected Hashtable<FlatNode, Boolean> fn2isPotentialStallSite;


  ////////////////////////
  // public interface
  ////////////////////////
  public FlatSESEEnterNode getMainSESE() {
    return mainSESE;
  }

  public Set<FlatSESEEnterNode> getAllSESEs() {
    return allSESEs;
  }

  public Set<FlatSESEEnterNode> getLocalRootSESEs() {
    return allLocalRootSESEs;
  }

  public Set<FlatSESEEnterNode> getLocalRootSESEs( FlatMethod fm ) {
    Set<FlatSESEEnterNode> out = md2localRootSESEs.get( fm );
    if( out == null ) {
      out = new HashSet<FlatSESEEnterNode>();
    }
    return out;
  }
  
  public Set<FlatSESEEnterNode> getPossibleExecutingRBlocks( FlatNode fn ) {
    return fn2currentSESEs.get( fn );
  }

  public FlatSESEEnterNode getLocalInnerRBlock( FlatNode fn ) {
    return fn2localInnerSESE.get( fn );
  }

  // the "caller proxy" is a static name for whichever
  // task invoked the current method context.  It is very
  // convenient to know this is ALWAYS a different instance
  // of any task defined within the current method context,
  // and so using its name simplifies many intraprocedural
  // analyses
  public FlatSESEEnterNode getCallerProxySESE() {
    return callerProxySESE;
  }

  public boolean isPotentialStallSite( FlatNode fn ) {
    Boolean ipss = fn2isPotentialStallSite.get( fn );
    if( ipss == null ) { 
      return false; 
    }
    return ipss;
  }


  public RBlockRelationAnalysis( State     state,
                                 TypeUtil  typeUtil,
                                 CallGraph callGraph ) {
    this.state     = state;
    this.typeUtil  = typeUtil;
    this.callGraph = callGraph;

    callerProxySESE = new FlatSESEEnterNode( null );
    callerProxySESE.setIsCallerProxySESE();

    allSESEs                = new HashSet<FlatSESEEnterNode>();
    allLocalRootSESEs       = new HashSet<FlatSESEEnterNode>();
    methodsContainingSESEs  = new HashSet<MethodDescriptor>();
    md2localRootSESEs       = new Hashtable<MethodDescriptor, Set<FlatSESEEnterNode>>();
    fn2currentSESEs         = new Hashtable<FlatNode, Set<FlatSESEEnterNode>>();
    fn2localInnerSESE       = new Hashtable<FlatNode, FlatSESEEnterNode>();
    fn2isPotentialStallSite = new Hashtable<FlatNode, Boolean>();

    
    MethodDescriptor mdSourceEntry = typeUtil.getMain();
    FlatMethod       fmMain        = state.getMethodFlat( mdSourceEntry );

    mainSESE = (FlatSESEEnterNode) fmMain.getNext( 0 );
    mainSESE.setfmEnclosing( fmMain );
    mainSESE.setmdEnclosing( fmMain.getMethod() );
    mainSESE.setcdEnclosing( fmMain.getMethod().getClassDesc() );
    

    // add all methods transitively reachable from the
    // source's main to set to find rblocks
    Set<MethodDescriptor> descriptorsToAnalyze = 
      callGraph.getAllMethods( mdSourceEntry );
    
    descriptorsToAnalyze.add( mdSourceEntry );

    findRblocksAndLocalParentChildRelations( descriptorsToAnalyze );

    findTransitiveParentChildRelations();

    findPossibleExecutingRBlocksAndStallSites();


    // Uncomment this phase to debug the marking of potential
    // stall sites for parents between/after children tasks.
    // After this debug thing runs in calls System.exit()
    //debugPrintPotentialStallSites( descriptorsToAnalyze );
  }



  
  protected void findRblocksAndLocalParentChildRelations( Set<MethodDescriptor> descriptorsToAnalyze ) {

    Iterator<MethodDescriptor> mdItr = descriptorsToAnalyze.iterator();
    while( mdItr.hasNext() ) {
      FlatMethod fm = state.getMethodFlat( mdItr.next() );
      
      // start from flat method top, visit every node in
      // method exactly once, find SESE stack on every
      // control path: this will discover every reachable
      // SESE in the program, and define the local parent
      // and local children relations
      Hashtable< FlatNode, Stack<FlatSESEEnterNode> > seseStacks =
        new Hashtable< FlatNode, Stack<FlatSESEEnterNode> >(); 

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

        if( !seseStack.isEmpty() ) {
          fn2localInnerSESE.put( fn, seseStack.peek() );          
        }

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
    }
  }

  protected void nodeActions( FlatNode fn,
                              Stack<FlatSESEEnterNode> seseStack,
                              FlatMethod fm ) {
    switch( fn.kind() ) {
      
    case FKind.FlatSESEEnterNode: {
      FlatSESEEnterNode fsen = (FlatSESEEnterNode) fn;

      allSESEs.add( fsen );
      methodsContainingSESEs.add( fm.getMethod() );

      fsen.setfmEnclosing( fm );
      fsen.setmdEnclosing( fm.getMethod() );
      fsen.setcdEnclosing( fm.getMethod().getClassDesc() );
      
      if( seseStack.empty() ) {
        // no local parent
        fsen.setLocalParent( null );

        allLocalRootSESEs.add( fsen );

        Set<FlatSESEEnterNode> seseSet = md2localRootSESEs.get( fm.getMethod() );
        if( seseSet == null ) {
          seseSet = new HashSet<FlatSESEEnterNode>();
        }
        seseSet.add( fsen );
        md2localRootSESEs.put( fm.getMethod(), seseSet );

      } else {
        // otherwise a local parent/child relation
        // which is also the broader parent/child
        // relation as well
	seseStack.peek().addLocalChild( fsen );
	fsen.setLocalParent( seseStack.peek() );
        
        seseStack.peek().addChild( fsen );
        fsen.addParent( seseStack.peek() );
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
	throw new Error( "Error: return statement enclosed within SESE "+
			 seseStack.peek().getPrettyIdentifier() );
      }
    } break;
      
    }
  }


  
  protected void findTransitiveParentChildRelations() {
       
    for (Iterator<FlatSESEEnterNode> itr = allSESEs.iterator(); itr.hasNext();) {
      FlatSESEEnterNode fsen = itr.next();

      boolean hasNoNestedChildren = fsen.getLocalChildren().isEmpty();
      boolean hasNoChildrenByCall = !hasChildrenByCall( fsen );

      fsen.setIsLeafSESE( hasNoNestedChildren && hasNoChildrenByCall );
    }
  }

  protected boolean hasChildrenByCall( FlatSESEEnterNode fsen ) {

    boolean hasChildrenByCall = false;

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
          hasChildrenByCall = true;

          Set reachableSESEMethodSet =
            callGraph.getFirstReachableMethodContainingSESE( mdCallee, methodsContainingSESEs );

          reachableSESEMethodSet.add( mdCallee );
          reachableSESEMethodSet.retainAll( methodsContainingSESEs );

          for( Iterator iterator = reachableSESEMethodSet.iterator(); iterator.hasNext(); ) {
            MethodDescriptor md = (MethodDescriptor) iterator.next();
            Set<FlatSESEEnterNode> seseSet = md2localRootSESEs.get( md );
            if( seseSet != null ) {
              fsen.addChildren( seseSet );
              for( Iterator iterator2 = seseSet.iterator(); iterator2.hasNext(); ) {
                FlatSESEEnterNode child = (FlatSESEEnterNode) iterator2.next();
                child.addParent( fsen );
              }            
            }
          }
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

    return hasChildrenByCall;
  }



  protected void findPossibleExecutingRBlocksAndStallSites() {
    for( Iterator<FlatSESEEnterNode> fsenItr = allSESEs.iterator(); fsenItr.hasNext(); ) {
      FlatSESEEnterNode fsen = fsenItr.next();

      // walk the program points, including across method calls, reachable within
      // this sese/rblock/task and mark that this rblock might be executing.
      // Important: skip the body of child rblocks, BUT DO mark the child ENTER
      // and EXIT flat nodes as the parent being the current executing rblock!
      Hashtable<FlatNode, FlatMethod> flatNodesToVisit = 
        new Hashtable<FlatNode, FlatMethod>();

      for( int i = 0; i < fsen.numNext(); i++ ) {
        FlatNode nn = fsen.getNext( i );        
        flatNodesToVisit.put( nn, fsen.getfmEnclosing() );
        mergeIsPotentialStallSite( nn, false );
      }
      
      Set<FlatNode> visited = new HashSet<FlatNode>();
      
      while( !flatNodesToVisit.isEmpty() ) {
        Map.Entry  me = (Map.Entry)  flatNodesToVisit.entrySet().iterator().next();
        FlatNode   fn = (FlatNode)   me.getKey();
        FlatMethod fm = (FlatMethod) me.getValue();

        flatNodesToVisit.remove( fn );
        visited.add( fn );


        // the "is potential stall site" strategy is to propagate
        // "false" from the beginning of a task until you hit a
        // child, then from the child's exit propagate "true" for
        // the parent statements after children.  When you pull a node
        // out of the bag for traversal and it happens to be an
        // enter or an exit node, fix the dumb propagation that
        // your IR predecessor pushed on you
        Boolean isPotentialStallSite = isPotentialStallSite( fn );

        if( fn instanceof FlatSESEEnterNode ||
            fn instanceof FlatSESEExitNode ) {
          // fix it so this is never a potential stall site, but from
          // a child definition onward propagate 'true'
          setIsPotentialStallSite( fn, false );
          isPotentialStallSite = true;
        }


        if( fn == fsen.getFlatExit() ) {
          // don't enqueue any futher nodes when you find your exit,
          // NOR mark your own flat as a statement you are currently
          // executing, your parent(s) will mark it
          continue;
        }


        // the purpose of this traversal is to find program
        // points where rblock 'fsen' might be executing
        addPossibleExecutingRBlock( fn, fsen );


        if( fn instanceof FlatSESEEnterNode ) {
          // don't visit internal nodes of child,
          // just enqueue the exit node
          FlatSESEEnterNode child = (FlatSESEEnterNode) fn;
          assert fsen.getChildren().contains( child );
          assert child.getParents().contains( fsen );
          flatNodesToVisit.put( child.getFlatExit(), fm );

          // explicitly do this to handle the case that you
          // should mark yourself as possibly executing at 
          // your own exit, because one instance can
          // recursively invoke another
          addPossibleExecutingRBlock( child.getFlatExit(), fsen );
          
          continue;
        }
                
        if( fn instanceof FlatCall ) {
          // start visiting nodes in other contexts
          FlatCall         fc       = (FlatCall) fn;
          MethodDescriptor mdCallee = fc.getMethod();

          Set<MethodDescriptor> implementations = new HashSet<MethodDescriptor>();

          if( mdCallee.isStatic() ) {
            implementations.add( mdCallee );
          } else {
            TypeDescriptor typeDesc = fc.getThis().getType();
            implementations.addAll( callGraph.getMethods( mdCallee, typeDesc ) );
          }

          for( Iterator imps = implementations.iterator(); imps.hasNext(); ) {
            MethodDescriptor mdImp = (MethodDescriptor) imps.next();
            FlatMethod       fmImp = state.getMethodFlat( mdImp );
            flatNodesToVisit.put( fmImp, fmImp );

            // propagate your IR graph predecessor's stall site potential
            mergeIsPotentialStallSite( fmImp, isPotentialStallSite );
          }
          // don't 'continue' out of this loop, also enqueue
          // flat nodes that flow in the current method context
        }
        
        // otherwise keep visiting nodes in same context
        for( int i = 0; i < fn.numNext(); i++ ) {
          FlatNode nn = fn.getNext( i );

          if( !visited.contains( nn ) ) {
            flatNodesToVisit.put( nn, fm );

            // propagate your IR graph predecessor's stall site potential
            mergeIsPotentialStallSite( nn, isPotentialStallSite );
          }
        }
      }     
    }
  }
  


  protected void addPossibleExecutingRBlock( FlatNode          fn,
                                             FlatSESEEnterNode fsen ) {

    Set<FlatSESEEnterNode> currentSESEs = fn2currentSESEs.get( fn );
    if( currentSESEs == null ) {
      currentSESEs = new HashSet<FlatSESEEnterNode>();
    }

    currentSESEs.add( fsen );
    fn2currentSESEs.put( fn, currentSESEs );
  }

  
  // definitively set whether a statement is a potential stall site
  // such as a task exit is FALSE and the statement following an exit
  // is TRUE
  protected void setIsPotentialStallSite( FlatNode   fn,
                                          Boolean    ipss ) {
    fn2isPotentialStallSite.put( fn, ipss );
  }


  // Use this to OR the previous result with a new result
  protected void mergeIsPotentialStallSite( FlatNode   fn,
                                            Boolean    ipss ) {
    Boolean ipssPrev = isPotentialStallSite( fn );
    setIsPotentialStallSite( fn, ipssPrev || ipss );
  }





  /////////////////////////////////////////////////
  // for DEBUG
  /////////////////////////////////////////////////
  protected void debugPrintPotentialStallSites(Set<MethodDescriptor> descriptorsToAnalyze) {
    Iterator<MethodDescriptor> mdItr = descriptorsToAnalyze.iterator();
    while (mdItr.hasNext()) {
      FlatMethod fm = state.getMethodFlat(mdItr.next());
      printStatusMap(fm);
    }
    System.exit( 0 );
  }

  protected void printStatusMap(FlatMethod fm) {

    System.out.println("\n\n=== "+fm+" ===");

    Set<FlatNode> flatNodesToVisit = new HashSet<FlatNode>();
    flatNodesToVisit.add(fm);

    Set<FlatNode> visited = new HashSet<FlatNode>();

    while (!flatNodesToVisit.isEmpty()) {
      Iterator<FlatNode> fnItr = flatNodesToVisit.iterator();
      FlatNode fn = fnItr.next();

      flatNodesToVisit.remove(fn);
      visited.add(fn);

      System.out.println(fn+"[["+isPotentialStallSite(fn)+"]]");

      for (int i = 0; i < fn.numNext(); i++) {
        FlatNode nn = fn.getNext(i);

        if (!visited.contains(nn)) {
          flatNodesToVisit.add(nn);
        }
      }
    }
  }

}