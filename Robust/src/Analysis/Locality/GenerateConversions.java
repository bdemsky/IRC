package Analysis.Locality;
import IR.State;
import IR.Flat.*;
import java.util.*;
import IR.MethodDescriptor;


public class GenerateConversions {
  LocalityAnalysis locality;
  State state;

  /** Warning: This class modifies the code in place.  */

  public GenerateConversions(LocalityAnalysis la, State state) {
    locality=la;
    this.state=state;
    doConversion();
  }

  private void doConversion() {
    Set<LocalityBinding> bindings=locality.getLocalityBindings();
    Iterator<LocalityBinding> bindit=bindings.iterator();
    while(bindit.hasNext()) {
      LocalityBinding lb=bindit.next();
      //Don't need to do conversion if it is already atomic
      if (lb.isAtomic())
	continue;
      converttoPtr(lb);
      converttoOid(lb);
    }
  }

  /* At the end of an atomic block, we need to convert any global
   * references that will be used again into OID's. */

  private void converttoOid(LocalityBinding lb) {
    Hashtable<FlatNode, Integer> atomictab=locality.getAtomic(lb);
    Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptab=locality.getNodeTempInfo(lb);
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Set<TempNodePair>> nodetotnpair=new Hashtable<FlatNode, Set<TempNodePair>>();
    Hashtable<FlatNode, Set<TempDescriptor>> nodetoconvs=new Hashtable<FlatNode, Set<TempDescriptor>>();
    Hashtable<FlatNode, Set<TempDescriptor>> nodetoconvs2=new Hashtable<FlatNode, Set<TempDescriptor>>();

    Set<FlatNode> toprocess=fm.getNodeSet();

    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);
      boolean isatomic=atomictab.get(fn).intValue()>0;

      Hashtable<TempDescriptor, Integer> nodetemptab=temptab.get(fn);

      List<TempDescriptor> reads=Arrays.asList(fn.readsTemps());
      List<TempDescriptor> writes=Arrays.asList(fn.writesTemps());

      if (!isatomic&&fn.kind()==FKind.FlatAtomicExitNode
          &&!nodetoconvs.containsKey(fn)) {
	nodetoconvs.put(fn, new HashSet<TempDescriptor>());
	nodetoconvs2.put(fn, new HashSet<TempDescriptor>());
      }

      HashSet<TempNodePair> tempset=new HashSet<TempNodePair>();

      for(int i=0; i<fn.numPrev(); i++) {
	FlatNode fnprev=fn.getPrev(i);
	if (!nodetotnpair.containsKey(fnprev))
	  continue;

	Set<TempNodePair> prevset=nodetotnpair.get(fnprev);
	for(Iterator<TempNodePair> it=prevset.iterator(); it.hasNext();) {
	  TempNodePair tnp=it.next();
	  if (fn.kind()==FKind.FlatGlobalConvNode&&
	      ((FlatGlobalConvNode)fn).getLocality()!=lb) {
	    //ignore this node
	    tempset.add(tnp);
	    continue;
	  }
	  if (reads.contains(tnp.getTemp())&&tnp.getNode()!=null) {
	    //Value actually is read...
	    nodetoconvs.get(tnp.getNode()).add(tnp.getTemp());
	  }

	  if (writes.contains(tnp.getTemp()))           //value overwritten
	    continue;
	  if (!isatomic&&fn.kind()==FKind.FlatAtomicExitNode) {
	    //Create new node and tag it with this exit
	    if (tnp.getNode()==null) {
	      TempNodePair tnp2=new TempNodePair(tnp.getTemp());
	      tnp2.setNode(fn);
	      tempset.add(tnp2);
	      nodetoconvs2.get(fn).add(tnp.getTemp());  //have to hide cached copies from gc
	    } else
	      tempset.add(tnp);
	  } else
	    tempset.add(tnp);
	}
      }
      if (isatomic) {
	/* If this is in an atomic block, record temps that
	 * are written to.*/

	/* NOTE: If this compiler is changed to maintain
	 * OID/Ptr's in variables, then we need to use all
	 * global temps that could be read and not just the
	 * ones converted by globalconvnode*/

	if (fn.kind()!=FKind.FlatGlobalConvNode||
	    ((FlatGlobalConvNode)fn).getLocality()==lb) {
	  /*If globalconvnode, make sure we have the right
	   * locality. */
	  for(Iterator<TempDescriptor> writeit=writes.iterator(); writeit.hasNext();) {
	    TempDescriptor wrtmp=writeit.next();
	    if (nodetemptab.get(wrtmp)==LocalityAnalysis.GLOBAL||state.SINGLETM) {
	      TempNodePair tnp=new TempNodePair(wrtmp);
	      tempset.add(tnp);
	    }
	  }
	}
      }
      if (!nodetotnpair.containsKey(fn)||!nodetotnpair.get(fn).equals(tempset)) {
	//changes to set, so enqueue next nodes
	nodetotnpair.put(fn, tempset);         //update set
	for(int i=0; i<fn.numNext(); i++) {
	  toprocess.add(fn.getNext(i));
	}
      }
    }
    //Place Convert to Oid nodes
    toprocess=fm.getNodeSet();
    for(Iterator<FlatNode> it=toprocess.iterator(); it.hasNext();) {
      FlatNode fn=it.next();
      if (atomictab.get(fn).intValue()==0&&fn.numPrev()>0&&
          atomictab.get(fn.getPrev(0)).intValue()>0) {
	//sanity check
	assert(fn.kind()==FKind.FlatAtomicExitNode);
	//insert calls here...
	Set<TempDescriptor> tempset=nodetoconvs2.get(fn);
	for(Iterator<TempDescriptor> tempit=tempset.iterator(); tempit.hasNext();) {
	  TempDescriptor tmpd=tempit.next();
	  FlatGlobalConvNode fgcn=new FlatGlobalConvNode(tmpd, lb, false, nodetoconvs.get(fn).contains(tmpd));
	  atomictab.put(fgcn, atomictab.get(fn));
	  temptab.put(fgcn, (Hashtable<TempDescriptor, Integer>)temptab.get(fn).clone());

	  for(int i=0; i<fn.numPrev(); i++) {
	    FlatNode fnprev=fn.getPrev(i);
	    for(int j=0; j<fnprev.numNext(); j++) {
	      if (fnprev.getNext(j)==fn) {
		//found index, change node
		fnprev.setNext(j, fgcn);
		break;
	      }
	    }
	  }
	  fgcn.addNext(fn);
	}
      }
    }
  }

  /* At the beginning of an atomic block, we need to convert any
   * OID's that will be used in the atomic block to pointers */

  private void converttoPtr(LocalityBinding lb) {
    Hashtable<FlatNode, Set<TempDescriptor>> nodetotranstemps=new Hashtable<FlatNode, Set<TempDescriptor>>();
    Hashtable<FlatNode, Integer> atomictab=locality.getAtomic(lb);
    Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptab=locality.getNodeTempInfo(lb);
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Set<FlatNode> toprocess=fm.getNodeSet();

    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);

      if (atomictab.get(fn).intValue()>0) {
	//build set of transaction temps use by next nodes
	HashSet<TempDescriptor> transtemps=new HashSet<TempDescriptor>();
	for(int i=0; i<fn.numNext(); i++) {
	  FlatNode fnnext=fn.getNext(i);
	  if (nodetotranstemps.containsKey(fnnext))
	    transtemps.addAll(nodetotranstemps.get(fnnext));
	}
	//subtract out the ones we write to
	transtemps.removeAll(Arrays.asList(fn.writesTemps()));
	//add in the globals we read from
	Hashtable<TempDescriptor, Integer> pretemptab=locality.getNodePreTempInfo(lb, fn);
	TempDescriptor [] readtemps=fn.readsTemps();
	for(int i=0; i<readtemps.length; i++) {
	  TempDescriptor tmp=readtemps[i];
	  if (pretemptab.get(tmp).intValue()==LocalityAnalysis.GLOBAL||state.SINGLETM) {
	    transtemps.add(tmp);
	  }
	}
	if (!nodetotranstemps.containsKey(fn)||!nodetotranstemps.get(fn).equals(transtemps)) {
	  nodetotranstemps.put(fn, transtemps);
	  for(int i=0; i<fn.numPrev(); i++)
	    toprocess.add(fn.getPrev(i));
	}
      }
    }
    toprocess=fm.getNodeSet();
    for(Iterator<FlatNode> it=toprocess.iterator(); it.hasNext();) {
      FlatNode fn=it.next();
      if (atomictab.get(fn).intValue()>0&&
          atomictab.get(fn.getPrev(0)).intValue()==0) {
	//sanity check
	assert(fn.kind()==FKind.FlatAtomicEnterNode);

	//insert calls here...
	Set<TempDescriptor> tempset=nodetotranstemps.get(fn);
	for(Iterator<TempDescriptor> tempit=tempset.iterator(); tempit.hasNext();) {
	  FlatGlobalConvNode fgcn=new FlatGlobalConvNode(tempit.next(), lb, true);
	  atomictab.put(fgcn, atomictab.get(fn));
	  temptab.put(fgcn, (Hashtable<TempDescriptor, Integer>)temptab.get(fn).clone());
	  fgcn.addNext(fn.getNext(0));
	  fn.setNext(0, fgcn);
	}
      }
    }
  }
}
