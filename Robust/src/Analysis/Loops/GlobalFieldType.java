package Analysis.Loops;

import IR.Flat.*;
import IR.State;
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
  HashSet<MethodDescriptor> containsAtomic;
  
  public GlobalFieldType(CallGraph cg, State st, MethodDescriptor root) {
    this.cg=cg;
    this.st=st;
    this.root=root;
    this.fields=new Hashtable<MethodDescriptor, Set<FieldDescriptor>>();
    this.arrays=new Hashtable<MethodDescriptor, Set<TypeDescriptor>>();
    this.containsAtomic=new HashSet<MethodDescriptor>();
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
      for(Iterator it=callees.iterator();it.hasNext();) {
	MethodDescriptor md2=(MethodDescriptor)it.next();
	if (!discovered.contains(md2)) {
	  discovered.add(md2);
	  toprocess.add(md2);
	}
      }
    }
    boolean changed=true;
    while(changed) {
      changed=false;
      for(Iterator it=discovered.iterator();it.hasNext();) {
	MethodDescriptor md=(MethodDescriptor)it.next();
	Set callees=cg.getCalleeSet(md);
	for(Iterator cit=callees.iterator();cit.hasNext();) {
	  MethodDescriptor md2=(MethodDescriptor)cit.next();
	  if (fields.get(md).addAll(fields.get(md2)))
	    changed=true;
	  if (arrays.get(md).addAll(arrays.get(md2)))
	    changed=true;
	  if (containsAtomic.contains(md2)) {
	    if (containsAtomic.add(md))
	      changed=true;
	  }
	}
      }
    }
  }

  public boolean containsAtomic(MethodDescriptor md) {
    return containsAtomic.contains(md);
  }

  public Set<FieldDescriptor> getFields(MethodDescriptor md) {
    return fields.get(md);
  }

  public Set<TypeDescriptor> getArrays(MethodDescriptor md) {
    return arrays.get(md);
  }

  public void analyzeMethod(MethodDescriptor md) {
    fields.put(md, new HashSet<FieldDescriptor>());
    arrays.put(md, new HashSet<TypeDescriptor>());
    
    FlatMethod fm=st.getMethodFlat(md);
    for(Iterator it=fm.getNodeSet().iterator();it.hasNext();) {
      FlatNode fn=(FlatNode)it.next();
      if (fn.kind()==FKind.FlatSetElementNode) {
	FlatSetElementNode fsen=(FlatSetElementNode)fn;
	arrays.get(md).add(fsen.getDst().getType());
      } else if (fn.kind()==FKind.FlatSetFieldNode) {
	FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	fields.get(md).add(fsfn.getField());
      } else if (fn.kind()==FKind.FlatAtomicEnterNode) {
	containsAtomic.add(md);
      }
    }
  }
}