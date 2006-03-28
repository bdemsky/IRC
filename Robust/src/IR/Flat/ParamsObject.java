package IR.Flat;
import IR.*;
import java.util.*;

public class ParamsObject {
    private Vector pointerparams;
    private Vector primitiveparams;
    private MethodDescriptor method;
    private int tag;
    private Hashtable paramtotemp;
    private Hashtable temptostore;
    private int count;

    public ParamsObject(MethodDescriptor md, int tag) {
	pointerparams=new Vector();
	primitiveparams=new Vector();
	paramtotemp=new Hashtable();
	temptostore=new Hashtable();
	this.method=md;
	this.tag=tag;
	count=0;
    }

    public int getUID() {
	return tag;
    }

    public void addPtr(TempDescriptor t) {
	Position p=new Position(true, pointerparams.size());
	pointerparams.add(t);
	paramtotemp.put(new Integer(count++), t);
	temptostore.put(t,p);
    }

    public boolean isParamPtr(TempDescriptor t) {
	if (containsTemp(t)) {
	    ParamsObject.Position p=(ParamsObject.Position)temptostore.get(t);
	    return p.inStruct;
	}
	return false;
    }

    public boolean isParamPrim(TempDescriptor t) {
	if (containsTemp(t)) {
	    ParamsObject.Position p=(ParamsObject.Position)temptostore.get(t);
	    return !p.inStruct;
	}
	return false;
    }

    public boolean containsTemp(TempDescriptor t) {
	return temptostore.containsKey(t);
    }

    public void addPrim(TempDescriptor t) {
	Position p=new Position(false, primitiveparams.size());
	primitiveparams.add(t);
	paramtotemp.put(new Integer(count++), t);
	temptostore.put(t,p);
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
