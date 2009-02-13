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

int MINIMUMDATE = 19000101;
float EPSILON = (float)10.0 * (float)(4.9E-324);

struct PathId {
	char * name;
	int startDate;
	int endDate;
	float dTime;
};

struct RatePath {
  float * pathValue;
  int pathLength;
  int * pathDate;
  int nAcceptedPathValue;
  struct PathId pathId;
};

struct ReturnPath {
  int COMPOUNDED; // 1: compount; 0: noncompound
  float * pathValue;
  int pathLength;
  int nPathValue;
  int returnDefinition;
  float expectedReturnRate;
  float volatility;
  float volatility2;
  float mean;
  float variance;
  struct PathId pathId;
};

struct MonteCarloPath {
  float * fluctuations;
  int fluctuationsLen;
  float * pathValue;
  int pathValueLen;
  int returnDefinition;
  float expectedReturnRate;
  float volatility;
  int nTimeSteps;
  float pathStartValue;
  struct PathId pathId;
};

struct PriceStock{
  struct MonteCarloPath mcPath;
  long randomSeed;
  float pathStartValue;
  float expectedReturnRate;
  float volatility;
  float volatility2;
  float finalStockPrice;
  float * pathValue;
};

struct MyRandom {
  int iseed;
  float v1;
  float v2;
};

#define nLines 200

int nTimeStepsMC = 1000;
int nruns = 32 * 16;
float dTime = (float)1.0/(float)365.0;
float pathStartValue = (float)100.0;
float* pathValue;
int* pathDate;
int nAcceptedPathValue = 0;
float* returnPathValue;
struct RatePath avgMCrate;
float avgExpectedReturnRateMC = (float)0.0;
float avgVolatilityMC = (float)0.0;
float JGFavgExpectedReturnRateMC = (float)0.0;
struct RatePath ratePath;
struct ReturnPath returnPath;

void begin(void);
void init(void);
void estimatePath(struct ReturnPath* returnPath);
void computeMean(struct ReturnPath* returnPath);
void computeVariance(struct ReturnPath* returnPath);
void computeExpectedReturnRate(struct ReturnPath* returnPath);
void computeVolatility(struct ReturnPath* returnPath);
int computeFluctuationsGaussian(long randomSeed, 
                                struct MonteCarloPath* mcPath);
void computePathValue(float startValue,  
                      struct MonteCarloPath* mcPath);
int inc_pathValue(float * operandPath, 
                  int len, 
                  struct RatePath* ratePath);
int inc_pathValue_int(float scale, 
                      struct RatePath* ratePath);
float seed(struct MyRandom* rnd);
float update(struct MyRandom* rnd);
void run(int id);

#ifndef RAW
int main(int argc, char **argv) {
  begin();
  return 0;
}
#endif

void begin(void) {
    int i = 0;

	pathValue = (float *)(malloc(sizeof(float) * nLines));
	pathDate = (int *)(malloc(sizeof(int) * nLines));
    
    avgMCrate.pathId.name = "MC";
	avgMCrate.pathId.startDate = 19990109;
	avgMCrate.pathId.endDate = 19991231;
	avgMCrate.pathId.dTime = dTime;
	avgMCrate.pathValue = (float *)malloc(sizeof(float) * nTimeStepsMC);
	avgMCrate.nAcceptedPathValue = nTimeStepsMC;

	init();

	/* Main loop: */
	for(i = 0; i < nruns; ++i) {
		run(i);
	}
	
	inc_pathValue_int((float)1.0/((float)nruns), &avgMCrate);
	avgExpectedReturnRateMC /= nruns;
	avgVolatilityMC         /= nruns;
	JGFavgExpectedReturnRateMC = avgExpectedReturnRateMC;
	    
#ifdef RAW
    raw_test_pass(raw_get_cycle());
	raw_test_done(1);
#endif
}

void run(int id) {	
    int iRun = id;
    int k;

    struct PriceStock ps;
    ps.randomSeed = (long)iRun*11;
    ps.pathStartValue = pathStartValue;
    ps.expectedReturnRate=(float)0.0;
    ps.volatility=(float)0.0;
    ps.volatility2=(float)0.0;
    ps.finalStockPrice=(float)0.0;
    ps.mcPath.pathId.name = returnPath.pathId.name;
    ps.mcPath.pathId.startDate = returnPath.pathId.startDate;
    ps.mcPath.pathId.endDate = returnPath.pathId.endDate;
    ps.mcPath.pathId.dTime = returnPath.pathId.dTime; 
    ps.mcPath.expectedReturnRate = returnPath.expectedReturnRate;
    ps.mcPath.volatility = returnPath.volatility;
    ps.mcPath.pathStartValue = pathStartValue;
    ps.mcPath.returnDefinition = returnPath.returnDefinition;       
    ps.mcPath.nTimeSteps = nTimeStepsMC;
    ps.mcPath.fluctuationsLen = nTimeStepsMC;
    ps.mcPath.fluctuations = (float *)malloc(sizeof(float) * nTimeStepsMC);
    ps.mcPath.pathValue = (float *)malloc(sizeof(float) * nTimeStepsMC);
    ps.mcPath.pathValueLen = nTimeStepsMC;

    computeFluctuationsGaussian(ps.randomSeed, &ps.mcPath);
    computePathValue(ps.pathStartValue, &ps.mcPath);

    struct RatePath rateP; 
    rateP.pathId.name = ps.mcPath.pathId.name;
    rateP.pathId.startDate = ps.mcPath.pathId.startDate;
    rateP.pathId.endDate = ps.mcPath.pathId.endDate;
    rateP.pathId.dTime = ps.mcPath.pathId.dTime;
    //
    // Fields pertaining to RatePath object itself.
    rateP.pathValue = ps.mcPath.pathValue;
    rateP.nAcceptedPathValue = ps.mcPath.nTimeSteps;
    //
    // Note that currently the pathDate is neither declared, defined,
    // nor used in the MonteCarloPath object.
    rateP.pathDate = (int *)malloc(sizeof(int) * nAcceptedPathValue);
        
    struct ReturnPath returnP;
    // create corresponding COMPOUND returnPath()
    float * returnPathValue = (float *)malloc(sizeof(float) * 
                                              rateP.nAcceptedPathValue);
    returnPathValue[0] = (float)0.0;
    for(k=1; k< rateP.nAcceptedPathValue; k++ ) {
 	returnPathValue[k] = logf(rateP.pathValue[k] / 
  	                          rateP.pathValue[k-1]);
	}
	returnP.pathValue = returnPathValue;
    returnP.nPathValue = rateP.nAcceptedPathValue;
    returnP.returnDefinition = 1;
   	returnP.COMPOUNDED = 1;
   	returnP.expectedReturnRate = (float)0.0;
    returnP.volatility = (float)0.0;
   	returnP.volatility2 = (float)0.0;
    returnP.mean = (float)0.0;
   	returnP.variance = (float)0.0;
	//
	// Copy the PathId information to the ReturnPath object.
	returnP.pathId.name = rateP.pathId.name;
	returnP.pathId.startDate = rateP.pathDate[0];
	returnP.pathId.endDate = rateP.pathDate[rateP.nAcceptedPathValue-1];
	returnP.pathId.dTime = (float)(1.0/365.0);
	estimatePath(&returnP);
	estimatePath(&returnP);

    ps.expectedReturnRate = returnP.expectedReturnRate;
    ps.volatility = returnP.volatility;
    ps.volatility2 = returnP.volatility2;
    ps.finalStockPrice = rateP.pathValue[rateP.pathLength-1];
    ps.pathValue = ps.mcPath.pathValue;

    inc_pathValue(ps.mcPath.pathValue, ps.mcPath.pathValueLen, &avgMCrate);
    avgExpectedReturnRateMC += ps.mcPath.expectedReturnRate;
    avgVolatilityMC         += ps.mcPath.volatility;
}

void init(void) {
	// create a RatePath()
	int year = 88;
	int month = 10;
	int day = 3;

	int iLine=0;
	int k = 0;
	int j = 0;
	int aDate = 19881003;
	for(k = 0; k < 40; k++) {
	    for(j = 0; j < 5; j++) {
			day++;
			aDate++;
			if(month == 2) {
			    if(day == 29) {
					day = 1;
					month++;
					aDate += 72;
			    }
			} else {
		    	if(day == 31) {
					day = 1;
					month++;
					aDate += 70;
					if(month == 13) {
			    		month = 1;
			    		year++;
						aDate += 8800;
					}
		    	}
			}
			//
			// static float float.parsefloat() method is a feature of JDK1.2!
			int tmp = k+j;
			float aPathValue = (float)(121.7500 - tmp);
			if( (aDate <= MINIMUMDATE)) {
		    	//System.printString("Skipped erroneous data indexed by date="+
		    	                     //date+".");
			} else {
		    	pathDate[iLine] = aDate;
		    	pathValue[iLine] = aPathValue;
		    	iLine++;
			}
	    }
	}
	//
	// Record the actual number of accepted data points.
	nAcceptedPathValue = iLine;
	//
	// Now to fill in the structures from the 'PathId' class.
	ratePath.pathValue = pathValue;
	ratePath.pathLength = nLines;
	ratePath.pathDate = pathDate;
	ratePath.nAcceptedPathValue = nAcceptedPathValue;
	ratePath.pathId.name = "rate";
	ratePath.pathId.startDate = pathDate[0];
	ratePath.pathId.endDate = pathDate[nAcceptedPathValue-1];
	ratePath.pathId.dTime = (float)(1.0/365.0);

	// create corresponding COMPOUND returnPath()
	returnPathValue = (float *)malloc(sizeof(float) * nAcceptedPathValue);
	returnPathValue[0] = (float)0.0;
	for(k=1; k< nAcceptedPathValue; k++ ) {
	    returnPathValue[k] = logf(pathValue[k] / pathValue[k-1]);
	}
	returnPath.pathValue = returnPathValue;
    returnPath.nPathValue = nAcceptedPathValue;
    returnPath.returnDefinition = 1;
    returnPath.COMPOUNDED = 1;
    returnPath.expectedReturnRate = (float)0.0;
    returnPath.volatility = (float)0.0;
    returnPath.volatility2 = (float)0.0;
    returnPath.mean = (float)0.0;
    returnPath.variance = (float)0.0;
	//
	// Copy the PathId information to the ReturnPath object.
	returnPath.pathId.name = ratePath.pathId.name;
	returnPath.pathId.startDate = pathDate[0];
	returnPath.pathId.endDate = pathDate[nAcceptedPathValue-1];
	returnPath.pathId.dTime = (float)(1.0/365.0);
	estimatePath(&returnPath);
	estimatePath(&returnPath);
}

void estimatePath(struct ReturnPath* returnPath) {
	computeMean(returnPath);
    computeVariance(returnPath);
	computeExpectedReturnRate(returnPath);
    computeVolatility(returnPath);
}

void computeMean(struct ReturnPath* returnPath) {
	int i = 0;
    returnPath->mean = (float)0.0;
    for(i=1; i < returnPath->nPathValue; i++ ) {
      returnPath->mean += returnPath->pathValue[i];
    }
    returnPath->mean /= ((float)(returnPath->nPathValue - (float)1.0));
}

void computeVariance(struct ReturnPath* returnPath) {
	int i = 0;
    returnPath->variance = (float)0.0;    
    for(i=1; i < returnPath->nPathValue; i++ ) {
      returnPath->variance += (returnPath->pathValue[i] - returnPath->mean)*
                             (returnPath->pathValue[i] - returnPath->mean);
    }
    returnPath->variance /= ((float)(returnPath->nPathValue - (float)1.0));
}
 
void computeExpectedReturnRate(struct ReturnPath * returnPath) {
	returnPath->expectedReturnRate = returnPath->mean/(float)returnPath->pathId.dTime + 
	                                (float)0.5*returnPath->volatility2;
}

void computeVolatility(struct ReturnPath* returnPath) {
    returnPath->volatility2 = returnPath->variance / 
                             (float)returnPath->pathId.dTime;
    returnPath->volatility  = sqrtf(returnPath->volatility2);
}

int computeFluctuationsGaussian(long randomSeed, 
                                struct MonteCarloPath* mcPath) {
    float mean, sd, gauss,meanGauss, variance;
    int i;
    
	if( mcPath->nTimeSteps > mcPath->fluctuationsLen ) {
	    return 0;
	}
	//
	// First, make use of the passed in seed value.
	struct MyRandom rnd;
	float v1,v2, r;
	v1 = (float)0.0;
	v2 = (float)0.0;
	if( randomSeed == -1 ) {
	    rnd.iseed = 0;
	} else {
	    rnd.iseed = (int)randomSeed;
	}
	rnd.v1 = v1;
	rnd.v2 = v2;
	//
	// Determine the mean and standard-deviation, from the mean-drift and volatility.
	mean = (mcPath->expectedReturnRate-
	       (float)0.5*mcPath->volatility*mcPath->volatility)*
	        mcPath->pathId.dTime;
	sd   = mcPath->volatility*sqrtf(mcPath->pathId.dTime);
	gauss = (float)0.0;
	meanGauss=(float)0.0;
	variance=(float)0.0;
	for( i=0; i < mcPath->nTimeSteps; i += 2 ) {
	    r  = seed(&rnd);
	    gauss = r*rnd.v1;
	    meanGauss+= gauss;
	    variance+= (gauss*gauss);
	    //
	    // Now map this onto a general Gaussian of given mean and variance.
	    mcPath->fluctuations[i] = mean + sd*gauss;
	    //      dbgPrintln("gauss="+gauss+" fluctuations="+fluctuations[i]);
	    
	    gauss  = r*rnd.v2;
	    meanGauss+= gauss;
	    variance+= (gauss*gauss);
	    //
	    // Now map this onto a general Gaussian of given mean and variance.
	    mcPath->fluctuations[i+1] = mean + sd*gauss;
	}
	meanGauss/=(float)mcPath->nTimeSteps;
	variance /=(float)mcPath->nTimeSteps;
	//    dbgPrintln("meanGauss="+meanGauss+" variance="+variance);
	return 1;
}
    
void computePathValue(float startValue, 
                      struct MonteCarloPath* mcPath) {
    int i;
	mcPath->pathValue[0] = startValue;
	if( mcPath->returnDefinition == 1 | 
		mcPath->returnDefinition == 2) {
	    for(i=1; i < mcPath->nTimeSteps; i++ ) {
			mcPath->pathValue[i] = mcPath->pathValue[i-1] * 
			                      expf(mcPath->fluctuations[i]);
	    }
	}
}

float seed(struct MyRandom* rnd) {
	float s,u1,u2,r;
	s = (float)1.0;
	u1 = update(rnd);
	u2 = update(rnd);

	rnd->v1 = (float)2.0 * u1 - (float)1.0;
	rnd->v2 = (float)2.0 * u2 - (float)1.0;
	s = rnd->v1*rnd->v1 + rnd->v2*rnd->v2;
	s = s - (int)s;
	r = sqrtf((float)(-2.0*logf(s))/(float)s);
	return r;
}

float update(struct MyRandom* rnd) {
	float rand;
	float scale= (float)4.656612875e-10;
	int is1,is2,iss2;
	int imult= 16807;
	int imod = 2147483647;

	if (rnd->iseed<=0) { 
	    rnd->iseed = 1; 
	}

	is2 = rnd->iseed % 32768;
	is1 = (rnd->iseed-is2)/32768;
	iss2 = is2 * imult;
	is2 = iss2 % 32768;
	is1 = (is1 * imult+(iss2-is2) / 32768) % (65536);

	rnd->iseed = (is1 * 32768 + is2) % imod;

	rand = scale * rnd->iseed;

	return rand;
}

int inc_pathValue(float* operandPath, 
                  int len, 
                  struct RatePath* ratePath) {
	int i;
	if( ratePath->pathLength != len ) {
	    return 0;
	}
	for(i=0; i<len; i++ ) {
	    ratePath->pathValue[i] += operandPath[i];
	}
	return 1;
}

int inc_pathValue_int(float scale, 
                      struct RatePath* ratePath) {
    int i;
	if( ratePath->pathValue==NULL ) {
	    return 0;
	}
	for(i=0; i<ratePath->pathLength; i++ ) {
	    ratePath->pathValue[i] *= scale;
	}
	return 1;
}
