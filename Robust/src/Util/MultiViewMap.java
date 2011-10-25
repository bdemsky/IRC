///////////////////////////////////////////////////////////
//
//  MultiViewMap is like a straight-forward multiple-key
//  map except that it supports retrieval and delete by
//  subset views of the multikey.
//
//  Ex:
//  mvm.put(<X, Y, Z>, V);
//  mvm.put(<X, W, T>, U);
//  print( mvm.get(<*, Y, *>) );  // prints "<X, Y, Z> -> V"
//  mvm.remove(<X, *, *>);        // removes both entries
//
///////////////////////////////////////////////////////////
package Util;

import java.util.Set;
import java.util.HashSet;
import java.util.BitSet;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;


public class MultiViewMap<T> {

  private Class[]        keyTypes;
  private Vector<BitSet> partialViews;
  private BitSet         fullView;

  private JoinOp<T> joinOp;

  private boolean checkTypes;
  private boolean checkConsistency;


  // for MultiViewMaps that don't need to use the value,
  // template on type Object and map every key to this dummy
  public static Object dummyValue = new Integer( -12345 );


  //  If the entire contents of this map are fullKey -> value:
  //    <a,b> -> 1
  //    <c,b> -> 2
  //    <d,e> -> 3
  private Map<MultiKey, T> fullKey2value;

  //  ...then this structure captures the partial views:
  //     view[1, 0] ->
  //       <a,*> -> {<a,b>}
  //       <c,*> -> {<c,b>}
  //       <d,*> -> {<d,e>}
  //     view[0, 1] ->
  //       <*,b> -> {<a,b>, <c,b>}
  //       <*,e> -> {<d,e>}
  private Map<BitSet, Map<MultiKey, Set<MultiKey>>>
    view2partialKey2fullKeys;


  //  NOTE!  Use MultiViewMapBuilder to get an
  //  instantiation of this class.
  protected MultiViewMap( Class[]        keyTypes,
                          JoinOp<T>      joinOp,
                          BitSet         fullView,
                          Vector<BitSet> partialViews,
                          boolean        checkTypes,
                          boolean        checkConsistency ) {

    this.keyTypes         = keyTypes;
    this.joinOp           = joinOp;
    this.partialViews     = partialViews;
    this.fullView         = fullView;
    this.checkTypes       = checkTypes;
    this.checkConsistency = checkConsistency;

    fullKey2value = new HashMap<MultiKey, T>();

    view2partialKey2fullKeys = 
      new HashMap<BitSet, Map<MultiKey, Set<MultiKey>>>(); 
  }


  public boolean equals( Object o ) {
    if( this == o ) {
      return true;
    }
    if( o == null ) {
      return false;
    }
    if( !(o instanceof MultiViewMap) ) {
      return false;
    }
    MultiViewMap that = (MultiViewMap) o;

    // check whether key types and views match
    if( !this.isHomogenous( that ) ) {
      return false;
    }
    
    // check contents
    return fullKey2value.equals( that.fullKey2value ) &&
      view2partialKey2fullKeys.equals( that.view2partialKey2fullKeys );
  }

  public int hashCode() {
    int hash = 1;
    hash = hash*31 + keyTypes.hashCode();
    hash = hash*31 + joinOp.hashCode();
    hash = hash*31 + fullView.hashCode();
    hash = hash*31 + partialViews.hashCode();
    hash = hash*31 + fullKey2value.hashCode();
    hash = hash*31 + view2partialKey2fullKeys.hashCode();
    return hash;
  }


  public int size() {
    return fullKey2value.size();
  }


  public void clear() {
    fullKey2value.clear();
    view2partialKey2fullKeys.clear();
  }

 
  public void put( MultiKey fullKey, T value ) {
    assert( typesMatch( fullKey ) );

    fullKey2value.put( fullKey, value );

    for( BitSet view : partialViews ) {
      MultiKey partialKey = makePartialKey( view, fullKey );
      getFullKeys( view, partialKey ).add( fullKey );
    }

    assert( isConsistent() );
  }


  public Map<MultiKey, T> get( final BitSet view, MultiKey partialKey ) {
    checkView( view );

    Map<MultiKey, T> fullKey2valueBYVIEW = new HashMap<MultiKey, T>();

    Set<MultiKey> fullKeys = getFullKeys( view, partialKey );
    for( MultiKey fullKey : fullKeys ) {
      fullKey2valueBYVIEW.put( fullKey, 
                               fullKey2value.get( fullKey ) );
    }

    return fullKey2valueBYVIEW;
  }

 
  public void remove( final BitSet viewForRemove, MultiKey fullOrPartialKey ) {    
    checkView( viewForRemove );

    Set<MultiKey> fullKeysToRemove = new HashSet<MultiKey>();
    
    if( viewForRemove.equals( fullView ) ) {
      fullKeysToRemove.add( fullOrPartialKey );
    } else {
      fullKeysToRemove.addAll( getFullKeys( viewForRemove, fullOrPartialKey ) );
    }

    for( MultiKey fullKeyToRemove : fullKeysToRemove ) {
      for( BitSet view : partialViews ) {
        MultiKey partialKey = makePartialKey( view, fullKeyToRemove );
        getFullKeys( view, partialKey ).remove( fullKeyToRemove );
      }
      fullKey2value.remove( fullKeyToRemove );
    }

    assert( isConsistent() );
  }
  

  public void merge( MultiViewMap<T> in ) {
    assert( isHomogenous( in ) );

    Set<MultiKey> mergedFullKeys = new HashSet<MultiKey>();
    mergedFullKeys.addAll( this.fullKey2value.keySet() );
    mergedFullKeys.addAll( in.fullKey2value.keySet() );

    for( MultiKey fullKey : mergedFullKeys ) { 
      fullKey2value.put( fullKey, 
                         joinOp.join( this.fullKey2value.get( fullKey ), 
                                      in.fullKey2value.get( fullKey )
                                      ) );
    }

    for( MultiKey fullKey : in.fullKey2value.keySet() ) { 
      for( BitSet view : partialViews ) {
        MultiKey partialKey = makePartialKey( view, fullKey );
        getFullKeys( view, partialKey ).add( fullKey );
      }
    }

    assert( isConsistent() );
  }


  private 
    Set<MultiKey> getFullKeys( BitSet   view,
                               MultiKey partialKey ) {

    Map<MultiKey, Set<MultiKey>> partialKey2fullKeys =
      getPartialKey2fullKeys( view );
    return getFullKeys( partialKey2fullKeys, partialKey );
  }


  private 
    Map<MultiKey, Set<MultiKey>> getPartialKey2fullKeys( BitSet view ) {

    Map<MultiKey, Set<MultiKey>> partialKey2fullKeys =
      view2partialKey2fullKeys.get( view );
    if( partialKey2fullKeys == null ) {
      partialKey2fullKeys = new HashMap<MultiKey, Set<MultiKey>>();
      view2partialKey2fullKeys.put( view, partialKey2fullKeys );
    }
    return partialKey2fullKeys;
  }


  private 
    Set<MultiKey> getFullKeys( Map<MultiKey, Set<MultiKey>> partialKey2fullKeys,
                               MultiKey                     partialKey ) {
    
    Set<MultiKey> fullKeys = partialKey2fullKeys.get( partialKey );
    if( fullKeys == null ) {
      fullKeys = new HashSet<MultiKey>();
      partialKey2fullKeys.put( partialKey, fullKeys );
    }    
    return fullKeys;
  }


  private MultiKey makePartialKey( BitSet view, MultiKey fullKey ) {
    Object[] partialKeys = new Object[view.cardinality()];
    int j = 0;
    for( int i = 0; i < view.size(); ++i ) {
      if( view.get( i ) ) {
        partialKeys[j] = fullKey.get( i );
        ++j;
      }
    }
    assert( j == view.cardinality() );
    return new MultiKey( partialKeys );
  }


  private void checkView( BitSet view ) {
    if( view != fullView &&
        !partialViews.contains( view ) ) {
      throw new IllegalArgumentException( "This view is not supported." );
    }
  }


  private boolean typesMatch( MultiKey multiKey ) {
    if( !checkTypes ) {
      return true;
    }

    return multiKey.typesMatch( keyTypes );
  }


  private boolean isHomogenous( MultiViewMap in ) {
    if( this.keyTypes.length != in.keyTypes.length ) {
      return false;
    }
    for( int i = 0; i < this.keyTypes.length; ++i ) {
      if( !this.keyTypes[i].equals( in.keyTypes[i] ) ) {
        return false;
      }
    }
    return 
      this.partialViews.equals( in.partialViews ) &&
      this.fullView.equals( in.fullView ) &&
      this.joinOp.equals( in.joinOp );
  }


  private boolean isConsistent() {
    if( !checkConsistency ) {
      return true;
    }

    // First, for every full key, make sure it is in every partial key
    // set it should be in.
    for( MultiKey fullKey : fullKey2value.keySet() ) {
      for( BitSet view : partialViews ) {
        MultiKey partialKey = makePartialKey( view, fullKey );
        if( !getFullKeys( view, partialKey ).contains( fullKey ) ) {
          System.out.println( "Expected full key="+fullKey+
                              " to be in view="+view+
                              " and partial key="+partialKey+
                              " but it is missing." );
          return false;
        }
      }
    }

    // Second, for each partial key set, make sure every full key is
    //   a) a match for the partial key it is filed under and
    //   b) also in the full key->value set
    for( BitSet view : partialViews ) {
      Map<MultiKey, Set<MultiKey>> partialKey2fullKeys = getPartialKey2fullKeys( view );
      for( MultiKey partialKey : partialKey2fullKeys.keySet() ) {
        Set<MultiKey> fullKeys = partialKey2fullKeys.get( partialKey );
        for( MultiKey fullKey : fullKeys ) {
          if( !partialKey.equals( makePartialKey( view, fullKey ) ) ) {
            System.out.println( "Full key="+fullKey+
                                " was filed under view="+view+
                                " and partial key="+partialKey+
                                " but the (fullKey, view)->partialKey mapping is inconsistent." );
            return false;
          }
          if( !fullKey2value.containsKey( fullKey ) ) {
            System.out.println( "Full key="+fullKey+
                                " was filed under view="+view+
                                " and partial key="+partialKey+
                                " but the fullKey is not in the fullKey->value map." );
            return false;
          }
        }
      }
    }

    return true;
  }
}
