
//import java.util.Random;

/**
 * A class that represents a node in a binary tree.  Each node represents
 * a city in the TSP benchmark.
 **/
final class Tree_tsp
{
  /**
   * The number of nodes (cities) in this subtree
   **/
  private int sz;
  /**
   * The coordinates that this node represents
   **/
  private float x,y;
  /**
   * Left child of the tree
   **/
  private Tree_tsp  left;
  /**
   * Right child of tree
   **/
  private Tree_tsp  right;
  /**
   * The next pointer in a linked list of nodes in the subtree.  The list
   * contains the order of the cities to visit.
   **/
  private Tree_tsp  next;
  /**
   * The previous pointer in a linked list of nodes in the subtree. The list
   * contains the order of the cities to visit.
   **/
  private Tree_tsp  prev;

  // used by the random number generator
  //private static final float  M_E2; //  = 7.3890560989306502274;
  //private static final float  M_E3; //  = 20.08553692318766774179;
  //private static final float  M_E6; //  = 403.42879349273512264299;
  //private static final float  M_E12; // = 162754.79141900392083592475;

  /**
   * Construct a Tree node (a city) with the specified size
   * @param size the number of nodes in the (sub)tree
   * @param x the x coordinate of the city
   * @param y the y coordinate of the city
   * @param left the left subtree
   * @param right the right subtree
   **/
  Tree_tsp(int size, float x, float y, Tree_tsp l, Tree_tsp r)
  {
    /*M_E2  = 7.3890560989306502274f;
    M_E3  = 20.08553692318766774179f;
    M_E6  = 403.42879349273512264299f;
    M_E12 = 162754.79141900392083592475f;*/
    
    sz = size;
    this.x = x;
    this.y = y;
    left = l;
    right = r;
    next = null;
    prev = null;
  }

  /**
   * Find Euclidean distance between this node and the specified node.
   * @param b the specified node
   * @return the Euclidean distance between two tree nodes.
   **/
  float distance(Tree_tsp b) 
  {
    return (Math.sqrtf((float)((x-b.x)*(x-b.x)+(y-b.y)*(y-b.y))));
  }

  /**
   * Create a list of tree nodes.  Requires root to be the tail of the list.
   * Only fills in next field, not prev.
   * @return the linked list of nodes
   **/
  Tree_tsp makeList() 
  {
    Tree_tsp myleft, myright;
    Tree_tsp tleft,tright;
    Tree_tsp retval = this;

    // head of left list
    if (left != null) 
      myleft = left.makeList();
    else
      myleft = null;

    // head of right list
    if (right != null)
      myright = right.makeList();
    else
      myright = null;

    if (myright != null) { 
      retval = myright; 
      right.next = this;
    }

    if (myleft != null) { 
      retval = myleft; 
      if (myright != null)
        left.next = myright;
      else
        left.next = this;
    }	
    next = null;

    return retval;
  }

  /**
   * Reverse the linked list.  Assumes that there is a dummy "prev"
   * node at the beginning.
   **/
  void reverse() 
  {
    Tree_tsp prev = this.prev;
    prev.next = null;
    this.prev = null;
    Tree_tsp back = this;
    Tree_tsp tmp = this;
    // reverse the list for the other nodes
    Tree_tsp next;
    for (Tree_tsp t = this.next; t != null; back = t, t = next) {
      next = t.next;
      t.next = back;
      back.prev = t;
    }
    // reverse the list for this node
    tmp.next = prev;
    prev.prev = tmp;
  }

  /** 
   * Use closest-point heuristic from Cormen, Leiserson, and Rivest.
   * @return a 
   **/
  Tree_tsp conquer() 
  {
    // create the list of nodes
    Tree_tsp t = makeList();

    // Create initial cycle 
    Tree_tsp cycle = t;
    t = t.next;
    cycle.next = cycle;
    cycle.prev = cycle;

    // Loop over remaining points 
    Tree_tsp donext;
    for (; t != null; t = donext) {
      donext = t.next; /* value won't be around later */
      Tree_tsp min = cycle;
      float mindist = t.distance(cycle);
      for (Tree_tsp tmp = cycle.next; tmp != cycle; tmp=tmp.next) {
        float test = tmp.distance(t);
        if (test < mindist) {
          mindist = test;
          min = tmp;
        } /* if */
      } /* for tmp... */

      Tree_tsp next = min.next;
      Tree_tsp prev = min.prev;

      float mintonext = min.distance(next);
      float mintoprev = min.distance(prev);
      float ttonext = t.distance(next);
      float ttoprev = t.distance(prev);

      if ((float)(ttoprev - mintoprev) < (float)(ttonext - mintonext)) {
        /* insert between min and prev */
        prev.next = t;
        t.next = min;
        t.prev = prev;
        min.prev = t;
      } else {
        next.prev = t;
        t.next = next;
        min.next = t;
        t.prev = min;
      }
    } /* for t... */

    return cycle;
  }

  /** 
   * Merge two cycles as per Karp.
   * @param a a subtree with one cycle
   * @param b a subtree with the other cycle
   **/
  Tree_tsp merge(Tree_tsp a, Tree_tsp b) 
  {
    // Compute location for first cycle
    Tree_tsp   min = a;
    float mindist = distance(a);
    Tree_tsp   tmp = a;
    for (a = a.next; a != tmp; a = a.next) {
      float test = distance(a);
      if (test < mindist) {
        mindist = test;
        min = a;
      }
    }

    Tree_tsp next = min.next;
    Tree_tsp prev = min.prev;
    float mintonext = min.distance(next);
    float mintoprev = min.distance(prev);
    float ttonext   = distance(next);
    float ttoprev   = distance(prev);

    Tree_tsp p1, n1;
    float tton1, ttop1;
    if ((ttoprev - mintoprev) < (ttonext - mintonext)) {
      // would insert between min and prev
      p1 = prev;
      n1 = min;
      tton1 = mindist;
      ttop1 = ttoprev;
    } else { 
      // would insert between min and next
      p1 = min;
      n1 = next;
      ttop1 = mindist;
      tton1 = ttonext;
    }

    // Compute location for second cycle
    min = b;
    mindist = distance(b);
    tmp = b;
    for (b = b.next; b != tmp; b = b.next) {
      float test = distance(b);
      if (test < mindist) {
        mindist = test;
        min = b;
      }
    }

    next = min.next;
    prev = min.prev;
    mintonext = min.distance(next);
    mintoprev = min.distance(prev);
    ttonext = this.distance(next);
    ttoprev = this.distance(prev);

    Tree_tsp p2, n2;
    float tton2, ttop2;
    if ((ttoprev - mintoprev) < (ttonext - mintonext)) {
      // would insert between min and prev
      p2 = prev;
      n2 = min;
      tton2 = mindist;
      ttop2 = ttoprev;
    } else { 
      // would insert between min andn ext 
      p2 = min;
      n2 = next;
      ttop2 = mindist;
      tton2 = ttonext;
    }

    // Now we have 4 choices to complete:
    // 1:t,p1 t,p2 n1,n2
    // 2:t,p1 t,n2 n1,p2
    // 3:t,n1 t,p2 p1,n2
    // 4:t,n1 t,n2 p1,p2 
    float n1ton2 = n1.distance(n2);
    float n1top2 = n1.distance(p2);
    float p1ton2 = p1.distance(n2);
    float p1top2 = p1.distance(p2);

    mindist = (float)(ttop1 + ttop2 + n1ton2); 
    int choice = 1;

    float test = (float)(ttop1 + tton2 + n1top2);
    if (test < mindist) {
      choice = 2;
      mindist = test;
    }

    test = tton1 + ttop2 + p1ton2;
    if (test < mindist) {
      choice = 3;
      mindist = test;
    }

    test = tton1 + tton2 + p1top2;
    if (test < mindist) 
      choice = 4;

    if (choice == 1) {
    //case 1:
      // 1:p1,this this,p2 n2,n1 -- reverse 2!
      n2.reverse();
      p1.next = this;
      this.prev = p1;
      this.next = p2;
      p2.prev = this;
      n2.next = n1;
      n1.prev = n2;
      //break;
    } else if(choice == 2) {
    //case 2:
      // 2:p1,this this,n2 p2,n1 -- OK
      p1.next = this;
      this.prev = p1;
      this.next = n2;
      n2.prev = this;
      p2.next = n1;
      n1.prev = p2;
      //break;
    } else if(choice == 3) {
    //case 3:
      // 3:p2,this this,n1 p1,n2 -- OK
      p2.next = this;
      this.prev = p2;
      this.next = n1;
      n1.prev = this;
      p1.next = n2;
      n2.prev = p1;
      //break;
    } else if(choice == 4) {
    //case 4:
      // 4:n1,this this,n2 p2,p1 -- reverse 1!
      n1.reverse();
      n1.next = this;
      this.prev = n1;
      this.next = n2;
      n2.prev = this;
      p2.next = p1;
      p1.prev = p2;
      //break;
    }
    return this;
  }

  /**
   * Compute TSP for the tree t. Use conquer for problems <= sz 
   * @param sz the cutoff point for using conquer vs. merge
   **/
  Tree_tsp tsp(int sz) 
  {
    if (this.sz <= sz) return conquer();

    Tree_tsp leftval  = left.tsp(sz);
    Tree_tsp rightval = right.tsp(sz); 

    return merge(leftval, rightval);
  }

  /**
   * Print the list of cities to visit from the current node.  The
   * list is the result of computing the TSP problem.
   * The list for the root node (city) should contain every other node
   * (city).
   **/
  /*void printVisitOrder()
  {
    System.out.println("x = " + x + " y = " + y);
    for (Tree_tsp tmp = next; tmp != this; tmp = tmp.next) {
      System.out.println("x = " + tmp.x + " y = " + tmp.y);
    }
  }*/

  //
  // static methods
  //

  /**
   * Return an estimate of median of n values distributed in [min,max)
   * @param min the minimum value 
   * @param max the maximum value
   * @param n 
   * @return an estimate of median of n values distributed in [min,max)
   **/
  private static float median(float min, float max, int n) 
  {
    float M_E2  = 7.3890560989306502274f;
    float M_E3  = 20.08553692318766774179f;
    float M_E6  = 403.42879349273512264299f;
    float M_E12 = 162754.79141900392083592475f;
    
    // get random value in [0.0, 1.0)
    float t = (new Random()).nextFloat();

    float retval;
    if (t > 0.5) {
      retval = /*java.lang.*/(float)(Math.log(1.0-(2.0*(M_E12-1)*(t-0.5)/M_E12))/12.0f);   
    } else {
      retval = (float)(-/*java.lang.*/Math.log(1.0-(2.0*(M_E12-1)*t/M_E12))/12.0f);
    }
    // We now have something distributed on (-1.0,1.0)
    retval = (float)((retval+1.0f) * (max-min)/2.0f);
    retval = retval + min;
    return retval;
  }

  /**
   * Get float uniformly distributed over [min,max) 
   * @return float uniformily distributed over [min,max)
   **/
  private static float uniform(float min, float max) 
  {
    // get random value between [0.0,1.0)
    float retval = (new Random()).nextFloat(); 
    retval = retval * (max-min);
    return retval + min;
  }

  /**
   * Builds a 2D tree of n nodes in specified range with dir as primary 
   * axis (false for x, true for y)
   *
   * @param n the size of the subtree to create
   * @param dir the primary axis
   * @param min_x the minimum x coordinate
   * @param max_x the maximum x coordinate
   * @param min_y the minimum y coordinate
   * @param max_y the maximum y coordinate
   * @return a reference to the root of the subtree
   **/
  public static Tree_tsp buildTree(int n, boolean dir, float min_x,
      float max_x, float min_y, float max_y) 
  {
    if (n==0) return null;

    Tree_tsp left, right;
    float x, y;
    if (dir) {
      dir = !dir;
      float med = median(min_x,max_x,n);
      left = buildTree(n/2, dir, min_x, med, min_y, max_y);
      right = buildTree(n/2, dir, med, max_x, min_y, max_y);
      x = med;
      y = uniform(min_y, max_y);
    } else {
      dir = !dir;
      float med = median(min_y,max_y,n);
      left = buildTree(n/2, dir, min_x, max_x, min_y, med);
      right = buildTree(n/2, dir, min_x, max_x, med, max_y);
      y = med;
      x = uniform(min_x, max_x);
    }
    return new Tree_tsp(n, x, y, left, right);
  }

}
