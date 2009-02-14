/*  Draw a Mandelbrot set, maximum magnification 10000000 times;
 */
#include <math.h>
#ifdef RAW
#include <raw.h>
#else
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#endif

const int AppletWidth = 3200; 
const int AppletHeight = 3200;
int length;
const float amin = (float)-2.0;
const float amax = (float)1.0;
const float bmin = (float)-1.5;
const float bmax = (float)1.5;
const float alen = (float)3.0;
const float blen = (float)3.0;
const int times = 255;
int alpha = 0xff;
int red = 0xff;
int green = 0xff;
int blue = 0xff;
int* pixels;

void begin(void);
void run(void);

#ifndef RAW
int main(int argc, char **argv) {
  int option;

  begin();
  return 0;
}
#endif

void begin(void){
    length = AppletWidth * AppletHeight;
    pixels = (int*)malloc(sizeof(int) * length);;
	int incr=0;
	while (incr < length) {
	    pixels[incr++] = alpha<<24 | 0x00<<16 | 0x00<<8 | 0xff;
	}
	int maxint = RAND_MAX;
	red   = (int)(((float)rand()/maxint)*255);
	green = (int)(((float)rand()/maxint)*255);
	blue  = (int)(((float)rand()/maxint)*255);
    run();
    
    free(pixels);

#ifdef RAW
    raw_test_pass(raw_get_cycle());
	raw_test_done(1);
#endif
}

void run () {
	float a,b,x,y; //a--width, b--height
	int scaleda,scaledb;
	float adelta = (float)(alen/AppletWidth);
	float bdelta = (float)(blen/AppletHeight);
	for(a=amin;a<amax;a+=adelta) {
	    for(b=bmin;b<bmax;b+=bdelta) {
		    x=(float)0.0;
		    y=(float)0.0;
    		int iteration=0;
    		float x2 = (float)0.0;
	    	float y2 = (float)0.0;
		    float xy = (float)0.0;
		    int finish = 1; //(x2 + y2 <= 4.0) & (iteration != times);
		    while(finish) {
		        float tmpy = (float)2.0*xy;
		        x = x2 - y2 + a;
		        y = tmpy + b;
		        x2 = x*x;
		        y2 = y*y;
		        xy = x*y;
		        iteration++;
		        int tmpf = (x2 + y2 <= 4.0);
		        finish = tmpf & (iteration != times);
		    }
		    if(iteration<=times & iteration>0) {
		        scaleda=(int)((a - amin)*AppletWidth/(amax - amin));
		        scaledb=(int)((b - bmin)*AppletHeight/(bmax - bmin));
		        int index = scaledb * AppletWidth + scaleda;
		        pixels[index] = alpha<<24 | red<<16 | iteration<<8 | blue;
		    }
	    }
	}
	
	// output image
}
