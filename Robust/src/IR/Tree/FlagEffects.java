package IR.Tree;

import IR.*;
import java.util.*;

public class FlagEffects {
    Vector effects;
    String name;
    VarDescriptor vd;

    public FlagEffects(String name) {
	effects=new Vector();
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

    public int numEffects() {
	return effects.size();
    }

    public FlagEffect getEffect(int i) {
	return (FlagEffect) effects.get(i);
    }

    public String printNode(int indent) {
	String st=name+"(";
	for(int i=0;i<effects.size();i++) {
	    FlagEffect fe=(FlagEffect)effects.get(i);
	    st+=fe.printNode(0);
	    if ((i+1)!=effects.size())
		st+=",";
	}
	return st+")";
    }
}
