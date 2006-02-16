package IR.Flat;
import IR.*;

public class TempDescriptor {
    static int currentid=0;
    int id;
    String safename;

    public TempDescriptor(String name) {
	safename="__"+name+"__";
	id=currentid++;
    }
    
    public static TempDescriptor tempFactory() {
	return new TempDescriptor("temp_"+currentid);
    }

    public static TempDescriptor tempFactory(String name) {
	return new TempDescriptor(name+currentid);
    }

    public String toString() {
	return safename;
    }
}
