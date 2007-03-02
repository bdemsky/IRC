import java.io.*;

public class Logger {
    //Logger flag
    FileOutputStream fos;


    //Constructor
    public Logger(){
	try {
	fos=new FileOutputStream("request.log");//Open request.log file 
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    //Logs filename as per client requests
    public void logrequest(String filename){
	try {
	String request = new String("\nNew Request received: ");
	fos.write(request.getBytes());
	fos.write(filename.getBytes());
	fos.flush();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void logrequest(){
	try {
	String request = new String("\nNew Request received: ");
	fos.write(request.getBytes());
	fos.flush();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    
    public void closerequest() {
	try {
	    fos.close();	
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
