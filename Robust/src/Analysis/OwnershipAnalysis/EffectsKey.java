package Analysis.OwnershipAnalysis;

import Analysis.MLP.AccKey;
import IR.TypeDescriptor;

public class EffectsKey {

	private String fd;
	private TypeDescriptor td;

	public EffectsKey(String fd, TypeDescriptor td) {
		this.fd = fd;
		this.td = td;
	}

	public String getFieldDescriptor() {
		return fd;
	}

	public TypeDescriptor getTypeDescriptor() {
		return td;
	}

	public String toString() {
		return "(" + td + ")" + fd;
	}

	public int hashCode() {

		int hash = 1;

		if (fd != null) {
			hash = hash * 31 + fd.hashCode();
		}

		if (td != null) {
			hash += td.getSymbol().hashCode();
		}

		return hash;

	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof EffectsKey)) {
			return false;
		}

		EffectsKey in = (EffectsKey) o;

		if (fd.equals(in.getFieldDescriptor())
				&& td.getSymbol().equals(in.getTypeDescriptor().getSymbol())) {
			return true;
		} else {
			return false;
		}

	}
}
