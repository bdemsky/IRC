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
import java.util.Iterator;

public class DelayComputation {
  State state;
  LocalityAnalysis locality;
  TypeAnalysis typeanalysis;
  GlobalFieldType gft;
  DiscoverConflicts dcopts;

  public DelayComputation(LocalityAnalysis locality, State state, TypeAnalysis typeanalysis, GlobalFieldType gft) {
    this.locality=locality;
    this.state=state;
    this.typeanalysis=typeanalysis;
    this.gft=gft;
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
    HashSet<FlatNode> atomicset=new HashSet<FlatNode>();
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      boolean isatomic=atomictable.get(fn).intValue()>0;
      if (isatomic)
	atomicset.add(fn);
    }
    if (!atomicset.isEmpty()) {
      atomicset.removeAll(notreadyset);
      atomicset.removeAll(cannotdelay);
      System.out.println("-----------------------------------------------------");
      System.out.println(md);
      Hashtable map=new Hashtable();

      for(Iterator it=atomicset.iterator();it.hasNext();) {
	map.put(it.next(),"A");
      }

      for(Iterator it=notreadyset.iterator();it.hasNext();) {
	map.put(it.next(),"2");
      }

      for(Iterator it=cannotdelay.iterator();it.hasNext();) {
	map.put(it.next(),"1");
      }
      System.out.println(fm.printMethod(map));
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