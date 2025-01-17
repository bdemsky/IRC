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
  * Class for defining the results of a task.  Currently, this is simply
  * the Monte Carlo generate rate path.
  *
  * @author H W Yau
  * @version $Revision: 1.2 $ $Date: 2009/02/13 21:37:19 $
  */
public class ToResult {
  //private String header;
  public float expectedReturnRate;
  public float volatility;
  public float volatility2;
  public float finalStockPrice;
  public float[] pathValue;

  /**
    * Constructor, for the results from a computation.
    *
    * @param header Simple header string.
    * @param pathValue Data computed by the Monte Carlo generator.
    */
  public ToResult(/*String header, */
	          float expectedReturnRate, 
	          float volatility, 
	          float volatility2, 
	          float finalStockPrice, 
	          float[] pathValue) {
    //this.header=header;
    this.expectedReturnRate = expectedReturnRate;
    this.volatility = volatility;
    this.volatility2 = volatility2;
    this.finalStockPrice = finalStockPrice;
    this.pathValue = pathValue;
  }
  /**
    * Gives a simple string representation of this object.
    *
    * @return String representation of this object.
    */
  /*public String toString(){
    return(header);
  }*/
  //------------------------------------------------------------------------
  // Accessor methods for class ToResult.
  // Generated by 'makeJavaAccessor.pl' script.  HWY.  20th January 1999.
  //------------------------------------------------------------------------
  /**
    * Accessor method for private instance variable <code>header</code>.
    *
    * @return Value of instance variable <code>header</code>.
    */
  /*public String get_header() {
    return(this.header);
  }*/
  /**
    * Set method for private instance variable <code>header</code>.
    *
    * @param header the value to set for the instance variable <code>header</code>.
    */
  /*public void set_header(String header) {
    this.header = header;
  }*/
  /**
    * Accessor method for private instance variable <code>expectedReturnRate</code>.
    *
    * @return Value of instance variable <code>expectedReturnRate</code>.
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
    */
  /*public float get_volatility() {
    return(this.volatility);
  }*/
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
    */
  /*public float get_volatility2() {
    return(this.volatility2);
  }*/
  /**
    * Set method for private instance variable <code>volatility2</code>.
    *
    * @param volatility2 the value to set for the instance variable <code>volatility2</code>.
    */
  public void set_volatility2(float volatility2) {
    this.volatility2 = volatility2;
  }
  /**
    * Accessor method for private instance variable <code>finalStockPrice</code>.
    *
    * @return Value of instance variable <code>finalStockPrice</code>.
    */
  /*public float get_finalStockPrice() {
    return(this.finalStockPrice);
  }*/
  /**
    * Set method for private instance variable <code>finalStockPrice</code>.
    *
    * @param finalStockPrice the value to set for the instance variable 
    * <code>finalStockPrice</code>.
    */
  public void set_finalStockPrice(float finalStockPrice) {
    this.finalStockPrice = finalStockPrice;
  }
  /**
    * Accessor method for private instance variable <code>pathValue</code>.
    *
    * @return Value of instance variable <code>pathValue</code>.
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
  //------------------------------------------------------------------------
}


