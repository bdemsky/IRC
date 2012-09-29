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
  Map<Descriptor, CompositeLocation> mapDescToInferCompositeLocation;

  public MethodSummary(MethodDescriptor md) {
    this.md = md;
    this.pcLoc = new CompositeLocation(new Location(md, Location.TOP));
    this.mapParamIdxToInferLoc = new HashMap<Integer, CompositeLocation>();
    this.thisLocName = "this";
  }

  public void addMapParamIdxToInferLoc(int paramIdx, CompositeLocation inferLoc) {
    mapParamIdxToInferLoc.put(paramIdx, inferLoc);
  }

}
