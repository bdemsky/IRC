package IR.Tree;
import IR.*;
import Util.Lattice;
import Util.Pair;

import java.io.File;
import java.util.*;
import java.io.*;
import java.lang.Throwable;
public class BuildIR {
  State state;
  private boolean isRunningRecursiveInnerClass;
  private int m_taskexitnum;

  public BuildIR(State state) {
    this.state=state;
    this.m_taskexitnum = 0;
    this.isRunningRecursiveInnerClass = false;
  }

  public void buildtree(ParseNode pn, Set toanalyze, String sourcefile) {
    parseFile(pn, toanalyze, sourcefile);

    // numering the interfaces
    int if_num = 0;
    Iterator it_classes = state.getClassSymbolTable().getValueSet().iterator();
    while(it_classes.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor)it_classes.next();
      if(cd.isInterface()) {
        cd.setInterfaceId(if_num++);
      }
    }
  }

  //This is all single imports and a subset of the
  //multi imports that have been resolved.
  ChainHashMap mandatoryImports;
  //maps class names in file to full name
  //Note may map a name to an ERROR.
  ChainHashMap multiimports;
  String packageName;

  String currsourcefile;
  Set analyzeset;

  void pushChainMaps() {
    mandatoryImports=mandatoryImports.makeChild();
    multiimports=multiimports.makeChild();
  }
  
  void popChainMaps() {
    mandatoryImports=mandatoryImports.getParent();
    multiimports=multiimports.getParent();
  }

  /** Parse the classes in this file */
  public void parseFile(ParseNode pn, Set toanalyze, String sourcefile) {
    mandatoryImports = new ChainHashMap();
    multiimports = new ChainHashMap();
    currsourcefile=sourcefile;
    analyzeset=toanalyze;

    if(state.JNI) {
      //add java.lang as our default multi-import
      this.addMultiImport(sourcefile, "java.lang", false);
    }

    ParseNode ipn = pn.getChild("imports").getChild("import_decls_list");
    if ((ipn != null) && !state.MGC) {
      ParseNodeVector pnv = ipn.getChildren();
      for (int i = 0; i < pnv.size(); i++) {
        ParseNode pnimport = pnv.elementAt(i);
        NameDescriptor nd = parseName(pnimport.getChild("name"));
        if (isNode(pnimport, "import_single")) {
          if (!mandatoryImports.containsKey(nd.getIdentifier())) {
            // map name to full name (includes package/directory
            mandatoryImports.put(nd.getIdentifier(), nd.getPathFromRootToHere());
          } else {
            throw new Error("An ambiguous class "+ nd.getIdentifier() +" has been found. It is included for " +
                            ((String)mandatoryImports.get(nd.getIdentifier())) + " and " +
                            nd.getPathFromRootToHere());
          }
        } else {
          addMultiImport(sourcefile, nd.getPathFromRootToHere(), false);
        }
      }
    }

    ParseNode ppn=pn.getChild("packages").getChild("package");
    packageName = null;
    if ((ppn!=null) && !state.MGC){
      NameDescriptor nd = parseClassName(ppn.getChild("name"));
      packageName = nd.getPathFromRootToHere();
      //Trick -> import the package directory as a multi-import and it'll
      //automatically recognize files in the same directory.
      addMultiImport(sourcefile, packageName, true);
    }

    ParseNode tpn=pn.getChild("type_declaration_list");
    if (tpn != null) {
      ParseNodeVector pnv = tpn.getChildren();
      for (int i = 0; i < pnv.size(); i++) {
        ParseNode type_pn = pnv.elementAt(i);
        if (isEmpty(type_pn)) /* Skip the semicolon */
          continue;
        if (isNode(type_pn, "class_declaration")) {
          ClassDescriptor cn = parseTypeDecl(type_pn);
          parseInitializers(cn);

          // for inner classes/enum
          HashSet tovisit = new HashSet();
          Iterator it_icds = cn.getInnerClasses();
          while (it_icds.hasNext()) {
            tovisit.add(it_icds.next());
          }

          while (!tovisit.isEmpty()) {
            ClassDescriptor cd = (ClassDescriptor) tovisit.iterator().next();
            tovisit.remove(cd);
            parseInitializers(cd);

            Iterator it_ics = cd.getInnerClasses();
            while (it_ics.hasNext()) {
              tovisit.add(it_ics.next());
            }
          }
        } else if (isNode(type_pn, "task_declaration")) {
          TaskDescriptor td = parseTaskDecl(type_pn);
          if (toanalyze != null)
            toanalyze.add(td);
          state.addTask(td);
        } else if (isNode(type_pn, "interface_declaration")) {
          // TODO add version for normal Java later
          ClassDescriptor cn = parseInterfaceDecl(type_pn, null);
        } else if (isNode(type_pn, "enum_declaration")) {
          // TODO add version for normal Java later
          ClassDescriptor cn = parseEnumDecl(null, type_pn);

        } else if(isNode(type_pn,"annotation_type_declaration")) {
          ClassDescriptor cn=parseAnnotationTypeDecl(type_pn);
        } else {
          throw new Error(type_pn.getLabel());
        }
      }
    }
  }


  //This kind of breaks away from tradition a little bit by doing the file checks here
  // instead of in Semantic check, but doing it here is easier because we have a mapping early on
  // if I wait until semantic check, I have to change ALL the type descriptors to match the new
  // mapping and that's both ugly and tedious.
  private void addMultiImport(String currentSource, String importPath, boolean isPackageDirectory) {
    boolean found = false;
    for (int j = 0; j < state.classpath.size(); j++) {
      String path = (String) state.classpath.get(j);
      File folder = new File(path, importPath.replace('.', '/'));
      if (folder.exists()) {
        found = true;
        for (String file : folder.list()) {
          // if the file is of type *.java add to multiImport list.
          if (file.lastIndexOf('.') != -1 && file.substring(file.lastIndexOf('.')).equalsIgnoreCase(".java")) {
            String classname = file.substring(0, file.length() - 5);
            // single imports have precedence over multi-imports
            if (!mandatoryImports.containsKey(classname)) {
              //package files have precedence over multi-imports.
              if (multiimports.containsKey(classname)  && !isPackageDirectory) {
                // put error in for later, in case we try to import
                multiimports.put(classname, new Error("Error: class " + classname + " is defined more than once in a multi-import in " + currentSource));
              } else {
                multiimports.put(classname, importPath + "." + classname);
              }
            }
          }
        }
      }
    }

    if(!found) {
      throw new Error("Import package " + importPath + " in  " + currentSource
                      + " cannot be resolved.");
    }
  }

  public void parseInitializers(ClassDescriptor cn) {
    Vector fv=cn.getFieldVec();
    Iterator methodit = cn.getMethods();
    HashMap<MethodDescriptor, Integer> md2pos = new HashMap<MethodDescriptor, Integer>();
    while(methodit.hasNext()) {
      MethodDescriptor currmd=(MethodDescriptor)methodit.next();
      if(currmd.isConstructor()) {
        BlockNode bn=state.getMethodBody(currmd);
        // if there are super(...) invokation, the initializers should be invoked after that
        int i = 0;
        for(; i < bn.size(); i++) {
          if(Kind.BlockExpressionNode==bn.get(i).kind()
        	  &&(((BlockExpressionNode)bn.get(i)).getExpression() instanceof MethodInvokeNode)
        	  &&((MethodInvokeNode)(((BlockExpressionNode)bn.get(i)).getExpression())).getMethodName().equals("super")) {
            break;
          }
        }
        if(i==bn.size()) {
          md2pos.put(currmd, 0);
        } else {
          md2pos.put(currmd, i+1);
        }
      }
    }
    int pos = 0;
    for(int i=0; i<fv.size(); i++) {
      FieldDescriptor fd=(FieldDescriptor)fv.get(i);
      if(fd.getExpressionNode()!=null) {
        methodit = cn.getMethods();
        while(methodit.hasNext()) {
          MethodDescriptor currmd=(MethodDescriptor)methodit.next();
          if(currmd.isConstructor()) {
            int offset = md2pos.get(currmd);
            BlockNode bn=state.getMethodBody(currmd);
            NameNode nn=new NameNode(new NameDescriptor(fd.getSymbol()));
            AssignmentNode an=new AssignmentNode(nn,fd.getExpressionNode(),new AssignOperation(1));
            bn.addBlockStatementAt(new BlockExpressionNode(an), pos+offset);
          }
        }
        pos++;
      }
    }
  }
  
  private ClassDescriptor parseEnumDecl(ClassDescriptor cn, ParseNode pn) {    
    String basename=pn.getChild("name").getTerminal();
    String classname=(cn!=null)?cn.getClassName()+"$"+basename:basename;
    ClassDescriptor ecd=new ClassDescriptor(cn!=null?cn.getPackage():null, classname, false);
    
    if (cn!=null) {
      if (packageName==null)
	cn.getSingleImportMappings().put(basename,classname);
      else
	cn.getSingleImportMappings().put(basename,packageName+"."+classname);
    }

    pushChainMaps();
    ecd.setImports(mandatoryImports, multiimports);
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

    addClass2State(ecd);

    popChainMaps();
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

  private ClassDescriptor parseAnnotationTypeDecl(ParseNode pn) {
    ClassDescriptor cn=new ClassDescriptor(pn.getChild("name").getTerminal(), true);
    pushChainMaps();
    cn.setImports(mandatoryImports, multiimports);
    ParseNode modifiers=pn.getChild("modifiers");
    if(modifiers!=null) {
      cn.setModifiers(parseModifiersList(modifiers));
    }
    parseAnnotationTypeBody(cn,pn.getChild("body"));
    popChainMaps();

    addClass2State(cn);

    return cn;
  }

  private void parseAnnotationTypeBody(ClassDescriptor cn, ParseNode pn) {
    ParseNode list_node=pn.getChild("annotation_type_element_list");
    if(list_node!=null) {
      ParseNodeVector pnv = list_node.getChildren();
      for (int i = 0; i < pnv.size(); i++) {
        ParseNode element_node = pnv.elementAt(i);
        if (isNode(element_node, "annotation_type_element_declaration")) {
          ParseNodeVector elementProps = element_node.getChildren();
          String identifier=null;
          TypeDescriptor type=null;
          Modifiers modifiers=new Modifiers();
          for(int eidx=0; eidx<elementProps.size(); eidx++) {
            ParseNode prop_node=elementProps.elementAt(eidx);
            if(isNode(prop_node,"name")) {
              identifier=prop_node.getTerminal();
            } else if(isNode(prop_node,"type")) {
              type=parseTypeDescriptor(prop_node);
            } else if(isNode(prop_node,"modifier")) {
              modifiers=parseModifiersList(prop_node);
            }
          }
          cn.addField(new FieldDescriptor(modifiers, type, identifier, null, false));
        }
      }
    }
  }

  public ClassDescriptor parseInterfaceDecl(ParseNode pn, ClassDescriptor outerclass) {
    String basename=pn.getChild("name").getTerminal();
    String classname=((outerclass==null)?"":(outerclass.getClassName()+"$"))+basename;
    if (outerclass!=null) {
      if (packageName==null)
	outerclass.getSingleImportMappings().put(basename,classname);
      else
	outerclass.getSingleImportMappings().put(basename,packageName+"."+classname);
    }
    ClassDescriptor cn= new ClassDescriptor(packageName, classname, true);

    pushChainMaps();
    cn.setImports(mandatoryImports, multiimports);
    //cn.setAsInterface();
    if (!isEmpty(pn.getChild("superIF").getTerminal())) {
      /* parse inherited interface name */
      ParseNode snlist=pn.getChild("superIF").getChild("extend_interface_list");
      ParseNodeVector pnv=snlist.getChildren();
      for(int i=0; i<pnv.size(); i++) {
        ParseNode decl=pnv.elementAt(i);
        if (isNode(decl,"type")) {
          NameDescriptor nd=parseClassName(decl.getChild("class").getChild("name"));
          cn.addSuperInterface(nd.toString());
        } else if (isNode(decl, "interface_declaration")) {
          ClassDescriptor innercn = parseInterfaceDecl(decl, cn);
	} else throw new Error();
      }
    }
    cn.setModifiers(parseModifiersList(pn.getChild("modifiers")));
    parseInterfaceBody(cn, pn.getChild("interfacebody"));
    addClass2State(cn);
    popChainMaps();
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
        } else if (isNode(decl, "interface_declaration")) {
	  parseInterfaceDecl(decl, cn);
	} else throw new Error(decl.PPrint(2, true));
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
      BlockNode bn=parseBlock(cn, md, md.isStatic()||md.isStaticBlock(), bodyn);
      cn.addMethod(md);
      state.addTreeCode(md,bn);
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
    BlockNode bn=parseBlock(null, null, false, bodyn);
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
               System.out.println("NOT OPTIONAL"); }

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

  private void addClass2State(ClassDescriptor cn) {
    if (analyzeset != null)
      analyzeset.add(cn);
    cn.setSourceFileName(currsourcefile);
    state.addClass(cn);
    // create this$n representing a final reference to the next surrounding class. each inner class should have whatever inner class
    // pointers the surrounding class has + a pointer to the surrounding class.
    if( true )
    {
      this.isRunningRecursiveInnerClass = true; //fOR dEBUGGING PURPOSES IN ORDER TO DUMP STRINGS WHILE IN THIS CODE PATH
      addOuterClassReferences( cn, cn, 0 );
      addOuterClassParam( cn, cn, 0 );
      this.isRunningRecursiveInnerClass = false;
    }
  }
  
  public ClassDescriptor parseTypeDecl(ParseNode pn) {
    ClassDescriptor cn=new ClassDescriptor(packageName, pn.getChild("name").getTerminal(), false);
    pushChainMaps();
    cn.setImports(mandatoryImports, multiimports);
    if (!isEmpty(pn.getChild("super").getTerminal())) {
      /* parse superclass name */
      ParseNode snn=pn.getChild("super").getChild("type").getChild("class").getChild("name");
      NameDescriptor nd=parseClassName(snn);
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
          NameDescriptor nd=parseClassName(decl.getChild("class").getChild("name"));
          cn.addSuperInterface(nd.toString());
        }
      }
    }
    cn.setModifiers(parseModifiersList(pn.getChild("modifiers")));
    parseClassBody(cn, pn.getChild("classbody"));

    boolean hasConstructor = false;
    for(Iterator method_it=cn.getMethods(); method_it.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)method_it.next();
      hasConstructor |= md.isConstructor();
    }
    if((!hasConstructor) && (!cn.isEnum())) {
      // add a default constructor for this class
      MethodDescriptor md = new MethodDescriptor(new Modifiers(Modifiers.PUBLIC),
                                                 cn.getSymbol(), false);
      BlockNode bn=new BlockNode();
      state.addTreeCode(md,bn);
      md.setDefaultConstructor();
      cn.addMethod(md);
    }


    popChainMaps();

    cn.setSourceFileName(currsourcefile);
    
    addClass2State(cn);

    return cn;
  }

private void initializeOuterMember( MethodDescriptor md, String fieldName, String formalParameter ) {
	 BlockNode obn = state.getMethodBody(md);
         NameNode nn=new NameNode( new NameDescriptor( fieldName ) );
	 NameNode fn = new NameNode ( new NameDescriptor( formalParameter ) );
          //nn.setNumLine(en.getNumLine())
         AssignmentNode an=new AssignmentNode(nn,fn,new AssignOperation(1));
         //an.setNumLine(pn.getLine());
         obn.addFirstBlockStatement(new BlockExpressionNode(an));
	// System.out.print( "The code inserted is : " + obn.printNode( 0 ) + "\n" );
         state.addTreeCode(md, obn);
}

private void addOuterClassParam( ClassDescriptor cn, ClassDescriptor ocn, int depth )
{
	Iterator nullCheckItr = cn.getInnerClasses();
	if( false == nullCheckItr.hasNext() )
		return;

	//create a typedescriptor of type cn
	TypeDescriptor theTypeDesc = new TypeDescriptor( ocn );
	
	for(Iterator it=cn.getInnerClasses(); it.hasNext(); ) {
      		ClassDescriptor icd=(ClassDescriptor)it.next();
      		if(icd.isStatic()||icd.getInStaticContext()) {
      		    continue;
      		}
		
		//iterate over all ctors of I.Cs and add a new param
		for(Iterator method_it=icd.getMethods(); method_it.hasNext(); ) {
     			 MethodDescriptor md=(MethodDescriptor)method_it.next();
      			 if( md.isConstructor() ){
				md.addParameter( theTypeDesc, "surrounding$" + String.valueOf(depth) );	
				initializeOuterMember( md, "this$" + String.valueOf( depth ), "surrounding$" + String.valueOf(depth) );
				//System.out.println( "The added param is " + md.toString() + "\n" );
			}
   		}
		addOuterClassParam( icd, ocn, depth + 1 );
		
    	}
	
}
private void addOuterClassReferences( ClassDescriptor cn, ClassDescriptor ocn, int depth )
{
	//SYMBOLTABLE does not have a length or empty method, hence could not define a hasInnerClasses method in classDescriptor
	Iterator nullCheckItr = cn.getInnerClasses();
	if( false == nullCheckItr.hasNext() )
		return;

	String tempCopy = ocn.getClassName();
	//MESSY HACK FOLLOWS
	int i = 0;

	ParseNode theNode = new ParseNode( "field_declaration" );
	theNode.addChild("modifier").addChild( new ParseNode( "modifier_list" ) ).addChild("final");
	ParseNode theTypeNode = new ParseNode("type");
	ParseNode tempChildNode = theTypeNode.addChild("class").addChild( "name" );
		//tempChildNode.addChild("base").addChild( new ParseNode("empty") );
	tempChildNode.addChild("identifier").addChild ( tempCopy );
	theNode.addChild("type").addChild( theTypeNode );
	ParseNode variableDeclaratorID = new ParseNode("single");
	String theStr = "this$" + String.valueOf( depth );
	variableDeclaratorID.addChild( theStr );
	ParseNode variableDeclarator = new ParseNode( "variable_declarator" );
	variableDeclarator.addChild( variableDeclaratorID );
	ParseNode variableDeclaratorList = new ParseNode("variable_declarators_list");
	variableDeclaratorList.addChild( variableDeclarator );
	theNode.addChild("variables").addChild( variableDeclaratorList );

	for(Iterator it=cn.getInnerClasses(); it.hasNext(); ) {
      		ClassDescriptor icd=(ClassDescriptor)it.next();
      		if(icd.isStatic() || icd.getInStaticContext()) {
      		  continue;
      		}
		parseFieldDecl( icd, theNode );		
		/*if( true ) {
			SymbolTable fieldTable = icd.getFieldTable();
			//System.out.println( fieldTable.toString() );
		}*/
		icd.setInnerDepth( depth + 1 );
		addOuterClassReferences( icd, ocn, depth + 1 );	
    	}
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
          parseStaticBlockDecl(cn, decl.getChild("static_block_declaration"));
        } else if (isNode(decl,"block")) {
        } else throw new Error();
      }
    }
  }

  private void parseClassMember(ClassDescriptor cn, ParseNode pn) {
    ParseNode fieldnode=pn.getChild("field");
    if (fieldnode!=null) {
      //System.out.println( pn.PPrint( 0, true ) );
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
      parseInnerClassDecl(cn,innerclassnode);
      return;
    }
     ParseNode innerinterfacenode=pn.getChild("interface_declaration");
    if (innerinterfacenode!=null) {
      parseInterfaceDecl(innerinterfacenode, cn);
      return;
    }

    ParseNode enumnode=pn.getChild("enum_declaration");
    if (enumnode!=null) {
      ClassDescriptor ecn=parseEnumDecl(cn,enumnode);
      return;
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
    System.out.println("Unrecognized node:"+pn.PPrint(2,true));
    throw new Error();
  }

//10/9/2011 changed this function to enable creation of default constructor for inner classes.
//the change was refactoring this function with the corresponding version for normal classes. sganapat
  private ClassDescriptor parseInnerClassDecl(ClassDescriptor cn, ParseNode pn) {
    String basename=pn.getChild("name").getTerminal();
    String classname=cn.getClassName()+"$"+basename;

    if (cn.getPackage()==null)
      cn.getSingleImportMappings().put(basename,classname);
    else
      cn.getSingleImportMappings().put(basename,cn.getPackage()+"."+classname);
    
    ClassDescriptor icn=new ClassDescriptor(cn.getPackage(), classname, false);
    pushChainMaps();
    icn.setImports(mandatoryImports, multiimports);
    icn.setSurroundingClass(cn.getSymbol());
    icn.setSurrounding(cn);
    cn.addInnerClass(icn);

     if (!isEmpty(pn.getChild("super").getTerminal())) {
      /* parse superclass name */
      ParseNode snn=pn.getChild("super").getChild("type").getChild("class").getChild("name");
      NameDescriptor nd=parseClassName(snn);
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
          NameDescriptor nd=parseClassName(decl.getChild("class").getChild("name"));
          icn.addSuperInterface(nd.toString());
        }
      }
    }
    icn.setModifiers(parseModifiersList(pn.getChild("modifiers")));

   if (!icn.isStatic())
     icn.setAsInnerClass();

    parseClassBody(icn, pn.getChild("classbody"));

    boolean hasConstructor = false;
    for(Iterator method_it=icn.getMethods(); method_it.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)method_it.next();
      hasConstructor |= md.isConstructor();
    }
//sganapat adding change to allow proper construction of inner class objects
    if((!hasConstructor) && (!icn.isEnum())) {
      // add a default constructor for this class
      MethodDescriptor md = new MethodDescriptor(new Modifiers(Modifiers.PUBLIC),
                                                 icn.getSymbol(), false);
      BlockNode bn=new BlockNode();
      state.addTreeCode(md,bn);
      md.setDefaultConstructor();
      icn.addMethod(md);
   }
    popChainMaps();

    addClass2State(icn);

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
      return state.getTypeDescriptor(parseClassName(nn.getChild("name")));
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

  //Needed to separate out top level call since if a base exists,
  //we do not want to apply our resolveName function (i.e. deal with imports)
  //otherwise, if base == null, we do just want to resolve name.
  private NameDescriptor parseClassName(ParseNode nn) {
    

    ParseNode base=nn.getChild("base");
    ParseNode id=nn.getChild("identifier");
    String classname = id.getTerminal();
    if (base==null) {
      return new NameDescriptor(resolveName(classname));
    }
    return new NameDescriptor(parseClassNameRecursive(base.getChild("name")),classname);
  }

  private NameDescriptor parseClassNameRecursive(ParseNode nn) {
    ParseNode base=nn.getChild("base");
    ParseNode id=nn.getChild("identifier");
    String classname = id.getTerminal();
    if (base==null) {
      return new NameDescriptor(classname);
    }
    return new NameDescriptor(parseClassNameRecursive(base.getChild("name")),classname);
  }

  //This will get the mapping of a terminal class name
  //to a canonical classname (with imports/package locations in them)
  private String resolveName(String terminal) {
    
    if(mandatoryImports.containsKey(terminal)) {
      return (String) mandatoryImports.get(terminal);
    } else {
      if(multiimports.containsKey(terminal)) {
        //Test for error
        Object o = multiimports.get(terminal);
        if(o instanceof Error) {
          throw new Error("Class " + terminal + " is ambiguous. Cause: more than 1 package import contain the same class.");
        } else {
          //At this point, if we found a unique class
          //we can treat it as a single, mandatory import.
          mandatoryImports.put(terminal, o);
          return (String) o;
        }
      }
    }

    return terminal;
  }

  //only function difference between this and parseName() is that this
  //does not look for a import mapping.
  private NameDescriptor parseName(ParseNode nn) {
    ParseNode base=nn.getChild("base");
    ParseNode id=nn.getChild("identifier");
    if (base==null) {
      return new NameDescriptor(id.getTerminal());
    }
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
    if(cn.isInterface()) {
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
       if( this.isRunningRecursiveInnerClass && false )
	{	
		System.out.println( "the length of the list is " + String.valueOf( pnv.size() ) );
		System.out.println( "\n the parse node is \n" + tmp.PPrint( 0, true ) );
	}
      while (tmp.getChild("single")==null) {
        arrayt=arrayt.makeArray(state);
        tmp=tmp.getChild("array");
      }
      String identifier=tmp.getChild("single").getTerminal();
      ParseNode epn=vardecl.getChild("initializer");

      ExpressionNode en=null;
      
      if (epn!=null) {
        en=parseExpression(cn, null, m.isStatic(), epn.getFirstChild());
        en.setNumLine(epn.getFirstChild().getLine());
        if(m.isStatic()) {
          // for static field, the initializer should be considered as a
          // static block
          boolean isfirst = false;
          MethodDescriptor md = (MethodDescriptor)cn.getMethodTable().getFromSameScope("staticblocks");
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
          nn.setNumLine(en.getNumLine());
          AssignmentNode an=new AssignmentNode(nn,en,new AssignOperation(1));
          an.setNumLine(pn.getLine());
          bn.addBlockStatement(new BlockExpressionNode(an));
          if(isfirst) {
            state.addTreeCode(md,bn);
          } else {
            BlockNode obn = state.getMethodBody(md);
            for(int ii = 0; ii < bn.size(); ii++) {
              BlockStatementNode bsn = bn.get(ii);
              obn.addBlockStatement(bsn);
            }
            state.addTreeCode(md, obn);
            bn = null;
          }
          en = null;
        }
      }

      cn.addField(new FieldDescriptor(m, arrayt, identifier, en, isglobal));
      assignAnnotationsToType(m,arrayt);
    }
  }

  private void assignAnnotationsToType(Modifiers modifiers, TypeDescriptor type) {
    Vector<AnnotationDescriptor> annotations=modifiers.getAnnotations();
    for(int i=0; i<annotations.size(); i++) {
      // it only supports a marker annotation
      AnnotationDescriptor an=annotations.elementAt(i);
      type.addAnnotationMarker(an);
    }
  }

  int innerCount=0;

  private ExpressionNode parseExpression(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    if (isNode(pn,"assignment"))
	{
          //System.out.println( "parsing a field decl in my class that has assignment in initialization " + pn.PPrint( 0, true ) + "\n");
      return parseAssignmentExpression(cn, md, isStaticContext, pn);
	}
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
      OpNode on=new OpNode(parseExpression(cn, md, isStaticContext, left),parseExpression(cn, md, isStaticContext,  right),op);
      on.setNumLine(pn.getLine());
      return on;
    } else if (isNode(pn,"unaryplus")||
               isNode(pn,"unaryminus")||
               isNode(pn,"not")||
               isNode(pn,"comp")) {
      ParseNode left=pn.getFirstChild();
      Operation op=new Operation(pn.getLabel());
      OpNode on=new OpNode(parseExpression(cn, md, isStaticContext, left),op);
      on.setNumLine(pn.getLine());
      return on;
    } else if (isNode(pn,"postinc")||
               isNode(pn,"postdec")) {
      ParseNode left=pn.getFirstChild();
      AssignOperation op=new AssignOperation(pn.getLabel());
      AssignmentNode an=new AssignmentNode(parseExpression(cn, md, isStaticContext, left),null,op);
      an.setNumLine(pn.getLine());
      return an;

    } else if (isNode(pn,"preinc")||
               isNode(pn,"predec")) {
      ParseNode left=pn.getFirstChild();
      AssignOperation op=isNode(pn,"preinc")?new AssignOperation(AssignOperation.PLUSEQ):new AssignOperation(AssignOperation.MINUSEQ);
      AssignmentNode an=new AssignmentNode(parseExpression(cn, md, isStaticContext, left),
                                           new LiteralNode("integer",new Integer(1)),op);
      an.setNumLine(pn.getLine());
      return an;
    } else if (isNode(pn,"literal")) {
      String literaltype=pn.getTerminal();
      ParseNode literalnode=pn.getChild(literaltype);
      Object literal_obj=literalnode.getLiteral();
      LiteralNode ln=new LiteralNode(literaltype, literal_obj);
      ln.setNumLine(pn.getLine());
      return ln;
    } else if (isNode(pn, "createobject")) {
      TypeDescriptor td = parseTypeDescriptor(pn);

      Vector args = parseArgumentList(cn, md, isStaticContext, pn);
      boolean isglobal = pn.getChild("global") != null || pn.getChild("scratch") != null;
      String disjointId = null;
      if (pn.getChild("disjoint") != null) {
        disjointId = pn.getChild("disjoint").getTerminal();
      }
      ParseNode idChild = (pn.getChild( "id" ));
      ParseNode baseChild = (pn.getChild( "base" ));
      
      CreateObjectNode con = new CreateObjectNode(td, isglobal, disjointId);
      if( null != idChild && null != idChild.getFirstChild() ) {
	idChild = idChild.getFirstChild();
	//System.out.println( "\nThe object passed has this expression " + idChild.PPrint( 0, true ) );
	ExpressionNode en = parseExpression(cn, md, isStaticContext, idChild ); 
	//System.out.println( "\nThe object passed has this expression " + en.printNode( 0 ) );
	con.setSurroundingExpression( en );
      }
      else if( null != baseChild  && null != baseChild.getFirstChild()  ) {
	baseChild = baseChild.getFirstChild();
	//System.out.println( "\nThe object passed has this expression " + baseChild.PPrint( 0, true ) );
	ExpressionNode en = parseExpression(cn, md, isStaticContext, baseChild ); 
	//System.out.println( "\nThe object passed has this expression " + en.printNode( 0 ) );
	con.setSurroundingExpression( en );     
      }
      con.setNumLine(pn.getLine());
      for (int i = 0; i < args.size(); i++) {
        con.addArgument((ExpressionNode) args.get(i));
      }
      /* Could have flag set or tag added here */
      if (pn.getChild("flag_list") != null || pn.getChild("tag_list") != null) {
        FlagEffects fe = new FlagEffects(null);
        if (pn.getChild("flag_list") != null)
          parseFlagEffect(fe, pn.getChild("flag_list"));

        if (pn.getChild("tag_list") != null)
          parseTagEffect(fe, pn.getChild("tag_list"));
        con.addFlagEffects(fe);
      }

      return con;
    } else if (isNode(pn,"createobjectcls")) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      innerCount++;
      ClassDescriptor cnnew=new ClassDescriptor(packageName,td.getSymbol()+"$"+innerCount, false);
      pushChainMaps();
      cnnew.setImports(mandatoryImports, multiimports);
      cnnew.setSuper(td.getSymbol());
      cnnew.setInline();
      // the inline anonymous class does not have modifiers, it cannot be static 
      // it is always implicit final
      cnnew.setModifiers(new Modifiers(Modifiers.FINAL));
      cnnew.setAsInnerClass();
      cnnew.setSurroundingClass(cn.getSymbol());
      cnnew.setSurrounding(cn);
      cn.addInnerClass(cnnew);
      // Note that if the inner class is declared in a static context, it does NOT 
      // have lexically enclosing instances.
      if((null!=md)&&(md.isStatic()||md.isStaticBlock())) {
	cnnew.setSurroundingBlock(md);
	cnnew.setInStaticContext();
      } else if (isStaticContext) {
	cnnew.setInStaticContext();
      }
      parseClassBody(cnnew, pn.getChild("decl").getChild("classbody"));
      boolean hasConstructor = false;
      for(Iterator method_it=cnnew.getMethods(); method_it.hasNext(); ) {
	  MethodDescriptor cmd=(MethodDescriptor)method_it.next();
	  hasConstructor |= cmd.isConstructor();
      }
      if(hasConstructor) {
	  // anonymous class should not have explicit constructors
	  throw new Error("Error! Anonymous class " + cnnew.getSymbol() + " in " + cn.getSymbol() + " has explicit constructors!");
      } else if(!cnnew.isEnum()) {
	  // add a default constructor for this class
	  MethodDescriptor cmd = new MethodDescriptor(new Modifiers(Modifiers.PUBLIC|Modifiers.FINAL),cnnew.getSymbol(), false);
	  BlockNode bn=new BlockNode();
	  state.addTreeCode(cmd,bn);
	  cmd.setDefaultConstructor();
	  cnnew.addMethod(cmd);
      }
      TypeDescriptor tdnew=state.getTypeDescriptor(cnnew.getSymbol());

      Vector args=parseArgumentList(cn, md, isStaticContext, pn);
      ParseNode idChild = pn.getChild( "id" );
      ParseNode baseChild = pn.getChild( "base" );
      //System.out.println("\n to print idchild and basechild for ");
      /*if( null != idChild )
	System.out.println( "\n trying to create an inner class and the id child passed is "  + idChild.PPrint( 0, true ) );
      if( null != baseChild )
	System.out.println( "\n trying to create an inner class and the base child passed is "  + baseChild.PPrint( 0, true ) );*/
      CreateObjectNode con=new CreateObjectNode(tdnew, false, null);
      con.setNumLine(pn.getLine());
      for(int i=0; i<args.size(); i++) {
        con.addArgument((ExpressionNode)args.get(i));
      }
      popChainMaps();

      addClass2State(cnnew);

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
      Vector args=parseDimExprs(cn, md, isStaticContext, pn);
      int num=0;
      if (pn.getChild("dims_opt").getLiteral()!=null)
        num=((Integer)pn.getChild("dims_opt").getLiteral()).intValue();
      for(int i=0; i<(args.size()+num); i++)
        td=td.makeArray(state);
      CreateObjectNode con=new CreateObjectNode(td, isglobal, disjointId);
      con.setNumLine(pn.getLine());
      for(int i=0; i<args.size(); i++) {
        con.addArgument((ExpressionNode)args.get(i));
      }
      return con;
    }
    if (isNode(pn,"createarray2")) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      int num=0;
      if (pn.getChild("dims_opt").getLiteral()!=null)
        num=((Integer)pn.getChild("dims_opt").getLiteral()).intValue();
      for(int i=0; i<num; i++)
        td=td.makeArray(state);
      CreateObjectNode con=new CreateObjectNode(td, false, null);
      con.setNumLine(pn.getLine());
      ParseNode ipn = pn.getChild("initializer");
      Vector initializers=parseVariableInitializerList(cn, md, isStaticContext, ipn);
      ArrayInitializerNode ain = new ArrayInitializerNode(initializers);
      ain.setNumLine(pn.getLine());
      con.addArrayInitializer(ain);
      return con;
    } else if (isNode(pn,"name")) {
      NameDescriptor nd=parseName(pn);
      NameNode nn=new NameNode(nd);
      nn.setNumLine(pn.getLine());
      return nn;
    } else if (isNode(pn,"this")) {
      NameDescriptor nd=new NameDescriptor("this");
      NameNode nn=new NameNode(nd);
      nn.setNumLine(pn.getLine());
      return nn;
    } else if (isNode(pn,"parentclass")) {
      NameDescriptor nd=new NameDescriptor(pn.getChild("name").getFirstChild().getFirstChild().getTerminal());
      NameNode nn=new NameNode(nd);
      nn.setNumLine(pn.getLine());
	//because inner classes pass right thru......
      FieldAccessNode fan=new FieldAccessNode(nn,"this");
      fan.setNumLine(pn.getLine());
      return fan;
    } else if (isNode(pn,"isavailable")) {
      NameDescriptor nd=new NameDescriptor(pn.getTerminal());
      NameNode nn=new NameNode(nd);
      nn.setNumLine(pn.getLine());
      return new OpNode(nn,null,new Operation(Operation.ISAVAILABLE));
    } else if (isNode(pn,"methodinvoke1")) {
      NameDescriptor nd=parseName(pn.getChild("name"));
      Vector args=parseArgumentList(cn, md, isStaticContext, pn);
      MethodInvokeNode min=new MethodInvokeNode(nd);
      min.setNumLine(pn.getLine());
      for(int i=0; i<args.size(); i++) {
        min.addArgument((ExpressionNode)args.get(i));
      }
      return min;
    } else if (isNode(pn,"methodinvoke2")) {
      String methodid=pn.getChild("id").getTerminal();
      ExpressionNode exp=parseExpression(cn, md, isStaticContext, pn.getChild("base").getFirstChild());
      Vector args=parseArgumentList(cn, md, isStaticContext, pn);
      MethodInvokeNode min=new MethodInvokeNode(methodid,exp);
      min.setNumLine(pn.getLine());
      for(int i=0; i<args.size(); i++) {
        min.addArgument((ExpressionNode)args.get(i));
      }
      return min;
    } else if (isNode(pn,"fieldaccess")) {
      ExpressionNode en=parseExpression(cn, md, isStaticContext, pn.getChild("base").getFirstChild());
      String fieldname=pn.getChild("field").getTerminal();

      FieldAccessNode fan=new FieldAccessNode(en,fieldname);
      fan.setNumLine(pn.getLine());
      return fan;
    } else if (isNode(pn,"superfieldaccess")) {
	ExpressionNode en=new NameNode(new NameDescriptor("super"));
	String fieldname=pn.getChild("field").getTerminal();

	FieldAccessNode fan=new FieldAccessNode(en,fieldname);
	fan.setNumLine(pn.getLine());
	return fan;
    } else if (isNode(pn,"supernamefieldaccess")) {
	ExpressionNode en=parseExpression(cn, md, isStaticContext, pn.getChild("base").getFirstChild());
	ExpressionNode exp = new FieldAccessNode(en, "super");
	exp.setNumLine(pn.getLine());
	String fieldname=pn.getChild("field").getTerminal();

	FieldAccessNode fan=new FieldAccessNode(exp,fieldname);
	fan.setNumLine(pn.getLine());
	return fan;
    } else if (isNode(pn,"arrayaccess")) {
      ExpressionNode en=parseExpression(cn, md, isStaticContext, pn.getChild("base").getFirstChild());
      ExpressionNode index=parseExpression(cn, md, isStaticContext, pn.getChild("index").getFirstChild());
      ArrayAccessNode aan=new ArrayAccessNode(en,index);
      aan.setNumLine(pn.getLine());
      return aan;
    } else if (isNode(pn,"cast1")) {
      try {
        CastNode castn=new CastNode(parseTypeDescriptor(pn.getChild("type")),parseExpression(cn, md, isStaticContext, pn.getChild("exp").getFirstChild()));
        castn.setNumLine(pn.getLine());
        return castn;
      } catch (Exception e) {
        System.out.println(pn.PPrint(1,true));
        e.printStackTrace();
        throw new Error();
      }
    } else if (isNode(pn,"cast2")) {
      CastNode castn=new CastNode(parseExpression(cn, md, isStaticContext, pn.getChild("type").getFirstChild()),parseExpression(cn, md, isStaticContext, pn.getChild("exp").getFirstChild()));
      castn.setNumLine(pn.getLine());
      return castn;
    } else if (isNode(pn, "getoffset")) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      String fieldname = pn.getChild("field").getTerminal();
      //System.out.println("Checking the values of: "+ " td.toString()= " + td.toString()+ "  fieldname= " + fieldname);
      return new OffsetNode(td, fieldname);
    } else if (isNode(pn, "tert")) {

      TertiaryNode tn=new TertiaryNode(parseExpression(cn, md, isStaticContext, pn.getChild("cond").getFirstChild()),
                                       parseExpression(cn, md, isStaticContext, pn.getChild("trueexpr").getFirstChild()),
                                       parseExpression(cn, md, isStaticContext, pn.getChild("falseexpr").getFirstChild()) );
      tn.setNumLine(pn.getLine());

      return tn;
    } else if (isNode(pn, "instanceof")) {
      ExpressionNode exp=parseExpression(cn, md, isStaticContext, pn.getChild("exp").getFirstChild());
      TypeDescriptor t=parseTypeDescriptor(pn);
      InstanceOfNode ion=new InstanceOfNode(exp,t);
      ion.setNumLine(pn.getLine());
      return ion;
    } else if (isNode(pn, "array_initializer")) {
      Vector initializers=parseVariableInitializerList(cn, md, isStaticContext, pn);
      return new ArrayInitializerNode(initializers);
    } else if (isNode(pn, "class_type")) {
      TypeDescriptor td=parseTypeDescriptor(pn);
      ClassTypeNode ctn=new ClassTypeNode(td);
      ctn.setNumLine(pn.getLine());
      return ctn;
    } else if (isNode(pn, "empty")) {
      return null;
    } else {
      System.out.println("---------------------");
      System.out.println(pn.PPrint(3,true));
      throw new Error();
    }
  }

  private Vector parseDimExprs(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    Vector arglist=new Vector();
    ParseNode an=pn.getChild("dim_exprs");
    if (an==null)       /* No argument list */
      return arglist;
    ParseNodeVector anv=an.getChildren();
    for(int i=0; i<anv.size(); i++) {
      arglist.add(parseExpression(cn, md, isStaticContext, anv.elementAt(i)));
    }
    return arglist;
  }

  private Vector parseArgumentList(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    Vector arglist=new Vector();
    ParseNode an=pn.getChild("argument_list");
    if (an==null)       /* No argument list */
      return arglist;
    ParseNodeVector anv=an.getChildren();
    for(int i=0; i<anv.size(); i++) {
      arglist.add(parseExpression(cn, md, isStaticContext, anv.elementAt(i)));
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
      arglist.add(parseExpression(null, null, false, exp));
    }
    return new Vector[] {varlist, arglist};
  }

  private Vector parseVariableInitializerList(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    Vector varInitList=new Vector();
    ParseNode vin=pn.getChild("var_init_list");
    if (vin==null)       /* No argument list */
      return varInitList;
    ParseNodeVector vinv=vin.getChildren();
    for(int i=0; i<vinv.size(); i++) {
      varInitList.add(parseExpression(cn, md, isStaticContext, vinv.elementAt(i)));
    }
    return varInitList;
  }

  private ExpressionNode parseAssignmentExpression(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    AssignOperation ao=new AssignOperation(pn.getChild("op").getTerminal());
    ParseNodeVector pnv=pn.getChild("args").getChildren();

    AssignmentNode an=new AssignmentNode(parseExpression(cn, md, isStaticContext, pnv.elementAt(0)),parseExpression(cn, md, isStaticContext, pnv.elementAt(1)),ao);
    an.setNumLine(pn.getLine());
    return an;
  }


  private void parseMethodDecl(ClassDescriptor cn, ParseNode pn) {
    ParseNode headern=pn.getChild("method_header");
    ParseNode bodyn=pn.getChild("body");
    MethodDescriptor md=parseMethodHeader(headern);
    try {
      BlockNode bn=parseBlock(cn, md, md.isStatic()||md.isStaticBlock(), bodyn);
      bn.setNumLine(pn.getLine()); // assume that method header is located at the beginning of method body
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
    String name=resolveName(cn.getSymbol());
    MethodDescriptor md=new MethodDescriptor(m, name, isglobal);
    ParseNode paramnode=cdecl.getChild("parameters");
    parseParameterList(md,paramnode);
    ParseNode bodyn0=pn.getChild("body");
    ParseNode bodyn=bodyn0.getChild("constructor_body");
    cn.addMethod(md);
    BlockNode bn=null;
    if (bodyn!=null&&bodyn.getChild("block_statement_list")!=null)
      bn=parseBlock(cn, md, md.isStatic()||md.isStaticBlock(), bodyn);
    else
      bn=new BlockNode();
    if (bodyn!=null&&bodyn.getChild("superinvoke")!=null) {
      ParseNode sin=bodyn.getChild("superinvoke");
      NameDescriptor nd=new NameDescriptor("super");
      Vector args=parseArgumentList(cn, md, true, sin);
      MethodInvokeNode min=new MethodInvokeNode(nd);
      min.setNumLine(sin.getLine());
      for(int i=0; i<args.size(); i++) {
        min.addArgument((ExpressionNode)args.get(i));
      }
      BlockExpressionNode ben=new BlockExpressionNode(min);
      bn.addFirstBlockStatement(ben);

    } else if (bodyn!=null&&bodyn.getChild("explconstrinv")!=null) {
      ParseNode eci=bodyn.getChild("explconstrinv");
      NameDescriptor nd=new NameDescriptor(cn.getSymbol());
      Vector args=parseArgumentList(cn, md, true, eci);
      MethodInvokeNode min=new MethodInvokeNode(nd);
      min.setNumLine(eci.getLine());
      for(int i=0; i<args.size(); i++) {
        min.addArgument((ExpressionNode)args.get(i));
      }
      BlockExpressionNode ben=new BlockExpressionNode(min);
      ben.setNumLine(eci.getLine());
      bn.addFirstBlockStatement(ben);
    }
    state.addTreeCode(md,bn);
  }

  private void parseStaticBlockDecl(ClassDescriptor cn, ParseNode pn) {
    // Each class maintains one MethodDecscriptor which combines all its
    // static blocks in their declaration order
    boolean isfirst = false;
    MethodDescriptor md = (MethodDescriptor)cn.getMethodTable().getFromSameScope("staticblocks");
    if(md == null) {
      // the first static block for this class
      Modifiers m_i=new Modifiers();
      m_i.addModifier(Modifiers.STATIC);
      md = new MethodDescriptor(m_i, "staticblocks", false);
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
      bn=parseBlock(cn, md, true, bodyn);
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
      state.addTreeCode(md, obn);
      bn = null;
    }
  }

  public BlockNode parseBlock(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    this.m_taskexitnum = 0;
    if (pn==null||isEmpty(pn.getTerminal()))
      return new BlockNode();
    ParseNode bsn=pn.getChild("block_statement_list");
    return parseBlockHelper(cn, md, isStaticContext, bsn);
  }

  private BlockNode parseBlockHelper(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    ParseNodeVector pnv=pn.getChildren();
    BlockNode bn=new BlockNode();
    for(int i=0; i<pnv.size(); i++) {
      Vector bsv=parseBlockStatement(cn, md, isStaticContext, pnv.elementAt(i));
      for(int j=0; j<bsv.size(); j++) {
        bn.addBlockStatement((BlockStatementNode)bsv.get(j));
      }
    }
    return bn;
  }

  public BlockNode parseSingleBlock(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn, String label){
    BlockNode bn=new BlockNode();
    Vector bsv=parseBlockStatement(cn, md, isStaticContext, pn,label);
    for(int j=0; j<bsv.size(); j++) {
      bn.addBlockStatement((BlockStatementNode)bsv.get(j));
    }
    bn.setStyle(BlockNode.NOBRACES);
    return bn;
  }
  
  public BlockNode parseSingleBlock(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn) {
    return parseSingleBlock(cn, md, isStaticContext, pn, null);
  }

  public Vector parseSESEBlock(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, Vector parentbs, ParseNode pn) {
    ParseNodeVector pnv=pn.getChildren();
    Vector bv=new Vector();
    for(int i=0; i<pnv.size(); i++) {
      bv.addAll(parseBlockStatement(cn, md, isStaticContext, pnv.elementAt(i)));
    }
    return bv;
  }
  
  public Vector parseBlockStatement(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn){
    return parseBlockStatement(cn, md, isStaticContext, pn,null);
  }

  public Vector parseBlockStatement(ClassDescriptor cn, MethodDescriptor md, boolean isStaticContext, ParseNode pn, String label) {
    Vector blockstatements=new Vector();
    if (isNode(pn,"tag_declaration")) {
      String name=pn.getChild("single").getTerminal();
      String type=pn.getChild("type").getTerminal();

      TagDeclarationNode tdn=new TagDeclarationNode(name, type);
      tdn.setNumLine(pn.getLine());

      blockstatements.add(tdn);
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
          en=parseExpression(cn, md, isStaticContext, epn.getFirstChild());

        DeclarationNode dn=new DeclarationNode(new VarDescriptor(arrayt, identifier),en);
        dn.setNumLine(tmp.getLine());
        
        ParseNode mn=pn.getChild("modifiers");
        if(mn!=null) {
          // here, modifers parse node has the list of annotations
          Modifiers m=parseModifiersList(mn);
          assignAnnotationsToType(m, arrayt);
        }

        blockstatements.add(dn);
      }
    } else if (isNode(pn,"nop")) {
      /* Do Nothing */
    } else if (isNode(pn,"expression")) {
      BlockExpressionNode ben=new BlockExpressionNode(parseExpression(cn, md, isStaticContext, pn.getFirstChild()));
      ben.setNumLine(pn.getLine());
      blockstatements.add(ben);
    } else if (isNode(pn,"ifstatement")) {
      IfStatementNode isn=new IfStatementNode(parseExpression(cn, md, isStaticContext, pn.getChild("condition").getFirstChild()),
                                              parseSingleBlock(cn, md, isStaticContext, pn.getChild("statement").getFirstChild()),
                                              pn.getChild("else_statement")!=null?parseSingleBlock(cn, md, isStaticContext, pn.getChild("else_statement").getFirstChild()):null);
      isn.setNumLine(pn.getLine());

      blockstatements.add(isn);
    } else if (isNode(pn,"switch_statement")) {
      // TODO add version for normal Java later
      SwitchStatementNode ssn=new SwitchStatementNode(parseExpression(cn, md, isStaticContext, pn.getChild("condition").getFirstChild()),
                                                      parseSingleBlock(cn, md, isStaticContext, pn.getChild("statement").getFirstChild()));
      ssn.setNumLine(pn.getLine());
      blockstatements.add(ssn);
    } else if (isNode(pn,"switch_block_list")) {
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
              SwitchLabelNode sln=new SwitchLabelNode(parseExpression(cn, md, isStaticContext, labeldecl.getChild("constant_expression").getFirstChild()), false);
              sln.setNumLine(labeldecl.getLine());
              slv.addElement(sln);
            } else if(isNode(labeldecl, "default_switch_label")) {
              SwitchLabelNode sln=new SwitchLabelNode(null, true);
              sln.setNumLine(labeldecl.getLine());
              slv.addElement(sln);
            }
          }

          SwitchBlockNode sbn=new SwitchBlockNode(slv,
                                                  parseSingleBlock(cn, md, isStaticContext, sblockdecl.getChild("switch_statements").getFirstChild()));
          sbn.setNumLine(sblockdecl.getLine());

          blockstatements.add(sbn);

        }
      }
    } else if (isNode(pn, "trycatchstatement")) {
      // TODO add version for normal Java later
      // Do not fully support exceptions now. Only make sure that if there are no
      // exceptions thrown, the execution is right
      ParseNode tpn = pn.getChild("tryblock").getFirstChild();
      BlockNode bn=parseBlockHelper(cn, md, isStaticContext, tpn);
      blockstatements.add(new SubBlockNode(bn));

      ParseNode fbk = pn.getChild("finallyblock");
      if(fbk != null) {
        ParseNode fpn = fbk.getFirstChild();
        BlockNode fbn=parseBlockHelper(cn, md, isStaticContext, fpn);
        blockstatements.add(new SubBlockNode(fbn));
      }
    } else if (isNode(pn, "throwstatement")) {
      // TODO Simply return here
      //blockstatements.add(new ReturnNode());
    } else if (isNode(pn,"taskexit")) {
      Vector vfe=null;
      if (pn.getChild("flag_effects_list")!=null)
        vfe=parseFlags(pn.getChild("flag_effects_list"));
      Vector ccs=null;
      if (pn.getChild("cons_checks")!=null)
        ccs=parseChecks(pn.getChild("cons_checks"));
      TaskExitNode ten=new TaskExitNode(vfe, ccs, this.m_taskexitnum++);
      ten.setNumLine(pn.getLine());
      blockstatements.add(ten);
    } else if (isNode(pn,"atomic")) {
      BlockNode bn=parseBlockHelper(cn, md, isStaticContext, pn);
      AtomicNode an=new AtomicNode(bn);
      an.setNumLine(pn.getLine());
      blockstatements.add(an);
    } else if (isNode(pn,"synchronized")) {
      BlockNode bn=parseBlockHelper(cn, md, isStaticContext, pn.getChild("block"));
      ExpressionNode en=parseExpression(cn, md, isStaticContext, pn.getChild("expr").getFirstChild());
      SynchronizedNode sn=new SynchronizedNode(en, bn);
      sn.setNumLine(pn.getLine());
      blockstatements.add(sn);
    } else if (isNode(pn,"return")) {
      if (isEmpty(pn.getTerminal()))
        blockstatements.add(new ReturnNode());
      else {
        ExpressionNode en=parseExpression(cn, md, isStaticContext, pn.getFirstChild());
        ReturnNode rn=new ReturnNode(en);
        rn.setNumLine(pn.getLine());
        blockstatements.add(rn);
      }
    } else if (isNode(pn,"block_statement_list")) {
      BlockNode bn=parseBlockHelper(cn, md, isStaticContext, pn);
      blockstatements.add(new SubBlockNode(bn));
    } else if (isNode(pn,"empty")) {
      /* nop */
    } else if (isNode(pn,"statement_expression_list")) {
      ParseNodeVector pnv=pn.getChildren();
      BlockNode bn=new BlockNode();
      for(int i=0; i<pnv.size(); i++) {
        ExpressionNode en=parseExpression(cn, md, isStaticContext, pnv.elementAt(i));
        blockstatements.add(new BlockExpressionNode(en));
      }
      bn.setStyle(BlockNode.EXPRLIST);
    } else if (isNode(pn,"forstatement")) {
      BlockNode init=parseSingleBlock(cn, md, isStaticContext, pn.getChild("initializer").getFirstChild());
      BlockNode update=parseSingleBlock(cn, md, isStaticContext, pn.getChild("update").getFirstChild());
      ExpressionNode condition=parseExpression(cn, md, isStaticContext, pn.getChild("condition").getFirstChild());
      BlockNode body=parseSingleBlock(cn, md, isStaticContext, pn.getChild("statement").getFirstChild());
      if(condition == null) {
        // no condition clause, make a 'true' expression as the condition
        condition = (ExpressionNode) new LiteralNode("boolean", new Boolean(true));
      }
      LoopNode ln=new LoopNode(init,condition,update,body,label);
      ln.setNumLine(pn.getLine());
      blockstatements.add(ln);
    } else if (isNode(pn,"whilestatement")) {
      ExpressionNode condition=parseExpression(cn, md, isStaticContext, pn.getChild("condition").getFirstChild());
      BlockNode body=parseSingleBlock(cn, md, isStaticContext, pn.getChild("statement").getFirstChild());
      if(condition == null) {
        // no condition clause, make a 'true' expression as the condition
        condition = (ExpressionNode) new LiteralNode("boolean", new Boolean(true));
      }
      blockstatements.add(new LoopNode(condition,body,LoopNode.WHILELOOP,label));
    } else if (isNode(pn,"dowhilestatement")) {
      ExpressionNode condition=parseExpression(cn, md, isStaticContext, pn.getChild("condition").getFirstChild());
      BlockNode body=parseSingleBlock(cn, md, isStaticContext, pn.getChild("statement").getFirstChild());
      if(condition == null) {
        // no condition clause, make a 'true' expression as the condition
        condition = (ExpressionNode) new LiteralNode("boolean", new Boolean(true));
      }
      blockstatements.add(new LoopNode(condition,body,LoopNode.DOWHILELOOP,label));
    } else if (isNode(pn,"sese")) {
      ParseNode pnID=pn.getChild("identifier");
      String stID=null;
      if( pnID != null ) {
        stID=pnID.getFirstChild().getTerminal();
      }
      SESENode start=new SESENode(stID);
      start.setNumLine(pn.getLine());
      SESENode end  =new SESENode(stID);
      start.setEnd(end);
      end.setStart(start);
      blockstatements.add(start);
      blockstatements.addAll(parseSESEBlock(cn, md, isStaticContext, blockstatements,pn.getChild("body").getFirstChild()));
      blockstatements.add(end);
    } else if (isNode(pn,"continue")) {
      ContinueBreakNode cbn=new ContinueBreakNode(false);
      cbn.setNumLine(pn.getLine());
      blockstatements.add(cbn);
    } else if (isNode(pn,"break")) {
      ContinueBreakNode cbn=new ContinueBreakNode(true);
      cbn.setNumLine(pn.getLine());
      blockstatements.add(cbn);
      ParseNode idopt_pn=pn.getChild("identifier_opt");
      ParseNode name_pn=idopt_pn.getChild("name");
      // name_pn.getTerminal() gives you the label

    } else if (isNode(pn,"genreach")) {
      String graphName = pn.getChild("graphName").getTerminal();
      blockstatements.add(new GenReachNode(graphName) );

    } else if (isNode(pn,"gen_def_reach")) {
      String outputName = pn.getChild("outputName").getTerminal();
      blockstatements.add(new GenDefReachNode(outputName) );

    } else if(isNode(pn,"labeledstatement")) {
      String labeledstatement = pn.getChild("name").getTerminal();
      BlockNode bn=parseSingleBlock(cn, md, isStaticContext, pn.getChild("statement").getFirstChild(),labeledstatement);
      blockstatements.add(new SubBlockNode(bn));
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
      } else  {

        TypeDescriptor type=parseTypeDescriptor(paramn);

        ParseNode tmp=paramn;
        while (tmp.getChild("single")==null) {
          type=type.makeArray(state);
          tmp=tmp.getChild("array");
        }
        String paramname=tmp.getChild("single").getTerminal();

        md.addParameter(type, paramname);
        if(isNode(paramn, "annotation_parameter")) {
          ParseNode listnode=paramn.getChild("annotation_list");
          parseParameterAnnotation(listnode,type);
        }

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
        else if(isNode(modn,"annotation_list"))
          parseAnnotationList(modn,m);
        else {
          throw new Error("Unrecognized Modifier:"+modn.getLabel());
        }
      }
    }
    return m;
  }

  private void parseAnnotationList(ParseNode pn, Modifiers m) {
    ParseNodeVector pnv = pn.getChildren();
    for (int i = 0; i < pnv.size(); i++) {
      ParseNode body_list = pnv.elementAt(i);
      if (isNode(body_list, "annotation_body")) {
        ParseNode body_node = body_list.getFirstChild();
        if (isNode(body_node, "marker_annotation")) {
          m.addAnnotation(new AnnotationDescriptor(body_node.getChild("name").getTerminal()));
        } else if (isNode(body_node, "single_annotation")) {
          m.addAnnotation(new AnnotationDescriptor(body_node.getChild("name").getTerminal(),
                                                   body_node.getChild("element_value").getTerminal()));
        } else if (isNode(body_node, "normal_annotation")) {
          throw new Error("Annotation with multiple data members is not supported yet.");
        }
      }
    }
  }

  private void parseParameterAnnotation(ParseNode pn,TypeDescriptor type) {
    ParseNodeVector pnv = pn.getChildren();
    for (int i = 0; i < pnv.size(); i++) {
      ParseNode body_list = pnv.elementAt(i);
      if (isNode(body_list, "annotation_body")) {
        ParseNode body_node = body_list.getFirstChild();
        if (isNode(body_node, "marker_annotation")) {
          type.addAnnotationMarker(new AnnotationDescriptor(body_node.getChild("name").getTerminal()));
        } else if (isNode(body_node, "single_annotation")) {
          type.addAnnotationMarker(new AnnotationDescriptor(body_node.getChild("name").getTerminal(),
                                                   body_node.getChild("element_value").getTerminal()));
        } else if (isNode(body_node, "normal_annotation")) {
          throw new Error("Annotation with multiple data members is not supported yet.");
        }
      }
    }
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
