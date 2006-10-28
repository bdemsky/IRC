public class Logger extends FileOutputStream {
	//Logging flag
	flag LogPending;
	
	//Constructor
	public Logger(){
	}

	public void logrequest(){
		String filepath = new String("./Tests/htmlfiles/request.log");
		String request = new String(" New Request received\n");
		int mode=0;
		FileOutputStream logfile = new FileOutputStream(filepath,mode);
		logfile.write(request.getBytes());
		logfile.close();	
	}

}
