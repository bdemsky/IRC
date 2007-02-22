public class Room {
    String name;
    HashSet participants;
    public Room(String n) {
	name=n;
	participants=new HashSet();
    }
    
    synchronized void addParticipant(ChatThread cs) {
	participants.add(cs);
	cs.room=this;
    }

    synchronized void sendToRoom(ChatThread caller, byte [] message) {
	HashMapIterator hmi=participants.iterator();
	while(hmi.hasNext()) {
	    ChatThread cs=(ChatThread) hmi.next();
	    if (cs!=caller)
		cs.sock.write(message);
	}
    }
}
