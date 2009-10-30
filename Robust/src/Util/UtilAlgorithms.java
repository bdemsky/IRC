package Util;
import java.util.*;

// this class has static methods that implement useful,
// non-trivially algorithms to prevent code duplication
// and reduce bugs

public class UtilAlgorithms {

  // This method merge hashtable b into a, where the
  // tables both have HashSets as the values.  If both
  // tables have a common key, the new value is the
  // union of the sets the key mapped to in each one.
  // Note: the reason it must be a HashSet instead of
  // a Set is that we want to clone sets of table b, so
  // only a is modified.  Set does not provide clone().
  static public void mergeHashtablesWithHashSetValues( Hashtable a,
						       Hashtable b ) {
    Iterator itr = b.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry me  = (Map.Entry) itr.next();
      Object    key = (Object)    me.getKey();
      HashSet   s1  = (HashSet)   me.getValue();
      HashSet   s2  = (HashSet)   a.get( key );

      if( s2 == null ) {
	a.put( key, s1.clone() );
      } else {
	s2.addAll( s1 );
      }
    }
  }

  
  // This method makes hashtable a the intersection of
  // itself and hashtable b, where the new key set is the
  // intersection.  The values are sets, so if a key is
  // common its new value should be the intersection of
  // the existing values in a and b.  If a new value is
  // the empty set, then also remove that key.
  static public void intersectHashtablesWithSetValues( Hashtable a,
						       Hashtable b ) {
    Set keysToRemove = new HashSet();

    Iterator mapItr = a.entrySet().iterator();
    while( mapItr.hasNext() ) {
      Map.Entry me    = (Map.Entry) mapItr.next();
      Object    akey  = (Object)    me.getKey();
      Set       avals = (Set)       me.getValue();
      Set       bvals = (Set)       b.get( akey );
    
      if( bvals == null ) {
        // if b doesn't have the key, mark it for
        // safe removal after we traverse the map
        keysToRemove.add( akey );

      } else {
        // otherwise we have the key, but pare
        // down the value set, if needed, and if
        // nothing is left, remove the key, too
        avals.retainAll( bvals );
        if( avals.isEmpty() ) {
          keysToRemove.add( akey );
        }
      }
    }

    // then safely remove keys
    Iterator keyItr = keysToRemove.iterator();
    while( keyItr.hasNext() ) {
      Object key = keyItr.next();
      a.remove( key );
    }  
  }
  
}
