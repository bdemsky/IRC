public class QuickSort {

  public QuickSort() {

  }

  public void swap (int A[], int x, int y)
  {
    int temp = A[x];
    A[x] = A[y];
    A[y] = temp;
  }

  // Reorganizes the given list so all elements less than the first are 
  // before it and all greater elements are after it.   
  public int partition(int a[], int left, int right)
  {
    int i = left - 1;
    int j = right;
    while (true) {
      while (less(a[++i], a[right]))      // find item on left to swap
        ;                               // a[right] acts as sentinel
      while (less(a[right], a[--j]))      // find item on right to swap
        if (j == left) break;           // don't go out-of-bounds
      if (i >= j) break;                  // check if pointers cross
      swap(a, i, j);                      // swap two elements into place
    }
    swap(a, i, right);                      // swap with partition element
    return i;
  }

  public void sort(int A[], int f, int l)
  {
    if (f >= l) return;
    int pivot_index = partition(A, f, l);
    sort(A, f, pivot_index);
    sort(A, pivot_index+1, l);
  }

  boolean less(int x, int y) {
    return(x<y);
  }
}
