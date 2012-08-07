package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.MethodDescriptor;
import Util.Pair;

public class LocationInfo {

  Map<String, Set<Descriptor>> mapLocSymbolToDescSet;
  Map<String, Set<Pair<Descriptor, Descriptor>>> mapLocSymbolToRelatedInferLocSet;
  Map<Descriptor, CompositeLocation> mapDescToInferCompositeLocation;
  MethodDescriptor md;
  ClassDescriptor cd;

  public LocationInfo() {
    mapDescToInferCompositeLocation = new HashMap<Descriptor, CompositeLocation>();
    mapLocSymbolToDescSet = new HashMap<String, Set<Descriptor>>();
    mapLocSymbolToRelatedInferLocSet = new HashMap<String, Set<Pair<Descriptor, Descriptor>>>();
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

  public void addMapLocSymbolToRelatedInferLoc(String locSymbol, Descriptor enclosingDesc,
      Descriptor desc) {
    if (!mapLocSymbolToRelatedInferLocSet.containsKey(locSymbol)) {
      mapLocSymbolToRelatedInferLocSet.put(locSymbol, new HashSet<Pair<Descriptor, Descriptor>>());
    }
    mapLocSymbolToRelatedInferLocSet.get(locSymbol).add(
        new Pair<Descriptor, Descriptor>(enclosingDesc, desc));
  }

  public Set<Pair<Descriptor, Descriptor>> getRelatedInferLocSet(String locSymbol) {
    return mapLocSymbolToRelatedInferLocSet.get(locSymbol);
  }

  public void mapDescriptorToLocation(Descriptor desc, CompositeLocation inferLoc) {
    mapDescToInferCompositeLocation.put(desc, inferLoc);
  }

  public CompositeLocation getInferLocation(Descriptor desc) {
    if (!mapDescToInferCompositeLocation.containsKey(desc)) {
      CompositeLocation newInferLoc = new CompositeLocation();
      Location loc;
      Descriptor enclosingDesc;
      if (md != null) {
        // method lattice
        enclosingDesc = md;
      } else {
        enclosingDesc = cd;
      }
      loc = new Location(enclosingDesc, desc.getSymbol());

      newInferLoc.addLocation(loc);
      mapDescToInferCompositeLocation.put(desc, newInferLoc);
      addMapLocSymbolToDescSet(desc.getSymbol(), desc);
      addMapLocSymbolToRelatedInferLoc(desc.getSymbol(), enclosingDesc, desc);
    }
    return mapDescToInferCompositeLocation.get(desc);
  }

  public void addMapLocSymbolToDescSet(String locSymbol, Descriptor desc) {
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

  public void removeRelatedInferLocSet(String oldLocSymbol, String newSharedLoc) {
    Set<Descriptor> descSet = getDescSet(oldLocSymbol);
    getDescSet(newSharedLoc).addAll(descSet);
    mapLocSymbolToDescSet.remove(oldLocSymbol);
    mapLocSymbolToRelatedInferLocSet.remove(oldLocSymbol);
  }

}
