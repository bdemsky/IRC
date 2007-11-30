package IR.Flat;
import Analysis.Prefetch.*;
import java.util.*;

public class FlatPrefetchNode extends FlatNode {
	HashSet<PrefetchPair> hspp;

	public FlatPrefetchNode() {
		hspp = new HashSet<PrefetchPair>();
	}

	public String toString() {
		return "prefetchnode";
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
}
