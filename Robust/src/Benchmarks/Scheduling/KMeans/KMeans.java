public class KMeans {
  // Flags
  flag init;
  flag run;
  flag turnin;
  flag reset;

  /**
   * thread id
   **/
  public int threadid;
  
  /**
   * Total number of threads
   **/
  int nthreads;

  // Read-only memebers
  /**
   * List of attributes
   **/
  public float[][] feature;

  /**
   * Number of attributes per Object
   **/
  public int nfeatures;

  /**
   * Number of Objects
   **/
  public int npoints;

  /**
   *
   **/
  public float[][] clusters;

  /**
   * Iteration id between min_nclusters to max_nclusters 
   **/
  public int nclusters;

  // Writable members
  /**
   * 
   */
  public float delta;
  
  /**
   * Array that holds change index of cluster center per thread 
   **/
  public int[] membership;

  // Local copy members, writable
  /**
   * Number of points in each cluster [nclusters]
   **/
  public intwrapper[] new_centers_len;

  /**
   * New centers of the clusters [nclusters][nfeatures]
   **/
  public float[][] new_centers;

  public KMeans(int threadid,
                int nthreads,
                int nfeatures,
                int npoints,
                int nclusters,
                float[][] feature,
                float[][] clusters) {
    this.threadid = threadid;
    this.nthreads = nthreads;
    this.nfeatures = nfeatures;
    this.npoints = npoints;
    this.nclusters = nclusters;
    this.feature = feature;
    this.clusters = clusters;
    this.delta = (float)0.0;
    this.membership = new int[this.npoints / this.nthreads];
    this.new_centers_len = null;
    this.new_centers = null;
  }

  public void setFeature(float[][] feature){
    this.feature = feature;
  }

  public void setClusters(int nclusters,
                          float[][] clusters){
    this.nclusters = nclusters;
    this.clusters = clusters;
  }
  
  public void init() {
    int[] membership = this.membership;
    int length = this.membership.length;
    for (int i = 0; i < length; i++) {
      membership[i] = -1;
    }
    this.new_centers_len = new intwrapper[this.nclusters];
    this.new_centers = new float[this.nclusters][this.nfeatures];
  }

  public void run() {
    float delta = (float) 0.0;

    float[][] feature = this.feature;
    int nfeatures = this.nfeatures;
    int npoints = this.npoints;
    int nclusters = this.nclusters;
    int[] membership = this.membership;
    float[][] clusters = this.clusters;
    intwrapper[] new_centers_len = this.new_centers_len;
    float[][] new_centers = this.new_centers;
    int index, start, span;

    start = this.threadid;
    span = this.nthreads;

    int k = 0;
    //System.out.println("myId= " + myId + " start= " + start + " npoints= " + npoints);
    for (int i = start; i < npoints; i += span) {
      index = Common.common_findNearestPoint(feature[i],
                                             nfeatures,
                                             clusters,
                                             nclusters);
      /*
       * If membership changes, increase delta by 1.
       * membership[i] cannot be changed by other threads
       */
      if (membership[k] != index) {
        delta += 1.0f;
      }

      /* Assign the membership to object i */
      /* membership[i] can't be changed by other thread */
      membership[k] = index;

      /* Update new cluster centers : sum of objects located within */
      new_centers_len[index] = new_centers_len[index] + 1;
      for (int j = 0; j < nfeatures; j++) {
        new_centers[index][j] = new_centers[index][j] + feature[i][j];
      }
      k++;
    }
    this.delta = delta;
  }
}