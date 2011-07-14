package MTree;

public class TestRunner extends Thread {

  int m_index;
  int m_size;
  int m_nodenum;
  Node m_tree; // The root of a BST

  public TestRunner(int index,
      int size,
      int nodenum) {
    this.m_index = index;
    this.m_size = size;
    this.m_nodenum = nodenum;
    this.m_tree = new Node();
  }

  public void run() {
    // Randomly generate new (key, value) pair and insert into the tree
    // If have collision, simply throw away the old node
    // The tree can hold m_size nodes at most, if it has reached the 
    // limitation of m_size, then replace the node whose key is the 
    // closest to the new key.
    Random rand = new Random(m_index);
    while(this.m_nodenum-- > 0) {
      // Generate a new (key, value) pair
      int key = Math.abs(rand.nextInt());
      int value = Math.abs(rand.nextInt());
      if(this.m_tree.insert(key, value, !(this.m_size > 0))) {
        // insert a new node
        this.m_size--;
      }
    }
  }
  
  public static void main(String[] args) {
    int threadnum = 62; // 56;
    int size = 40000;
    int nodenum = size*10;
    for(int i = 0; i < threadnum; ++i) {
      TestRunner tr = new TestRunner(i, size, nodenum);
      tr.start();
    }
  }
}
