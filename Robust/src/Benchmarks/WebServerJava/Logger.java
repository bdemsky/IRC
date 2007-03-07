public class Logger {
    //Logger flag
    flag Initialize;
    FileOutputStream fos;


    //Constructor
    public Logger(){
	fos=new FileOutputStream("request.log");//Open request.log file 
    }

    //Logs filename as per client requests
    public synchronized void logrequest(String filename){
	String request = new String("\nNew Request received: ");
	fos.write(request.getBytes());
	fos.write(filename.getBytes());
	fos.flush();
    }

    public synchronized void logrequest(){
	String request = new String("\nNew Request received: ");
	fos.write(request.getBytes());
	fos.flush();
    }
    
    public void closerequest() {
	fos.close();	
    }
}
