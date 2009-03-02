import java.io.*;
import java.util.*;



class MessageList
{
    private static LinkedList messages=new LinkedList();
    
    public static Message data()
    {
	return (Message) messages.getFirst();
    }
    
    public static Message next()
    {
        Message mAux=new Message((Message) messages.getFirst());
	messages.removeFirst();
	return (Message) mAux;
    }

    public static boolean hasNext()
    {
	return (messages.size()!=0);
    }

  public static boolean setMessage(String line) //is true for DO_WORK
    {	
        if (line.equals(""))
	   return false;
	System.out.println("I'm reading line "+line);       
        // treating comments
        if ((line.charAt(0)=='/')&&(line.charAt(1)=='/'))
  	    return false;
        StringTokenizer st=new StringTokenizer(line);
        int time=Integer.parseInt(st.nextToken());
        String type=st.nextToken();	
        Message newMessage=new Message(time,type,st);
	//System.out.println("???"+newMessage);	
	messages.add(newMessage);
	if (type.equals("DO_WORK"))
	  return true;
	else return false;
    }

  
  public static void executeAll()
  {
    System.out.println("executeAll: we have "+messages.size()+" messages.");
    while (hasNext())
      next().executeMessage();     
    Static.printInfo();
    FixList.printInfo();
    AircraftList.printInfo();	
    FlightList.printInfo();
    System.out.println("Messages executed\n\n\n\n\n");
  }

}




