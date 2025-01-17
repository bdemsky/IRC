package Analysis.Locality;

import IR.Flat.*;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Hashtable;
import IR.State;
import IR.Operation;
import IR.TypeDescriptor;
import IR.MethodDescriptor;
import IR.FieldDescriptor;
import Analysis.Liveness;
import Analysis.Loops.GlobalFieldType;

public class DiscoverConflicts {
  Set<FieldDescriptor> fields;
  Set<TypeDescriptor> arrays;
  LocalityAnalysis locality;
  State state;
  Hashtable<LocalityBinding, Set<FlatNode>> treadmap;
  Hashtable<LocalityBinding, Set<TempFlatPair>> transreadmap;
  Hashtable<LocalityBinding, Set<FlatNode>> twritemap;
  Hashtable<LocalityBinding, Set<TempFlatPair>> writemap;
  Hashtable<LocalityBinding, Set<FlatNode>> getmap;

  Hashtable<LocalityBinding, Set<FlatNode>> srcmap;
  Hashtable<LocalityBinding, Set<FlatNode>> leftsrcmap;
  Hashtable<LocalityBinding, Set<FlatNode>> rightsrcmap;
  TypeAnalysis typeanalysis;
  Hashtable<LocalityBinding, HashSet<FlatNode>>cannotdelaymap;
  Hashtable<LocalityBinding, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>>> lbtofnmap;
  boolean inclusive=false;
  boolean normalassign=false;
  GlobalFieldType gft;

  public DiscoverConflicts(LocalityAnalysis locality, State state, TypeAnalysis typeanalysis, GlobalFieldType gft) {
    this.locality=locality;
    this.fields=new HashSet<FieldDescriptor>();
    this.arrays=new HashSet<TypeDescriptor>();
    this.state=state;
    this.typeanalysis=typeanalysis;
    transreadmap=new Hashtable<LocalityBinding, Set<TempFlatPair>>();
    treadmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    srcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    leftsrcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    rightsrcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    lbtofnmap=new Hashtable<LocalityBinding, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>>>();
    if (gft!=null) {
      twritemap=new Hashtable<LocalityBinding, Set<FlatNode>>();
      writemap=new Hashtable<LocalityBinding, Set<TempFlatPair>>();
    }
    getmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    this.gft=gft;
  }

  public DiscoverConflicts(LocalityAnalysis locality, State state, TypeAnalysis typeanalysis, Hashtable<LocalityBinding, HashSet<FlatNode>> cannotdelaymap, boolean inclusive, boolean normalassign, GlobalFieldType gft) {
    this.locality=locality;
    this.fields=new HashSet<FieldDescriptor>();
    this.arrays=new HashSet<TypeDescriptor>();
    this.state=state;
    this.typeanalysis=typeanalysis;
    this.cannotdelaymap=cannotdelaymap;
    transreadmap=new Hashtable<LocalityBinding, Set<TempFlatPair>>();
    treadmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    srcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    leftsrcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    rightsrcmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    lbtofnmap=new Hashtable<LocalityBinding, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>>>();
    this.inclusive=inclusive;
    this.normalassign=normalassign;
    if (gft!=null) {
      twritemap=new Hashtable<LocalityBinding, Set<FlatNode>>();
      writemap=new Hashtable<LocalityBinding, Set<TempFlatPair>>();
    }
    getmap=new Hashtable<LocalityBinding, Set<FlatNode>>();
    this.gft=gft;
  }

  public Set<FieldDescriptor> getFields() {
    return fields;
  }

  public Set<TypeDescriptor> getArrays() {
    return arrays;
  }

  public void doAnalysis() {
    //Compute fields and arrays for all transactions.  Note that we
    //only look at changes to old objects

    Set<LocalityBinding> localityset=locality.getLocalityBindings();
    for(Iterator<LocalityBinding> lb=localityset.iterator(); lb.hasNext(); ) {
      computeModified(lb.next());
    }
    expandTypes();
    //Compute set of nodes that need transread
    for(Iterator<LocalityBinding> lb=localityset.iterator(); lb.hasNext(); ) {
      LocalityBinding l=lb.next();
      analyzeLocality(l);
    }
  }

  //Change flatnode/temp pairs to just flatnodes that need transactional reads

  private void setNeedReadTrans(LocalityBinding lb) {
    HashSet<FlatNode> set=new HashSet<FlatNode>();
    for(Iterator<TempFlatPair> it=transreadmap.get(lb).iterator(); it.hasNext(); ) {
      TempFlatPair tfp=it.next();
      set.add(tfp.f);
    }
    treadmap.put(lb, set);
    if (gft!=null) {
      //need to translate write map set
      set=new HashSet<FlatNode>();
      for(Iterator<TempFlatPair> it=writemap.get(lb).iterator(); it.hasNext(); ) {
        TempFlatPair tfp=it.next();
        set.add(tfp.f);
      }
      twritemap.put(lb, set);
    }
  }

  private void computeneedsarrayget(LocalityBinding lb, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> fnmap) {
    //    Set<FlatNode> gwriteset=(state.READSET&&gft!=null)?twritemap.get(lb):treadmap.get(lb);
    if (state.READSET&&gft!=null) {
      if (twritemap.get(lb).size()==0) {
        getmap.put(lb, new HashSet<FlatNode>());
        return;
      }
    }

    Set<FlatNode> gwriteset=treadmap.get(lb);
    FlatMethod fm=state.getMethodFlat(lb.getMethod());
    HashSet<FlatNode> needsget=new HashSet<FlatNode>();
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
      if (atomictable.get(fn).intValue()>0&&fn.kind()==FKind.FlatElementNode) {
        FlatElementNode fen=(FlatElementNode)fn;
        Set<TempFlatPair> tfpset=fnmap.get(fen).get(fen.getSrc());
        if (tfpset!=null) {
          for(Iterator<TempFlatPair> tfpit=tfpset.iterator(); tfpit.hasNext(); ) {
            TempFlatPair tfp=tfpit.next();
            if (gwriteset.contains(tfp.f)) {
              needsget.add(fen);
              break;
            }
          }
        }
      }
    }
    getmap.put(lb, needsget);
  }

  //We have a set of things we write to, figure out what things this
  //could effect.
  public void expandTypes() {
    Set<TypeDescriptor> expandedarrays=new HashSet<TypeDescriptor>();
    for(Iterator<TypeDescriptor> it=arrays.iterator(); it.hasNext(); ) {
      TypeDescriptor td=it.next();
      expandedarrays.addAll(typeanalysis.expand(td));
    }
    arrays=expandedarrays;
  }

  Hashtable<TempDescriptor, Set<TempFlatPair>> doMerge(FlatNode fn, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> tmptofnset) {
    Hashtable<TempDescriptor, Set<TempFlatPair>> table=new Hashtable<TempDescriptor, Set<TempFlatPair>>();
    for(int i=0; i<fn.numPrev(); i++) {
      FlatNode fprev=fn.getPrev(i);
      Hashtable<TempDescriptor, Set<TempFlatPair>> tabset=tmptofnset.get(fprev);
      if (tabset!=null) {
        for(Iterator<TempDescriptor> tmpit=tabset.keySet().iterator(); tmpit.hasNext(); ) {
          TempDescriptor td=tmpit.next();
          Set<TempFlatPair> fnset=tabset.get(td);
          if (!table.containsKey(td))
            table.put(td, new HashSet<TempFlatPair>());
          table.get(td).addAll(fnset);
        }
      }
    }
    return table;
  }

  public Set<FlatNode> getNeedSrcTrans(LocalityBinding lb) {
    return srcmap.get(lb);
  }

  public boolean getNeedSrcTrans(LocalityBinding lb, FlatNode fn) {
    return srcmap.get(lb).contains(fn);
  }

  public boolean getNeedLeftSrcTrans(LocalityBinding lb, FlatNode fn) {
    return leftsrcmap.get(lb).contains(fn);
  }

  public boolean getNeedRightSrcTrans(LocalityBinding lb, FlatNode fn) {
    return rightsrcmap.get(lb).contains(fn);
  }

  public boolean getNeedTrans(LocalityBinding lb, FlatNode fn) {
    return treadmap.get(lb).contains(fn);
  }

  public boolean getNeedGet(LocalityBinding lb, FlatNode fn) {
    if (gft!=null)
      return getmap.get(lb).contains(fn);
    else throw new Error();
  }

  public boolean getNeedWriteTrans(LocalityBinding lb, FlatNode fn) {
    if (gft!=null)
      return twritemap.get(lb).contains(fn);
    else throw new Error();
  }

  public Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> getMap(LocalityBinding lb) {
    return lbtofnmap.get(lb);
  }

  private void analyzeLocality(LocalityBinding lb) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);

    //Compute map from flatnode -> (temps -> source of value)
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> fnmap=computeTempSets(lb);
    lbtofnmap.put(lb,fnmap);
    HashSet<TempFlatPair> writeset=null;
    if (gft!=null) {
      writeset=new HashSet<TempFlatPair>();
    }
    HashSet<TempFlatPair> tfset=computeTranslationSet(lb, fm, fnmap, writeset);
    if (gft!=null) {
      writemap.put(lb, writeset);
    }

    HashSet<FlatNode> srctrans=new HashSet<FlatNode>();
    HashSet<FlatNode> leftsrctrans=new HashSet<FlatNode>();
    HashSet<FlatNode> rightsrctrans=new HashSet<FlatNode>();
    transreadmap.put(lb, tfset);
    srcmap.put(lb,srctrans);
    leftsrcmap.put(lb,leftsrctrans);
    rightsrcmap.put(lb,rightsrctrans);

    //compute writes that need translation on source
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
      if (atomictable.get(fn).intValue()>0) {
        Hashtable<TempDescriptor, Set<TempFlatPair>> tmap=fnmap.get(fn);
        switch(fn.kind()) {
        //We might need to translate arguments to pointer comparison

        case FKind.FlatOpNode: {
          FlatOpNode fon=(FlatOpNode)fn;
          if (fon.getOp().getOp()==Operation.EQUAL||
              fon.getOp().getOp()==Operation.NOTEQUAL) {
            if (!fon.getLeft().getType().isPtr())
              break;
            Set<TempFlatPair> lefttfpset=tmap.get(fon.getLeft());
            Set<TempFlatPair> righttfpset=tmap.get(fon.getRight());
            //handle left operand
            if (lefttfpset!=null) {
              for(Iterator<TempFlatPair> tfpit=lefttfpset.iterator(); tfpit.hasNext(); ) {
                TempFlatPair tfp=tfpit.next();
                if (tfset.contains(tfp)||outofscope(tfp)) {
                  leftsrctrans.add(fon);
                  break;
                }
              }
            }
            //handle right operand
            if (righttfpset!=null) {
              for(Iterator<TempFlatPair> tfpit=righttfpset.iterator(); tfpit.hasNext(); ) {
                TempFlatPair tfp=tfpit.next();
                if (tfset.contains(tfp)||outofscope(tfp)) {
                  rightsrctrans.add(fon);
                  break;
                }
              }
            }
          }
          break;
        }

        case FKind.FlatGlobalConvNode: {
          //need to translate these if the value we read from may be a
          //shadow...  check this by seeing if any of the values we
          //may read are in the transread set or came from our caller
          //or a method we called

          FlatGlobalConvNode fgcn=(FlatGlobalConvNode)fn;
          if (fgcn.getLocality()!=lb||
              fgcn.getMakePtr())
            break;

          Set<TempFlatPair> tfpset=tmap.get(fgcn.getSrc());

          if (tfpset!=null) {
            for(Iterator<TempFlatPair> tfpit=tfpset.iterator(); tfpit.hasNext(); ) {
              TempFlatPair tfp=tfpit.next();
              if (tfset.contains(tfp)||outofscope(tfp)) {
                srctrans.add(fgcn);
                break;
              }
            }
          }
          break;
        }

        case FKind.FlatSetFieldNode: {
          //need to translate these if the value we read from may be a
          //shadow...  check this by seeing if any of the values we
          //may read are in the transread set or came from our caller
          //or a method we called

          FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
          if (!fsfn.getField().getType().isPtr())
            break;
          Set<TempFlatPair> tfpset=tmap.get(fsfn.getSrc());
          if (tfpset!=null) {
            for(Iterator<TempFlatPair> tfpit=tfpset.iterator(); tfpit.hasNext(); ) {
              TempFlatPair tfp=tfpit.next();
              if (tfset.contains(tfp)||outofscope(tfp)) {
                srctrans.add(fsfn);
                break;
              }
            }
          }
          break;
        }

        case FKind.FlatSetElementNode: {
          //need to translate these if the value we read from may be a
          //shadow...  check this by seeing if any of the values we
          //may read are in the transread set or came from our caller
          //or a method we called

          FlatSetElementNode fsen=(FlatSetElementNode)fn;
          if (!fsen.getSrc().getType().isPtr())
            break;
          Set<TempFlatPair> tfpset=tmap.get(fsen.getSrc());
          if (tfpset!=null) {
            for(Iterator<TempFlatPair> tfpit=tfpset.iterator(); tfpit.hasNext(); ) {
              TempFlatPair tfp=tfpit.next();
              if (tfset.contains(tfp)||outofscope(tfp)) {
                srctrans.add(fsen);
                break;
              }
            }
          }
          break;
        }

        default:
        }
      }
    }
    //Update results
    setNeedReadTrans(lb);
    computeneedsarrayget(lb, fnmap);
  }

  public boolean outofscope(TempFlatPair tfp) {
    FlatNode fn=tfp.f;
    return fn.kind()==FKind.FlatCall||fn.kind()==FKind.FlatMethod;
  }

  private void computeReadOnly(LocalityBinding lb, Hashtable<FlatNode, Set<TypeDescriptor>> updatedtypemap, Hashtable<FlatNode, Set<FieldDescriptor>> updatedfieldmap) {
    //inside of transaction, try to convert rw access to ro access
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);

    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    toanalyze.addAll(fm.getNodeSet());

    while(!toanalyze.isEmpty()) {
      FlatNode fn=toanalyze.iterator().next();
      toanalyze.remove(fn);
      HashSet<TypeDescriptor> updatetypeset=new HashSet<TypeDescriptor>();
      HashSet<FieldDescriptor> updatefieldset=new HashSet<FieldDescriptor>();

      //Stop if we aren't in a transaction
      if (atomictable.get(fn).intValue()==0)
        continue;

      //Do merge of all exits
      for(int i=0; i<fn.numNext(); i++) {
        FlatNode fnnext=fn.getNext(i);
        if (updatedtypemap.containsKey(fnnext)) {
          updatetypeset.addAll(updatedtypemap.get(fnnext));
        }
        if (updatedfieldmap.containsKey(fnnext)) {
          updatefieldset.addAll(updatedfieldmap.get(fnnext));
        }
      }

      //process this node
      if (cannotdelaymap!=null&&cannotdelaymap.containsKey(lb)&&cannotdelaymap.get(lb).contains(fn)!=inclusive) {
        switch(fn.kind()) {
        case FKind.FlatSetFieldNode: {
          FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
          updatefieldset.add(fsfn.getField());
          break;
        }

        case FKind.FlatSetElementNode: {
          FlatSetElementNode fsen=(FlatSetElementNode)fn;
          updatetypeset.addAll(typeanalysis.expand(fsen.getDst().getType()));
          break;
        }

        case FKind.FlatCall: {
          FlatCall fcall=(FlatCall)fn;
          MethodDescriptor mdfc=fcall.getMethod();

          //get modified fields
          Set<FieldDescriptor> fields=gft.getFieldsAll(mdfc);
          updatefieldset.addAll(fields);

          //get modified arrays
          Set<TypeDescriptor> arrays=gft.getArraysAll(mdfc);
          updatetypeset.addAll(typeanalysis.expandSet(arrays));
          break;
        }
        }
      }

      if (!updatedtypemap.containsKey(fn)||!updatedfieldmap.containsKey(fn)||
          !updatedtypemap.get(fn).equals(updatetypeset)||!updatedfieldmap.get(fn).equals(updatefieldset)) {
        updatedtypemap.put(fn, updatetypeset);
        updatedfieldmap.put(fn, updatefieldset);
        for(int i=0; i<fn.numPrev(); i++) {
          toanalyze.add(fn.getPrev(i));
        }
      }
    }
  }


  /** Need to figure out which nodes need a transread to make local
     copies.  Transread conceptually tracks conflicts.  This depends on
     what fields/elements are accessed We iterate over all flatnodes that
     access fields...If these accesses could conflict, we mark the source
     tempflat pair as needing a transread */


  HashSet<TempFlatPair> computeTranslationSet(LocalityBinding lb, FlatMethod fm, Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> fnmap, Set<TempFlatPair> writeset) {
    HashSet<TempFlatPair> tfset=new HashSet<TempFlatPair>();

    //Compute maps from flatnodes -> sets of things that may be updated after this node
    Hashtable<FlatNode, Set<TypeDescriptor>> updatedtypemap=null;
    Hashtable<FlatNode, Set<FieldDescriptor>> updatedfieldmap=null;

    if (writeset!=null&&!lb.isAtomic()) {
      updatedtypemap=new Hashtable<FlatNode, Set<TypeDescriptor>>();
      updatedfieldmap=new Hashtable<FlatNode, Set<FieldDescriptor>>();
      computeReadOnly(lb, updatedtypemap, updatedfieldmap);
    }

    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      //Check whether this node matters for cannot delayed computation
      if (cannotdelaymap!=null&&cannotdelaymap.containsKey(lb)&&cannotdelaymap.get(lb).contains(fn)==inclusive)
        continue;

      Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
      if (atomictable.get(fn).intValue()>0) {
        Hashtable<TempDescriptor, Set<TempFlatPair>> tmap=fnmap.get(fn);
        switch(fn.kind()) {
        case FKind.FlatElementNode: {
          FlatElementNode fen=(FlatElementNode)fn;
          if (arrays.contains(fen.getSrc().getType())) {
            //this could cause conflict...figure out conflict set
            Set<TempFlatPair> tfpset=tmap.get(fen.getSrc());
            if (tfpset!=null)
              tfset.addAll(tfpset);
          }
          if (updatedtypemap!=null&&updatedtypemap.get(fen).contains(fen.getSrc().getType())) {
            //this could cause conflict...figure out conflict set
            Set<TempFlatPair> tfpset=tmap.get(fen.getSrc());
            if (tfpset!=null)
              tfset.addAll(tfpset);
          }
          break;
        }

        case FKind.FlatFieldNode: {
          FlatFieldNode ffn=(FlatFieldNode)fn;
          if (fields.contains(ffn.getField())) {
            //this could cause conflict...figure out conflict set
            Set<TempFlatPair> tfpset=tmap.get(ffn.getSrc());
            if (tfpset!=null)
              tfset.addAll(tfpset);
          }
          if (updatedfieldmap!=null&&updatedfieldmap.get(ffn).contains(ffn.getField())) {
            //this could cause conflict...figure out conflict set
            Set<TempFlatPair> tfpset=tmap.get(ffn.getSrc());
            if (tfpset!=null)
              tfset.addAll(tfpset);
          }
          break;
        }

        case FKind.FlatSetFieldNode: {
          //definitely need to translate these
          FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
          Set<TempFlatPair> tfpset=tmap.get(fsfn.getDst());
          if (tfpset!=null)
            tfset.addAll(tfpset);
          if (writeset!=null) {
            if (tfpset!=null)
              writeset.addAll(tfpset);
          }
          break;
        }

        case FKind.FlatSetElementNode: {
          //definitely need to translate these
          FlatSetElementNode fsen=(FlatSetElementNode)fn;
          Set<TempFlatPair> tfpset=tmap.get(fsen.getDst());
          if (tfpset!=null)
            tfset.addAll(tfpset);
          if (writeset!=null) {
            if (tfpset!=null)
              writeset.addAll(tfpset);
          }
          break;
        }

        case FKind.FlatCall: //assume pessimistically that calls do bad things
        case FKind.FlatReturnNode: {
          TempDescriptor [] readarray=fn.readsTemps();
          for(int i=0; i<readarray.length; i++) {
            TempDescriptor rtmp=readarray[i];
            Set<TempFlatPair> tfpset=tmap.get(rtmp);
            if (tfpset!=null)
              tfset.addAll(tfpset);
            if (writeset!=null) {
              if (tfpset!=null)
                writeset.addAll(tfpset);
            }
          }
          break;
        }

        default:
          //do nothing
        }
      }
    }
    return tfset;
  }


  //This method generates as output for each node
  //A map from from temps to a set of temp/flat pairs that the
  //original temp points to
  //A temp/flat pair gives the flatnode that the value was created at
  //and the original temp

  Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> computeTempSets(LocalityBinding lb) {
    Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>> tmptofnset=new Hashtable<FlatNode, Hashtable<TempDescriptor, Set<TempFlatPair>>>();
    HashSet<FlatNode> discovered=new HashSet<FlatNode>();
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
    Hashtable<FlatNode, Set<TempDescriptor>> livetemps=Liveness.computeLiveTemps(fm,-1);
    tovisit.add(fm);
    discovered.add(fm);

    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      for(int i=0; i<fn.numNext(); i++) {
        FlatNode fnext=fn.getNext(i);
        if (!discovered.contains(fnext)) {
          discovered.add(fnext);
          tovisit.add(fnext);
        }
      }
      Hashtable<TempDescriptor, Set<TempFlatPair>> ttofn=null;
      if (atomictable.get(fn).intValue()!=0) {
        if ((fn.numPrev()>0)&&atomictable.get(fn.getPrev(0)).intValue()==0) {
          //atomic node, start with new set
          ttofn=new Hashtable<TempDescriptor, Set<TempFlatPair>>();
        } else {
          ttofn=doMerge(fn, tmptofnset);
          switch(fn.kind()) {
          case FKind.FlatGlobalConvNode: {
            FlatGlobalConvNode fgcn=(FlatGlobalConvNode)fn;
            if (lb==fgcn.getLocality()&&
                fgcn.getMakePtr()) {
              TempDescriptor[] writes=fn.writesTemps();
              for(int i=0; i<writes.length; i++) {
                TempDescriptor wtmp=writes[i];
                HashSet<TempFlatPair> set=new HashSet<TempFlatPair>();
                set.add(new TempFlatPair(wtmp, fn));
                ttofn.put(wtmp, set);
              }
            }
            break;
          }

          case FKind.FlatFieldNode:
          case FKind.FlatElementNode: {
            TempDescriptor[] writes=fn.writesTemps();
            for(int i=0; i<writes.length; i++) {
              TempDescriptor wtmp=writes[i];
              HashSet<TempFlatPair> set=new HashSet<TempFlatPair>();
              set.add(new TempFlatPair(wtmp, fn));
              ttofn.put(wtmp, set);
            }
            break;
          }

          case FKind.FlatCall:
          case FKind.FlatMethod: {
            TempDescriptor[] writes=fn.writesTemps();
            for(int i=0; i<writes.length; i++) {
              TempDescriptor wtmp=writes[i];
              HashSet<TempFlatPair> set=new HashSet<TempFlatPair>();
              set.add(new TempFlatPair(wtmp, fn));
              ttofn.put(wtmp, set);
            }
            break;
          }

          case FKind.FlatCastNode:
          case FKind.FlatOpNode:
            if (fn.kind()==FKind.FlatCastNode) {
              FlatCastNode fcn=(FlatCastNode)fn;
              if (fcn.getDst().getType().isPtr()) {
                HashSet<TempFlatPair> set=new HashSet<TempFlatPair>();
                if (ttofn.containsKey(fcn.getSrc()))
                  set.addAll(ttofn.get(fcn.getSrc()));
                if (normalassign)
                  set.add(new TempFlatPair(fcn.getDst(), fn));
                ttofn.put(fcn.getDst(), set);
                break;
              }
            } else if (fn.kind()==FKind.FlatOpNode) {
              FlatOpNode fon=(FlatOpNode)fn;
              if (fon.getOp().getOp()==Operation.ASSIGN&&fon.getDest().getType().isPtr()) {
                HashSet<TempFlatPair> set=new HashSet<TempFlatPair>();
                if (ttofn.containsKey(fon.getLeft()))
                  set.addAll(ttofn.get(fon.getLeft()));
                if (normalassign)
                  set.add(new TempFlatPair(fon.getDest(), fn));
                ttofn.put(fon.getDest(), set);
                break;
              }
            }

          default:
            //Do kill computation
            TempDescriptor[] writes=fn.writesTemps();
            for(int i=0; i<writes.length; i++) {
              TempDescriptor wtmp=writes[i];
              ttofn.remove(writes[i]);
            }
          }
        }
        if (ttofn!=null) {
          if (!tmptofnset.containsKey(fn)||
              !tmptofnset.get(fn).equals(ttofn)) {
            //enqueue nodes to process
            tmptofnset.put(fn, ttofn);
            for(int i=0; i<fn.numNext(); i++) {
              FlatNode fnext=fn.getNext(i);
              tovisit.add(fnext);
            }
          }
        }
      }
    }
    return tmptofnset;
  }

  /* See what fields and arrays transactions might modify.  We only
   * look at changes to old objects. */

  //Bug fix: original version forget to check if object is new and
  //could be optimized

  public void computeModified(LocalityBinding lb) {
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Set<TempDescriptor>> oldtemps=computeOldTemps(lb);
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
      if (atomictable.get(fn).intValue()>0) {
        Set<TempDescriptor> oldtemp=oldtemps.get(fn);
        switch (fn.kind()) {
        case FKind.FlatSetFieldNode:
          FlatSetFieldNode fsfn=(FlatSetFieldNode) fn;
          if (oldtemp.contains(fsfn.getDst()))
            fields.add(fsfn.getField());
          break;

        case FKind.FlatSetElementNode:
          FlatSetElementNode fsen=(FlatSetElementNode) fn;
          if (oldtemp.contains(fsen.getDst()))
            arrays.add(fsen.getDst().getType());
          break;

        default:
        }
      }
    }
  }


  //Returns a table that maps a flatnode to a set of temporaries
  //This set of temporaries is old (meaning they may point to object
  //allocated before the beginning of the current transaction

  Hashtable<FlatNode, Set<TempDescriptor>> computeOldTemps(LocalityBinding lb) {
    Hashtable<FlatNode, Set<TempDescriptor>> fntooldtmp=new Hashtable<FlatNode, Set<TempDescriptor>>();
    HashSet<FlatNode> discovered=new HashSet<FlatNode>();
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Integer> atomictable=locality.getAtomic(lb);
    Hashtable<FlatNode, Set<TempDescriptor>> livetemps=Liveness.computeLiveTemps(fm,-1);
    tovisit.add(fm);
    discovered.add(fm);

    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      for(int i=0; i<fn.numNext(); i++) {
        FlatNode fnext=fn.getNext(i);
        if (!discovered.contains(fnext)) {
          discovered.add(fnext);
          tovisit.add(fnext);
        }
      }
      HashSet<TempDescriptor> oldtemps=null;
      if (atomictable.get(fn).intValue()!=0) {
        if ((fn.numPrev()>0)&&atomictable.get(fn.getPrev(0)).intValue()==0) {
          //Everything live is old
          Set<TempDescriptor> lives=livetemps.get(fn);
          oldtemps=new HashSet<TempDescriptor>();

          for(Iterator<TempDescriptor> it=lives.iterator(); it.hasNext(); ) {
            TempDescriptor tmp=it.next();
            if (tmp.getType().isPtr()) {
              oldtemps.add(tmp);
            }
          }
        } else {
          oldtemps=new HashSet<TempDescriptor>();
          //Compute union of old temporaries
          for(int i=0; i<fn.numPrev(); i++) {
            Set<TempDescriptor> pset=fntooldtmp.get(fn.getPrev(i));
            if (pset!=null)
              oldtemps.addAll(pset);
          }

          switch (fn.kind()) {
          case FKind.FlatNew:
            oldtemps.removeAll(Arrays.asList(fn.readsTemps()));
            break;

          case FKind.FlatOpNode:
          case FKind.FlatCastNode:
            if (fn.kind()==FKind.FlatCastNode) {
              FlatCastNode fcn=(FlatCastNode)fn;
              if (fcn.getDst().getType().isPtr()) {
                if (oldtemps.contains(fcn.getSrc()))
                  oldtemps.add(fcn.getDst());
                else
                  oldtemps.remove(fcn.getDst());
                break;
              }
            } else if (fn.kind()==FKind.FlatOpNode) {
              FlatOpNode fon=(FlatOpNode)fn;
              if (fon.getOp().getOp()==Operation.ASSIGN&&fon.getDest().getType().isPtr()) {
                if (oldtemps.contains(fon.getLeft()))
                  oldtemps.add(fon.getDest());
                else
                  oldtemps.remove(fon.getDest());
                break;
              }
            }

          default: {
            TempDescriptor[] writes=fn.writesTemps();
            for(int i=0; i<writes.length; i++) {
              TempDescriptor wtemp=writes[i];
              if (wtemp.getType().isPtr())
                oldtemps.add(wtemp);
            }
          }
          }
        }
      }

      if (oldtemps!=null) {
        if (!fntooldtmp.containsKey(fn)||!fntooldtmp.get(fn).equals(oldtemps)) {
          fntooldtmp.put(fn, oldtemps);
          //propagate changes
          for(int i=0; i<fn.numNext(); i++) {
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
  public String toString() {
    return f.toString()+" "+t.toString();
  }
}
