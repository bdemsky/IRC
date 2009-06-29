public class Packet {
  int flowId;
  int fragmentId;
  int numFragment;
  int length;
  String data;

  public Packet(int numDataBytes) 
  {
    char c[] = new char[numDataByte];
    data = new String(c);
  }

  public long packet_compareFlowID(Packet aPtr, Packet bPtr) 
  {
    return aPtr.flowId - bPtr.flowId;
  }

  public long packet_compareFragmentID(Packet aPtr, Packet bPtr)
  {
    return aPtr.fragmentId - bPtr.fragmentId;
  }
}
