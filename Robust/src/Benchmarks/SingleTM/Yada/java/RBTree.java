public class RBTree {
    Node root;
    int compID;
    public RBTree() {}
  private Node lookup(Object k)
    {
        Node p = root;
        while(p != null) {
            int cmp = compare(k,p.k);
            if(cmp == 0) {
                return p;
            }
            p = (cmp < 0) ? p.l : p.r;
        }
        return null;
    }
    private void rotateLeft(Node x)
    {
        Node r = x.r;
        Node rl = r.l;
        x.r = rl;
        if(rl != null) {
            rl.p = x;
        }
        Node xp = x.p;
        r.p = xp;
        if (xp == null) {
            root = r;
        } else if (xp.l == x) {
            xp.l = r;
        } else {
            xp.r = r;
        }
        r.l = x;
        x.p = r;
    }
    private void rotateRight(Node x)
    {
        Node l = x.l;
        Node lr = l.r;
        x.l = lr;
        if (lr != null) {
            lr.p = x;
        }
        Node xp = x.p;
        l.p = xp;
        if (xp == null) {
            root = l;
        } else if (xp.r == x) {
            xp.r = l;
        } else {
            xp.l = l;
        }
        l.r = x;
        x.p = l;
    }
    private void fixAfterInsertion(Node x)
    {
        x.c = 0;
        while (x != null && x != root) {
            Node xp = x.p;
            if(xp.c != 0) {
                break;
            }
            if((x!=null? x.p : null) == (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null? ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).l : null)) {
                Node y = (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null? ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).r : null);
                if((y!=null? y.c : 1) == 0) {
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=1;;
                    if (y!=null) y.c=1;;
                    if (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null) ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).c=0;;
                    x = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null);
                } else {
                    if ( x== ((x!=null? x.p : null)!=null? (x!=null? x.p : null).r : null)) {
                        x = (x!=null? x.p : null);
                        rotateLeft(x);
                    }
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=1;;
                    if (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null) ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).c=0;;
                    if(((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null) != null) {
                        rotateRight(((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null));
                    }
                }
            } else {
                Node y = (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null? ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).l : null);
                if((y!=null? y.c : 1) == 0) {
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=1;;
                    if (y!=null) y.c=1;;
                    if (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null) ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).c=0;;
                    x = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null);
                } else {
                    if (x == ((x!=null? x.p : null)!=null? (x!=null? x.p : null).l : null)) {
                        x = (x!=null? x.p : null);
                        rotateRight(x);
                    }
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=1;;
                    if (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null)!=null) ((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null).c=0;;
                    if (((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null) != null) {
                        rotateLeft(((x!=null? x.p : null)!=null? (x!=null? x.p : null).p : null));
                    }
                }
            }
        }
        Node ro = root;
        if(ro.c != 1) {
            ro.c = 1;
        }
    }
    private Node insert(Object k,Object v,Node n)
    {
        Node t = root;
        if (t== null) {
            if (n == null) {
                return null;
            }
            n.l = null;
            n.r = null;
            n.p = null;
            n.k = k;
            n.v = v;
            n.c = 1;
            root = n;
            return null;
        }
        while(true) {
            int cmp = compare(k,t.k);
            if (cmp == 0) {
                return t;
            } else if (cmp < 0) {
                Node tl = t.l;
                if (tl != null) {
                    t = tl;
                } else {
                    n.l = null;
                    n.r = null;
                    n.k = k;
                    n.v = v;
                    n.p = t;
                    t.l = n;
                    fixAfterInsertion(n);
                    return null;
                }
            } else {
                Node tr = t.r;
                if (tr != null) {
                    t = tr;
                } else {
                    n.l = null;
                    n.r = null;
                    n.k = k;
                    n.v = v;
                    n.p = t;
                    t.r = n;
                    fixAfterInsertion(n);
                    return null;
                }
            }
        }
    }
    private Node successor(Node t)
    {
        if ( t == null) {
            return null;
        } else if( t.r != null) {
            Node p = t.r;
            while (p.l != null) {
                p = p.l;
            }
            return p;
        } else {
            Node p = t.p;
            Node ch = t;
            while (p != null && ch == p.r) {
                ch = p;
                p = p.p;
            }
            return p;
        }
    }
    private void fixAfterDeletion(Node x)
    {
        while (x != root && (x!=null? x.c : 1) == 1) {
            if ( x == ((x!=null? x.p : null)!=null? (x!=null? x.p : null).l : null)) {
                Node sib = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).r : null);
                if ((sib!=null? sib.c : 1) == 0) {
                    if (sib!=null) sib.c=1;;
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=0;;
                    rotateLeft((x!=null? x.p : null));
                    sib = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).r : null);
                }
                if(((sib!=null? sib.l : null)!=null? (sib!=null? sib.l : null).c : 1) == 1 &&
                        ((sib!=null? sib.r : null)!=null? (sib!=null? sib.r : null).c : 1) == 1) {
                    if (sib!=null) sib.c=0;;
                    x = (x!=null? x.p : null);
                } else {
                    if(((sib!=null? sib.r : null)!=null? (sib!=null? sib.r : null).c : 1) == 1) {
                        if ((sib!=null? sib.l : null)!=null) (sib!=null? sib.l : null).c=1;;
                        if (sib!=null) sib.c=0;;
                        rotateRight(sib);
                        sib = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).r : null);
                    }
                    if (sib!=null) sib.c=((x!=null? x.p : null)!=null? (x!=null? x.p : null).c : 1);;
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=1;;
                    if ((sib!=null? sib.r : null)!=null) (sib!=null? sib.r : null).c=1;;
                    rotateLeft((x!=null? x.p : null));
                    x = root;
                }
            } else {
                Node sib = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).l : null);
                if((sib!=null? sib.c : 1) == 0) {
                    if (sib!=null) sib.c=1;;
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=0;;
                    rotateRight((x!=null? x.p : null));
                    sib = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).l : null);
                }
                if (((sib!=null? sib.r : null)!=null? (sib!=null? sib.r : null).c : 1) == 1 &&
                        ((sib!=null? sib.l : null)!=null? (sib!=null? sib.l : null).c : 1) == 1) {
                    if (sib!=null) sib.c=0;;
                    x = (x!=null? x.p : null);
                } else {
                    if(((sib!=null? sib.l : null)!=null? (sib!=null? sib.l : null).c : 1) == 1) {
                        if ((sib!=null? sib.r : null)!=null) (sib!=null? sib.r : null).c=1;;
                        if (sib!=null) sib.c=0;;
                        rotateLeft(sib);
                        sib = ((x!=null? x.p : null)!=null? (x!=null? x.p : null).l : null);
                    }
                    if (sib!=null) sib.c=((x!=null? x.p : null)!=null? (x!=null? x.p : null).c : 1);;
                    if ((x!=null? x.p : null)!=null) (x!=null? x.p : null).c=1;;
                    if ((sib!=null? sib.l : null)!=null) (sib!=null? sib.l : null).c=1;;
                    rotateRight((x!=null? x.p : null));
                    x = root;
                }
            }
        }
        if (x != null && x.c != 1) {
            x.c = 1;
        }
    }
    private Node deleteNode(Node p) {
        if(p.l != null && p.r != null) {
            Node s = successor(p);
            p.k = s.k;
            p.v = s.v;
            p = s;
        }
        Node replacement = (p.l != null)? p.l : p.r;
        if (replacement != null) {
            replacement.p = p.p;
            Node pp = p.p;
            if(pp == null) {
                root = replacement;
            } else if( p == pp.l) {
                pp.l = replacement;
            } else {
                pp.r = replacement;
            }
            p.l = null;
            p.r = null;
            p.p = null;
            if(p.c == 1) {
                fixAfterDeletion(replacement);
            }
        } else if(p.p == null) {
            root = null;
        } else {
            if (p.c == 1) {
                fixAfterDeletion(p);
            }
            Node pp = p.p;
            if(pp != null) {
                if( p == pp.l) {
                    pp.l = null;
                } else if( p == pp.r) {
                    pp.r = null;
                }
                p.p = null;
            }
        }
        return p;
    }
    private Node firstEntry()
    {
        Node p = root;
        if( p != null) {
            while ( p.l != null) {
                p = p.l;
            }
        }
        return p;
    }
    private int verifyRedBlack(Node root,int depth)
    {
        int height_left;
        int height_right;
        if ( root == null) {
            return 1;
        }
        height_left = verifyRedBlack(root.l,depth+1);
        height_right = verifyRedBlack(root.r,depth+1);
        if(height_left == 0 || height_right == 0) {
            return 0;
        }
        if (height_left != height_right) {
            System.out.println(" Imbalace @depth = " + depth + " : " + height_left + " " + height_right);
        }
        if (root.l != null && root.l.p != root) {
            System.out.println(" lineage");
        }
        if (root.r != null && root.r.p != root) {
            System.out.println(" lineage");
        }
        if (root.c == 0) {
            if (root.l != null && root.l.c != 1) {
                System.out.println("VERIFY in verifyRedBlack");
                return 0;
             }
            if (root.r != null && root.r.c != 1) {
                 System.out.println("VERIFY in verifyRedBlack");
                 return 0;
            }
            return height_left;
        }
        if(root.c != 1) {
                System.out.println("VERIFY in verifyRedBlack");
                return 0;
        }
        return (height_left + 1);
    }
    private int compare(Object a,Object b)
    {
        if(compID == 0)
   return element.compareEdge((edge)a,(edge)b);
	return 0;
    }
    public int verify(int verbose)
    {
        if ( root == null) {
            return 1;
        }
        if(verbose != 0) {
            System.out.println("Integrity check: ");
        }
        if (root.p != null) {
            System.out.println("  (WARNING) root = " + root + " parent = " + root.p);
            return -1;
        }
        if (root.c != 1) {
            System.out.println("  (WARNING) root = " + root + " color = " + root.c);
        }
        int ctr = 0;
        Node its = firstEntry();
        while (its != null) {
            ctr++;
            Node child = its.l;
            if ( child != null && child.p != its) {
                System.out.println("bad parent");
            }
            child = its.r;
            if ( child != null && child.p != its) {
                System.out.println("Bad parent");
            }
            Node nxt = successor(its);
            if (nxt == null) {
                break;
            }
            if( compare(its.k,nxt.k) >= 0) {
                System.out.println("Key order " + its + " ("+its.k+" "+its.v+") "
                                                + nxt + " ("+nxt.k+" "+nxt.v+") ");
                return -3;
            }
            its = nxt;
        }
        int vfy = verifyRedBlack(root, 0);
        if(verbose != 0) {
            System.out.println(" Nodes = " + ctr + " Depth = " + vfy);
        }
        return vfy;
    }
  public RBTree(int compID) {
    this.compID = compID;
    this.root = null;
  }
  public boolean insert(Object key,Object val) {
    Node node = new Node();
    Node ex = insert(key,val,node);
    if ( ex != null) {
      node = null;
    }
    return ((ex == null) ? true : false);
  }
  public boolean deleteObjNode(Object key) {
        Node node = null;
        node = lookup(key);
        if(node != null) {
            node = deleteNode(node);
        }
        if(node != null) {
        }
        return ((node != null) ? true : false);
    }
  public boolean update(Object key,Object val) {
    Node nn = new Node();
    Node ex = insert(key,val,nn);
    if (ex != null) {
      ex.v = val;
      nn = null;
      return true;
    }
    return false;
  }
  public Object get(Object key) {
    Node n = lookup(key);
    if (n != null) {
      Object val = n.v;
      return val;
    }
    return null;
  }
  public boolean contains(Object key) {
    Node n = lookup(key);
    return (n != null);
  }
}
