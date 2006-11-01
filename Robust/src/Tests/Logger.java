public class Logger extends FileOutputStream {
	//Logger flag
	flag Initialize;

	//Constructor
	public Logger(){
		FileOutputStreamOpen("./Tests/htmlfiles/request.log");//Open request.log file 
	}
	//Logs filename as per client requests
	public void logrequest(String filename){
		String request = new String("\nNew Request received: ");
		write(request.getBytes());
		write(filename.getBytes());
		flush();
	}

	public void logrequest(){
		String request = new String("\nNew Request received: ");
		write(request.getBytes());
		flush();
	}

	public void closerequest() {
		close();	
 	}
}
