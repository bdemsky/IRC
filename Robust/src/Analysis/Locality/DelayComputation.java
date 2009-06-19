package Analysis.Locality;
import IR.State;
import IR.MethodDescriptor;
import IR.TypeDescriptor;
import IR.FieldDescriptor;
import IR.Flat.*;
import Analysis.Loops.GlobalFieldType;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Stack;
import java.util.Iterator;

public class DelayComputation {
  State state;
  LocalityAnalysis locality;
  TypeAnalysis typeanalysis;
  GlobalFieldType gft;
  DiscoverConflicts dcopts;
  Hashtable<LocalityBinding, HashSet<FlatNode>> notreadymap;
  Hashtable<LocalityBinding, HashSet<FlatNode>> cannotdelaymap;
  Hashtable<LocalityBinding, HashSet<FlatNode>> othermap;

  public DelayComputation(LocalityAnalysis locality, State state, TypeAnalysis typeanalysis, GlobalFieldType gft) {
    this.locality=locality;
    this.state=state;
    this.typeanalysis=typeanalysis;
    this.gft=gft;
    this.notreadymap=new Hashtable<LocalityBinding, HashSet<FlatNode>>();
    this.cannotdelaymap=new Hashtable<LocalityBinding, HashSet<FlatNode>>();
    this.othermap=new Hashtable<LocalityBinding, HashSet<FlatNode>>();
  }

  public DiscoverConflicts getConflicts() {
    return dcopts;
  }

  public void doAnalysis() {
    Set<LocalityBinding> localityset=locality.getLocalityBindings();
    for(Iterator<LocalityBinding> lb=localityset.iterator();lb.hasNext();) {
      analyzeMethod(lb.next());
    }
  }

  public HashSet<FlatNode> getNotReady(LocalityBinding lb) {
    return notreadymap.get(lb);
  }

  public HashSet<FlatNode> getCannotDelay(LocalityBinding lb) {
    return cannotdelaymap.get(lb);
  }

  public HashSet<FlatNode> getOther(LocalityBinding lb) {
    return othermap.get(lb);
  }

  //This method computes which nodes from the first part of the
  //transaction must store their output for the second part
  //Note that many nodes don't need to...

  public Set<FlatNode> livecode(LocalityBinding lb) {
    if (!othermap.containsKey(lb))
      return null;
    HashSet<FlatNode> delayedset=notreadymap.get(lb);
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Hashtable<TempDescriptor, HashSet<FlatNode>>> map=new Hashtable<FlatNode, Hashtable<TempDescriptor, HashSet<FlatNode>>>();

    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    toanalyze.add(fm);
    
    HashSet<FlatNode> livenodes=new HashSet<FlatNode>();

    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      toanalyze.remove(fn);
      Hashtable<TempDescriptor, HashSet<FlatNode>> tmptofn=new Hashtable<TempDescriptor, HashSet<FlatNode>>();
      
      //Do merge on incoming edges
      for(int i=0;i<fn.numPrev();i++) {
	FlatNode fnprev=fn.getPrev(i);
	Hashtable<TempDescriptor, HashSet<FlatNode>> prevmap=map.get(fnprev);

	for(Iterator<TempDescriptor> tmpit=prevmap.keySet().iterator();tmpit.hasNext();) {
	  TempDescriptor tmp=tmpit.next();
	  if (!tmptofn.containsKey(tmp))
	    tmptofn.put(tmp, new HashSet<FlatNode>());
	  tmptofn.get(tmp).addAll(prevmap.get(tmp));
	}
      }

      if (delayedset.contains(fn)) {
	//Check our readset
	TempDescriptor readset[]=fn.readsTemps();
	for(int i=0;i<readset.length;i++) {
	  TempDescriptor tmp=readset[i];
	  if (tmptofn.containsKey(tmp))
	    livenodes.addAll(tmptofn.get(tmp)); // add live nodes
	}

	//Do kills
	TempDescriptor writeset[]=fn.writesTemps();
	for(int i=0;i<writeset.length;i++) {
	  TempDescriptor tmp=writeset[i];
	  tmptofn.remove(tmp);
	}
      } else {
	//We write -- our reads are done
	TempDescriptor writeset[]=fn.writesTemps();
	for(int i=0;i<writeset.length;i++) {
	  TempDescriptor tmp=writeset[i];
	  HashSet<FlatNode> set=new HashSet<FlatNode>();
	  set.add(fn);
	  tmptofn.put(tmp,set);
	}
	if (fn.numNext()>1) {
	  //We have a conditional branch...need to handle this carefully
	  Set<FlatNode> set0=getNext(fn, 0, delayedset);
	  Set<FlatNode> set1=getNext(fn, 1, delayedset);
	  if (!set0.equals(set1)||set0.size()>1) {
	    //This branch is important--need to remember how it goes
	    livenodes.add(fn);
	  }
	}
      }
      if (!map.containsKey(fn)||!map.get(fn).equals(tmptofn)) {
	map.put(fn, tmptofn);
	//enqueue next ndoes
	for(int i=0;i<fn.numNext();i++)
	  toanalyze.add(fn.getNext(i));
      }
    }
    return livenodes;
  }
  
  //Returns null if more than one possible next

  public static Set<FlatNode> getNext(FlatNode fn, int i, HashSet<FlatNode> delayset) {
    FlatNode fnnext=fn.getNext(i);
    HashSet<FlatNode> reachable=new HashSet<FlatNode>();    

    if (delayset.contains(fnnext)) {
      reachable.add(fnnext);
      return reachable;
    }
    Stack<FlatNode> nodes=new Stack<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    nodes.push(fnnext);

    while(!nodes.isEmpty()) {
      FlatNode fn2=nodes.pop();
      if (visited.contains(fn2)) 
	continue;
      visited.add(fn2);
      for (int j=0;j<fn2.numNext();j++) {
	FlatNode fn2next=fn2.getNext(j);
	if (delayset.contains(fn2next)) {
	  reachable.add(fn2next);
	} else
	  nodes.push(fn2next);
      }
    }
    return reachable;
  }

  public void analyzeMethod(LocalityBinding lb) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    System.out.println("Analyzing "+md);
    HashSet<FlatNode> cannotdelay=new HashSet<FlatNode>();
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
    if (lb.isAtomic()) {
      //We are in a transaction already...
      //skip past this method or something
      return;
    }

    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    toanalyze.addAll(fm.getNodeSet());

    //Build the hashtables
    Hashtable<FlatNode, HashSet<TempDescriptor>> nodelaytemps=new Hashtable<FlatNode, HashSet<TempDescriptor>>();
    Hashtable<FlatNode, HashSet<FieldDescriptor>> nodelayfieldswr=new Hashtable<FlatNode, HashSet<FieldDescriptor>>();
    Hashtable<FlatNode, HashSet<TypeDescriptor>> nodelayarrayswr=new Hashtable<FlatNode, HashSet<TypeDescriptor>>();
    Hashtable<FlatNode, HashSet<FieldDescriptor>> nodelayfieldsrd=new Hashtable<FlatNode, HashSet<FieldDescriptor>>();
    Hashtable<FlatNode, HashSet<TypeDescriptor>> nodelayarraysrd=new Hashtable<FlatNode, HashSet<TypeDescriptor>>();
    
    //Effect of adding something to nodelay set is to move it up past everything in delay set
    //Have to make sure we can do this commute

    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      toanalyze.remove(fn);
      
      boolean isatomic=atomictable.get(fn).intValue()>0;

      if (!isatomic)
	continue;
      boolean isnodelay=false;

      /* Compute incoming nodelay sets */
      HashSet<TempDescriptor> nodelaytempset=new HashSet<TempDescriptor>();
      HashSet<FieldDescriptor> nodelayfieldwrset=new HashSet<FieldDescriptor>();
      HashSet<TypeDescriptor> nodelayarraywrset=new HashSet<TypeDescriptor>();
      HashSet<FieldDescriptor> nodelayfieldrdset=new HashSet<FieldDescriptor>();
      HashSet<TypeDescriptor> nodelayarrayrdset=new HashSet<TypeDescriptor>();
      for(int i=0;i<fn.numNext();i++) {
	if (nodelaytemps.containsKey(fn.getNext(i)))
	  nodelaytempset.addAll(nodelaytemps.get(fn.getNext(i)));
	//do field/array write sets
	if (nodelayfieldswr.containsKey(fn.getNext(i)))
	  nodelayfieldwrset.addAll(nodelayfieldswr.get(fn.getNext(i)));	  
	if (nodelayarrayswr.containsKey(fn.getNext(i)))
	  nodelayarraywrset.addAll(nodelayarrayswr.get(fn.getNext(i)));	  
	//do read sets
	if (nodelayfieldsrd.containsKey(fn.getNext(i)))
	  nodelayfieldrdset.addAll(nodelayfieldsrd.get(fn.getNext(i)));	  
	if (nodelayarrayswr.containsKey(fn.getNext(i)))
	  nodelayarraywrset.addAll(nodelayarrayswr.get(fn.getNext(i)));	  
      }
      
      /* Check our temp write set */

      TempDescriptor writeset[]=fn.writesTemps();
      for(int i=0;i<writeset.length;i++) {
	TempDescriptor tmp=writeset[i];
	if (nodelaytempset.contains(tmp)) {
	  //We are writing to a nodelay temp
	  //Therefore we are nodelay
	  isnodelay=true;
	  //Kill temp we wrote to
	  nodelaytempset.remove(tmp);
	}
      }
      
      //See if flatnode is definitely no delay
      if (fn.kind()==FKind.FlatCall) {
	isnodelay=true;
	//Have to deal with fields/arrays
	FlatCall fcall=(FlatCall)fn;
	MethodDescriptor mdcall=fcall.getMethod();
	nodelayfieldwrset.addAll(gft.getFieldsAll(mdcall));
	nodelayarraywrset.addAll(typeanalysis.expandSet(gft.getArraysAll(mdcall)));
	//Have to deal with field/array reads
	nodelayfieldrdset.addAll(gft.getFieldsRdAll(mdcall));
	nodelayarrayrdset.addAll(typeanalysis.expandSet(gft.getArraysRdAll(mdcall)));
      }
      
      // Can't delay branches
      if (fn.kind()==FKind.FlatCondBranch) {
	isnodelay=true;
      }

      //Check for field conflicts
      if (fn.kind()==FKind.FlatSetFieldNode) {
	FieldDescriptor fd=((FlatSetFieldNode)fn).getField();
	//write conflicts
	if (nodelayfieldwrset.contains(fd))
	  isnodelay=true;
	//read 
	if (nodelayfieldrdset.contains(fd))
	  isnodelay=true;
      }

      if (fn.kind()==FKind.FlatFieldNode) {
	FieldDescriptor fd=((FlatFieldNode)fn).getField();
	//write conflicts
	if (nodelayfieldwrset.contains(fd))
	  isnodelay=true;
      }

      //Check for array conflicts
      if (fn.kind()==FKind.FlatSetElementNode) {
	TypeDescriptor td=((FlatSetElementNode)fn).getDst().getType();
	//check for write conflicts
	if (nodelayarraywrset.contains(td))
	  isnodelay=true;
	//check for read conflicts
	if (nodelayarrayrdset.contains(td))
	  isnodelay=true;
      }
      if (fn.kind()==FKind.FlatElementNode) {
	TypeDescriptor td=((FlatElementNode)fn).getSrc().getType();
	//check for write conflicts
	if (nodelayarraywrset.contains(td))
	  isnodelay=true;
      }
      
      //If we are no delay, then the temps we read are no delay
      if (isnodelay) {
	/* Add our read set */
	TempDescriptor readset[]=fn.readsTemps();
	for(int i=0;i<readset.length;i++) {
	  TempDescriptor tmp=readset[i];
	  nodelaytempset.add(tmp);
	}
	cannotdelay.add(fn);

	/* Do we write to fields */
	if (fn.kind()==FKind.FlatSetFieldNode) {
	  nodelayfieldwrset.add(((FlatSetFieldNode)fn).getField());
	}
	/* Do we read from fields */
	if (fn.kind()==FKind.FlatFieldNode) {
	  nodelayfieldrdset.add(((FlatFieldNode)fn).getField());
	}

	/* Do we write to arrays */
	if (fn.kind()==FKind.FlatSetElementNode) {
	  //have to do expansion
	  nodelayarraywrset.addAll(typeanalysis.expand(((FlatSetElementNode)fn).getDst().getType()));	  
	}
	/* Do we read from arrays */
	if (fn.kind()==FKind.FlatElementNode) {
	  //have to do expansion
	  nodelayarrayrdset.addAll(typeanalysis.expand(((FlatElementNode)fn).getSrc().getType()));	  
	}
      } else {
	//Need to know which objects to lock on
	switch(fn.kind()) {
	case FKind.FlatSetFieldNode: {
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  nodelaytempset.add(fsfn.getDst());
	  break;
	}
	case FKind.FlatSetElementNode: {
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  nodelaytempset.add(fsen.getDst());
	  break;
	}
	case FKind.FlatFieldNode: {
	  FlatFieldNode ffn=(FlatFieldNode)fn;
	  nodelaytempset.add(ffn.getSrc());
	  break;
	}
	case FKind.FlatElementNode: {
	  FlatElementNode fen=(FlatElementNode)fn;
	  nodelaytempset.add(fen.getSrc());
	  break;
	}
	}
      }
      
      boolean changed=false;
      //See if we need to propagate changes
      if (!nodelaytemps.containsKey(fn)||
	  !nodelaytemps.get(fn).equals(nodelaytempset)) {
	nodelaytemps.put(fn, nodelaytempset);
	changed=true;
      }

      //See if we need to propagate changes
      if (!nodelayfieldswr.containsKey(fn)||
	  !nodelayfieldswr.get(fn).equals(nodelayfieldwrset)) {
	nodelayfieldswr.put(fn, nodelayfieldwrset);
	changed=true;
      }

      //See if we need to propagate changes
      if (!nodelayfieldsrd.containsKey(fn)||
	  !nodelayfieldsrd.get(fn).equals(nodelayfieldrdset)) {
	nodelayfieldsrd.put(fn, nodelayfieldrdset);
	changed=true;
      }

      //See if we need to propagate changes
      if (!nodelayarrayswr.containsKey(fn)||
	  !nodelayarrayswr.get(fn).equals(nodelayarraywrset)) {
	nodelayarrayswr.put(fn, nodelayarraywrset);
	changed=true;
      }

      //See if we need to propagate changes
      if (!nodelayarraysrd.containsKey(fn)||
	  !nodelayarraysrd.get(fn).equals(nodelayarrayrdset)) {
	nodelayarraysrd.put(fn, nodelayarrayrdset);
	changed=true;
      }

      if (changed)
	for(int i=0;i<fn.numPrev();i++)
	  toanalyze.add(fn.getPrev(i));
    }//end of while loop
    HashSet<FlatNode> notreadyset=computeNotReadySet(lb, cannotdelay);
    HashSet<FlatNode> otherset=new HashSet<FlatNode>();
    otherset.addAll(fm.getNodeSet());
    if (lb.getHasAtomic()) {
      otherset.removeAll(notreadyset);
      otherset.removeAll(cannotdelay);
      notreadymap.put(lb, notreadyset);
      cannotdelaymap.put(lb, cannotdelay);
      othermap.put(lb, otherset);
    }

    //We now have:
    //(1) Cannot delay set -- stuff that must be done before commit
    //(2) Not ready set -- stuff that must wait until commit
    //(3) everything else -- stuff that should be done before commit
  } //end of method

  //Problems:
  //1) we acquire locks too early to object we don't need to yet
  //2) we don't realize that certain operations have side effects

  public HashSet<FlatNode> computeNotReadySet(LocalityBinding lb, HashSet<FlatNode> cannotdelay) {
    //You are in not ready set if:
    //I. You read a not ready temp
    //II. You access a field or element and
    //(A). You are not in the cannot delay set
    //(B). You read a field/element in the transactional set
    //(C). The source didn't have a transactional read on it

    dcopts=new DiscoverConflicts(locality, state, typeanalysis);
    dcopts.doAnalysis();
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);

    HashSet<FlatNode> notreadynodes=new HashSet<FlatNode>();
    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    toanalyze.addAll(fm.getNodeSet());
    Hashtable<FlatNode, HashSet<TempDescriptor>> notreadymap=new Hashtable<FlatNode, HashSet<TempDescriptor>>();
    
    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      toanalyze.remove(fn);
      boolean isatomic=atomictable.get(fn).intValue()>0;

      if (!isatomic)
	continue;

      //Compute initial notready set
      HashSet<TempDescriptor> notreadyset=new HashSet<TempDescriptor>();
      for(int i=0;i<fn.numPrev();i++) {
	if (notreadymap.containsKey(fn.getPrev(i)))
	  notreadyset.addAll(notreadymap.get(fn.getPrev(i)));
      }
      
      //Are we ready
      boolean notready=false;

      //Test our read set first
      TempDescriptor readset[]=fn.readsTemps();
      for(int i=0;i<readset.length;i++) {
	TempDescriptor tmp=readset[i];
	if (notreadyset.contains(tmp)) {
	  notready=true;
	  break;
	}
      }

      if (!notready&&!cannotdelay.contains(fn)) {
	switch(fn.kind()) {
	case FKind.FlatFieldNode: {
	  FlatFieldNode ffn=(FlatFieldNode)fn;
	  if (!dcopts.getFields().contains(ffn.getField())) {
	    break;
	  }
	  TempDescriptor tmp=ffn.getSrc();
	  Set<TempFlatPair> tfpset=dcopts.getMap(lb).get(fn).get(tmp);
	  if (tfpset!=null) {
	    for(Iterator<TempFlatPair> tfpit=tfpset.iterator();tfpit.hasNext();) {
	      TempFlatPair tfp=tfpit.next();
	      if (!dcopts.getNeedSrcTrans(lb, tfp.f)) {
		//if a source didn't need a translation and we are
		//accessing it, it did...so therefore we are note
		//ready
		notready=true;
		break;
	      }
	    }
	  }
	  break;
	}
	case FKind.FlatSetFieldNode: {
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  TempDescriptor tmp=fsfn.getDst();
	  Hashtable<TempDescriptor, Set<TempFlatPair>> tmpmap=dcopts.getMap(lb).get(fn);
	  Set<TempFlatPair> tfpset=tmpmap!=null?tmpmap.get(tmp):null;

	  if (tfpset!=null) {
	    for(Iterator<TempFlatPair> tfpit=tfpset.iterator();tfpit.hasNext();) {
	      TempFlatPair tfp=tfpit.next();
	      if (!dcopts.getNeedSrcTrans(lb, tfp.f)) {
		//if a source didn't need a translation and we are
		//accessing it, it did...so therefore we are note
		//ready
		notready=true;
		break;
	      }
	    }
	  }
	  break;
	}
	case FKind.FlatElementNode: {
	  FlatElementNode fen=(FlatElementNode)fn;
	  if (!dcopts.getArrays().contains(fen.getSrc().getType())) {
	    break;
	  }
	  TempDescriptor tmp=fen.getSrc();
	  Set<TempFlatPair> tfpset=dcopts.getMap(lb).get(fn).get(tmp);
	  if (tfpset!=null) {
	    for(Iterator<TempFlatPair> tfpit=tfpset.iterator();tfpit.hasNext();) {
	      TempFlatPair tfp=tfpit.next();
	      if (!dcopts.getNeedSrcTrans(lb, tfp.f)) {
		//if a source didn't need a translation and we are
		//accessing it, it did...so therefore we are note
		//ready
		notready=true;
		break;
	      }
	    }
	  }
	  break;
	}
	case FKind.FlatSetElementNode: {
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  TempDescriptor tmp=fsen.getDst();
	  Set<TempFlatPair> tfpset=dcopts.getMap(lb).get(fn).get(tmp);
	  if (tfpset!=null) {
	    for(Iterator<TempFlatPair> tfpit=tfpset.iterator();tfpit.hasNext();) {
	      TempFlatPair tfp=tfpit.next();
	      if (!dcopts.getNeedSrcTrans(lb, tfp.f)) {
		//if a source didn't need a translation and we are
		//accessing it, it did...so therefore we are note
		//ready
		notready=true;
		break;
	      }
	    }
	  }
	  break;
	}
	}
      }

      //Fix up things based on our status
      if (notready) {
	//add us to the list
	notreadynodes.add(fn);
	//Add our writes
	TempDescriptor writeset[]=fn.writesTemps();
	for(int i=0;i<writeset.length;i++) {
	  TempDescriptor tmp=writeset[i];
	  notreadyset.add(tmp);
	}
      } else {
	//Kill our writes
	TempDescriptor writeset[]=fn.writesTemps();
	for(int i=0;i<writeset.length;i++) {
	  TempDescriptor tmp=writeset[i];
	  notreadyset.remove(tmp);
	}
      }
      
      //See if we need to propagate changes
      if (!notreadymap.containsKey(fn)||
	  !notreadymap.get(fn).equals(notreadyset)) {
	notreadymap.put(fn, notreadyset);
	for(int i=0;i<fn.numNext();i++)
	  toanalyze.add(fn.getNext(i));
      }
    } //end of while
    return notreadynodes;
  } //end of computeNotReadySet
} //end of class
