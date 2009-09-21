public class Array2D extends Thread {
    int [][][] array;

    public Array2D() {
    }
    
    public static void main(String [] argv) {
	Array2D a;
	atomic {
	    a=global new Array2D();
	}
	a.start((128<<24)|(195<<16)|(136<<8)|162);
	a.join();
	atomic {
	a.exec();
	}
    }

    public void exec() {
	    int xlength=array[0].length;
	    int ylength=array[0][0].length;
	    long sum;
	for(int ii=0;ii<10;ii++) {
	    int zz[][]=array[ii];
	    for(int i=0;i<xlength;i++) {
		int a[]=zz[i];
		for(int j=0;j<ylength;j++) {
		    sum+=a[j];
		}
	    }
	}
    }
    
    public void run() {
        atomic {
	int xmax=32000;
	int ymax=4;
	array=global new int[10][xmax][ymax];
	for(int ii=0;ii<10;ii++) {
	for(int i=0;i<xmax;i++) {
	    for(int j=0;j<ymax;j++) {
		array[ii][i][j]=i*j;
	    }
	}
	}
	}
    }
}
