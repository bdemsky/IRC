/** Banboo Version  **/

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
 *      Original version of this code by Hon Yau (hwyau@epcc.ed.ac.uk)     *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/


/**
 * Code, a test-harness for invoking and driving the Applications
 * Demonstrator classes.
 *
 * <p>To do:
 * <ol>
 *   <li>Very long delay prior to connecting to the server.</li>
 *   <li>Some text output seem to struggle to get out, without
 *       the user tapping ENTER on the keyboard!</li>
 * </ol>
 *
 * @author H W Yau
 * @version $Revision: 1.1 $ $Date: 2008/08/18 22:22:20 $
 */
public class AppDemo {
    flag merge;
    flag validate;
    
    //------------------------------------------------------------------------
    // Class variables.
    //------------------------------------------------------------------------

    public float JGFavgExpectedReturnRateMC;

    public int Serial;
    //------------------------------------------------------------------------
    // Instance variables.
    //------------------------------------------------------------------------
    /**
     * The number of time-steps which the Monte Carlo simulation should
     * run for.
     */
    public int nTimeStepsMC;
    /**
     * The number of Monte Carlo simulations to run.
     */
    public int nRunsMC;
    
    public int group;
    /**
     * The default duration between time-steps, in units of a year.
     */
    private float dTime;
    /**
     * Flag to determine whether initialisation has already taken place.
     */
    private boolean initialised;
    /**
     * Variable to determine which deployment scenario to run.
     */
    private int runMode;

    public Vector results;

    PriceStock psMC;
    public float pathStartValue;
    float avgExpectedReturnRateMC;
    float avgVolatilityMC;
    
    int counter;

    RatePath avgMCrate;
    
    public ToInitAllTasks initAllTasks;

    public AppDemo(int nTimeStepsMC, int nRunsMC, int group) {
	this.JGFavgExpectedReturnRateMC = (float)0.0;
	this.Serial = 1;

	this.nTimeStepsMC   = nTimeStepsMC;
	this.nRunsMC        = nRunsMC;
	this.group          = group;
	this.initialised    = false;

	this.dTime = (float)1.0/(float)365.0;
	this.pathStartValue = (float)100.0;
	this.avgExpectedReturnRateMC = (float)0.0;
	this.avgVolatilityMC = (float)0.0;
	
	this.counter = 0;
	
	this.avgMCrate = new RatePath(this.nTimeStepsMC, "MC", 19990109, 19991231, this.dTime);
	
	this.initAllTasks = null;
    }
    /**
     * Single point of contact for running this increasingly bloated
     * class.  Other run modes can later be defined for whether a new rate
     * should be loaded in, etc.
     * Note that if the <code>hostname</code> is set to the string "none",
     * then the demonstrator runs in purely serial mode.
     */

    /**
     * Initialisation and Run methods.
     */
    public void initSerial() { 
	//
	// Measure the requested path rate.
	//System.printI(0xf0);
	RatePath rateP = new RatePath();
	//System.printI(0xf1);
	//rateP.dbgDumpFields();
	ReturnPath returnP = rateP.getReturnCompounded();
	//System.printI(0xf2);
	returnP.estimatePath();
	//System.printI(0xf3);
	//returnP.dbgDumpFields();
	float expectedReturnRate = returnP.get_expectedReturnRate();
	float volatility         = returnP.get_volatility();
	initAllTasks = new ToInitAllTasks(returnP, nTimeStepsMC, pathStartValue);
	this.counter = 0;
	//System.printI(0xf4);
	return;
    }

    //------------------------------------------------------------------------
    /**
     * Method for doing something with the Monte Carlo simulations.
     * It's probably not mathematically correct, but shall take an average over
     * all the simulated rate paths.
     *
     * @exception DemoException thrown if there is a problem with reading in
     *            any values.
     */
    boolean processResults(Vector returnMCs) {
	for(int i = 0; i < returnMCs.size(); i++) {
	    ToResult returnMC = (ToResult)returnMCs.elementAt(i);
	    avgMCrate.inc_pathValue(returnMC.get_pathValue());
	    avgExpectedReturnRateMC += returnMC.get_expectedReturnRate();
	    avgVolatilityMC         += returnMC.get_volatility();
	}

	this.counter++;
	if(this.counter == this.group) {
	    avgMCrate.inc_pathValue((float)1.0/((float)nRunsMC));
	    avgExpectedReturnRateMC /= nRunsMC;
	    avgVolatilityMC         /= nRunsMC;
	    JGFavgExpectedReturnRateMC = avgExpectedReturnRateMC;
	}
	
	return (this.counter == this.group);
    }
    //
    //------------------------------------------------------------------------
    // Accessor methods for class AppDemo.
    // Generated by 'makeJavaAccessor.pl' script.  HWY.  20th January 1999.
    //------------------------------------------------------------------------
    /**
     * Accessor method for private instance variable <code>nTimeStepsMC</code>.
     *
     * @return Value of instance variable <code>nTimeStepsMC</code>.
     */
    public int get_nTimeStepsMC() {
	return(this.nTimeStepsMC);
    }
    /**
     * Set method for private instance variable <code>nTimeStepsMC</code>.
     *
     * @param nTimeStepsMC the value to set for the instance variable <code>nTimeStepsMC</code>.
     */
    public void set_nTimeStepsMC(int nTimeStepsMC) {
	this.nTimeStepsMC = nTimeStepsMC;
    }
    /**
     * Accessor method for private instance variable <code>nRunsMC</code>.
     *
     * @return Value of instance variable <code>nRunsMC</code>.
     */
    public int get_nRunsMC() {
	return(this.nRunsMC);
    }
    /**
     * Set method for private instance variable <code>nRunsMC</code>.
     *
     * @param nRunsMC the value to set for the instance variable <code>nRunsMC</code>.
     */
    public void set_nRunsMC(int nRunsMC) {
	this.nRunsMC = nRunsMC;
    }
    /**
     * Accessor method for private instance variable <code>results</code>.
     *
     * @return Value of instance variable <code>results</code>.
     */
    public Vector get_results() {
	return(this.results);
    }
    /**
     * Set method for private instance variable <code>results</code>.
     *
     * @param results the value to set for the instance variable <code>results</code>.
     */
    public void set_results(Vector results) {
	this.results = results;
    }
    //------------------------------------------------------------------------
}

