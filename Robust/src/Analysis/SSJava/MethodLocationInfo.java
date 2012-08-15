package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import IR.MethodDescriptor;

public class MethodLocationInfo extends LocationInfo {

  String thisLocName;
  CompositeLocation pcLoc;
  CompositeLocation returnLoc;

  public CompositeLocation getReturnLoc() {
    return returnLoc;
  }

  public void setReturnLoc(CompositeLocation returnLoc) {
    this.returnLoc = returnLoc;
  }

  String globalLocName;

  Map<Integer, CompositeLocation> mapParamIdxToInferLoc;
  Set<String> paramLocNameSet;

  public MethodLocationInfo(MethodDescriptor md) {
    this.md = md;
    this.paramLocNameSet = new HashSet<String>();
    this.pcLoc = new CompositeLocation(new Location(md, Location.TOP));
    this.mapParamIdxToInferLoc = new HashMap<Integer, CompositeLocation>();
  }

  public void addMapParamIdxToInferLoc(int paramIdx, CompositeLocation inferLoc) {
    mapParamIdxToInferLoc.put(paramIdx, inferLoc);
  }

  public int getNumParam() {
    return mapParamIdxToInferLoc.keySet().size();
  }

  public CompositeLocation getParamCompositeLocation(int idx) {
    return mapParamIdxToInferLoc.get(idx);
  }

  public Map<Integer, CompositeLocation> getMapParamIdxToInferLoc() {
    return mapParamIdxToInferLoc;
  }

  public String getGlobalLocName() {
    return globalLocName;
  }

  public void setGlobalLocName(String globalLocName) {
    this.globalLocName = globalLocName;
  }

  public String getThisLocName() {
    return thisLocName;
  }

  public void setThisLocName(String thisLocName) {
    this.thisLocName = thisLocName;
  }

  public CompositeLocation getPCLoc() {
    return pcLoc;
  }

  public void setPCLoc(CompositeLocation pcLoc) {
    this.pcLoc = pcLoc;
  }

  public void removeMaplocalVarToLocSet(Descriptor localVarDesc) {
    String localVarLocSymbol = localVarDesc.getSymbol();
    getDescSet(localVarLocSymbol).remove(localVarDesc);
  }

  public MethodDescriptor getMethodDesc() {
    return md;
  }

}
