/*
Usage :
  ./Spider.java master <num_thread> <first machine> <first page> <maxDepth>
*/


public class Spider {
	public static void main(String[] args) {
		int NUM_THREADS = 3;
		int maxDepth = 3;
		int maxSearchDepth = 10;
		int i, j;
		Work[] works;
		QueryTask[] qt;
		GlobalQuery[] currentWorkList;
    String fm = "www.uci.edu";
    String fp = "";

    if(args.length != 4) {
      System.out.println("./Spider.java master <num_thread> <first machine> <first page> <maxDepth>");
      System.exit(0);
    }
    else {
      NUM_THREADS = Integer.parseInt(args[0]);
      fm = args[1];
      fp = args[2];
      maxDepth = Integer.parseInt(args[3]);
    }

		GlobalString firstmachine;
		GlobalString firstpage;

		int mid[] = new int[8];
		mid[0] = (128<<24)|(195<<16)|(180<<8)|21;
		mid[1] = (128<<24)|(195<<16)|(180<<8)|26;
		mid[2] = (128<<24)|(195<<16)|(180<<8)|24;
/*    
		mid[0] = (128<<24)|(195<<16)|(136<<8)|162;
		mid[1] = (128<<24)|(195<<16)|(136<<8)|163;
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164;
		mid[3] = (128<<24)|(195<<16)|(136<<8)|165;
		mid[4] = (128<<24)|(195<<16)|(136<<8)|166;
		mid[5] = (128<<24)|(195<<16)|(136<<8)|167;
		mid[6] = (128<<24)|(195<<16)|(136<<8)|168;
		mid[7] = (128<<24)|(195<<16)|(136<<8)|169;
  */
		atomic {
			firstmachine = global new GlobalString(fm);
			if (args.length == 3) {
				firstpage = global new GlobalString(fp);
			}
			else 
				firstpage = global new GlobalString("");;
      
			works = global new Work[NUM_THREADS];
			qt = global new QueryTask[NUM_THREADS];
			currentWorkList = global new GlobalQuery[NUM_THREADS];
			
			GlobalQuery firstquery = global new GlobalQuery(firstmachine, firstpage);

			GlobalQueue todoList = global new GlobalQueue();
			DistributedHashMap visitedList = global new DistributedHashMap(500, 500, 0.75f);
			DistributedHashMap results = global new DistributedHashMap(100, 100, 0.75f);
			DistributedLinkedList results_list = global new DistributedLinkedList();
			
			todoList.push(firstquery);

			for (i = 0; i < NUM_THREADS; i++) {
				qt[i] = global new QueryTask(todoList, visitedList, maxDepth, maxSearchDepth, results, results_list);
				works[i] = global new Work(qt[i], NUM_THREADS, i, currentWorkList);
			}
		}
		System.printString("Finished to create Objects\n");

		Work tmp;
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			Thread.myStart(tmp, mid[i]);
		}

		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			tmp.join();
		}
	}
}
