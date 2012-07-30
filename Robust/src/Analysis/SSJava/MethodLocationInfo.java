package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;
import IR.MethodDescriptor;

public class MethodLocationInfo extends LocationInfo {

  String returnLocName;
  String thisLocName;
  String PCLocName;

  Map<Integer, String> mapParamIdxToLocName;
  Set<String> paramLocNameSet;

  public MethodLocationInfo(MethodDescriptor md) {
    this.md = md;
    this.mapParamIdxToLocName = new HashMap<Integer, String>();
    this.paramLocNameSet = new HashSet<String>();
    this.PCLocName = SSJavaAnalysis.TOP;
  }

  /*
   * public void mapFlowNodeToInferLocation(FlowNode node, CompositeLocation
   * location) { mapFlowNodeToLocation.put(node, location); }
   * 
   * public CompositeLocation getInferLocation(FlowNode node) { return
   * mapFlowNodeToLocation.get(node); }
   */
  public String getReturnLocName() {
    return returnLocName;
  }

  public void setReturnLocName(String returnLocName) {
    this.returnLocName = returnLocName;
  }

  public String getThisLocName() {
    return thisLocName;
  }

  public void setThisLocName(String thisLocName) {
    this.thisLocName = thisLocName;
  }

  public String getPCLocName() {
    return PCLocName;
  }

  public void setPCLocName(String pCLocName) {
    PCLocName = pCLocName;
  }

  public void addParameter(String name, Descriptor desc, int idx) {
    mapParamIdxToLocName.put(new Integer(idx), name);
    // addMappingOfLocNameToDescriptor(name, desc);
  }

  public Set<String> getParameterLocNameSet() {
    Set<String> paramSet = new HashSet<String>();

    paramSet.add(PCLocName);

    if (thisLocName != null) {
      paramSet.add(thisLocName);
    }

    if (returnLocName != null) {
      paramSet.add(returnLocName);
    }

    paramSet.addAll(mapParamIdxToLocName.values());

    return paramSet;
  }

  public void removeMaplocalVarToLocSet(Descriptor localVarDesc) {
    String localVarLocSymbol = localVarDesc.getSymbol();
    getDescSet(localVarLocSymbol).remove(localVarDesc);
  }

}
