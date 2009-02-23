// This class is the connection between the input and the output threads, 
// synchronizing the two threads
// It reads effectively reads the input and writes the output and  


import java.io.*;
import java.util.*;

class ReadWrite
{

    private boolean readAccess=true;
    private static boolean readDone = false;

    public synchronized void read(RandomAccessFile f) throws IOException
    {
	// reads a data set 	
	if (!readAccess)
	    {
		try{
		    wait();
		} catch (InterruptedException e){}
	    }
	
	// read operations

	try{	   
	    while (true)
		{
		    String line=f.readLine();
		    if (line==null)
			System.exit(0);		    
		    if (MessageList.setMessage(line))
			break;
		}
	} catch (EOFException e) {};	  		    
	
	System.out.println("Input data read.");

	//	D2.threadRead.stop();
	// just temporarly
	//	MessageList.executeAll();
	
	/*Algorithm.doIteration();	
	Static.printInfo();
       	FixList.printInfo();
	AircraftList.printInfo();	
	FlightList.printInfo();

	ConflictList.printInfo();
	System.out.println("Connflicts List OVER");*/

	readAccess=false;
    }   


    public synchronized void write(RandomAccessFile f) throws IOException
    {
      //System.out.println("Does it reach 'write'?");
	// writes a data set
	if (readAccess)
	    {
		try{
		    wait();
		} catch (InterruptedException e){}
		
	    }
	
	// write operations
	
	Static.printInfo();
       	FixList.printInfo();
	AircraftList.printInfo();	
	FlightList.printInfo();
	//	Algorithm.doWork(f);
	readAccess=true;
	notify();
	
    }

}
