import java.io.*;
public class add {
    public static void main(String args[]) {
      int ii=0;
      boolean outputerr=false;      
      boolean normalize=false;
      double norma=0.0;
      for(;ii<args.length;ii++) {
	if (args[ii].equals("-err"))
	  outputerr=true;
	else if (args[ii].equals("-norm")) {
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
	  String num=nextline.substring(start, nextline.length());
	  v=Double.parseDouble(num);
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