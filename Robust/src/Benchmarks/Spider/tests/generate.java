import java.io.*;

public class generate {
    public static void main(String x[]) {
	int MAX=100;
	int current=0;
	int currentref=1;
	while(current<currentref) {
	    try {
	    String filename=current+".html";
	    FileOutputStream fos=new FileOutputStream(filename);
	    PrintStream ps=new PrintStream(fos);
	    int count=0;
	    while(currentref<MAX) {
		if ((count++)>2)
		    break;
		String reffile=currentref+".html";
		ps.println("<a href=\""+reffile+"\">"+reffile+"</a>");
		currentref++;
	    }
	    current++;
	    fos.close();
	    } catch (Exception e) {e.printStackTrace();}
	}
    }


}
