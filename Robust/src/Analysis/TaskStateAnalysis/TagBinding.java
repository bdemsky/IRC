package Analysis.TaskStateAnalysis;
import IR.MethodDescriptor;
import IR.TagDescriptor;
import Util.GraphNode;
import java.util.HashSet;
import java.util.Set;

public class TagBinding extends GraphNode {
  private MethodDescriptor md;
  private TagDescriptor[] tdarray;
  private HashSet allocations;

  public TagBinding(MethodDescriptor md) {
    this.md=md;
    tdarray=new TagDescriptor[md.numParameters()];
    allocations=new HashSet();
  }

  public String toString() {
    String st=md.toString();
    for(int i=0; i<tdarray.length; i++)
      st+=tdarray[i]+" ";
    return st;
  }

  public Set getAllocations() {
    return allocations;
  }

  public void setBinding(int i, TagDescriptor td) {
    tdarray[i]=td;
  }

  public MethodDescriptor getMethod() {
    return md;
  }

  public TagDescriptor getBinding(int i) {
    return tdarray[i];
  }

  public boolean equals(Object o) {
    if (o instanceof TagBinding) {
      TagBinding tb=(TagBinding)o;
      if (md!=tb.md)
	return false;
      for(int i=0; i<tdarray.length; i++)
	if (tdarray[i]!=null) {
	  if (!tdarray[i].equals(tb.tdarray[i]))
	    return false;
	} else if(tb.tdarray[i]!=null)
	  return false;
      return true;
    }
    return false;
  }

  public int hashCode() {
    int hashcode=md.hashCode();
    for(int i=0; i<tdarray.length; i++) {
      if (tdarray[i]!=null)
	hashcode^=tdarray[i].hashCode();
    }
    return hashcode;
  }
}
