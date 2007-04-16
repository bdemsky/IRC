public class Message {
    flag Sent;
    ChatSocket cs;
    byte buffer[];
    int length;

    public Message(byte[]  b, int l, ChatSocket cs) {
	this.cs=cs;
	this.buffer=b;
	this.length=l;
    }
}
