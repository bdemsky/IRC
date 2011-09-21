package Util.UnitTests;

import Util.*;

import java.util.Set;
import java.util.HashSet;
import java.util.BitSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.reflect.Array;


public class MultiViewMapTest {

  private static JoinOpInteger joinOp;

  private static Random random;


  private static void p() { System.out.println( "  passed" ); }
  private static void f() { System.out.println( "  !!!FAILED!!!" ); }

  private static void verify( Map<MultiKey, Integer> expected, 
                              Map<MultiKey, Integer> actual ) {
    if( expected.equals( actual ) ) { 
      p();
    } else { 
      f();
      System.out.println( "expected = "+expected );
      System.out.println( "actual   = "+actual );
    }
  }


  public static void main(String[] args) {
    
    int randomSeed = 12345;
    if( args.length > 0 ) {
      randomSeed = Integer.parseInt( args[0] );
    }
    random = new Random( randomSeed );

    joinOp = new JoinOpInteger();
    
    testBuilder();
    System.out.println("");
    testMap();
    System.out.println("");
    stressTest();
    System.out.println("");
  }


  public static void testBuilder() {
    System.out.println( "Testing MultiViewMapBuilder..." );

    MultiViewMapBuilder builder;

    try {
      builder = new MultiViewMapBuilder<Integer>( new Class[]{}, joinOp );
      f();
    } catch( IllegalArgumentException expected ) {
      p();
    } catch( Exception e ) {
      f();
    }

    try {
      builder =
        new MultiViewMapBuilder<Integer>( new Class[] 
                                          {Integer.class,
                                           Boolean.class,
                                           Integer.class,
                                           String.class},
                                          joinOp );
      p();
    } catch( Exception e ) {
      f();
    }

    
    builder =
      new MultiViewMapBuilder<Integer>( new Class[] 
                                        {Integer.class,
                                         Boolean.class,
                                         Integer.class,
                                         String.class},
                                        joinOp );

    try {
      // can't build a map with no views yet
      builder.build();
      f();
    } catch( Exception expected ) {
      p();   
    }

    try {
      builder.addPartialView();
      f();
    } catch( IllegalArgumentException expected ) {
      p();   
    } catch( Exception e ) {
      f();
    }

    try {
      // an index is out of bounds for this multikey (0-3)
      builder.addPartialView( 1, 6 );
      f();
    } catch( IllegalArgumentException expected ) {
      p();   
    } catch( Exception e ) {
      f();
    }

    try {
      // an index is out of bounds for this multikey (0-3)
      builder.addPartialView( 2, -1, 3 );
      f();
    } catch( IllegalArgumentException expected ) {
      p();   
    } catch( Exception e ) {
      f();
    }

    try {
      // an index is specified more than once
      builder.addPartialView( 0, 3, 0 );
      f();
    } catch( IllegalArgumentException expected ) {
      p();   
    } catch( Exception e ) {
      f();
    }

    try {
      // the full view is implicit
      builder.addPartialView( 0, 1, 2, 3 );
      f();
    } catch( IllegalArgumentException expected ) {
      p();   
    } catch( Exception e ) {
      f();
    }

    try {
      builder.addPartialView( 1, 3 );
      p();
    } catch( Exception e ) {
      f();
    }

    try {
      builder.addPartialView( 0, 1, 3 );
      p();
    } catch( Exception e ) {
      f();
    }

    try {
      // duplicate view
      builder.addPartialView( 1, 3 );
      f();
    } catch( IllegalArgumentException expected ) {
      p();   
    } catch( Exception e ) {
      f();
    }
    System.out.println( "DONE" );
  }


  public static void testMap() {    
    System.out.println( "Testing MultiViewMap..." );
    
    Map<MultiKey, Integer> expected;

    MultiViewMapBuilder<Integer> builder =
      new MultiViewMapBuilder<Integer>( new Class[] 
                                        {Integer.class,   // key0
                                         Boolean.class,   // key1
                                         String.class},   // key2
                                        joinOp );
    final BitSet view012 = builder.getFullView();
    final BitSet view01  = builder.addPartialView( 0, 1 );
    final BitSet view0   = builder.addPartialView( 0 );
    final BitSet view2   = builder.addPartialView( 2 );
    builder.setCheckTypes( true );
    builder.setCheckConsistency( true );

    MultiViewMap<Integer> mapA = builder.build();


    // Simple put and remove
    MultiKey partialKey4 = MultiKey.factory( 4 );
    expected = new HashMap<MultiKey, Integer>();
    verify( expected, mapA.get( view0, partialKey4 ) );

    MultiKey vader = MultiKey.factory( 4, true, "Vader" );
    mapA.put( vader, 1001 );
    expected = new HashMap<MultiKey, Integer>();
    expected.put( vader, 1001 );
    verify( expected, mapA.get( view0, partialKey4 ) );

    mapA.remove( view0, partialKey4 );
    expected = new HashMap<MultiKey, Integer>();
    verify( expected, mapA.get( view0, partialKey4 ) );

    
    // Try across a merge
    mapA.put( vader, 1001 );
    expected = new HashMap<MultiKey, Integer>();
    expected.put( vader, 1001 );
    verify( expected, mapA.get( view0, partialKey4 ) );

    MultiViewMap<Integer> mapB = builder.build();
    expected = new HashMap<MultiKey, Integer>();
    verify( expected, mapB.get( view0, partialKey4 ) );

    mapB.merge( mapA );
    expected = new HashMap<MultiKey, Integer>();
    expected.put( vader, 1001 );
    verify( expected, mapB.get( view0, partialKey4 ) );

    
    // Remove the correct entries
    MultiKey luke = MultiKey.factory( 5, true,  "Luke" );
    MultiKey han  = MultiKey.factory( 4, true,  "Han Solo" );
    MultiKey r2   = MultiKey.factory( 4, false, "R2-D2" );

    mapA.put( luke, 1002 );
    mapA.put( han,  1003 );
    mapA.put( r2,   1004 );
    expected = new HashMap<MultiKey, Integer>();
    expected.put( vader, 1001 );
    expected.put( han,   1003 );
    expected.put( r2,    1004 );
    verify( expected, mapA.get( view0, partialKey4 ) );

    // removes vader and han
    MultiKey partialKey4true = MultiKey.factory( 4, true );
    mapA.remove( view01, partialKey4true );
    
    expected = new HashMap<MultiKey, Integer>();
    expected.put( r2, 1004 );
    verify( expected, mapA.get( view0, partialKey4 ) );

    MultiKey partialKeyLuke = MultiKey.factory( "Luke" );
    expected = new HashMap<MultiKey, Integer>();
    expected.put( luke, 1002 );
    verify( expected, mapA.get( view2, partialKeyLuke ) );


    System.out.println( "DONE" );
  }


  public static void stressTest() {
    System.out.println( "Stressing MultiViewMap..." );

    // Just pound away with operations to see if we can crash anything.

    MultiViewMapBuilder<Integer> builder =
      new MultiViewMapBuilder<Integer>( new Class[] 
                                        {Integer.class,   // key0
                                         Boolean.class,   // key1
                                         String.class},   // key2
                                        joinOp );
    builder.setCheckTypes( true );
    builder.setCheckConsistency( true );

    Integer[] ints = new Integer[] {
      1, 2, 3, 4, 5, 6, 7,
    };

    Boolean[] bools = new Boolean[] {
      true, false, false, // skew distribution
    };

    String[] strs = new String[] {
      "Vader", "Han Solo", "R2-D2", "Luke", "Leia",
    };

    final BitSet view012 = builder.getFullView();
    final BitSet view01  = builder.addPartialView( 0, 1 );
    final BitSet view12  = builder.addPartialView( 1, 2 );
    final BitSet view02  = builder.addPartialView( 0, 2 );
    final BitSet view0   = builder.addPartialView( 0 );
    final BitSet view1   = builder.addPartialView( 1 );
    final BitSet view2   = builder.addPartialView( 2 );

    // This might be the ugliest line of Java I've ever written.  BARF
    @SuppressWarnings({"unchecked"})
    MultiViewMap<Integer>[] maps = 
      (MultiViewMap<Integer>[]) Array.newInstance( builder.build().getClass(), 8 );

    for( int i = 0; i < maps.length; ++i ) {
      maps[i] = builder.build();
    }


    System.out.println( "    Number of full keys in each table per op cycle:" );
    
    for( int reps = 0; reps < 100; ++reps ) {
      int nextOp = random.nextInt( 100 );

      System.out.print( "    Op: " );

      if( nextOp < 15 ) {
        // put some new values in
        System.out.print( "PT  " );
        int numNewValues = 1 + random.nextInt( 8 );
        for( int i = 0; i < numNewValues; ++i ) {
          MultiKey newKey = MultiKey.factory( getInt( ints ),
                                              getBool( bools ),
                                              getStr( strs ) );
          getMap( maps ).put( newKey, random.nextInt() );
        }

      } else if( nextOp < 70 ) {
        // remove values by a random view
        System.out.print( "RM  " );
        MultiViewMap<Integer> map = getMap( maps );

        switch( random.nextInt( 6 ) ) {
        case 0: { map.remove( view0,  MultiKey.factory( getInt (ints ) ) ); } break;
        case 1: { map.remove( view1,  MultiKey.factory( getBool(bools) ) ); } break;
        case 2: { map.remove( view2,  MultiKey.factory( getStr (strs ) ) ); } break;
        case 3: { map.remove( view01, MultiKey.factory( getInt (ints ), getBool(bools) ) ); } break;
        case 4: { map.remove( view12, MultiKey.factory( getBool(bools), getStr (strs ) ) ); } break;
        case 5: { map.remove( view02, MultiKey.factory( getInt (ints ), getStr (strs ) ) ); } break;
        }

      } else {
        // merge two tables
        System.out.print( "MG  " );
        getMap( maps ).merge( getMap( maps ) );
      }   

      for( int i = 0; i < maps.length - 1; ++i ) {
        if( i < maps.length - 1 ) {
          System.out.print( maps[i].size() + ", " );
        }
      }
      System.out.println( maps[maps.length-1].size() );
    }

    System.out.println( "DONE" );
  }

  private static Integer getInt( Integer[] ints ) {
    return ints[random.nextInt( ints.length )];
  }

  private static Boolean getBool( Boolean[] bools ) {
    return bools[random.nextInt( bools.length )];
  }

  private static String getStr( String[] strs ) {
    return strs[random.nextInt( strs.length )];
  }

  private static MultiViewMap<Integer> getMap( MultiViewMap<Integer>[] maps ) {
    return maps[random.nextInt( maps.length )];
  }
}
