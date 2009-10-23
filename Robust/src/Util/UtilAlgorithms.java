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

}
