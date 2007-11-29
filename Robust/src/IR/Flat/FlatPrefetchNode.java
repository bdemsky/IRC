package IR.Flat;
import Analysis.Prefetch.*;
import java.util.*;

public class FlatPrefetchNode extends FlatNode {
	HashSet<PrefetchPair> pp;

	public FlatPrefetchNode() {
		pp = new HashSet<PrefetchPair>();
	}

	public String toString() {
		return "prefetchnode";
	}

	public int kind() {
		return FKind.FlatPrefetchNode;
	}

	public void insPrefetchPair(PrefetchPair pp) {
		this.pp.add(pp);
	}

	public void insAllpp(HashSet<PrefetchPair> hspp) {
		this.pp.addAll(hspp);
	}

	public HashSet<PrefetchPair> getPrefetchPairs() {
		return this.pp;
	}
}
