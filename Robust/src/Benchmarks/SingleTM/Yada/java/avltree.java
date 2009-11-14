class avlnode {
  int balance;
  Object data;
  avlnode link[];
  avlnode() {
    link=new avlnode[2];
  }
  avlnode(avltree tree, Object data) {
    this.data = data;
    link=new avlnode[2];
  }
}
class avltrav {
  avltree tree;
  avlnode it;
  avlnode path[];
  int top;
  avltrav() {
    path=new avlnode[64];
  }
  Object start(avltree tree, int dir) {
    this.tree = tree;
    it = tree.root;
    top = 0;
    if ( it != null ) {
      while ( it.link[dir] != null ) {
 path[top++] = it;
 it = it.link[dir];
      }
    }
    return it == null? null: it.data;
  }
  Object move(int dir) {
    if (it.link[dir] != null) {
      path[top++] = it;
      it = it.link[dir];
      while (it.link[1-dir] != null ) {
 path[top++] = it;
 it = it.link[1-dir];
      }
    } else {
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
    return start(tree, 0 );
  }
  Object avltlast (avltree tree ) {
    return start(tree, 1 );
  }
  Object avltnext() {
    return move(1);
  }
  Object avltprev() {
    return move(0);
  }
}
public class avltree {
  avlnode root;
  int size;
  int mode;
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
    if ( root == null ) {
      root = new avlnode(this,data);
    } else {
      avlnode head =new avlnode();
      avlnode s, t;
      avlnode p, q;
      int dir;
      t = head;
      t.link[1] = root;
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
      for ( p = s; p != q; p = p.link[dir] ) {
 dir = (cmp ( p.data, data ) < 0)?1:0;
 p.balance += (dir == 0) ? -1 : +1;
      }
      q = s;
      if ((s.balance<0?-s.balance:s.balance)>1) {
 dir =(cmp(s.data,data) < 0)?1:0;
 do { avlnode ni = s.link[dir]; int bal = dir == 0 ? -1 : +1; if ( ni.balance == bal ) { s.balance = ni.balance = 0; do { avlnode save = s.link[1-(1-dir)]; s.link[1-(1-dir)] = save.link[(1-dir)]; save.link[(1-dir)] = s; s = save; } while (false); } else { do { avlnode n = s.link[dir]; avlnode nn = n.link[1-dir]; if ( nn.balance == 0 ) s.balance = n.balance = 0; else if ( nn.balance == bal ) { s.balance = -bal; n.balance = 0; } else { s.balance = 0; n.balance = bal; } nn.balance = 0; } while (false); do { avlnode save = s.link[1-(1-dir)].link[(1-dir)]; s.link[1-(1-dir)].link[(1-dir)] = save.link[1-(1-dir)]; save.link[1-(1-dir)] = s.link[1-(1-dir)]; s.link[1-(1-dir)] = save; save = s.link[1-(1-dir)]; s.link[1-(1-dir)] = save.link[(1-dir)]; save.link[(1-dir)] = s; s = save; } while (false); } } while (false);
      }
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
      avlnode up[]=new avlnode[64];
      int upd[]=new int[64];
      int top = 0;
      int done = 0;
      it = root;
      for ( ; true ; ) {
 if ( it == null )
   return false;
 else if ( cmp ( it.data, data ) == 0 )
   break;
 upd[top] = (cmp ( it.data, data ) < 0)?1:0;
 up[top++] = it;
 it = it.link[upd[top - 1]];
      }
      if ( it.link[0] == null || it.link[1] == null ) {
 int dir = (it.link[0] == null)?1:0;
 if (top != 0)
   up[top-1].link[upd[top - 1]] = it.link[dir];
 else
   root = it.link[dir];
      } else {
 avlnode heir = it.link[1];
 upd[top] = 1;
 up[top++] = it;
 while ( heir.link[0] != null ) {
   upd[top] = 0;
   up[top++] = heir;
   heir = heir.link[0];
 }
 Object save = it.data;
 it.data = heir.data;
 heir.data = save;
 up[top-1].link[(up[top - 1] == it)?1:0] = heir.link[1];
    }
      while ( --top >= 0 && (done==0) ) {
 up[top].balance += upd[top] != 0 ? -1 : +1;
 if (up[top].balance == 1 || up[top].balance==-1 )
   break;
 else if ( up[top].balance > 1 ||up[top].balance < -1) {
   do { avlnode nr = up[top].link[1-upd[top]]; int bal = upd[top] == 0 ? -1 : +1; if ( nr.balance == -bal ) { up[top].balance = nr.balance = 0; do { avlnode save = up[top].link[1-upd[top]]; up[top].link[1-upd[top]] = save.link[upd[top]]; save.link[upd[top]] = up[top]; up[top] = save; } while (false); } else if ( nr.balance == bal ) { do { avlnode n = up[top].link[(1-upd[top])]; avlnode nn = n.link[1-(1-upd[top])]; if ( nn.balance == 0 ) up[top].balance = n.balance = 0; else if ( nn.balance == -bal ) { up[top].balance = - -bal; n.balance = 0; } else { up[top].balance = 0; n.balance = -bal; } nn.balance = 0; } while (false); do { avlnode save = up[top].link[1-upd[top]].link[upd[top]]; up[top].link[1-upd[top]].link[upd[top]] = save.link[1-upd[top]]; save.link[1-upd[top]] = up[top].link[1-upd[top]]; up[top].link[1-upd[top]] = save; save = up[top].link[1-upd[top]]; up[top].link[1-upd[top]] = save.link[upd[top]]; save.link[upd[top]] = up[top]; up[top] = save; } while (false); } else { up[top].balance = -bal; nr.balance = bal; do { avlnode save = up[top].link[1-upd[top]]; up[top].link[1-upd[top]] = save.link[upd[top]]; save.link[upd[top]] = up[top]; up[top] = save; } while (false); done = 1; } } while (false);
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
