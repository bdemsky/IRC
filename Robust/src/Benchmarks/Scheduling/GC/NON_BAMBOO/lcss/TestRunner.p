package lcss;

public class TestRunner extends Thread {

  int[] testargs;

  public TestRunner(int[] args) {
    this.testargs = args;
  }

  private Vector algb2(int x,
      int p,
      int q,
      Vector ys,
      Vector yst,
      Vector yst1) {
    Vector result = null;
    if(ys.size() == 0) {
      // algb2 _ _ [] = []
      result = new Vector();
    } else {
      // algb2 k0j1 k1j1 ((y,k0j):ys)
      int y = ((Integer)ys.elementAt(0)).intValue();
      int yt = ((Integer)yst.elementAt(0)).intValue();
      Vector ys2 = new Vector();
      for(int i = 1; i < ys.size(); i++) {
        ys2.addElement(ys.elementAt(i));
      }
      Vector yst2 = new Vector();
      for(int i = 1; i < yst.size(); i++) {
        yst2.addElement(yst.elementAt(i));
      }

      // = let kjcurr = if x == y then k0j1+1 else max k1j1 k0j
      // in (y,kjcurr) : algb2 k0j kjcurr ys
      int k = 0;
      if(x == y) {
        k = p + 1;
      } else {
        k = (q > yt) ? q : yt;
      }
      result = this.algb2(x, yt, k, ys2, yst2, yst1);
      result.insertElementAt(new Integer(y), 0);
      yst1.insertElementAt(new Integer(k), 0);
    }
    return result;
  }

  private Vector algb1(Vector xs,
      Vector ys,
      Vector yst) {
    Vector result = null;
    if(xs.size() == 0) {
      // algb1 [] ys' = map snd ys'
      result = yst;
    } else {
      // algb1 (x:xs) ys'
      // = algb1 xs (algb2 0 0 ys')
      int x = ((Integer)xs.elementAt(0)).intValue();
      Vector xs1 = new Vector();
      for(int i = 1; i < xs.size(); i++) {
        xs1.addElement(xs.elementAt(i));
      }

      Vector yst1 = new Vector();
      Vector ys1 = this.algb2(x, 0, 0, ys, yst, yst1);

      result = this.algb1(xs1, ys1, yst1);
    }
    return result;
  }

  private Vector algb(Vector xs,
      Vector ys) {
    // algb xs ys
    // = 0 : algb1 xs [ (y,0) | y <- ys ]
    Vector yst = new Vector();
    for(int i = 0; i < ys.size(); i++) {
      yst.addElement(new Integer(0));
    }
    Vector result = this.algb1(xs, ys, yst);
    result.insertElementAt(new Integer(0), 0);
    return result;
  }

  private int findk(int k,
      int km,
      int m,
      Vector v,
      Vector vt) {
    int result = 0;
    if(v.size() == 0) {
      // findk k km m [] = km
      result = km;
    } else {
      // findk k km m ((x,y):xys)
      int x = ((Integer)v.elementAt(0)).intValue();
      int y = ((Integer)vt.elementAt(0)).intValue();
      Vector v1 = new Vector();
      for(int i = 1; i < v.size(); i++) {
        v1.addElement(v.elementAt(i));
      }
      Vector vt1 = new Vector();
      for(int i = 1; i < vt.size(); i++) {
        vt1.addElement(vt.elementAt(i));
      }

      if(x + y >= m) {
        // | x+y >= m  = findk (k+1) k  (x+y) xys
        result = this.findk(k+1, k, x+y, v1, vt1);
      } else {
        // | otherwise = findk (k+1) km m     xys
        result = this.findk(k+1, km, m, v1, vt1);
      }
    }
    return result;
  }

  private Vector algc(int m,
      int n,
      Vector xs,
      Vector ys,
      Vector r) {
    Vector result = null;
    if(ys.size() == 0) {
      // algc m n xs []  = id
      result = r;
    } else if(xs.size() == 1) {
      // algc m n [x] ys = if x `elem` ys then (x:) else id
      result = r;

      int x = ((Integer)xs.elementAt(0)).intValue();
      // if x `elem` ys
      boolean iscontains = false;
      for(int i = 0; i < ys.size(); i++) {
        if(((Integer)ys.elementAt(i)).intValue() == x) {
          iscontains = true;
          i = ys.size();  // break;
        }
      }
      if(iscontains) {
        // then (x:)
        r.insertElementAt(new Integer(x), 0);
      }
    } else {
      // algc m n xs ys
      // = algc m2 k xs1 (take k ys) . algc (m-m2) (n-k) xs2 (drop k ys)
      // where
      // m2 = m `div` 2
      int m2 = m/2;

      // xs1 = take m2 xs
      Vector xs1 = new Vector();
      int i = 0;
      for(i = 0; i < m2; i++) {
        xs1.addElement(xs.elementAt(i));
      }

      // xs2 = drop m2 xs
      Vector xs2 = new Vector();
      for(; i < xs.size(); i++) {
        xs2.addElement(xs.elementAt(i));
      }

      // l1 = algb xs1 ys
      Vector l1 = this.algb(xs1, ys);

      // l2 = reverse (algb (reverse xs2) (reverse ys))
      Vector rxs2 = new Vector();
      for(i = xs2.size(); i > 0; i--) {
        rxs2.addElement(xs2.elementAt(i - 1));
      }
      Vector rys = new Vector();
      for(i = ys.size(); i > 0; i--) {
        rys.addElement(ys.elementAt(i - 1));
      }
      Vector rl2 = algb(rxs2, rys);
      Vector l2 = new Vector();
      for(i = rl2.size(); i > 0; i--) {
        l2.addElement(rl2.elementAt(i - 1));
      }

      // k = findk 0 0 (-1) (zip l1 l2)
      int k = this.findk(0, 0, (-1), l1, l2);

      // algc m n xs ys
      // = algc m2 k xs1 (take k ys) . algc (m-m2) (n-k) xs2 (drop k ys)
      Vector ysk = new Vector();
      // (take k ys)
      for(i = 0; i < k; i++) {
        ysk.addElement(ys.elementAt(i));
      }
      Vector ysd = new Vector();
      // (drop k ys)
      for(; i < ys.size(); i++) {
        ysd.addElement(ys.elementAt(i));
      }
      Vector interresult = this.algc(m-m2, n-k, xs2, ysd, r);
      result = this.algc(m2, k, xs1, ysk, interresult);
    }
    return result;
  }

  public void run() {
    Vector xs = new Vector();
    Vector ys = new Vector();

    int x1 = this.testargs[0];
    int x2 = this.testargs[1];
    int xfinal = this.testargs[2];
    int xdelta = x2-x1;
    int y1 = this.testargs[3];
    int y2 = this.testargs[4];
    int yfinal = this.testargs[5];
    int ydelta = y2-y1;
    xs.addElement(new Integer(x1));
    for(int i = x2; i <= xfinal; ) {
      xs.addElement(new Integer(i));
      i += xdelta;
    }
    ys.addElement(new Integer(y1));
    for(int i = y2; i <= yfinal; ) {
      ys.addElement(new Integer(i));
      i += ydelta;
    }

    Vector r = new Vector();

    Vector result = this.algc(xs.size(), ys.size(), xs, ys, r);
    for(int i = 0; i < result.size(); i++) {
      int tmp = ((Integer)result.elementAt(i)).intValue();
    }
  }
  public static void main(String argv[]){
    int threadnum = THREADNUM;
    int[] args = new int[6];
    args[0] = 1;
    args[1] = 2;
    args[2] = 160;
    args[3] = 80;
    args[4] = 81;
    args[5] = 240;
    System.setgcprofileflag();
    TestRunner trarray[]=new TestRunner[threadnum];
    for(int i = 1; i < threadnum; ++i) {
      TestRunner tr = new TestRunner(args);
      tr.start();
      trarray[i]=tr;
    }
    TestRunner tr0 = new TestRunner(args);
    tr0.run();
    for(int i = 1; i < threadnum; ++i) {
      trarray[i].join();
    }
  }
}
