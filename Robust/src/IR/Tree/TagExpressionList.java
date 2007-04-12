package IR.Tree;
import java.util.Vector;
import IR.Flat.TempDescriptor;

public class TagExpressionList {
    Vector names;
    Vector types;
    Vector temps;

    public TagExpressionList() {
	names=new Vector();
	types=new Vector();
	temps=new Vector();
    }
    
    public void addTag(String type, String name) {
	types.add(type);
	names.add(name);
    }

    public int numTags() {
	return names.size();
    }

    public String getName(int i) {
	return (String) names.get(i);
    }

    public String getType(int i) {
	return (String) types.get(i);
    }
}
