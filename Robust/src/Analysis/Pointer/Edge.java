package Analysis.Pointer;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.AllocFactory.AllocNode;

public class Edge {
  FieldDescriptor fd;
  AllocNode src;
  TempDescriptor srcvar;
  AllocNode dst;

  private Edge() {
  }

  public Edge(AllocNode src, FieldDescriptor fd, AllocNode dst) {
    this.src=src;
    this.fd=fd;
    this.dst=dst;
  }
  
  public Edge(TempDescriptor tmp, AllocNode dst) {
    this.srcvar=tmp;
    this.dst=dst;
  }
  
  public int hashCode() {
    int hashcode=dst.hashCode();
    if (fd!=null) {
      hashcode^=fd.hashCode();
    }
    if (src!=null) {
      hashcode^=(src.hashCode()<<3);
    } else {
      hashcode^=(srcvar.hashCode()<<3);
    }
    return hashcode;
  }

  public boolean equals(Object o) {
    if (o instanceof Edge) {
      Edge e=(Edge) o;
      if (srcvar!=null) {
	return (srcvar==e.srcvar)&&(dst==e.dst);
      } else {
	return (src==e.src)&&(dst==e.dst)&&(fd==e.fd);
      }
    }
    return false;
  }

  public Edge copy() {
    Edge e=new Edge();
    e.fd=fd;
    e.src=src;
    e.srcvar=srcvar;
    e.dst=dst;
    return e;
  }
}