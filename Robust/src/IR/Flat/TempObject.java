package IR.Flat;
import IR.*;
import java.util.*;

public class TempObject {
    ParamsObject params;
    private Vector pointerparams;
    private Vector primitiveparams;
    private MethodDescriptor method;
    private int tag;
    private Hashtable paramtotemp;
    private Hashtable temptostore;
    private int count;

    public TempObject(ParamsObject p, MethodDescriptor md, int tag) {
	params=p;
	pointerparams=new Vector();
	primitiveparams=new Vector();
	paramtotemp=new Hashtable();
	temptostore=new Hashtable();
	this.method=md;
	this.tag=tag;
	count=0;
    }

    public void addPtr(TempDescriptor t) {
	if (!params.containsTemp(t)&&!pointerparams.contains(t)) {
	    Position p=new Position(true, pointerparams.size());
	    pointerparams.add(t);
	    paramtotemp.put(new Integer(count++), t);
	    temptostore.put(t,p);
	}
    }

    public void addPrim(TempDescriptor t) {
	if (!params.containsTemp(t)&&!primitiveparams.contains(t)) {
	    Position p=new Position(false, primitiveparams.size());
	    primitiveparams.add(t);
	    paramtotemp.put(new Integer(count++), t);
	    temptostore.put(t,p);
	}
    }

    public boolean isLocalPtr(TempDescriptor t) {
	if (!params.containsTemp(t)) {
	    Position p=(Position)temptostore.get(t);
	    return p.inStruct;
	}
	return false;
    }

    public boolean isLocalPrim(TempDescriptor t) {
	if (!params.containsTemp(t)) {
	    Position p=(Position)temptostore.get(t);
	    return !p.inStruct;
	}
	return false;
    }

    public boolean isParamPtr(TempDescriptor t) {
	return params.isParamPtr(t);
    }

    public boolean isParamPrim(TempDescriptor t) {
	return params.isParamPrim(t);
    }

    int numPointers() {
	return pointerparams.size();
    }

    TempDescriptor getPointer(int i) {
	return (TempDescriptor) pointerparams.get(i);
    }

    int numPrimitives() {
	return primitiveparams.size();
    }

    TempDescriptor getPrimitive(int i) {
	return (TempDescriptor) primitiveparams.get(i);
    }

    static class Position {
	boolean inStruct;
	int position;
	Position(boolean inStruct, int position) {
	    this.inStruct=inStruct;
	    this.position=position;
	}
    }
}
