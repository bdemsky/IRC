package IR.Flat;
import IR.*;
import java.util.*;

public class FieldShadow {
  public static void handleFieldShadow(State state) {
    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
    HashMap<ClassDescriptor, HashMap<String,Integer>> namemap=new HashMap<ClassDescriptor,HashMap<String, Integer>>();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      handleClass(cd, state, namemap);
    }
  }
  
  private static void handleClass(ClassDescriptor cd, State state, HashMap<ClassDescriptor, HashMap<String, Integer>> namemap) {
    if (cd.getSuperDesc()!=null&&!namemap.containsKey(cd.getSuperDesc()))
      handleClass(cd.getSuperDesc(), state, namemap);
    HashMap<String, Integer> supermap=cd.getSuperDesc()!=null?namemap.get(cd.getSuperDesc()):new HashMap<String, Integer>();
    
    HashMap<String, Integer> fieldmap=new HashMap<String, Integer>();
    namemap.put(cd, fieldmap);
    
    for(Iterator fieldit=cd.getFields();fieldit.hasNext();) {
      FieldDescriptor fd=(FieldDescriptor)fieldit.next();
      if (supermap.containsKey(fd.getSymbol())) {
	Integer oldint=supermap.get(fd.getSymbol());
	int newint=oldint.intValue()+1;
	fieldmap.put(fd.getSymbol(), new Integer(newint));
	fd.changeSafeSymbol(newint);
      } else {
	fieldmap.put(fd.getSymbol(), new Integer(0));
      }
    }
  }
}