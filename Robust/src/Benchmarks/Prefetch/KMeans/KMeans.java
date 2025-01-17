/* =============================================================================
 *
 * kmeans.java
 *
 * =============================================================================
 *
 * Description:
 *
 * Takes as input a file:
 *   ascii  file: containing 1 data point per line
 *   binary file: first int is the number of objects
 *                2nd int is the no. of features of each object
 *
 * This example performs a fuzzy c-means clustering on the data. Fuzzy clustering
 * is performed using min to max clusters and the clustering that gets the best
 * score according to a compactness and separation criterion are returned.
 *
 *
 * Author:
 *
 * Wei-keng Liao
 * ECE Department Northwestern University
 * email: wkliao@ece.northwestern.edu
 *
 *
 * Edited by:
 *
 * Jay Pisharath
 * Northwestern University
 *
 * Chi Cao Minh
 * Stanford University
 *
 * Port to Java version
 * Alokika Dash
 * University of California, Irvine
 *
 * =============================================================================
 *
 * ------------------------------------------------------------------------
 * 
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */

public class KMeans extends Thread {
  /**
   * User input for max clusters
   **/
  int max_nclusters;

  /**
   * User input for min clusters
   **/
  int min_nclusters;

  /**
   * Check for Binary file
   **/
  int isBinaryFile;

  /**
   * Using zscore transformation for cluster center 
   * deviating from distribution's mean
   **/
  int use_zscore_transform;

  /**
   * Input file name used for clustering
   **/
  String filename;

  /**
   * Total number of threads
   **/
  int nthreads;

  /**
   * threshold until which kmeans cluster continues
   **/
  float threshold;

  /**
   * thread id
   **/
  int threadid;

  /**
   * Global arguments for threads 
   **/
  GlobalArgs g_args;

  /**
   * Output:  Number of best clusters
   **/
  int best_nclusters;

  /**
   * Output: Cluster centers
   **/
  float[][] cluster_centres;

  /**
   *
   **/
  float[][] attributes;

  int numObjects;

  int numAttributes;

  public KMeans() {
    max_nclusters = 13;
    min_nclusters = 4;
    isBinaryFile = 0;
    use_zscore_transform = 1;
    threshold = (float) 0.001;
    best_nclusters = 0;
  }

  /*
  public KMeans(int threadid, GlobalArgs g_args) {
    this.threadid = threadid;
    this.g_args = g_args;
  }
  */

  public KMeans(int threadid, GlobalArgs g_args, int nthreads, int use_zscore_transform, 
      int max_nclusters, int min_nclusters, float threshold,
      float[][] attributes, int numObjects, int numAttributes) {
    this.threadid = threadid;
    this.g_args = g_args;
    //this.nthreads = nthreads;
    //this.use_zscore_transform = use_zscore_transform;
    //this.max_nclusters = max_nclusters;
    //this.min_nclusters = min_nclusters;
    //this.threshold = threshold;
    //this.attributes = attributes;
    //this.numObjects = numObjects;
    //this.numAttributes = numAttributes;
  }

  public void run() {
    Barrier barr = new Barrier("128.195.136.162");
    int id;
    GlobalArgs tmp_g_args;
    atomic {
      id = threadid;
      tmp_g_args = g_args;
    }
    while(true) {
      Barrier.enterBarrier(barr);
      Normal.work(id, tmp_g_args);
      Barrier.enterBarrier(barr);
    }
  }

  /* =============================================================================
   * main
   * =============================================================================
   */
  public static void main(String[] args) {
    int nthreads;
    int MAX_LINE_LENGTH = 1000000; /* max input is 400000 one digit input + spaces */
    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1.calit2
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2.calit2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3.calit2
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4.calit2
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5.calit2
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6.calit2
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7.calit2
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8.calit2


    /**
     * Read options fron the command prompt 
     **/
    KMeans kms;
    kms = new KMeans();
    KMeans.parseCmdLine(args, kms);
    nthreads = kms.nthreads;
    System.out.println("nthreads= " + kms.nthreads);

    /* Initiate Barriers */
    BarrierServer mybarr;

    atomic {
      mybarr = global new BarrierServer(nthreads);
    }
    mybarr.start(mid[0]);


    if (kms.max_nclusters < kms.min_nclusters) {
      System.out.println("Error: max_clusters must be >= min_clusters\n");
      System.exit(0);
    }
    
    float[][] buf;
    float[][] attributes;
    int numAttributes = 0;
    int numObjects = 0;

    /*
     * From the input file, get the numAttributes (columns in txt file) and numObjects (rows in txt file)
     */
    if (kms.isBinaryFile == 1) {
      System.out.println("TODO: Unimplemented Binary file option\n");
      System.exit(0);
    }

    FileInputStream inputFile = new FileInputStream(kms.filename);
    byte b[] = new byte[MAX_LINE_LENGTH];
    int n;
    while ((n = inputFile.read(b)) != 0) {
      for (int i = 0; i < n; i++) {
        if (b[i] == '\n')
          numObjects++;
      }
    }
    inputFile.close();
    inputFile = new FileInputStream(kms.filename);
    String line = null;
    if((line = inputFile.readLine()) != null) {
      int index = 0;
      boolean prevWhiteSpace = true;
      while(index < line.length()) {
        char c = line.charAt(index++);
        boolean currWhiteSpace = Character.isWhitespace(c);
        if(prevWhiteSpace && !currWhiteSpace){
          numAttributes++;
        }   
        prevWhiteSpace = currWhiteSpace;
      }   
    }   
    inputFile.close();

    /* Ignore the first attribute: numAttributes = 1; */
    numAttributes = numAttributes - 1; 

    /* Allocate new shared objects and read attributes of all objects */
    float[][] tmp_buf;
    atomic {
      buf = global new float[numObjects][numAttributes];
      attributes = global new float[numObjects][numAttributes];
      tmp_buf = buf;
    }
    KMeans.readFromFile(inputFile, kms.filename, tmp_buf, MAX_LINE_LENGTH);
    System.out.println("Finished Reading from file ......");

    /*
     * The core of the clustering
     */

    int len = kms.max_nclusters - kms.min_nclusters + 1;

    int nloops = 1;

    KMeans[] km;
    GlobalArgs g_args;
    atomic {
      km = global new KMeans[nthreads];
      g_args = global new GlobalArgs();
      g_args.nthreads = nthreads;
      for(int x = 0; x < numObjects; x++) {
        for(int y = 0; y < numAttributes; y++) {
          attributes[x][y] = buf[x][y];
        }
      }
    }

    atomic {
      /* Create and Start Threads */
      for(int i = 1; i<nthreads; i++) {
        km[i] = global new KMeans(i, g_args, nthreads, kms.use_zscore_transform, 
            kms.max_nclusters, kms.min_nclusters, kms.threshold, attributes, numObjects, numAttributes);
      }
    }

    KMeans tmp;

    boolean waitfordone=true;
    while(waitfordone) {
      atomic {
        if (mybarr.done)
          waitfordone=false;
      }
    }

    for(int i = 1; i<nthreads; i++) {
      atomic {
        tmp = km[i];
      }
      tmp.start(mid[i]);
    }

    System.out.println("Finished Starting threads......");

    for (int i = 0; i < nloops; i++) {
      //
      // Since zscore transform may perform in cluster() which modifies the
      // contents of attributes[][], we need to re-store the originals
      //
      float[][] tmp_attributes;
      GlobalArgs tmp_g_args;
      atomic {
        for(int x = 0; x < numObjects; x++) {
          for(int y = 0; y < numAttributes; y++) {
            attributes[x][y] = buf[x][y];
          }
        }
        tmp_attributes = attributes;
        tmp_g_args = g_args;
      }


      Cluster.cluster_exec(nthreads,
          numObjects,
          numAttributes,
          tmp_attributes,             // [numObjects][numAttributes] 
          kms,                    //main class that holds users inputs from command prompt and output arrays that need to be filled
          tmp_g_args);                // Global arguments common to all threads
    }

    System.out.println("Printing output......");
    System.out.println("Best_nclusters= " + kms.best_nclusters);

    /* Output: the coordinates of the cluster centres */
    
    /*
    {
      for (int i = 0; i < kms.best_nclusters; i++) {
        System.out.print(i + " ");
        for (int j = 0; j < numAttributes; j++) {
          System.out.print(kms.cluster_centres[i][j] + " ");
        }
        System.out.println("\n");
      }
    }
    */

    System.out.println("Finished......\n");
    System.exit(0);
  }

  public static void parseCmdLine(String args[], KMeans km) {
    int i = 0;
    String arg;
    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-m")) {
        if(i < args.length) {
          km.max_nclusters = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-n")) {
        if(i < args.length) {
          km.min_nclusters = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-t")) {
        if(i < args.length) {
          //km.threshold = new Integer(args[i++]).intValue();
          km.threshold = (float) Double.parseDouble(args[i++]);
        }
      } else if(arg.equals("-i")) {
        if(i < args.length) {
          km.filename = args[i++];
        }
      } else if(arg.equals("-b")) {
        if(i < args.length) {
          km.isBinaryFile = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-z")) {
        km.use_zscore_transform=0;
      } else if(arg.equals("-nthreads")) {
        if(i < args.length) {
          km.nthreads = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
        km.usage();
      }
    }
    if(km.nthreads == 0 || km.filename == null) {
      km.usage();
    }
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage() {
    System.out.println("usage: ./kmeans -m <max_clusters> -n <min_clusters> -t <threshold> -i <filename> -nthreads <threads>\n");
    System.out.println(                   "  -i filename:     file containing data to be clustered\n");
    System.out.println(                   "  -b               input file is in binary format\n");
    System.out.println(                   "  -m max_clusters: maximum number of clusters allowed\n");
    System.out.println(                   "  -n min_clusters: minimum number of clusters allowed\n");
    System.out.println(                   "  -z             : don't zscore transform data\n");
    System.out.println(                   "  -t threshold   : threshold value\n");
    System.out.println(                   "  -nthreads      : number of threads\n");
  }

  /**
   * readFromFile()
   * Read attributes from the input file into an array
   **/
  public static void readFromFile(FileInputStream inputFile, String filename, float[][] buf, int MAX_LINE_LENGTH) {
    inputFile = new FileInputStream(filename);
    int j;
    int i = 0;

    byte b[] = new byte[MAX_LINE_LENGTH];
    int n;
    byte oldbytes[]=null;


    atomic { //FIXME: temporary fix  Note that this 
      // transaction will never abort because it is only executed
      // on master machine and therefore the fileread native call is
      //allowed as a warning
      j = -1;
      while ((n = inputFile.read(b)) != 0) {
        int x=0;

        if (oldbytes!=null) {
          //find space
          boolean cr=false;
          for (;x < n; x++) {
            if (b[x] == ' ')
              break;
            if(b[x] =='\n') {
              cr=true;
              break;
            }
          }
          byte newbytes[]= new byte[x+oldbytes.length];
          boolean isnumber=false;
          for(int ii=0;ii<oldbytes.length;ii++) {
            if (oldbytes[ii]>='0'&&oldbytes[ii]<='9')
              isnumber=true;
            newbytes[ii]=oldbytes[ii];
          }
          for(int ii=0;ii<x;ii++) {
            if (b[ii]>='0'&&b[ii]<='9')
              isnumber=true;
            newbytes[ii+oldbytes.length]=b[ii];
          }
          if(x!=n)
            x++; //skip past space
          if(isnumber) {
            if (j>=0) {
              buf[i][j]=(float)Double.parseDouble(new String(newbytes, 0, newbytes.length));
            }
            j++;
          }
          if(cr) {
            j=-1;
            i++;
          }
          oldbytes=null;
        }

        while (x < n) {
          int y=x;
          boolean cr=false;
          boolean isnumber=false;
          for(y=x;y<n;y++) {
            if ((b[y]>='0')&&(b[y]<='9'))
              isnumber=true;
            if (b[y]==' ')
              break;
            if (b[y]=='\n') {
              cr=true;
              break;
            }
          }
          if (y==n) {
            //need to continue for another read
            oldbytes=new byte[y-x];
            for(int ii=0;ii<(y-x);ii++)
              oldbytes[ii]=b[ii+x];
            break;
          }
          //otherwise x is beginning of character string, y is end
          if (isnumber) {
            if (j>=0) {
              buf[i][j]=(float)Double.parseDouble(new String(b,x,y-x));
            }
            j++;
          }
          if(cr) {
            i++;//skip to next line
            j = -1;//don't store line number
            x=y;//skip to end of number
            x++;//skip past return
          } else {
            x=y;//skip to end of number
            x++;//skip past space
          }
        }
      }
    }
    inputFile.close();
  }
}

/* =============================================================================
 *
 * End of kmeans.java
 *
 * =============================================================================
 */
