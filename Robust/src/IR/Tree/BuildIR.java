package IR.Tree;
import IR.*;

public class BuildIR {
    State state;
    public BuildIR(State state) {
	this.state=state;
    }
    public void buildtree() {
	ParseNode pn=state.parsetree;
	FileNode fn=parseFile(pn);
	System.out.println(fn.printNode());
    }

    /** Parse the classes in this file */
    public FileNode parseFile(ParseNode pn) {
	FileNode fn=new FileNode();
	ParseNode tpn=pn.getChild("type_declaration_list");
	if (tpn!=null) {
	    ParseNodeVector pnv=tpn.getChildren();
	    for(int i=0;i<pnv.size();i++) {
		ParseNode type_pn=pnv.elementAt(i);
		if (isEmpty(type_pn)) /* Skip the semicolon */
		    continue;
		ClassNode cn=parseTypeDecl(type_pn);
		fn.addClass(cn);
	    }
	}
	return fn;
    }

    public ClassNode parseTypeDecl(ParseNode pn) {
	if (isNode(pn, "class_declaration")) {
	    ClassNode cn=new ClassNode();
	    cn.setName(pn.getChild("name").getTerminal());
	    if (!isEmpty(pn.getChild("super").getTerminal())) {
		/* parse superclass name */
	    }
	    cn.setModifiers(parseModifiersList(pn.getChild("modifiers")));
	    parseClassBody(cn, pn.getChild("classbody"));
	    return cn;
	} else throw new Error();
    }

    private void parseClassBody(ClassNode cn, ParseNode pn) {
	ParseNode decls=pn.getChild("class_body_declaration_list");
	if (decls!=null) {
	    ParseNodeVector pnv=decls.getChildren();
	    for(int i=0;i<pnv.size();i++) {
		ParseNode decl=pnv.elementAt(i);
		if (isNode(decl,"member")) {
		    parseClassMember(cn,decl);
		} else if (isNode(decl,"constructor")) {
		} else if (isNode(decl,"block")) {
		} else throw new Error();
	    }
	}
    }

    private void parseClassMember(ClassNode cn, ParseNode pn) {
	ParseNode fieldnode=pn.getChild("field");

	if (fieldnode!=null) {
	    FieldDescriptor fd=parseFieldDecl(fieldnode.getChild("field_declaration"));
	    cn.addField(fd);
	    return;
	}
	ParseNode methodnode=pn.getChild("method");
	if (methodnode!=null) {
	    parseMethodDecl(cn,methodnode);
	    return;
	}
	throw new Error();
    }

    private FieldDescriptor parseFieldDecl(ParseNode pn) {
	ParseNode mn=pn.getChild("modifier");
	Modifiers m=parseModifiersList(mn);
	return new FieldDescriptor(m,null,null);
    }

    private void parseMethodDecl(ClassNode cn, ParseNode pn) {
	
    }

    public Modifiers parseModifiersList(ParseNode pn) {
	Modifiers m=new Modifiers();
	ParseNode modlist=pn.getChild("modifier_list");
	if (modlist!=null) {
	    ParseNodeVector pnv=modlist.getChildren();
	    for(int i=0;i<pnv.size();i++) {
		ParseNode modn=pnv.elementAt(i);
		if (isNode(modn,"public"))
		    m.addModifier(Modifiers.PUBLIC);
		if (isNode(modn,"protected"))
		    m.addModifier(Modifiers.PROTECTED);
		if (isNode(modn,"private"))
		    m.addModifier(Modifiers.PRIVATE);
		if (isNode(modn,"static"))
		    m.addModifier(Modifiers.STATIC);
		if (isNode(modn,"final"))
		    m.addModifier(Modifiers.FINAL);
		if (isNode(modn,"native"))
		    m.addModifier(Modifiers.NATIVE);
	    }
	}
	return m;
    }

    private boolean isNode(ParseNode pn, String label) {
	if (pn.getLabel().equals(label))
	    return true;
	else return false;
    }

    private static boolean isEmpty(ParseNode pn) {
	if (pn.getLabel().equals("empty"))
	    return true;
	else
	    return false;
    }

    private static boolean isEmpty(String s) {
	if (s.equals("empty"))
	    return true;
	else
	    return false;
    }
	

    /** Throw an exception if something is unexpected */
    private void check(ParseNode pn, String label) {
        if (pn == null) {
            throw new Error(pn+ "IE: Expected '" + label + "', got null");
        }
        if (! pn.getLabel().equals(label)) {
            throw new Error(pn+ "IE: Expected '" + label + "', got '"+pn.getLabel()+"'");
        }
    }

}
