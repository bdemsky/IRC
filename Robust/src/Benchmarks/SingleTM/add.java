import java.io.*;
public class add {
    public static void main(String args[]) {
      int ii=0;
      boolean outputerr=false;      
      boolean normalize=false;
      boolean timeinsec=false;
      double norma=0.0;
      for(;ii<args.length;ii++) {
	if (args[ii].equals("-err"))
	  outputerr=true;
	else if (args[ii].equals("-sec")) {
	  timeinsec=true;
	} else if (args[ii].equals("-norm")) {
	  normalize=true;
	  ii++;
	  norma=Double.parseDouble(args[ii]);
	} else
	  break;
      }

      String filename=args[ii];

      try {
	BufferedReader br=new BufferedReader(new FileReader(filename));
	double vals[]=new double[1000];
	int numvals=0;
	String nextline=null;
	while((nextline=br.readLine())!=null) {
	  double v;
	  int start=nextline.indexOf("TIME=")+5;

	  if (start==4) {
	    start=nextline.indexOf("Time: ")+6;
	    if (start==5) {
	      start=nextline.indexOf("Time = ")+7;
	      if (start==6) {
		start=nextline.indexOf("Time taken for kernel 1 is  ")+28;
		if (start==27) {
		  start=nextline.indexOf("Time taken for kernel 1 is ")+27;
		  if (start==26) {
		    start=nextline.indexOf("Elapsed time    = ")+18;
		    if (start==17) {
		      start=nextline.indexOf("Elapsed time                    = ");
		      if (start==-1) {
			start=nextline.indexOf("Learn time = ");
			if (start==-1)
			  continue;
			else
			  start+=(new String("Learn time = ")).length();
		      } else {
			start+=(new String("Elapsed time                    = ")).length();
		      }
		    }
		  }
		}
	      }
	    }
	  }
	  
	  nextline=nextline.substring(start, nextline.length());
	  int lastindex=nextline.indexOf(' ');
	  if (lastindex==-1)
	    lastindex=nextline.length();
	  String num=nextline.substring(0, lastindex);
	  v=Double.parseDouble(num);
	  if (timeinsec)
	    v=v*1000;

	  if (normalize)
	    v=norma/v;
	  vals[numvals++]=v;
	}
	double sum=0;
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
	if (!outputerr)
	  System.out.println(ave);
	else
	  System.out.println(err);
      } catch (Exception e) {
	e.printStackTrace();
      }
    }
}