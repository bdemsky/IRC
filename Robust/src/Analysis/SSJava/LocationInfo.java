package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.MethodDescriptor;

public class LocationInfo {

  // Map<Descriptor, String> mapDescToLocSymbol;
  Map<String, Set<Descriptor>> mapLocSymbolToDescSet;

  Map<Descriptor, CompositeLocation> mapDescToInferCompositeLocation;
  MethodDescriptor md;
  ClassDescriptor cd;

  public LocationInfo() {
    mapDescToInferCompositeLocation = new HashMap<Descriptor, CompositeLocation>();
    mapLocSymbolToDescSet = new HashMap<String, Set<Descriptor>>();
  }

  public LocationInfo(ClassDescriptor cd) {
    this();
    this.cd = cd;
  }

  public Map<String, Set<Descriptor>> getMapLocSymbolToDescSet() {
    return mapLocSymbolToDescSet;
  }

  public Map<Descriptor, CompositeLocation> getMapDescToInferLocation() {
    return mapDescToInferCompositeLocation;
  }

  public void mapDescriptorToLocation(Descriptor desc, CompositeLocation inferLoc) {
    mapDescToInferCompositeLocation.put(desc, inferLoc);
  }

  // public void mapDescSymbolToLocName(String descSymbol, String locName) {
  // mapDescSymbolToLocName.put(descSymbol, locName);
  // }

  public CompositeLocation getInferLocation(Descriptor desc) {
    if (!mapDescToInferCompositeLocation.containsKey(desc)) {
      CompositeLocation newInferLoc = new CompositeLocation();
      Location loc;
      if (md != null) {
        // method lattice
        loc = new Location(md, desc.getSymbol());
      } else {
        loc = new Location(cd, desc.getSymbol());
      }
      newInferLoc.addLocation(loc);
      mapDescToInferCompositeLocation.put(desc, newInferLoc);
      addMapLocSymbolToDescSet(desc.getSymbol(), desc);
    }
    return mapDescToInferCompositeLocation.get(desc);
  }

  public void addMapLocSymbolToDescSet(String locSymbol, Descriptor desc) {
    System.out.println("mapLocSymbolToDescSet=" + mapLocSymbolToDescSet);
    if (!mapLocSymbolToDescSet.containsKey(locSymbol)) {
      mapLocSymbolToDescSet.put(locSymbol, new HashSet<Descriptor>());
    }
    mapLocSymbolToDescSet.get(locSymbol).add(desc);
  }

  public Location getFieldInferLocation(Descriptor desc) {
    return getInferLocation(desc).get(0);
  }

  public Set<Descriptor> getDescSet(String locSymbol) {
    if (!mapLocSymbolToDescSet.containsKey(locSymbol)) {
      mapLocSymbolToDescSet.put(locSymbol, new HashSet<Descriptor>());
    }
    return mapLocSymbolToDescSet.get(locSymbol);
  }

  public void mergeMapping(String oldLocSymbol, String newSharedLoc) {
    Set<Descriptor> descSet = getDescSet(oldLocSymbol);
    getDescSet(newSharedLoc).addAll(descSet);
    mapLocSymbolToDescSet.remove(oldLocSymbol);

    Set<Descriptor> keySet = mapDescToInferCompositeLocation.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Descriptor key = (Descriptor) iterator.next();
      CompositeLocation inferLoc = getInferLocation(key);

      CompositeLocation newInferLoc = new CompositeLocation();
      if (inferLoc.getSize() > 1) {
        // local variable has a composite location [refLoc.inferedLoc]

        Location oldLoc = inferLoc.get(inferLoc.getSize() - 1);
        // oldLoc corresponds to infered loc.

        if (oldLoc.getLocIdentifier().equals(oldLocSymbol)) {
          for (int i = 0; i < inferLoc.getSize() - 1; i++) {
            Location loc = inferLoc.get(i);
            newInferLoc.addLocation(loc);
          }
          Location newLoc = new Location(oldLoc.getDescriptor(), newSharedLoc);
          newInferLoc.addLocation(newLoc);
          mapDescriptorToLocation(key, newInferLoc);
        }
        // else {
        // return;
        // }
      } else {
        // local var has a local location
        Location oldLoc = inferLoc.get(0);
        if (oldLoc.getLocIdentifier().equals(oldLocSymbol)) {
          Location newLoc = new Location(oldLoc.getDescriptor(), newSharedLoc);
          newInferLoc.addLocation(newLoc);
          mapDescriptorToLocation(key, newInferLoc);
        }
        // else {
        // return;
        // }
      }

    }
  }

}
