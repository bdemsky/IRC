/** Single thread C Version  **/

/**************************************************************************
*                                                                         *
*         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
*                                                                         *
*                            produced by                                  *
*                                                                         *
*                  Java Grande Benchmarking Project                       *
*                                                                         *
*                                at                                       *
*                                                                         *
*                Edinburgh Parallel Computing Centre                      *
*                                                                         * 
*                email: epcc-javagrande@epcc.ed.ac.uk                     *
*                                                                         *
*                                                                         *
*      This version copyright (c) The University of Edinburgh, 2001.      *
*                         All rights reserved.                            *
*                                                                         *
**************************************************************************/

#ifdef RAW
#include <raw.h>
#else
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#endif
#include <math.h>

void begin(void);
void run(int id);
float TrapezoidIntegrate(float x0, float x1, int nsteps, float omegan, int select);
float thefunction(float x, float omegan, int select);

#ifndef RAW
int main(int argc, char **argv) {
  begin();
  return 0;
}
#endif

void begin(void) {
	int datasize = 16;
	int i = 0;
	/* Main loop: */
	for(i = 0; i < datasize; ++i) {
		run(i);
	}
#ifdef RAW
    raw_test_pass(raw_get_cycle());
	raw_test_done(1);
#endif
}

void run(int id) {
	float pair[2];
	// Calculate the fourier series. Begin by calculating A[0].
	if (id==0) {
	    pair[0] = TrapezoidIntegrate((float)0.0, //Lower bound.
			                         (float)2.0, // Upper bound.
			                         1000,        // # of steps.
		    	                     (float)0.0, // No omega*n needed.
		        	                 0) / (float)2.0; // 0 = term A[0].
	    pair[1] = 0;
	} else {
	    // Calculate the fundamental frequency.
	    // ( 2 * pi ) / period...and since the period
	    // is 2, omega is simply pi.
	    float omega = (float) 3.1415926535897932; // Fundamental frequency.

	    // Calculate A[i] terms. Note, once again, that we
	    // can ignore the 2/period term outside the integral
	    // since the period is 2 and the term cancels itself
	    // out.
	    pair[0] = TrapezoidIntegrate((float)0.0,
		            	             (float)2.0,
		                	         1000,
		                    	     omega * (float)id, 
		                        	 1);                       // 1 = cosine term.
	    //System.printI(0xa2);
	    // Calculate the B[i] terms.
	    pair[1] = TrapezoidIntegrate((float)0.0,
		                         	(float)2.0,
		                         	1000,
		                         	omega * (float)id,
		                         	2);                       // 2 = sine term.
	}

#ifdef RAW
	//raw_test_pass_reg(id);
	//raw_test_pass((int)(pair[0]*10000));
	//raw_test_pass((int)(pair[1]*10000));
#else
	printf("coefficient NO. %d: %f; %f \n", id, pair[0], pair[1]);
#endif

	// validate
	if(id < 4) {
		int j = 0;
	    float ref[4][2];
	    ref[0][0] = 2.8729524964837996;
	    ref[0][1] = 0.0;
	    ref[1][0] = 1.1161046676147888;
	    ref[1][1] = -1.8819691893398025;
	    ref[2][0] = 0.34429060398168704;
	    ref[2][1] = -1.1645642623320958;
	    ref[3][0] = 0.15238898702519288;
	    ref[3][1] = -0.8143461113044298;
	    for (j = 0; j < 2; j++){
		float error = abs(pair[j] - ref[id][j]);
		if (error > 1.0e-12 ){
#ifdef RAW
			//raw_test_pass(0xeeee);
#else
		    printf("Validation failed for coefficient %d:%d \n", j, id);
		    printf("Computed value = %f \n", pair[j]);
		    printf("Reference value = %f \n", ref[id][j]);
#endif
		}
	    }
	}
}

/*
 * TrapezoidIntegrate
 *
 * Perform a simple trapezoid integration on the function (x+1)**x.
 * x0,x1 set the lower and upper bounds of the integration.
 * nsteps indicates # of trapezoidal sections.
 * omegan is the fundamental frequency times the series member #.
 * select = 0 for the A[0] term, 1 for cosine terms, and 2 for
 * sine terms. Returns the value.
 */

float TrapezoidIntegrate (float x0,     // Lower bound.
                           float x1,     // Upper bound.
                           int nsteps,    // # of steps.
                           float omegan, // omega * n.
                           int select)  { // Term type.
	float x;               // Independent variable.
	float dx;              // Step size.
	float rvalue;          // Return value.

	// Initialize independent variable.
	x = x0;

	// Calculate stepsize.
	dx = (x1 - x0) / (float)nsteps;

	// Initialize the return value.
	rvalue = thefunction(x0, omegan, select) / (float)2.0;

	// Compute the other terms of the integral.
	if (nsteps != 1) {
	    --nsteps;               // Already done 1 step.
	    while (--nsteps > 0) {
		x += dx;
		rvalue += thefunction(x, omegan, select);
	    }
	}

	// Finish computation.
	rvalue=(rvalue + thefunction(x1,omegan,select) / (float)2.0) * dx;
	return(rvalue);
}

/*
 * thefunction
 *
 * This routine selects the function to be used in the Trapezoid
 * integration. x is the independent variable, omegan is omega * n,
 * and select chooses which of the sine/cosine functions
 * are used. Note the special case for select=0.
 */

float thefunction(float x,      // Independent variable.
	                       float omegan, // Omega * term.
	                       int select) {  // Choose type.

	// Use select to pick which function we call.
	float result = 0.0;
	if(0 == select) {
	    result = powf(x+(float)1.0,x);
	} else if (1 == select) {
	    return(powf(x+(float)1.0,x) * cosf(omegan*x));
	} else if (2 == select) {
	    return(powf(x+(float)1.0,x) * sinf(omegan*x));
	}

	// We should never reach this point, but the following
	// keeps compilers from issuing a warning message.
	return result;
}    
