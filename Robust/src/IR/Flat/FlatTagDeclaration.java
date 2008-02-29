package IR.Flat;
import IR.TagDescriptor;

public class FlatTagDeclaration extends FlatNode {
    TempDescriptor dst;
    TagDescriptor type;
    
    public FlatTagDeclaration(TagDescriptor type, TempDescriptor dst) {
	this.type=type;
	this.dst=dst;
    }

    public String toString() {
	return "FlatTagDeclaration_"+dst.toString()+"= new Tag("+type.toString()+")";
    }

    public int kind() {
	return FKind.FlatTagDeclaration;
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[] {dst};
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[0];
    }

    public TempDescriptor getDst() {
	return dst;
    }

    public TagDescriptor getType() {
	return type;
    }
}
