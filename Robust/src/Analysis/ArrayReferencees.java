//////////////////////////////////////////////////
//
//  The object of this analysis is to maintain a
//  relation for program points: 
//  array variable -> variables referenced by the array's elements
//
//  This analysis is useful in reachability analysis
//  because if a variable is read from an array,
//  then inserted back into the array, we can be
//  sure that no new reachability paths are created.
//
//////////////////////////////////////////////////

package Analysis;

import IR.State;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatCall;
import IR.Flat.FKind;
import IR.Descriptor;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.TaskDescriptor;
import IR.TypeDescriptor;
import Util.UtilAlgorithms;
import java.util.*;
import java.io.*;


public class ArrayReferencees {

  ////////////////////////////////
  // public interface
  ////////////////////////////////
  public ArrayReferencees( State state ) {
    init( state );
  }

  public boolean mayCreateNewReachabilityPaths( FlatSetElementNode fsen ) {
    return true;
  }
  ////////////////////////////////
  // end public interface
  ////////////////////////////////



  protected State state;

  // maintain the relation at every program point
  protected Hashtable<FlatNode, InArrayRelation> fn2rel;


  protected ArrayReferencees() {}

  protected void init( State state ) {
    this.state = state;
    fn2rel = new Hashtable<FlatNode, InArrayRelation>();
    buildRelation();
  }

  protected void buildRelation() {
    // just analyze every method of every class that the
    // compiler has code for, fix if this is too costly
    Iterator classItr = state.getClassSymbolTable().getDescriptorsIterator();
    while( classItr.hasNext() ) {
      ClassDescriptor cd = (ClassDescriptor)classItr.next();

      Iterator methodItr = cd.getMethods();
      while( methodItr.hasNext() ) {
	MethodDescriptor md = (MethodDescriptor)methodItr.next();

	FlatMethod fm =	state.getMethodFlat( md );
	analyzeMethod( fm );
      }
    }
  }  

  protected void analyzeMethod( FlatMethod fm ) {
    Set<FlatNode> toVisit = new HashSet<FlatNode>();
    toVisit.add( fm );

    while( !toVisit.isEmpty() ) {
      FlatNode fn = toVisit.iterator().next();
      toVisit.remove( fn );

      // find the result flowing into this node
      InArrayRelation rel = new InArrayRelation();
      
      // the join operation is intersection, so
      // merge with 1st predecessor (if any) and
      // then do intersect with all others
      if( fn.numPrev() > 0 ) {
        rel.merge( fn2rel.get( fn.getPrev( 0 ) ) );

        for( int i = 1; i < fn.numPrev(); ++i ) {
          rel.intersect( fn2rel.get( fn.getPrev( i ) ) );
        }
      }

      analyzeFlatNode( rel, fn );

      // enqueue child nodes if new results were found
      InArrayRelation relPrev = fn2rel.get( fn );
      if( !rel.equals( relPrev ) ) {
        fn2rel.put( fn, rel );
	for( int i = 0; i < fn.numNext(); i++ ) {
	  FlatNode nn = fn.getNext( i );
	  toVisit.add( nn );
	}
      }
    }    
  }

  protected void analyzeFlatNode( FlatNode fn,
                                  InArrayRelation rel ) {
	  
    TempDescriptor lhs;
    TempDescriptor rhs;

    // use node type to transform relation
    switch( fn.kind() ) {

    case FKind.FlatElementNode:
      // lhs = rhs[...]
      FlatElementNode fen = (FlatElementNode) fn;
      lhs = fen.getDst();
      rhs = fen.getSrc();
      rel.remove( lhs );
      rel.put_array2refee( rhs, lhs );
      break;

    case FKind.FlatSetElementNode:
      // lhs[...] = rhs
      FlatSetElementNode fsen = (FlatSetElementNode) fn;
      lhs = fsen.getDst();
      rhs = fsen.getSrc();
      rel.put_array2refee( lhs, rhs );
      break;    
      
    default:
      // the default action is to remove every temp
      // written by this node from the relation
      TempDescriptor[] writeTemps = fn.writesTemps();
      for( int i = 0; i < writeTemps.length; ++i ) {
        TempDescriptor td = writeTemps[i];
        rel.remove( td );
      }
      break;

    }
  }
}



protected class InArrayRelation {

  // The relation is possibly dense, in that any variable might
  // be referenced by many arrays, and an array may reference
  // many variables.  So maintain the relation as two hashtables
  // that are redundant but allow efficient operations
  protected Hashtable< TempDescriptor, Set<TempDescriptor> > array2refees;
  protected Hashtable< TempDescriptor, Set<TempDescriptor> > refee2arrays;
  
  public InArrayRelation() {
    array2refees = new Hashtable< TempDescriptor, Set<TempDescriptor> >();
    refee2arrays = new Hashtable< TempDescriptor, Set<TempDescriptor> >();
  }

  public void put_array2refee( TempDescriptor array, TempDescriptor refee ) {
    // update one direction
    Set<TempDescriptor> refees = array2refees.get( array );
    if( refees == null ) {
      refees = new HashSet<TempDescriptor>();
    }
    refees.add( refee );
    array2refees.put( array, refees );

    // and then the other
    Set<TempDescriptor> arrays = refee2arrays.get( refee );
    if( arrayss == null ) {
      arrays = new HashSet<TempDescriptor>();
    }
    arrays.add( array );
    refee2arrays.put( refee, arrays );

    assertConsistent();
  }

  public void remove( TempDescriptor td ) {
    

    assertConsistent();
  }
  
  public void merge( InArrayRelation r ) {
    if( r == null ) {
      return;
    }
    UtilAlgorithms.mergeHashtablesWithHashSetValues( array2refees, r.array2refees );
    UtilAlgorithms.mergeHashtablesWithHashSetValues( refee2arrays, r.refee2arrays );
    assertConsistent();
  }

  public void intersect( InArrayRelation r ) {
    if( r == null ) {
      array2refees.clear();
      refee2arrays.clear();
      return;
    }
    UtilAlgorithms.intersectHashtablesWithSetValues( array2refees, r.array2refees );
    UtilAlgorithms.intersectHashtablesWithSetValues( refee2arrays, r.refee2arrays );
    assertConsistent();
  }
  
  public void assertConsistent() {
    assert allEntriesInAReversedInB( array2refees, refee2arrays );
    assert allEntriesInAReversedInB( refee2arrays, array2refees );
  }
  
  protected boolean allEntriesInAReversedInB( 
    Hashtable< TempDescriptor, Set<TempDescriptor> > a,
    Hashtable< TempDescriptor, Set<TempDescriptor> > b ) {
    
    Iterator mapItr = a.entrySet().iterator();
    while( mapItr.hasNext() ) {
      Map.Entry           me    = (Map.Entry)           mapItr.next();
      TempDescriptor      keyA  = (TempDescriptor)      me.getKey();
      Set<TempDescriptor> valsA = (Set<TempDescriptor>) me.getValue();
      
      Iterator<TempDescriptor> valItr = valsA.iterator();
      while( valItr.hasNext() ) {
        TempDescriptor valA = valItr.next();
        
        Set<TempDescriptor> valsB = b.get( valA );
        
        if( valsB == null ) {
          return false;
        }
        
        if( !valsB.contains( keyA ) ) {
          return false;
        }
      }
    }
    
    return true;
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }
    if( !(o instanceof InArrayRelation) ) {
      return false;
    }
    InArrayRelation rel = (InArrayRelation) o;
    return 
      array2refees.equals( rel.array2refees ) &&
      refee2arrays.equals( rel.refee2arrays );
  }

  public int hashCode() {
    return 
      array2refees.hashCode() +
      refee2arrays.hashCode();
  }
}
