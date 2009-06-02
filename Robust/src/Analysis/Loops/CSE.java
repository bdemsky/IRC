package Analysis.Loops;

import IR.Flat.*;
import IR.TypeUtil;
import IR.Operation;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.TypeDescriptor;
import java.util.Map;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;

public class CSE {
  GlobalFieldType gft;
  TypeUtil typeutil;
  public CSE(GlobalFieldType gft, TypeUtil typeutil) {
    this.gft=gft;
    this.typeutil=typeutil;
  }

  public void doAnalysis(FlatMethod fm) {
    Hashtable<FlatNode,Hashtable<Expression, TempDescriptor>> availexpr=new Hashtable<FlatNode,Hashtable<Expression, TempDescriptor>>();
    HashSet toprocess=new HashSet();
    HashSet discovered=new HashSet();
    toprocess.add(fm);
    discovered.add(fm);
    while(!toprocess.isEmpty()) {
      FlatNode fn=(FlatNode)toprocess.iterator().next();
      toprocess.remove(fn);
      for(int i=0;i<fn.numNext();i++) {
	FlatNode nnext=fn.getNext(i);
	if (!discovered.contains(nnext)) {
	  toprocess.add(nnext);
	  discovered.add(nnext);
	}
      }
      Hashtable<Expression, TempDescriptor> tab=computeIntersection(fn, availexpr);

      //Do kills of expression/variable mappings
      TempDescriptor[] write=fn.writesTemps();
      for(int i=0;i<write.length;i++) {
	if (tab.containsKey(write[i]))
	  tab.remove(write[i]);
      }
      
      switch(fn.kind()) {
      case FKind.FlatAtomicEnterNode:
	{
	  killexpressions(tab, null, null, true);
	  break;
	}
      case FKind.FlatCall:
	{
	  FlatCall fc=(FlatCall) fn;
	  MethodDescriptor md=fc.getMethod();
	  Set<FieldDescriptor> fields=gft.getFields(md);
	  Set<TypeDescriptor> arrays=gft.getArrays(md);
	  killexpressions(tab, fields, arrays, gft.containsAtomic(md)||gft.containsBarrier(md));
	  break;
	}
      case FKind.FlatOpNode:
	{
	  FlatOpNode fon=(FlatOpNode) fn;
	  Expression e=new Expression(fon.getLeft(), fon.getRight(), fon.getOp());
	  tab.put(e, fon.getDest());
	  break;
	}
      case FKind.FlatSetFieldNode:
	{
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  Set<FieldDescriptor> fields=new HashSet<FieldDescriptor>();
	  fields.add(fsfn.getField());
	  killexpressions(tab, fields, null, false);
	  Expression e=new Expression(fsfn.getDst(), fsfn.getField());
	  tab.put(e, fsfn.getSrc());
	  break;
	}
      case FKind.FlatFieldNode:
	{
	  FlatFieldNode ffn=(FlatFieldNode)fn;
	  Expression e=new Expression(ffn.getSrc(), ffn.getField());
	  tab.put(e, ffn.getDst());
	  break;
	}
      case FKind.FlatSetElementNode:
	{
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  Expression e=new Expression(fsen.getDst(),fsen.getIndex());
	  tab.put(e, fsen.getSrc());
	  break;
	}
      case FKind.FlatElementNode:
	{
	  FlatElementNode fen=(FlatElementNode)fn;
	  Expression e=new Expression(fen.getSrc(),fen.getIndex());
	  tab.put(e, fen.getDst());
	  break;
	}
      default:
      }
      
      if (write.length==1) {
	TempDescriptor w=write[0];
	for(Iterator it=tab.entrySet().iterator();it.hasNext();) {
	  Map.Entry m=(Map.Entry)it.next();
	  Expression e=(Expression)m.getKey();
	  if (e.a==w||e.b==w)
	    it.remove();
	}
      }
      if (!availexpr.containsKey(fn)||!availexpr.get(fn).equals(tab)) {
	availexpr.put(fn, tab);
	for(int i=0;i<fn.numNext();i++) {
	  FlatNode nnext=fn.getNext(i);
	  toprocess.add(nnext);
	}
      }
    }

    doOptimize(fm, availexpr);
  }
    
  public void doOptimize(FlatMethod fm, Hashtable<FlatNode,Hashtable<Expression, TempDescriptor>> availexpr) {
    Hashtable<FlatNode, FlatNode> replacetable=new Hashtable<FlatNode, FlatNode>();
    for(Iterator<FlatNode> it=fm.getNodeSet().iterator();it.hasNext();) {
      FlatNode fn=it.next();
      Hashtable<Expression, TempDescriptor> tab=computeIntersection(fn, availexpr);
      switch(fn.kind()) {
      case FKind.FlatOpNode:
	{
	  FlatOpNode fon=(FlatOpNode) fn;
	  Expression e=new Expression(fon.getLeft(), fon.getRight(),fon.getOp());
	  if (tab.containsKey(e)) {
	    TempDescriptor t=tab.get(e);
	    FlatNode newfon=new FlatOpNode(fon.getDest(),t,null,new Operation(Operation.ASSIGN));
	    replacetable.put(fon,newfon);
	  }
	  break;
	}
      case FKind.FlatFieldNode:
	{
	  FlatFieldNode ffn=(FlatFieldNode)fn;
	  Expression e=new Expression(ffn.getSrc(), ffn.getField());
	  if (tab.containsKey(e)) {
	    TempDescriptor t=tab.get(e);
	    FlatNode newfon=new FlatOpNode(ffn.getDst(),t,null,new Operation(Operation.ASSIGN));
	    replacetable.put(ffn,newfon);
	  }
	  break;
	}
      case FKind.FlatElementNode:
	{
	  FlatElementNode fen=(FlatElementNode)fn;
	  Expression e=new Expression(fen.getSrc(),fen.getIndex());
	  if (tab.containsKey(e)) {
	    TempDescriptor t=tab.get(e);
	    FlatNode newfon=new FlatOpNode(fen.getDst(),t,null,new Operation(Operation.ASSIGN));
	    replacetable.put(fen,newfon);
	  }
	  break;
	}
      default: 
      }
    }
    for(Iterator<FlatNode> it=replacetable.keySet().iterator();it.hasNext();) {
      FlatNode fn=it.next();
      FlatNode newfn=replacetable.get(fn);
      fn.replace(newfn);
    }
  }
  
  public Hashtable<Expression, TempDescriptor> computeIntersection(FlatNode fn, Hashtable<FlatNode,Hashtable<Expression, TempDescriptor>> availexpr) {
    Hashtable<Expression, TempDescriptor> tab=new Hashtable<Expression, TempDescriptor>();
    boolean first=true;
    
    //compute intersection
    for(int i=0;i<fn.numPrev();i++) {
      FlatNode prev=fn.getPrev(i);
      if (first) {
	if (availexpr.containsKey(prev)) {
	  tab.putAll(availexpr.get(prev));
	  first=false;
	}
      } else {
	if (availexpr.containsKey(prev)) {
	  Hashtable<Expression, TempDescriptor> table=availexpr.get(prev);
	  for(Iterator mapit=tab.entrySet().iterator();mapit.hasNext();) {
	    Object entry=mapit.next();
	    if (!table.contains(entry))
	      mapit.remove();
	  }
	}
      }
    }
    return tab;
  }

  public void killexpressions(Hashtable<Expression, TempDescriptor> tab, Set<FieldDescriptor> fields, Set<TypeDescriptor> arrays, boolean killall) {
    for(Iterator it=tab.entrySet().iterator();it.hasNext();) {
      Map.Entry m=(Map.Entry)it.next();
      Expression e=(Expression)m.getKey();
      if (killall&&(e.f!=null||e.a!=null))
	it.remove();
      else if (e.f!=null&&fields!=null&&fields.contains(e.f)) 
	it.remove();
      else if ((e.a!=null)&&(arrays!=null)) {
	for(Iterator<TypeDescriptor> arit=arrays.iterator();arit.hasNext();) {
	  TypeDescriptor artd=arit.next();
	  if (typeutil.isSuperorType(artd,e.a.getType())||
	      typeutil.isSuperorType(e.a.getType(),artd)) {
	    it.remove();
	    break;
	  }
	}
      }
    }
  }
}

class Expression {
  Operation op;
  TempDescriptor a;
  TempDescriptor b;
  FieldDescriptor f;
  Expression(TempDescriptor a, TempDescriptor b, Operation op) {
    this.a=a;
    this.b=b;
    this.op=op;
  }
  Expression(TempDescriptor a, FieldDescriptor f) {
    this.a=a;
    this.f=f;
  }
  Expression(TempDescriptor a, TempDescriptor index) {
    this.a=a;
    this.b=index;
  }
  public int hashCode() {
    int h=0;
    h^=a.hashCode();
    if (op!=null)
      h^=op.getOp();
    if (b!=null)
      h^=b.hashCode();
    if (f!=null)
      h^=f.hashCode();
    return h;
  }
  public boolean equals(Object o) {
    Expression e=(Expression)o;
    if (a!=e.a||f!=e.f||b!=e.b)
      return false;
    if (op!=null)
      return op.getOp()==e.op.getOp();
    return true;
  }
}
