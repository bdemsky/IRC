package Analysis.Disjoint;
import IR.Flat.FlatNew;

public interface HeapAnalysis {
  public EffectsAnalysis getEffectsAnalysis();
  public Alloc getAllocationSiteFromFlatNew(FlatNew node);
}

