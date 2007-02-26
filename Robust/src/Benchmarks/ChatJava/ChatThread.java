public class ChatThread extends Thread {
    Room room;
    String roomrequest;
    Socket sock;
    RoomObject ro;

    public ChatThread(Socket sock, RoomObject ro) {
	this.sock=sock;
	this.ro=ro;
    }

    public void run() {
	sock.write("Please choose a chatroom".getBytes());
	ReadRequest();
	ProcessRoom();
	while(true)
	    Message();
    }

    public void ReadRequest() {
	while (!processRead())
	    ;
    }

    private void ProcessRoom() {
	processRoom(ro);
    }

    public void Message() {
	byte buffer[]=new byte[1024];
	int length=sock.read(buffer);
	String st=(new String(buffer)).subString(0, length);
	System.printString(st);
	System.printString("\n");
	System.printInt(length);
	System.printString("\n");
	room.sendToRoom(this, st.getBytes());
    }
    
    public boolean processRead() {
        byte buffer[]=new byte[1024];
        int length=sock.read(buffer);
        String st=new String(buffer);
        String curr=st.subString(0, length);
        if (roomrequest!=null) {
            StringBuffer sb=new StringBuffer(roomrequest);
            sb.append(curr);
            curr=sb.toString();
        }
        roomrequest=curr;
        if (roomrequest.indexOf("\n")>=0) {
	    return true;
        }
        return false;
    }
    public void processRoom(RoomObject ro) {
	ro.getChatRoom(roomrequest).addParticipant(this);
    }
}
