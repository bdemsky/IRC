/**
 * 4-way split version of merge sort
 */

public class MergeSort4 extends MergeSort {

  public static void main(String[] args) {
    int problemSize= 1048576;
    if(args.length>0){
      problemSize=Integer.parseInt(args[0]);
    }
    MergeSort4 sort = new MergeSort4();
    sort.run(problemSize);
  }
  
  public MergeSort4(){
    super();
  }
  
  public void serializedSort(int A[]) {

    if (A.length <= QUICK_SIZE) {
      quickSort(A, 0, A.length - 1);
    } else {

      int q = A.length / 4;

      int idxs0 = q;
      int idxs1 = 2 * q;
      int idxs2 = 3 * q;

      int size0 = idxs0;
      int size1 = idxs1 - idxs0;
      int size2 = idxs2 - idxs1;
      int size3 = A.length - idxs2;

      int[] A_quarters0 = new int[size0];
      int[] A_quarters1 = new int[size1];
      int[] A_quarters2 = new int[size2];
      int[] A_quarters3 = new int[size3];

      for (int i = 0; i < idxs0; i++) {
        A_quarters0[i] = A[i];
      }
      for (int i = idxs0; i < idxs1; i++) {
        A_quarters1[i - idxs0] = A[i];
      }
      for (int i = idxs1; i < idxs2; i++) {
        A_quarters2[i - idxs1] = A[i];
      }
      int amax=A.length;
      for (int i = idxs2; i < amax; i++) {
        A_quarters3[i - idxs2] = A[i];
      }

      int h1 = A_quarters0.length + A_quarters1.length;
      int h2 = A_quarters2.length + A_quarters3.length;
      int[] B_halves0 = new int[h1];
      int[] B_halves1 = new int[h2];

      serializedSort(A_quarters0);
      serializedSort(A_quarters1);
      serializedSort(A_quarters2);
      serializedSort(A_quarters3);

      sequentialMerge(A_quarters0, A_quarters1, B_halves0);
      sequentialMerge(A_quarters2, A_quarters3, B_halves1);
      sequentialMerge(B_halves0, B_halves1, A);

    }

  }

  public void sort(int A[]) {
    
    if(A.length<=SERIALIZED_CUT_OFF){
      serializedSort(A);
    }else{
      if (A.length <= QUICK_SIZE) {
        quickSort(A,0,A.length-1);
      } else {
  
        int q = A.length / 4;
  
        int idxs0 = q;
        int idxs1 = 2 * q;
        int idxs2 = 3 * q;
  
        int size0 = idxs0;
        int size1 = idxs1 - idxs0;
        int size2 = idxs2 - idxs1;
        int size3 = A.length - idxs2;
  
        int[] A_quarters0 = new int[size0];
        int[] A_quarters1 = new int[size1];
        int[] A_quarters2 = new int[size2];
        int[] A_quarters3 = new int[size3];
  
        for (int i = 0; i < idxs0; i++) {
          A_quarters0[i] = A[i];
        }
        for (int i = idxs0; i < idxs1; i++) {
          A_quarters1[i - idxs0] = A[i];
        }
        for (int i = idxs1; i < idxs2; i++) {
          A_quarters2[i - idxs1] = A[i];
        }
	int amax=A.length;
        for (int i = idxs2; i < amax; i++) {
          A_quarters3[i - idxs2] = A[i];
        }

        int h1 = A_quarters0.length+A_quarters1.length;
        int h2 = A_quarters2.length+A_quarters3.length;
  
        sese p1{
          sort(A_quarters0);
        }
        sese p2{
          sort(A_quarters1);
        }
        sese p3{
          sort(A_quarters2);
        }
	//don't spawn off sese for last one...
	sort(A_quarters3);
  
	merge(A_quarters0, A_quarters1, A_quarters2, A_quartes3, A);
      }
    }
  }

  public static void merge(int []a1, int []a2, int []a3, int[] a4, int[] a) {
    int i1=0;
    int i2=0;
    int i3=0;
    int i4=0;
    int alength=a.length;
    int v1=a1[0];
    int v2=a2[0];
    int v3=a3[0];
    int v4=a4[0];
    int v1m=a1.length;
    int v2m=a2.length;
    int v3m=a3.length;
    int v4m=a4.length;
    for(int i=0;i<alength;i++) {
      if (v1<v2) {
	if (v1<v3) {
	  if (v1<v4) {
	    a[i]=v1;
	    //v1 smallest
	    if (i1<v1m) {
	      v1=a1[i1++];
	    } else {
	      v1=2147483647;
	    }
	  } else {
	    //v4 smalles
	    if (i4<v4m) {
	      v4=a4[i4++];
	    } else {
	      v4=2147483647;
	    }
	  }
	} else {
	  if (v3<v4) {
	    //v3 smallest
	    if (i3<v3m) {
	      v3=a3[i3++];
	    } else {
	      v3=2147483647;
	    }
	  } else {
	    //v4 smallest
	    if (i4<v4m) {
	      v4=a4[i4++];
	    } else {
	      v4=2147483647;
	    }
	  }
	}
      } else {
	if (v2<v3) {
	  if (v2<v4) {
	    //v2 smallest
	    if (i2<v2m) {
	      v2=a2[i2++];
	    } else {
	      v2=2147483647;
	    }
	  } else {
	    //v4 smallest
	    if (i4<v4m) {
	      v4=a4[i4++];
	    } else {
	      v4=2147483647;
	    }
	  }
	} else {
	  if (v3<v4) {
	    //v3 smallest
	    if (i3<v3m) {
	      v3=a3[i3++];
	    } else {
	      v3=2147483647;
	    }
	  } else {
	    //v4 smallest
	    if (i4<v4m) {
	      v4=a4[i4++];
	    } else {
	      v4=2147483647;
	    }
	  }
	}
      }
    }
  }
}
