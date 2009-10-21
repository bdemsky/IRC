public class QuickSort {
  public  QuickSort() {

  }

  /**
   * Sort an array of Objects into ascending order. The sort algorithm is an optimised
   * quicksort, as described in Jon L. Bentley and M. Douglas McIlroy's
   * "Engineering a Sort Function", Software-Practice and Experience, Vol.
   * 23(11) P. 1249-1265 (November 1993). This algorithm gives nlog(n)
   * performance on many arrays that would take quadratic time with a standard
   * quicksort.
   *
   * @param a the array to sort
   */
  public static void sort(Object[] a)
  {
    qsort(a, 0, a.length);
  }

  public static void sort(Object[] a, int fromIndex, int toIndex)
  {
    qsort(a, fromIndex, toIndex);
  }

  private static int med3(int a, int b, int c, Object[]d)
  {
    if(less(d[a], d[b])) {
      if(less(d[b], d[c])) {
        return b;
      } else {
        if(less(d[a], d[c])) 
          return c;
        else
          return a;
      }
    } else {
      if(less(d[c], d[b])) {
        return b;
      } else {
        if(less(d[c], d[a])) 
          return c;
        else
          return a;
      }
    }
  }

  private static void swap(int i, int j, Object[] a)
  {
    Object c = a[i];
    a[i] = a[j];
    a[j] = c;
  }

  private static void qsort(Object[] a, int start, int n)
  {
    // use an insertion sort on small arrays
    if (n <= 7)
    {
      for (int i = start + 1; i < start + n; i++)
        for (int j = i; j > 0 && less(a[j], a[j - 1]); j--)
          swap(j, j - 1, a);
      return;
    }

    int pm = n / 2;             // small arrays, middle element
    if (n > 7)
    {
      int pl = start;
      int pn = start + n - 1;

      if (n > 40)
      {                     // big arrays, pseudomedian of 9
        int s = n / 8;
        pl = med3(pl, pl + s, pl + 2 * s, a);
        pm = med3(pm - s, pm, pm + s, a);
        pn = med3(pn - 2 * s, pn - s, pn, a);
      }
      pm = med3(pl, pm, pn, a);       // mid-size, med of 3
    }

    int pa, pb, pc, pd, pv;
    int r;

    pv = start;
    swap(pv, pm, a);
    pa = pb = start;
    pc = pd = start + n - 1;

    while(true)
    {
      while (pb <= pc && (r = diff(a[pb],a[pv])) <= 0)
      {
        if (r == 0)
        {
          swap(pa, pb, a);
          pa++;
        }
        pb++;
      }
      while (pc >= pb && (r = diff(a[pc],a[pv])) >= 0)
      {
        if (r == 0)
        {
          swap(pc, pd, a);
          pd--;
        }
        pc--;
      }
      if (pb > pc)
        break;
      swap(pb, pc, a);
      pb++;
      pc--;
    }
    int pn = start + n;
    int s;
    s = Math.imin(pa - start, pb - pa);
    vecswap(start, pb - s, s, a);
    s = Math.imin(pd - pc, pn - pd - 1);
    vecswap(pb, pn - s, s, a);
    if ((s = pb - pa) > 1)
      qsort(a, start, s);
    if ((s = pd - pc) > 1)
      qsort(a, pn - s, s);
  }

  private static void vecswap(int i, int j, int n, Object[] a)
  {
    for (; n > 0; i++, j++, n--)
      swap(i, j, a);
  }

  /* ===========================================
   * compareQuery
   * -- Want smallest ID first
   * -- For vector_sort
   * ===========================================
   */
  public static boolean less(Object x, Object y) {
    Query aQueryPtr = (Query) x;
    Query bQueryPtr = (Query) y;
    if(aQueryPtr.index < bQueryPtr.index)
      return true;
    return false;
  }

  public static int diff(Object x, Object y) {
    Query aQueryPtr = (Query) x;
    Query bQueryPtr = (Query) y;
    return (aQueryPtr.index - bQueryPtr.index);
  }

  /**
   * main to test quick sort 
   **/
  /*
  public static void main(String[] args) {
    QuickSort qs = new QuickSort();
    Query[] queries = new Query[10];

    Random r = new Random();
    r.random_alloc();
    r.random_seed(0);

    for(int i = 0; i<10; i++) {
      queries[i] = new Query();
      queries[i].index = (int) (r.random_generate() % 100);
      queries[i].value = -1;
    }

    System.out.println("Before sorting");
    for(int i = 0; i<10; i++)
      System.out.println("queries["+i+"]= "+ queries[i]);

    qs.sort(queries);

    System.out.println("After sorting");
    for(int i = 0; i<10; i++)
      System.out.println("queries["+i+"]= "+ queries[i]);
  }
  */
}
