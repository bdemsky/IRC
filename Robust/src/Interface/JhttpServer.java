package Interface;

//****************************************************************************
// Programmer: Duane M. Gran, ragnar@cs.bsu.edu
// Program:    JhttpServer
// Date:       April 24, 1998
//****************************************************************************

import java.net.*;
import java.io.*;

public class JhttpServer extends Thread {

  private ServerSocket server;
  private WebInterface webinterface;

//****************************************************************************
// Constructor: JhttpServer(int)
//****************************************************************************
  public JhttpServer(int port, WebInterface webinterface) {
    System.out.println("starting...");
    this.webinterface=webinterface;
    try{
      System.out.println("creating the port");
      server = new ServerSocket(port);
    }
    catch (IOException e){
      System.err.println(e);
      System.exit(1);
    }
  }

  private void startWorker(Socket client) throws Exception {
    (new JhttpWorker(client,false,webinterface)).start();
  }

  public void run() {
    // infinite loop
    while (true){
      try{
	startWorker(server.accept());
      }
      catch (Exception e){
	System.err.println(e);
      }
    }
  }
}
