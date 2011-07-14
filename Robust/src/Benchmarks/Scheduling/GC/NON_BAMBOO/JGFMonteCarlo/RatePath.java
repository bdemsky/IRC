package JGFMonteCarlo;

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
 * Class for recording the values in the time-dependent path of a security.
 *
 * <p>To Do list:
 * <ol>
 *   <li><i>None!</i>
 * </ol>
 *
 * @author H W Yau
 * @version $Revision: 1.2 $ $Date: 2011/07/14 21:28:29 $
 */
public class RatePath extends PathId {

  //------------------------------------------------------------------------
  // Class variables.
  //------------------------------------------------------------------------
  /**
   * Class variable to represent the minimal date, whence the stock prices
   * appear. Used to trap any potential problems with the data.
   */
  public int MINIMUMDATE;
  /**
   * Class variable for defining what is meant by a small number, small enough
   * to cause an arithmetic overflow when dividing.  According to the
   * Java Nutshell book, the actual range is +/-4.9406564841246544E-324
   */
  public float EPSILON;

  //------------------------------------------------------------------------
  // Instance variables.
  //------------------------------------------------------------------------
  /**
   * An instance variable, for storing the rate's path values itself.
   */
  public float[] pathValue;
  /**
   * An instance variable, for storing the corresponding date of the datum,
   * in 'YYYYMMDD' format.
   */
  public int[] pathDate;
  /**
   * The number of accepted values in the rate path.
   */
  public int nAcceptedPathValue;

  //------------------------------------------------------------------------
  // Constructors.
  //------------------------------------------------------------------------
  /**
   * Constructor, where the user specifies the directory and filename in
   * from which the data should be read.
   *
   * @param String dirName
   * @param String filename
   * @exception DemoException thrown if there is a problem reading in
   *                          the data file.
   */
  public RatePath() {
    this.MINIMUMDATE = 19000101;
    this.EPSILON= (float)10.0 * (float)(4.9E-324);
    this.nAcceptedPathValue = 0;
    readRatesFile();
  }
  /**
   * Constructor, for when the user specifies simply an array of values
   * for the path.  User must also include information for specifying
   * the other characteristics of the path.
   *
   * @param pathValue the array containing the values for the path.
   * @param name the name to attach to the path.
   * @param startDate date from which the path is supposed to start, in
   *        'YYYYMMDD' format.
   * @param startDate date from which the path is supposed to end, in
   *        'YYYYMMDD' format.
   * @param dTime the time interval between successive path values, in
   *        fractions of a year.
   */
  public RatePath(float[] pathValue, 
      String name, 
      int startDate, 
      int endDate, 
      float dTime) {
    this.MINIMUMDATE = 19000101;
    this.EPSILON= (float)10.0 * (float)(4.9E-324);

    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dTime = dTime;
    this.pathValue = pathValue;
    this.nAcceptedPathValue = pathValue.length;
  }
  /**
   * Constructor, for use by the Monte Carlo generator, when it wishes
   * to represent its findings as a RatePath object.
   *
   * @param mc the Monte Carlo generator object, whose data are to
   *           be copied over.
   * @exception DemoException thrown if there is an attempt to access
   *            an undefined variable.
   */
  public RatePath(MonteCarloPath mc) {
    this.MINIMUMDATE = 19000101;
    this.EPSILON= (float)10.0 * (float)(4.9E-324);

    //
    // Fields pertaining to the parent PathId object:
    this.name = mc.name;
    this.startDate = mc.startDate;
    this.endDate = mc.endDate;
    this.dTime = mc.dTime;
    //
    // Fields pertaining to RatePath object itself.
    pathValue=mc.pathValue;
    nAcceptedPathValue=mc.nTimeSteps;
    //
    // Note that currently the pathDate is neither declared, defined,
    // nor used in the MonteCarloPath object.
    pathDate=new int[nAcceptedPathValue];
  }
  /**
   * Constructor, for when there is no actual pathValue with which to
   * initialise.
   *
   * @param pathValueLegth the length of the array containing the values
   *        for the path.
   * @param name the name to attach to the path.
   * @param startDate date from which the path is supposed to start, in
   *        'YYYYMMDD' format.
   * @param startDate date from which the path is supposed to end, in
   *        'YYYYMMDD' format.
   * @param dTime the time interval between successive path values, in
   *        fractions of a year.
   */
  public RatePath(int pathValueLength, 
      String name, 
      int startDate, 
      int endDate, 
      float dTime) {
    this.MINIMUMDATE = 19000101;
    this.EPSILON= (float)10.0 * (float)(4.9E-324);

    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dTime = dTime;
    this.pathValue = new float[pathValueLength];
    this.nAcceptedPathValue = pathValue.length;
  }
  //------------------------------------------------------------------------
  // Methods.
  //------------------------------------------------------------------------
  /**
   * Routine to update this rate path with the values from another rate
   * path, via its pathValue array.
   *
   * @param operandPath the path value array to use for the update.
   * @exception DemoException thrown if there is a mismatch between the
   *            lengths of the operand and target arrays.
   */
  public boolean inc_pathValue(float[] operandPath) {
    int length = this.pathValue.length;
    if( length != operandPath.length ) {
      return false;
    }
    float[] pathvalue = this.pathValue;
    for(int i=0; i<length; i++ ) {
      pathvalue[i] += operandPath[i];
    }
    return true;
  }
  /**
   * Routine to scale this rate path by a constant.
   *
   * @param scale the constant with which to multiply to all the path
   *        values.
   * @exception DemoException thrown if there is a mismatch between the
   *            lengths of the operand and target arrays.
   */
  public boolean inc_pathValue(float scale) {
    float[] pathvalue = this.pathValue;
    if( pathvalue==null ) {
      return false;
    }
    int length = this.pathValue.length;
    for(int i=0; i<length; i++ ) {
      pathvalue[i] *= scale;
    }
    return true;
  }
  //------------------------------------------------------------------------
  // Accessor methods for class RatePath.
  // Generated by 'makeJavaAccessor.pl' script.  HWY.  20th January 1999.
  //------------------------------------------------------------------------
  /**
   * Accessor method for private instance variable <code>pathValue</code>.
   *
   * @return Value of instance variable <code>pathValue</code>.
   * @exception DemoException thrown if instance variable <code>pathValue</code> is undefined.
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
   * Accessor method for private instance variable <code>pathDate</code>.
   *
   * @return Value of instance variable <code>pathDate</code>.
   * @exception DemoException thrown if instance variable <code>pathDate</code> is undefined.
   */
  /*public int[] get_pathDate() {
	return(this.pathDate);
    }*/
  /**
   * Set method for private instance variable <code>pathDate</code>.
   *
   * @param pathDate the value to set for the instance variable <code>pathDate</code>.
   */
  public void set_pathDate(int[] pathDate) {
    this.pathDate = pathDate;
  }
  //------------------------------------------------------------------------
  /**
   * Method to return the terminal value for a given rate path, as used
   * in derivative calculations.
   * 
   * @return The last value in the rate path.
   */
  public float getEndPathValue() {
    return( getPathValue(pathValue.length-1) );
  }
  /**
   * Method to return the value for a given rate path, at a given index.
   * <i>One may want to index this in a more user friendly manner!</i>
   * 
   * @param index the index on which to return the path value.
   * @return The value of the path at the designated index.
   */
  public float getPathValue(int index) {
    return(pathValue[index]);
  }
  /**
   * Method for calculating the returns on a given rate path, via the
   * definition for the instantaneous compounded return.
   *       u_i = \ln{\frac{S_i}{S_{i-1}}}
   * 
   * @return the return, as defined.
   * @exception DemoException thrown if there is a problem with the
   *                          calculation.
   */
  public ReturnPath getReturnCompounded() {
    int length = this.nAcceptedPathValue;
    float[] pathvalue = this.pathValue;
    if( pathvalue == null || length == 0 ) {
      return null;
    }
    float[] returnPathValue = new float[length];
    returnPathValue[0] = (float)0.0;
    for(int i=1; i< length; i++ ) {
      returnPathValue[i] = Math.logf(pathvalue[i] / pathvalue[i-1]);
    }

    ReturnPath rPath = new ReturnPath(returnPathValue, length, 1);
    //
    // Copy the PathId information to the ReturnPath object.
    rPath.copyInstanceVariables(this);
    rPath.estimatePath();
    return(rPath);
  }
  /**
   * Method for calculating the returns on a given rate path, via the
   * definition for the instantaneous non-compounded return.
   *       u_i = \frac{S_i - S_{i-1}}{S_i}
   * 
   * @return the return, as defined.
   * @exception DemoException thrown if there is a problem with the
   *                          calculation.
   */
  public ReturnPath getReturnNonCompounded() {
    int length = this.nAcceptedPathValue;
    float[] pathvalue = this.pathValue;
    if( pathvalue == null || length == 0 ) {
      return null;
    }
    float[] returnPathValue = new float[length];
    returnPathValue[0] = (float)0.0;
    for(int i=1; i< length; i++ ) {
      returnPathValue[i] = (pathvalue[i] - pathvalue[i-1])/pathvalue[i];
    }

    ReturnPath rPath = new ReturnPath(returnPathValue, length, 2);
    //
    // Copy the PathId information to the ReturnPath object.
    rPath.copyInstanceVariables(this);
    rPath.estimatePath();
    return(rPath);
  }
  //------------------------------------------------------------------------
  // Private methods.
  //------------------------------------------------------------------------
  /**
   * Method for reading in data file, in a given format.
   * Namely:
      <pre>
      881003,0.0000,14.1944,13.9444,14.0832,2200050,0
      881004,0.0000,14.1668,14.0556,14.1668,1490850,0
      ...
      990108,35.8125,36.7500,35.5625,35.8125,4381200,0
      990111,35.8125,35.8750,34.8750,35.1250,3920800,0
      990112,34.8750,34.8750,34.0000,34.0625,3577500,0
      </pre>
   * <p>Where the fields represent, one believes, the following:
   * <ol>
   *   <li>The date in 'YYMMDD' format</li>
   *   <li>Open</li>
   *   <li>High</li>
   *   <li>Low</li>
   *   <li>Last</li>
   *   <li>Volume</li>
   *   <li>Open Interest</li>
   * </ol>
   * One will probably make use of the closing price, but this can be
   * redefined via the class variable <code>DATUMFIELD</code>.  Note that
   * since the read in data are then used to compute the return, this would
   * be a good place to trap for zero values in the data, which will cause
   * all sorts of problems.
   *
   * @param dirName the directory in which to search for the data file.
   * @param filename the data filename itself.
   * @exception DemoException thrown if there was a problem with the data
   *                          file.
   */
  private void readRatesFile(){
    //
    // Now create an array to store the rates data.
    int minimumdate = MINIMUMDATE;
    float epsilon = EPSILON;
    int nLines = 1000; //200;
    int year = 88;
    int month = 10;
    int day = 3;
    this.pathValue = new float[nLines];
    this.pathDate  = new int[nLines];
    float[] pathvalue = this.pathValue;
    int[] pathdate = this.pathDate;
    nAcceptedPathValue=0;
    int iLine=0;
    /*char[] date = new char[9];
	date[0] = '1';
	date[1] = '9';
	date[2] = (char)(year/10 + '0');
	date[3] = (char)(year%10 + '0');
	date[4] = (char)(month/10 + '0');
	date[5] = (char)(month%10 + '0');
	date[6] = (char)(day/10 + '0');
	date[7] = (char)(day%10 + '0');
	date[8] = '\0';*/
    int aDate = 19881003;
    /*for(int di = 0; di < 9; di++) {
	    aDate = aDate * 10 + (int)date[di];
	}*/
    for(int k = 0; k < 20; /*40;*/ k++ ) {
      for(int j = 0; j < 50; /*5;*/ j++) {
        /*String date = "19"+String.valueOf(year);
		if(month < 10) {
		    date += "0";
		} 
		date += String.valueOf(month);
		if(day < 10) {
		    date += "0";
		}
		date +=  String.valueOf(day);*/
        //int aDate = Integer.parseInt(date);		
        day++;
        aDate++;
        /*if(date[7] == '9') {
		    date[7] = '0';
		    date[6] = (char)(date[6] + 1);
		} else {
		    date[7] = (char)(date[7] + 1);
		}*/
        if(month == 2) {
          if(day == 29) {
            day = 1;
            month++;
            /*date[6] = '0';
			date[7] = '1';
			date[5] = '3';*/
            aDate += 72;// - day(29) + 101;
          }
        } else {
          if(day == 31) {
            day = 1;
            month++;
            aDate += 70;
            /*date[6] = '0';
			date[7] = '1';*/
            if(month == 13) {
              month = 1;
              year++;
              aDate += 8800;
              /*date[4] = '0';
			    date[5] = '1';
			    if(date[3] == '9') {
				if(date[2] == '9') {
				    if(date[1] == '9') {
					date[1] = '0';
					date[0] = (char)(date[0] + 1);
				    } else {
					date[1] = (char)(date[1] + 1);
				    }
				    date[2] = '0';
				} else {
				    date[2] = (char)(date[2] + 1);
				}
				date[3] = '0';
			    } else {
				date[3] = (char)(date[3] + 1);
			    }*/
            } /*else {
			    if(date[5] == '9') {
				date[4] = '1';
				date[5] = '0';
			    } else {
				date[5] = (char)(date[5] + 1);
			    }
			}*/
          }
        }
        //
        // static float float.parsefloat() method is a feature of JDK1.2!
        int tmp = k + j;
        float aPathValue = (float)(121.7500 - tmp);
        if( (aDate <= minimumdate) /*| (Math.abs(aPathValue) < epsilon)*/ ) {
          //System.printString("Skipped erroneous data indexed by date="+date+".");
        } else {
          pathdate[iLine] = aDate;
          pathvalue[iLine] = aPathValue;
          iLine++;
        }
      }
    }
    //
    // Record the actual number of accepted data points.
    nAcceptedPathValue = iLine;
    //
    // Now to fill in the structures from the 'PathId' class.
    this.name = "rate";
    this.startDate = pathdate[0];
    this.endDate = pathdate[iLine-1];
    this.dTime = (float)(1.0/365.0);
  }
}
