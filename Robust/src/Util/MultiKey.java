////////////////////////////////////////////
//
//  A MultiKey is a vector of Objects that
//  serves as a key.  One MultiKey is equal
//  to another if they have the same number
//  of Objects, and Objects in corresponding
//  positions are equal.
//
////////////////////////////////////////////
package Util;


public class MultiKey {

  static public MultiKey factory( Object... keys ) {
    return new MultiKey( keys );
  }


  private Object[] keys;

  public MultiKey( Object[] keys ) {
    if( keys.length == 0 ) {
      throw new IllegalArgumentException( "A MultiKey must have at least one key." );
    }
    for( int i = 0; i < keys.length; ++i ) {
      if( keys[i] == null ) {
        throw new IllegalArgumentException( "A MultiKey cannot have null elements." );
      }
    }
    this.keys = keys.clone();
  }

  public boolean typesMatch( Class[] keyTypes ) {
    if( keys.length != keyTypes.length ) {
      return false;
    }
    for( int i = 0; i < keys.length; ++i ) {
      if( !(keyTypes[i].isInstance( keys[i] )) ) {
        return false;
      }
    }
    return true;
  }

  public Object get( int index ) {
    if( index < 0 || index >= keys.length ) {
      throw new IllegalArgumentException( "Index "+index+" is out of range for this MultiKey." );
    }
    return keys[index];
  }

  public boolean equals( Object o ) {
    if( this == o ) {
      return true;
    }
    if( o == null ) {
      return false;
    }
    if( !(o instanceof MultiKey) ) {
      return false;
    }
    MultiKey mk = (MultiKey) o;
    if( this.keys.length != mk.keys.length ) {
      return false;
    }
    for( int i = 0; i < keys.length; ++i ) {
      if( !this.keys[i].equals( mk.keys[i] ) ) {
        return false;
      }
    }
    return true;
  }

  public int hashCode() {
    int hash = 1;
    for( Object key : keys ) {
      hash = hash*31 + key.hashCode();
    }
    return hash;
  }

  public String toString() {
    StringBuilder s = new StringBuilder( "MK[" );
    for( int i = 0; i < keys.length; ++i ) {
      s.append( keys[i].toString() );
      if( i < keys.length - 1 ) {
        s.append( ", " );
      }
    }
    s.append( "]" );
    return s.toString();
  }
}
