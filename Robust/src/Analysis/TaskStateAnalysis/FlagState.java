package Analysis.TaskStateAnalysis;
import Analysis.TaskStateAnalysis.*;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;


public class FlagState {
    Hashtable flagstate;
    ClassDescriptor cd;

    public FlagState(FlagDescriptor[] flags, ClassDescriptor cd) {
	flagstate=new Hashtable(flags.length);
	this.cd=cd;
	for (int i=0; i < flags.length; i++) {
	    flagstate.put(flags[i],new Boolean(false));
	}
    }
    
    public FlagState(Hashtable flagstate, ClassDescriptor cd) {
	this.flagstate = new Hashtable(flagstate);
	this.cd=cd;
    }

    public Hashtable getStateTable() {
	return 	flagstate;
    }

    public void put(FlagDescriptor fd, Boolean status) {
	flagstate.put(fd,status);
    }

    public boolean get(FlagDescriptor fd) {
	if (! flagstate.containsKey(fd))
	    return false;
	else
	    return ((Boolean)(flagstate.get(fd))).booleanValue();
    }
    
    public String toString() {
	StringBuffer sb = new StringBuffer(flagstate.size());
	Enumeration e = flagstate.keys();
	
	while (e.hasMoreElements()) {
	    if (((Boolean)(flagstate.get((FlagDescriptor)e.nextElement()))).booleanValue())
		sb.append(1);
	    else
		sb.append(0);
	}
	return new String(sb);
    }

    public String toString(FlagDescriptor[] flags) {
	StringBuffer sb = new StringBuffer(flagstate.size());
	Enumeration e;

	for(int i=0;i < flags.length; i++) {
	    e = flagstate.keys();

	    while (e.hasMoreElements()) {
		FlagDescriptor fdtemp=(FlagDescriptor)e.nextElement();
		if( flags[i] == fdtemp) {
		    if (((Boolean)(flagstate.get(fdtemp))).booleanValue())
			sb.append(1);
		    else
			sb.append(0);
		}
	    }
	}
	return new String(sb);
    }

    public Enumeration getFlags() {
	return flagstate.keys();
    }

    public boolean equal(Object o) {
	if (o instanceof FlagState) {
	    FlagState fs=(FlagState)o;
	    if (fs.cd!=cd)
		return false;

	    Enumeration en = fs.getFlags();
	    while(en.hasMoreElements()) {
		FlagDescriptor flag=(FlagDescriptor)en.nextElement();
		
		if (fs.get(flag) != get(flag))
		    return false;
	    }
	    return true;
	}
	return false;
    }

    public int hashCode() {
	return cd.hashCode()^flagstate.hashCode();
    }
}
