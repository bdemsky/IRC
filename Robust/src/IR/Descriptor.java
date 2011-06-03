package IR;

/**
 * Descriptor
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public abstract class Descriptor {

  protected String name;
  protected String safename;
  static int count=0;
  int uniqueid;

  public Descriptor(String name) {
    this.name = name;
    this.safename = "___" + name + "___";
    this.uniqueid=count++;
  }

  protected Descriptor(String name, String safename) {
    this.name = name;
    this.safename = safename;
    this.uniqueid=count++;
  }

  public String toString() {
    return name;
  }

  public String getSymbol() {
    return name;
  }

  //the text replacement is done here because SOMEWHERE someone
  //modifies safename without going through the constructor...
  public String getSafeSymbol() {
    return safename;
  }

  public int getNum() {
    return uniqueid;
  }

  public String getCoreSafeSymbol(int num) {
    return safename + "core" + num + "___";
  }
}
