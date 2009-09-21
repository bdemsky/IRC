public class Simple extends Thread {
  Counting mycount;
	int id;
  public Simple(Counting mycount, int i) {
    this.mycount = mycount;
		this.id = i;
  }

  public void run() {
    
		int threadid;

    atomic {
			threadid = id;
//			System.println("\n##threadid: " + threadid + "\n");
		}
//    if(threadid == ((128<<24)|(195<<16)|(180<<8)|24))
  //         System.exit(0);

			for(int i = 0;  i < 1000; i++) {
        atomic {
//					System.println("##threadid: " + threadid);
					mycount.increment();
				}

//        if(threadid == ((128<<24)|(195<<16)|(180<<8)|21)) {
          for(int j =0;j< 4;j++)  {
            atomic {
 //             System.out.println("##Threadid " + j + " : " + getStatus(j));
            }
          }
 //       }
			}
        
        FileOutputStream output = new FileOutputStream("output"+threadid);
        int cc;

        atomic {
            cc = mycount.count;
        }

    String outStr = "Count = " + cc + "\n";
    output.write(outStr.getBytes());                          
		System.out.println("\n\n\nFinished!!!!!!\n\n\n\n");
    output.close();
    
		
  }

  public static void main(String[] args) {
    Simple[] s;
    Counting c;
    int numthreads = 3;
    int[] mid = new int[numthreads];
    FileOutputStream out = new FileOutputStream("output");

/*		mid[0] = (128<<24)|(195<<16)|(180<<8)|26; //dw-7
  	mid[1] = (128<<24)|(195<<16)|(180<<8)|24; //dw-5
		mid[2] = (128<<24)|(195<<16)|(180<<8)|21; //dw-2
*/
//		mid[1] = (128<<24)|(195<<16)|(180<<8)|22; //dw-3
   	mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1
		mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
//		mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4


		atomic {
      
      c = global new Counting();
      s = global new Simple[numthreads];
      for(int i = 0; i < numthreads; i++) {
        s[i] = global new Simple(c, mid[i]);
      }
      
    }

    ccc od = new ccc();
    ddd ad = new ddd();
    od.increment();

    od.increment(3);
      
    od = (ccc)ad;

    od.increment(5);



    
		System.out.println("##Done creating objects");
    Simple tmp;
    for(int i = 0; i <  numthreads; i++) {
      atomic {
        tmp = s[i];
      }
			System.out.println("##Temp gets simple object; start temp simple object");
//      tmp.start(mid[i]);
      Thread.myStart(tmp,mid[i]);
    }

    System.out.println("\n\n##DONE starting Threads\n");

    for(int i = 0; i < numthreads; i++) {
      atomic {
        tmp = s[i];
      }
      System.out.println("##Wait\n");
      tmp.join();
    }
    //print count
    int finalcount;
    atomic {
      finalcount = c.count;
    }

    String outStr = "Count = " + finalcount + "\n";
    out.write(outStr.getBytes());
    System.printString("Count = "+finalcount+"\n");
    out.close();
    
  }

}

class Counting {
  global int count;
  public Counting() {
    this.count = 0;
  }

  public increment() {
    count = count + 1;
  }
}

class ccc {
  int dd;
  public ccc() {
    this.dd = 0;
  }

  public increment() {
    dd++;
  }

  public increment(int o) {
    dd += o;
  }
}

class ddd extends ccc {

  public ddd() {
    this.dd = 0;
  }
  public increment(int u)
  {
    dd = u;
  }
}
