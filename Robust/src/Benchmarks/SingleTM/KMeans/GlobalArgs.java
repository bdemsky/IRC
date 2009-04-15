/* ==============================================================================
 *
 * GlobalArgs.java
 * -- Class that holds all the global parameters used by each thread
 *    during parallel execution
 *
 * =============================================================================
 * Author:
 *
 * Alokika Dash
 * University of California, Irvine
 * email adash@uci.edu
 *
 * =============================================================================
 */

public class GlobalArgs {

  public GlobalArgs() {

  }

  /**
   * Number of threads
   **/
  public int nthreads;

  /**
   * List of attributes
   **/
  public double[][] feature;

  /**
   * Number of attributes per Object
   **/
  public int nfeatures;

  /**
   * Number of Objects
   **/
  public int npoints;


  /**
   * Iteration id between min_nclusters to max_nclusters 
   **/
  public int nclusters;


  /**
   * Array that holds change index of cluster center per thread 
   **/
  public int[] membership;

  /**
   *
   **/
  public double[][] clusters;


  /**
   * Number of points in each cluster [nclusters]
   **/
  public int[] new_centers_len;

  /**
   * New centers of the clusters [nclusters][nfeatures]
   **/
  public double[][] new_centers;

  /**
    *
  **/
  public long global_i;

  public double global_delta;

}
