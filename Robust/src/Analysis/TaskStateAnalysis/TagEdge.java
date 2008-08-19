package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import Util.Edge;

/* Edge *****************/

public class TagEdge extends Edge {

  private TaskDescriptor td;
  /** Class Constructor
   *
   */
  public TagEdge(TagState target, TaskDescriptor td) {
    super(target);
    this.td=td;
  }

  public int hashCode() {
    return target.hashCode()^td.hashCode();
  }

  public TaskDescriptor getTask() {
    return td;
  }

  public boolean equals(Object o) {
    if (o instanceof TagEdge) {
      TagEdge e=(TagEdge)o;
      if (e.target.equals(target)&&
          e.td==td)
	return true;
    }
    return false;
  }
}
