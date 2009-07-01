package Analysis.Locality;
import Analysis.Liveness;
import Analysis.ReachingDefs;
import Analysis.Loops.DomTree;
import IR.State;
import IR.MethodDescriptor;
import IR.TypeDescriptor;
import IR.FieldDescriptor;
import IR.Flat.*;
import Analysis.Loops.GlobalFieldType;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
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
    for(Iterator<LocalityBinding> lbit=localityset.iterator();lbit.hasNext();) {
      analyzeMethod(lbit.next());
    }

    dcopts=new DiscoverConflicts(locality, state, typeanalysis, cannotdelaymap);
    dcopts.doAnalysis();


    for(Iterator<LocalityBinding> lbit=localityset.iterator();lbit.hasNext();) {
      LocalityBinding lb=lbit.next();

      MethodDescriptor md=lb.getMethod();
      FlatMethod fm=state.getMethodFlat(md);
      if (lb.isAtomic())
	continue;
      
      if (lb.getHasAtomic()) {
	HashSet<FlatNode> cannotdelay=cannotdelaymap.get(lb);
	HashSet<FlatNode> notreadyset=computeNotReadySet(lb, cannotdelay);
	HashSet<FlatNode> otherset=new HashSet<FlatNode>();
	otherset.addAll(fm.getNodeSet());
	otherset.removeAll(notreadyset);
	otherset.removeAll(cannotdelay);
	if (state.MINIMIZE) {
	  notreadyset.addAll(otherset);
	  otherset=new HashSet<FlatNode>();
	}

	notreadymap.put(lb, notreadyset);
	othermap.put(lb, otherset);
      }
      
      //We now have:
      //(1) Cannot delay set -- stuff that must be done before commit
      //(2) Not ready set -- stuff that must wait until commit
      //(3) everything else -- stuff that should be done before commit
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

  //This method computes which temps are live into the second part
  public Set<TempDescriptor> alltemps(LocalityBinding lb, FlatAtomicEnterNode faen, Set<FlatNode> recordset) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Set<FlatNode> atomicnodes=faen.getReachableSet(faen.getExits());

    //Compute second part set of nodes
    Set<FlatNode> secondpart=new HashSet<FlatNode>();
    secondpart.addAll(getNotReady(lb));
    secondpart.addAll(recordset);

    //make it just this transaction
    secondpart.retainAll(atomicnodes);
    
    HashSet<TempDescriptor> tempset=new HashSet<TempDescriptor>();
    
    for(Iterator<FlatNode> fnit=secondpart.iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      List<TempDescriptor> writes=Arrays.asList(fn.writesTemps());
      tempset.addAll(writes);
      if (!recordset.contains(fn)) {
	List<TempDescriptor> reads=Arrays.asList(fn.readsTemps());
	tempset.addAll(reads);
      }
    }
    
    return tempset;
  }

  //This method computes which temps are live into the second part
  public Set<TempDescriptor> liveinto(LocalityBinding lb, FlatAtomicEnterNode faen, Set<FlatNode> recordset) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Set<FlatNode> atomicnodes=faen.getReachableSet(faen.getExits());

    //Compute second part set of nodes
    Set<FlatNode> secondpart=new HashSet<FlatNode>();
    secondpart.addAll(getNotReady(lb));
    secondpart.addAll(recordset);

    //make it just this transaction
    secondpart.retainAll(atomicnodes);

    Set<TempDescriptor> liveinto=new HashSet<TempDescriptor>();
    
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> reachingdefs=ReachingDefs.computeReachingDefs(fm, Liveness.computeLiveTemps(fm), true);
    
    for(Iterator<FlatNode> fnit=secondpart.iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      if (recordset.contains(fn))
	continue;
      TempDescriptor readset[]=fn.readsTemps();
      for(int i=0;i<readset.length;i++) {
	TempDescriptor rtmp=readset[i];
	Set<FlatNode> fnset=reachingdefs.get(fn).get(rtmp);
	for(Iterator<FlatNode> fnit2=fnset.iterator();fnit2.hasNext();) {
	  FlatNode fn2=fnit2.next();
	  if (secondpart.contains(fn2))
	    continue;
	  //otherwise we mark this as live in
	  liveinto.add(rtmp);
	  break;
	}
      }
    }
    return liveinto;
  }

  //This method computes which temps are live out of the second part 
  public Set<TempDescriptor> liveoutvirtualread(LocalityBinding lb, FlatAtomicEnterNode faen) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Set<FlatNode> exits=faen.getExits();
    Hashtable<FlatNode, Set<TempDescriptor>> livemap=Liveness.computeLiveTemps(fm);
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> reachingdefs=ReachingDefs.computeReachingDefs(fm, Liveness.computeLiveTemps(fm), true);

    Set<FlatNode> atomicnodes=faen.getReachableSet(faen.getExits());

    Set<FlatNode> secondpart=new HashSet<FlatNode>(getNotReady(lb));
    secondpart.retainAll(atomicnodes);

    Set<TempDescriptor> liveset=new HashSet<TempDescriptor>();
    //Have list of all live temps

    for(Iterator<FlatNode> fnit=exits.iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      Set<TempDescriptor> tempset=livemap.get(fn);
      Hashtable<TempDescriptor, Set<FlatNode>> reachmap=reachingdefs.get(fn);
      //Look for reaching defs for all live variables that are in the secondpart

      for(Iterator<TempDescriptor> tmpit=tempset.iterator();tmpit.hasNext();) {
	TempDescriptor tmp=tmpit.next();
	Set<FlatNode> fnset=reachmap.get(tmp);
	boolean outsidenode=false;
	boolean insidenode=false;

	for(Iterator<FlatNode> fnit2=fnset.iterator();fnit2.hasNext();) {
	  FlatNode fn2=fnit2.next();
	  if (secondpart.contains(fn2)) {
	    insidenode=true;
	  } else if (!atomicnodes.contains(fn2)) {
	    outsidenode=true;
	  }
	  if (outsidenode&&insidenode) {
	    liveset.add(tmp);
	    break;
	  }
	}
      }
    }
    return liveset;
  }

  //This method computes which temps are live out of the second part
  public Set<TempDescriptor> liveout(LocalityBinding lb, FlatAtomicEnterNode faen) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Set<FlatNode> exits=faen.getExits();
    Hashtable<FlatNode, Set<TempDescriptor>> livemap=Liveness.computeLiveTemps(fm);
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> reachingdefs=ReachingDefs.computeReachingDefs(fm, livemap, true);
    
    Set<FlatNode> atomicnodes=faen.getReachableSet(faen.getExits());

    Set<FlatNode> secondpart=new HashSet<FlatNode>(getNotReady(lb));
    secondpart.retainAll(atomicnodes);

    Set<TempDescriptor> liveset=new HashSet<TempDescriptor>();
    //Have list of all live temps

    for(Iterator<FlatNode> fnit=exits.iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      Set<TempDescriptor> tempset=livemap.get(fn);
      Hashtable<TempDescriptor, Set<FlatNode>> reachmap=reachingdefs.get(fn);
      //Look for reaching defs for all live variables that are in the secondpart

      for(Iterator<TempDescriptor> tmpit=tempset.iterator();tmpit.hasNext();) {
	TempDescriptor tmp=tmpit.next();
	Set<FlatNode> fnset=reachmap.get(tmp);
	if (fnset==null) {
	  System.out.println("null temp set for"+fn+" tmp="+tmp);
	  System.out.println(fm.printMethod());
	}
	for(Iterator<FlatNode> fnit2=fnset.iterator();fnit2.hasNext();) {
	  FlatNode fn2=fnit2.next();
	  if (secondpart.contains(fn2)) {
	    liveset.add(tmp);
	    break;
	  }
	}
      }
    }
    return liveset;
  }

  //This method computes which nodes from the first part of the
  //transaction must store their output for the second part
  //Note that many nodes don't need to...

  public Set<FlatNode> livecode(LocalityBinding lb) {
    if (!othermap.containsKey(lb))
      return null;
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);

    HashSet<FlatNode> delayedset=notreadymap.get(lb);
    HashSet<FlatNode> otherset=othermap.get(lb);
    HashSet<FlatNode> cannotdelayset=cannotdelaymap.get(lb);
    Hashtable<FlatNode,Set<TempDescriptor>> livemap=Liveness.computeLiveTemps(fm);
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> reachingdefsmap=ReachingDefs.computeReachingDefs(fm, livemap, true);
    HashSet<FlatNode> unionset=new HashSet<FlatNode>(delayedset);
    Hashtable<FlatNode, Hashtable<TempDescriptor, HashSet<FlatNode>>> map=new Hashtable<FlatNode, Hashtable<TempDescriptor, HashSet<FlatNode>>>();
    HashSet<FlatNode> livenodes=new HashSet<FlatNode>();
    Hashtable<FlatCondBranch, Set<FlatNode>> branchmap=revGetBranchSet(lb);

    //Here we check for temps that are live out on the transaction...
    //If both parts can contribute to the temp, then we need to do
    //reads to make sure that liveout set has the right values

    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      if (fn.kind()==FKind.FlatAtomicExitNode) {
	Set<TempDescriptor> livetemps=livemap.get(fn);
	Hashtable<TempDescriptor, Set<FlatNode>> tempmap=reachingdefsmap.get(fn);

	//Iterate over the temps that are live into this node
	for(Iterator<TempDescriptor> tmpit=livetemps.iterator();tmpit.hasNext();) {
	  TempDescriptor tmp=tmpit.next();
	  Set<FlatNode> fnset=tempmap.get(tmp);
	  boolean inpart1=false;
	  boolean inpart2=false;

	  //iterate over the reaching definitions for the temp
	  for(Iterator<FlatNode> fnit2=fnset.iterator();fnit2.hasNext();) {
	    FlatNode fn2=fnit2.next();
	    if (delayedset.contains(fn2)) {
	      inpart2=true;
	      if (inpart1)
		break;
	    } else if (otherset.contains(fn2)||cannotdelayset.contains(fn2)) {
	      inpart1=true;
	      if (inpart2)
		break;
	    }
	  }
	  if (inpart1&&inpart2) {
	    for(Iterator<FlatNode> fnit2=fnset.iterator();fnit2.hasNext();) {
	      FlatNode fn2=fnit2.next();
	      if (otherset.contains(fn2)||cannotdelayset.contains(fn2)) {
		unionset.add(fn2);
		System.out.println("ADDA"+fn2);
		livenodes.add(fn2);
	      }
	    }
	  }
	}
      }
    }

    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    toanalyze.add(fm);

    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      toanalyze.remove(fn);
      Hashtable<TempDescriptor, HashSet<FlatNode>> tmptofn=new Hashtable<TempDescriptor, HashSet<FlatNode>>();

      //Don't process non-atomic nodes
      if (locality.getAtomic(lb).get(fn).intValue()==0) {
	if (!map.containsKey(fn)) {
	  map.put(fn, new Hashtable<TempDescriptor, HashSet<FlatNode>>());
	  //enqueue next nodes
	  for(int i=0;i<fn.numNext();i++)
	    toanalyze.add(fn.getNext(i));
	}
	continue;
      }

      //Do merge on incoming edges
      for(int i=0;i<fn.numPrev();i++) {
	FlatNode fnprev=fn.getPrev(i);
	Hashtable<TempDescriptor, HashSet<FlatNode>> prevmap=map.get(fnprev);
	if (prevmap!=null)
	  for(Iterator<TempDescriptor> tmpit=prevmap.keySet().iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    if (!tmptofn.containsKey(tmp))
	      tmptofn.put(tmp, new HashSet<FlatNode>());
	    tmptofn.get(tmp).addAll(prevmap.get(tmp));
	  }
      }

      if (delayedset.contains(fn)) {
	//If the node is in the second set, check our readset
	TempDescriptor readset[]=fn.readsTemps();
	for(int i=0;i<readset.length;i++) {
	  TempDescriptor tmp=readset[i];
	  if (tmptofn.containsKey(tmp)) {
	    System.out.println("ADDB"+tmptofn.get(tmp));
	    livenodes.addAll(tmptofn.get(tmp)); //Add live nodes
	    unionset.addAll(tmptofn.get(tmp));
	  }
	}
	
	//Do kills
	TempDescriptor writeset[]=fn.writesTemps();
	for(int i=0;i<writeset.length;i++) {
	  TempDescriptor tmp=writeset[i];
	  tmptofn.remove(tmp);
	}
      } else {
	//If the node is in the first set, search over what we write
	//We write -- our reads are done
	TempDescriptor writeset[]=fn.writesTemps();
	for(int i=0;i<writeset.length;i++) {
	  TempDescriptor tmp=writeset[i];
	  HashSet<FlatNode> set=new HashSet<FlatNode>();
	  tmptofn.put(tmp,set);
	  set.add(fn);
	}
	if (fn.numNext()>1) {
	  Set<FlatNode> branchset=branchmap.get((FlatCondBranch)fn);
	  for(Iterator<FlatNode> brit=branchset.iterator();brit.hasNext();) {
	    FlatNode brfn=brit.next();
	    if (unionset.contains(brfn)) {
	      //This branch is important--need to remember how it goes
	      System.out.println("ADDC"+fn);
	      livenodes.add(fn);
	      unionset.add(fn);	      
	    }
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

  public static Set<FlatNode> getNext(FlatNode fn, int i, Set<FlatNode> delayset, LocalityBinding lb, LocalityAnalysis locality, boolean contpastnode) {
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
    FlatNode fnnext=fn.getNext(i);
    HashSet<FlatNode> reachable=new HashSet<FlatNode>();    

    if (delayset.contains(fnnext)||atomictable.get(fnnext).intValue()==0) {
      reachable.add(fnnext);
      return reachable;
    }
    Stack<FlatNode> nodes=new Stack<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    nodes.push(fnnext);
    if (contpastnode)
      visited.add(fn);

    while(!nodes.isEmpty()) {
      FlatNode fn2=nodes.pop();
      if (visited.contains(fn2))
	continue;
      visited.add(fn2);
      for (int j=0;j<fn2.numNext();j++) {
	FlatNode fn2next=fn2.getNext(j);
	if (delayset.contains(fn2next)||atomictable.get(fn2next).intValue()==0) {
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
    
    Hashtable<FlatNode, Set<FlatCondBranch>> branchmap=getBranchSet(lb);
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
	if (nodelayarraysrd.containsKey(fn.getNext(i)))
	  nodelayarrayrdset.addAll(nodelayarraysrd.get(fn.getNext(i)));	  
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
      
      //Delay branches if possible
      if (fn.kind()==FKind.FlatCondBranch) {
	Set<FlatNode> leftset=getNext(fn, 0, cannotdelay, lb, locality,true);
	Set<FlatNode> rightset=getNext(fn, 1, cannotdelay, lb, locality,true);
	if (leftset.size()>0&&rightset.size()>0&&
	    !leftset.equals(rightset)||leftset.size()>1)
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

	if (branchmap.containsKey(fn)) {
	  Set<FlatCondBranch> fcbset=branchmap.get(fn);
	  for(Iterator<FlatCondBranch> fcbit=fcbset.iterator();fcbit.hasNext();) {
	    FlatCondBranch fcb=fcbit.next();
	    cannotdelay.add(fcb);
	    nodelaytempset.add(fcb.getTest());
	  }
	}
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
	  //TODO: Can improve by only locking if there is a field that requires a lock
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

    if (lb.getHasAtomic()) {
      cannotdelaymap.put(lb, cannotdelay);
    }
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

    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
    HashSet<FlatNode> notreadynodes=new HashSet<FlatNode>();
    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    toanalyze.addAll(fm.getNodeSet());
    Hashtable<FlatNode, HashSet<TempDescriptor>> notreadymap=new Hashtable<FlatNode, HashSet<TempDescriptor>>();

    Hashtable<FlatCondBranch, Set<FlatNode>> revbranchmap=revGetBranchSet(lb);
    Hashtable<FlatNode, Set<FlatCondBranch>> branchmap=getBranchSet(lb);

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

      if (!notready) {
	//See if we depend on a conditional branch that is not ready
	Set<FlatCondBranch> branchset=branchmap.get(fn);
	if (branchset!=null)
	  for(Iterator<FlatCondBranch> branchit=branchset.iterator();branchit.hasNext();) {
	    FlatCondBranch fcb=branchit.next();
	    if (notreadynodes.contains(fcb)) {
	      //if we depend on a branch that isn't ready, we aren't ready
	      notready=true;
	      break;
	    }
	  }
      }


      //Fix up things based on our status
      if (notready) {
	if (fn.kind()==FKind.FlatCondBranch&&!notreadynodes.contains(fn)) {
	  //enqueue everything in our dependence set
	  Set<FlatNode> branchdepset=revbranchmap.get((FlatCondBranch)fn);
	  toanalyze.addAll(branchdepset);
	}

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

  public Hashtable<FlatCondBranch, Set<FlatNode>> revGetBranchSet(LocalityBinding lb) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatCondBranch, Set<FlatNode>> condmap=new Hashtable<FlatCondBranch, Set<FlatNode>>();
    DomTree postdt=new DomTree(fm, true);
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      if (fn.kind()!=FKind.FlatCondBranch)
	continue;
      FlatCondBranch fcb=(FlatCondBranch)fn;
      //only worry about fcb inside of transactions
      if (locality.getAtomic(lb).get(fcb).intValue()==0)
	continue;
      FlatNode postdom=postdt.idom(fcb);

      //Reverse the mapping
      Set<FlatNode> fnset=computeBranchSet(lb, fcb, postdom);
      condmap.put(fcb, fnset);
    }
    return condmap;
  }

  public Hashtable<FlatNode, Set<FlatCondBranch>> getBranchSet(LocalityBinding lb) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Set<FlatCondBranch>> condmap=new Hashtable<FlatNode, Set<FlatCondBranch>>();
    DomTree postdt=new DomTree(fm, true);
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      if (fn.kind()!=FKind.FlatCondBranch)
	continue;
      FlatCondBranch fcb=(FlatCondBranch)fn;
      //only worry about fcb inside of transactions
      if (locality.getAtomic(lb).get(fcb).intValue()==0)
	continue;
      FlatNode postdom=postdt.idom(fcb);

      //Reverse the mapping
      Set<FlatNode> fnset=computeBranchSet(lb, fcb, postdom);
      for(Iterator<FlatNode>fnit2=fnset.iterator();fnit2.hasNext();) {
	FlatNode fn2=fnit2.next();
	if (!condmap.containsKey(fn2))
	  condmap.put(fn2,new HashSet<FlatCondBranch>());
	condmap.get(fn2).add(fcb);
      }
    }
    return condmap;
  }

  public Set<FlatNode> computeBranchSet(LocalityBinding lb, FlatNode first, FlatNode last) {
    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    toanalyze.add(first);

    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      toanalyze.remove(fn);

      //already examined or exit node
      if (visited.contains(fn)||fn==last)
	continue;

      //out of transaction
      if (locality.getAtomic(lb).get(fn).intValue()==0)
	continue;
      
      visited.add(fn);      
      for(int i=0;i<fn.numNext();i++) {
	FlatNode fnext=fn.getNext(i);
	toanalyze.add(fnext);
      }
    }
    return visited;
  } //end of computeBranchSet
} //end of class
