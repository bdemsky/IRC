public class Room {
    String name;
    HashSet participants;
    public Room(int i) {
	name=String.valueOf(i);
	participants=new HashSet();
    }
    
    void addParticipant(ChatSocket cs) {
	participants.add(cs);
	cs.room=this;
    }

    void sendToRoom(ChatSocket caller, byte [] message) {
	HashMapIterator hmi=participants.iterator();
	while(hmi.hasNext()) {
	    ChatSocket cs=(ChatSocket) hmi.next();
	    if (cs!=caller)
		cs.write(message);
	}
    }
}
