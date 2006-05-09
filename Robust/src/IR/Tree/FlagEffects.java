package IR.Tree;

import IR.*;
import java.util.*;

public class FlagEffects {
    Vector effects;
    String name;

    public FlagEffects(String name) {
	this.name=name;
	effects=new Vector();
    }

    public void addEffect(FlagEffect fe) {
	effects.add(fe);
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
