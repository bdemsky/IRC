// class Write - writes the output to local file/network

import java.io.*;

class Write extends Thread
{
    private ReadWrite buffer;
    private RandomAccessFile f;
    
    public Write(ReadWrite b) throws IOException
    {
	buffer=b;      
	try{
	    f=new RandomAccessFile("results.out","rw");
	} catch (FileNotFoundException e) {System.out.println("File Open Error");return;}
    }


    public void run()
    {
	try {
	    while (true)
		buffer.write(f);
	} catch(IOException e) {System.out.println("Invalid Output Data");System.exit(0);}
	System.out.println("Data set written");
    }
}
