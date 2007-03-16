import java.io.*;
public class analyze {
    public static void main(String[] q) {
	int sum=0;
	int count=0;
	try {
	BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
	while(true) {
	    String s=br.readLine();
	    String x=s.substring(6,s.indexOf(' ',6));
	    
	    sum+=(new Integer(x)).intValue();
	    count++;
	    System.out.println(sum+"   "+(((double)sum)/count));
	}
	} catch(Exception e) {}
    }



}
