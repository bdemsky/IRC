package Analysis.Disjoint;
import IR.Flat.FlatNew;

public interface Alloc {
  public FlatNew getFlatNew();
  public String toStringBrief();
  public int getUniqueAllocSiteID();
}