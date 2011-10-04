/*
Lonestar Benchmark Suite for irregular applications that exhibit 
amorphous data-parallelism.

Center for Grid and Distributed Computing
The University of Texas at Austin

Copyright (C) 2007, 2008, 2009 The University of Texas at Austin

Licensed under the Eclipse Public License, Version 1.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.eclipse.org/legal/epl-v10.html

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

File: SerialBarneshut.java 
 */


public final class Barneshut {
  private  int nbodies; // number of bodies in system
  private  int ntimesteps; // number of time steps to run
  private  double dtime; // length of one time step
  private  double eps; // potential softening parameter
  private  double tol; // tolerance for stopping recursion, should be less than 0.57 for 3D case to bound error

  private  double dthf, epssq, itolsq;
  private  OctTreeLeafNodeData body[]; // the n bodies
  private  double diameter, centerx, centery, centerz;
  private  int curr;

  private  boolean isFirstRun;
  
  public  Barneshut(){
    isFirstRun = true;
  }

  private  void ReadInput(String filename) {
    double vx, vy, vz;
    FileInputStream inputFile = new FileInputStream(filename);
    nbodies = Integer.parseInt(inputFile.readLine());
    ntimesteps = Integer.parseInt(inputFile.readLine());
    dtime = Double.parseDouble(inputFile.readLine());
    eps = Double.parseDouble(inputFile.readLine());
    tol =Double.parseDouble(inputFile.readLine());
    
    dthf = 0.5 * dtime;
    epssq = eps * eps;
    itolsq = 1.0 / (tol * tol);
    
    if (isFirstRun) {
      System.out.println("configuration: " + nbodies + " bodies, " + ntimesteps + " time steps");
      System.out.println("");
    }
    
    body = new OctTreeLeafNodeData[nbodies];
    for (int i = 0; i < nbodies; i++) {
      body[i] = new OctTreeLeafNodeData();
    }
    
    String line=null;
    for (int i = 0; i < nbodies; i++) {
      line=inputFile.readLine();
      if(line!=null){
        StringTokenizer token=new StringTokenizer(line);
        body[i].mass = Double.parseDouble(token.nextToken());
        body[i].posx = Double.parseDouble(token.nextToken());
        body[i].posy = Double.parseDouble(token.nextToken());
        body[i].posz = Double.parseDouble(token.nextToken());
        vx = Double.parseDouble(token.nextToken());
        vy = Double.parseDouble(token.nextToken());
        vz = Double.parseDouble(token.nextToken());
        body[i].setVelocity(vx, vy, vz);
      }
      
    }
  }


  private  void ComputeCenterAndDiameter() {
    double minx, miny, minz;
    double maxx, maxy, maxz;
    double posx, posy, posz;
//    minx = miny = minz = Double.MAX_VALUE;
    minx = miny = minz = 1.7976931348623157E308;
//    maxx = maxy = maxz = Double.MIN_VALUE;
    maxx = maxy = maxz = 4.9E-324;
    for (int i = 0; i < nbodies; i++) {
      posx = body[i].posx;
      posy = body[i].posy;
      posz = body[i].posz;
      if (minx > posx) {
        minx = posx;
      }
      if (miny > posy) {
        miny = posy;
      }
      if (minz > posz) {
        minz = posz;
      }
      if (maxx < posx) {
        maxx = posx;
      }
      if (maxy < posy) {
        maxy = posy;
      }
      if (maxz < posz) {
        maxz = posz;
      }
    }
    diameter = maxx - minx;
    if (diameter < (maxy - miny)) {
      diameter = (maxy - miny);
    }
    if (diameter < (maxz - minz)) {
      diameter = (maxz - minz);
    }
    centerx = (maxx + minx) * 0.5;
    centery = (maxy + miny) * 0.5;
    centerz = (maxz + minz) * 0.5;
  }


public   void ComputeCenterOfMass(ArrayIndexedGraph octree, ArrayIndexedNode root) { // recursively summarizes info about subtrees
    double m, px = 0.0, py = 0.0, pz = 0.0;
    OctTreeNodeData n = octree.getNodeData(root);
    int j = 0;
    n.mass = 0.0;
    for (int i = 0; i < 8; i++) {
      ArrayIndexedNode child = octree.getNeighbor(root, i);
      if (child != null) {
        if (i != j) {
          octree.removeNeighbor(root, i);
          octree.setNeighbor(root, j, child); // move non-null children to the front (needed later to make other code faster)
        }
        j++;
        OctTreeNodeData ch = octree.getNodeData(child);
        if (ch instanceof OctTreeLeafNodeData) {
          body[curr++] = (OctTreeLeafNodeData) ch; // sort bodies in tree order (approximation of putting nearby nodes together for locality)
        } else {
          ComputeCenterOfMass(octree, child);
        }
        m = ch.mass;
        n.mass += m;
        px += ch.posx * m;
        py += ch.posy * m;
        pz += ch.posz * m;
      }
    }
    m = 1.0 / n.mass;
    n.posx = px * m;
    n.posy = py * m;
    n.posz = pz * m;
  }


 public static void Insert(ArrayIndexedGraph octree, ArrayIndexedNode root, OctTreeLeafNodeData b, double r) { // builds the tree
    double x = 0.0, y = 0.0, z = 0.0;
    OctTreeNodeData n = octree.getNodeData(root);
    int i = 0;
    if (n.posx < b.posx) {
      i = 1;
      x = r;
    }
    if (n.posy < b.posy) {
      i += 2;
      y = r;
    }
    if (n.posz < b.posz) {
      i += 4;
      z = r;
    }
    ArrayIndexedNode child = octree.getNeighbor(root, i);
    if (child == null) {
      ArrayIndexedNode newnode = octree.createNode(b);
      octree.addNode(newnode);
      octree.setNeighbor(root, i, newnode);
    } else {
      double rh = 0.5 * r;
      OctTreeNodeData ch = octree.getNodeData(child);
      if (!(ch instanceof OctTreeLeafNodeData)) {
        Insert(octree, child, b, rh);
      } else {
        ArrayIndexedNode newnode = octree.createNode(new OctTreeNodeData(n.posx - rh + x, n.posy - rh + y, n.posz - rh + z));
        octree.addNode(newnode);
        Insert(octree, newnode, b, rh);
        Insert(octree, newnode, (OctTreeLeafNodeData) ch, rh);
        octree.setNeighbor(root, i, newnode);
      }
    }
  }


  public static void main(String args[]) {
    Barneshut bh=new Barneshut();
    
    long runtime, lasttime, mintime, run;

    runtime = 0;
    lasttime =9223372036854775807L;
    mintime =9223372036854775807L;
    run = 0;
    
//    while (((run < 3) || (Math.abs(lasttime-runtime)*64 > min(lasttime, runtime))) && (run < 7)) {
      runtime = bh.run(args);
      if (runtime < mintime) mintime = runtime;
      run++;
//    }
    System.out.println("minimum runtime: " + mintime + " ms");
    System.out.println("");
  }

  public  long run(String args[]) {
    if (isFirstRun) {
      System.out.println("Lonestar Benchmark Suite v2.1");
      System.out.println("Copyright (C) 2007, 2008, 2009 The University of Texas at Austin");
      System.out.println("http://iss.ices.utexas.edu/lonestar/");
      System.out.println("");
      System.out.println("application: BarnesHut (serial version)");
      System.out.println("Simulation of the gravitational forces in a galactic");
      System.out.println("cluster using the Barnes-Hut n-body algorithm");
      System.out.println("http://iss.ices.utexas.edu/lonestar/barneshut.html");
      System.out.println("");
    }
    if (args.length != 1) {
      System.out.println("arguments: input_file_name");
      System.exit(-1);
    }
    long fstart_time = System.currentTimeMillis();
    ReadInput(args[0]);
    long fend_time = System.currentTimeMillis();
    System.out.println("file reading time="+(fend_time-fstart_time));
    long runtime = 0;
    int local_nbodies=nbodies;
    int local_ntimesteps=ntimesteps;

    long start_time = System.currentTimeMillis();
    for (int step = 0; step < local_ntimesteps; step++) { // time-step the system
      ComputeCenterAndDiameter();
      ArrayIndexedGraph octree = disjoint AIG new ArrayIndexedGraph(8);
      ArrayIndexedNode root = octree.createNode(new OctTreeNodeData(centerx, centery, centerz)); // create the tree's root
      octree.addNode(root);
      double radius = diameter * 0.5;
    

      genreach b0;

      for (int i = 0; i < local_nbodies; i++) {
        Insert(octree, root, body[i], radius); // grow the tree by inserting each body
        body[i].root=root;
      }
      curr = 0;


      genreach b1;


      // summarize subtree info in each internal node (plus restructure tree and sort bodies for performance reasons)
      ComputeCenterOfMass(octree, root);


      genreach b2;


      sese force {

        genreach b3;

	for(int i=0; i < local_nbodies; i++){
	  // compute the acceleration of each body (consumes most of the total runtime)
	  // n.ComputeForce(octree, root, diameter, itolsq, step, dthf, epssq);
	  OctTreeLeafNodeData eachbody=body[i];
	  double di=diameter;
	  double it=itolsq;
	  double dt=dthf;
	  double ep=epssq;   
	  sese parallel{
            genreach intoPar;
	    eachbody.ComputeForce(octree, di, it, step, dt, ep);
	  }
	}
      }

      for (int i = 0; i < local_nbodies; i++) {        
        body[i].Advance(dthf, dtime); // advance the position and velocity of each body
      }

    } // end of time step


    long end_time=System.currentTimeMillis();
    
    if (isFirstRun) {
      /*
        for (int i = 0; i < local_nbodies; i++) {
          System.out.println(body[i].posx + " " + body[i].posy + " " + body[i].posz); // print result
        }
     */
      System.out.println("");
    }
    runtime += (end_time-start_time);
    System.out.println("runtime: " + runtime + " ms");

    isFirstRun = false;
    return runtime;
  }
  
  
  public static long min(long a, long b) {
    if(a == b)
      return a;
    if(a > b) {
      return b;
    } else {
      return a;
    }
  }
  
}
