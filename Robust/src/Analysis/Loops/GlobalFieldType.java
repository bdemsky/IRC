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
  HashSet<MethodDescriptor> containsAtomic;
  HashSet<MethodDescriptor> containsBarrier;
  
  public GlobalFieldType(CallGraph cg, State st, MethodDescriptor root) {
    this.cg=cg;
    this.st=st;
    this.root=root;
    this.fields=new Hashtable<MethodDescriptor, Set<FieldDescriptor>>();
    this.arrays=new Hashtable<MethodDescriptor, Set<TypeDescriptor>>();
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
      for(Iterator it=callees.iterator();it.hasNext();) {
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
	for(Iterator methodit=md.getClassDesc().getMethodTable().getSet("run").iterator(); methodit.hasNext();) {
	  MethodDescriptor mdrun=(MethodDescriptor) methodit.next();
	  if (mdrun.numParameters()!=0||mdrun.getModifiers().isStatic())
	    continue;
	  runmd=mdrun;
	  break;
	}
	if (runmd!=null) {
	  Set runmethodset=cg.getMethods(runmd);
	  for(Iterator it=runmethodset.iterator();it.hasNext();) {
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
      } else if (fn.kind()==FKind.FlatCall) {
	MethodDescriptor mdcall=((FlatCall)fn).getMethod();
	if (mdcall.getSymbol().equals("enterBarrier")&&
	    mdcall.getClassDesc().getSymbol().equals("Barrier")) {
	  containsBarrier.add(md);
	  containsBarrier.add(mdcall);
	}
      }
    }
  }
}
