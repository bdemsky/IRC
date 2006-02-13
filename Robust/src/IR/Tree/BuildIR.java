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
	    parseFieldDecl(cn,fieldnode.getChild("field_declaration"));
	    return;
	}
	ParseNode methodnode=pn.getChild("method");
	if (methodnode!=null) {
	    parseMethodDecl(cn,methodnode.getChild("method_declaration"));
	    return;
	}
	throw new Error();
    }

    private TypeDescriptor parseTypeDescriptor(ParseNode pn) {
	ParseNode tn=pn.getChild("type");
	String type_st=tn.getTerminal();
	if(type_st.equals("byte")) {
	    return state.getTypeDescriptor(TypeDescriptor.BYTE);
	} else if(type_st.equals("short")) {
	    return state.getTypeDescriptor(TypeDescriptor.SHORT);
	} else if(type_st.equals("boolean")) {
	    return state.getTypeDescriptor(TypeDescriptor.BOOLEAN);
	} else if(type_st.equals("int")) {
	    return state.getTypeDescriptor(TypeDescriptor.INT);
	} else if(type_st.equals("long")) {
	    return state.getTypeDescriptor(TypeDescriptor.LONG);
	} else if(type_st.equals("char")) {
	    return state.getTypeDescriptor(TypeDescriptor.CHAR);
	} else if(type_st.equals("float")) {
	    return state.getTypeDescriptor(TypeDescriptor.FLOAT);
	} else if(type_st.equals("double")) {
	    return state.getTypeDescriptor(TypeDescriptor.DOUBLE);
	} else if(type_st.equals("class")) {
	    ParseNode nn=tn.getChild("class");
	    return state.getTypeDescriptor(parseName(nn));
	} else {
	    throw new Error();
	}
    }

    private NameDescriptor parseName(ParseNode pn) {
	ParseNode nn=pn.getChild("name");
	ParseNode base=nn.getChild("base");
	ParseNode id=nn.getChild("identifier");
	
	if (base==null)
	    return new NameDescriptor(id.getTerminal());

	return new NameDescriptor(parseName(base),id.getTerminal());
	
    }

    private void parseFieldDecl(ClassNode cn,ParseNode pn) {
	ParseNode mn=pn.getChild("modifier");
	Modifiers m=parseModifiersList(mn);
	ParseNode tn=pn.getChild("type");
	TypeDescriptor t=parseTypeDescriptor(tn);
	ParseNode vn=pn.getChild("variables").getChild("variable_declarators_list");
	ParseNodeVector pnv=vn.getChildren();
	for(int i=0;i<pnv.size();i++) {
	    ParseNode vardecl=pnv.elementAt(i);
	    String identifier=vardecl.getChild("single").getTerminal();
	    ParseNode epn=vardecl.getChild("initializer");
	    
	    ExpressionNode en=null;
	    if (epn!=null)
		en=parseExpression(epn.getFirstChild());
  
	    cn.addField(new FieldDescriptor(m,t,identifier, en));
	}
	
    }

    private ExpressionNode parseExpression(ParseNode pn) {
	if (isNode(pn,"assignment"))
	    return parseAssignmentExpression(pn);
	else if (isNode(pn,"logical_or")||isNode(pn,"logical_and")||
		 isNode(pn,"bitwise_or")||isNode(pn,"bitwise_xor")||
		 isNode(pn,"bitwise_and")||isNode(pn,"equal")||
		 isNode(pn,"not_equal")||isNode(pn,"comp_lt")||
		 isNode(pn,"comp_lte")||isNode(pn,"comp_gt")||
		 isNode(pn,"comp_gte")||isNode(pn,"leftshift")||
		 isNode(pn,"rightshift")||isNode(pn,"sub")||
		 isNode(pn,"add")||isNode(pn,"mult")||
		 isNode(pn,"div")||isNode(pn,"mod")) {
	    ParseNodeVector pnv=pn.getChildren();
	    ParseNode left=pnv.elementAt(0);
	    ParseNode right=pnv.elementAt(1);
	    Operation op=new Operation(pn.getLabel());
   	    return new OpNode(parseExpression(left),parseExpression(right),op);
	} else if (isNode(pn,"unaryplus")||
		   isNode(pn,"unaryminus")||
		   isNode(pn,"postinc")||
		   isNode(pn,"postdec")||
		   isNode(pn,"preinc")||
		   isNode(pn,"predec")) {
	    ParseNode left=pn.getFirstChild();
	    Operation op=new Operation(pn.getLabel());
   	    return new OpNode(parseExpression(left),op);
	} else if (isNode(pn,"literal")) {
	    String literaltype=pn.getTerminal();
	    ParseNode literalnode=pn.getChild(literaltype);
	    Object literal_obj=literalnode.getLiteral();
	    return new LiteralNode(literaltype, literal_obj);
	}
	throw new Error();
    }

    private ExpressionNode parseAssignmentExpression(ParseNode pn) {
	return null;
    }


    private void parseMethodDecl(ClassNode cn, ParseNode pn) {
	ParseNode headern=pn.getChild("method_header");
	ParseNode bodyn=pn.getChild("body");
	MethodDescriptor md=parseMethodHeader(headern);
	BlockNode bn=parseBlock(bodyn);
	cn.addMethod(md);
    }

    public BlockNode parseBlock(ParseNode pn) {


    }

    public MethodDescriptor parseMethodHeader(ParseNode pn) {
	ParseNode mn=pn.getChild("modifiers");
	Modifiers m=parseModifiersList(mn);
	
	ParseNode tn=pn.getChild("returntype");
	TypeDescriptor returntype;
	if (tn!=null) 
	    returntype=parseTypeDescriptor(tn);
	else
	    returntype=new TypeDescriptor(TypeDescriptor.VOID);

	ParseNode pmd=pn.getChild("method_declarator");
	String name=pmd.getChild("name").getTerminal();
	MethodDescriptor md=new MethodDescriptor(m, returntype, name);
       
	ParseNode paramnode=pmd.getChild("parameters");
	parseParameterList(md,paramnode);
	return md;
    }

    public void parseParameterList(MethodDescriptor md, ParseNode pn) {
	ParseNode paramlist=pn.getChild("formal_parameter_list");
	if (paramlist==null)
	    return;
	 ParseNodeVector pnv=paramlist.getChildren();
	 for(int i=0;i<pnv.size();i++) {
	     ParseNode paramn=pnv.elementAt(i);
	     TypeDescriptor type=parseTypeDescriptor(paramn);
	     String paramname=paramn.getChild("single").getTerminal();
	     md.addParameter(type,paramname);
	 }
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
