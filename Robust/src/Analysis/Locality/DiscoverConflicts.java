package Analysis.Locality;

import IR.Flat.*;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Hashtable;
import IR.State;
import IR.TypeDescriptor;
import IR.MethodDescriptor;
import IR.FieldDescriptor;

public DiscoverConflicts {
    Set<FieldDescriptor> fields;
    Set<TypeDescriptor> arrays;
    LocalityAnalysis locality;
    State state;
    Hashtable<Locality, Set<FlatNode>> needsTR;

    public DiscoverConflicts(LocalityAnalysis locality, State state) {
	this.locality=locality;
	this.fields=new HashSet<FieldDescriptor>();
	this.arrays=new HashSet<TypeDescriptor>();
	this.needsTR=new Hashtable<Locality, Set<FlatNode>>();
	this.state=state;
	transreadmap=new Hashtable<LocalityBinding, Set<TempFlatPair>>();
	srcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    }
    
    public void doAnalysis() {
	//Compute fields and arrays for all transactions
	Set<LocalityBinding> localityset=locality.getLocalityBindings();
	for(Iterator<LocalityBinding> lb=localityset.iteratory();lb.hasNext();) {
	    computeModified(lb.next());
	}
	expandTypes();
	//Compute set of nodes that need transread
	Set<LocalityBinding> localityset=locality.getLocalityBindings();
	for(Iterator<LocalityBinding> lb=localityset.iteratory();lb.hasNext();) {
	    analyzeLocality(lb.next());
	}
    }
    public void expandTypes() {
	FIX ARRAY...compute super/sub sets of each so we can do simple membership test
    }

    Hashtable<TempDescriptor, Set<FlatNode>> doMerge(FlatNode fn, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<FlatNode>>> tmptofnset) {
	Hashtable<TempDescriptor, Set<FlatNode>> table=new Hashtable<TempDescriptor, Set<FlatNode>>();
	for(int i=0;i<fn.numPrev();i++) {
	    FlatNode fprev=fn.getPrev(i);
	    Hashtable<TempDescriptor, Set<FlatNode>> tabset=tmptofnset.get(fprev);
	    if (tabset!=null) {
		for(Iterator<TempDescriptor> tmpit=tabset.keySet().iterator();tmpit.hasNext();) {
		    TempDescriptor td=tmpit.next();
		    Set<FlatNode> fnset=tabset.get(td);
		    if (!table.containsKey(td))
			table.put(td, new HashSet<FlatNode>());
		    table.get(td).addAll(fnset);
		}
	    }
	}
	return table;
    }

    Hashtable<LocalityBinding, Set<TempFlatPair>> transreadmap;
    Hashtable<LocalityBinding, Set<FlatNode>> srcmap;


    public void analyzeLocality(LocalityBinding lb) {
	Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> fnmap=computeTempSets(lb);
	HashSet<TempFlatPair> tfset=computeTranslationSet(lb, fnmap);
	HashSet<FlatNode> srctrans=new HashSet<FlatNode>();
	transreadmap.put(lb, tfset);
	srcmap.put(lb,srctrans);

	for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
	    FlatNode fn=fnit.next();
	    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
	    if (atomictable.get(fn).intValue()>0) {
		Hashtable<TempDescriptor, Set<TempFlatPair>> tmap=fnmap.get(fn);
		switch(fn.kind()) {
		case FKind.FlatSetFieldNode: { 
		    //definitely need to translate these
		    FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
		    Set<TempFlatPair> tfpset=tmap.get(fsfn.getSrc());
		    for(Iterator<TempFlatPair> tfpit=tfpset.iterator();tfpit.hasNext();) {
			TempFlatPair tfp=tfpit.nexT();
			if (tfset.contains(tfp)) {
			    srctrans.add(fsfn);
			    break;
			}
		    }
		    break;
		}
		case FKind.FlatSetElementNode: { 
		    //definitely need to translate these
		    FlatSetElementNode fsen=(FlatSetElementNode)fn;
		    Set<TempFlatPair> tfpset=tmap.get(fsen.getSrc());
		    for(Iterator<TempFlatPair> tfpit=tfpset.iterator();tfpit.hasNext();) {
			TempFlatPair tfp=tfpit.nexT();
			if (tfset.contains(tfp)) {
			    srctrans.add(fsfn);
			    break;
			}
		    }
		    break;
		}
		default:
		}
	    }
	}
    }

    HashSet<TempFlatPair> computeTranslationSet(LocalityBinding lb, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> fnmap) {
	HashSet<TempFlatPair> tfset=new HashSet<TempFlatPair>();

	for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
	    FlatNode fn=fnit.next();
	    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
	    if (atomictable.get(fn).intValue()>0) {
		Hashtable<TempDescriptor, Set<TempFlatPair>> tmap=fnmap.get(fn);
		switch(fn.kind()) {
		case FKind.FlatElementNode: {
		    FlatElementNode fen=(FlatElementNode)fn;
		    if (arrays.contains(fen.getField())) {
			//this could cause conflict...figure out conflict set
			Set<TempFlatPair> tfpset=tmap.get(fen.getSrc());
			tfset.addAll(tfpset);
		    }
		    break;
		}
		case FKind.FlatFieldNode: { 
		    FlatFieldNode ffn=(FlatFieldNode)fn;
		    if (fields.contains(ffn.getField())) {
			//this could cause conflict...figure out conflict set
			Set<TempFlatPair> tfpset=tmap.get(ffn.getSrc());
			tfset.addAll(tfpset);
		    }
		    break;
		}
		case FKind.FlatSetFieldNode: { 
		    //definitely need to translate these
		    FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
		    Set<TempFlatPair> tfpset=tmap.get(fsfn.getDst());
		    tfset.addAll(tfpset);
		    break;
		}
		case FKind.FlatSetElementNode: { 
		    //definitely need to translate these
		    FlatSetElementNode fsen=(FlatSetElementNode)fn;
		    Set<TempFlatPair> tfpset=tmap.get(fsen.getDst());
		    tfset.addAll(tfpset);
		    break;
		}
		case FKind.FlatCall: //assume pessimistically that calls do bad things
		case FKind.FlatReturn: {
		    TempDescriptor []readarray=fn.readsTemps();
		    for(int i=0;i<readarray.length;i++) {
			TempDescriptor rtmp=readarray[i];
			Set<TempFlatPair> tfpset=tmap.get(rtmp);
			tfset.addAll(tfpset);
		    }
		    break;
		default:
		    //do nothing
		}
		}
	    }
	}	
	return tfset;
    }

    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> computeTempSets(LocalityBinding lb) {
	Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>> tmptofnset=new Hashtable<FlatNode, Hashtable<TempDescriptor>, Set<TempFlatPair>>();
	HashSet<FlatNode> discovered=new HashSet<FlatNode>();
	HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
	MethodDescriptor md=lb.getMethod();
	FlatMethod fm=state.getMethodFlat(md);
	Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
	Hashtable<FlatNode, Set<TempDescriptor>> livetemps=locality.computeLiveTemps(fm);
	tovisit.add(fm);
	discovered.add(fm);
	
	while(!tovisit.isEmpty()) {
	    FlatNode fn=tovisit.iterator().next();
	    tovisit.remove(fn);
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode fnext=fn.getNext(i);
		if (!discovered.contains(fnext)) {
		    discovered.add(fnext);
		    tovisit.add(fnext);
		}
	    }
	    Hashtable<TempDescriptor, Set<TempFlatPair>> ttofn=null;
	    if (atomictable.get(fn).intValue()!=0) {
		if (fn.numPrev()>0&&atomictable.get(fn.getPrev(0))) {
		    //flatatomic enter node...  see what we really need to transread
		    Set<TempDescriptor> liveset=livetemps.get(fn);
		    ttofn=new Hashtable<TempDescriptor, Set<TempFlatPair>>();
		    for(Iterator<TempDescriptor> tmpit=liveset.iterator();tmpit.hasNext();) {
			TempDescriptor tmp=tmpit.next();
			if (tmp.getType().isPtr()) {
			    HashSet<TempFlatPair> fnset=new HashSet<TempFlatPair>();
			    fnset.add(new TempFlatPair(tmp, fn));
			    ttofn.put(tmp, fnset);
			}
		    }
		} else {
		    ttofn=doMerge(fn, tmptofnset);
		    switch(fn.kind()) {
		    case FKind.FlatFieldNode:
		    case FKind.FlatElementNode: {
			TempDescriptor[] writes=fn.writesTemps();
			for(int i=0;i<writes.length;i++) {
			    TempDescriptor wtmp=writes[i];
			    HashSet<TempFlatPair> set=new HashSet<TempFlatPair>();
			    set.add(new TempFlatPair(wtmp, fn));
			    mtable.put(wtmp, set);
			}
			break;
		    }
		    case FKind.FlatOpNode: {
			FlatOpNode fon=(FlatOpNode)fn;
			if (fon.getOp().getOp()==Operation.ASSIGN&&fon.getDest().getType().isPtr()) {
			    mtable.put(fon.getDest(), new HashSet<TempFlatPair>(mtable.get(fon.getLeft())));
			    break;
			}
		    }
		    default:
			//Do kill computation
			TempDescriptor[] writes=fn.writesTemps();
			for(int i=0;i<writes.length;i++) {
			    TempDescriptor wtmp=writes[i];
			    mtable.remove(writes[i]);
			}
		    }
		}
		if (ttofn!=null) {
		    if (!tmptofnset.containsKey(fn)||
			!tmptofnset.get(fn).equals(ttofn)) {
			//enqueue nodes to process
			tmptofnset.put(fn, ttofn);
			for(int i=0;i<fn.numNext();i++) {
			    FlatNode fnext=fn.getNext(i);
			    tovisit.add(fnext);
			}
		    }
		}
	    }
	}	
	return tmptofnset;
    }

    public void computeModified(LocalityBinding lb) {
	Hashtable<FlatNode, Set<TempDescriptor>> oldtemps=computeOldTemps(lb);
	for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
	    FlatNode fn=fnit.next();
	    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
	    if (atomictable.get(fn).intValue()>0) {
		switch (fn.kind()) {
		case FKind.FlatSetFieldNode:
		    FlatSetFieldNode fsfn=(FlatSetFieldNode) fn;
		    fields.add(fsfn.getField());
		    break;
		case FKind.FlatSetElementNode:
		    FlatSetElementNode fsfn=(FlatSetElementNode) fn;
		    arrays.add(fsen.getDst().getType());
		    break;
		default:
		}
	    }
	}
    }
    
    Hashtable<FlatNode, Set<TempDescriptor>> computeOldTemps(LocalityBinding lb) {
	Hashtable<FlatNode, Set<TempDescriptor>> fntooldtmp=new Hashtable<FlatNode, Set<TempDescriptor>>();
	HashSet<FlatNode> discovered=new HashSet<FlatNode>();
	HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
	MethodDescriptor md=lb.getMethod();
	FlatMethod fm=state.getMethodFlat(md);
	Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
	Hashtable<FlatNode, Set<TempDescriptor>> livetemps=locality.computeLiveTemps(fm);
	tovisit.add(fm);
	discovered.add(fm);
	
	while(!tovisit.isEmpty()) {
	    FlatNode fn=tovisit.iterator().next();
	    tovisit.remove(fn);
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode fnext=fn.getNext(i);
		if (!discovered.contains(fnext)) {
		    discovered.add(fnext);
		    tovisit.add(fnext);
		}
	    }
	    HashSet<TempDescriptor> oldtemps=null;
	    if (atomictable.get(fn).intValue()!=0) {
		if (fn.numPrev()>0&&atomictable.get(fn.getPrev(0))) {
		    //Everything live is old
		    Set<TempDescriptor> lives=livetemps.get(fn);
		    oldtemps=new HashSet<TempDescriptor>();
		    
		    for(Iterator<TempDescriptor> it=lives.iterator();it.hasNext();) {
			TempDescriptor tmp=it.next();
			if (tmp.getType().isPtr()) {
			    oldtemps.add(tmp);
			}
		    } else {
			oldtemps=new HashSet<TempDescriptor>();
			//Compute union of old temporaries
			for(int i=0;i<fn.numPrev();i++) {
			    HashSet<TempDescriptor> pset=fnotooldtmp.get(fn.getPrev(i));
			    if (pset!=null)
				oldtemps.addAll(pset);
			}
			
			switch (fn.kind()) {
			case FKind.FlatNew:
			    oldtemps.removeAll(Arrays.asList(fn.readsTemps()));
			    break;
			case FKind.FlatOpNode:
			    {
				FlatOpNode fon=(FlatOpNode)fn;
				if (fon.getOp().getOp()==Operation.ASSIGN&&fn.getDest().getType().isPtr()) {
				    if (oldtemps.contains(fn.getLeft()))
					oldtemps.add(fn.getDest());
				    else
					oldtemps.remove(fn.getDest());
				    break;
				}
			    }
			default:
			    {
				TempDescriptor[] writes=fn.writesTemps();
				for(int i=0;i<writes.length;i++) {
				    TempDescriptor wtemp=writes[i];
				    if (wtemp.getType().isPtr())
					oldtemps.add(wtemp);
				}
			    }
			}
		    }
		}
	    }
	    if (oldtemps!=null) {
		if (!fntooldtmp.containsKey(fn)||!fntooldtmp.get(fn).equals(oldtemps)) {
		    fntooldtmp.put(fn, oldtemps);
		    //propagate changes
		    for(int i=0;i<fn.numNext();i++) {
			FlatNode fnext=fn.getNext(i);
			tovisit.add(fnext);
		    }
		}
	    }
	}
	return fntooldtmp;
    }
}

class TempFlatPair {
    FlatNode f;
    TempDescriptor t;
    TempFlatPair(TempDescriptor t, FlatNode f) {
	this.t=t;
	this.f=f;
    }

    public int hashCode() {
	return f.hashCode()^t.hashCode();
    }
    public boolean equals(Object o) {
	TempFlatPair tf=(TempFlatPair)o;
	return t.equals(tf.t)&&f.equals(tf.f);
    }
}
