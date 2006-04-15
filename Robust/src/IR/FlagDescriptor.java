package IR;

/**
 * Descriptor 
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class FlagDescriptor extends Descriptor {

    public FlagDescriptor(String identifier) {
	super(identifier);
    }

    public String toString() {
	return "Flag "+getSymbol();
    }
}
