package IR;

/**
 * Descriptor
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class TagDescriptor extends Descriptor {

  public TagDescriptor(String identifier) {
    super(identifier);
  }

  public boolean equals(Object o) {
    if (o instanceof TagDescriptor) {
      TagDescriptor t=(TagDescriptor) o;
      return getSymbol().equals(t.getSymbol());
    } else return false;
  }

  public int hashCode() {
    return getSymbol().hashCode();
  }

  public String toString() {
    return "Tag "+getSymbol();
  }
}
