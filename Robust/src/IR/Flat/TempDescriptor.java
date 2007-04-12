package IR.Flat;
import IR.*;

public class TempDescriptor extends Descriptor {
    static int currentid=0;
    int id;
    //    String safename;
    TypeDescriptor type;
    TagDescriptor tag;

    public TempDescriptor(String name) {
	super(name);
	id=currentid++;
    }

    public TempDescriptor(String name, TypeDescriptor td) {
	this(name);
	type=td;
    }

    public TempDescriptor(String name, TagDescriptor td) {
	this(name);
	tag=td;
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

    public static TempDescriptor tempFactory(String name, TagDescriptor tag) {
	return new TempDescriptor(name+currentid,tag);
    }

    public static TempDescriptor paramtempFactory(String name, TypeDescriptor td) {
	return new TempDescriptor(name,td);
    }

    public static TempDescriptor paramtempFactory(String name, TagDescriptor tag) {
	return new TempDescriptor(name,tag);
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

    public TagDescriptor getTag() {
	return tag;
    }
}
