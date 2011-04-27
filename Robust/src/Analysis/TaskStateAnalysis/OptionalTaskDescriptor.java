package Analysis.TaskStateAnalysis;
import java.util.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.io.*;
import Util.Edge;

public class OptionalTaskDescriptor {
  public TaskDescriptor td;
  public HashSet enterflagstates;
  public HashSet<HashSet> exitfses;
  public Predicate predicate;
  private static int nodeid=0;
  private int index;
  private int uid;

  protected OptionalTaskDescriptor(TaskDescriptor td, int index, HashSet enterflagstates, Predicate predicate) {
    this.td = td;
    this.enterflagstates = enterflagstates;
    this.exitfses = new HashSet();
    this.predicate = predicate;
    this.index=index;
  }

  public int hashCode() {
    return td.hashCode()^enterflagstates.hashCode()^predicate.hashCode()^index;
  }

  public boolean equals(Object o) {
    if (o instanceof OptionalTaskDescriptor) {
      OptionalTaskDescriptor otd=(OptionalTaskDescriptor) o;
      if (otd.td==td&&
          otd.enterflagstates.equals(enterflagstates)&&
          otd.predicate.equals(predicate)&&
          otd.index==index)
        return true;
    }
    return false;
  }

  public int getIndex() {
    return index;
  }

  public String tostring() {
    return "Optional task "+td.getSymbol();
  }

  public void setuid() {
    uid=nodeid++;
  }

  public int getuid() {
    return uid;
  }
}
