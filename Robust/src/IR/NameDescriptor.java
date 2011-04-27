package IR;

public class NameDescriptor extends Descriptor {
  String identifier;
  NameDescriptor nd;
  public NameDescriptor(NameDescriptor nd, String id) {
    super(nd.toString()+"."+id);
    identifier=getPathFromRootToHere(id);
    this.nd=nd;
  }

  public NameDescriptor(String id) {
    super(id);
    identifier=getPathFromRootToHere(id);
    nd=null;
  }

  public String getIdentifier() {
    return identifier;
  }

  public NameDescriptor getBase() {
    return nd;
  }

  public String getRoot() {
    if (nd==null)
      return identifier;
    else
      return nd.getRoot();
  }

  public String getPathFromRootToHere() {
    return getPathFromRootToHere(identifier);
  }

  public String getPathFromRootToHere(String id) {
    String path = id;
    NameDescriptor temp = this.nd;
    while(temp!=null) {
      path =  temp.identifier + "." + path;
      temp = temp.nd;
    }

    return path;
  }

  public String toString() {
    if (nd==null)
      return identifier;
    else
      return nd+"."+identifier;
  }
}
