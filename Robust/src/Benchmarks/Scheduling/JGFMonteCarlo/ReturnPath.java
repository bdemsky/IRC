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
  * Class for representing the returns of a given security.
  *
  * <p>To do list:
  * <ol>
  *   <li>Define a window over which the mean drift and volatility
  *       are calculated.</li>
  *   <li>Hash table to reference {DATE}->{pathValue-index}.</li>
  * </ol>
  *
  * @author H W Yau
  * @version $Revision: 1.1 $ $Date: 2008/08/18 22:22:21 $
  */
public class ReturnPath extends PathId {
  /**
    * Flag for indicating one of the return definitions, via:
    *       u_i = \ln{\frac{S_i}{S_{i-1}}}
    * corresponding to the instantaneous compounded return.
    */
  public int COMPOUNDED;

  /**
    * Flag for indicating one of the return definitions, via:
    *       u_i = \frac{S_i - S_{i-1}}{S_i}
    * corresponding to the instantaneous non-compounded return.
    */
  public int NONCOMPOUNDED;

  //------------------------------------------------------------------------
  // Instance variables.
  //------------------------------------------------------------------------
  /**
    * An instance variable, for storing the return values.
    */
  private float[] pathValue;
  /**
    * The number of accepted values in the rate path.
    */
  private int nPathValue;
  /**
    * Integer flag for indicating how the return was calculated.
    */
  private int returnDefinition;
  /**
    * Value for the expected return rate.
    */
  private float expectedReturnRate;
  /**
    * Value for the volatility, calculated from the return data.
    */
  private float volatility;
  /**
    * Value for the volatility-squared, a more natural quantity
    * to use for many of the calculations.
    */
  private float volatility2;
  /**
    * Value for the mean of this return.
    */
  private float mean;
  /**
    * Value for the variance of this return.
    */
  private float variance;

  //------------------------------------------------------------------------
  // Constructors.
  //------------------------------------------------------------------------
  /**
    * Default constructor.
    */
  public ReturnPath() {
    super();
    
    this.COMPOUNDED = 1;
    this.NONCOMPOUNDED = 2;
    this.nPathValue=-1;
    this.returnDefinition = -1;
    this.expectedReturnRate = (float)0.0;
    this.volatility = (float)0.0;
    this.volatility2 = (float)0.0;
    this.mean = (float)0.0;
    this.variance = (float)0.0;
  }

  /**
    * Another constructor.
    *
    * @param pathValue for creating a return path with a precomputed path
    *                  value.  Indexed from 1 to <code>nPathArray-1</code>.
    * @param nPathValue the number of accepted data points in the array.
    * @param returnDefinition to tell this class how the return path values
    *                         were computed.
    */
  public ReturnPath(float[] pathValue, int nPathValue, int returnDefinition) {
    this.pathValue = pathValue;
    this.nPathValue = nPathValue;
    this.returnDefinition = returnDefinition;
    
    this.COMPOUNDED = 1;
    this.NONCOMPOUNDED = 2;
    this.expectedReturnRate = (float)0.0;
    this.volatility = (float)0.0;
    this.volatility2 = (float)0.0;
    this.mean = (float)0.0;
    this.variance = (float)0.0;
  }

  //------------------------------------------------------------------------
  // Methods.
  //------------------------------------------------------------------------
  //------------------------------------------------------------------------
  // Accessor methods for class ReturnPath.
  // Generated by 'makeJavaAccessor.pl' script.  HWY.  20th January 1999.
  //------------------------------------------------------------------------
  /**
    * Accessor method for private instance variable <code>pathValue</code>.
    *
    * @return Value of instance variable <code>pathValue</code>.
    * @exception DemoException thrown if instance variable <code>pathValue</code> is undefined.
    */
  public float[] get_pathValue(){
    return(this.pathValue);
  }
  /**
    * Set method for private instance variable <code>pathValue</code>.
    *
    * @param pathValue the value to set for the instance variable <code>pathValue</code>.
    */
  public void set_pathValue(float[] pathValue) {
    this.pathValue = pathValue;
  }
  /**
    * Accessor method for private instance variable <code>nPathValue</code>.
    *
    * @return Value of instance variable <code>nPathValue</code>.
    * @exception DemoException thrown if instance variable <code>nPathValue</code> is undefined.
    */
  public int get_nPathValue() {
    return(this.nPathValue);
  }
  /**
    * Set method for private instance variable <code>nPathValue</code>.
    *
    * @param nPathValue the value to set for the instance variable <code>nPathValue</code>.
    */
  public void set_nPathValue(int nPathValue) {
    this.nPathValue = nPathValue;
  }
  /**
    * Accessor method for private instance variable <code>returnDefinition</code>.
    *
    * @return Value of instance variable <code>returnDefinition</code>.
    * @exception DemoException thrown if instance variable <code>returnDefinition</code> is undefined.
    */
  public int get_returnDefinition() {
    return(this.returnDefinition);
  }
  /**
    * Set method for private instance variable <code>returnDefinition</code>.
    *
    * @param returnDefinition the value to set for the instance variable <code>returnDefinition</code>.
    */
  public void set_returnDefinition(int returnDefinition) {
    this.returnDefinition = returnDefinition;
  }
  /**
    * Accessor method for private instance variable <code>expectedReturnRate</code>.
    *
    * @return Value of instance variable <code>expectedReturnRate</code>.
    * @exception DemoException thrown if instance variable <code>expectedReturnRate</code> is undefined.
    */
  public float get_expectedReturnRate() {
    return(this.expectedReturnRate);
  }
  /**
    * Set method for private instance variable <code>expectedReturnRate</code>.
    *
    * @param expectedReturnRate the value to set for the instance variable <code>expectedReturnRate</code>.
    */
  public void set_expectedReturnRate(float expectedReturnRate) {
    this.expectedReturnRate = expectedReturnRate;
  }
  /**
    * Accessor method for private instance variable <code>volatility</code>.
    *
    * @return Value of instance variable <code>volatility</code>.
    * @exception DemoException thrown if instance variable <code>volatility</code> is undefined.
    */
  public float get_volatility() {
    return(this.volatility);
  }
  /**
    * Set method for private instance variable <code>volatility</code>.
    *
    * @param volatility the value to set for the instance variable <code>volatility</code>.
    */
  public void set_volatility(float volatility) {
    this.volatility = volatility;
  }
  /**
    * Accessor method for private instance variable <code>volatility2</code>.
    *
    * @return Value of instance variable <code>volatility2</code>.
    * @exception DemoException thrown if instance variable <code>volatility2</code> is undefined.
    */
  public float get_volatility2() {
    return(this.volatility2);
  }
  /**
    * Set method for private instance variable <code>volatility2</code>.
    *
    * @param volatility2 the value to set for the instance variable <code>volatility2</code>.
    */
  public void set_volatility2(float volatility2) {
    this.volatility2 = volatility2;
  }
  /**
    * Accessor method for private instance variable <code>mean</code>.
    *
    * @return Value of instance variable <code>mean</code>.
    * @exception DemoException thrown if instance variable <code>mean</code> is undefined.
    */
  public float get_mean() {
    return(this.mean);
  }
  /**
    * Set method for private instance variable <code>mean</code>.
    *
    * @param mean the value to set for the instance variable <code>mean</code>.
    */
  public void set_mean(float mean) {
    this.mean = mean;
  }
  /**
    * Accessor method for private instance variable <code>variance</code>.
    *
    * @return Value of instance variable <code>variance</code>.
    * @exception DemoException thrown if instance variable <code>variance</code> is undefined.
    */
  public float get_variance() {
    return(this.variance);
  }
  /**
    * Set method for private instance variable <code>variance</code>.
    *
    * @param variance the value to set for the instance variable <code>variance</code>.
    */
  public void set_variance(float variance) {
    this.variance = variance;
  }
  //------------------------------------------------------------------------
  /**
    * Method to calculate the expected return rate from the return data,
    * using the relationship:
    *    \mu = \frac{\bar{u}}{\Delta t} + \frac{\sigma^2}{2}
    *
    * @exception DemoException thrown one tries to obtain an undefined variable.
    */
  public void computeExpectedReturnRate() {
    this.expectedReturnRate = mean/(float)get_dTime() + (float)0.5*volatility2;
  }
  /**
    * Method to calculate <code>volatility</code> and <code>volatility2</code>
    * from the return path data, using the relationship, based on the
    * precomputed <code>variance</code>. 
    *   \sigma^2 = s^2\Delta t
    * 
    * @exception DemoException thrown if one of the quantites in the
    *                          computation are undefined.
    */
  public void computeVolatility() {
    this.volatility2 = this.variance / (float)get_dTime();
    this.volatility  = Math.sqrtf(volatility2);
  }
  /**
    * Method to calculate the mean of the return, for use by other
    * calculations.
    *
    * @exception DemoException thrown if <code>nPathValue</code> is
    *            undefined.
    */
  public void computeMean() {
    this.mean = (float)0.0;
    for( int i=1; i < nPathValue; i++ ) {
      mean += pathValue[i];
    }
    this.mean /= ((float)(nPathValue - (float)1.0));
  }
  /**
    * Method to calculate the variance of the retrun, for use by other
    * calculations.
    *
    * @exception DemoException thrown if the <code>mean</code> or
    *            <code>nPathValue</code> values are undefined.
    */
  public void computeVariance() {
    this.variance = (float)0.0;    
    for( int i=1; i < nPathValue; i++ ) {
      variance += (pathValue[i] - mean)*(pathValue[i] - mean);
    }
    this.variance /= ((float)(nPathValue - (float)1.0));
  }
  /**
    * A single method for invoking all the necessary methods which
    * estimate the parameters.
    *
    * @exception DemoException thrown if there is a problem reading any
    *            variables.
    */
  public void estimatePath() {
    computeMean();
    //System.printI(0xb0);
    computeVariance();
    //System.printI(0xb1);
    computeExpectedReturnRate();
    //System.printI(0xb2);
    computeVolatility();
    //System.printI(0xb3);
  }
  /**
    * Dumps the contents of the fields, to standard-out, for debugging.
    */
  public void dbgDumpFields() {
    super.dbgDumpFields();
//    dbgPrintln("nPathValue="        +this.nPathValue);
//    dbgPrintln("expectedReturnRate="+this.expectedReturnRate);
//    dbgPrintln("volatility="        +this.volatility);
//    dbgPrintln("volatility2="       +this.volatility2);
//    dbgPrintln("mean="              +this.mean);
//    dbgPrintln("variance="          +this.variance);
  }
}
