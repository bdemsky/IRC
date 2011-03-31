/**************************************************************************
*                                                                         *
*             Java Grande Forum Benchmark Suite - Version 2.0             *
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
*      This version copyright (c) The University of Edinburgh, 1999.      *
*                         All rights reserved.                            *
*                                                                         *
**************************************************************************/

/**
  * Wrapper code to invoke the Application demonstrator.
  *
  * @author H W Yau
  * @version $Revision: 1.2 $ $Date: 2011/03/31 21:45:51 $
  */
public class CallAppDemo {
    public int size;
//    int datasizes[] = {10000,60000};
//    int input[] = new int[2];
//    AppDemo ap = null;

    int datasizes[];
    int input[];
    AppDemo ap;

    public CallAppDemo(){
      datasizes=new int[2];
      datasizes[0]=10000;
      datasizes[1]=60000;
      input = new int[2];
      AppDemo ap = null;
    }
    
    public void initialise (int workload) {

      input[0] = 1000;
      input[1] = datasizes[size];

      String dirName="Data";
      String filename="hitData";
      ap = new AppDemo(dirName, filename,
      (input[0]),(input[1]),workload);
      ap.initSerial();
    }

    public void runiters () {
      long sTime=System.currentTimeMillis();
      ap.runSerial();
      System.out.println("Time="+(System.currentTimeMillis()-sTime));
    }
    
    public AppDemo getAppDemo(){
      return ap;
    }

}
