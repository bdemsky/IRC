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
	a.start((128<<24)|(195<<16)|(175<<8)|71);
	a.join();
    }
    
    public void run() {
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
