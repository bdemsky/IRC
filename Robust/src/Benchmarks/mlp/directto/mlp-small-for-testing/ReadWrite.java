// This class is the connection between the input and the output threads, 
// synchronizing the two threads

//import java.io.*;
//import java.util.*;

public class ReadWrite {

  public ReadWrite() {}

  public void read(D2 d2) {
    FileInputStream in = new FileInputStream( "input4.txt" );

    while(true) {
      String line=in.readLine();
      
      if(line==null)
	System.exit(0);		    
      
      if(d2.getMessageList().setMessage(line))
	break;
    }

    System.out.println("Input data read.");
    System.out.println("Data set read");
  }   

  public void write(D2 d2) {
    d2.getStatic().printInfo();
    d2.getFixList().printInfo();
    d2.getAircraftList().printInfo();	
    d2.getFlightList().printInfo();
  }
}
