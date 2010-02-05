public class Spider {
	public static void main(String[] args) {
		int maxDepth = 3;
		int maxSearchDepth = 10;
		int i, j;
		QueryTask qt;

		String firstmachine;
		String firstpage;

		firstmachine = new String(args[0]);
		if (args.length == 2) {
			firstpage = new String(args[1]);
		}
		else 
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
