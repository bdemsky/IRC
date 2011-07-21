package RayTracer;

/**************************************************************************
 * * Java Grande Forum Benchmark Suite - Version 2.0 * * produced by * * Java
 * Grande Benchmarking Project * * at * * Edinburgh Parallel Computing Centre *
 * * email: epcc-javagrande@epcc.ed.ac.uk * * * This version copyright (c) The
 * University of Edinburgh, 1999. * All rights reserved. * *
 **************************************************************************/

public class TestRunner extends RayTracer {

  int numCore;
  public int id;

  public TestRunner(int id, 
      int numCore,
      int size,
      int image[][],					
      Scene scene) {
    super();
    this.id = id;
    this.numCore = numCore;
    this.size = size;
    this.image=image;
    // create the objects to be rendered
    this.scene = scene; //createScene();

    // set image size
    width=size;
    height=size;
    // get lights, objects etc. from scene.
    setScene(this.scene);

    numobjects = this.scene.getObjects();
    /*this.image=new int[size][];

    // get lights, objects etc. from scene.
    setScene(scene);

    numobjects = scene.getObjects();*/
  }

  public void init() {

  }

  public void JGFvalidate() {
    // long refval[] = {2676692,29827635};
    //  long refval[] = new long[2];
    //  refval[0] = 2676692;
    //  refval[1] = 29827635;
    //  long dev = checksum - refval[size];
    //  if (dev != 0) {
    //  System.out.println("Validation failed");
    //  System.out.println("Pixel checksum = " + checksum);
    //  System.out.println("Reference value = " + refval[size]);
    //  }
  }

  public void JGFtidyup() {
    //  scene = null;
    //  lights = null;
    //  prim = null;
    //  tRay = null;
    //  inter = null;
    // System.gc();
  }

  public void run() {
		    this.init();
		    float heightPerCore=height/numCore;
		    int startidx=(height*this.id)/numCore;
		    int endidx=(height*(this.id+1))/numCore;
		    if (id==(THREADNUM-1))
		    endidx=height;
		    Interval interval = new Interval(0, width, height, startidx, endidx, 1);
		    render(interval);
    //System.out.println("CHECKSUM="+checksum);

  }
  public static void main(String[] args) {
    int threadnum = THREADNUM; // 56;
    int size = 500; //threadnum * 25;
    System.setgcprofileflag();
    Composer comp = new Composer(threadnum, size);
    RayTracer rt = new RayTracer();
    Scene scene = rt.createScene();
    int image[][]=new int[size][];
    TestRunner trarray[]=new TestRunner[threadnum];					 
    for(int i = 1; i < threadnum; ++i) {
      TestRunner tr = new TestRunner(i, threadnum, size, image, scene);
      tr.start();
				       trarray[i]=tr;
				       }
             TestRunner tr0 = new TestRunner(0, threadnum, size, image, scene);
      tr0.run();
    for(int i = 1; i < threadnum; ++i) {
				       trarray[i].join();
				       }
}
}
