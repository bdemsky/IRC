package Analysis.Pointer;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.AllocFactory.AllocNode;

public class Edge {
  FieldDescriptor fd;
  AllocNode src;
  TempDescriptor srcvar;
  AllocNode dst;

  public Edge(AllocNode src, FieldDescriptor fd, AllocNode dst) {
    this.src=src;
    this.fd=fd;
    this.dst=dst;
  }
  
  public Edge(TempDescriptor tmp, AllocNode dst) {
    this.srcvar=tmp;
    this.dst=dst;
  }
  
}