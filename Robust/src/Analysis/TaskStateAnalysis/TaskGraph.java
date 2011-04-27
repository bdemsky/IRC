package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.State;
import IR.SymbolTable;
import IR.ClassDescriptor;
import IR.TaskDescriptor;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;

public class TaskGraph {
  TaskAnalysis taskanalysis;
  State state;
  Hashtable cdtonodes;
  Hashtable nodes;
  Hashtable<TaskNode,TaskNode> alltasknodes;

  //Colors
  String colors[]={"red","blue","green","brown","orange","pink","black","grey","olivedrab","yellow"};

  public TaskGraph(State state, TaskAnalysis taskanalysis) {
    this.state=state;
    this.taskanalysis=taskanalysis;
    this.cdtonodes=new Hashtable();
    this.alltasknodes=new Hashtable<TaskNode,TaskNode>();

    for(Iterator classit=state.getClassSymbolTable().getDescriptorsIterator(); classit.hasNext(); ) {
      ClassDescriptor cd=(ClassDescriptor) classit.next();
      if (cd.hasFlags())
	produceTaskNodes(cd);
    }
    produceAllTaskNodes();
  }


  public void createDOTfiles() {
    for(Iterator it_classes=(Iterator)cdtonodes.keys(); it_classes.hasNext(); ) {
      ClassDescriptor cd=(ClassDescriptor) it_classes.next();
      Set tasknodes=getTaskNodes(cd);
      if (tasknodes!=null) {
	try {
	  File dotfile_tasknodes=new File("graph"+cd.getSymbol()+"_task.dot");
	  FileOutputStream dotstream=new FileOutputStream(dotfile_tasknodes,true);
	  TaskNode.DOTVisitor.visit(dotstream,tasknodes);
	} catch(Exception e) {
	  e.printStackTrace();
	  throw new Error();
	}
      }
    }
  }

  /** Returns the set of TaskNodes for the class descriptor cd */

  public Set getTaskNodes(ClassDescriptor cd) {
    if (cdtonodes.containsKey(cd))
      return ((Hashtable)cdtonodes.get(cd)).keySet();
    else
      return null;
  }

  private TaskNode canonicalizeTaskNode(Hashtable nodes, TaskNode node) {
    if (nodes.containsKey(node))
      return (TaskNode)nodes.get(node);
    else {
      nodes.put(node,node);
      return (TaskNode)node;
    }
  }

  private void produceTaskNodes(ClassDescriptor cd) {
    Set fsnodes=taskanalysis.getFlagStates(cd);
    if (fsnodes==null)
      return;

    Hashtable<TaskNode,TaskNode> tasknodes=new Hashtable<TaskNode,TaskNode>();
    cdtonodes.put(cd, tasknodes);

    for(Iterator it=fsnodes.iterator(); it.hasNext(); ) {
      FlagState fs=(FlagState)it.next();
      Iterator it_inedges=fs.inedges();
      TaskNode tn,sn;

      if (fs.isSourceNode()) {
	Vector src=fs.getAllocatingTasks();
	for(Iterator it2=src.iterator(); it2.hasNext(); ) {
	  TaskDescriptor td=(TaskDescriptor)it2.next();
	  sn=new TaskNode(td.getSymbol());
	  if(fs.edges().hasNext()) {
	    addEdges(fs,sn,tasknodes);
	  }
	}
      }

      while(it_inedges.hasNext()) {

	FEdge inedge=(FEdge)it_inedges.next();
	tn=new TaskNode(inedge.getLabel());
	if(fs.edges().hasNext()) {
	  addEdges(fs,tn,tasknodes);
	}
      }
    }
  }

  private void produceAllTaskNodes() {
    alltasknodes=new Hashtable<TaskNode,TaskNode>();

    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator(); it_tasks.hasNext(); ) {
      TaskDescriptor td=(TaskDescriptor)it_tasks.next();
      TaskNode tn=new TaskNode(td.getSymbol());
      alltasknodes.put(tn,tn);
    }
    TaskNode tn_runtime=new TaskNode("Runtime");
    alltasknodes.put(tn_runtime,tn_runtime);

    int ColorID=0;
    for(Iterator classit=state.getClassSymbolTable().getDescriptorsIterator(); classit.hasNext()&&ColorID<10; ) {
      ClassDescriptor cd=(ClassDescriptor) classit.next();
      Set fsnodes;

      if (cd.hasFlags()&&((fsnodes=taskanalysis.getFlagStates(cd))!=null)) {
	//
	System.out.println("\nWorking on fses of Class: "+cd.getSymbol());
	//
	for(Iterator it=fsnodes.iterator(); it.hasNext(); ) {
	  FlagState fs=(FlagState)it.next();
	  //
	  System.out.println("Evaluating fs: "+fs.getTextLabel());
	  //
	  Iterator it_inedges=fs.inedges();
	  TaskNode tn,sn;


	  if (fs.isSourceNode()) {
	    //
	    System.out.println("A sourcenode");
	    //
	    if(fs.edges().hasNext()) {
	      Vector allocatingtasks=fs.getAllocatingTasks();
	      //
	      if (allocatingtasks.iterator().hasNext())
		System.out.println("has been allocated by "+allocatingtasks.size()+" tasks");
	      //
	      for(Iterator it_at=allocatingtasks.iterator(); it_at.hasNext(); ) {
		TaskDescriptor allocatingtd=(TaskDescriptor)it_at.next();
		//
		System.out.println(allocatingtd.getSymbol());
		//
		tn=new TaskNode(allocatingtd.getSymbol());

		addEdges(fs,tn,alltasknodes,ColorID);
	      }
	    }
	  }

	  while(it_inedges.hasNext()) {
	    FEdge inedge=(FEdge)it_inedges.next();
	    tn=new TaskNode(inedge.getLabel());
	    if(fs.edges().hasNext()) {
	      addEdges(fs,tn,alltasknodes,ColorID);
	    }
	  }
	}
	ColorID++;
      }

    }
  }

  public Set getAllTaskNodes() {
    return alltasknodes.keySet();
  }








  /*    private void mergeAllNodes(){
                Hashtable alltasks=new Hashtable();
                for(Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();classit.hasNext();) {
                ClassDescriptor cd=(ClassDescriptor) classit.next();
                        Set tnodes=((Hashtable)cdtonodes.get(cd)).keyset();
                        while (it_tnodes=tnodes.iterator();it_nodes.hasNext()){
                                TaskNode tn=it_nodes.next();
                                if (alltasks.containsKey(tn)){
                                        while(tn.
                                }
                        }




     }

   */

  private void addEdges(FlagState fs, TaskNode tn,Hashtable<TaskNode,TaskNode> tasknodes) {

    //  Hashtable<TaskNode,TaskNode> tasknodes=(Hashtable<TaskNode,TaskNode>)cdtonodes.get(fs.getClassDescriptor());
    tn=(TaskNode)canonicalizeTaskNode(tasknodes, tn);
    for (Iterator it_edges=fs.edges(); it_edges.hasNext(); ) {
      TaskNode target=new TaskNode(((FEdge)it_edges.next()).getLabel());
      target=(TaskNode)canonicalizeTaskNode(tasknodes,target);

      TEdge newedge=new TEdge(target);
      if (!tn.edgeExists(newedge))
	tn.addEdge(newedge);
    }

  }

  private void addEdges(FlagState fs, TaskNode tn,Hashtable<TaskNode,TaskNode> tasknodes,int ColorID) {

    tn=(TaskNode)canonicalizeTaskNode(tasknodes, tn);
    for (Iterator it_edges=fs.edges(); it_edges.hasNext(); ) {
      TaskNode target=new TaskNode(((FEdge)it_edges.next()).getLabel());
      target=(TaskNode)canonicalizeTaskNode(tasknodes,target);

      TEdge newedge=new TEdge(target);
      newedge.setDotNodeParameters("style=bold, color = "+colors[ColorID]);
      if (!tn.edgeExists(newedge))
	tn.addEdge(newedge);
    }

  }

}
