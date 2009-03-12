public class Array extends Thread {
  int [][] array;

  public Array() {
    int xmax=10000;
    int ymax=10;
    array=global new int[xmax][ymax];
    for(int i=0;i<xmax;i++) {
      for(int j=0;j<ymax;j++) {
        array[i][j]=i*j;
      }
    }
  }

  public static void main(String [] argv) {
    Array a;
    atomic {
      a=global new Array();
    }
	a.start((128<<24)|(195<<16)|(136<<8)|162);
    a.join();
  }

  public void run() {
    short[] offsets = new short[4];
    offsets[0] = getoffset{Array, array};
    offsets[1] = (short) 0;
    offsets[2] = (short) 0;
    offsets[3] = (short) 100;
    System.rangePrefetch(this, offsets);
    atomic {
      int xlength=array.length;
      int ylength=array[0].length;
      long sum;
      for(int i=0;i<xlength;i++) {
        int a[]=array[i];
        for(int j=0;j<ylength;j++) {
          sum+=a[j];
        }
      }
    }
  }
}
