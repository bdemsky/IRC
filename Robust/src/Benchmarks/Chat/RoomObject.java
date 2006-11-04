public class RoomObject {
    flag Initialized;
    HashMap rooms;
    public RoomObject() {
	rooms=new HashMap();
    }

    Room getChatRoom(String name) {
	if (rooms.containsKey(name))
	    return (Room) rooms.get(name);
	Room r=new Room(name);
	rooms.put(name, r);
	return r;
    }
}
