package IR.Flat;
import IR.MethodDescriptor;
import java.util.*;

public class FlatMethod extends FlatNode {
    FlatNode method_entry;
    MethodDescriptor method;
    Vector parameterTemps;

    FlatMethod(MethodDescriptor md, FlatNode entry) {
	method=md;
	method_entry=entry;
	parameterTemps=new Vector();
    }

    public String toString() {
	return method.toString();
    }

    public MethodDescriptor getMethod() {
	return method;
    }
    
    public void addParameterTemp(TempDescriptor t) {
	parameterTemps.add(t);
    }

    public int numParameters() {
	return parameterTemps.size();
    }

    public TempDescriptor getParameter(int i) {
	return (TempDescriptor) parameterTemps.get(i);
    }

    public FlatNode methodEntryNode() {
	return method_entry;
    }


    public Set getNodeSet() {
	HashSet tovisit=new HashSet();
	HashSet visited=new HashSet();
	tovisit.add(method_entry);
	while(!tovisit.isEmpty()) {
	    FlatNode fn=(FlatNode)tovisit.iterator().next();
	    tovisit.remove(fn);
	    visited.add(fn);
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode nn=fn.getNext(i);
		if (!visited.contains(nn))
		    tovisit.add(nn);
	    }
	}
	return visited;
    }
    
    public String printMethod() {
	String st=method+" {\n";
	HashSet tovisit=new HashSet();
	HashSet visited=new HashSet();
	int labelindex=0;
	Hashtable nodetolabel=new Hashtable();
	tovisit.add(method_entry);
	FlatNode current_node=null;
	//Assign labels 1st
	//Node needs a label if it is
	while(!tovisit.isEmpty()) {
	    FlatNode fn=(FlatNode)tovisit.iterator().next();
	    tovisit.remove(fn);
	    visited.add(fn);
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode nn=fn.getNext(i);
		if(i>0) {
		    //1) Edge >1 of node
		    nodetolabel.put(nn,new Integer(labelindex++));
		}
		if (!visited.contains(nn)) {
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
	tovisit.add(method_entry);
	while(current_node!=null||!tovisit.isEmpty()) {
	    if (current_node==null) {
		current_node=(FlatNode)tovisit.iterator().next();
		tovisit.remove(current_node);
	    }
	    visited.add(current_node);
	    if (nodetolabel.containsKey(current_node))
		st+="L"+nodetolabel.get(current_node)+":\n";
	    if (current_node.numNext()==0) {
		st+="   "+current_node.toString()+"\n";
		current_node=null;
	    } else if(current_node.numNext()==1) {
		st+="   "+current_node.toString()+"\n";
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
	return (TempDescriptor[]) parameterTemps.toArray(new TempDescriptor[ parameterTemps.size()]);
    }
}
