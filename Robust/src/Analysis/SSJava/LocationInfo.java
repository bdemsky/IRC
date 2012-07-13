package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.MethodDescriptor;

public class LocationInfo {

  Map<String, Set<Descriptor>> mapLocNameToDescSet;
  Map<String, String> mapDescSymbolToLocName;
  Map<Descriptor, CompositeLocation> mapDescToInferCompositeLocation;
  MethodDescriptor md;
  ClassDescriptor cd;

  public LocationInfo() {
    mapDescSymbolToLocName = new HashMap<String, String>();
    mapLocNameToDescSet = new HashMap<String, Set<Descriptor>>();
    mapDescToInferCompositeLocation = new HashMap<Descriptor, CompositeLocation>();
  }

  public LocationInfo(ClassDescriptor cd) {
    this.cd = cd;
    this.mapDescSymbolToLocName = new HashMap<String, String>();
  }

  public void mapDescriptorToCompositeLocation(Descriptor desc, CompositeLocation inferLoc) {
    mapDescToInferCompositeLocation.put(desc, inferLoc);
  }

  public void mapDescSymbolToLocName(String descSymbol, String locName) {
    mapDescSymbolToLocName.put(descSymbol, locName);
  }

  public String getLocName(String descSymbol) {
    if (!mapDescSymbolToLocName.containsKey(descSymbol)) {
      mapDescSymbolToLocName.put(descSymbol, descSymbol);
    }
    return mapDescSymbolToLocName.get(descSymbol);
  }

  public void addMappingOfLocNameToDescriptor(String locName, Descriptor desc) {

    // System.out.println("### MAP LocName=" + locName + " to " + desc);

    if (!mapLocNameToDescSet.containsKey(locName)) {
      mapLocNameToDescSet.put(locName, new HashSet<Descriptor>());
    }

    mapLocNameToDescSet.get(locName).add(desc);

  }

  public Set<Descriptor> getFlowNodeSet(String locName) {

    if (!mapLocNameToDescSet.containsKey(locName)) {
      mapLocNameToDescSet.put(locName, new HashSet<Descriptor>());
    }

    return mapLocNameToDescSet.get(locName);
  }

}
