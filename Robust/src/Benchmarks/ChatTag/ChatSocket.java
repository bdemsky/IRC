public class ChatSocket {
    flag Initialized;
    flag ProcessRoom;
    flag InRoom;
    Room room;
    String roomrequest;
    Socket sock;

    public ChatSocket() {
    }

    public boolean processRead(Socket s) {
        byte buffer[]=new byte[1024];
        int length=s.read(buffer);
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
