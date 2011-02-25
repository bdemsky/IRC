package Analysis.Pointer;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.AllocFactory.AllocNode;

public class Edge {
  FieldDescriptor fd;
  AllocNode src;
  TempDescriptor srcvar;
  AllocNode dst;
  int statuspredicate;
  public static final int SNGSNG=1;
  public static final int SNGSUM=2;
  public static final int SUMSNG=4;
  public static final int SUMSUM=8;
  public static final int NEW=16;

  public static int mergeStatus(int stat1, int stat2) {
    int status=stat1|stat2;
    return ((status&NEW)==NEW)?NEW:status;
  }

  private Edge() {
  }

  public Edge(AllocNode src, FieldDescriptor fd, AllocNode dst) {
    this.src=src;
    this.fd=fd;
    this.dst=dst;
  }

  public Edge(AllocNode src, FieldDescriptor fd, AllocNode dst, int statuspredicate) {
    this.src=src;
    this.fd=fd;
    this.dst=dst;
    this.statuspredicate=statuspredicate;
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
    e.statuspredicate=statuspredicate;
    return e;
  }

  public boolean statusDominates(Edge other) {
    return (statuspredicate==NEW)||
      ((other.statuspredicate|statuspredicate)==statuspredicate);
  }

  public Edge makeOld() {
    Edge e=new Edge();
    e.fd=fd;
    e.src=src;
    e.srcvar=srcvar;
    e.dst=dst;
    int val=1;
    if (dst.isSummary())
      val=val<<1;
    if (src.isSummary())
      val=val<<2;
    e.statuspredicate=val;
    return e;
  }
}