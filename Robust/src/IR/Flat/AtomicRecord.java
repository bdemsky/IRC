package IR.Flat;
import java.util.Set;

public class AtomicRecord {
  String name;
  Set<TempDescriptor> livein;
  Set<TempDescriptor> reallivein;
  Set<TempDescriptor> liveout;
  Set<TempDescriptor> liveoutvirtualread;

}
