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
