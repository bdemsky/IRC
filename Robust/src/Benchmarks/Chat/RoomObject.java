public class RoomObject {
    flag Initialized;
    Room[] rooms;
    public RoomObject() {
	rooms=new Room[10];
	for(int i=0;i<rooms.length;i++)
	    rooms[i]=new Room(i);
    }

    Room getChatRoom(int i) {
	return rooms[i];
    }
}
