public class Spider {
	public static int[] getMID (int num_threads) {
		int[] mid = new int[num_threads];

		FileInputStream ifs = new FileInputStream("dstm.conf");
		String str;
		String sub;
		int fromIndex = 0;
		int endIndex = 0;
		int[] tmp = new int[4];

		for (int i = 0; i < num_threads; i++) { 
			str = ifs.readLine();
			endIndex = str.indexOf('.', fromIndex);
			sub = str.subString(fromIndex, endIndex);

			fromIndex = endIndex + 1;
			endIndex = str.indexOf('.', fromIndex);
			sub = str.subString(fromIndex, endIndex);

			fromIndex = endIndex + 1;
			endIndex = str.indexOf('.', fromIndex);
			sub = str.subString(fromIndex, endIndex);

			fromIndex = endIndex + 1;
			sub = str.subString(fromIndex);

			fromIndex = 0;
		}
		return mid;
	}

	public static void main(String[] args) {
		int NUM_THREADS = 3;
		int depth = 5;
		int searchDepth = 5;
		int i, j;
		Work[] works;
		QueryThread[] qt;
		Query[] currentWorkList;

		NUM_THREADS = Integer.parseInt(args[0]);
		GlobalString firstmachine;
		GlobalString firstpage;

		int[] mid = getMID(NUM_THREADS);

/*		int mid[] = new int[NUM_THREADS];
		mid[0] = (128<<24)|(195<<16)|(136<<8)|166;	 //dc-4
		mid[1] = (128<<24)|(195<<16)|(136<<8)|167;	 //dc-5
		mid[2] = (128<<24)|(195<<16)|(136<<8)|168;	 //dc-6
*/
		atomic {
			firstmachine = global new GlobalString(args[1]);
			firstpage = global new GlobalString(args[2]);

			works = global new Work[NUM_THREADS];
			qt = global new QueryThread[NUM_THREADS];
			currentWorkList = global new Query[NUM_THREADS];
			
			Query firstquery = global new Query(firstmachine, firstpage);

			Queue todoList = global new Queue();
			todoList.push(firstquery);
			QueryList doneList = global new QueryList();

			for (i = 0; i < NUM_THREADS; i++) {
				qt[i] = global new QueryThread(todoList, doneList, depth, searchDepth);
				works[i] = global new Work(qt[i], NUM_THREADS, i, currentWorkList);
			}
		}
		System.printString("Finished to create Objects\n");

		Work tmp;
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			tmp.start(mid[i]);
		}

		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			tmp.join();
		}

//		while(true)
//			Thread.sleep(1000000);

	}
}
