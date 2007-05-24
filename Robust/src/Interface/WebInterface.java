package Interface;
import java.io.*;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import java.util.*;
import Util.Namer;

public class WebInterface {
    TaskAnalysis taskanalysis;
    TaskGraph taskgraph;
    State state;
    Hashtable taskmap;
    Hashtable taskgraphmap;
    GarbageAnalysis garbageanalysis;

    public WebInterface(State state, TaskAnalysis taskanalysis, TaskGraph taskgraph, GarbageAnalysis garbageanalysis) {
	this.state=state;
	this.taskanalysis=taskanalysis;
	this.taskgraph=taskgraph;
	this.garbageanalysis=garbageanalysis;

	taskmap=new Hashtable();
	taskgraphmap=new Hashtable();
    }
    
    public boolean specialRequest(String filename) {
	System.out.println(filename);
	if (filename.equals("/index.html"))
	    return true;
	if (taskmap.containsKey(filename))
	    return true;
	if (taskgraphmap.containsKey(filename))
	    return true;
	return false;
    }

    public String handleresponse(String filename, BufferedWriter out, HTTPResponse resp) {
	if (filename.equals("/index.html"))
	    return indexpage(out, resp);
	if (taskmap.containsKey(filename))
	    return flagstate((ClassDescriptor) taskmap.get(filename), out, resp);
	if (taskgraphmap.containsKey(filename))
	    return taskstate((ClassDescriptor) taskgraphmap.get(filename), out, resp);
	return "NORESP";
    }

    private String flagstate(ClassDescriptor cd, BufferedWriter out, HTTPResponse resp) {
	Set objects=taskanalysis.getFlagStates(cd);
	File file=new File(cd.getSymbol()+".dot");
	Vector namers=new Vector();
	namers.add(new Namer());
	namers.add(garbageanalysis);
	namers.add(new Allocations());
	try {
	    //Generate jpg
	    Runtime r=Runtime.getRuntime();
	    FileOutputStream dotstream=new FileOutputStream(file,false);
	    FlagState.DOTVisitor.visit(dotstream, objects, namers);
	    dotstream.close();
	    Process p=r.exec("dot -Tjpg "+cd.getSymbol()+".dot -o"+cd.getSymbol()+".jpg");
	    p.waitFor();
	    p=r.exec("dot -Tps "+cd.getSymbol()+".dot -o"+cd.getSymbol()+".ps");
	    p.waitFor();

	    PrintWriter pw=new PrintWriter(out);
	    pw.println("<a href=\"/"+ cd.getSymbol()+".ps\">ps</a><br>");
	    pw.println("<img src=\"/"+ cd.getSymbol()+".jpg\">");
	    pw.flush();
	} catch (Exception e) {e.printStackTrace();System.exit(-1);}
	return null;
    }

    private String taskstate(ClassDescriptor cd, BufferedWriter out, HTTPResponse resp) {
	Set objects=taskgraph.getTaskNodes(cd);
	File file=new File(cd.getSymbol()+"-t.dot");
	try {
	    //Generate jpg
	    Runtime r=Runtime.getRuntime();
	    FileOutputStream dotstream=new FileOutputStream(file,false);
	    FlagState.DOTVisitor.visit(dotstream, objects);
	    dotstream.close();
	    Process p=r.exec("dot -Tjpg "+cd.getSymbol()+"-t.dot -o"+cd.getSymbol()+"-t.jpg");
	    p.waitFor();
	    p=r.exec("dot -Tps "+cd.getSymbol()+".dot -o"+cd.getSymbol()+"-t.ps");
	    p.waitFor();

	    PrintWriter pw=new PrintWriter(out);
	    pw.println("<a href=\"/"+ cd.getSymbol()+"-t.ps\">ps</a><br>");
	    pw.println("<img src=\"/"+ cd.getSymbol()+"-t.jpg\">");
	    pw.flush();
	} catch (Exception e) {e.printStackTrace();System.exit(-1);}
	return null;
    }


    private String indexpage(BufferedWriter out, HTTPResponse resp) {
	PrintWriter pw=new PrintWriter(out);
	for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator();it_classes.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor) it_classes.next();
	    if (taskanalysis.getFlagStates(cd)!=null) {
		pw.println("<a href=\""+cd.getSymbol()+".html\">"+ cd.getSymbol() +"</a>");
		pw.println("<br>");
		taskmap.put("/"+cd.getSymbol()+".html", cd);
	    }
	    if (taskgraph.getTaskNodes(cd)!=null) {
		pw.println("<a href=\""+cd.getSymbol()+"-t.html\">Task Graph "+ cd.getSymbol() +"</a>");
		pw.println("<br>");
		taskgraphmap.put("/"+cd.getSymbol()+"-t.html", cd);
	    }
	}
	pw.flush();
	return null;
    }

}
