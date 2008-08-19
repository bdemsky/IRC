package Analysis.Locality;
import IR.Flat.*;


public class TempNodePair {
  TempDescriptor tmp;
  FlatNode fn;

  public TempNodePair(TempDescriptor tmp) {
    this.tmp=tmp;
  }

  public TempDescriptor getTemp() {
    return tmp;
  }

  public void setNode(FlatNode fn) {
    this.fn=fn;
  }

  public FlatNode getNode() {
    return fn;
  }

  public boolean equals(Object o) {
    if (o instanceof TempNodePair) {
      TempNodePair tnp=(TempNodePair)o;
      if (tnp.fn!=null||fn!=null) {
	// need to check flat node equivalence also
	if (tnp.fn==null||fn==null||(!fn.equals(tnp.fn)))
	  return false;
      }
      return tmp.equals(tnp.tmp);
    }
    return false;
  }

  public int hashCode() {
    return tmp.hashCode();
  }

  public String toString() {
    if (getNode()==null)
      return "<null,"+getTemp()+">";
    else
      return "<"+getNode()+","+getTemp()+">";
  }
}
