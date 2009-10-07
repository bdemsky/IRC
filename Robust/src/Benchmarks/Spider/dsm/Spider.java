public class Spider {
	public static void main(String[] args) {
		int NUM_THREADS = 4;
		int maxDepth = 5;
		int searchDepth = 10;
		int i, j;
		Work[] works;
		QueryThread[] qt;
		Query[] currentWorkList;

		NUM_THREADS = Integer.parseInt(args[0]);
		GlobalString firstmachine;
		GlobalString firstpage;

//		int[] mid = getMID(NUM_THREADS);
		int mid[] = new int[NUM_THREADS];
/*		mid[0] = (128<<24)|(195<<16)|(180<<8)|21;	 //dc-4
		mid[1] = (128<<24)|(195<<16)|(180<<8)|24;	 //dc-5
		mid[2] = (128<<24)|(195<<16)|(180<<8)|26;	 //dc-6
    */
		mid[0] = (128<<24)|(195<<16)|(136<<8)|162;	 //dc-1
		mid[1] = (128<<24)|(195<<16)|(136<<8)|163;	 //dc-2
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164;	 //dc-3
		mid[3] = (128<<24)|(195<<16)|(136<<8)|165;	 //dc-3

		atomic {
			firstmachine = global new GlobalString(args[1]);
			firstpage = global new GlobalString(args[2]);

			works = global new Work[NUM_THREADS];
			qt = global new QueryThread[NUM_THREADS];
			currentWorkList = global new Query[NUM_THREADS];
			
			Query firstquery = global new Query(firstmachine, firstpage, 0);

			Queue todoList = global new Queue();
			Queue doneList = global new Queue();
			todoList.push(firstquery);

			for (i = 0; i < NUM_THREADS; i++) {
				qt[i] = global new QueryThread(todoList, doneList, maxDepth, searchDepth);
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

	public static int[] getMID (int num_threads) {
		int[] mid = new int[num_threads];

		FileInputStream ifs = new FileInputStream("dstm.conf");
		String str;
		String sub;
		int fromIndex;
		int endIndex;
		double num;

		for (int i = 0; i < num_threads; i++) { 
			int power = 3 - i;
			fromIndex = 0;
			num = 0;

			str = ifs.readLine();

			endIndex = str.indexOf('.', fromIndex);
			sub = str.subString(fromIndex, endIndex);
			num += (Integer.parseInt(sub) << 24);

			fromIndex = endIndex + 1;
			endIndex = str.indexOf('.', fromIndex);
			sub = str.subString(fromIndex, endIndex);
			num += (Integer.parseInt(sub) << 16);

			fromIndex = endIndex + 1;
			endIndex = str.indexOf('.', fromIndex);
			sub = str.subString(fromIndex, endIndex);
			num += (Integer.parseInt(sub) << 8);

			fromIndex = endIndex + 1;
			sub = str.subString(fromIndex);
			num += Integer.parseInt(sub);

			mid[i] = (int)num;
		}
		return mid;
	}
}
