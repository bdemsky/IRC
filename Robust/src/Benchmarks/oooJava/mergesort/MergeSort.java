/**
 * Sample sort program adapted from a demo in <A
 * href="http://supertech.lcs.mit.edu/cilk/"> Cilk</A> and <A
 * href="http://www.cs.utexas.edu/users/hood/"> Hood</A>.
 * 
 * There are two versions of MergeSort here: One that splits the array into four
 * pieces at each recursive step, and one that splits the array into eight
 * pieces. This abstract class represents the common elements of both versions.
 * 
 **/

public class MergeSort {

  /* Threshold values */

  // Cutoff for when to do sequential versus parallel merges
  public static  int MERGE_SIZE;

  // Cutoff for when to do sequential quicksort versus parallel mergesort
  public static  int QUICK_SIZE;

  // Cutoff for when to use insertion-sort instead of quicksort
  public static  int INSERTION_SIZE;
  
  public static  int SERIALIZED_CUT_OFF;


  protected int[] input;
  protected int[] result;
  protected int size;

  public void run(int size) {
    this.size=size;
    long startT=System.currentTimeMillis();
    initialize();
    System.out.println("init time="+(System.currentTimeMillis()-startT));
    runWorkAndTest();
  }

  public MergeSort() {
    MERGE_SIZE = 2048;
    QUICK_SIZE = 2048;
    INSERTION_SIZE = 2000;
  }

  public void initialize() {
    
    SERIALIZED_CUT_OFF=size/16;
      
    input = new int[size];
    result = new int[size];

    Random random = new Random(100100);
    for (int i = 0; i < size; ++i) {
      int k = random.nextInt();
      if (k < 0) {
        k *= -1;
      }
      k = k % 10000;
      input[i] = k;
    }
  }

  public void runWorkAndTest() {
    sese run{
      sort(input, result);
    }
//    sese test{
//      checkSorted(input);
//    }
  }
  
  public void sort(int A[], int B[]){
  }

  protected void checkSorted(int[] array) {
    System.out.println("Verifying results- size of " + array.length);
    for (int i = 1; i < array.length; i++) {
      if (array[i - 1] > array[i]) {
        System.out.println("Validation Failed.");
        return;
      }
    }
    System.out.println("Validation Success.");
  }

  protected void merge(int A[], int B[], int out[]) {

    if (A.length <= MERGE_SIZE) {
      sequentialMerge(A, B, out);
    } else {
      int aHalf = A.length >>> 1; /* l33t shifting h4x!!! */
      int bSplit = findSplit(A[aHalf], B);

      int[] A_split0 = new int[aHalf];
      int[] A_split1 = new int[A.length - aHalf];
      for (int i = 0; i < aHalf; i++) {
        A_split0[i] = A[i];
      }
      for (int i = aHalf; i < A.length; i++) {
        A_split1[i - aHalf] = A[i];
      }

      int[] B_split0 = new int[bSplit];
      int[] B_split1 = new int[B.length - bSplit];
      for (int i = 0; i < bSplit; i++) {
        B_split0[i] = B[i];
      }
      for (int i = bSplit; i < B.length; i++) {
        B_split1[i - bSplit] = B[i];
      }

      int outSplit = aHalf + bSplit;
      int[] out_split0 = new int[outSplit];
      int[] out_split1 = new int[out.length - outSplit];

      merge(A_split0, B_split0, out_split0);
      merge(A_split1, B_split1, out_split1);
      
      for (int i = 0; i < outSplit; i++) {
        out[i]=out_split0[i]; 
      }
      for (int i = outSplit; i < out.length; i++) {
        out[i]=out_split1[i - outSplit];
      }
      
    }
    
  }

  /** A standard sequential merge **/
  protected void sequentialMerge(int A[], int B[], int out[]) {
    int a = 0;
    int aFence = A.length;
    int b = 0;
    int bFence = B.length;
    int k = 0;

    while (a < aFence && b < bFence) {
      if (A[a] < B[b])
        out[k++] = A[a++];
      else
        out[k++] = B[b++];
    }

    while (a < aFence)
      out[k++] = A[a++];
    while (b < bFence)
      out[k++] = B[b++];
  }

  protected int findSplit(int value, int B[]) {
    int low = 0;
    int high = B.length;
    while (low < high) {
      int middle = low + ((high - low) >>> 1);
      if (value <= B[middle])
        high = middle;
      else
        low = middle + 1;
    }
    return high;
  }

  /** A standard sequential quicksort **/
  protected void quickSort(int array[], int lo, int hi) {
//    int lo = 0;
//    int hi = array.length - 1;
    // If under threshold, use insertion sort
    int[] arr = array;
    if (hi - lo + 1l <= INSERTION_SIZE) {
      for (int i = lo + 1; i <= hi; i++) {
        int t = arr[i];
        int j = i - 1;
        while (j >= lo && arr[j] > t) {
          arr[j + 1] = arr[j];
          --j;
        }
        arr[j + 1] = t;
      }
      return;
    }

    // Use median-of-three(lo, mid, hi) to pick a partition.
    // Also swap them into relative order while we are at it.

    int mid = (lo + hi) >>> 1;

    if (arr[lo] > arr[mid]) {
      int t = arr[lo];
      arr[lo] = arr[mid];
      arr[lo] = arr[mid];
      arr[mid] = t;
    }
    if (arr[mid] > arr[hi]) {
      int t = arr[mid];
      arr[mid] = arr[hi];
      arr[hi] = t;

      if (arr[lo] > arr[mid]) {
        t = arr[lo];
        arr[lo] = arr[mid];
        arr[mid] = t;
      }

    }

    int left = lo + 1; // start one past lo since already handled lo
    int right = hi - 1; // similarly

    int partition = arr[mid];

    while(true){

      while (arr[right] > partition)
        --right;

      while (left < right && arr[left] <= partition)
        ++left;

      if (left < right) {
        int t = arr[left];
        arr[left] = arr[right];
        arr[right] = t;
        --right;
      } else
        break;

    }

    quickSort(arr,lo,left+1);
    quickSort(arr,left+1,hi);

  }

}
