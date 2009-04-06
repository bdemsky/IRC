package IR;
import java.util.*;
import Analysis.Locality.LocalityBinding;
import Analysis.Locality.LocalityAnalysis;


public class Virtual {
  State state;
  LocalityAnalysis locality;
  Hashtable<MethodDescriptor, Integer> methodnumber;
  Hashtable<ClassDescriptor, Integer> classmethodcount;
  Hashtable<LocalityBinding, Integer> localitynumber;

  public int getMethodNumber(MethodDescriptor md) {
    return methodnumber.get(md).intValue();
  }

  public int getMethodCount(ClassDescriptor md) {
    return classmethodcount.get(md).intValue();
  }

  public int getLocalityNumber(LocalityBinding lb) {
    return localitynumber.get(lb).intValue();
  }

  public Virtual(State state, LocalityAnalysis locality) {
    this.state=state;
    this.locality=locality;
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
      if (state.DSM||state.SINGLETM)
	numberLocality(cd);
      else
	numberMethods(cd);
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
      for(Iterator<LocalityBinding> lbit=locality.getClassBindings(cd).iterator(); lbit.hasNext();) {
	LocalityBinding lb=lbit.next();
	MethodDescriptor md=lb.getMethod();
	//Is it a static method or constructor
	if (md.isStatic()||md.getReturnType()==null)
	  continue;

	if (superdesc!=null) {
	  Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
	  boolean foundmatch=false;
	  for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	    MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	    if (md.matches(matchmd)) {
	      Set<LocalityBinding> lbset=locality.getMethodBindings(matchmd);
	      if (lbset!=null)
		for(Iterator<LocalityBinding> suplbit=lbset.iterator(); suplbit.hasNext();) {
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

  private int numberMethods(ClassDescriptor cd) {
    if (classmethodcount.containsKey(cd))
      return classmethodcount.get(cd).intValue();
    ClassDescriptor superdesc=cd.getSuperDesc();
    int start=0;
    if (superdesc!=null)
      start=numberMethods(superdesc);
    for(Iterator it=cd.getMethods(); it.hasNext();) {
      MethodDescriptor md=(MethodDescriptor)it.next();
      if (md.isStatic()||md.getReturnType()==null)
	continue;
      if (superdesc!=null) {
	Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
	boolean foundmatch=false;
	for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	  MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	  if (md.matches(matchmd)) {
	    int num=((Integer)methodnumber.get(matchmd)).intValue();
	    methodnumber.put(md, new Integer(num));
	    foundmatch=true;
	    break;
	  }
	}
	if (!foundmatch)
	  methodnumber.put(md, new Integer(start++));
      } else {
	methodnumber.put(md, new Integer(start++));
      }
    }
    classmethodcount.put(cd, new Integer(start));
    return start;
  }
}

