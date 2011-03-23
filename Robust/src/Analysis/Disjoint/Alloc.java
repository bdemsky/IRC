package Analysis.Disjoint;
import IR.TypeDescriptor;
import IR.Flat.FlatNew;

public interface Alloc {
  public FlatNew getFlatNew();
  public String toStringBrief();
  public int getUniqueAllocSiteID();
  public TypeDescriptor getType();
}