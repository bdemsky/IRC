package IR;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class TagVarDescriptor extends Descriptor {

    protected TagDescriptor td;
    protected String identifier;
    
    public TagVarDescriptor(TagDescriptor t, String identifier) {
	super(identifier);
	this.td=t;
	this.identifier=identifier;
        this.safename = "___" + name + "___";
	this.uniqueid=count++;
    }

    public String getName() {
	return identifier;
    }

    public TagDescriptor getTag() {
	return td;
    }

    public TypeDescriptor getType() {
	return new TypeDescriptor(TypeDescriptor.TAG);
    }

    /*    public boolean equals(Object o) {
	if (o instanceof TagVarDescriptor) {
	    TagVarDescriptor tvd=(TagVarDescriptor)o;
	    if (tvd.identifier.equals(identifier)) {
		if (tvd.td!=null) {
		    if (!tvd.td.equals(td))
			throw new Error();
		} else if (td!=null)
		    throw new Error();
		return true;
	    }
	}
	return false;
    }

    public int hashCode() {
	return identifier.hashCode();
	}*/

    public String toString() {
	    return td.toString()+" "+identifier;
    }
}
