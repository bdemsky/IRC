package IR;
import java.util.*;

import Analysis.Locality.LocalityBinding;
import Analysis.Locality.LocalityAnalysis;
import Analysis.CallGraph.CallGraph;

public class Virtual {
  State state;
  LocalityAnalysis locality;
  Hashtable<MethodDescriptor, Integer> methodnumber;
  Hashtable<ClassDescriptor, Integer> classmethodcount;
  Hashtable<LocalityBinding, Integer> localitynumber;
  CallGraph callgraph;

  // for interfaces
  int if_starts;
  SymbolTable if_methods;

  public Integer getMethodNumber(MethodDescriptor md) {
    return methodnumber.get(md);
  }

  public int getMethodCount(ClassDescriptor md) {
    return classmethodcount.get(md).intValue();
  }

  public int getLocalityNumber(LocalityBinding lb) {
    return localitynumber.get(lb).intValue();
  }

  public Virtual(State state, LocalityAnalysis locality, CallGraph callgraph) {
    this.state=state;
    this.locality=locality;
    this.if_starts = 0;
    this.if_methods = new SymbolTable();
    this.callgraph=callgraph;
    classmethodcount=new Hashtable<ClassDescriptor, Integer>();
    if (state.DSM||state.SINGLETM)
      localitynumber=new Hashtable<LocalityBinding, Integer>();
    else
      methodnumber=new Hashtable<MethodDescriptor, Integer>();
    doAnalysis();
  }

  private void doAnalysis() {
    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      numberMethodsIF(cd);
    }
    classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      if (state.DSM||state.SINGLETM)
        numberLocality(cd);
      else
        numberMethods(cd);
    }
    classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      if(!cd.isInterface()) {
        int count = classmethodcount.get(cd).intValue();
        classmethodcount.put(cd, new Integer(count+this.if_starts));
      }
    }
  }

  private int numberLocality(ClassDescriptor cd) {
    if (classmethodcount.containsKey(cd))
      return classmethodcount.get(cd).intValue();
    ClassDescriptor superdesc=cd.getSuperDesc();
    int start=0;
    if (superdesc!=null)
      start=numberLocality(superdesc);

    if (locality.getClassBindings(cd)!=null)
      for(Iterator<LocalityBinding> lbit=locality.getClassBindings(cd).iterator(); lbit.hasNext(); ) {
        LocalityBinding lb=lbit.next();
        MethodDescriptor md=lb.getMethod();
        //Is it a static method or constructor
        if (md.isStatic()||md.getReturnType()==null)
          continue;

        if (superdesc!=null) {
          Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
          boolean foundmatch=false;
          for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
            MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
            if (md.matches(matchmd)) {
              Set<LocalityBinding> lbset=locality.getMethodBindings(matchmd);
              if (lbset!=null)
                for(Iterator<LocalityBinding> suplbit=lbset.iterator(); suplbit.hasNext(); ) {
                  LocalityBinding suplb=suplbit.next();
                  if (lb.contextMatches(suplb)) {
                    foundmatch=true;
                    localitynumber.put(lb, localitynumber.get(suplb));
                    break;
                  }
                }
              break;
            }
          }
          if (!foundmatch)
            localitynumber.put(lb, new Integer(start++));
        } else {
          localitynumber.put(lb, new Integer(start++));
        }
      }
    classmethodcount.put(cd, new Integer(start));
    return start;
  }

  private int numberMethodsIF(ClassDescriptor cd) {
    if(!cd.isInterface()) {
      return 0;
    }
    int mnum = 0;
    if (classmethodcount.containsKey(cd))
      return classmethodcount.get(cd).intValue();
    // check the inherited interfaces
    Iterator it_sifs = cd.getSuperInterfaces();
    while(it_sifs.hasNext()) {
      ClassDescriptor superif = (ClassDescriptor)it_sifs.next();
      mnum += numberMethodsIF(superif);
    }
    for(Iterator it=cd.getMethods(); it.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)it.next();
      if (md.isStatic()||md.getReturnType()==null)
        continue;

      if (!callgraph.isCallable(md)&&!callgraph.isCalled(md))
        continue;
      boolean foundmatch=false;
      // check if there is a matched method that has been assigned method num
      Set possiblematches_if = if_methods.getSet(md.getSymbol());
      for(Iterator matchit=possiblematches_if.iterator(); matchit.hasNext(); ) {
        MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
        if (md.matches(matchmd)) {
          int num=methodnumber.get(matchmd);
          methodnumber.put(md, new Integer(num));
          foundmatch=true;
          break;
        }
      }
      if (!foundmatch) {
        methodnumber.put(md, new Integer(if_starts++));
        if_methods.add(md);
        mnum++;
      }
    }
    classmethodcount.put(cd, new Integer(mnum));
    return mnum;
  }

  private int numberMethods(ClassDescriptor cd) {
    if (classmethodcount.containsKey(cd))
      return classmethodcount.get(cd).intValue();
    ClassDescriptor superdesc=cd.getSuperDesc();
    int start=if_starts;
    int mnum = 0;
    if (superdesc!=null) {
      mnum = numberMethods(superdesc);
      start += mnum;
    }
methodit:
    for(Iterator it=cd.getMethods(); it.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)it.next();
      if (md.isStatic()||md.getReturnType()==null)
        continue;
      if (!callgraph.isCallable(md)&&!callgraph.isCalled(md))
        continue;
      // check if there is a matched method in methods defined in interfaces
      Set possiblematches_if=if_methods.getSet(md.getSymbol());
      for(Iterator matchit=possiblematches_if.iterator(); matchit.hasNext(); ) {
        MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
        if (md.matches(matchmd)) {
          int num;
          if (!methodnumber.containsKey(matchmd)) {
            num=start++;
            mnum++;
            methodnumber.put(matchmd,num);
          } else
            num = methodnumber.get(matchmd);
          methodnumber.put(md, new Integer(num));
          continue methodit;
        }
      }
      if (superdesc!=null) {
        Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
        for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
          MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
          if (md.matches(matchmd)) {
            int num;
            if (!methodnumber.containsKey(matchmd)) {
              num=start++;
              mnum++;
              methodnumber.put(matchmd,num);
            } else
              num = methodnumber.get(matchmd);
            methodnumber.put(md, new Integer(num));
            continue methodit;
          }
        }
      }

      methodnumber.put(md, new Integer(start++));
      mnum++;
    }
    classmethodcount.put(cd, new Integer(mnum));
    return mnum;
  }
}

