public class Spider {
	public static void main(String[] args) {
		int NUM_THREADS = 3;
		int maxDepth = 3;
		int maxSearchDepth = 10;
		int i, j;
		QueryTask qt;
        String fm = "www.uci.edu";

		String firstmachine;
		String firstpage;

        if(args.length != 3) {
          System.out.println("./Spider.java master <num_thread> <first machine> <maxDepth>");
          System.exit(0);
        } else {
          NUM_THREADS = Integer.parseInt(args[0]);
          fm = args[1];
          maxDepth = Integer.parseInt(args[2]);
        }

		firstmachine = new String(fm);
        firstpage = new String("");;

		HashMap visitedList = new HashMap(500, 0.75f);
		HashMap results = new HashMap(100, 0.75f);
		LinkedList results_list = new LinkedList();

		LocalQuery firstquery = new LocalQuery(firstmachine, firstpage, 0);

		Queue todoList = new Queue();
		todoList.push(firstquery);

		qt = new QueryTask(todoList, visitedList, maxDepth, maxSearchDepth, results, results_list);

		System.printString("Finished to create Objects\n");

		qt.execute();
	}
}
