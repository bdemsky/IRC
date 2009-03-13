public class Array extends Thread {
  int [][][] array;

  public Array() {
    int xmax=100;
    int ymax=4000;
    int zmax=10;
    array=global new int[xmax][ymax][zmax];
    for(int i=0;i<xmax;i++) {
	for(int j=0;j<ymax;j++) {
	    for(int k=0;k<zmax;k++) {
		array[i][j][k]=i*j+k;
	    }
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
    short[] offsets = new short[6];
    offsets[0] = getoffset{Array, array};
    offsets[1] = (short) 0;
    offsets[2] = (short) 0;
    offsets[3] = (short) 100;
    offsets[4] = (short) 0;
    offsets[5] = (short) 4000;
    System.rangePrefetch(this, offsets);

    atomic {
      int xlength=array.length;
      int ylength=array[0].length;
      int zlength=array[0][0].length;
      long sum;
      for(int i=0;i<xlength;i++) {
        int a[][]=array[i];
        for(int j=0;j<ylength;j++) {
	    int a2[]=a[j];
	    for(int k=0;k<zlength;k++) {
		sum+=a2[k];
	    }
        }
      }
    }
  }
}
