// LoopFinder.java, created Tue Jun 15 23:15:07 1999 by bdemsky
// Licensed under the terms of the GNU GPL; see COPYING for details.
// Copyright 1999 by Brian Demsky

package Analysis.Loops;

import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.Hashtable;
import java.util.Iterator;
/**
 * <code>LoopFinder</code> implements Dominator Tree Loop detection.
 *
 * @author  Brian Demsky <bdemsky@mit.edu>
 * @version $Id: LoopFinder.java,v 1.4 2011/04/27 20:34:22 bdemsky Exp $
 */

public class LoopFinder implements Loops {
  DomTree dominator;
  FlatMethod hc,lasthc;
  HashSet setofloops;
  Loop root;
  Loop ptr;


  /** Creates a new LoopFinder object.
   * This call takes an HCode and a CFGrapher
   * and returns a LoopFinder object
   * at the root level.
   */

  public LoopFinder(FlatMethod hc) {
    this.hc=hc;
    this.dominator=new DomTree(hc,false);
    analyze();
    this.ptr=root;
  }

  /**This method is for internal use only.
   *It returns a Loopfinder object at any level,
   *but it doesn't regenerate the internal tree
   *so any external calls would result in garbage.*/

  private LoopFinder(FlatMethod hc, DomTree dt, Loop root, Loop ptr) {
    this.lasthc=hc;
    this.hc=hc;
    this.dominator=dt;
    this.root=root;
    this.ptr=ptr;
  }

  /*-----------------------------*/

  /**  This method returns the Root level loop for a given <code>HCode</code>.
   *  Does the same thing as the constructor call, but for an existing
   *  LoopFinder object.*/

  public Loops getRootloop(FlatMethod hc) {
    this.hc=hc;
    analyze();
    return new LoopFinder(hc,dominator,root,root);
  }

  /**  This method returns the entry point of the loop.
   *   For the natural loops we consider, that is simply the header.
   *   It returns a <code>Set</code> of <code>HCodeElement</code>s.*/

  public Set loopEntrances() {
    HashSet entries=new HashSet();
    analyze();
    entries.add(ptr.header);
    return entries;
  }


  /**Returns a <code>Set</code> with all of the <code>HCodeElement</code>s of the loop and
   *loops included entirely within this loop. */

  public Set loopIncElements() {
    analyze();
    HashSet A=new HashSet(ptr.entries);
    return A;
  }

  /** Returns all of the <code>HCodeElement</code>s of this loop that aren't in a nested
   *  loop. This returns a <code>Set</code> of <code>HCodeElement</code>s.*/

  public Set loopExcElements() {
    analyze();
    HashSet A=new HashSet(ptr.entries);
    HashSet todo=new HashSet();
    //Get the children
    todo.addAll(ptr.children);

    //Go down the tree
    while(!todo.isEmpty()) {
      Loop currptr=(Loop)todo.iterator().next();
      todo.remove(currptr);
      todo.addAll(currptr.children);
      A.removeAll(currptr.entries);
    }
    return A;
  }

  /** Returns a <code>Set</code> of loops that are nested inside of this loop.*/

  public Set nestedLoops() {
    analyze();
    HashSet L=new HashSet();
    Iterator iterate=ptr.children.iterator();
    while (iterate.hasNext())
      L.add(new LoopFinder(hc,dominator,root,(Loop) iterate.next()));
    return L;
  }

  /** Returns the <code>Loops</code> that contains this loop.
   *  If this is the top level loop, this call returns a null pointer.*/

  public Loops parentLoop() {
    analyze();
    if (ptr.parent!=null)
      return new LoopFinder(hc,dominator,root,ptr.parent);
    else return null;
  }

  /*---------------------------*/
  // public information accessor methods.

  /*---------------------------*/
  // Analysis code.


  /** Main analysis method. */

  void analyze() {
    //Have we analyzed this set before?
    //If so, don't do it again!!!
    if (hc!=lasthc) {

      //Did the caller hand us a bogus object?
      //If so, throw it something

      lasthc=hc;

      //Set up the top level loop, so we can fill it with HCodeElements
      //as we go along
      root=new Loop();
      root.header=hc;

      //Set up a WorkSet for storing loops before we build the
      //nested loop tree
      setofloops=new HashSet();


      //Find loops
      findloopheaders(hc);

      //Build the nested loop tree
      buildtree();
    }
  }
  // end analysis.

  void buildtree() {
    //go through set of generated loops
    while(!setofloops.isEmpty()) {
      //Pull out one
      Loop A=(Loop) setofloops.iterator().next();
      setofloops.remove(A);

      //Add it to the tree, complain if oddness
      if (addnode(A, root)!=1)
	System.out.println("Evil Error in LoopFinder while building tree.");
    }
  }

  //Adds a node to the tree...Its recursive

  int addnode(Loop A, Loop treenode) {
    //Only need to go deeper if the header is contained in this loop
    if (treenode.entries.contains(A.header))

      //Do we share headers?
      if (treenode.header!=A.header) {

	//No...  Loop through our children to see if they want this
	//node.

	//Use integers for tri-state:
	//0=not stored here, 1=stored and everything is good
	//2=combined 2 natural loops with same header...need cleanup

	int stored=0;
	Iterator iterate=treenode.children.iterator();
	Loop temp=new Loop();
	while (iterate.hasNext()) {
	  temp=(Loop) iterate.next();
	  stored=addnode(A,temp);
	  if (stored!=0) break;
	}

	//See what our children did for us

	if (stored==0) {
	  //We get a new child...
	  treenode.children.add(A);
	  temp=A;
	}

	//Need to do cleanup for case 0 or 2
	//temp points to the new child

	if (stored!=1) {

	  //Have to make sure that none of the nodes under this one
	  //are children of the new node

	  Iterator iterate2=treenode.children.iterator();
	  temp.parent=treenode;

	  //Loop through the children
	  while (iterate2.hasNext()) {
	    Loop temp2=(Loop)iterate2.next();

	    //Don't look at the new node...otherwise we will create
	    //a unreachable subtree

	    if (temp2!=temp)
	      //If the new node has a childs header
	      //give the child up to it...

	      if (temp.entries.contains(temp2.header)) {
		temp.children.add(temp2);
		iterate2.remove();
	      }
	  }
	}

	//We fixed everything...let our parents know
	return 1;
      } else {
	//need to combine loops
	while (!A.entries.isEmpty()) {
	  FlatNode node=(FlatNode)A.entries.iterator().next();
	  A.entries.remove(node);
	  treenode.entries.add(node);
	}
	//let the previous caller know that they have stuff todo
	return 2;
      }
    //We aren't adopting the new node
    else return 0;
  }

  void findloopheaders(FlatNode current_nodeOrig) {
    Stack stk = new Stack();
    stk.push(current_nodeOrig);
    while( !stk.isEmpty() ) {
      FlatNode current_node = (FlatNode) stk.pop();
      //look at the current node
      visit(current_node);

      //add it to the all inclusive root loop
      root.entries.add(current_node);

      //See if those we dominate are backedges
      Set<FlatNode> children=dominator.children(current_node);

      if (children!=null) {
	for(Iterator<FlatNode> it=children.iterator(); it.hasNext(); ) {
	  FlatNode fn=it.next();
	  if (fn!=current_node)
	    stk.push(fn);
	}
      }
    }
  }

  void visit(FlatNode q) {
    Loop A=new Loop();
    HashSet B=new HashSet();

    //Loop through all of our outgoing edges
    for (int i=0; i<q.numNext(); i++) {
      FlatNode temp=q;
      FlatNode temp_to=q.getNext(i);

      //Go up the dominator tree until
      //we hit the root element or we
      //find the node we jump back too
      while ((temp!=hc)&&
             (temp_to!=temp)) {
	temp=dominator.idom(temp);
      }

      //If we found the node we jumped back to
      //then build loop

      if (temp_to==temp) {

	//found a loop
	A.entries.add(temp); //Push the header
	A.header=temp;
	B.add(q); //Put the backedge in the todo list

	//Starting with the backedge, work on the incoming edges
	//until we get back to the loop header...
	//Then we have the entire natural loop

	while(!B.isEmpty()) {
	  FlatNode newnode=(FlatNode)B.iterator().next();
	  B.remove(newnode);

	  //Add all of the new incoming edges that we haven't already
	  //visited
	  for (int j=0; j<newnode.numPrev(); j++) {
	    FlatNode from=newnode.getPrev(j);
	    if (!A.entries.contains(from))
	      B.add(from);
	  }

	  //push the new node on our list of nodes in the loop
	  A.entries.add(newnode);
	}

	//save our new loop
	setofloops.add(A);
      }
    }
  }

  //Structure for building internal trees...

  class Loop {
    public HashSet entries=new HashSet();
    public FlatNode header;
    //Elements of the WorkSet of children are
    //of the type Loop
    public HashSet children=new HashSet();
    public Loop parent;
  }
}
