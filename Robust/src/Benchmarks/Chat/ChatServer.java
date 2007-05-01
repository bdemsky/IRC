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

task Messages(ChatSocket cs{InRoom && IOPending}) {
    byte buffer[]=new byte[1024];
    int length=cs.read(buffer);
    Message m=new Message(buffer, length, cs){};
}

task SendMessage(Message m{!Sent}) {
    String st=(new String(m.buffer)).subString(0, m.length);
    m.cs.room.sendToRoom(m.cs,st.getBytes());
    taskexit(m {Sent});
}
