package IR.Flat;
import IR.TagVarDescriptor;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;

public class FlatTagActionNode extends FlatNode {
    Hashtable temptagpairs; 
    int taskexit;
    public static final int NEWOBJECT=0;
    public static final int PRE=1;
    public static final int TASKEXIT=2;


    public FlatTagActionNode(int taskexit) {
	temptagpairs=new Hashtable();
	this.taskexit=taskexit;
    }

    public int getTaskType() {
	return taskexit;
    }

    public void addTagAction(TempDescriptor td, TagVarDescriptor tvd, boolean status) {
	TempTagPair ttp=new TempTagPair(td,tvd);
	if (temptagpairs.containsKey(ttp)) {
	    throw new Error("Temp/Tag combination used twice: "+ttp);
	}
	temptagpairs.put(ttp, new Boolean(status));
    }

    public int kind() {
        return FKind.FlatTagActionNode;
    }
    
    /** This method returns an iterator over the Temp/Tag pairs. */
    
    public Iterator getTempTagPairs() {
	return temptagpairs.keySet().iterator();
    }

    public boolean getTagChange(TempTagPair ttp) {
	return ((Boolean)temptagpairs.get(ttp)).booleanValue();
    }

    public TempDescriptor [] readsTemps() {
        if (temptagpairs.size()==0)
            return new TempDescriptor [0];
        else {
	    HashSet temps=new HashSet();
	    for(Iterator it=temptagpairs.keySet().iterator();it.hasNext();) {
		TempTagPair ttp=(TempTagPair)it.next();
		temps.add(ttp.getTemp());
	    }
            return (TempDescriptor[]) temps.toArray(new TempDescriptor [temps.size()]);
	}
    }

    public String toString() {
	return "FlatTagActionNode";
    }
}
