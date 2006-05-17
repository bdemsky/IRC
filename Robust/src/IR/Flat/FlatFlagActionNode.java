package IR.Flat;
import IR.FlagDescriptor;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;

public class FlatFlagActionNode extends FlatNode {
    Hashtable tempflagpairs; 
    boolean taskexit;

    public FlatFlagActionNode(boolean taskexit) {
	tempflagpairs=new Hashtable();
	this.taskexit=taskexit;
    }

    public void addFlagAction(TempDescriptor td, FlagDescriptor fd, boolean status) {
	TempFlagPair tfp=new TempFlagPair(td,fd);
	if (tempflagpairs.containsKey(tfp)) {
	    throw new Error("Temp/Flag combination used twice: "+tfp);
	}
	tempflagpairs.put(tfp, new Boolean(status));
    }

    public int kind() {
        return FKind.FlatFlagActionNode;
    }

    public TempDescriptor [] readsTemps() {
        if (tempflagpairs.size()==0)
            return new TempDescriptor [0];
        else {
	    HashSet temps=new HashSet();
	    for(Iterator it=tempflagpairs.keySet().iterator();it.hasNext();) {
		TempFlagPair tfp=(TempFlagPair)it.next();
		temps.add(tfp.getTemp());
	    }
            return (TempDescriptor[]) temps.toArray(new TempDescriptor [temps.size()]);
	}
    }
}
