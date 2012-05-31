//import java.io.*;
//import java.util.*;

public class OoOJavaExample {
  
  public static void main( String[] args ) {
    OoOJavaExample ex = new OoOJavaExample( 1234, 100, 0.05f, 15 );
    ex.runExample();
  }

  private HashSet roots;
  //private HashMap bin2set;

  public OoOJavaExample( int seed, int numTrees, float probNull, int maxTreeDepth ) {
    Random random = new Random( seed );

    roots = new HashSet();
    for( int i = 0; i < numTrees; ++i ) {
      roots.add( makeNewTree( random, probNull, maxTreeDepth ) );
    }

    //bin2set = new HashMap();
  }

  private TreeNode makeNewTree( Random random, float probNull, int maxTreeDepth ) {

    TreeNode root = new TreeNode( random.nextFloat() );

    TreeNode left = buildSubTree( random, probNull, maxTreeDepth, 0 ); 
    root.left = left;
    
    TreeNode right = buildSubTree( random, probNull, maxTreeDepth, 0 ); 
    root.right = right;
    
    return root;
  }


  private TreeNode buildSubTree( Random random, float probNull, int maxTreeDepth, int depth ) {

    TreeNode node = new TreeNode( random.nextFloat() );
    if( depth > maxTreeDepth || random.nextFloat() < probNull ) {
      genreach retearly;
      return node;
    }

    TreeNode left = buildSubTree( random, probNull, maxTreeDepth, depth + 1 );
    node.left = left;
    
    TreeNode right = buildSubTree( random, probNull, maxTreeDepth, depth + 1 );
    node.right = right;

    genreach retlate;
    return node;
  }

  public void runExample() {
    Iterator itr = roots.iterator();

    int zzz = 0;
      
    while( itr.hasNext() ) {
      TreeNode root = (TreeNode) itr.next();
  
      sese par {
        int weightBin = (int) root.computeTreeWeight();
      }
      sese seq {
        zzz += weightBin;
        /*
        HashSet set = (HashSet)bin2set.get( weightBin );
        if( set == null ) {
          set = new HashSet();
          bin2set.put( weightBin, set );
        }
        set.add( root );
        */
      }
    }

    System.out.println( "Num weight bins: "+zzz ); //bin2set.size() );
  }
}
