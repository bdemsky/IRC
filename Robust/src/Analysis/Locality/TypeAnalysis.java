package Analysis.Locality;

import IR.Flat.*;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Hashtable;
import Analysis.CallGraph.CallGraph;
import IR.State;
import IR.TypeUtil;
import IR.Operation;
import IR.TypeDescriptor;
import IR.MethodDescriptor;
import IR.FieldDescriptor;

public class TypeAnalysis {
  /* This analysis is essentially a dataflow analysis.
   */
  LocalityAnalysis locality;
  State state;
  TypeUtil typeutil;
  CallGraph cg;
  Hashtable<TypeDescriptor, Set<TypeDescriptor>> map;
  HashSet<TypeDescriptor> roottypes;
  Hashtable<TypeDescriptor, Set<TypeDescriptor>> transmap;
  Hashtable<TypeDescriptor, Set<TypeDescriptor>> namemap;
  
  public TypeAnalysis(LocalityAnalysis locality, State state, TypeUtil typeutil, CallGraph cg) {
    this.state=state;
    this.locality=locality;
    this.typeutil=typeutil;
    this.cg=cg;
    map=new Hashtable<TypeDescriptor, Set<TypeDescriptor>>();
    transmap=new Hashtable<TypeDescriptor, Set<TypeDescriptor>>();
    namemap=new Hashtable<TypeDescriptor, Set<TypeDescriptor>>();
    roottypes=new HashSet<TypeDescriptor>();
    doAnalysis();
  }
  
  /* We use locality bindings to get calleable methods.  This could be
   * changed to use the callgraph starting from the main method. */

  void doAnalysis() {
    Set<LocalityBinding> localityset=locality.getLocalityBindings();
    for(Iterator<LocalityBinding> lb=localityset.iterator();lb.hasNext();) {
      computeTypes(lb.next().getMethod());
    }
    computeTrans();
    computeOtherNames();
  }
  
  void computeOtherNames() {
    for(Iterator<TypeDescriptor> it=transmap.keySet().iterator();it.hasNext();) {
      TypeDescriptor td=it.next();
      Set<TypeDescriptor> set=transmap.get(td);
      for(Iterator<TypeDescriptor> it2=set.iterator();it2.hasNext();) {
	TypeDescriptor type=it2.next();
	if (!namemap.containsKey(type))
	  namemap.put(type, new HashSet<TypeDescriptor>());
	namemap.get(type).addAll(set);
      }
    }
  }
  
  void computeTrans() {
    //Idea: for each type we want to know all of the possible types it could be called
    for(Iterator<TypeDescriptor> it=roottypes.iterator();it.hasNext();) {
      TypeDescriptor td=it.next();
      HashSet<TypeDescriptor> tovisit=new HashSet<TypeDescriptor>();
      transmap.put(td, new HashSet<TypeDescriptor>());
      tovisit.add(td);
      transmap.get(td).add(td);
      
      while(!tovisit.isEmpty()) {
	TypeDescriptor type=tovisit.iterator().next();
	tovisit.remove(type);
	//Is type a supertype of td...if not skip along
	if (!typeutil.isSuperorType(type,td))
	  continue;
	//Check if we have seen it before
	if (!transmap.get(td).contains(type)) {
	  //If not, add to set and continue processing
	  transmap.get(td).add(type);
	  tovisit.add(type);
	}
      }
    }
  }
  
  public Set<TypeDescriptor> expand(TypeDescriptor td) {
    return namemap.get(td);
  }

  public Set<TypeDescriptor> expandSet(Set<TypeDescriptor> tdset) {
    HashSet<TypeDescriptor> expandedSet=new HashSet<TypeDescriptor>();
    for(Iterator<TypeDescriptor> it=tdset.iterator();it.hasNext();) {
      TypeDescriptor td=it.next();
      expandedSet.addAll(expand(td));
    }
    return expandedSet;
  }

  public boolean couldAlias(TypeDescriptor td1, TypeDescriptor td2) {
    return namemap.get(td1).contains(td2);
  }

  public void addMapping(TypeDescriptor src, TypeDescriptor dst) {
    if (!map.containsKey(src))
      map.put(src, new HashSet<TypeDescriptor>());
    map.get(src).add(dst);
  }

  void computeTypes(MethodDescriptor md) {
    FlatMethod fm=state.getMethodFlat(md);
    for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      switch(fn.kind()) {
      case FKind.FlatOpNode: {
	FlatOpNode fon=(FlatOpNode)fn;
	if(fon.getOp().getOp()==Operation.ASSIGN) {
	  addMapping(fon.getLeft().getType(),fon.getDest().getType());
	}
	break;
      }
      case FKind.FlatNew: {
	FlatNew fnew=(FlatNew)fn;
	roottypes.add(fnew.getType());
	break;
      }
      case FKind.FlatCastNode: {
	FlatCastNode fcn=(FlatCastNode)fn;
	addMapping(fcn.getSrc().getType(), fcn.getDst().getType());
	break;
      }
      case FKind.FlatFieldNode: {
	FlatFieldNode ffn=(FlatFieldNode)fn;
	addMapping(ffn.getField().getType(), ffn.getDst().getType());
	break;
      }
      case FKind.FlatSetFieldNode: {
	FlatSetFieldNode fsfn=(FlatSetFieldNode) fn;
	addMapping(fsfn.getSrc().getType(), fsfn.getField().getType());
	break;
      }
      case FKind.FlatElementNode: {
	FlatElementNode fen=(FlatElementNode)fn;
	addMapping(fen.getSrc().getType().dereference(), fen.getDst().getType());
	break;
      }
      case FKind.FlatSetElementNode: {
	FlatSetElementNode fsen=(FlatSetElementNode)fn;
	addMapping(fsen.getSrc().getType(), fsen.getDst().getType().dereference());
	break;
      }
      case FKind.FlatCall: {
	FlatCall fc=(FlatCall)fn;
	if (fc.getReturnTemp()!=null) {
	  addMapping(fc.getMethod().getReturnType(), fc.getReturnTemp().getType());
	}
	MethodDescriptor callmd=fc.getMethod();
	if (fc.getThis()!=null) {
	  //complicated...need to deal with virtual dispatch here
	  Set methods=cg.getMethods(callmd);
	  for(Iterator mdit=methods.iterator();mdit.hasNext();) {
	    MethodDescriptor md2=(MethodDescriptor)mdit.next();
	    if (fc.getThis()!=null) {
	      TypeDescriptor ttype=new TypeDescriptor(md2.getClassDesc());
	      if (!typeutil.isSuperorType(fc.getThis().getType(),ttype)&&
		  !typeutil.isSuperorType(ttype,fc.getThis().getType()))
		continue;
	      addMapping(fc.getThis().getType(), ttype);
	    }
	  }
	}
	for(int i=0;i<fc.numArgs();i++) {
	  TempDescriptor arg=fc.getArg(i);
	  TypeDescriptor ptype=callmd.getParamType(i);
	  addMapping(arg.getType(), ptype);
	}
	break;
      }
	//both inputs and output
      case FKind.FlatReturnNode: {
	FlatReturnNode frn=(FlatReturnNode) fn;
	if (frn.getReturnTemp()!=null)
	  addMapping(frn.getReturnTemp().getType(), md.getReturnType());
      }
      }
    }
  }

}
