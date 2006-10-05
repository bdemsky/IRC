/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

task Startup(StartupObject s {initialstate}) {
    System.printString("Starting\n");
    ServerSocket ss=new ServerSocket(8000);
    System.printString("Creating ServerSocket\n");
    taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

task AcceptConnection(ServerSocket ss{SocketPending}) {
    Socket s=ss.accept();
    System.printString("Creating Socket\n");
}

task IncomingIO(Socket s{IOPending}) {
    byte[] b=new byte[10];
    int length=s.read(b);
    byte[] b2=new byte[length];
    int i;
    for(i=0;i<length;i++) {
	b2[i]=b[i];
    }
    System.printString("receiving input\n");
    s.write(b2);
}

