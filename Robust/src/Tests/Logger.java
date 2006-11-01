public class Logger extends FileOutputStream {
	//Logging flag
	flag LogPending;

	//Constructor
	public Logger(){
		FileOutputStreamOpen("./Tests/htmlfiles/request.log");
	}

	public void logrequest(String filename){
		String request = new String("\nNew Request received: ");
		write(request.getBytes());
		write(filename.getBytes());
		flush();
	}

	public void logrequest(){
		String request = new String("\nNew Request received: ");
		flush();
	}

	public void closerequest() {
		close();	
 	}
}
