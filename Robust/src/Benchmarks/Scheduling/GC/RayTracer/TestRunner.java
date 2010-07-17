/**************************************************************************
 * * Java Grande Forum Benchmark Suite - Version 2.0 * * produced by * * Java
 * Grande Benchmarking Project * * at * * Edinburgh Parallel Computing Centre *
 * * email: epcc-javagrande@epcc.ed.ac.uk * * * This version copyright (c) The
 * University of Edinburgh, 1999. * All rights reserved. * *
 **************************************************************************/

public class TestRunner extends RayTracer {

  flag run;
  flag compose;

  int numCore;
  public int id;

  public TestRunner(int id, 
                    int numCore,
                    int size) {
    super();
    this.id = id;
    this.numCore = numCore;
    this.size = size;

    // set image size
    width=size;
    height=size;
    this.image=new int[size][];

    // create the objects to be rendered
    scene = createScene();

    // get lights, objects etc. from scene.
    setScene(scene);

    numobjects = scene.getObjects();
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

    int heightPerCore=height/numCore;
    int startidx=heightPerCore * this.id;
    int endidx=startidx + heightPerCore;
    Interval interval = new Interval(0, width, height, startidx, endidx, 1);
    render(interval);

    //System.out.println("CHECKSUM="+checksum);

  }


}
