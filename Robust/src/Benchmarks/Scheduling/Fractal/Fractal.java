/*  Draw a Mandelbrot set, maximum magnification 10000000 times;
 */
task t1(StartupObject s{initialstate}) {
    //System.printString("task t1\n");
    
    int width = 62 * 14; 
    int height = 62 * 14;
    int group = 62;

    //int h = height / group;
	//Random rnd = new Random();
	//int maxint = (~0x1) + 1;
	int red   = 255;  //(int)(((float)rnd.nextInt()/maxint)*255);
	int green = 237;  //(int)(((float)rnd.nextInt()/maxint)*255);
	int blue  = 100;  //(int)(((float)rnd.nextInt()/maxint)*255);
    for(int i = 0; i < group; i++) {
	Fractal fratal = new Fractal(i,
		                     group,
		                     width,
		                     height,
							 red,
							 green,
							 blue){run};
    }
    Image image = new Image(group){!finish};
    
    taskexit(s{!initialstate});
}

task t2(Fractal fractal{run}) {
    //System.printString("task t2\n");
    
    //  Now do the computation.
    fractal.run();
    
    taskexit(fractal{!run, output});
}

task t3(Image image{!finish}, Fractal fractal{output}) {
    //System.printString("task t3\n");

    if(image.outputImage(fractal.pixels, fractal.id)) {
	//System.printString("Finish!\n");
	taskexit(image{finish}, fractal{!output});
    } else {
	taskexit(fractal{!output});
    }
}

public class Fractal {
    flag run;
    flag output;
    
    public int id;
    public int group;
    public int AppletWidth;
    private int AppletHeight;
    private float amin;
    private float amax;
    private float bmin;
    private float bmax;
    private float alen, blen;
    public int[] pixels;
    int alpha;
    int red;
    int green;
    int blue;
    int times;
    
    public Fractal(int index,
	           int group,
	           int width, 
	           int height,
			   int red,
			   int green,
			   int blue) {
	this.id = index;
	this.group = group;
	this.AppletWidth = width;
	this.AppletHeight = height;
	this.amin = (float)-2.0;
	this.amax =  (float)1.0;
	this.bmin = (float)-1.5;
	this.bmax =  (float)1.5;
	this.alen = (float)3.0;//this.amax - this.amin;
	this.blen = (float)3.0;//this.bmax - this.bmin;
	this.alpha = 0xff;
	this.red = red;
	this.green = green;
	this.blue = blue;
	this.times = 255;
	int length = this.AppletWidth * this.AppletHeight / this.group;
	this.pixels = new int[length];
	int[] ps = this.pixels;
	int incr=0;
	while (incr < length) {
	    ps[incr++] = this.alpha<<24 | 0x00<<16 | 0x00<<8 | 0xff;
	}
    }

    public void run () {
	float amin = this.amin;
	float amax = this.amax;
	float bmin = this.bmin;
	float bmax = this.bmax;
	int appletWidth = this.AppletWidth;
	int appletHeight = this.AppletHeight;
	int times = this.times;
	int alpha = this.alpha;
	int red = this.red;
	int blue = this.blue;
	float a,b,x,y; //a--width, b--height
	int scaleda,scaledb;
	float adelta = (this.alen/appletWidth);
	float bdelta = (this.blen/appletHeight);
	int[] ps = this.pixels;
	int length = ps.length;
	int id = this.id;
	int group = this.group;
	float startb = bmin + bdelta * id;
	float bspan = bdelta * group;
	for(a=amin;a<amax;a+=adelta) {
	    for(b=startb;b<bmax;b+=bspan) {
		x=(float)0.0;
		y=(float)0.0;
		int iteration=0;
		float x2 = (float)0.0;
		float y2 = (float)0.0;
		float xy = (float)0.0;
		boolean finish = true; //(x2 + y2 <= 4.0) & (iteration != times);
		while(finish) {
		    float tmpy = (float)2.0*xy;
		    x = x2 - y2 + a;
		    y = tmpy + b;
		    x2 = x*x;
		    y2 = y*y;
		    xy = x*y;
		    iteration++;
		    boolean tmpf = (x2 + y2 <= 4.0);
		    finish = tmpf & (iteration != times);
		}
		if(iteration<=times & iteration>0) {
		    scaleda=(int)((a - amin)*appletWidth/(amax - amin));
		    scaledb=(int)((b - bmin)*appletHeight/(bmax - bmin));
		    int index = ((scaledb - id) / group) * appletWidth + scaleda;  //(scaledb * appletWidth + scaleda - id) / group;
		    if(index < length) {
			ps[index] = alpha<<24 | red<<16 | iteration<<8 | blue;
		    }
		}
	    }
	}
    }
}

public class Image {
    flag finish;
    
    private int group;
    private int counter;
    private int[][] pixels;
    private String outputfile;
    
    public Image(int g) {
	this.group = g;
	this.counter = 0;
	this.pixels = new int[g][];
	this.outputfile = new String("/scratch/fractal/image.dat");
    }
    
    public boolean outputImage(int[] pixels, int index) {
	this.counter++;

	this.pixels[index] = pixels;

	boolean isFinish = (this.group == this.counter);
	if(isFinish) {
	    // output the image
	    /*FileOutputStream oStream = new FileOutputStream(outputfile, true);
	    //System.printString(new String(ps, 0, ps.length) + "\n");
	    oStream.write(ps, 0, ps.length);
	    oStream.close();*/
	}
	return isFinish;
    }
}
