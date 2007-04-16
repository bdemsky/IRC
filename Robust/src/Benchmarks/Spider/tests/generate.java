import java.io.*;

public class generate {
    public static void main(String x[]) {
	int MAX=100;
	int current=0;
	int currentref=1;
	while(current<MAX) {
	    try {
	    String filename=current+".html";
	    FileOutputStream fos=new FileOutputStream(filename);
	    PrintStream ps=new PrintStream(fos);
	    int count=0;
	    while(true) {
		if ((count++)>2)
		    break;
		int cc=currentref%MAX;
		String reffile=cc+".html";
		ps.println("<a href=\""+reffile+"\">"+reffile+"</a>");
		currentref++;
	    }
	    current++;
	    fos.close();
	    } catch (Exception e) {e.printStackTrace();}
	}
    }


}
