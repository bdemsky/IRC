// This class is the connection between the input and the output threads, 
// synchronizing the two threads

//import java.io.*;
//import java.util.*;

class ReadWrite {
  D2 d2;

  public ReadWrite( D2 d2 ) {
    this.d2 = d2;
  }

  public void read() {
    FileInputStream in = new FileInputStream( "input.txt" );

    while(true) {
      String line=in.readLine();
      
      if(line==null)
	System.exit(0);		    
      
      if(d2.getMessageList().setMessage(line))
	break;
    }

    System.out.println("Input data read.");
  }   

  public void write() {
    d2.getStatic().printInfo();
    d2.getFixList().printInfo();
    d2.getAircraftList().printInfo();	
    d2.getFlightList().printInfo();
  }
}
