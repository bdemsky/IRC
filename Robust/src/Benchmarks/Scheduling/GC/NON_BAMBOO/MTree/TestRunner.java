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
  
  class Node {
    int m_key;
    int m_value;
    Node m_left;
    Node m_right;
    Node m_parent;

    public Node() {
      // an empty node
      this.m_key = -1;
      this.m_value = -1;
      this.m_left = null;
      this.m_right = null;
      this.m_parent = null;
    }

    public Node(int key,
        int value) {
      this.m_key = key;
      this.m_value = value;
      this.m_left = null;
      this.m_right = null;
      this.m_parent = null;
    }

    public int getKey() {
      return this.m_key;
    }

    public void setParent(Node p) {
      this.m_parent = p;
    }

    public void setLeftChild(int key,
        int value) {
      Node n = new Node(key, value);
      this.m_left = n;
      n.setParent(this);
    }

    public void setRightChild(int key,
        int value) {
      Node n = new Node(key, value);
      this.m_right = n;
      n.setParent(this);
    }

    public boolean insert(int key,
        int value,
        boolean candelete) {
      if(this.m_key == -1) {
        // empty tree
        this.m_key = key;
        this.m_value = value;
      } else {
        if(this.m_key == key) {
          // collision
          replace(key, value);
          return false;
        } else if(this.m_key > key) {
          if(this.m_left == null) {
            // no left subtree
            if(candelete) {
              // replace this node with the new node
              replace(key, value);
              return false;
            } else {
              setLeftChild(key, value);
            }
          } else {
            // insert into the left subtree
            return this.m_left.insert(key, value, candelete);
          }
        } else if(this.m_key < key) {
          if(this.m_right == null) {
            // no right subtree
            if(candelete) {
              replace(key, value);
              return false;
            } else {
              setRightChild(key, value);
            }
          } else {
            // insert into the right subtree
            return this.m_right.insert(key, value, candelete);
          }
        }
      }
      return true;
    }

    public void replace(int key,
        int value) {
      Node n = new Node(key, value);
      n.m_left = this.m_left;
      n.m_right = this.m_right;
      n.m_parent = this.m_parent;
      if(this.m_parent != null) {
        if(this.m_parent.getKey() > key) {
          this.m_parent.m_left = n;
        } else {
          this.m_parent.m_right = n;
        }
      }
      if(this.m_left != null) {
        this.m_left.m_parent = n;
      }
      if(this.m_right != null) {
        this.m_right.m_parent = n;
      }
    }
  }
  
  public static void main(String[] args) {
    int threadnum = 62; // 56;
    int size = 40000;
    int nodenum = size*10;
    for(int i = 0; i < threadnum; ++i) {
      TestRunner tr = new TestRunner(i, size, nodenum);
      tr.run();
    }
  }
}
