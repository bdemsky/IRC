import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class NetsClient extends Thread {

    static boolean debug;
    static int sendoption=0;

    public static void main(String argv[]) {

	String host=null;
	int numberofclients=0;
	int numberofmessages=0;
	int port=4321;

	NetsClient.debug=false;
	try {
	    host=argv[0];
	    port=Integer.parseInt(argv[1]);
	    numberofclients=Integer.parseInt(argv[2]);
	    numberofmessages=Integer.parseInt(argv[3]);
	}
	catch (Exception e) {
	    System.out.println("NetsClient host port numberofclients numberofmessages debugflag");
	}
	try {
	    NetsClient.debug=(Integer.parseInt(argv[4])==1);
	    NetsClient.sendoption=Integer.parseInt(argv[5]);
	} catch (Exception e) {}

	NetsClient[] tarray=new NetsClient[numberofclients];
	for (int i = 0; i < numberofclients; i++) {
	    String room="test";
	    tarray[i] = new NetsClient(i, host, port,
				       numberofmessages, numberofclients, room);
	    if (debug)
		System.out.println("Attempting to start "+i);
	    tarray[i].connectt();
	}

	long starttime=System.currentTimeMillis();
	for (int i = 0; i < numberofclients; i++)
	    tarray[i].start();
	try {
	    for (int i = 0; i < numberofclients; i++) {
		tarray[i].join();
	    }
	} catch (InterruptedException e) {
	    e.printStackTrace();
	    System.out.println(e);
	}
	long endtime=System.currentTimeMillis();

	int messages=0;
	for(int i=0;i<numberofclients;i++)
	    messages+=tarray[i].lines;

	System.out.println("ChatClient");
	System.out.println("numclients:" + numberofclients);
	System.out.println("port:" + port);
	System.out.println("number of messages:" + numberofmessages);
	System.out.println("Elapsed time:(mS)" + (endtime - starttime));
	System.out.println("Throughput:" + (double) numberofclients*
			   ((sendoption==4) ? 1 : numberofclients) *
			   numberofmessages/((double) (endtime-starttime)));
	System.out.println("Lines="+messages+" out of "+numberofclients*(numberofclients-1)*numberofmessages);
    }

    public NetsClient(int clientnumber, String host,
		      int port, int nom, int noc, String room) {
	this.port=port;
	this.clientnumber=clientnumber;
	this.host=host;
	this.nom=nom;
	this.noc=noc;
	this.room=room;
    }

    String room;
    int nom, noc,clientnumber,port;
    String host;
    Socket sock;
    PrintStream pout;
    InputStream in;
    OutputStream out;
    //DataInputStream din;
    BufferedReader d;
    int lines=0;

    public void connectt() {
	try{
	    sock = new Socket(host, port); // unix server
	    if (debug)
		System.out.println("connection made");
	    in = sock.getInputStream();
	    out = sock.getOutputStream();
	    pout = new PrintStream(out);
	    //din = new DataInputStream(in);
	    pout.println(room);
	}
	catch (UnknownHostException e ) {
	    System.out.println("can't find host");
	}
	catch (IOException e) {
	    System.out.println("Error connecting to host");
	}
    }

    public void run() {
	if (debug)
	    System.out.println("client thread started");
	int ns=0;

        try {
	    for(int nr=0;nr<noc*nom;nr++) {
		if ((nr%noc)==clientnumber) {
		    ns++;
		    pout.println("0|"+clientnumber+"|hello#"+ns+"**");
		}
		while(in.available()>0) {
		    int nchar=in.read();
		    if (nchar==10)
			lines++;
		}
            }
	    pout.flush();
	    long time=System.currentTimeMillis();
	    while((System.currentTimeMillis()-time)<5*1000) {
		if(in.available()>0) {
		    int nchar=in.read();
		    time=System.currentTimeMillis();
		    if (nchar==10)
			lines++;
		}
	    }
	}

        catch (UnknownHostException e ) {System.out.println("can't find host"); }
        catch ( IOException e ) {System.out.println("Error connecting to host");}

    }

} // end of client class
