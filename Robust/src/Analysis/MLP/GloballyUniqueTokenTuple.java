package Analysis.MLP;

import Analysis.OwnershipAnalysis.Canonical;
import Analysis.OwnershipAnalysis.TokenTuple;

public class GloballyUniqueTokenTuple extends Canonical{

	private Integer token;
	private boolean isMultiObject;
	private int arity;
	private String id;

	public GloballyUniqueTokenTuple(String uniqueID, TokenTuple tt) {
		this.id = uniqueID;
		this.arity = tt.getArity();
		this.token = tt.getToken();
		this.isMultiObject = tt.isMultiObject();
	}

	public boolean isMultiObject() {
		return isMultiObject;
	}

	public int getArity() {
		return arity;
	}

	public int hashCode() {
		return id.hashCode() + arity;
	}

	public String getID() {
		return id;
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (!(o instanceof GloballyUniqueTokenTuple)) {
			return false;
		}

		GloballyUniqueTokenTuple tt = (GloballyUniqueTokenTuple) o;

		return id.equals(tt.getID()) && arity == tt.getArity();
	}

	public String toString() {
		String s = id;

		if (isMultiObject) {
			s += "M";
		}

		if (arity == TokenTuple.ARITY_ZEROORMORE) {
			s += "*";
		} else if (arity == TokenTuple.ARITY_ONEORMORE) {
			s += "+";
		}

		return s;
	}

}
