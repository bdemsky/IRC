package IR.Flat;
import IR.MethodDescriptor;
import IR.TaskDescriptor;
import java.util.*;

public class FlatMethod extends FlatNode {
  MethodDescriptor method;
  TaskDescriptor task;
  Vector parameterTemps;
  Vector tagTemps;
  Hashtable tagtointmap;
  FlatExit flatExit;

  FlatMethod(MethodDescriptor md, FlatExit fe) {
    method=md;
    task=null;
    parameterTemps=new Vector();
    tagTemps=new Vector();
    tagtointmap=new Hashtable();
    flatExit=fe;
  }

  FlatMethod(TaskDescriptor td, FlatExit fe) {
    task=td;
    method=null;
    parameterTemps=new Vector();
    tagTemps=new Vector();
    tagtointmap=new Hashtable();
    flatExit=fe;
  }

  public String toString() {
    String ret = "FlatMethod_";
    if( method != null ) {
      ret += method.toString();
    } else {
      ret += task.toString();
    }
    ret+="(";
    boolean first=true;
    for(int i=0;i<numParameters();i++) {
      if (first) {
	first=false;
      } else
	ret+=", ";
      ret+=getParameter(i);
    }
    ret+=")";
    return ret;
  }

  public MethodDescriptor getMethod() {
    return method;
  }

  public TaskDescriptor getTask() {
    return task;
  }

  public int kind() {
    return FKind.FlatMethod;
  }

  public void addParameterTemp(TempDescriptor t) {
    parameterTemps.add(t);
  }

  public int numParameters() {
    return parameterTemps.size();
  }

  public void addTagTemp(TempDescriptor t) {
    tagtointmap.put(t, new Integer(tagTemps.size()));
    tagTemps.add(t);
  }

  public int getTagInt(TempDescriptor t) {
    return ((Integer)tagtointmap.get(t)).intValue();
  }

  public int numTags() {
    return tagTemps.size();
  }

  public TempDescriptor getTag(int i) {
    return (TempDescriptor) tagTemps.get(i);
  }

  public TempDescriptor getParameter(int i) {
    return (TempDescriptor) parameterTemps.get(i);
  }

  public FlatExit getFlatExit() {
    return flatExit;
  }

  /** This method returns a set of the nodes in this flat representation */

  public Set<FlatNode> getNodeSet() {
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    tovisit.add(this);
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);
      for(int i=0; i<fn.numNext(); i++) {
	FlatNode nn=fn.getNext(i);
	if (!visited.contains(nn))
	  tovisit.add(nn);
      }
    }
    return visited;
  }

  public String printMethod() {
    return printMethod(null);
  }

  /** This method returns a string that is a human readable
   * representation of this method. */

  public String printMethod(Hashtable map) {
    String st=method+" {\n";
    HashSet tovisit=new HashSet();
    HashSet visited=new HashSet();
    int labelindex=0;
    Hashtable nodetolabel=new Hashtable();
    tovisit.add(this);
    FlatNode current_node=null;
    //Assign labels 1st
    //Node needs a label if it is
    while(!tovisit.isEmpty()) {
      FlatNode fn=(FlatNode)tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);

      for(int i=0; i<fn.numNext(); i++) {
	FlatNode nn=fn.getNext(i);
	if(i>0) {
	  //1) Edge >1 of node
	  nodetolabel.put(nn,new Integer(labelindex++));
	}
	if (!visited.contains(nn)&&!tovisit.contains(nn)) {
	  tovisit.add(nn);
	} else {
	  //2) Join point
	  nodetolabel.put(nn,new Integer(labelindex++));
	}
      }
    }

    //Do the actual printing
    tovisit=new HashSet();
    visited=new HashSet();
    tovisit.add(this);
    while(current_node!=null||!tovisit.isEmpty()) {
      if (current_node==null) {
	current_node=(FlatNode)tovisit.iterator().next();
	tovisit.remove(current_node);
      } else {
	if (tovisit.contains(current_node))
	  tovisit.remove(current_node);
      }
      visited.add(current_node);
      if (nodetolabel.containsKey(current_node)) {
	st+="L"+nodetolabel.get(current_node)+":\n";
	for(int i=0;i<current_node.numPrev();i++) {
	  st+="i="+i+" "+current_node.getPrev(i);
	}
	st+="\n";
      }
      if (current_node.numNext()==0) {
	if (map==null)
	  st+="   "+current_node.toString()+"\n";
	else
	  st+="   "+current_node.toString()+"["+map.get(current_node)+"]\n";
	current_node=null;
      } else if(current_node.numNext()==1) {
	if (map==null)
	  st+="   "+current_node.toString()+"\n";
	else
	  st+="   "+current_node.toString()+"["+map.get(current_node)+"]\n";
	FlatNode nextnode=current_node.getNext(0);
	if (visited.contains(nextnode)) {
	  st+="goto L"+nodetolabel.get(nextnode)+"\n";
	  current_node=null;
	} else
	  current_node=nextnode;
      } else if (current_node.numNext()==2) {
	/* Branch */
	st+="   "+((FlatCondBranch)current_node).toString("L"+nodetolabel.get(current_node.getNext(1)))+"\n";
	if (!visited.contains(current_node.getNext(1)))
	  tovisit.add(current_node.getNext(1));
	if (visited.contains(current_node.getNext(0))) {
	  st+="goto L"+nodetolabel.get(current_node.getNext(0))+"\n";
	  current_node=null;
	} else
	  current_node=current_node.getNext(0);
      } else throw new Error();
    }
    return st+"}\n";
  }

  public TempDescriptor [] writesTemps() {
    return (TempDescriptor[])parameterTemps.toArray(new TempDescriptor[ parameterTemps.size()]);
  }
}
