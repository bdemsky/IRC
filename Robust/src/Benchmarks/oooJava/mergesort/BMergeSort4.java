/**
 * 4-way split version of merge sort
 */

public class MergeSort4 extends MergeSort {
  
  public  boolean validationTest;

  public static void main(String[] args) {
    int problemSize = 4194304;
    int parallelBranch=32;
    
    MergeSort4 sort = new MergeSort4();
    
    if (args.length > 0) {
      problemSize = Integer.parseInt(args[0]);
    }
    if (args.length > 1) {
      parallelBranch = Integer.parseInt(args[1]);
    }
    if (args.length > 2){
     sort.validationTest=true;
    }
    sort.run(problemSize,parallelBranch);
  }

  public MergeSort4() {
    super();
    validationTest=false;
  }

  public void runWorkAndTest() {
    sese run{
      long startT=System.currentTimeMillis();
      int output[]=sort(input, 0, input.length);
      long endT=System.currentTimeMillis();
      System.out.println("running time="+(endT-startT));
    }
    if(validationTest){
      sese test{
        checkSorted(output);
      }
    }
  }

  public int[] serializedSort(int A[], int low, int high) {

    int length = high - low;

    if (length <= QUICK_SIZE) {
      int[] R = new int[length];
      int max = R.length;
      int j = low;
      for (int i = 0; i < max; i++) {
          R[i] = A[j++];
      }
      quickSort(R, 0, R.length - 1);
      return R;
    } else {

      int q = length / 4;
      int idxs0 = q + low;
      int idxs1 = (2 * q) + low;
      int idxs2 = (3 * q) + low;

      int[] A_quarters0 = serializedSort(A, low, idxs0);
      int[] A_quarters1 = serializedSort(A, idxs0, idxs1);
      int[] A_quarters2 = serializedSort(A, idxs1, idxs2);
      int[] A_quarters3 = serializedSort(A, idxs2, idxs2 + q);

      int[] R = new int[high - low];

      merge(A_quarters0, A_quarters1, A_quarters2, A_quarters3, R);
      return R;
    }
  }

  public int[] sort(int A[], int low, int high) {
    
    int length=high-low;
    
    if (length <= SERIALIZED_CUT_OFF) {
      return serializedSort(A, low, high);
    } else {
      if (length <= QUICK_SIZE) {
        int[] R = new int[length];
        int max = R.length;
        int j = low;
        for (int i = 0; i < max; i++) {
            R[i] = A[j++];
        }
        quickSort(R, 0, R.length-1);
        return R;
      } else {
        
        int q=length/4;
        int idxs0 = q+low;
        int idxs1 = (2 * q)+low;
        int idxs2 = (3 * q)+low;

        sese p1{
          int[] A_quarters0 = sort(A, low, idxs0);
        }
        sese p2{
          int[] A_quarters1 = sort(A, idxs0, idxs1);
        }
        sese p3{
          int[] A_quarters2 = sort(A, idxs1, idxs2);
        }
        // don't spawn off sese for last one...
        int[] A_quarters3 = sort(A, idxs2, idxs2+q);

        int[] R = new int[high - low];

        merge(A_quarters0, A_quarters1, A_quarters2, A_quarters3, R);
        return R;
      }
    }
  }

  public static void merge(int[] a1, int[] a2, int[] a3, int[] a4, int[] a) {
    int i1 = 0;
    int i2 = 0;
    int i3 = 0;
    int i4 = 0;
    int alength = a.length;
    int v1 = a1[0];
    int v2 = a2[0];
    int v3 = a3[0];
    int v4 = a4[0];
    int v1m = a1.length;
    int v2m = a2.length;
    int v3m = a3.length;
    int v4m = a4.length;
    for (int i = 0; i < alength; i++) {
      if (v1 < v2) {
        if (v1 < v3) {
          if (v1 < v4) {
            a[i] = v1;
            // v1 smallest
            if (i1 < v1m) {
              v1 = a1[i1++];
            } else {
              v1 = 2147483647;
            }
          } else {
            a[i] = v4;
            // v4 smalles
            if (i4 < v4m) {
              v4 = a4[i4++];
            } else {
              v4 = 2147483647;
            }
          }
        } else {
          if (v3 < v4) {
            a[i] = v3;
            // v3 smallest
            if (i3 < v3m) {
              v3 = a3[i3++];
            } else {
              v3 = 2147483647;
            }
          } else {
            a[i] = v4;
            // v4 smallest
            if (i4 < v4m) {
              v4 = a4[i4++];
            } else {
              v4 = 2147483647;
            }
          }
        }
      } else {
        if (v2 < v3) {
          if (v2 < v4) {
            a[i] = v2;
            // v2 smallest
            if (i2 < v2m) {
              v2 = a2[i2++];
            } else {
              v2 = 2147483647;
            }
          } else {
            a[i] = v4;
            // v4 smallest
            if (i4 < v4m) {
              v4 = a4[i4++];
            } else {
              v4 = 2147483647;
            }
          }
        } else {
          if (v3 < v4) {
            a[i] = v3;
            // v3 smallest
            if (i3 < v3m) {
              v3 = a3[i3++];
            } else {
              v3 = 2147483647;
            }
          } else {
            a[i] = v4;
            // v4 smallest
            if (i4 < v4m) {
              v4 = a4[i4++];
            } else {
              v4 = 2147483647;
            }
          }
        }
      }
    }
  }

}
