public class Packet {
  int flowId;
  int fragmentId;
  int numFragment;
  int length;
  String data;

  public Packet(int numDataBytes) 
  {
    char c[] = new char[numDataBytes];
    data = new String(c);
  }

  public static int compareFlowID(Packet aPtr, Packet bPtr) 
  {
    return aPtr.flowId - bPtr.flowId;
  }

  public static int compareFragmentID(Packet aPtr, Packet bPtr)
  {
    return aPtr.fragmentId - bPtr.fragmentId;
  }
}
