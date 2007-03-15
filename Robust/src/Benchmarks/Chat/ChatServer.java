task Startup(StartupObject s{initialstate}) {
    System.printString("Chat Server Benchmark");
    RoomObject ro=new RoomObject() {Initialized};
    ServerSocket ss=new ServerSocket(8000);
    taskexit(s{!initialstate});
}

task AcceptConnection(ServerSocket ss{SocketPending}) {
    ChatSocket cs=new ChatSocket() {Initialized};
    ss.accept(cs);
    cs.write("Please choose a chatroom".getBytes());
}

task ReadRequest(ChatSocket cs{Initialized && IOPending}) {
    if (cs.processRead()) {
	taskexit(cs{!Initialized, ProcessRoom});
    }
}

task ProcessRoom(ChatSocket cs{ProcessRoom}, RoomObject ro{Initialized}) {
    cs.processRoom(ro);
    taskexit(cs{!ProcessRoom, InRoom});
}

task Message(ChatSocket cs{InRoom && IOPending}) {
    byte buffer[]=new byte[1024];
    int length=cs.read(buffer);
    String st=(new String(buffer)).subString(0, length);
    Message m=new Message(st, cs){};
}

task SendMessage(Message m{!Sent}) {
    m.cs.room.sendToRoom(m.cs,m.st.getBytes());
    taskexit(m {Sent});
}
