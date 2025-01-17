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
 * Class to do the work in the Application demonstrator, in particular
 * the pricing of the stock path generated by Monte Carlo.  The run
 * method will generate a single sequence with the required statistics,
 * estimate its volatility, expected return rate and final stock price
 * value.
 *
 * @author H W Yau
 * @version $Revision: 1.2 $ $Date: 2011/07/14 21:28:29 $
 */
public class PriceStock{

  //------------------------------------------------------------------------
  // Instance variables.
  //------------------------------------------------------------------------
  /**
   * The Monte Carlo path to be generated.
   */
  public MonteCarloPath mcPath;
  /**
   * String identifier for a given task.
   */
  //private String taskHeader;
  /**
   * Random seed from which the Monte Carlo sequence is started.
   */
  public long randomSeed;
  /**
   * Initial stock price value.
   */
  public float pathStartValue;
  /**
   * Object which represents the results from a given computation task.
   */
  public ToResult result;
  public float expectedReturnRate;
  public float volatility;
  public float volatility2;
  public float finalStockPrice;
  public float[] pathValue;

  //------------------------------------------------------------------------
  // Constructors.
  //------------------------------------------------------------------------
  /**
   * Default constructor.
   */
  public PriceStock() {
    //this.taskHeader = "";
    this.randomSeed=-1;
    this.pathStartValue=(float)0.0;
    this.expectedReturnRate=(float)0.0;
    this.volatility=(float)0.0;
    this.volatility2=(float)0.0;
    this.finalStockPrice=(float)0.0;

    mcPath = new MonteCarloPath();
  }
  //------------------------------------------------------------------------
  // Methods.
  //------------------------------------------------------------------------
  //------------------------------------------------------------------------
  // Methods which implement the Slaveable interface.
  //------------------------------------------------------------------------
  /**
   * Method which is passed in the initialisation data common to all tasks,
   * and then unpacks them for use by this object.
   *
   * @param obj Object representing data which are common to all tasks.
   */
  public void setInitAllTasks(AppDemoRunner obj) {
    mcPath.name = obj.name;
    mcPath.startDate = obj.startDate;
    mcPath.endDate = obj.endDate;
    mcPath.dTime = obj.dTime;
    mcPath.returnDefinition = obj.returnDefinition;
    mcPath.expectedReturnRate = obj.expectedReturnRate;
    mcPath.volatility = obj.volatility;
    int nTimeSteps = obj.nTimeSteps;
    mcPath.nTimeSteps = nTimeSteps;
    this.pathStartValue = obj.pathStartValue;
    mcPath.pathStartValue = pathStartValue;
    mcPath.pathValue = new float[nTimeSteps];
    mcPath.fluctuations = new float[nTimeSteps];
  }
  /**
   * Method which is passed in the data representing each task, which then
   * unpacks it for use by this object.
   *
   * @param obj Object representing the data which defines a given task.
   */
  public void setTask(/*String header, */long randomSeed) {
    //this.taskHeader     = header;
    this.randomSeed     = randomSeed;
  }
  /**
   * The business end.  Invokes the necessary computation routine, for a
   * a given task.
   */
  public void run() {
    mcPath.computeFluctuationsGaussian(randomSeed);
    mcPath.computePathValue(pathStartValue);
    RatePath rateP = new RatePath(mcPath);
    ReturnPath returnP = rateP.getReturnCompounded();
    returnP.estimatePath();
    expectedReturnRate = returnP.expectedReturnRate;
    volatility = returnP.volatility;
    volatility2 = returnP.volatility2;
    finalStockPrice = rateP.getEndPathValue();//pathValue[rateP.pathValue.length-1];
    pathValue = mcPath.pathValue;
  }
  /*
   * Method which returns the results of a computation back to the caller.
   *
   * @return An object representing the computed results.
   */
  public ToResult getResult() {
    //String resultHeader = "Result of task with Header="+taskHeader+": randomSeed="+randomSeed+": pathStartValue="+(int)pathStartValue;
    ToResult res = new ToResult(/*resultHeader,*/
        expectedReturnRate,
        volatility,
        volatility2,
        finalStockPrice,
        pathValue);
    return res;
  }
}
