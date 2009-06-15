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

  public DelayComputation(LocalityAnalysis locality, State state, TypeAnalysis typeanalysis, GlobalFieldType gft) {
    this.locality=locality;
    this.state=state;
    this.typeanalysis=typeanalysis;
    this.gft=gft;
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
	  nodelayarrayrdset.addAll(typeanalysis.expand(((FlatSetElementNode)fn).getSrc().getType()));	  
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
    } //end of while loop

  } //end of method
} //end of class