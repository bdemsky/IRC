package Analysis.Loops;

import IR.Flat.*;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.FieldDescriptor;
import IR.TypeDescriptor;
import Analysis.CallGraph.*;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

public class GlobalFieldType {
  CallGraph cg;
  State st;
  MethodDescriptor root;
  Hashtable<MethodDescriptor, Set<FieldDescriptor>> fields;
  Hashtable<MethodDescriptor, Set<TypeDescriptor>> arrays;
  Hashtable<MethodDescriptor, Set<FieldDescriptor>> fieldsrd;
  Hashtable<MethodDescriptor, Set<TypeDescriptor>> arraysrd;
  HashSet<MethodDescriptor> containsAtomic;
  HashSet<MethodDescriptor> containsBarrier;

  public GlobalFieldType(CallGraph cg, State st, MethodDescriptor root) {
    this.cg=cg;
    this.st=st;
    this.root=root;
    this.fields=new Hashtable<MethodDescriptor, Set<FieldDescriptor>>();
    this.arrays=new Hashtable<MethodDescriptor, Set<TypeDescriptor>>();
    this.fieldsrd=new Hashtable<MethodDescriptor, Set<FieldDescriptor>>();
    this.arraysrd=new Hashtable<MethodDescriptor, Set<TypeDescriptor>>();
    this.containsAtomic=new HashSet<MethodDescriptor>();
    this.containsBarrier=new HashSet<MethodDescriptor>();
    doAnalysis();
  }
  private void doAnalysis() {
    HashSet toprocess=new HashSet();
    toprocess.add(root);
    HashSet discovered=new HashSet();
    discovered.add(root);
    while(!toprocess.isEmpty()) {
      MethodDescriptor md=(MethodDescriptor)toprocess.iterator().next();
      toprocess.remove(md);
      analyzeMethod(md);
      Set callees=cg.getCalleeSet(md);
      for(Iterator it=callees.iterator(); it.hasNext(); ) {
	MethodDescriptor md2=(MethodDescriptor)it.next();
	if (!discovered.contains(md2)) {
	  discovered.add(md2);
	  toprocess.add(md2);
	}
      }
      if (md.getClassDesc().getSymbol().equals(TypeUtil.ThreadClass)&&
          md.getSymbol().equals("start")&&!md.getModifiers().isStatic()&&
          md.numParameters()==0) {
	//start -> run link
	MethodDescriptor runmd=null;
	for(Iterator methodit=md.getClassDesc().getMethodTable().getSet("run").iterator(); methodit.hasNext(); ) {
	  MethodDescriptor mdrun=(MethodDescriptor) methodit.next();
	  if (mdrun.numParameters()!=0||mdrun.getModifiers().isStatic())
	    continue;
	  runmd=mdrun;
	  break;
	}
	if (runmd!=null) {
	  Set runmethodset=cg.getMethods(runmd);
	  for(Iterator it=runmethodset.iterator(); it.hasNext(); ) {
	    MethodDescriptor md2=(MethodDescriptor)it.next();
	    if (!discovered.contains(md2)) {
	      discovered.add(md2);
	      toprocess.add(md2);
	    }
	  }
	} else throw new Error("Can't find run method");
      }
    }
    boolean changed=true;
    while(changed) {
      changed=false;
      for(Iterator it=discovered.iterator(); it.hasNext(); ) {
	MethodDescriptor md=(MethodDescriptor)it.next();
	Set callees=cg.getCalleeSet(md);
	for(Iterator cit=callees.iterator(); cit.hasNext(); ) {
	  MethodDescriptor md2=(MethodDescriptor)cit.next();
	  if (fields.get(md).addAll(fields.get(md2)))
	    changed=true;
	  if (arrays.get(md).addAll(arrays.get(md2)))
	    changed=true;
	  if (fieldsrd.get(md).addAll(fieldsrd.get(md2)))
	    changed=true;
	  if (arraysrd.get(md).addAll(arraysrd.get(md2)))
	    changed=true;
	  if (containsAtomic.contains(md2)) {
	    if (containsAtomic.add(md))
	      changed=true;
	  }
	  if (containsBarrier.contains(md2)) {
	    if (containsBarrier.add(md))
	      changed=true;
	  }
	}
      }
    }
  }

  public boolean containsAtomic(MethodDescriptor md) {
    return containsAtomic.contains(md);
  }

  public boolean containsBarrier(MethodDescriptor md) {
    return containsBarrier.contains(md);
  }

  public Set<FieldDescriptor> getFields(MethodDescriptor md) {
    return fields.get(md);
  }

  public Set<TypeDescriptor> getArrays(MethodDescriptor md) {
    return arrays.get(md);
  }

  public Set<FieldDescriptor> getFieldsRd(MethodDescriptor md) {
    return fieldsrd.get(md);
  }

  public Set<TypeDescriptor> getArraysRd(MethodDescriptor md) {
    return arraysrd.get(md);
  }

  public boolean containsAtomicAll(MethodDescriptor md) {
    Set methodset=cg.getMethods(md);
    for(Iterator it=methodset.iterator(); it.hasNext(); ) {
      MethodDescriptor md2=(MethodDescriptor)it.next();
      if (containsAtomic.contains(md2))
	return true;
    }
    return false;
  }

  public boolean containsBarrierAll(MethodDescriptor md) {
    Set methodset=cg.getMethods(md);
    for(Iterator it=methodset.iterator(); it.hasNext(); ) {
      MethodDescriptor md2=(MethodDescriptor)it.next();
      if (containsBarrier.contains(md2))
	return true;
    }
    return false;
  }

  public Set<FieldDescriptor> getFieldsAll(MethodDescriptor md) {
    HashSet<FieldDescriptor> s=new HashSet<FieldDescriptor>();
    Set methodset=cg.getMethods(md);
    for(Iterator it=methodset.iterator(); it.hasNext(); ) {
      MethodDescriptor md2=(MethodDescriptor)it.next();
      if (fields.containsKey(md2))
	s.addAll(fields.get(md2));
    }
    return s;
  }

  public Set<TypeDescriptor> getArraysAll(MethodDescriptor md) {
    HashSet<TypeDescriptor> s=new HashSet<TypeDescriptor>();
    Set methodset=cg.getMethods(md);
    for(Iterator it=methodset.iterator(); it.hasNext(); ) {
      MethodDescriptor md2=(MethodDescriptor)it.next();
      if (arrays.containsKey(md2))
	s.addAll(arrays.get(md2));
    }
    return s;
  }

  public Set<FieldDescriptor> getFieldsRdAll(MethodDescriptor md) {
    HashSet<FieldDescriptor> s=new HashSet<FieldDescriptor>();
    Set methodset=cg.getMethods(md);
    for(Iterator it=methodset.iterator(); it.hasNext(); ) {
      MethodDescriptor md2=(MethodDescriptor)it.next();
      if (fieldsrd.containsKey(md2))
	s.addAll(fieldsrd.get(md2));
    }
    return s;
  }

  public Set<TypeDescriptor> getArraysRdAll(MethodDescriptor md) {
    HashSet<TypeDescriptor> s=new HashSet<TypeDescriptor>();
    Set methodset=cg.getMethods(md);
    for(Iterator it=methodset.iterator(); it.hasNext(); ) {
      MethodDescriptor md2=(MethodDescriptor)it.next();
      if (arraysrd.containsKey(md2))
	s.addAll(arraysrd.get(md2));
    }
    return s;
  }

  public void analyzeMethod(MethodDescriptor md) {
    fields.put(md, new HashSet<FieldDescriptor>());
    arrays.put(md, new HashSet<TypeDescriptor>());
    fieldsrd.put(md, new HashSet<FieldDescriptor>());
    arraysrd.put(md, new HashSet<TypeDescriptor>());

    FlatMethod fm=st.getMethodFlat(md);
    for(Iterator it=fm.getNodeSet().iterator(); it.hasNext(); ) {
      FlatNode fn=(FlatNode)it.next();
      if (fn.kind()==FKind.FlatSetElementNode) {
	FlatSetElementNode fsen=(FlatSetElementNode)fn;
	arrays.get(md).add(fsen.getDst().getType());
      } else if (fn.kind()==FKind.FlatElementNode) {
	FlatElementNode fen=(FlatElementNode)fn;
	arraysrd.get(md).add(fen.getSrc().getType());
      } else if (fn.kind()==FKind.FlatSetFieldNode) {
	FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	fields.get(md).add(fsfn.getField());
      } else if (fn.kind()==FKind.FlatFieldNode) {
	FlatFieldNode ffn=(FlatFieldNode)fn;
	fieldsrd.get(md).add(ffn.getField());
      } else if (fn.kind()==FKind.FlatAtomicEnterNode) {
	containsAtomic.add(md);
      } else if (fn.kind()==FKind.FlatCall) {
	MethodDescriptor mdcall=((FlatCall)fn).getMethod();
	if (mdcall.getSymbol().equals("enterBarrier")&&
	    mdcall.getClassDesc().getSymbol().equals("Barrier")) {
	  containsBarrier.add(md);
	  containsBarrier.add(mdcall);
	}
	//treat lock acquire the same as a barrier
	if ((mdcall.getSymbol().equals("MonitorEnter")||mdcall.getSymbol().equals("MonitorExit")||mdcall.getSymbol().equals("wait"))&&
	    mdcall.getClassDesc().getSymbol().equals("Object")) {
	  containsBarrier.add(md);
	  containsBarrier.add(mdcall);
	}
      }
    }
  }
}
