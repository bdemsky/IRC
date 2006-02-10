package IR;

public class NameDescriptor extends Descriptor {
    String identifier;
    NameDescriptor nd;
    public NameDescriptor(NameDescriptor nd, String id) {
	super(nd.toString()+"."+id);
	identifier=id;
	this.nd=nd;
    }

    public NameDescriptor(String id) {
	super(id);
	identifier=id;
	nd=null;
    }

    public String toString() {
	if (nd==null) 
	    return identifier;
	else
	    return nd+"."+identifier;
    }

}
