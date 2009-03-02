//import java.io.*;
//import java.util.*;

public class MessageList {
  private D2 d2;
  private Vector messages;
    
  public MessageList( D2 d2 ) { 
    this.d2 = d2; 
    messages=new Vector();
  }

  public Message data() {
    Message m = (Message) messages.elementAt(0);
    messages.removeElementAt(0);
    return m;
  }
    
  public Message next() {
    return data();
  }

  public boolean hasNext() {
    return messages.size() != 0;
  }

  //is true for DO_WORK
  public boolean setMessage(String line) {	
    if (line.equals(""))
      return false;

    System.out.println("I'm reading line "+line);       

    // treating comments
    if ((line.charAt(0)=='/')&&(line.charAt(1)=='/'))
      return false;

    StringTokenizer st=new StringTokenizer(line);
    int time=Integer.parseInt(st.nextToken());
    String type=st.nextToken();	
    Message newMessage=disjoint msgs new Message(d2,time,type,st);
    messages.addElement(newMessage);
    if (type.equals("DO_WORK"))
      return true;
    
    return false;
  }
  
  public void executeAll() {
    System.out.println("executeAll: we have "+messages.size()+" messages.");
    while(hasNext())
      next().executeMessage();     
    d2.getStatic().printInfo();
    d2.getFixList().printInfo();
    d2.getAircraftList().printInfo();	
    d2.getFlightList().printInfo();
    System.out.println("Messages executed\n\n\n\n\n");
  }
}
