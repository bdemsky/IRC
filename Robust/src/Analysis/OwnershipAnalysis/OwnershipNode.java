package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipNode {

    protected int id;
    protected Vector<OwnershipNode> inEdges;
    protected Vector<OwnershipNode> outEdges;

    public OwnershipNode( int id ) {
	this.id  = id;
	inEdges  = new Vector<OwnershipNode>();
	outEdges = new Vector<OwnershipNode>();
    }

    public String getIDString() {
	return (new Integer( id )).toString();
    }



    /*
digraph test 
{
  node [shape=record];
  p1 [label="{P1 p1|{<x>int x|         {<m>Thing m|{<j>int j|{Part f|{a|<b>b|c}}|{Part g|{a|b|c}}}}}}"];
  p2 [label="{P2 p2|{<y>int y|<z>int z|{<n>Thing n|{<j>int j|{Part f|{a|<b>b|c}}|{Part g|{a|b|c}}}}}}"];

  edge [color=red];
  p1:b -> p2:j;
}
    */

     /*
    public String makeDotNode() {
	String 
	TypeDescriptor typeDesc = td.getType();
	if( typeDesc.isClass() ) {
	    ClassDescriptor classDesc = typeDesc.getClassDesc();
	    Iterator fieldItr = classDesc.getFields();
	    while( fieldItr.hasNext() ) {
		FieldDescriptor fd = (FieldDescriptor)it.next();
	    }
	} else if( typeDesc.isArray() ) {
	    // deal with arrays
	} else {
	    
	}
	return toString();
    }
     */


    public int numInEdges() {
	return inEdges.size();
    }

    public OwnershipNode getInEdge(int i) {
	return (OwnershipNode) inEdges.get(i);
    }
    
    public void addInEdge(OwnershipNode n) {
	inEdges.add(n);
    }

    public int numOutEdges() {
	return outEdges.size();
    }

    public OwnershipNode getOutEdge(int i) {
	return (OwnershipNode) outEdges.get(i);
    }
    
    public void addOutEdge(OwnershipNode n) {
	outEdges.add(n);
    }
}
