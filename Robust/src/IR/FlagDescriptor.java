package IR;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class FlagDescriptor extends Descriptor {
    public final static String InitialFlag="initialstate";

    public FlagDescriptor(String identifier) {
	super(identifier);
    }

    private boolean isExternal=false;
    public void makeExternal() {
	isExternal=true;
    }

    public boolean getExternal() {
	return isExternal;
    }

    public String toString() {
	return "Flag "+getSymbol();
    }
}
