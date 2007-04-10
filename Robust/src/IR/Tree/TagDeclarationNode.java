package IR.Tree;
import IR.TagVarDescriptor;
import IR.TagDescriptor;

public class TagDeclarationNode extends BlockStatementNode {
    String name;
    String type;
    TagVarDescriptor tvd;

    public TagDeclarationNode(String name, String type) {
	this.name=name;
	this.type=type;
	tvd=new TagVarDescriptor(new TagDescriptor(type), name);
    }
    
    public String printNode(int indent) {
	return "Tag "+name+"=new("+type+")";
    }
    
    public TagVarDescriptor getTagVarDescriptor() {
	return tvd;
    }

    public String getName() {
	return name;
    }

    public String getType() {
	return type;
    }

    public int kind() {
	return Kind.TagDeclarationNode;
    }
}
