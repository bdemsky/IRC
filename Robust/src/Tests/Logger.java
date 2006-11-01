public class Logger extends FileOutputStream {
	//Logging flag
	flag Initialize;

	//Constructor
	public Logger(){
	        System.printString(" Log Object Created\n");
		FileOutputStreamOpen("./Tests/htmlfiles/request.log");
	}
/*
	public void logrequest(String filename){
		String request = new String("\nNew Request received: ");
		write(request.getBytes());
		write(filename.getBytes());
		flush();
		close();
	}

	public void logrequest(){
		String request = new String("\nNew Request received: ");
		write(request.getBytes());
		flush();
		closerequest();
	}
*/
	public void logtesting(){
		System.printString(" testing log object\n");
	}
	public void closerequest() {
		close();	
 	}
}
