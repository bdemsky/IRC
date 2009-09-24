package Analysis.MLP;

import IR.TypeDescriptor;

public class AccKey {

	private String fd;
	private TypeDescriptor td;

	// private Integer hrnID;

	public AccKey(String fd, TypeDescriptor td) {
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
		return "(" + td + ")" + fd ;
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

		if (!(o instanceof AccKey)) {
			return false;
		}

		AccKey in = (AccKey) o;

		if (fd.equals(in.getFieldDescriptor()) && td.getSymbol().equals(in.getTypeDescriptor().getSymbol())) {
			return true;
		} else {
			return false;
		}

	}
}
