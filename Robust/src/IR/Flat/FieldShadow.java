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
    
    Iterator it_sifs = cd.getSuperInterfaces();
    while(it_sifs.hasNext()) {
      ClassDescriptor sif = (ClassDescriptor)it_sifs.next();
      if (!namemap.containsKey(sif))
        handleClass(sif, state, namemap);
    }
    
    HashMap<String, Integer> supermap=cd.getSuperDesc()!=null?namemap.get(cd.getSuperDesc()):new HashMap<String, Integer>();
    
    Vector<HashMap<String, Integer>> superifmaps = new Vector<HashMap<String, Integer>>();
    it_sifs = cd.getSuperInterfaces();
    while(it_sifs.hasNext()) {
      ClassDescriptor sif = (ClassDescriptor)it_sifs.next();
      superifmaps.addElement(namemap.get(sif));
    }
    
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
        // the fields in interfaces are defaultely static & final, so do not need to 
        // check them, they will always have the interface name as prefix
	fieldmap.put(fd.getSymbol(), new Integer(0));
      }
    }
  }
}