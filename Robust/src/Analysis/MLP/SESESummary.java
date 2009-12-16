package Analysis.MLP;

import IR.Flat.FlatNode;
import IR.Flat.FlatSESEEnterNode;

public class SESESummary {

	private FlatNode currentParent;
	private FlatNode currentSESE;

	public SESESummary(FlatNode currentParent, FlatNode currentChild) {
		this.currentParent = currentParent;
		this.currentSESE = currentChild;
	}

	public FlatNode getCurrentParent() {
		return currentParent;
	}

	public FlatNode getCurrentSESE() {
		return currentSESE;
	}

	public void setCurrentParent(FlatNode parent) {
		currentParent = parent;
	}

	public void setCurrentSESE(FlatNode current) {
		currentSESE = current;
	}

	public String toString() {
		String rtn;

		rtn = "parent=" + currentParent;
		if (currentSESE instanceof FlatSESEEnterNode) {
			rtn += " current="
					+ ((FlatSESEEnterNode) currentSESE).getPrettyIdentifier();
		} else {
			rtn += " current=" + currentSESE;
		}

		return rtn;
	}

}
