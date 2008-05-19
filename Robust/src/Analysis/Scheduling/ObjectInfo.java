package Analysis.Scheduling;

import Analysis.TaskStateAnalysis.FlagState;

public class ObjectInfo {
    public ObjectSimulator obj;
    public FlagState fs;
    public int version;

    public ObjectInfo(ObjectSimulator obj) {
	this.obj = obj;
	this.fs = obj.getCurrentFS();
	this.version = obj.getVersion();
    }

    public boolean equals(Object o) {
	if (o instanceof ObjectInfo) {
	    ObjectInfo oi=(ObjectInfo)o;
	    if ((oi.obj != obj) || 
		    (oi.fs != fs) ||
		    (oi.version != version)) {
		return false;
	    }
	    return true;
	}
	return false;
    }

    public int hashCode() {
	return obj.hashCode()^fs.hashCode()^version;
    }
}