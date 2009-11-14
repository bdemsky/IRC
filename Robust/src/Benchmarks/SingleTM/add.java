import java.io.*;
public class add {
    public static void main(String args[]) {
	String filename=args[0];
	try {
	    BufferedReader br=new BufferedReader(new FileReader(filename));
	    long vals[]=new long[1000];
	    int numvals=0;
	    String nextline=null;
	    while((nextline=br.readLine())!=null) {
		long v;
		int start=nextline.indexOf("TIME=")+5;
		String num=nextline.substring(start, nextline.length());
		v=Long.parseLong(num);
		vals[numvals++]=v;
	    }
	    long sum=0;
	    for(int i=0;i<numvals;i++) {
		sum+=vals[i];
	    }
	    double ave=((double)sum)/numvals;
	    double diff=0;
	    for(int i=0;i<numvals;i++) {
		double delta=vals[i]-ave;
		diff+=delta*delta;
	    }
	    diff=diff/(numvals-1);
	    double std=Math.sqrt(diff);
	    double err=std/Math.sqrt(numvals);
	    if (args.length==1)
		System.out.println(ave);
	    else
		System.out.println(err);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }


}