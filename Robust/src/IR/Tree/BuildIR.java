package IR.Tree;
import IR.*;

import java.util.*;


public class BuildIR {
  State state;

  private int m_taskexitnum;

  public BuildIR(State state) {
    this.state=state;
    this.m_taskexitnum = 0;
  }

  public void buildtree(ParseNode pn, Set toanalyze) {
    parseFile(pn, toanalyze);
  }

  Vector singleimports;
  Vector multiimports;
  NameDescriptor packages;

  /** Parse the classes in this file */
  public void parseFile(ParseNode pn, Set toanalyze) {
    singleimports=new Vector();
    multiimports=new Vector();

    ParseNode ipn=pn.getChild("imports").getChild("import_decls_list");
    if (ipn!=null) {
      ParseNodeVector pnv=ipn.getChildren();
      for(int i=0; i<pnv.size(); i++) {
	ParseNode pnimport=pnv.elementAt(i);
	NameDescriptor nd=parseName(pnimport.getChild("name"));
	if (isNode(pnimport,"import_single"))
	  singleimports.add(nd);
	else
	  multiimports.add(nd);
      }
    }
    ParseNode ppn=pn.getChild("packages").getChild("package");
    if (ppn!=null) {
      packages=parseName(ppn.getChild("name"));
    }
    ParseNode tpn=pn.getChild("type_declaration_list");
    if (tpn!=null) {
      ParseNodeVector pnv=tpn.getChildren();
      for(int i=0; i<pnv.size(); i++) {
	ParseNode type_pn=pnv.elementAt(i);
	if (isEmpty(type_pn))         /* Skip the semicolon */
	  continue;
	if (isNode(type_pn,"class_declaration")) {
	  ClassDescriptor cn=parseTypeDecl(type_pn);
	  parseInitializers(cn);
	  if (toanalyze!=null)
	    toanalyze.add(cn);
	  state.addClass(cn);
      // for inner classes/enum
      if(state.MGC) {
        // TODO add version for normal Java later
        HashSet tovisit = new HashSet();
        Iterator it_icds = cn.getInnerClasses();
        while(it_icds.hasNext()) {
          tovisit.add(it_icds.next());
        }
        
        while(!tovisit.isEmpty()) {
          ClassDescriptor cd = (ClassDescriptor)tovisit.iterator().next();
          tovisit.remove(cd);
	  parseInitializers(cd);
          if(toanalyze != null) {
            toanalyze.add(cd);
          }
          state.addClass(cd);
          
          Iterator it_ics = cd.getInnerClasses();
          while(it_ics.hasNext()) {
            tovisit.add(it_ics.next());
          }
          
          Iterator it_ienums = cd.getEnum();
          while(it_ienums.hasNext()) {
            ClassDescriptor iecd = (ClassDescriptor)it_ienums.next();
            if(toanalyze != null) {
              toanalyze.add(iecd);
            }
            state.addClass(iecd);
          }
        }
        
        Iterator it_enums = cn.getEnum();
        while(it_enums.hasNext()) {
          ClassDescriptor ecd = (ClassDescriptor)it_enums.next();
          if(toanalyze != null) {
            toanalyze.add(ecd);
          }
          state.addClass(ecd);
        }
      }
	} else if (isNode(type_pn,"task_declaration")) {
	  TaskDescriptor td=parseTaskDecl(type_pn);
	  if (toanalyze!=null)
	    toanalyze.add(td);
	  state.addTask(td);
	} else if ((state.MGC) && isNode(type_pn,"interface_declaration")) {
      // TODO add version for normal Java later
      ClassDescriptor cn = parseInterfaceDecl(type_pn);
      if (toanalyze!=null)
        toanalyze.add(cn);
      state.addClass(cn);
      
      // for enum
      if(state.MGC) {
        // TODO add version for normal Java later        
        Iterator it_enums = cn.getEnum();
        while(it_enums.hasNext()) {
          ClassDescriptor ecd = (ClassDescriptor)it_enums.next();
          if(toanalyze != null) {
            toanalyze.add(ecd);
          }
          state.addClass(ecd);
        }
      }
    } else if ((state.MGC) && isNode(type_pn,"enum_declaration")) {
      // TODO add version for normal Java later
      ClassDescriptor cn = parseEnumDecl(null, type_pn);
      if (toanalyze!=null)
        toanalyze.add(cn);
      state.addClass(cn);
    } else {
	  throw new Error(type_pn.getLabel());
	}
      }
    }
  }

 public void parseInitializers(ClassDescriptor cn){
	Vector fv=cn.getFieldVec();
	for(int i=0;i<fv.size();i++) {
	    FieldDescriptor fd=(FieldDescriptor)fv.get(i);
 	    if(fd.getExpressionNode()!=null) {
		Iterator methodit = cn.getMethods();
		while(methodit.hasNext()){
		    MethodDescriptor currmd=(MethodDescriptor)methodit.next();
		    if(currmd.isConstructor()){
			BlockNode bn=state.getMethodBody(currmd);
			NameNode nn=new NameNode(new NameDescriptor(fd.getSymbol()));
			AssignmentNode an=new AssignmentNode(nn,fd.getExpressionNode(),new AssignOperation(1));
			bn.addFirstBlockStatement(new BlockExpressionNode(an));			
		    }
		}
	    }
	}
    }  

  private ClassDescriptor parseEnumDecl(ClassDescriptor cn, ParseNode pn) {
    ClassDescriptor ecd=new ClassDescriptor(pn.getChild("name").getTerminal(), false);
    ecd.setAsEnum();
    if(cn != null) {
      ecd.setSurroundingClass(cn.getSymbol());
      ecd.setSurrounding(cn);
      cn.addEnum(ecd);
    }
    if (!(ecd.getSymbol().equals(TypeUtil.ObjectClass)||
        ecd.getSymbol().equals(TypeUtil.TagClass))) {
      ecd.setSuper(TypeUtil.ObjectClass);
    }
    ecd.setModifiers(parseModifiersList(pn.getChild("modifiers")));
    parseEnumBody(ecd, pn.getChild("enumbody"));
    return ecd;
  }
  
  private void parseEnumBody(ClassDescriptor cn, ParseNode pn) {
    ParseNode decls=pn.getChild("enum_constants_list");
    if (decls!=null) {
      ParseNodeVector pnv=decls.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode decl=pnv.elementAt(i);
        if (isNode(decl,"enum_constant")) {
          parseEnumConstant(cn,decl);
        } else throw new Error();
      }
    }
  }
  
  private void parseEnumConstant(ClassDescriptor cn, ParseNode pn) {
    cn.addEnumConstant(pn.getChild("name").getTerminal());
  }
  
  public ClassDescriptor parseInterfaceDecl(ParseNode pn) {
    ClassDescriptor cn=new ClassDescriptor(pn.getChild("name").getTerminal(), true);
    //cn.setAsInterface();
    if (!isEmpty(pn.getChild("superIF").getTerminal())) {
      /* parse inherited interface name */
      ParseNode snlist=pn.getChild("superIF").getChild("extend_interface_list");
      ParseNodeVector pnv=snlist.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode decl=pnv.elementAt(i);
        if (isNode(decl,"type")) {
          NameDescriptor nd=parseName(decl.getChild("class").getChild("name"));
          cn.addSuperInterface(nd.toString());
        }
      }
    }
    cn.setModifiers(parseModifiersList(pn.getChild("modifiers")));
    parseInterfaceBody(cn, pn.getChild("interfacebody"));
    return cn;
  }
  
  private void parseInterfaceBody(ClassDescriptor cn, ParseNode pn) {
    assert(cn.isInterface());
    ParseNode decls=pn.getChild("interface_member_declaration_list");
    if (decls!=null) {
      ParseNodeVector pnv=decls.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode decl=pnv.elementAt(i);
        if (isNode(decl,"constant")) {
          parseInterfaceConstant(cn,decl);
        } else if (isNode(decl,"method")) {
          parseInterfaceMethod(cn,decl.getChild("method_declaration"));
        } else throw new Error();
      }
    }
  }
  
  private void parseInterfaceConstant(ClassDescriptor cn, ParseNode pn) {
    if (pn!=null) {
      parseFieldDecl(cn,pn.getChild("field_declaration"));
      return;
    }
    throw new Error();
  }
  
  private void parseInterfaceMethod(ClassDescriptor cn, ParseNode pn) {
    ParseNode headern=pn.getChild("header");
    ParseNode bodyn=pn.getChild("body");
    MethodDescriptor md=parseMethodHeader(headern.getChild("method_header"));
    md.getModifiers().addModifier(Modifiers.PUBLIC);
    md.getModifiers().addModifier(Modifiers.ABSTRACT);
    try {
      BlockNode bn=parseBlock(bodyn);
      cn.addMethod(md);
      state.addTreeCode(md,bn);

      // this is a hack for investigating new language features
      // at the AST level, someday should evolve into a nice compiler
      // option *wink*
      //if( cn.getSymbol().equals( ***put a class in here like:     "Test" ) &&
      //    md.getSymbol().equals( ***put your method in here like: "main" ) 
      //) {
      //  bn.setStyle( BlockNode.NORMAL );
      //  System.out.println( bn.printNode( 0 ) );
      //}

    } catch (Exception e) {
      System.out.println("Error with method:"+md.getSymbol());
      e.printStackTrace();
      throw new Error();
    } catch (Error e) {
      System.out.println("Error with method:"+md.getSymbol());
      e.printStackTrace();
      throw new Error();
    }
  }

  public TaskDescriptor parseTaskDecl(ParseNode pn) {
    TaskDescriptor td=new TaskDescriptor(pn.getChild("name").getTerminal());
    ParseNode bodyn=pn.getChild("body");
    BlockNode bn=parseBlock(bodyn);
    parseParameterList(td, pn);
    state.addTreeCode(td,bn);
    if (pn.getChild("flag_effects_list")!=null)
      td.addFlagEffects(parseFlags(pn.getChild("flag_effects_list")));
    return td;
  }

  public Vector parseFlags(ParseNode pn) {
    Vector vfe=new Vector();
    ParseNodeVector pnv=pn.getChildren();
    for(int i=0; i<pnv.size(); i++) {
      ParseNode fn=pnv.elementAt(i);
      FlagEffects fe=parseFlagEffects(fn);
      vfe.add(fe);
    }
    return vfe;
  }

  public FlagEffects parseFlagEffects(ParseNode pn) {
    if (isNode(pn,"flag_effect")) {
      String flagname=pn.getChild("name").getTerminal();
      FlagEffects fe=new FlagEffects(flagname);
      if (pn.getChild("flag_list")!=null)
	parseFlagEffect(fe, pn.getChild("flag_list"));
      if (pn.getChild("tag_list")!=null)
	parseTagEffect(fe, pn.getChild("tag_list"));
      return fe;
    } else throw new Error();
  }

  public void parseTagEffect(FlagEffects fes, ParseNode pn) {
    ParseNodeVector pnv=pn.getChildren();
    for(int i=0; i<pnv.size(); i++) {
      ParseNode pn2=pnv.elementAt(i);
      boolean status=true;
      if (isNode(pn2,"not")) {
	status=false;
	pn2=pn2.getChild("name");
      }
      String name=pn2.getTerminal();
      fes.addTagEffect(new TagEffect(name,status));
    }
  }

  public void parseFlagEffect(FlagEffects fes, ParseNode pn) {
    ParseNodeVector pnv=pn.getChildren();
    for(int i=0; i<pnv.size(); i++) {
      ParseNode pn2=pnv.elementAt(i);
      boolean status=true;
      if (isNode(pn2,"not")) {
	status=false;
	pn2=pn2.getChild("name");
      }
      String name=pn2.getTerminal();
      fes.addEffect(new FlagEffect(name,status));
    }
  }

  public FlagExpressionNode parseFlagExpression(ParseNode pn) {
    if (isNode(pn,"or")) {
      ParseNodeVector pnv=pn.getChildren();
      ParseNode left=pnv.elementAt(0);
      ParseNode right=pnv.elementAt(1);
      return new FlagOpNode(parseFlagExpression(left), parseFlagExpression(right), new Operation(Operation.LOGIC_OR));
    } else if (isNode(pn,"and")) {
      ParseNodeVector pnv=pn.getChildren();
      ParseNode left=pnv.elementAt(0);
      ParseNode right=pnv.elementAt(1);
      return new FlagOpNode(parseFlagExpression(left), parseFlagExpression(right), new Operation(Operation.LOGIC_AND));
    } else if (isNode(pn, "not")) {
      ParseNodeVector pnv=pn.getChildren();
      ParseNode left=pnv.elementAt(0);
      return new FlagOpNode(parseFlagExpression(left), new Operation(Operation.LOGIC_NOT));

    } else if (isNode(pn,"name")) {
      return new FlagNode(pn.getTerminal());
    } else {
      throw new Error();
    }
  }

  public Vector parseChecks(ParseNode pn) {
    Vector ccs=new Vector();
    ParseNodeVector pnv=pn.getChildren();
    for(int i=0; i<pnv.size(); i++) {
      ParseNode fn=pnv.elementAt(i);
      ConstraintCheck cc=parseConstraintCheck(fn);
      ccs.add(cc);
    }
    return ccs;
  }

  public ConstraintCheck parseConstraintCheck(ParseNode pn) {
    if (isNode(pn,"cons_check")) {
      String specname=pn.getChild("name").getChild("identifier").getTerminal();
      Vector[] args=parseConsArgumentList(pn);
      ConstraintCheck cc=new ConstraintCheck(specname);
      for(int i=0; i<args[0].size(); i++) {
	cc.addVariable((String)args[0].get(i));
	cc.addArgument((ExpressionNode)args[1].get(i));
      }
      return cc;
    } else throw new Error();
  }

  public void parseParameterList(TaskDescriptor td, ParseNode pn) {

    boolean optional;
    ParseNode paramlist=pn.getChild("task_parameter_list");
    if (paramlist==null)
      return;
    ParseNodeVector pnv=paramlist.getChildren();
    for(int i=0; i<pnv.size(); i++) {
      ParseNode paramn=pnv.elementAt(i);
      if(paramn.getChild("optional")!=null) {
	optional = true;
	paramn = paramn.getChild("optional").getFirstChild();
	System.out.println("OPTIONAL FOUND!!!!!!!");
      } else { optional = false;
	       System.out.println("NOT OPTIONAL");}

      TypeDescriptor type=parseTypeDescriptor(paramn);

      String paramname=paramn.getChild("single").getTerminal();
      FlagExpressionNode fen=null;
      if (paramn.getChild("flag")!=null)
	fen=parseFlagExpression(paramn.getChild("flag").getFirstChild());

      ParseNode tagnode=paramn.getChild("tag");

      TagExpressionList tel=null;
      if (tagnode!=null) {
	tel=parseTagExpressionList(tagnode);
      }

      td.addParameter(type,paramname,fen, tel, optional);
    }
  }

  public TagExpressionList parseTagExpressionList(ParseNode pn) {
    //BUG FIX: change pn.getChildren() to pn.getChild("tag_expression_list").getChildren()
    //To test, feed in any input program that uses tags
    ParseNodeVector pnv=pn.getChild("tag_expression_list").getChildren();
    TagExpressionList tel=new TagExpressionList();
    for(int i=0; i<pnv.size(); i++) {
      ParseNode tn=pnv.elementAt(i);
      String type=tn.getChild("type").getTerminal();
      String name=tn.getChild("single").getTerminal();
      tel.addTag(type, name);
    }
    return tel;
  }

  public ClassDescriptor parseTypeDecl(ParseNode pn) {
    ClassDescriptor cn=new ClassDescriptor(pn.getChild("name").getTerminal(), false);
    if (!isEmpty(pn.getChild("super").getTerminal())) {
      /* parse superclass name */
      ParseNode snn=pn.getChild("super").getChild("type").getChild("class").getChild("name");
      NameDescriptor nd=parseName(snn);
      cn.setSuper(nd.toString());
    } else {
      if (!(cn.getSymbol().equals(TypeUtil.ObjectClass)||
            cn.getSymbol().equals(TypeUtil.TagClass)))
	cn.setSuper(TypeUtil.ObjectClass);
    }
    // check inherited interfaces
    if (!isEmpty(pn.getChild("superIF").getTerminal())) {
      /* parse inherited interface name */
      ParseNode snlist=pn.getChild("superIF").getChild("interface_type_list");
      ParseNodeVector pnv=snlist.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode decl=pnv.elementAt(i);
        if (isNode(decl,"type")) {
          NameDescriptor nd=parseName(decl.getChild("class").getChild("name"));
          cn.addSuperInterface(nd.toString());
        }
      }
    }
    cn.setModifiers(parseModifiersList(pn.getChild("modifiers")));
    parseClassBody(cn, pn.getChild("classbody"));
    return cn;
  }

  private void parseClassBody(ClassDescriptor cn, ParseNode pn) {
    ParseNode decls=pn.getChild("class_body_declaration_list");
    if (decls!=null) {
      ParseNodeVector pnv=decls.getChildren();
      for(int i=0; i<pnv.size(); i++) {
	ParseNode decl=pnv.elementAt(i);
	if (isNode(decl,"member")) {
	  parseClassMember(cn,decl);
	} else if (isNode(decl,"constructor")) {
	  parseConstructorDecl(cn,decl.getChild("constructor_declaration"));
	} else if (isNode(decl, "static_block")) {
      if(state.MGC) {
        // TODO add version for normal Java later
      parseStaticBlockDecl(cn, decl.getChild("static_block_declaration"));
      } else {
        throw new Error("Static blocks not implemented");
      }
    } else if (isNode(decl,"block")) {
	} else throw new Error();
      }
    }
  }
  
  private void parseClassMember(ClassDescriptor cn, ParseNode pn) {
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
    ParseNode innerclassnode=pn.getChild("inner_class_declaration");
    if (innerclassnode!=null) {
      if(state.MGC){
        parseInnerClassDecl(cn,innerclassnode);
        return;
      } else {
        // TODO add version for noraml Java later
        throw new Error("Error: inner class in Class " + cn.getSymbol() + " is not supported yet");
      }
    }
    ParseNode enumnode=pn.getChild("enum_declaration");
    if (enumnode!=null) {
      if(state.MGC){
        parseEnumDecl(cn,enumnode);
        return;
      } else {
        // TODO add version for noraml Java later
        throw new Error("Error: enumerated type in Class " + cn.getSymbol() + " is not supported yet");
      }
    }
    ParseNode flagnode=pn.getChild("flag");
    if (flagnode!=null) {
      parseFlagDecl(cn, flagnode.getChild("flag_declaration"));
      return;
    }
    // in case there are empty node
    ParseNode emptynode=pn.getChild("empty");
    if(emptynode != null) {
      return;
    }
    throw new Error();
  }
  
  private ClassDescriptor parseInnerClassDecl(ClassDescriptor cn, ParseNode pn) {
    ClassDescriptor icn=new ClassDescriptor(pn.getChild("name").getTerminal(), false);
    icn.setAsInnerClass();
    icn.setSurroundingClass(cn.getSymbol());
    icn.setSurrounding(cn);
    cn.addInnerClass(icn);
    if (!isEmpty(pn.getChild("super").getTerminal())) {
      /* parse superclass name */
      ParseNode snn=pn.getChild("super").getChild("type").getChild("class").getChild("name");
      NameDescriptor nd=parseName(snn);
      icn.setSuper(nd.toString());
    } else {
      if (!(icn.getSymbol().equals(TypeUtil.ObjectClass)||
          icn.getSymbol().equals(TypeUtil.TagClass)))
        icn.setSuper(TypeUtil.ObjectClass);
    }
    // check inherited interfaces
    if (!isEmpty(pn.getChild("superIF").getTerminal())) {
      /* parse inherited interface name */
      ParseNode snlist=pn.getChild("superIF").getChild("interface_type_list");
      ParseNodeVector pnv=snlist.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode decl=pnv.elementAt(i);
        if (isNode(decl,"type")) {
          NameDescriptor nd=parseName(decl.getChild("class").getChild("name"));
          icn.addSuperInterface(nd.toString());
        }
      }
    }
    icn.setModifiers(parseModifiersList(pn.getChild("modifiers")));
    if(!icn.isStatic()) {
      throw new Error("Error: inner class " + icn.getSymbol() + " in Class " + 
          cn.getSymbol() + " is not a nested class and is not supported yet!");
    }
    parseClassBody(icn, pn.getChild("classbody"));
    return icn;
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
      return state.getTypeDescriptor(parseName(nn.getChild("name")));
    } else if(type_st.equals("array")) {
      ParseNode nn=tn.getChild("array");
      TypeDescriptor td=parseTypeDescriptor(nn.getChild("basetype"));
      Integer numdims=(Integer)nn.getChild("dims").getLiteral();
      for(int i=0; i<numdims.intValue(); i++)
	td=td.makeArray(state);
      return td;
    } else {
      System.out.println(pn.PPrint(2, true));
      throw new Error();
    }
  }

  private NameDescriptor parseName(ParseNode nn) {
    ParseNode base=nn.getChild("base");
    ParseNode id=nn.getChild("identifier");
    if (base==null)
      return new NameDescriptor(id.getTerminal());
    return new NameDescriptor(parseName(base.getChild("name")),id.getTerminal());

  }

  private void parseFlagDecl(ClassDescriptor cn,ParseNode pn) {
    String name=pn.getChild("name").getTerminal();
    FlagDescriptor flag=new FlagDescriptor(name);
    if (pn.getChild("external")!=null)
      flag.makeExternal();
    cn.addFlag(flag);
  }

  private void parseFieldDecl(ClassDescriptor cn,ParseNode pn) {
    ParseNode mn=pn.getChild("modifier");
    Modifiers m=parseModifiersList(mn);
    if((state.MGC) && cn.isInterface()) {
      // TODO add version for normal Java later
      // Can only be PUBLIC or STATIC or FINAL
      if((m.isAbstract()) || (m.isAtomic()) || (m.isNative()) 
          || (m.isSynchronized())) {
        throw new Error("Error: field in Interface " + cn.getSymbol() + "can only be PUBLIC or STATIC or FINAL");
      }
      m.addModifier(Modifiers.PUBLIC);
      m.addModifier(Modifiers.STATIC);
      m.addModifier(Modifiers.FINAL);
    }

    ParseNode tn=pn.getChild("type");
    TypeDescriptor t=parseTypeDescriptor(tn);
    ParseNode vn=pn.getChild("variables").getChild("variable_declarators_list");
    ParseNodeVector pnv=vn.getChildren();
    boolean isglobal=pn.getChild("global")!=null;

    for(int i=0; i<pnv.size(); i++) {
      ParseNode vardecl=pnv.elementAt(i);
      ParseNode tmp=vardecl;
      TypeDescriptor arrayt=t;
      while (tmp.getChild("single")==null) {
	arrayt=arrayt.makeArray(state);
	tmp=tmp.getChild("array");
      }
      String identifier=tmp.getChild("single").getTerminal();
      ParseNode epn=vardecl.getChild("initializer");

      ExpressionNode en=null;
      if (epn!=null) {
        en=parseExpression(epn.getFirstChild());
        if(state.MGC && m.isStatic()) {
          // for static field, the initializer should be considered as a 
          // static block
          boolean isfirst = false;
          MethodDescriptor md = (MethodDescriptor)cn.getMethodTable().get("staticblocks");
          if(md == null) {
            // the first static block for this class
            Modifiers m_i=new Modifiers();
            m_i.addModifier(Modifiers.STATIC);
            md = new MethodDescriptor(m_i, "staticblocks", false);
            md.setAsStaticBlock();
            isfirst = true;
          }
          if(isfirst) {
            cn.addMethod(md);
          }
          cn.incStaticBlocks();
          BlockNode bn=new BlockNode();
          NameNode nn=new NameNode(new NameDescriptor(identifier));
          AssignmentNode an=new AssignmentNode(nn,en,new AssignOperation(1));
          bn.addBlockStatement(new BlockExpressionNode(an));
          if(isfirst) {
            state.addTreeCode(md,bn);
          } else {
            BlockNode obn = state.getMethodBody(md);
            for(int ii = 0; ii < bn.size(); ii++) {
              BlockStatementNode bsn = bn.get(ii);
              obn.addBlockStatement(bsn);
            }
            //TODO state.addTreeCode(md, obn);
            bn = null;
          }
          en = null;
        }
      }

      cn.addField(new FieldDescriptor(m, arrayt, identifier, en, isglobal));
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
             isNode(pn,"urightshift")||isNode(pn,"sub")||
             isNode(pn,"add")||isNode(pn,"mult")||
             isNode(pn,"div")||isNode(pn,"mod")) {
      ParseNodeVector pnv=pn.getChildren();
      ParseNode left=pnv.elementAt(0);
      ParseNode right=pnv.elementAt(1);
      Operation op=new Operation(pn.getLabel());
      return new OpNode(parseExpression(left),parseExpression(right),op);
    } else if (isNode(pn,"unaryplus")||
               isNode(pn,"unaryminus")||
               isNode(pn,"not")||
               isNode(pn,"comp")) {
      ParseNode left=pn.getFirstChild();
      Operation op=new Operation(pn.getLabel());
      return new OpNode(parseExpression(left),op);
    } else if (isNode(pn,"postinc")||
               isNode(pn,"postdec")) {
      ParseNode left=pn.getFirstChild();
      AssignOperation op=new AssignOperation(pn.getLabel());
      return new AssignmentNode(parseExpression(left),null,op);

    } else if (isNode(pn,"preinc")||
               isNode(pn,"predec")) {
      ParseNode left=pn.getFirstChild();
      AssignOperation op=isNode(pn,"preinc") ? new AssignOperation(AssignOperation.PLUSEQ) : new AssignOperation(AssignOperation.MINUSEQ);
      return new AssignmentNode(parseExpression(left),
                                new LiteralNode("integer",new Integer(1)),op);
    } else if (isNode(pn,"literal")) {
      String literaltype=pn.getTerminal();
      ParseNode literalnode=pn.getChild(literaltype);
      Object literal_obj=literalnode.getLiteral();
      return new LiteralNode(literaltype, literal_obj);
    } else if (isNode(pn,"createobject")) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      
      Vector args=parseArgumentList(pn);
      boolean isglobal=pn.getChild("global")!=null||
	pn.getChild("scratch")!=null;
      String disjointId=null;
      if( pn.getChild("disjoint") != null) {
	disjointId = pn.getChild("disjoint").getTerminal();
      }
      CreateObjectNode con=new CreateObjectNode(td, isglobal, disjointId);
      for(int i=0; i<args.size(); i++) {
	con.addArgument((ExpressionNode)args.get(i));
      }
      /* Could have flag set or tag added here */
      if (pn.getChild("flag_list")!=null||pn.getChild("tag_list")!=null) {
	FlagEffects fe=new FlagEffects(null);
	if (pn.getChild("flag_list")!=null)
	  parseFlagEffect(fe, pn.getChild("flag_list"));

	if (pn.getChild("tag_list")!=null)
	  parseTagEffect(fe, pn.getChild("tag_list"));
	con.addFlagEffects(fe);
      }

      return con;
    } else if (isNode(pn,"createarray")) {
      //System.out.println(pn.PPrint(3,true));
      boolean isglobal=pn.getChild("global")!=null||
	pn.getChild("scratch")!=null;
      String disjointId=null;
      if( pn.getChild("disjoint") != null) {
	disjointId = pn.getChild("disjoint").getTerminal();
      }
      TypeDescriptor td=parseTypeDescriptor(pn);
      Vector args=parseDimExprs(pn);
      int num=0;
      if (pn.getChild("dims_opt").getLiteral()!=null)
	num=((Integer)pn.getChild("dims_opt").getLiteral()).intValue();
      for(int i=0; i<(args.size()+num); i++)
	td=td.makeArray(state);
      CreateObjectNode con=new CreateObjectNode(td, isglobal, disjointId);
      for(int i=0; i<args.size(); i++) {
	con.addArgument((ExpressionNode)args.get(i));
      }
      return con;
    } if (isNode(pn,"createarray2") && state.MGC) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      int num=0;
      if (pn.getChild("dims_opt").getLiteral()!=null)
    num=((Integer)pn.getChild("dims_opt").getLiteral()).intValue();
      for(int i=0; i<num; i++)
    td=td.makeArray(state);
      CreateObjectNode con=new CreateObjectNode(td, false, null);
      // TODO array initializers
      ParseNode ipn = pn.getChild("initializer");     
      Vector initializers=parseVariableInitializerList(ipn);
      ArrayInitializerNode ain = new ArrayInitializerNode(initializers);
      con.addArrayInitializer(ain);
      return con;
    } else if (isNode(pn,"name")) {
      NameDescriptor nd=parseName(pn);
      return new NameNode(nd);
    } else if (isNode(pn,"this")) {
      NameDescriptor nd=new NameDescriptor("this");
      return new NameNode(nd);
    } else if (isNode(pn,"isavailable")) {
      NameDescriptor nd=new NameDescriptor(pn.getTerminal());
      return new OpNode(new NameNode(nd),null,new Operation(Operation.ISAVAILABLE));
    } else if (isNode(pn,"methodinvoke1")) {
      NameDescriptor nd=parseName(pn.getChild("name"));
      Vector args=parseArgumentList(pn);
      MethodInvokeNode min=new MethodInvokeNode(nd);
      for(int i=0; i<args.size(); i++) {
	min.addArgument((ExpressionNode)args.get(i));
      }
      return min;
    } else if (isNode(pn,"methodinvoke2")) {
      String methodid=pn.getChild("id").getTerminal();
      ExpressionNode exp=parseExpression(pn.getChild("base").getFirstChild());
      Vector args=parseArgumentList(pn);
      MethodInvokeNode min=new MethodInvokeNode(methodid,exp);
      for(int i=0; i<args.size(); i++) {
	min.addArgument((ExpressionNode)args.get(i));
      }
      return min;
    } else if (isNode(pn,"fieldaccess")) {
      ExpressionNode en=parseExpression(pn.getChild("base").getFirstChild());
      String fieldname=pn.getChild("field").getTerminal();
      return new FieldAccessNode(en,fieldname);
    } else if (isNode(pn,"arrayaccess")) {
      ExpressionNode en=parseExpression(pn.getChild("base").getFirstChild());
      ExpressionNode index=parseExpression(pn.getChild("index").getFirstChild());
      return new ArrayAccessNode(en,index);
    } else if (isNode(pn,"cast1")) {
      try {
	return new CastNode(parseTypeDescriptor(pn.getChild("type")),parseExpression(pn.getChild("exp").getFirstChild()));
      } catch (Exception e) {
	System.out.println(pn.PPrint(1,true));
	e.printStackTrace();
	throw new Error();
      }
    } else if (isNode(pn,"cast2")) {
      return new CastNode(parseExpression(pn.getChild("type").getFirstChild()),parseExpression(pn.getChild("exp").getFirstChild()));
    } else if (isNode(pn, "getoffset")) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      String fieldname = pn.getChild("field").getTerminal();
      //System.out.println("Checking the values of: "+ " td.toString()= " + td.toString()+ "  fieldname= " + fieldname);
      return new OffsetNode(td, fieldname);
    } else if (isNode(pn, "tert")) {
      return new TertiaryNode(parseExpression(pn.getChild("cond").getFirstChild()),
			      parseExpression(pn.getChild("trueexpr").getFirstChild()),
			      parseExpression(pn.getChild("falseexpr").getFirstChild()) );
    } else if (isNode(pn, "instanceof")) {
      ExpressionNode exp=parseExpression(pn.getChild("exp").getFirstChild());
      TypeDescriptor t=parseTypeDescriptor(pn);
      return new InstanceOfNode(exp,t);
    } else if (isNode(pn, "array_initializer")) {  
      Vector initializers=parseVariableInitializerList(pn);
      return new ArrayInitializerNode(initializers);
    } else if (isNode(pn, "class_type") && state.MGC) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      return new ClassTypeNode(td);
    } else if (isNode(pn, "empty") && state.MGC) {
      return null;
    } else {
      System.out.println("---------------------");
      System.out.println(pn.PPrint(3,true));
      throw new Error();
    }
  }

  private Vector parseDimExprs(ParseNode pn) {
    Vector arglist=new Vector();
    ParseNode an=pn.getChild("dim_exprs");
    if (an==null)       /* No argument list */
      return arglist;
    ParseNodeVector anv=an.getChildren();
    for(int i=0; i<anv.size(); i++) {
      arglist.add(parseExpression(anv.elementAt(i)));
    }
    return arglist;
  }

  private Vector parseArgumentList(ParseNode pn) {
    Vector arglist=new Vector();
    ParseNode an=pn.getChild("argument_list");
    if (an==null)       /* No argument list */
      return arglist;
    ParseNodeVector anv=an.getChildren();
    for(int i=0; i<anv.size(); i++) {
      arglist.add(parseExpression(anv.elementAt(i)));
    }
    return arglist;
  }

  private Vector[] parseConsArgumentList(ParseNode pn) {
    Vector arglist=new Vector();
    Vector varlist=new Vector();
    ParseNode an=pn.getChild("cons_argument_list");
    if (an==null)       /* No argument list */
      return new Vector[] {varlist, arglist};
    ParseNodeVector anv=an.getChildren();
    for(int i=0; i<anv.size(); i++) {
      ParseNode cpn=anv.elementAt(i);
      ParseNode var=cpn.getChild("var");
      ParseNode exp=cpn.getChild("exp").getFirstChild();
      varlist.add(var.getTerminal());
      arglist.add(parseExpression(exp));
    }
    return new Vector[] {varlist, arglist};
  }

  private Vector parseVariableInitializerList(ParseNode pn) {
    Vector varInitList=new Vector();
    ParseNode vin=pn.getChild("var_init_list");
    if (vin==null)       /* No argument list */
      return varInitList;
    ParseNodeVector vinv=vin.getChildren();
    for(int i=0; i<vinv.size(); i++) {
      varInitList.add(parseExpression(vinv.elementAt(i)));
    }
    return varInitList;
  }

  private ExpressionNode parseAssignmentExpression(ParseNode pn) {
    AssignOperation ao=new AssignOperation(pn.getChild("op").getTerminal());
    ParseNodeVector pnv=pn.getChild("args").getChildren();

    AssignmentNode an=new AssignmentNode(parseExpression(pnv.elementAt(0)),parseExpression(pnv.elementAt(1)),ao);
    return an;
  }


  private void parseMethodDecl(ClassDescriptor cn, ParseNode pn) {
    ParseNode headern=pn.getChild("method_header");
    ParseNode bodyn=pn.getChild("body");
    MethodDescriptor md=parseMethodHeader(headern);
    try {
      BlockNode bn=parseBlock(bodyn);
      cn.addMethod(md);
      state.addTreeCode(md,bn);

      // this is a hack for investigating new language features
      // at the AST level, someday should evolve into a nice compiler
      // option *wink*
      //if( cn.getSymbol().equals( ***put a class in here like:     "Test" ) &&
      //    md.getSymbol().equals( ***put your method in here like: "main" ) 
      //) {
      //  bn.setStyle( BlockNode.NORMAL );
      //  System.out.println( bn.printNode( 0 ) );
      //}

    } catch (Exception e) {
      System.out.println("Error with method:"+md.getSymbol());
      e.printStackTrace();
      throw new Error();
    } catch (Error e) {
      System.out.println("Error with method:"+md.getSymbol());
      e.printStackTrace();
      throw new Error();
    }
  }

  private void parseConstructorDecl(ClassDescriptor cn, ParseNode pn) {
    ParseNode mn=pn.getChild("modifiers");
    Modifiers m=parseModifiersList(mn);
    ParseNode cdecl=pn.getChild("constructor_declarator");
    boolean isglobal=cdecl.getChild("global")!=null;
    String name=cdecl.getChild("name").getChild("identifier").getTerminal();
    MethodDescriptor md=new MethodDescriptor(m, name, isglobal);
    ParseNode paramnode=cdecl.getChild("parameters");
    parseParameterList(md,paramnode);
    ParseNode bodyn0=pn.getChild("body");
    ParseNode bodyn=bodyn0.getChild("constructor_body");
    cn.addMethod(md);
    BlockNode bn=null;
    if (bodyn!=null&&bodyn.getChild("block_statement_list")!=null)
      bn=parseBlock(bodyn);
    else
      bn=new BlockNode();
    if (bodyn!=null&&bodyn.getChild("superinvoke")!=null) {
      ParseNode sin=bodyn.getChild("superinvoke");
      NameDescriptor nd=new NameDescriptor("super");
      Vector args=parseArgumentList(sin);
      MethodInvokeNode min=new MethodInvokeNode(nd);
      for(int i=0; i<args.size(); i++) {
	min.addArgument((ExpressionNode)args.get(i));
      }
      BlockExpressionNode ben=new BlockExpressionNode(min);
      bn.addFirstBlockStatement(ben);

    } else if (bodyn!=null&&bodyn.getChild("explconstrinv")!=null) {
      ParseNode eci=bodyn.getChild("explconstrinv");
      NameDescriptor nd=new NameDescriptor(cn.getSymbol());
      Vector args=parseArgumentList(eci);
      MethodInvokeNode min=new MethodInvokeNode(nd);
      for(int i=0; i<args.size(); i++) {
	min.addArgument((ExpressionNode)args.get(i));
      }
      BlockExpressionNode ben=new BlockExpressionNode(min);
      bn.addFirstBlockStatement(ben);
    }
    state.addTreeCode(md,bn);
  }
  
  private void parseStaticBlockDecl(ClassDescriptor cn, ParseNode pn) {
    // Each class maintains one MethodDecscriptor which combines all its 
    // static blocks in their declaration order
    boolean isfirst = false;
    MethodDescriptor md = (MethodDescriptor)cn.getMethodTable().get("staticblocks");
    if(md == null) {
      // the first static block for this class
      Modifiers m=new Modifiers();
      m.addModifier(Modifiers.STATIC);
      md = new MethodDescriptor(m, "staticblocks", false);
      md.setAsStaticBlock();
      isfirst = true;
    }
    ParseNode bodyn=pn.getChild("body");
    if(isfirst) {
      cn.addMethod(md);
    }
    cn.incStaticBlocks();
    BlockNode bn=null;
    if (bodyn!=null&&bodyn.getChild("block_statement_list")!=null)
      bn=parseBlock(bodyn);
    else
      bn=new BlockNode();
    if(isfirst) {
      state.addTreeCode(md,bn);
    } else {
      BlockNode obn = state.getMethodBody(md);
      for(int i = 0; i < bn.size(); i++) {
        BlockStatementNode bsn = bn.get(i);
        obn.addBlockStatement(bsn);
      }
      //TODO state.addTreeCode(md, obn);
      bn = null;
    }
  }

  public BlockNode parseBlock(ParseNode pn) {
    this.m_taskexitnum = 0;
    if (pn==null||isEmpty(pn.getTerminal()))
      return new BlockNode();
    ParseNode bsn=pn.getChild("block_statement_list");
    return parseBlockHelper(bsn);
  }

  private BlockNode parseBlockHelper(ParseNode pn) {
    ParseNodeVector pnv=pn.getChildren();
    BlockNode bn=new BlockNode();
    for(int i=0; i<pnv.size(); i++) {
      Vector bsv=parseBlockStatement(pnv.elementAt(i));
      for(int j=0; j<bsv.size(); j++) {
	bn.addBlockStatement((BlockStatementNode)bsv.get(j));
      }
    }
    return bn;
  }

  public BlockNode parseSingleBlock(ParseNode pn) {
    BlockNode bn=new BlockNode();
    Vector bsv=parseBlockStatement(pn);
    for(int j=0; j<bsv.size(); j++) {
      bn.addBlockStatement((BlockStatementNode)bsv.get(j));
    }
    bn.setStyle(BlockNode.NOBRACES);
    return bn;
  }

  public Vector parseSESEBlock(Vector parentbs, ParseNode pn) {
    ParseNodeVector pnv=pn.getChildren();
    Vector bv=new Vector();
    for(int i=0; i<pnv.size(); i++) {
      bv.addAll(parseBlockStatement(pnv.elementAt(i)));
    }
    return bv;
  }

  public Vector parseBlockStatement(ParseNode pn) {
    Vector blockstatements=new Vector();
    if (isNode(pn,"tag_declaration")) {
      String name=pn.getChild("single").getTerminal();
      String type=pn.getChild("type").getTerminal();

      blockstatements.add(new TagDeclarationNode(name, type));
    } else if (isNode(pn,"local_variable_declaration")) {
      TypeDescriptor t=parseTypeDescriptor(pn);
      ParseNode vn=pn.getChild("variable_declarators_list");
      ParseNodeVector pnv=vn.getChildren();
      for(int i=0; i<pnv.size(); i++) {
	ParseNode vardecl=pnv.elementAt(i);


	ParseNode tmp=vardecl;
	TypeDescriptor arrayt=t;
	while (tmp.getChild("single")==null) {
	  arrayt=arrayt.makeArray(state);
	  tmp=tmp.getChild("array");
	}
	String identifier=tmp.getChild("single").getTerminal();

	ParseNode epn=vardecl.getChild("initializer");


	ExpressionNode en=null;
	if (epn!=null)
	  en=parseExpression(epn.getFirstChild());

	blockstatements.add(new DeclarationNode(new VarDescriptor(arrayt, identifier),en));
      }
    } else if (isNode(pn,"nop")) {
      /* Do Nothing */
    } else if (isNode(pn,"expression")) {
      blockstatements.add(new BlockExpressionNode(parseExpression(pn.getFirstChild())));
    } else if (isNode(pn,"ifstatement")) {
      blockstatements.add(new IfStatementNode(parseExpression(pn.getChild("condition").getFirstChild()),
                                              parseSingleBlock(pn.getChild("statement").getFirstChild()),
                                              pn.getChild("else_statement")!=null ? parseSingleBlock(pn.getChild("else_statement").getFirstChild()) : null));
    } else if ((state.MGC) && (isNode(pn,"switch_statement"))) {
      // TODO add version for normal Java later
      blockstatements.add(new SwitchStatementNode(parseExpression(pn.getChild("condition").getFirstChild()),
          parseSingleBlock(pn.getChild("statement").getFirstChild())));
    } else if ((state.MGC) && (isNode(pn,"switch_block_list"))) {
      // TODO add version for normal Java later
      ParseNodeVector pnv=pn.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode sblockdecl=pnv.elementAt(i);
        
        if(isNode(sblockdecl, "switch_block")) {
          ParseNode lpn=sblockdecl.getChild("switch_labels").getChild("switch_label_list");
          ParseNodeVector labelv=lpn.getChildren();
          Vector<SwitchLabelNode> slv = new Vector<SwitchLabelNode>();
          for(int j=0; j<labelv.size(); j++) {
            ParseNode labeldecl=labelv.elementAt(j);
            if(isNode(labeldecl, "switch_label")) {
              slv.addElement(new SwitchLabelNode(parseExpression(labeldecl.getChild("constant_expression").getFirstChild()), false));
            } else if(isNode(labeldecl, "default_switch_label")) {
              slv.addElement(new SwitchLabelNode(null, true));
            }
          }
          
          blockstatements.add(new SwitchBlockNode(slv, 
              parseSingleBlock(sblockdecl.getChild("switch_statements").getFirstChild())));
          
        }
      }
    } else if (state.MGC && isNode(pn, "trycatchstatement")) {
      // TODO add version for normal Java later
      // Do not fully support exceptions now. Only make sure that if there are no
      // exceptions thrown, the execution is right
      ParseNode tpn = pn.getChild("tryblock").getFirstChild();
      BlockNode bn=parseBlockHelper(tpn);
      blockstatements.add(new SubBlockNode(bn));
      
      ParseNode fbk = pn.getChild("finallyblock");
      if(fbk != null) {
        ParseNode fpn = fbk.getFirstChild();
        BlockNode fbn=parseBlockHelper(fpn);
        blockstatements.add(new SubBlockNode(fbn));
      }
    } else if (isNode(pn, "throwstatement")) {
      // TODO Simply return here
      blockstatements.add(new ReturnNode());
    } else if (isNode(pn,"taskexit")) {
      Vector vfe=null;
      if (pn.getChild("flag_effects_list")!=null)
	vfe=parseFlags(pn.getChild("flag_effects_list"));
      Vector ccs=null;
      if (pn.getChild("cons_checks")!=null)
	ccs=parseChecks(pn.getChild("cons_checks"));

      blockstatements.add(new TaskExitNode(vfe, ccs, this.m_taskexitnum++));
    } else if (isNode(pn,"atomic")) {
      BlockNode bn=parseBlockHelper(pn);
      blockstatements.add(new AtomicNode(bn));
    } else if (isNode(pn,"synchronized")) {
      BlockNode bn=parseBlockHelper(pn.getChild("block"));
      ExpressionNode en=parseExpression(pn.getChild("expr").getFirstChild());
      blockstatements.add(new SynchronizedNode(en, bn));
    } else if (isNode(pn,"return")) {
      if (isEmpty(pn.getTerminal()))
	blockstatements.add(new ReturnNode());
      else {
	ExpressionNode en=parseExpression(pn.getFirstChild());
	blockstatements.add(new ReturnNode(en));
      }
    } else if (isNode(pn,"block_statement_list")) {
      BlockNode bn=parseBlockHelper(pn);
      blockstatements.add(new SubBlockNode(bn));
    } else if (isNode(pn,"empty")) {
      /* nop */
    } else if (isNode(pn,"statement_expression_list")) {
      ParseNodeVector pnv=pn.getChildren();
      BlockNode bn=new BlockNode();
      for(int i=0; i<pnv.size(); i++) {
	ExpressionNode en=parseExpression(pnv.elementAt(i));
	blockstatements.add(new BlockExpressionNode(en));
      }
      bn.setStyle(BlockNode.EXPRLIST);
    } else if (isNode(pn,"forstatement")) {
      BlockNode init=parseSingleBlock(pn.getChild("initializer").getFirstChild());
      BlockNode update=parseSingleBlock(pn.getChild("update").getFirstChild());
      ExpressionNode condition=parseExpression(pn.getChild("condition").getFirstChild());
      BlockNode body=parseSingleBlock(pn.getChild("statement").getFirstChild());
      blockstatements.add(new LoopNode(init,condition,update,body));
    } else if (isNode(pn,"whilestatement")) {
      ExpressionNode condition=parseExpression(pn.getChild("condition").getFirstChild());
      BlockNode body=parseSingleBlock(pn.getChild("statement").getFirstChild());
      blockstatements.add(new LoopNode(condition,body,LoopNode.WHILELOOP));
    } else if (isNode(pn,"dowhilestatement")) {
      ExpressionNode condition=parseExpression(pn.getChild("condition").getFirstChild());
      BlockNode body=parseSingleBlock(pn.getChild("statement").getFirstChild());
      blockstatements.add(new LoopNode(condition,body,LoopNode.DOWHILELOOP));
    } else if (isNode(pn,"sese")) {
      ParseNode pnID=pn.getChild("identifier");
      String stID=null;
      if( pnID != null ) { stID=pnID.getFirstChild().getTerminal(); }
      SESENode start=new SESENode(stID);
      SESENode end  =new SESENode(stID);
      start.setEnd( end   );
      end.setStart( start );
      blockstatements.add(start);
      blockstatements.addAll(parseSESEBlock(blockstatements,pn.getChild("body").getFirstChild()));
      blockstatements.add(end);
    } else if (isNode(pn,"continue")) {
      blockstatements.add(new ContinueBreakNode(false));
    } else if (isNode(pn,"break")) {
      blockstatements.add(new ContinueBreakNode(true));

    } else if (isNode(pn,"genreach")) {
      String graphName = pn.getChild("graphName").getTerminal();
      blockstatements.add( new GenReachNode( graphName ) );

    } else {
      System.out.println("---------------");
      System.out.println(pn.PPrint(3,true));
      throw new Error();
    }
    return blockstatements;
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
    for(int i=0; i<pnv.size(); i++) {
      ParseNode paramn=pnv.elementAt(i);

      if (isNode(paramn, "tag_parameter")) {
	String paramname=paramn.getChild("single").getTerminal();
	TypeDescriptor type=new TypeDescriptor(TypeDescriptor.TAG);
	md.addTagParameter(type, paramname);
      } else {
	TypeDescriptor type=parseTypeDescriptor(paramn);

	ParseNode tmp=paramn;
	while (tmp.getChild("single")==null) {
	  type=type.makeArray(state);
	  tmp=tmp.getChild("array");
	}
	String paramname=tmp.getChild("single").getTerminal();

	md.addParameter(type, paramname);
      }
    }
  }

  public Modifiers parseModifiersList(ParseNode pn) {
    Modifiers m=new Modifiers();
    ParseNode modlist=pn.getChild("modifier_list");
    if (modlist!=null) {
      ParseNodeVector pnv=modlist.getChildren();
      for(int i=0; i<pnv.size(); i++) {
	ParseNode modn=pnv.elementAt(i);
	if (isNode(modn,"public"))
	  m.addModifier(Modifiers.PUBLIC);
	else if (isNode(modn,"protected"))
	  m.addModifier(Modifiers.PROTECTED);
	else if (isNode(modn,"private"))
	  m.addModifier(Modifiers.PRIVATE);
	else if (isNode(modn,"static"))
	  m.addModifier(Modifiers.STATIC);
	else if (isNode(modn,"final"))
	  m.addModifier(Modifiers.FINAL);
	else if (isNode(modn,"native"))
	  m.addModifier(Modifiers.NATIVE);
	else if (isNode(modn,"synchronized"))
	  m.addModifier(Modifiers.SYNCHRONIZED);
	else if (isNode(modn,"atomic"))
	  m.addModifier(Modifiers.ATOMIC);
    else if (isNode(modn,"abstract"))
      m.addModifier(Modifiers.ABSTRACT);
    else if (isNode(modn,"volatile"))
      m.addModifier(Modifiers.VOLATILE);
    else if (isNode(modn,"transient"))
      m.addModifier(Modifiers.TRANSIENT);
	else throw new Error("Unrecognized Modifier");
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
    if (!pn.getLabel().equals(label)) {
      throw new Error(pn+ "IE: Expected '" + label + "', got '"+pn.getLabel()+"'");
    }
  }
}
