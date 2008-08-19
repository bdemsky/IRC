package Interface;
import java.io.*;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import java.util.*;
import Util.Namer;

public class WebInterface {
  TaskAnalysis taskanalysis;
  TaskGraph taskgraph;
  TagAnalysis taganalysis;
  State state;
  Hashtable flagstatemap;
  Hashtable taskgraphmap;
  Hashtable sourcenodemap;   //to hold the filenames for each of the pages linked to the source nodes.
  Hashtable taskmap;    // to hold the filenames for each of the pages linked to tasks in the program.
  GarbageAnalysis garbageanalysis;

  public WebInterface(State state, TaskAnalysis taskanalysis, TaskGraph taskgraph, GarbageAnalysis garbageanalysis, TagAnalysis taganalysis) {
    this.state=state;
    this.taskanalysis=taskanalysis;
    this.taskgraph=taskgraph;
    this.garbageanalysis=garbageanalysis;
    this.taganalysis=taganalysis;

    flagstatemap=new Hashtable();
    taskgraphmap=new Hashtable();
    taskmap = new Hashtable();
    sourcenodemap=new Hashtable();

    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator(); it_tasks.hasNext();){
      TaskDescriptor td=(TaskDescriptor)it_tasks.next();
      taskmap.put("/"+td.getSymbol()+".html",td);
    }

    for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext();) {
      ClassDescriptor cd=(ClassDescriptor) it_classes.next();
      if(cd.hasFlags()){
	Vector rootnodes=taskanalysis.getRootNodes(cd);

	if(rootnodes!=null)
	  for(Iterator it_rootnodes=rootnodes.iterator(); it_rootnodes.hasNext();){
	    FlagState root=(FlagState)it_rootnodes.next();
	    Vector cd_nodeid=new Vector();                     //Vector is designed to contain only 2 elements: ClassDescriptor,Node label
	    // Both the values are required to correctly resolve the rootnode.
	    // Should think of a better way to do this, instead of using a vector(maybe a class)
	    cd_nodeid.addElement(cd);                      //adding the ClassDescriptor
	    cd_nodeid.addElement(root.getLabel());                     //adding the Node label
	    System.out.println(cd+" "+root.getLabel());
	    sourcenodemap.put("/"+cd.getSymbol()+"_"+root.getLabel()+".html",cd_nodeid);
	  }
      }
    }
  }

  public boolean specialRequest(String filename) {
    System.out.println(filename);
    if (filename.equals("/index.html"))
      return true;
    if (filename.equals("/UnifiedTaskGraph.html"))
      return true;
    if (flagstatemap.containsKey(filename))
      return true;
    if (taskgraphmap.containsKey(filename))
      return true;
    if (taskmap.containsKey(filename))
      return true;
    if (sourcenodemap.containsKey(filename))
      return true;
    return false;
  }

  public String handleresponse(String filename, OutputStream out, HTTPResponse resp) {
    if (filename.equals("/index.html"))
      return indexpage(out, resp);
    if (filename.equals("/UnifiedTaskGraph.html"))
      return unifiedTaskGraph(out,resp);
    if (flagstatemap.containsKey(filename))
      return flagstate((ClassDescriptor) flagstatemap.get(filename), out, resp);
    if (taskgraphmap.containsKey(filename))
      return taskstate((ClassDescriptor) taskgraphmap.get(filename), out, resp);
    if (taskmap.containsKey(filename))
      return task((TaskDescriptor)taskmap.get(filename),out,resp);
    if (sourcenodemap.containsKey(filename))
      return sourcenode((Vector) sourcenodemap.get(filename), out, resp);
    return "NORESP";
  }

  private String task(TaskDescriptor td, OutputStream out, HTTPResponse resp) {
    try {
      PrintWriter pw=new PrintWriter(out);
      pw.println("<br><br><h3>Task:&nbsp;&nbsp;&nbsp;"+td.toString()+"</h3><br>");
      printTask(td,pw);

      //printing out the classes that are instantiated by this task
      pw.println("<br><h3>Instantiated Classes:</h3>");
      Set newstates=taganalysis.getFlagStates(td);
      for(Iterator fsit=newstates.iterator(); fsit.hasNext();) {
	FlagState fsnew=(FlagState) fsit.next();
	ClassDescriptor cd=fsnew.getClassDescriptor();
	pw.println("&nbsp;&nbsp;<a href=\"/"+cd.getSymbol()+".html\">"+cd.getSymbol()+"</a><br>");
	pw.println("&nbsp;&nbsp;&nbsp;&nbsp;"+fsnew.getTextLabel()+"<br>");
      }

      pw.flush();
    } catch (Exception e) {
      e.printStackTrace(); System.exit(-1);
    }
    return null;
  }

  private String printTask(TaskDescriptor td, PrintWriter pw) {
    try {

      for(int i=0; i < td.numParameters(); i++){
	pw.println("FlagState Graph:&nbsp;&nbsp;<a href=\"/"+td.getParamType(i)+".html\">"+td.getParamType(i)+"</a><br>");
	pw.println("Task Graph:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"/"+td.getParamType(i)+"-t.html\">"
	           +td.getParamType(i)+"</a><br>");
      }
      pw.flush();
    } catch(Exception e) {
      e.printStackTrace(); System.exit(-1);
    }
    return null;
  }

  private String sourcenode(Vector cd_nodeid,OutputStream out, HTTPResponse resp) {
    Vector rootnodes=taskanalysis.getRootNodes((ClassDescriptor)cd_nodeid.elementAt(0));
    for(Iterator it_rootnodes=rootnodes.iterator(); it_rootnodes.hasNext();){
      FlagState root=(FlagState)it_rootnodes.next();
      if (root.getLabel().equals((String)cd_nodeid.elementAt(1))){
	try {
	  PrintWriter pw=new PrintWriter(out);
	  pw.println("<br><br><h3>Allocating tasks for "+root.getTextLabel()+":</h3><br>");
	  Vector tasks=root.getAllocatingTasks();
	  for(Iterator it_tasks=tasks.iterator(); it_tasks.hasNext();){
	    TaskDescriptor td=(TaskDescriptor)it_tasks.next();
	    pw.println("<br><strong>Task:&nbsp;&nbsp;&nbsp;"+td.toString()+"</strong><br>");
	    printTask(td,pw);
	  }

	} catch (Exception e) {
	  e.printStackTrace(); System.exit(-1);
	}
	break;
      }

    }
    return null;
  }

  private String flagstate(ClassDescriptor cd, OutputStream out, HTTPResponse resp) {
    Set objects=taskanalysis.getFlagStates(cd);
    File file=new File(cd.getSymbol()+".dot");
    File mapfile;
    String str;
    Vector namers=new Vector();
    namers.add(new Namer());
    namers.add(garbageanalysis);
    namers.add(new Allocations());
    namers.add(new TaskEdges());
    try {
      //Generate jpg
      Runtime r=Runtime.getRuntime();

      FileOutputStream dotstream=new FileOutputStream(file,false);
      FlagState.DOTVisitor.visit(dotstream, objects, namers);
      dotstream.close();
      Process p=r.exec("dot -Tcmapx -o"+cd.getSymbol()+".map -Tjpg -o"+cd.getSymbol()+".jpg "+cd.getSymbol()+".dot");
      p.waitFor();
      p=r.exec("dot -Tps "+cd.getSymbol()+".dot -o"+cd.getSymbol()+".ps");
      p.waitFor();

      mapfile=new File(cd.getSymbol()+".map");
      BufferedReader mapbr=new BufferedReader(new FileReader(mapfile));
      PrintWriter pw=new PrintWriter(out);
      pw.println("<a href=\"/"+ cd.getSymbol()+".ps\">ps</a><br>");
      //pw.println("<a href=\"/"+ cd.getSymbol()+".map\"><img src=\"/"+ cd.getSymbol()+".gif\" ismap=\"ismap\"></A>");
      pw.println("<img src=\""+cd.getSymbol()+".jpg\" usemap=\"#dotvisitor\" />");
      while((str=mapbr.readLine())!=null){
	pw.println(str);
      }

      pw.flush();
    } catch (Exception e) {
      e.printStackTrace(); System.exit(-1);
    }
    return null;
  }

  private String taskstate(ClassDescriptor cd, OutputStream out, HTTPResponse resp) {
    Set objects=taskgraph.getTaskNodes(cd);
    File file=new File(cd.getSymbol()+"-t.dot");
    File mapfile;
    String str;
    Vector namers=new Vector();
    namers.add(new Namer());
    namers.add(new TaskNodeNamer());

    try {
      //Generate jpg
      Runtime r=Runtime.getRuntime();
      FileOutputStream dotstream=new FileOutputStream(file,false);
      FlagState.DOTVisitor.visit(dotstream, objects,namers);
      dotstream.close();
      Process p=r.exec("dot -Tcmapx -o"+cd.getSymbol()+"-t.map -Tjpg -o"+cd.getSymbol()+"-t.jpg "+cd.getSymbol()+"-t.dot");
      p.waitFor();
      p=r.exec("dot -Tps "+cd.getSymbol()+".dot -o"+cd.getSymbol()+"-t.ps");

      p.waitFor();

      mapfile=new File(cd.getSymbol()+"-t.map");
      BufferedReader mapbr=new BufferedReader(new FileReader(mapfile));
      PrintWriter pw=new PrintWriter(out);
      pw.println("<a href=\"/"+ cd.getSymbol()+"-t.ps\">ps</a><br>");
      // pw.println("<a href=\"/"+ cd.getSymbol()+"-t.map\"><img src=\"/"+ cd.getSymbol()+"-t.gif\" ismap=\"ismap\"></A>");
      pw.println("<img src=\""+cd.getSymbol()+"-t.jpg\" usemap=\"#dotvisitor\" />");

      while((str=mapbr.readLine())!=null){
	pw.println(str);
      }
      pw.flush();
    } catch (Exception e) {
      e.printStackTrace(); System.exit(-1);
    }
    return null;
  }

  /* public void taskgraph(
   */

  private String indexpage(OutputStream out, HTTPResponse resp) {

    PrintWriter pw=new PrintWriter(out);
    for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext();) {
      ClassDescriptor cd=(ClassDescriptor) it_classes.next();
      if (cd.hasFlags()){
	if (taskanalysis.getFlagStates(cd)!=null) {
	  pw.println("<a href=\""+cd.getSymbol()+".html\">"+ cd.getSymbol() +"</a>");
	  pw.println("<br>");
	  flagstatemap.put("/"+cd.getSymbol()+".html", cd);
	}
	if (taskgraph.getTaskNodes(cd)!=null) {
	  pw.println("<a href=\""+cd.getSymbol()+"-t.html\">Task Graph "+ cd.getSymbol() +"</a>");
	  pw.println("<br>");
	  taskgraphmap.put("/"+cd.getSymbol()+"-t.html", cd);
	}
      }
    }
    pw.println("<br><br><a href=\"/UnifiedTaskGraph.html\">Program flow</a>");
    pw.flush();
    return null;
  }

  private String unifiedTaskGraph(OutputStream out, HTTPResponse resp) {
    Set objects=taskgraph.getAllTaskNodes();
    File file=new File("UnifiedTaskGraph.dot");
    String str;
    Vector namers=new Vector();
    namers.add(new Namer());
    namers.add(new TaskNodeNamer());

    try {
      //Generate jpg
      Runtime r=Runtime.getRuntime();
      FileOutputStream dotstream=new FileOutputStream(file,false);
      FlagState.DOTVisitor.visit(dotstream, objects, namers);
      dotstream.close();
      Process p=r.exec("dot -Tjpg -oUnifiedTaskGraph.jpg -Tcmapx -oUnifiedTaskGraph.map UnifiedTaskGraph.dot");
      p.waitFor();
      p=r.exec("dot -Tps UnifiedTaskGraph.dot -oUnifiedTaskGraph.ps");

      p.waitFor();

      File mapfile=new File("UnifiedTaskGraph.map");
      BufferedReader mapbr=new BufferedReader(new FileReader(mapfile));
      PrintWriter pw=new PrintWriter(out);
      pw.println("<a href=\"/UnifiedTaskGraph.ps\">ps</a><br>");
      // pw.println("<a href=\"/"+ cd.getSymbol()+"-t.map\"><img src=\"/"+ cd.getSymbol()+"-t.gif\" ismap=\"ismap\"></A>");
      pw.println("<img src=\"/UnifiedTaskGraph.jpg\" usemap=\"#dotvisitor\"  />");

      while((str=mapbr.readLine())!=null)
	pw.println(str);

      pw.flush();
    } catch (Exception e) {
      e.printStackTrace(); System.exit(-1);
    }
    return null;
  }

}
