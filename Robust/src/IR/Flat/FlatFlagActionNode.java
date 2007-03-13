package IR.Flat;
import IR.FlagDescriptor;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;

public class FlatFlagActionNode extends FlatNode {
    Hashtable tempflagpairs; 
    int taskexit;
    public static final int NEWOBJECT=0;
    public static final int PRE=1;
    public static final int TASKEXIT=2;


    public FlatFlagActionNode(int taskexit) {
	tempflagpairs=new Hashtable();
	this.taskexit=taskexit;
    }

    public int getTaskType() {
	return taskexit;
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
    
    /** This method returns an iterator over the Temp/Flag pairs. */
    
    public Iterator getTempFlagPairs() {
	return tempflagpairs.keySet().iterator();
    }

    public boolean getFlagChange(TempFlagPair tfp) {
	return ((Boolean)tempflagpairs.get(tfp)).booleanValue();
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

    public String toString() {
	return "FlatFlagActionNode";
    }
}
