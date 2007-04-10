package IR.Tree;
import java.util.Vector;

public class TagExpressionList {
    Vector names;
    Vector types;

    public TagExpressionList() {
	names=new Vector();
	types=new Vector();
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
