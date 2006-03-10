package IR.Flat;
import IR.*;

public class TempDescriptor {
    static int currentid=0;
    int id;
    String safename;
    TypeDescriptor type;

    public TempDescriptor(String name) {
	safename="__"+name+"__";
	id=currentid++;
    }

    public TempDescriptor(String name, TypeDescriptor td) {
	this(name);
	type=td;
    }
    
    public static TempDescriptor tempFactory() {
	return new TempDescriptor("temp_"+currentid);
    }

    public static TempDescriptor tempFactory(String name) {
	return new TempDescriptor(name+currentid);
    }

    public static TempDescriptor tempFactory(String name, TypeDescriptor td) {
	return new TempDescriptor(name+currentid,td);
    }


    public String toString() {
	return safename;
    }

    public void setType(TypeDescriptor td) {
	type=td;
    }

    public TypeDescriptor getType() {
	return type;
    }
}
