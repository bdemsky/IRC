public class Spider {
	public static void main(String[] args) {
		int NUM_THREADS = 3;
    int maxDepth = 3;
		int i, j;
		QueryThread[] qt;
		GlobalQuery[] currentWorkList;

		NUM_THREADS = Integer.parseInt(args[0]);

    if(args.length == 3) {
      maxDepth = Integer.parseInt(args[2]);
    }

    GlobalString firstmachine;

		int mid[] = new int[NUM_THREADS];
/*		mid[0] = (128<<24)|(195<<16)|(180<<8)|21;	 //dc-4
		mid[1] = (128<<24)|(195<<16)|(180<<8)|24;	 //dc-5
		mid[2] = (128<<24)|(195<<16)|(180<<8)|26;	 //dc-6
    */
		mid[0] = (128<<24)|(195<<16)|(136<<8)|162;	 //dc-1
		mid[1] = (128<<24)|(195<<16)|(136<<8)|163;	 //dc-2
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164;	 //dc-3
		mid[3] = (128<<24)|(195<<16)|(136<<8)|165;	 //dc-4
//		mid[4] = (128<<24)|(195<<16)|(136<<8)|166;	 //dc-5
//		mid[5] = (128<<24)|(195<<16)|(136<<8)|167;	 //dc-6

		atomic {
			firstmachine = global new GlobalString(args[1]);

			qt = global new QueryThread[NUM_THREADS];
			currentWorkList = global new GlobalQuery[NUM_THREADS];
			
			GlobalQuery firstquery = global new GlobalQuery(firstmachine);

			Queue todoList = global new Queue();
      DistributedHashMap doneList = global new DistributedHashMap(500,500, 0.75f);
      DistributedHashMap results = global new DistributedHashMap(100,100,0.75f);

			todoList.push(firstquery);

			for (i = 0; i < NUM_THREADS; i++) {
				qt[i] = global new QueryThread(todoList, doneList, results,maxDepth, i,NUM_THREADS,currentWorkList);
			}
		}
		System.printString("Finished to create Objects\n");

		QueryThread tmp;
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = qt[i];
			}
      tmp.start(mid[i]);
		}

		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = qt[i];
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
