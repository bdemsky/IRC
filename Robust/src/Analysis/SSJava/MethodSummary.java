package Analysis.SSJava;

import java.util.HashMap;
import java.util.Map;

import IR.Descriptor;
import IR.MethodDescriptor;

public class MethodSummary extends LocationSummary {

  MethodDescriptor md;

  String thisLocName;
  String globalLocName;

  CompositeLocation pcLoc;
  CompositeLocation returnLoc;

  Map<Integer, CompositeLocation> mapParamIdxToInferLoc;
  Map<Descriptor, CompositeLocation> mapVarDescToInferCompositeLocation;

  boolean hasGlobalAccess;

  public MethodSummary(MethodDescriptor md) {
    this.md = md;
    this.pcLoc = new CompositeLocation(new Location(md, Location.TOP));
    this.mapParamIdxToInferLoc = new HashMap<Integer, CompositeLocation>();
    this.mapVarDescToInferCompositeLocation = new HashMap<Descriptor, CompositeLocation>();
    this.thisLocName = "this";
    this.globalLocName = "GLOBAL";
    this.hasGlobalAccess = false;
  }

  public Map<Descriptor, CompositeLocation> getMapVarDescToInferCompositeLocation() {
    return mapVarDescToInferCompositeLocation;
  }

  public void addMapVarNameToInferCompLoc(Descriptor varDesc, CompositeLocation inferLoc) {
    mapVarDescToInferCompositeLocation.put(varDesc, inferLoc);
  }

  public CompositeLocation getInferLocation(Descriptor varDesc) {
    return mapVarDescToInferCompositeLocation.get(varDesc);
    // if (mapVarNameToInferCompositeLocation.containsKey(varName)) {
    // // it already has a composite location assignment.
    // return mapVarNameToInferCompositeLocation.get(varName);
    // } else {
    // String locName = getLocationName(varName);
    // return new CompositeLocation(new Location(md, locName));
    // }
  }

  public void addMapParamIdxToInferLoc(int paramIdx, CompositeLocation inferLoc) {
    mapParamIdxToInferLoc.put(paramIdx, inferLoc);
  }

  public Map<Integer, CompositeLocation> getMapParamIdxToInferLoc() {
    return mapParamIdxToInferLoc;
  }

  public void setPCLoc(CompositeLocation in) {
    this.pcLoc = in;
  }

  public CompositeLocation getPCLoc() {
    return pcLoc;
  }

  public void setRETURNLoc(CompositeLocation in) {
    this.returnLoc = in;
  }

  public CompositeLocation getRETURNLoc() {
    return returnLoc;
  }

  public void setThisLocName(String name) {
    this.thisLocName = name;
  }

  public String getThisLocName() {
    return thisLocName;
  }

  public void setGlobalLocName(String name) {
    this.globalLocName = name;
  }

  public String getGlobalLocName() {
    return globalLocName;
  }

  public void setHasGlobalAccess() {
    this.hasGlobalAccess = true;
  }

  public boolean hasGlobalAccess() {
    return hasGlobalAccess;
  }

}
