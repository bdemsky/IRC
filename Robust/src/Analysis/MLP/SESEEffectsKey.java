package Analysis.MLP;

import IR.TypeDescriptor;

public class SESEEffectsKey {

	private String fd;
	private TypeDescriptor td;
	private Integer hrnId;

	public SESEEffectsKey(String fd, TypeDescriptor td, Integer hrnId) {
		this.fd = fd;
		this.td = td;
		this.hrnId = hrnId;
	}

	public String getFieldDescriptor() {
		return fd;
	}

	public TypeDescriptor getTypeDescriptor() {
		return td;
	}

	public Integer getHRNId() {
		return hrnId;
	}

	public String toString() {
		return "(" + td + ")" + fd + "#" + hrnId;
	}

	public int hashCode() {

		int hash = 1;

		if (fd != null) {
			hash = hash * 31 + fd.hashCode();
		}

		if (td != null) {
			hash += td.getSymbol().hashCode();
		}

		if (hrnId != null) {
			hash += hrnId.hashCode();
		}

		return hash;

	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof SESEEffectsKey)) {
			return false;
		}

		SESEEffectsKey in = (SESEEffectsKey) o;

		if (fd.equals(in.getFieldDescriptor())
				&& td.getSymbol().equals(in.getTypeDescriptor().getSymbol())
				&& hrnId.equals(in.getHRNId())) {
			return true;
		} else {
			return false;
		}

	}
	
}
