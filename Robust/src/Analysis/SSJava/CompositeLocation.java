package Analysis.SSJava;

import IR.TypeExtension;

public class CompositeLocation implements TypeExtension {

  protected NTuple<Location> locTuple;

  public CompositeLocation() {
    locTuple = new NTuple<Location>();
  }

  public CompositeLocation(Location loc) {
    locTuple = new NTuple<Location>();
    locTuple.addElement(loc);
  }

  public NTuple<Location> getTuple() {
    return locTuple;
  }

  public int getSize() {
    return locTuple.size();
  }

  public void addLocation(Location loc) {
    locTuple.addElement(loc);
  }

  public Location get(int idx) {
    return locTuple.get(idx);
  }
  
  public boolean isEmpty(){
    return locTuple.size()==0;
  }

  // public void addLocationSet(Set<Location> set) {
  //
  // for (Iterator iterator = set.iterator(); iterator.hasNext();) {
  // Location location = (Location) iterator.next();
  // locTuple.addElement(location);
  // }
  //
  // }

  // public Location getLocation(ClassDescriptor cd) {
  //
  // // need to get more optimization version later
  // Set<Location> locSet = getBaseLocationSet();
  // for (Iterator iterator = locSet.iterator(); iterator.hasNext();) {
  // Location location = (Location) iterator.next();
  // if (location.getClassDescriptor().equals(cd)) {
  // return location;
  // }
  // }
  //
  // return null;
  //
  // }

  // public Map<ClassDescriptor, Location> getCd2Loc() {
  //
  // Map<ClassDescriptor, Location> cd2loc = new Hashtable<ClassDescriptor,
  // Location>();
  //
  // Set<Location> baseLocSet = getBaseLocationSet();
  // for (Iterator iterator = baseLocSet.iterator(); iterator.hasNext();) {
  // Location location = (Location) iterator.next();
  // cd2loc.put(location.getClassDescriptor(), location);
  // }
  //
  // return cd2loc;
  //
  // }

  public NTuple<Location> getBaseLocationTuple() {

    return locTuple;

    // NTuple<Location> baseLocationTuple = new NTuple<Location>();
    // int tupleSize = locTuple.size();
    // for (int i = 0; i < tupleSize; i++) {
    // Location locElement = locTuple.at(i);
    //
    // if (locElement instanceof DeltaLocation) {
    // // baseLocationSet.addAll(((DeltaLocation)
    // // locElement).getDeltaOperandLocationVec());
    // baseLocationTuple.addAll(((DeltaLocation)
    // locElement).getBaseLocationTuple());
    // } else {
    // baseLocationTuple.addElement(locElement);
    // }
    // }
    // return baseLocationTuple;

  }

  // public List<Location> getBaseLocationList() {
  //
  // Set<Location> baseLocationSet = new HashSet<Location>();
  // int tupleSize = locTuple.size();
  // for (int i = 0; i < tupleSize; i++) {
  // Location locElement = locTuple.at(i);
  //
  // if (locElement instanceof DeltaLocation) {
  // // baseLocationSet.addAll(((DeltaLocation)
  // // locElement).getDeltaOperandLocationVec());
  // baseLocationSet.addAll(((DeltaLocation) locElement).getBaseLocationSet());
  // } else {
  // baseLocationSet.add(locElement);
  // }
  // }
  // return baseLocationSet;
  // }

  // public int getNumofDelta() {
  //
  // int result = 0;
  //
  // if (locTuple.size() == 1) {
  // Location locElement = locTuple.at(0);
  // if (locElement instanceof DeltaLocation) {
  // result++;
  // result += getNumofDelta((DeltaLocation) locElement);
  // }
  // }
  // return result;
  // }

  // public int getNumofDelta(DeltaLocation delta) {
  // int result = 0;
  //
  // if (delta.getDeltaOperandLocationVec().size() == 1) {
  // Location locElement = delta.getDeltaOperandLocationVec().at(0);
  // if (locElement instanceof DeltaLocation) {
  // result++;
  // result += getNumofDelta((DeltaLocation) locElement);
  // }
  // }
  //
  // return result;
  // }

  // public void removieLocation(ClassDescriptor cd) {
  // for (int i = 0; i < locTuple.size(); i++) {
  // if (locTuple.at(i).getClassDescriptor().equals(cd)) {
  // locTuple.removeAt(i);
  // return;
  // }
  // }
  // }

  public String toString() {

    // for better representation
    // if compositeLoc has only one single location,
    // just print out single location
    // if(locTuple.size()==1){
    // Location locElement=locTuple.at(0);
    // if(locElement instanceof Location){
    // return locElement.toString();
    // }
    // }

    String rtr = "CompLoc[";

    int tupleSize = locTuple.size();
    for (int i = 0; i < tupleSize; i++) {
      Location locElement = locTuple.get(i);
      if (i != 0) {
        rtr += ",";
      }
      rtr += locElement;
    }
    rtr += "]";

    return rtr;
  }

  public boolean equals(Object o) {

    if (!(o instanceof CompositeLocation)) {
      return false;
    }

    CompositeLocation compLoc = (CompositeLocation) o;

    if (compLoc.getTuple().equals(getTuple())) {
      return true;
    } else {
      return false;
    }

  }

  public int hashCode() {

    return locTuple.hashCode();

  }

}
