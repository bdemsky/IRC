package IR.Flat;
import Analysis.Prefetch.*;
import java.util.*;

public class FlatPrefetchNode extends FlatNode {
  public Integer siteid;
  HashSet<PrefetchPair> hspp;

  public FlatPrefetchNode() {
    hspp = new HashSet<PrefetchPair>();
    siteid = new Integer(1);
  }

  public String toString() {
    String st="prefetch(";
    boolean first=true;
    for(Iterator<PrefetchPair> it=hspp.iterator(); it.hasNext(); ) {
      PrefetchPair pp=it.next();
      if (!first)
        st+=", ";
      first=false;
      st+=pp;
    }
    return st+")";
  }

  public int kind() {
    return FKind.FlatPrefetchNode;
  }

  public void insPrefetchPair(PrefetchPair pp) {
    hspp.add(pp);
  }

  public void insAllpp(HashSet<PrefetchPair> hspp) {
    this.hspp.addAll(hspp);
  }

  public HashSet<PrefetchPair> getPrefetchPairs() {
    return hspp;
  }

  public int getNumPairs() {
    return hspp.size();
  }
}
