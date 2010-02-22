/**
 ** A single node in the search graph
 ** Has same dimensions as the Map where we are searching
 **/
private class Node {
  /** The x coordinate of the node */
  private int x;
  /** The y coordinate of the node */
  private int y;
  /** The path cost for this node */
  private int cost;
  /** The parent of this node, how we reached it in the search */
  private Node parent;
  /** The heuristic cost of this node */
  private int heuristic;
  /** The search depth of this node */
  private int depth;

  /**
   **Create a new node
   ** 
   ** @param x The x coordinate of the node
   ** @param y The y coordinate of the node
   **/
  public Node(int x, int y) {
    this.x = x;
    this.y = y;
  }

  /**
   ** Set the parent of this node
   ** 
   ** @param parent The parent node which lead us to this node
   ** @return The depth we have no reached in searching
   **/
  public int setParent(Node parent) {
    depth = parent.depth + 1;
    this.parent = parent;

    return depth;
  }

  /**
   ** compareTo(Object)
   **/
  public int compareTo(Object other) {
    Node o = (Node) other;

    int f = heuristic + cost;
    int of = o.heuristic + o.cost;

    if (f < of) {
      return -1;
    } else if (f > of) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   ** @return The cost of the heuristic
   **/
  public int getHeuristic() {
    return heuristic;
  }


  /**
   ** @return The actual cost of traversal 
   **/
  public int getCost() {
    return cost;
  }

  /**
   ** Only for Debugging by printing contents of Node
   **/
  public void debugNode() {
    System.println("x= "+ x + " y= "+ y + " cost= " + cost + " heuristic= "+ heuristic + " depth= " + depth);
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }
}

/**
 ** A simple sorted list
 **
 **/
class SortedList {
  /** The list of elements */
  private Vector list;

  public SortedList() {
    list = new Vector();
  }
  /**
   ** Retrieve the first element from the list
   **  
   ** @return The first element from the list
   **/
  public Object first() {
    Object o = list.elementAt(0);
    return o;
  }

  /**
   ** Empty the list
   **/
  public void clear() {
    list.clear();
  }

  /**
   **Add an element to the list - causes sorting
   ** 
   ** @param o The element to add
   **/
  public void add(Object o) {
    list.addElement(o);
    Node tmp = (Node) o;
    int min = tmp.heuristic + tmp.cost;
    int i;
    int index = 0;
    /* Move the Node with minimum cost to the first position */
    for(i = 0; i < list.size(); i++) {
      if(min > totalCost(list.elementAt(i))) {
        min = totalCost(list.elementAt(i));
        index = i;
      }
    }

    if(index < 0 || index >=list.size()) {
      System.printString("Illegal SortedList.add\n");
      System.exit(-1);
      return;
    }

    Object temp = list.elementAt(0);
    list.setElementAt(list.elementAt(index),0);
    list.setElementAt(temp, index);
  }

  /**
   **@return fixed cost + heuristic cost
   **/
  public int totalCost(Object o) {
    Node tmp = (Node) o;
    int cost = tmp.getHeuristic() + tmp.getCost();
    return cost;
  }


  /**
   ** Remove an element from the list
   ** 
   ** @param o The element to remove
   **/
  public void remove(Object o) {
    list.remove(o);
  }

  /**
   ** Get the number of elements in the list
   ** 
   ** @return The number of element in the list
   **/
  public int size() {
    return list.size();
  }

  /**
   ** Check if an element is in the list
   ** 
   ** @param o The element to search for
   ** @return True if the element is in the list
   **/
  public boolean contains(Object o) {
    return list.contains(o);
  }

  /**
   ** Only for Debugging by printing contents of openlist
   **/
  public void debugOpenList() {
    for(int i=0; i<list.size(); i++) {
      Node n = (Node) list.elementAt(i);
      System.print("Element "+i+": n.getX()= "+n.getX()+" n.getY()= "+n.getY()+ "\n");
    }
    System.printString("\n");
  }
}
