package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class LocationSummary {

  Map<String, String> mapHNodeNameToLocationName;
  Map<String, Set<String>> mapLocationNameToHNodeNameSet;

  public LocationSummary() {
    mapHNodeNameToLocationName = new HashMap<String, String>();
    mapLocationNameToHNodeNameSet = new HashMap<String, Set<String>>();
  }

  public void addMapHNodeNameToLocationName(String nodeName, String locName) {
    // System.out.println("nodeName="+nodeName+"  locName="+locName);
    mapHNodeNameToLocationName.put(nodeName, locName);

    if (!mapLocationNameToHNodeNameSet.containsKey(locName)) {
      mapLocationNameToHNodeNameSet.put(locName, new HashSet<String>());
    }
    mapLocationNameToHNodeNameSet.get(locName).add(nodeName);
  }

  public Set<String> getHNodeNameSetByLatticeLoationName(String locName) {
    return mapLocationNameToHNodeNameSet.get(locName);
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
