public class ChatSocket extends Socket {
    flag Initialized;
    flag ProcessRoom;
    flag InRoom;
    Room room;
    String roomrequest;

    public ChatSocket() {
    }

    public boolean processRead() {
        byte buffer[]=new byte[1024];
        int length=read(buffer);
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
	int i=Integer.parseInt(roomrequest);
	ro.getChatRoom(i).addParticipant(this);
    }
}
