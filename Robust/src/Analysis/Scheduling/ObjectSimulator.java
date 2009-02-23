package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import IR.ClassDescriptor;

public class ObjectSimulator {
  static int objid = 0;
  
  int oid;
  ClassDescriptor cd;
  FlagState currentFS;
  boolean changed;
  boolean shared;
  boolean hold;
  int version;

  public ObjectSimulator(ClassDescriptor cd, 
	                 FlagState currentFS) {
    super();
    this.oid = ObjectSimulator.objid++;
    this.cd = cd;
    this.currentFS = currentFS;
    this.changed = true;
    this.shared = false;
    this.hold = false;
    this.version = 0;
  }

  public void applyEdge(FEdge fedge) {
    if(!currentFS.equals((FlagState)fedge.getTarget())) {
      this.changed = true;
      currentFS = (FlagState)fedge.getTarget();
    } else {
      this.changed = false;
    }
  }

  public int getOid() {
    return oid;
  }

  public ClassDescriptor getCd() {
    return cd;
  }

  public FlagState getCurrentFS() {
    return currentFS;
  }

  public boolean isChanged() {
    return changed;
  }

  public void setCurrentFS(FlagState currentFS) {
    changed = true;
    this.currentFS = currentFS;
  }

  public boolean isHold() {
    return hold;
  }

  public void setHold(boolean hold) {
    this.hold = hold;
  }

  public boolean isShared() {
    return shared;
  }

  public void setShared(boolean shared) {
    this.shared = shared;
  }

  public int getVersion() {
    return version;
  }

  public void increaseVersion() {
    this.version++;
  }
}