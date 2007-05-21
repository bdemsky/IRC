package IR.Tree;
import IR.TagVarDescriptor;
import IR.TagDescriptor;

public class TagDeclarationNode extends BlockStatementNode {
    String name;
    String tagtype;
    TagVarDescriptor tvd;

    public TagDeclarationNode(String name, String tagtype) {
	this.name=name;
	this.tagtype=tagtype;
	tvd=new TagVarDescriptor(new TagDescriptor(tagtype), name);
    }
    
    public String printNode(int indent) {
	return "Tag "+name+"=new("+tagtype+")";
    }
    
    public TagVarDescriptor getTagVarDescriptor() {
	return tvd;
    }

    public String getName() {
	return name;
    }

    public String getTagType() {
	return tagtype;
    }

    public int kind() {
	return Kind.TagDeclarationNode;
    }
}
