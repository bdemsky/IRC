
// This class contains the the main method of this project. 
// All it does is to initialize the input and output threads and
// to launch the graphical interface

import java.io.*;

class D2
{
    public static ReadWrite rw;
    public static Read threadRead;

    public static void main(String arg[]) throws Exception
    {
	System.out.println("D2 - Application started");

	rw=new ReadWrite();
	threadRead=new Read(rw);
	//	Write threadWrite=new Write(rw);	
			
	//	Graphic mainGraphic=new Graphic();
	//	mainGraphic.display();

	threadRead.run();
	//System.out.println("Hey!!!");
	MessageList.executeAll();
	
	while (FlightList.anyPlanesAlive())
	    Algorithm.doIteration();
	//	threadWrite.start();
    }

}




