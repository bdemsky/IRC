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
  
  public void serializedSort(int A[], int B[]){

    if (A.length <= QUICK_SIZE) {
      quickSort(A,0,A.length-1);
    } else {

      int q = A.length / 4;

      int[] idxs = new int[3];
      idxs[0] = q;
      idxs[1] = 2 * q;
      idxs[2] = 3 * q;

      int size0 = idxs[0];
      int size1 = idxs[1] - idxs[0];
      int size2 = idxs[2] - idxs[1];
      int size3 = A.length - idxs[2];

      int[] A_quarters0 = new int[size0];
      int[] A_quarters1 = new int[size1];
      int[] A_quarters2 = new int[size2];
      int[] A_quarters3 = new int[size3];

      for (int i = 0; i < idxs[0]; i++) {
        A_quarters0[i] = A[i];
      }
      for (int i = idxs[0]; i < idxs[1]; i++) {
        A_quarters1[i - idxs[0]] = A[i];
      }
      for (int i = idxs[1]; i < idxs[2]; i++) {
        A_quarters2[i - idxs[1]] = A[i];
      }
      for (int i = idxs[2]; i < A.length; i++) {
        A_quarters3[i - idxs[2]] = A[i];
      }

      int[] B_quarters0 = new int[size0];
      int[] B_quarters1 = new int[size1];
      int[] B_quarters2 = new int[size2];
      int[] B_quarters3 = new int[size3];

      for (int i = 0; i < idxs[0]; i++) {
        B_quarters0[i] = B[i];
      }
      for (int i = idxs[0]; i < idxs[1]; i++) {
        B_quarters1[i - idxs[0]] = B[i];
      }
      for (int i = idxs[1]; i < idxs[2]; i++) {
        B_quarters2[i - idxs[1]] = B[i];
      }
      for (int i = idxs[2]; i < B.length; i++) {
        B_quarters3[i - idxs[2]] = B[i];
      }

      int halfSize = B.length - (2 * q);
      int[] B_halves0 = new int[halfSize];
      int[] B_halves1 = new int[halfSize];

      sort(A_quarters0, B_quarters0);
      sort(A_quarters1, B_quarters1);
      sort(A_quarters2, B_quarters2);
      sort(A_quarters3, B_quarters3);

      merge(A_quarters0, A_quarters1, B_halves0);
      merge(A_quarters2, A_quarters3, B_halves1);
      merge(B_halves0, B_halves1, A);
      
    }
  
  }

  public void sort(int A[], int B[]) {
    
    if(A.length<=SERIALIZED_CUT_OFF){
      serializedSort(A, B);
    }else{
      if (A.length <= QUICK_SIZE) {
        quickSort(A,0,A.length-1);
      } else {
  
        int q = A.length / 4;
  
        int[] idxs = new int[3];
        idxs[0] = q;
        idxs[1] = 2 * q;
        idxs[2] = 3 * q;
  
        int size0 = idxs[0];
        int size1 = idxs[1] - idxs[0];
        int size2 = idxs[2] - idxs[1];
        int size3 = A.length - idxs[2];
  
        int[] A_quarters0 = new int[size0];
        int[] A_quarters1 = new int[size1];
        int[] A_quarters2 = new int[size2];
        int[] A_quarters3 = new int[size3];
  
        for (int i = 0; i < idxs[0]; i++) {
          A_quarters0[i] = A[i];
        }
        for (int i = idxs[0]; i < idxs[1]; i++) {
          A_quarters1[i - idxs[0]] = A[i];
        }
        for (int i = idxs[1]; i < idxs[2]; i++) {
          A_quarters2[i - idxs[1]] = A[i];
        }
        for (int i = idxs[2]; i < A.length; i++) {
          A_quarters3[i - idxs[2]] = A[i];
        }
  
        int[] B_quarters0 = new int[size0];
        int[] B_quarters1 = new int[size1];
        int[] B_quarters2 = new int[size2];
        int[] B_quarters3 = new int[size3];
  
        for (int i = 0; i < idxs[0]; i++) {
          B_quarters0[i] = B[i];
        }
        for (int i = idxs[0]; i < idxs[1]; i++) {
          B_quarters1[i - idxs[0]] = B[i];
        }
        for (int i = idxs[1]; i < idxs[2]; i++) {
          B_quarters2[i - idxs[1]] = B[i];
        }
        for (int i = idxs[2]; i < B.length; i++) {
          B_quarters3[i - idxs[2]] = B[i];
        }
  
        int halfSize = B.length - (2 * q);
        int[] B_halves0 = new int[halfSize];
        int[] B_halves1 = new int[halfSize];
  
        sese p1{
          sort(A_quarters0, B_quarters0);
        }
        sese p2{
          sort(A_quarters1, B_quarters1);
        }
        sese p3{
          sort(A_quarters2, B_quarters2);
        }
        sese p4{
          sort(A_quarters3, B_quarters3);
        }
  
        sese m1{
          merge(A_quarters0, A_quarters1, B_halves0);
        }
        
        sese m2{
          merge(A_quarters2, A_quarters3, B_halves1);
        }
        
        sese m3{
         merge(B_halves0, B_halves1, A);
        }
        
      }
    }
  }

}
