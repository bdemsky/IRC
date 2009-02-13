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
 * Class representing the paths generated by the Monte Carlo engine.
 *
 * <p>To do list:
 * <ol>
 *   <li><code>double[] pathDate</code> is not simulated.</li>
 * </ol>
 *
 * @author H W Yau
 * @version $Revision: 1.2 $ $Date: 2009/02/13 21:37:19 $
 */
public class MonteCarloPath extends PathId {

    //------------------------------------------------------------------------
    // Instance variables.
    //------------------------------------------------------------------------
    /**
     * Random fluctuations generated as a series of random numbers with
     * given distribution.
     */
    public float[] fluctuations;
    /**
     * The path values from which the random fluctuations are used to update.
     */
    public float[] pathValue;
    /**
     * Integer flag for determining how the return was calculated, when
     * used to calculate the mean drift and volatility parameters.
     */
    public int returnDefinition;
    /**
     * Value for the mean drift, for use in the generation of the random path.
     */
    public float expectedReturnRate;
    /**
     * Value for the volatility, for use in the generation of the random path.
     */
    public float volatility;
    /**
     * Number of time steps for which the simulation should act over.
     */
    public int nTimeSteps;
    /**
     * The starting value for of the security.
     */
    public float pathStartValue;
    //------------------------------------------------------------------------
    // Constructors.
    //------------------------------------------------------------------------
    /**
     * Default constructor.  Needed by the HPT library to start create
     * new instances of this class.  The instance variables for this should
     * then be initialised with the <code>setInitAllTasks()</code> method.
     */
    public MonteCarloPath() {
	super();
	this.expectedReturnRate = (float)0.0;
	this.volatility = (float)0.0;
	this.pathStartValue = (float)0.0;
	this.returnDefinition=1;
	this.nTimeSteps=0;
    }

    /**
     * Constructor, using the <code>ReturnPath</code> object to initialise
     * the necessary instance variables.
     *
     * @param returnPath Object used to define the instance variables in
     *                   this object.
     * @param nTimeSteps The number of time steps for which to generate the
     *                   random path.
     * @exception DemoException Thrown if there is a problem initialising the
     *                          object's instance variables.
     */
    public MonteCarloPath(ReturnPath returnPath, 
	                  int nTimeSteps) {
	/**
	 * These instance variables are members of PathId class.
	 */
	this.expectedReturnRate = (float)0.0;
	this.volatility = (float)0.0;
	this.pathStartValue = (float)0.0;
	this.returnDefinition=1;
	
	copyInstanceVariables(returnPath);
	this.nTimeSteps = nTimeSteps;
	this.pathValue = new float[nTimeSteps];
	this.fluctuations = new float[nTimeSteps];
    }
    /**
     * Constructor, where the <code>PathId</code> objects is used to ease
     * the number of instance variables to pass in.
     *
     * @param pathId Object used to define the identity of this Path.
     * @param returnDefinition How the statistic variables were defined,
     *                         according to the definitions in
     *                         <code>ReturnPath</code>'s two class variables
     *                         <code>COMPOUNDED</code> and
     *                         <code>NONCOMPOUNDED</code>.
     * @param expectedReturnRate The measured expected return rate for which to generate.
     * @param volatility The measured volatility for which to generate.
     * @param nTimeSteps The number of time steps for which to generate.
     * @exception DemoException Thrown if there is a problem initialising the
     *                          object's instance variables.
     */
    public MonteCarloPath(PathId pathId, 
	                  int returnDefinition, 
	                  float expectedReturnRate, 
	                  float volatility, 
	                  int nTimeSteps) {
	/**
	 * These instance variables are members of PathId class.
	 * Invoking with this particular signature should point to the
	 * definition in the PathId class.
	 */
	this.pathStartValue = (float)0.0;
	    
	copyInstanceVariables(pathId);
	this.returnDefinition   = returnDefinition;
	this.expectedReturnRate = expectedReturnRate;
	this.volatility         = volatility;
	this.nTimeSteps         = nTimeSteps;
	this.pathValue          = new float[nTimeSteps];
	this.fluctuations       = new float[nTimeSteps];
    }
    /**
     * Constructor, for when the user wishes to define each of the instance
     * variables individually.
     *
     * @param name The name of the security which this Monte Carlo path
     *             should represent.
     * @param startDate The date when the path starts, in 'YYYYMMDD' format.
     * @param endDate The date when the path ends, in 'YYYYMMDD' format.
     * @param dTime The interval in the data between successive data points
     *              in the generated path.
     * @param returnDefinition How the statistic variables were defined,
     *                         according to the definitions in
     *                         <code>ReturnPath</code>'s two class variables
     *                         <code>COMPOUNDED</code> and
     *                         <code>NONCOMPOUNDED</code>.
     * @param expectedReturnRate The measured mean drift for which to generate.
     * @param volatility The measured volatility for which to generate.
     * @param nTimeSteps The number of time steps for which to generate.
     */
    public MonteCarloPath(String name, 
	                  int startDate, 
	                  int endDate, 
	                  float dTime, 
	                  int returnDefinition, 
	                  float expectedReturnRate, 
	                  float volatility, 
	                  int nTimeSteps) {
	/**
	 * These instance variables are members of PathId class.
	 */
	this.name = name;
	this.startDate = startDate;
	this.endDate = endDate;
	this.dTime = dTime;
	this.returnDefinition   = returnDefinition;
	this.expectedReturnRate = expectedReturnRate;
	this.volatility         = volatility;
	this.nTimeSteps         = nTimeSteps;
	this.pathValue          = new float[nTimeSteps];
	this.fluctuations       = new float[nTimeSteps];
    }

    //------------------------------------------------------------------------
    // Methods.
    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    // Accessor methods for class MonteCarloPath.
    // Generated by 'makeJavaAccessor.pl' script.  HWY.  20th January 1999.
    //------------------------------------------------------------------------
    /**
     * Accessor method for private instance variable <code>fluctuations</code>.
     *
     * @return Value of instance variable <code>fluctuations</code>.
     * @exception DemoException thrown if instance variable <code>fluctuations</code> 
     * is undefined.
     */
    /*public float[] get_fluctuations() {
	return(this.fluctuations);
    }*/
    /**
     * Set method for private instance variable <code>fluctuations</code>.
     *
     * @param fluctuations the value to set for the instance variable 
     * <code>fluctuations</code>.
     */
    public void set_fluctuations(float[] fluctuations) {
	this.fluctuations = fluctuations;
    }
    /**
     * Accessor method for private instance variable <code>pathValue</code>.
     *
     * @return Value of instance variable <code>pathValue</code>.
     * @exception DemoException thrown if instance variable <code>pathValue</code> 
     * is undefined.
     */
    /*public float[] get_pathValue() {
	return(this.pathValue);
    }*/
    /**
     * Set method for private instance variable <code>pathValue</code>.
     *
     * @param pathValue the value to set for the instance variable <code>pathValue</code>.
     */
    public void set_pathValue(float[] pathValue) {
	this.pathValue = pathValue;
    }
    /**
     * Accessor method for private instance variable <code>returnDefinition</code>.
     *
     * @return Value of instance variable <code>returnDefinition</code>.
     * @exception DemoException thrown if instance variable <code>returnDefinition</code> 
     * is undefined.
     */
    /*public int get_returnDefinition() {
	return(this.returnDefinition);
    }*/
    /**
     * Set method for private instance variable <code>returnDefinition</code>.
     *
     * @param returnDefinition the value to set for the instance variable 
     * <code>returnDefinition</code>.
     */
    public void set_returnDefinition(int returnDefinition) {
	this.returnDefinition = returnDefinition;
    }
    /**
     * Accessor method for private instance variable <code>expectedReturnRate</code>.
     *
     * @return Value of instance variable <code>expectedReturnRate</code>.
     * @exception DemoException thrown if instance variable <code>expectedReturnRate</code> 
     * is undefined.
     */
    /*public float get_expectedReturnRate() {
	return(this.expectedReturnRate);
    }*/
    /**
     * Set method for private instance variable <code>expectedReturnRate</code>.
     *
     * @param expectedReturnRate the value to set for the instance variable 
     * <code>expectedReturnRate</code>.
     */
    public void set_expectedReturnRate(float expectedReturnRate) {
	this.expectedReturnRate = expectedReturnRate;
    }
    /**
     * Accessor method for private instance variable <code>volatility</code>.
     *
     * @return Value of instance variable <code>volatility</code>.
     * @exception DemoException thrown if instance variable <code>volatility</code> 
     * is undefined.
     */
    /*public float get_volatility() {
	return(this.volatility);
    }*/
    /**
     * Set method for private instance variable <code>volatility</code>.
     *
     * @param volatility the value to set for the instance variable 
     * <code>volatility</code>.
     */
    public void set_volatility(float volatility) {
	this.volatility = volatility;
    }
    /**
     * Accessor method for private instance variable <code>nTimeSteps</code>.
     *
     * @return Value of instance variable <code>nTimeSteps</code>.
     * @exception DemoException thrown if instance variable <code>nTimeSteps</code> 
     * is undefined.
     */
    /*public int get_nTimeSteps() {
	return(this.nTimeSteps);
    }*/
    /**
     * Set method for private instance variable <code>nTimeSteps</code>.
     *
     * @param nTimeSteps the value to set for the instance variable 
     * <code>nTimeSteps</code>.
     */
    public void set_nTimeSteps(int nTimeSteps) {
	this.nTimeSteps = nTimeSteps;
    }
    /**
     * Accessor method for private instance variable <code>pathStartValue</code>.
     *
     * @return Value of instance variable <code>pathStartValue</code>.
     * @exception DemoException thrown if instance variable <code>pathStartValue</code> 
     * is undefined.
     */
    /*public float get_pathStartValue() {
	return(this.pathStartValue);
    }*/
    /**
     * Set method for private instance variable <code>pathStartValue</code>.
     *
     * @param pathStartValue the value to set for the instance variable 
     * <code>pathStartValue</code>.
     */
    public void set_pathStartValue(float pathStartValue) {
	this.pathStartValue = pathStartValue;
    }
    //------------------------------------------------------------------------
    /**
     * Method for copying the suitable instance variable from a
     * <code>ReturnPath</code> object.
     *
     * @param obj Object used to define the instance variables which
     *            should be carried over to this object.
     * @exception DemoException thrown if there is a problem accessing the
     *                          instance variables from the target objetct.
     */
    private void copyInstanceVariables(ReturnPath obj) {
	//
	// Instance variables defined in the PathId object.
	this.name = obj.name;
	this.startDate = obj.startDate;
	this.endDate = obj.endDate;
	this.dTime = obj.dTime;
	//
	// Instance variables defined in this object.
	this.returnDefinition   = obj.returnDefinition;
	this.expectedReturnRate = obj.expectedReturnRate;
	this.volatility         = obj.volatility;
    }

    /**
     * Method for returning a RatePath object from the Monte Carlo data
     * generated.
     *
     * @return a <code>RatePath</code> object representing the generated
     *         data.
     * @exception DemoException thrown if there was a problem creating
     *            the RatePath object.
     */
    public RatePath getRatePath() {
	return(new RatePath(this));
    }
    /**
     * Method for calculating the sequence of fluctuations, based around
     * a Gaussian distribution of given mean and variance, as defined
     * in this class' instance variables.  Mapping from Gaussian
     * distribution of (0,1) to (mean-drift,volatility) is done via
     * Ito's lemma on the log of the stock price.
     * 
     * @param randomSeed The psuedo-random number seed value, to start off a
     *                   given sequence of Gaussian fluctuations.
     * @exception DemoException thrown if there are any problems with
     *                          the computation.
     */
    public boolean computeFluctuationsGaussian(long randomSeed) {
	int ntimesteps = this.nTimeSteps;
	int length = this.fluctuations.length;
	if( ntimesteps > length ) {
	    return false;
	}
	float[] flucts = this.fluctuations;
	float expectedreturnrate = this.expectedReturnRate;
	float vol = this.volatility;
	float dtime = this.dTime;
	
	//
	// First, make use of the passed in seed value.
	MyRandom rnd;
	float v1,v2, r;
	v1 = (float)0.0;
	v2 = (float)0.0;
	if( randomSeed == -1 ) {
	    rnd = new MyRandom(0, v1, v2);
	} else {
	    rnd = new MyRandom((int)randomSeed, v1, v2);
	}
	
	//
	// Determine the mean and standard-deviation, from the mean-drift and volatility.
	float mean = (expectedreturnrate-(float)0.5*vol*vol)*dtime;
	float sd   = vol*Math.sqrtf(dtime);
	float gauss, meanGauss=(float)0.0, variance=(float)0.0;
	for( int i=0; i < ntimesteps; i += 2 ) {
	    r  = rnd.seed();
	    gauss = r*rnd.v1;
	    meanGauss+= gauss;
	    variance+= gauss*gauss;
	    //
	    // Now map this onto a general Gaussian of given mean and variance.
	    flucts[i] = mean + sd*gauss;
	    //      dbgPrintln("gauss="+gauss+" fluctuations="+fluctuations[i]);
	    
	    gauss  = r*rnd.v2;
	    meanGauss+= gauss;
	    variance+= gauss*gauss;
	    //
	    // Now map this onto a general Gaussian of given mean and variance.
	    flucts[i+1] = mean + sd*gauss;
	}
	meanGauss/=(float)ntimesteps;
	variance /=(float)ntimesteps;
	//    dbgPrintln("meanGauss="+meanGauss+" variance="+variance);
    }
    /**
     * Method for calculating the sequence of fluctuations, based around
     * a Gaussian distribution of given mean and variance, as defined
     * in this class' instance variables.  Mapping from Gaussian
     * distribution of (0,1) to (mean-drift,volatility) is done via
     * Ito's lemma on the log of the stock price.  This overloaded method
     * is for when the random seed should be decided by the system.
     * 
     * @exception DemoException thrown if there are any problems with
     *                          the computation.
     */
    public void computeFluctuationsGaussian() {
	computeFluctuationsGaussian((long)-1);
    }
    /**
     * Method for calculating the corresponding rate path, given the
     * fluctuations and starting rate value.
     * 
     * @param startValue the starting value of the rate path, to be
     *                   updated with the precomputed fluctuations.
     * @exception DemoException thrown if there are any problems with
     *                          the computation.
     */
    public void computePathValue(float startValue) {
	float[] pathvalue = this.pathValue;
	float[] flucts = this.fluctuations;
	int length = this.nTimeSteps;
	pathvalue[0] = startValue;
	if( returnDefinition == 1 | 
		returnDefinition == 2) {
	    for(int i=1; i < length; i++ ) {
		//System.printI((int)(flucts[i] * 10000));
		pathvalue[i] = pathvalue[i-1] * Math.expf(flucts[i]);
	    }
	}
    }
}
