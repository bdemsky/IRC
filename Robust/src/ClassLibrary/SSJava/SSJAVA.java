import Benchmarks.SSJava.EyeTracking.LOC;

public class SSJAVA {

  // Definitely written analysis assumes that the first parameter may have write
  // effects through the below methods

  static void arrayinit(float array[], float value) {
    for (int i = 0; i < array.length; i++) {
      array[i] = value;
    }
  }

  static void arrayinit(int array[], int value) {
    for (int i = 0; i < array.length; i++) {
      array[i] = value;
    }
  }

  static void arrayinit(float array[][][], int size_1, int size_2, int size_3, float value) {

    for (int idx1 = 0; idx1 < size_1; idx1++) {
      if (array[idx1].length != size_2) {
        throw new Error("Array initilizatiion failed to assign to all of elements.");
      }
      for (int idx2 = 0; idx2 < size_2; idx2++) {
        if (array[idx1][idx2].length != size_3) {
          throw new Error("Array initilizatiion failed to assign to all of elements.");
        }
        for (int idx3 = 0; idx3 < size_3; idx3++) {
          array[idx1][idx2][idx3] = value;
        }
      }
    }
  }

  static void arrayinit(float array[][], int size_1, int size_2, float value) {

    for (int idx1 = 0; idx1 < size_1; idx1++) {
      if (array[idx1].length != size_2) {
        throw new Error("Array initilizatiion failed to assign to all of elements.");
      }
      for (int idx2 = 0; idx2 < size_2; idx2++) {
        array[idx1][idx2] = value;
      }
    }
  }

  static void arraycopy(float array[][], float src[][], int size_1, int size_2) {

    for (int idx1 = 0; idx1 < size_1; idx1++) {
      if (array[idx1].length != size_2 || src[idx1].length != size_2) {
        throw new Error("Array initilizatiion failed to assign to all of elements.");
      }
      for (int idx2 = 0; idx2 < size_2; idx2++) {
        array[idx1][idx2] = src[idx1][idx2];
      }
    }
  }

  static void append(Object array[], Object item) {
    for (int i = 1; i < array.length; i++) {
      array[i - 1] = array[i];
      array[i]=null;
    }
    array[array.length - 1] = item;
  }

}
