package IR.Tree;

import IR.*;
import java.util.*;

public class FlagEffects {
  Vector effects;
  Vector tageffects;
  String name;
  VarDescriptor vd;

  public FlagEffects(String name) {
    effects=new Vector();
    tageffects=new Vector();
    this.name=name;
  }

  public void setVar(VarDescriptor vd) {
    this.vd=vd;
  }

  public VarDescriptor getVar() {
    return vd;
  }

  public String getName() {
    return name;
  }

  public void addEffect(FlagEffect fe) {
    effects.add(fe);
  }

  public void addTagEffect(TagEffect te) {
    tageffects.add(te);
  }

  public int numTagEffects() {
    return tageffects.size();
  }

  public TagEffect getTagEffect(int i) {
    return (TagEffect) tageffects.get(i);
  }

  public int numEffects() {
    return effects.size();
  }

  public FlagEffect getEffect(int i) {
    return (FlagEffect) effects.get(i);
  }

  public String printNode(int indent) {
    String st=name+"(";
    for(int i=0; i<effects.size(); i++) {
      FlagEffect fe=(FlagEffect)effects.get(i);
      st+=fe.printNode(0);
      if ((i+1)!=effects.size())
        st+=",";
    }
    return st+")";
  }
}
