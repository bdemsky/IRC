package Analysis.Loops;

import IR.MethodDescriptor;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.Operation;
import IR.Flat.*;
import IR.FieldDescriptor;
import java.util.Set;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

public class localCSE {
  GlobalFieldType gft;
  TypeUtil typeutil;
  public localCSE(GlobalFieldType gft, TypeUtil typeutil) {
    this.gft=gft;
    this.typeutil=typeutil;
  }
  int index;

  public Group getGroup(Hashtable<LocalExpression, Group> tab, TempDescriptor t) {
    LocalExpression e=new LocalExpression(t);
    return getGroup(tab, e);
  }
  public Group getGroup(Hashtable<LocalExpression, Group> tab, LocalExpression e) {
    if (tab.containsKey(e))
      return tab.get(e);
    else {
      Group g=new Group(index++);
      g.set.add(e);
      tab.put(e,g);
      return g;
    }
  }
  public TempDescriptor getTemp(Group g) {
    for(Iterator it=g.set.iterator();it.hasNext();) {
      LocalExpression e=(LocalExpression)it.next();
      if (e.t!=null)
	return e.t;
    }
    return null;
  }

  public void doAnalysis(FlatMethod fm) {
    Set nodes=fm.getNodeSet();
    HashSet<FlatNode> toanalyze=new HashSet<FlatNode>();
    for(Iterator it=nodes.iterator();it.hasNext();) {
      FlatNode fn=(FlatNode)it.next();
      if (fn.numPrev()>1)
	toanalyze.add(fn);
    }
    for(Iterator<FlatNode> it=toanalyze.iterator();it.hasNext();) {
      FlatNode fn=it.next();
      Hashtable<LocalExpression, Group> table=new Hashtable<LocalExpression,Group>();
      do {
	index=0;
	switch(fn.kind()) {
	case FKind.FlatOpNode: {
	  FlatOpNode fon=(FlatOpNode)fn;
	  Group left=getGroup(table, fon.getLeft());
	  Group right=getGroup(table, fon.getRight());
	  LocalExpression dst=new LocalExpression(fon.getDest());
	  if (fon.getOp().getOp()==Operation.ASSIGN) {
	    left.set.add(dst);
	    kill(table, fon.getDest());
	    table.put(dst, left);
	  } else {
	    LocalExpression e=new LocalExpression(left, right, fon.getOp());
	    Group g=getGroup(table,e);
	    TempDescriptor td=getTemp(g);
	    if (td!=null) {
	      FlatNode nfon=new FlatOpNode(fon.getDest(),td,null,new Operation(Operation.ASSIGN));
	      fn.replace(nfon);
	    }
	    g.set.add(dst);
	    kill(table, fon.getDest());
	    table.put(dst,g);
	  }
	  break;
	}
	case FKind.FlatLiteralNode: {
	  FlatLiteralNode fln=(FlatLiteralNode)fn;
	  LocalExpression e=new LocalExpression(fln.getValue());
	  Group src=getGroup(table, e);
	  LocalExpression dst=new LocalExpression(fln.getDst());
	  src.set.add(dst);
	  kill(table, fln.getDst());
	  table.put(dst, src);
	  break;
	}
	case FKind.FlatFieldNode: {
	  FlatFieldNode ffn=(FlatFieldNode) fn;
	  Group src=getGroup(table, ffn.getSrc());
	  LocalExpression e=new LocalExpression(src, ffn.getField());
	  Group srcf=getGroup(table, e);
	  LocalExpression dst=new LocalExpression(ffn.getDst());
	  TempDescriptor td=getTemp(srcf);
	  if (td!=null) {
	    FlatOpNode fon=new FlatOpNode(ffn.getDst(),td,null,new Operation(Operation.ASSIGN));
	    fn.replace(fon);
	  }
	  srcf.set.add(dst);
	  kill(table, ffn.getDst());
	  table.put(dst, srcf);
	  break;
	}
	case FKind.FlatElementNode: {
	  FlatElementNode fen=(FlatElementNode) fn;
	  Group src=getGroup(table, fen.getSrc());
	  Group index=getGroup(table, fen.getIndex());
	  LocalExpression e=new LocalExpression(src, fen.getSrc().getType(), index);
	  Group srcf=getGroup(table, e);
	  LocalExpression dst=new LocalExpression(fen.getDst());
	  TempDescriptor td=getTemp(srcf);
	  if (td!=null) {
	    FlatOpNode fon=new FlatOpNode(fen.getDst(),td,null,new Operation(Operation.ASSIGN));
	    fn.replace(fon);
	  }
	  srcf.set.add(dst);
	  kill(table, fen.getDst());
	  table.put(dst, srcf);
	  break;
	}
	case FKind.FlatSetFieldNode: {
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  Group dst=getGroup(table, fsfn.getDst());
	  LocalExpression e=new LocalExpression(dst, fsfn.getField());
	  Group dstf=getGroup(table, e);
	  LocalExpression src=new LocalExpression(fsfn.getSrc());
	  dstf.set.add(src);
	  HashSet<FieldDescriptor> fields=new HashSet<FieldDescriptor>();
	  fields.add(fsfn.getField());
	  kill(table, fields, null, false, false);
	  table.put(src, dstf);
	  break;
	}
	case FKind.FlatSetElementNode: {
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  Group dst=getGroup(table, fsen.getDst());
	  Group index=getGroup(table, fsen.getIndex());
	  LocalExpression e=new LocalExpression(dst, fsen.getDst().getType(), index);
	  Group dstf=getGroup(table, e);
	  LocalExpression src=new LocalExpression(fsen.getSrc());
	  dstf.set.add(src);
	  HashSet<TypeDescriptor> arrays=new HashSet<TypeDescriptor>();
	  arrays.add(fsen.getDst().getType());
	  kill(table, null, arrays, false, false);
	  table.put(src, dstf);
	  break;
	}
	case FKind.FlatCall:{
	  //do side effects
	  FlatCall fc=(FlatCall)fn;
	  MethodDescriptor md=fc.getMethod();
	  Set<FieldDescriptor> fields=gft.getFields(md);
	  Set<TypeDescriptor> arrays=gft.getArrays(md);
	  kill(table, fields, arrays, gft.containsAtomic(md), gft.containsBarrier(md));
	}
	default: {
	  TempDescriptor[] writes=fn.writesTemps();
	  for(int i=0;i<writes.length;i++) {
	    kill(table,writes[i]);
	  }
	}
	}
      } while(fn.numPrev()==1);
    }
  }
  public void kill(Hashtable<LocalExpression, Group> tab, Set<FieldDescriptor> fields, Set<TypeDescriptor> arrays, boolean isAtomic, boolean isBarrier) {
    Set<LocalExpression> eset=tab.keySet();
    for(Iterator<LocalExpression> it=eset.iterator();it.hasNext();) {
      LocalExpression e=it.next();
      if (isBarrier) {
	//make Barriers kill everything
	it.remove();
      } else if (isAtomic&&(e.td!=null||e.f!=null)) {
	Group g=tab.get(e);
	g.set.remove(e);
	it.remove();
      } else if (e.td!=null) {
	//have array
	TypeDescriptor artd=e.td;
	for(Iterator<TypeDescriptor> arit=arrays.iterator();arit.hasNext();) {
	  TypeDescriptor td=arit.next();
	  if (typeutil.isSuperorType(artd,td)||
	      typeutil.isSuperorType(td,artd)) {
	    Group g=tab.get(e);
	    g.set.remove(e);
	    it.remove();
	    break;
	  }
	}
      } else if (e.f!=null) {
	if (fields.contains(e.f)) {
	  Group g=tab.get(e);
	  g.set.remove(e);
	  it.remove();
	}
      }
    }
  }
  public void kill(Hashtable<LocalExpression, Group> tab, TempDescriptor t) {
    LocalExpression e=new LocalExpression(t);
    Group g=tab.get(e);
    if (g!=null) {
      tab.remove(e);
      g.set.remove(e);
    }
  }
}

class Group {
  HashSet set;
  int i;
  Group(int i) {
    set=new HashSet();
    this.i=i;
  }
  public int hashCode() {
    return i;
  }
  public boolean equals(Object o) {
    Group g=(Group)o;
    return i==g.i;
  }
}

class LocalExpression {
  Operation op;
  Object obj;
  Group a;
  Group b;
  TempDescriptor t;
  FieldDescriptor f;
  TypeDescriptor td;
  LocalExpression(TempDescriptor t) {
    this.t=t;
  }
  LocalExpression(Object o) {
    this.obj=o;
  }
  LocalExpression(Group a, Group b, Operation op) {
    this.a=a;
    this.b=b;
    this.op=op;
  }
  LocalExpression(Group a, FieldDescriptor f) {
    this.a=a;
    this.f=f;
  }
  LocalExpression(Group a, TypeDescriptor td, Group index) {
    this.a=a;
    this.td=td;
    this.b=index;
  }
  public int hashCode() {
    int h=0;
    if (td!=null)
      h^=td.hashCode();
    if (t!=null)
      h^=t.hashCode();
    if (a!=null)
      h^=a.hashCode();
    if (op!=null)
      h^=op.getOp();
    if (b!=null)
      h^=b.hashCode();
    if (f!=null)
      h^=f.hashCode();
    if (obj!=null)
      h^=obj.hashCode();
    return h;
  }
  public static boolean equiv(Object a, Object b) {
    if (a!=null)
      return a.equals(b);
    else
      return b==null;
  }

  public boolean equals(Object o) {
    LocalExpression e=(LocalExpression)o;
    if (!(equiv(a,e.a)&&equiv(f,e.f)&&equiv(b,e.b)&&
	  equiv(td,e.td)&&equiv(this.obj,e.obj)))
      return false;
    if (op!=null)
      return op.getOp()==e.op.getOp();
    else if (e.op!=null)
      return false;
    return true;
  }
}