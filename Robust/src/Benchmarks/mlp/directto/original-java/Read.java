// class Read - reads the input from local file/network

import java.io.*;

class Read extends Thread
{
    private ReadWrite buffer;
    private RandomAccessFile f;

    public Read(ReadWrite b) throws IOException
    {
	buffer=b;
	try{
	    f=new RandomAccessFile("input.txt","r");
	} catch (FileNotFoundException e) {System.out.println("File Open Error");return;}
	
    }
    
    public void run()
    {
	try {
	    //	    while(true)		
		buffer.read(f);
	} catch(IOException e) {System.out.println("Invalid Input Data");System.exit(0);}
	System.out.println("Data set read");
    }
}

















