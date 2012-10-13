package Analysis.SSJava;

import java.util.HashMap;
import java.util.Map;

public abstract class LocationSummary {

  Map<String, String> mapHNodeNameToLocationName;

  public LocationSummary() {
    mapHNodeNameToLocationName = new HashMap<String, String>();
  }

  public void addMapHNodeNameToLocationName(String nodeName, String locName) {
    // System.out.println("nodeName="+nodeName+"  locName="+locName);
    mapHNodeNameToLocationName.put(nodeName, locName);
  }

  public String getLocationName(String nodeName) {
    if (!mapHNodeNameToLocationName.containsKey(nodeName)) {
      mapHNodeNameToLocationName.put(nodeName, nodeName);
    }
    return mapHNodeNameToLocationName.get(nodeName);
  }

  public Map<String, String> getMapHNodeNameToLocationName() {
    return mapHNodeNameToLocationName;
  }

}
