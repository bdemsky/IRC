public class JGFInstrumentor{

	private HashMap timers;
	private HashMap data; 

	public JGFInstrumentor() {
		timers = new HashMap();
		data = new HashMap(); 
	}
	public static void addTimer (String name, String opname, int size){

		if (timers.containsKey(name)) {
			System.printString("JGFInstrumentor.addTimer: warning -  timer " + name +
					" already exists");
		}
		else {
			timers.put(name, new JGFTimer(name,opname,size));
		}

	}

	public static void startTimer(String name){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).start();
		}
		else {
			System.printString("JGFInstrumentor.startTimer: failed -  timer " + name + 
					" does not exist");
		}

	}

	public static void stopTimer(String name){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).stop();
		}
		else {
			System.printString("JGFInstrumentor.stopTimer: failed -  timer " + name + 
					" does not exist");
		}
	}

	public static void addOpsToTimer(String name, double count){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).addops(count);
		}
		else {
			System.printString("JGFInstrumentor.addOpsToTimer: failed -  timer " + name + 
					" does not exist");
		}
	}  

	public static void printTimer(String name){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).print();
		}
		else {
			System.printString("JGFInstrumentor.printTimer: failed -  timer " + name +
					" does not exist");
		}
	}

	public static void printHeader(int section, int size,int nthreads) {

		String header = new String("");
		String base = new String("Java Grande Forum Thread Benchmark Suite - Version 1.0 - Section "); 

		if(section == 1) {
				header = base + "1";
		} else if(section ==2) {
			if(size == 0) {
				header = base + "2 - Size A";
			} else if(size == 1) {
				header = base + "2 - Size B";
			} else if(size == 2) {
				header = base + "2 - Size C";
			}
		} else if(section == 3) {
			if(size == 0) {
				header = base + "3 - Size A";
			} else if(size == 1) {
				header = base + "3 - Size B";
			}
		} else {
			System.printString("Error\n");
		}

		System.printString(header); 

		if (nthreads == 1) {
			System.printString("Executing on " + nthreads + " thread");
		}
		else {
			System.printString("Executing on " + nthreads + " threads");
		}

		System.printString("");

	} 

	/*
	public static void addTimeToTimer(String name, double added_time){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).addtime(added_time);
		}
		else {
			System.printString("JGFInstrumentor.addTimeToTimer: failed -  timer " + name +
					" does not exist");
		}



	}

	public static double readTimer(String name){
		double time; 
		if (timers.containsKey(name)) {
			time = ((JGFTimer) timers.get(name)).time;
		}
		else {
			System.printString("JGFInstrumentor.readTimer: failed -  timer " + name + 
					" does not exist");
			time = 0.0; 
		}
		return time; 
	}  

	public static void resetTimer(String name){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).reset();
		}
		else {
			System.printString("JGFInstrumentor.resetTimer: failed -  timer " + name +
					" does not exist");
		}
	}


	public static void printperfTimer(String name){
		if (timers.containsKey(name)) {
			((JGFTimer) timers.get(name)).printperf();
		}
		else {
			System.printString("JGFInstrumentor.printTimer: failed -  timer " + name +
					" does not exist");
		}
	}

	public static void storeData(String name, Object obj){
		data.put(name,obj); 
	}

	public static void retrieveData(String name, Object obj){
		obj = data.get(name); 
	}
	public static void addTimer (String name){

		if (timers.containsKey(name)) {
			System.printString("JGFInstrumentor.addTimer: warning -  timer " + name + 
					" already exists");
		}
		else {
			timers.put(name, new JGFTimer(name));
		}
	}

	public static void addTimer (String name, String opname){
		if (timers.containsKey(name)) {
			System.printString("JGFInstrumentor.addTimer: warning -  timer " + name + 
					" already exists");
		}
		else {
			timers.put(name, new JGFTimer(name,opname));
		}

	}
	*/
}
