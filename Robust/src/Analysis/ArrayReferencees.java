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
import IR.Descriptor;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.TaskDescriptor;
import IR.TypeDescriptor;
import IR.Flat.TempDescriptor;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatElementNode;
import IR.Flat.FlatSetElementNode;
import IR.Flat.FKind;
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

  public boolean doesNotCreateNewReaching( FlatSetElementNode fsen ) {
    return noNewReaching.contains( fsen );
  }
  ////////////////////////////////
  // end public interface
  ////////////////////////////////



  protected State state;

  // maintain the relation at every program point
  protected Hashtable<FlatNode, InArrayRelation> fn2rel;

  // use relation to calculate set of array populate
  // nodes that cannot create new reachability paths
  protected Set<FlatSetElementNode> noNewReaching;


  protected ArrayReferencees() {}

  protected void init( State state ) {
    this.state = state;
    fn2rel = new Hashtable<FlatNode, InArrayRelation>();
    noNewReaching = new HashSet<FlatSetElementNode>();
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

      analyzeFlatNode( fn, rel );

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

      // use relation result coming into this program
      // point ("rel" before we modify it) to compute
      // whether this node affects reachability paths
      if( rel.canArrayAlreadyReach( lhs, rhs ) ) {
        noNewReaching.add( fsen );

      } else {
        // otherwise we can't be sure, so remove
        noNewReaching.remove( fsen );
      }

      // then update the relation for the fixed-point
      // analysis to continue
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

    public boolean canArrayAlreadyReach( TempDescriptor array, 
                                         TempDescriptor elem ) {
      
      Set<TempDescriptor> refees = array2refees.get( array );
      return refees != null && refees.contains( elem );
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
      if( arrays == null ) {
        arrays = new HashSet<TempDescriptor>();
      }
      arrays.add( array );
      refee2arrays.put( refee, arrays );

      assertConsistent();
    }

    public void remove( TempDescriptor td ) {
      // removal of one temp from the relation is a bit
      // tricky--it can be on either side of the pair or
      // both at the same time

      // during traversal, mark keys that should be removed
      Set<TempDescriptor> a2rKeysToRemove = new HashSet<TempDescriptor>();
      Set<TempDescriptor> r2aKeysToRemove = new HashSet<TempDescriptor>();

      // also during traversal, mark sets by how they 
      // should be shortened
      Hashtable<Set, Set> set2removals = new Hashtable<Set, Set>();

      {
        // first consider one side of the relation
        Set<TempDescriptor> refees = array2refees.get( td );
        if( refees != null ) {
          assert !refees.isEmpty();
      
          // definitely remove the key from this mapping
          a2rKeysToRemove.add( td );
      
          // and remove it from set values in the other mapping
          Iterator<TempDescriptor> refItr = refees.iterator();
          while( refItr.hasNext() ) {
            TempDescriptor ref = refItr.next();
            
            Set<TempDescriptor> arrays = refee2arrays.get( ref );
            assert arrays != null;
            assert !arrays.isEmpty();
            
            Set<TempDescriptor> removals = set2removals.get( arrays );
            if( removals == null ) {
              removals = new HashSet<TempDescriptor>();
            }
            removals.add( td );
            set2removals.put( arrays, removals );
            
            if( removals.size() == arrays.size() ) {
              // we've marked everything in this for removal! So
              // just remove the key from the mapping
              assert arrays.containsAll( removals );
              r2aKeysToRemove.add( ref );
            }
          }
        }
      }

      {
        // and then see if it is in the relation's other direction
        Set<TempDescriptor> arrays = refee2arrays.get( td );
        if( arrays != null ) {
          assert !arrays.isEmpty();
          
          // definitely remove the key from this mapping
          r2aKeysToRemove.add( td );
          
          // and remove it from set values in the other mapping
          Iterator<TempDescriptor> arrItr = arrays.iterator();
          while( arrItr.hasNext() ) {
            TempDescriptor arr = arrItr.next();
            
            Set<TempDescriptor> refees = array2refees.get( arr );
            assert refees != null;
            assert !refees.isEmpty();

            Set<TempDescriptor> removals = set2removals.get( refees );
            if( removals == null ) {
              removals = new HashSet<TempDescriptor>();
            }
            removals.add( td );
            set2removals.put( refees, removals );

            if( removals.size() == refees.size() ) {
              // we've marked everything in this for removal! So
              // just remove the key from the mapping
              assert refees.containsAll( removals );
              a2rKeysToRemove.add( arr );
            }
          }
        }
      }
    
      // perform all marked removing now
      Iterator<TempDescriptor> keyItr;
      
      keyItr = a2rKeysToRemove.iterator();
      while( keyItr.hasNext() ) {
        array2refees.remove( keyItr.next() );
      }

      keyItr = r2aKeysToRemove.iterator();
      while( keyItr.hasNext() ) {
        refee2arrays.remove( keyItr.next() );
      }

      Iterator meItr = set2removals.entrySet().iterator();
      while( meItr.hasNext() ) {
        Map.Entry me       = (Map.Entry) meItr.next();
        Set       set      = (Set)       me.getKey();
        Set       removals = (Set)       me.getValue();

        set.removeAll( removals );
      }


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
}
