public class Spider {
	public static void main(String[] parameters) {
		String firstmachine=parameters[0];
		String firstpage=parameters[1];

		QueryList ql=new QueryList();
		QueryQueue toprocess=new QueryQueue();
		Query firstquery=new Query(firstmachine, firstpage);
		toprocess.addQuery(firstquery);

		QueryThread qt1=new QueryThread(toprocess, ql);
		qt1.start();
		QueryThread qt2=new QueryThread(toprocess, ql);
		qt2.start();
		QueryThread qt3=new QueryThread(toprocess, ql);
		qt3.start();

		while(true)
			Thread.sleep(1000000);
    }
}
