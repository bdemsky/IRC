package IR.Flat;
import Analysis.TaskStateAnalysis.FlagState;
import IR.ClassDescriptor;
import IR.FlagDescriptor;
import IR.TagDescriptor;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

public class FlatFlagActionNode extends FlatNode {
    Hashtable<TempFlagPair, Boolean> tempflagpairs; 
    Hashtable<TempTagPair, Boolean> temptagpairs; 

    int taskexit;
    public static final int NEWOBJECT=0;
    public static final int PRE=1;
    public static final int TASKEXIT=2;
    
    Hashtable<ClassDescriptor, Vector<FlagState>> cd2initfs;
    Hashtable<ClassDescriptor, Vector<FlagState>> cd2fs4new;
    Hashtable<FlagState, Vector<FlagState>> fs2fs;


    public FlatFlagActionNode(int taskexit) {
	tempflagpairs=new Hashtable<TempFlagPair, Boolean>();
	temptagpairs=new Hashtable<TempTagPair, Boolean>();
	this.taskexit=taskexit;
	
	this.cd2initfs = null;
	this.cd2fs4new = null;
	this.fs2fs = null;
    }

    public int getTaskType() {
	return taskexit;
    }
    
    public Vector<FlagState> getInitFStates(ClassDescriptor cd) {
	if(this.cd2initfs == null) {
	    this.cd2initfs = new Hashtable<ClassDescriptor, Vector<FlagState>>();
	}
	if(this.cd2initfs.get(cd) == null) {
	    this.cd2initfs.put(cd, new Vector<FlagState>());
	}
	return this.cd2initfs.get(cd);
    }
    
    public Vector<FlagState> getTargetFStates4NewObj(ClassDescriptor cd) {
	if(this.cd2fs4new == null) {
	    this.cd2fs4new = new Hashtable<ClassDescriptor, Vector<FlagState>>();
	}
	if(this.cd2fs4new.get(cd) == null) {
	    this.cd2fs4new.put(cd, new Vector<FlagState>());
	}
	return this.cd2fs4new.get(cd);
    }
    
    public Vector<FlagState> getTargetFStates(FlagState fs) {
	if(this.fs2fs == null) {
	    this.fs2fs = new Hashtable<FlagState, Vector<FlagState>>();
	}
	if(this.fs2fs.get(fs) == null) {
	    this.fs2fs.put(fs, new Vector<FlagState>());
	}
	return this.fs2fs.get(fs);
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
    
    public Iterator<TempFlagPair> getTempFlagPairs() {
	return tempflagpairs.keySet().iterator();
    }

    public Iterator<TempTagPair> getTempTagPairs() {
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
		temps.add(ttp.getTagTemp());
	    }
            return (TempDescriptor[]) temps.toArray(new TempDescriptor [temps.size()]);
	}
    }

    public int getFFANType()
    {
	throw new Error("Use getTaskType() instead and remove this method");
    }

    public String toString() {
	String st="FlatFlagActionNode_";
	for(Iterator it=tempflagpairs.keySet().iterator();it.hasNext();) {
	    TempFlagPair tfp=(TempFlagPair)it.next();
	    st+=getFlagChange(tfp)?"":"!";
	    st+=tfp.getTemp()+" "+tfp.getFlag()+",";
	}
	for(Iterator it=temptagpairs.keySet().iterator();it.hasNext();) {
	    TempTagPair ttp=(TempTagPair)it.next();
	    st+=getTagChange(ttp)?"":"!";
	    st+=ttp.getTemp()+" "+ttp.getTag()+"("+ttp.getTagTemp()+"),";
	}

	return st;
    }
}
