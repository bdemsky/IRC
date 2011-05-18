/*
Usage :
  ./Spider.java master <num_thread> <first machine> <first page> <maxDepth>
*/


public class Spider {
  public Spider() {}
  public static void main(String[] args) {
    int NUM_THREADS = 3;
    int maxDepth = 3;
    int maxSearchDepth = 10;
    int i, j;
    String fm = "dc-11.calit2.uci.edu";
    String fp = "";
    
    if(args.length != 3) {
      System.out.println("./Spider.java master <num_thread> <first machine> <maxDepth>");
      System.exit(0);
    } else {
      NUM_THREADS = Integer.parseInt(args[0]);
      fm = "dc-11.calit2.uci.edu";
      maxDepth = Integer.parseInt(args[2]);
    }
    
    int nQueue = 3;
    int[] mid = null;

    if(NUM_THREADS <= 8 ) {
      mid = new int[8];
      mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
      mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
      mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
      mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
      mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
      mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
      mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
      mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc8
    } else {
      mid = new int[16];
      mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
      mid[1] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
      mid[2] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
      mid[3] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
      mid[4] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
      mid[5] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
      mid[6] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
      mid[7] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
      mid[8] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
      mid[9] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
      mid[10] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
      mid[11] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
      mid[12] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
      mid[13] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
      mid[14] = (128<<24)|(195<<16)|(136<<8)|169; //dc8
      mid[15] = (128<<24)|(195<<16)|(136<<8)|169; //dc8
    }

    if(mid == null) {
      System.out.println("mid variable not initialized");
      System.exit(1);
    }

    
    TaskSet ts;
    atomic {
      //set up workers
      ts=global new TaskSet(NUM_THREADS);
      for (i = 0; i < nQueue; i++) {
        ts.todo[i] = global new GlobalQueue();
      }
      for (i = 0; i < NUM_THREADS; i++) {
        ts.threads[i] = global new Worker(ts,i,nQueue);
      }
    }

    atomic {
      GlobalString firstmachine = global new GlobalString(fm);
      GlobalString firstpage = global new GlobalString("1.html");
      DistributedHashMap visitedList = global new DistributedHashMap(500, 500, 0.75f);
      DistributedHashMap results = global new DistributedHashMap(100, 100, 0.75f);
      DistributedLinkedList results_list = global new DistributedLinkedList();
      QueryTask firstquery = global new QueryTask(visitedList, maxDepth, maxSearchDepth, results, results_list, firstmachine, firstpage, 0);
      ts.todo[0].push(firstquery);
    }

    System.printString("Finished to create Objects\n");
    
    
    Worker tmp;
    for (i = 0; i < NUM_THREADS; i++) {
      atomic {
	tmp = ts.threads[i];
      }
      Thread.myStart(tmp, mid[i]);
    }

    while(true)
      Thread.sleep(100000);

    for (i = 0; i < NUM_THREADS; i++) {
      atomic {
	tmp = ts.threads[i];
      }
      tmp.join();
    }
  }
}
