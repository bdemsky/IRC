public class Spider {
	public static void main(String[] args) {
		int NUM_THREADS = 3;
		int maxDepth = 3;
		int i, j;
		Work[] works;
		QueryTask[] qt;
		GlobalQuery[] currentWorkList;

		NUM_THREADS = Integer.parseInt(args[0]);

		if (args.length == 3) {
			maxDepth = Integer.parseInt(args[2]);
		}

		GlobalString firstmachine;

		int mid[] = new int[NUM_THREADS];
		mid[0] = (128<<24)|(195<<16)|(180<<8)|21;	 
		mid[1] = (128<<24)|(195<<16)|(180<<8)|24;	 
		mid[2] = (128<<24)|(195<<16)|(180<<8)|26;	 

		atomic {
			firstmachine = global new GlobalString(args[1]);

			works = global new Work[NUM_THREADS];
			qt = global new QueryTask[NUM_THREADS];
			currentWorkList = global new GlobalQuery[NUM_THREADS];
			
			GlobalQuery firstquery = global new GlobalQuery(firstmachine);

			Queue todoList = global new Queue();
			DistributedHashMap doneList = global new DistributedHashMap(500, 500, 0.75f);
			DistributedHashMap results = global new DistributedHashMap(100, 100, 0.75f);
			
			todoList.push(firstquery);

			for (i = 0; i < NUM_THREADS; i++) {
				qt[i] = global new QueryTask(todoList, doneList, maxDepth, results);
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
