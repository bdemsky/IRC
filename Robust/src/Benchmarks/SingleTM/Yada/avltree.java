/* =============================================================================
 *
 * avltree.c
 * -- AVL balanced tree library
 *
 * =============================================================================
 *
 * AVL balanced tree library
 *
 *   > Created (Julienne Walker): June 17, 2003
 *   > Modified (Julienne Walker): September 24, 2005
 *
 * =============================================================================
 *
 * Modified May 5, 2006 by Chi Cao Minh
 *
 * - Changed to not need functions to duplicate and release the data pointer
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/mt19937ar.c and lib/mt19937ar.h, please see the
 * header of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/rbtree.h and lib/rbtree.c, please see
 * lib/LEGALNOTICE.rbtree and lib/LICENSE.rbtree
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */

#define HEIGHT_LIMIT 64

/* Two way single rotation */
#define jsw_single(root,dir) do {         \
  avlnode save = root.link[1-dir]; \
  root.link[1-dir] = save.link[dir];     \
  save.link[dir] = root;                 \
  root = save;                            \
} while (false)

/* Two way double rotation */
#define jsw_double(root,dir) do {                    \
  avlnode save = root.link[1-dir].link[dir]; \
  root.link[1-dir].link[dir] = save.link[1-dir];    \
  save.link[1-dir] = root.link[1-dir];               \
  root.link[1-dir] = save;                           \
  save = root.link[1-dir];                           \
  root.link[1-dir] = save.link[dir];                \
  save.link[dir] = root;                            \
  root = save;                                       \
} while (false)

/* Adjust balance before double rotation */
#define jsw_adjust_balance(root,dir,bal) do { \
  avlnode n = root.link[dir];         \
  avlnode nn = n.link[1-dir];          \
  if ( nn.balance == 0 )                     \
    root.balance = n.balance = 0;           \
  else if ( nn.balance == bal ) {            \
    root.balance = -bal;                     \
    n.balance = 0;                           \
  }                                           \
  else { /* nn.balance == -bal */            \
    root.balance = 0;                        \
    n.balance = bal;                         \
  }                                           \
  nn.balance = 0;                            \
} while (false)

/* Rebalance after insertion */
#define jsw_insert_balance(root,dir) do {  \
  avlnode ni = root.link[dir];      \
  int bal = dir == 0 ? -1 : +1;	  \
  if ( ni.balance == bal ) {               \
    root.balance = ni.balance = 0;        \
    jsw_single ( root, (1-dir) );	   \
  }                                        \
  else { /* n.balance == -bal */          \
    jsw_adjust_balance( root, dir, bal ); \
    jsw_double( root, (1-dir) );	   \
  }                                        \
} while (false)

/* Rebalance after deletion */
#define jsw_remove_balance(root,dir,done) do { \
  avlnode nr = root.link[1-dir];         \
  int bal = dir == 0 ? -1 : +1;	      \
  if ( nr.balance == -bal ) {		     \
    root.balance = nr.balance = 0;            \
    jsw_single ( root, dir );                  \
  }                                            \
  else if ( nr.balance == bal ) {              \
    jsw_adjust_balance ( root, (1-dir), -bal ); \
    jsw_double ( root, dir );                  \
  }                                            \
  else { /* n.balance == 0 */                 \
    root.balance = -bal;                      \
    nr.balance = bal;			       \
    jsw_single ( root, dir );                  \
    done = 1;                                  \
  }                                            \
} while (false)


class avlnode {
  int balance; /* Balance factor */
  Object data;    /* User-defined content */
  avlnode link[]; /* Left (0) and right (1) links */

  avlnode() {
    link=new avlnode[2];
  }

  avlnode(avltree tree, Object data) {
    this.data = data;
    link=new avlnode[2];
  }
}

class avltrav {
  avltree tree;               /* Paired tree */
  avlnode it;                 /* Current node */
  avlnode path[]; /* Traversal path */
  int  top;                /* Top of stack */

  avltrav() {
    path=new avlnode[HEIGHT_LIMIT];
  }

/*
  First step in traversal,
  handles min and max
*/
  Object start(avltree tree, int dir) {
    this.tree = tree;
    it = tree.root;
    top = 0;

    /* Build a path to work with */
    if ( it != null ) {
      while ( it.link[dir] != null ) {
	path[top++] = it;
	it = it.link[dir];
      }
    }
    
    return it == null? null: it.data;
  }

  /*
    Subsequent traversal steps,
    handles ascending and descending
  */
  Object move(int dir) {
    if (it.link[dir] != null) {
      /* Continue down this branch */
      path[top++] = it;
      it = it.link[dir];

      while (it.link[1-dir] != null ) {
	path[top++] = it;
	it = it.link[1-dir];
      }
    } else {
      /* Move to the next branch */
      avlnode last;

      do {
	if (top == 0 ) {
	  it = null;
	  break;
	}

	last = it;
	it = path[--top];
      } while ( last == it.link[dir] );
    }

    return it==null? null: it.data;
  }

  Object avltfirst(avltree tree ) {
    return start(tree, 0 ); /* Min value */
  }

  Object avltlast (avltree tree ) {
    return start(tree, 1 ); /* Max value */
  }

  Object avltnext() {
    return move(1); /* Toward larger items */
  }

  Object avltprev() {
    return move(0); /* Toward smaller items */
  }
}

public class avltree {
  avlnode root; /* Top of the tree */
  int         size;   /* Number of items (user-defined) */
  int mode;
  //mode=0 element_mapCompareEdge
  //mode=1 element_mapCompare

  public avltree(int mode) {
    size = 0;
    this.mode=mode;
  }

  int cmp(Object a, Object b) {
    if (mode==0) {
      return element.element_mapCompareEdge((edge)a, (edge)b);
    } else if (mode==1) {
      return element.element_mapCompare((edge)a, (edge)b);      
    }
    return 0;
  }


  boolean contains(Object key) {
    boolean success = false;
    edge searchPair=new edge();
    searchPair.firstPtr = key;
    if (avlfind(searchPair) != null) {
      success = true;
    }
    return success;
  }

  Object find(Object key) {
    Object dataPtr = null;
    edge searchPair=new edge();
    searchPair.firstPtr = key;
    edge pairPtr = (edge)avlfind(searchPair);
    if (pairPtr != null) {
      dataPtr = pairPtr.secondPtr;
    }
    return dataPtr;
  }

  boolean insert(Object key, Object data) {
    boolean success = false;
    edge insertPtr = new edge(key, data);
    if (avlinsert(insertPtr)) {
      success = true;
    }
    return success;
  }

  boolean remove(Object key) {
    boolean success = false;
    edge searchPair=new edge();
    searchPair.firstPtr = key;
    edge pairPtr = (edge) avlfind(searchPair);
    if (avlerase(searchPair)) {
      success=true;
    }
    return success;
  }

  Object avlfind(Object data ) {
    avlnode it = root;
    
    while ( it != null ) {
      int cmp =cmp(it.data, data );
      
      if ( cmp == 0 )
	break;
      
      it = it.link[(cmp < 0)?1:0];
    }
    
    return it == null ? null : it.data;
  }

  boolean avlinsert(Object data ) {
    /* Empty tree case */
    if ( root == null ) {
      root = new avlnode(this,data);
    } else {
      avlnode head =new avlnode(); /* Temporary tree root */
      avlnode s, t;     /* Place to rebalance and parent */
      avlnode p, q;     /* Iterator and save pointer */
      int dir;

      /* Set up false root to ease maintenance */
      t = head;
      t.link[1] = root;
      
      /* Search down the tree, saving rebalance points */
      for ( s = p = t.link[1]; true ; p=q ) {
	dir = (cmp ( p.data, data ) < 0)?1:0;
	q = p.link[dir];
	
	if ( q == null )
	  break;
	
	if ( q.balance != 0 ) {
	  t = p;
	  s = q;
	}
      }
      
      p.link[dir] = q = new avlnode(this, data);
      if (q==null)
	return false;
      
      /* Update balance factors */
      for ( p = s; p != q; p = p.link[dir] ) {
	dir = (cmp ( p.data, data ) < 0)?1:0;
	p.balance += (dir == 0) ? -1 : +1;
      }
      
      q = s; /* Save rebalance point for parent fix */
      
      /* Rebalance if necessary */
      if ((s.balance<0?-s.balance:s.balance)>1) {
	dir =(cmp(s.data,data) < 0)?1:0;
	jsw_insert_balance ( s, dir );
      }
      
      /* Fix parent */
      if ( q == head.link[1] )
	root = s;
      else
	t.link[(q == t.link[1])?1:0] = s;
    }
    
    size++;
    
    return true;
  }

  boolean avlerase (Object data) {
    if (root != null) {
      avlnode it;
      avlnode up[]=new avlnode[HEIGHT_LIMIT];
      int upd[]=new int[HEIGHT_LIMIT];
      int top = 0;
      int done = 0;

      it = root;

      /* Search down tree and save path */
      for ( ; true ; ) {
	if ( it == null )
	  return false;
	else if ( cmp ( it.data, data ) == 0 )
	  break;
	
	/* Push direction and node onto stack */
	upd[top] = (cmp ( it.data, data ) < 0)?1:0;
	up[top++] = it;
	
	it = it.link[upd[top - 1]];
      }
      
      /* Remove the node */
      if ( it.link[0] == null || it.link[1] == null ) {
      /* Which child is not null? */
	int dir = (it.link[0] == null)?1:0;

	/* Fix parent */
	if (top != 0)
	  up[top-1].link[upd[top - 1]] = it.link[dir];
	else
	  root = it.link[dir];
      } else {
	/* Find the inorder successor */
	avlnode heir = it.link[1];

	/* Save this path too */
	upd[top] = 1;
	up[top++] = it;

	while ( heir.link[0] != null ) {
	  upd[top] = 0;
	  up[top++] = heir;
	  heir = heir.link[0];
	}

	/* Swap data */
	Object save = it.data;
	it.data = heir.data;
	heir.data = save;

	/* Unlink successor and fix parent */
	up[top-1].link[(up[top - 1] == it)?1:0] = heir.link[1];
    }

      /* Walk back up the search path */
      while ( --top >= 0 && (done==0) ) {
	/* Update balance factors */
	up[top].balance += upd[top] != 0 ? -1 : +1;
	
	/* Terminate or rebalance as necessary */
	if (up[top].balance == 1 || up[top].balance==-1 )
	  break;
	else if ( up[top].balance > 1 ||up[top].balance < -1) {
	  jsw_remove_balance ( up[top], upd[top], done );
	  
	  /* Fix parent */
	  if ( top != 0 )
	    up[top - 1].link[upd[top - 1]] = up[top];
	  else
	    root = up[0];
	}
      }
    }
    size--;
    return true;
  }
  
  int avlsize() {
    return size;
  }

}  



/* =============================================================================
 *
 * End of avltree.c
 *
 * =============================================================================
 */
