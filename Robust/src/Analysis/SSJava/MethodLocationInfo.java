package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import IR.MethodDescriptor;

public class MethodLocationInfo {

  String returnLocName;
  String thisLocName;
  String PCLocName;
  Map<Integer, String> mapParamIdxToLocName;
  Map<String, FlowNode> mapLocNameToFlowNode;
  MethodDescriptor md;

  public MethodLocationInfo(MethodDescriptor md) {
    this.md = md;
    this.mapParamIdxToLocName = new HashMap<Integer, String>();
    this.mapLocNameToFlowNode = new HashMap<String, FlowNode>();
    this.PCLocName = SSJavaAnalysis.TOP;
  }

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

  public void addParameter(String name, FlowNode node, int idx) {
    mapParamIdxToLocName.put(new Integer(idx), name);
    mapLocNameToFlowNode.put(name, node);
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

    paramSet.addAll(mapLocNameToFlowNode.keySet());

    return paramSet;
  }

}
