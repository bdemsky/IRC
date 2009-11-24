package Analysis.MLP;

import IR.TypeDescriptor;

public class SESEEffectsKey {

	private String fd;
	private TypeDescriptor td;
	private Integer hrnId;
	private String hrnUniqueId;

	public SESEEffectsKey(String fd, TypeDescriptor td, Integer hrnId, String hrnUniqueId) {
		this.fd = fd;
		this.td = td;
		this.hrnId = hrnId;
		this.hrnUniqueId=hrnUniqueId;
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
	
	public String getHRNUniqueId(){
		return hrnUniqueId;
	}

	public String toString() {
		return "(" + td + ")" + fd + "#" + hrnId+":"+hrnUniqueId;
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
