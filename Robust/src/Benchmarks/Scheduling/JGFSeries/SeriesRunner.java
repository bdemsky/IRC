/** Bristlecone Version  **/

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
 *                  Original version of this code by                       *
 *                 Gabriel Zachmann (zach@igd.fhg.de)                      *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/

/**
 * Class SeriesRunner
 *
 * Performs the transcendental/trigonometric portion of the
 * benchmark. This test calculates the nth fourier
 * coefficients of the function (x+1)^x defined on the interval
 * 0,2 (where n is an arbitrary number set in the constructor).
 *
 * The first four pairs of coefficients calculated shoud be:
 * (2.83777, 0), (1.04578, -1.8791), (0.2741, -1.15884), and
 * (0.0824148, -0.805759).
 */
public class SeriesRunner {
    flag finish;

    int id;

    public SeriesRunner(int id){
	this.id=id;
    }

    public void run() {
	//System.printI(0xa0);
	float pair[] = new float[2];
	// Calculate the fourier series. Begin by calculating A[0].
	if (id==0) {
	    pair[0] = TrapezoidIntegrate((float)0.0, //Lower bound.
		                         (float)2.0, // Upper bound.
		                         1000,        // # of steps.
		                         (float)0.0, // No omega*n needed.
		                         0) / (float)2.0; // 0 = term A[0].
	    //System.printI(0xa1);
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
	//System.printI(0xa3);
	//System.printString("coefficient NO.");
	//System.printI(id);
	//System.printI((int)(pair[0]*10000));
	//System.printI((int)(pair[1]*10000));

	// validate
	if(id < 4) {
	    //System.printI(0xa4);
	    float ref[][] = new float[4][2];
	    ref[0][0] = (float)2.87290112;
	    ref[0][1] = (float)0.0;
	    ref[1][0] = (float)1.11594856;
	    ref[1][1] = (float)-1.88199680;
	    ref[2][0] = (float)0.34412988;
	    ref[2][1] = (float)-1.16458096;
	    ref[3][0] = (float)0.15222694;
	    ref[3][1] = (float)-0.81435320;
	    //System.printI(0xa5);
	    for (int j = 0; j < 2; j++){
		//System.printI(0xa6);
		float error = Math.abs(pair[j] - ref[id][j]);
		if (error > 1.0e-7 ){
			//System.printI(0xa7);
		    //System.printString("Validation failed for coefficient " + j + "," + id + "\n");
		    //System.printString("Computed value = " + (int)(pair[j]*100000000) + "\n");
		    //System.printString("Reference value = " + (int)(ref[id][j]*100000000) + "\n");
			//System.printI((int)(pair[j]*10000));
			//System.printI((int)(ref[id][j]*10000));
		}
	    }
	}
	//System.printI(0xa8);
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

    private float TrapezoidIntegrate (float x0,     // Lower bound.
	                              float x1,     // Upper bound.
	                              int nsteps,    // # of steps.
	                              float omegan, // omega * n.
	                              int select)    // Term type.
    {
	float x;               // Independent variable.
	float dx;              // Step size.
	float rvalue;          // Return value.

	//System.printI(0xb0);
	// Initialize independent variable.

	x = x0;

	// Calculate stepsize.

	dx = (x1 - x0) / (float)nsteps;
	//System.printI((int)(dx * 1000000));
	//System.printI(0xb1);

	// Initialize the return value.

	rvalue = thefunction(x0, omegan, select) / (float)2.0;
	//System.printI((int)(rvalue * 1000000));
	//System.printI(0xb2);

	// Compute the other terms of the integral.

	if (nsteps != 1)
	{
	    //System.printI(0xb3);
	    --nsteps;               // Already done 1 step.
	    while (--nsteps > 0)
	    {
		//System.printI(0xb4);
		//System.printI(nsteps);
		x += dx;
		rvalue += thefunction(x, omegan, select);
		//System.printI((int)(rvalue * 1000000));
		//System.printI(0xb5);
	    }
	}

	// Finish computation.

	//System.printI(0xb6);
	rvalue=(float)(rvalue + thefunction(x1,omegan,select) / (float)2.0) * dx;
	//System.printI((int)(rvalue * 1000000));
	//System.printString("rvalue: " + (int)(rvalue * 10000) + "\n");
	//System.printI(0xb7);
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

    private float thefunction(float x,      // Independent variable.
	                      float omegan, // Omega * term.
	                      int select)    // Choose type.
    {

	// Use select to pick which function we call.
	//System.printI(0xc0);
	float result = (float)0.0;
	if(0 == select) {
	    //System.printI(0xc1);
	    result = Math.powf(x+(float)1.0,x);
	    //System.printI((int)(result * 1000000));
	} else if (1 == select) {
	    //System.printI(0xc2);
	    return(Math.powf(x+(float)1.0,x) * Math.cosf(omegan*x));
	} else if (2 == select) {
	    //System.printI(0xc3);
	    return(Math.powf(x+(float)1.0,x) * Math.sinf(omegan*x));
	}

	//System.printI(0xc4);
	// We should never reach this point, but the following
	// keeps compilers from issuing a warning message.
	return result;
    }    
}



















