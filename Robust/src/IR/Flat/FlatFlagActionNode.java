package IR.Flat;
import IR.FlagDescriptor;
import IR.TagDescriptor;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;

public class FlatFlagActionNode extends FlatNode {
    Hashtable tempflagpairs; 
    Hashtable temptagpairs; 

    int taskexit;
    public static final int NEWOBJECT=0;
    public static final int PRE=1;
    public static final int TASKEXIT=2;


    public FlatFlagActionNode(int taskexit) {
	tempflagpairs=new Hashtable();
	temptagpairs=new Hashtable();
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

    public void addTagAction(TempDescriptor td, TagDescriptor tagd, TempDescriptor tagt, boolean status) {
	TempTagPair ttp=new TempTagPair(td,tagd, tagt);
	if (temptagpairs.containsKey(ttp)) {
	    throw new Error("Temp/Tag combination used twice: "+ttp);
	}
	temptagpairs.put(ttp, new Boolean(status));
    }

    public int kind() {
        return FKind.FlatFlagActionNode;
    }
    
    /** This method returns an iterator over the Temp/Flag pairs. */
    
    public Iterator getTempFlagPairs() {
	return tempflagpairs.keySet().iterator();
    }

    public Iterator getTempTagPairs() {
	return temptagpairs.keySet().iterator();
    }

    public boolean getFlagChange(TempFlagPair tfp) {
	return ((Boolean)tempflagpairs.get(tfp)).booleanValue();
    }

    public boolean getTagChange(TempTagPair ttp) {
	return ((Boolean)temptagpairs.get(ttp)).booleanValue();
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
	    for(Iterator it=temptagpairs.keySet().iterator();it.hasNext();) {
		TempTagPair ttp=(TempTagPair)it.next();
		temps.add(ttp.getTemp());
	    }
            return (TempDescriptor[]) temps.toArray(new TempDescriptor [temps.size()]);
	}
    }

    public int getFFANType()
    {
	throw new Error("Use getTaskType() instead and remove this method");
    }

    public String toString() {
	return "FlatFlagActionNode";
    }
}
