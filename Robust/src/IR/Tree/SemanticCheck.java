package IR.Tree;

import java.util.*;
import IR.*;

public class SemanticCheck {
  State state;
  TypeUtil typeutil;
  Stack loopstack;
  HashSet toanalyze;
  HashSet completed;


  public SemanticCheck(State state, TypeUtil tu) {
    this.state=state;
    this.typeutil=tu;
    this.loopstack=new Stack();
    this.toanalyze=new HashSet();
    this.completed=new HashSet();
  }

  public ClassDescriptor getClass(String classname) {
    ClassDescriptor cd=typeutil.getClass(classname, toanalyze);
    checkClass(cd);
    return cd;
  }

  private void checkClass(ClassDescriptor cd) {
    if (!completed.contains(cd)) {
      completed.add(cd);
      
      //Set superclass link up
      if (cd.getSuper()!=null) {
	cd.setSuper(getClass(cd.getSuper()));
	// Link together Field, Method, and Flag tables so classes
	// inherit these from their superclasses
	cd.getFieldTable().setParent(cd.getSuperDesc().getFieldTable());
	cd.getMethodTable().setParent(cd.getSuperDesc().getMethodTable());
	cd.getFlagTable().setParent(cd.getSuperDesc().getFlagTable());
      }
      
      /* Check to see that fields are well typed */
      for(Iterator field_it=cd.getFields(); field_it.hasNext();) {
	FieldDescriptor fd=(FieldDescriptor)field_it.next();
	checkField(cd,fd);
      }
      
      for(Iterator method_it=cd.getMethods(); method_it.hasNext();) {
	MethodDescriptor md=(MethodDescriptor)method_it.next();
	checkMethod(cd,md);
      }
    }
  }

  public void semanticCheck() {
    SymbolTable classtable=state.getClassSymbolTable();
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());

    // Do methods next
    while(!toanalyze.isEmpty()) {
      Object obj=toanalyze.iterator().next();
      if (obj instanceof TaskDescriptor) {
	toanalyze.remove(obj);
	TaskDescriptor td=(TaskDescriptor)obj;
	try {
	  checkTask(td);
	} catch( Error e ) {
	    System.out.println( "Error in "+td );
	    throw e;
	}
      } else {
	ClassDescriptor cd=(ClassDescriptor)obj;
	toanalyze.remove(cd);
	//need to initialize typeutil object here...only place we can
	//get class descriptors without first calling getclass
	getClass(cd.getSymbol());
	for(Iterator method_it=cd.getMethods(); method_it.hasNext();) {
	  MethodDescriptor md=(MethodDescriptor)method_it.next();
	  try {
	    checkMethodBody(cd,md);
	  } catch( Error e ) {
	    System.out.println( "Error in "+md );
	    throw e;
	  }
	}
      }
    }
  }

  public void checkTypeDescriptor(TypeDescriptor td) {
    if (td.isPrimitive())
      return;       /* Done */
    else if (td.isClass()) {
      String name=td.toString();
      ClassDescriptor field_cd=getClass(name);
      if (field_cd==null)
	throw new Error("Undefined class "+name);
      td.setClassDescriptor(field_cd);
      return;
    } else if (td.isTag())
      return;
    else
      throw new Error();
  }

  public void checkField(ClassDescriptor cd, FieldDescriptor fd) {
    checkTypeDescriptor(fd.getType());
  }

  public void checkConstraintCheck(TaskDescriptor td, SymbolTable nametable, Vector ccs) {
    if (ccs==null)
      return;       /* No constraint checks to check */
    for(int i=0; i<ccs.size(); i++) {
      ConstraintCheck cc=(ConstraintCheck) ccs.get(i);

      for(int j=0; j<cc.numArgs(); j++) {
	ExpressionNode en=cc.getArg(j);
	checkExpressionNode(td,nametable,en,null);
      }
    }
  }

  public void checkFlagEffects(TaskDescriptor td, Vector vfe, SymbolTable nametable) {
    if (vfe==null)
      return;       /* No flag effects to check */
    for(int i=0; i<vfe.size(); i++) {
      FlagEffects fe=(FlagEffects) vfe.get(i);
      String varname=fe.getName();
      //Make sure the variable is declared as a parameter to the task
      VarDescriptor vd=(VarDescriptor)td.getParameterTable().get(varname);
      if (vd==null)
	throw new Error("Parameter "+varname+" in Flag Effects not declared in "+td);
      fe.setVar(vd);

      //Make sure it correspods to a class
      TypeDescriptor type_d=vd.getType();
      if (!type_d.isClass())
	throw new Error("Cannot have non-object argument for flag_effect");

      ClassDescriptor cd=type_d.getClassDesc();
      for(int j=0; j<fe.numEffects(); j++) {
	FlagEffect flag=fe.getEffect(j);
	String name=flag.getName();
	FlagDescriptor flag_d=(FlagDescriptor)cd.getFlagTable().get(name);
	//Make sure the flag is declared
	if (flag_d==null)
	  throw new Error("Flag descriptor "+name+" undefined in class: "+cd.getSymbol());
	if (flag_d.getExternal())
	  throw new Error("Attempting to modify external flag: "+name);
	flag.setFlag(flag_d);
      }
      for(int j=0; j<fe.numTagEffects(); j++) {
	TagEffect tag=fe.getTagEffect(j);
	String name=tag.getName();

	Descriptor d=(Descriptor)nametable.get(name);
	if (d==null)
	  throw new Error("Tag descriptor "+name+" undeclared");
	else if (!(d instanceof TagVarDescriptor))
	  throw new Error(name+" is not a tag descriptor");
	tag.setTag((TagVarDescriptor)d);
      }
    }
  }

  public void checkTask(TaskDescriptor td) {
    for(int i=0; i<td.numParameters(); i++) {
      /* Check that parameter is well typed */
      TypeDescriptor param_type=td.getParamType(i);
      checkTypeDescriptor(param_type);

      /* Check the parameter's flag expression is well formed */
      FlagExpressionNode fen=td.getFlag(td.getParameter(i));
      if (!param_type.isClass())
	throw new Error("Cannot have non-object argument to a task");
      ClassDescriptor cd=param_type.getClassDesc();
      if (fen!=null)
	checkFlagExpressionNode(cd, fen);
    }

    checkFlagEffects(td, td.getFlagEffects(),td.getParameterTable());
    /* Check that the task code is valid */
    BlockNode bn=state.getMethodBody(td);
    checkBlockNode(td, td.getParameterTable(),bn);
  }

  public void checkFlagExpressionNode(ClassDescriptor cd, FlagExpressionNode fen) {
    switch(fen.kind()) {
    case Kind.FlagOpNode:
    {
      FlagOpNode fon=(FlagOpNode)fen;
      checkFlagExpressionNode(cd, fon.getLeft());
      if (fon.getRight()!=null)
	checkFlagExpressionNode(cd, fon.getRight());
      break;
    }

    case Kind.FlagNode:
    {
      FlagNode fn=(FlagNode)fen;
      String name=fn.getFlagName();
      FlagDescriptor fd=(FlagDescriptor)cd.getFlagTable().get(name);
      if (fd==null)
	throw new Error("Undeclared flag: "+name);
      fn.setFlag(fd);
      break;
    }

    default:
      throw new Error("Unrecognized FlagExpressionNode");
    }
  }

  public void checkMethod(ClassDescriptor cd, MethodDescriptor md) {
    /* Check return type */
    if (!md.isConstructor())
      if (!md.getReturnType().isVoid())
	checkTypeDescriptor(md.getReturnType());

    for(int i=0; i<md.numParameters(); i++) {
      TypeDescriptor param_type=md.getParamType(i);
      checkTypeDescriptor(param_type);
    }
    /* Link the naming environments */
    if (!md.isStatic())     /* Fields aren't accessible directly in a static method, so don't link in this table */
      md.getParameterTable().setParent(cd.getFieldTable());
    md.setClassDesc(cd);
    if (!md.isStatic()) {
      VarDescriptor thisvd=new VarDescriptor(new TypeDescriptor(cd),"this");
      md.setThis(thisvd);
    }
  }

  public void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    ClassDescriptor superdesc=cd.getSuperDesc();
    if (superdesc!=null) {
      Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
      for(Iterator methodit=possiblematches.iterator(); methodit.hasNext();) {
	MethodDescriptor matchmd=(MethodDescriptor)methodit.next();
	if (md.matches(matchmd)) {
	  if (matchmd.getModifiers().isFinal()) {
	    throw new Error("Try to override final method in method:"+md+" declared in  "+cd);
	  }
	}
      }
    }
    BlockNode bn=state.getMethodBody(md);
    checkBlockNode(md, md.getParameterTable(),bn);
  }

  public void checkBlockNode(Descriptor md, SymbolTable nametable, BlockNode bn) {
    /* Link in the naming environment */
    bn.getVarTable().setParent(nametable);
    for(int i=0; i<bn.size(); i++) {
      BlockStatementNode bsn=bn.get(i);
      checkBlockStatementNode(md, bn.getVarTable(),bsn);
    }
  }

  public void checkBlockStatementNode(Descriptor md, SymbolTable nametable, BlockStatementNode bsn) {
    switch(bsn.kind()) {
    case Kind.BlockExpressionNode:
      checkBlockExpressionNode(md, nametable,(BlockExpressionNode)bsn);
      return;

    case Kind.DeclarationNode:
      checkDeclarationNode(md, nametable, (DeclarationNode)bsn);
      return;

    case Kind.TagDeclarationNode:
      checkTagDeclarationNode(md, nametable, (TagDeclarationNode)bsn);
      return;

    case Kind.IfStatementNode:
      checkIfStatementNode(md, nametable, (IfStatementNode)bsn);
      return;

    case Kind.LoopNode:
      checkLoopNode(md, nametable, (LoopNode)bsn);
      return;

    case Kind.ReturnNode:
      checkReturnNode(md, nametable, (ReturnNode)bsn);
      return;

    case Kind.TaskExitNode:
      checkTaskExitNode(md, nametable, (TaskExitNode)bsn);
      return;

    case Kind.SubBlockNode:
      checkSubBlockNode(md, nametable, (SubBlockNode)bsn);
      return;

    case Kind.AtomicNode:
      checkAtomicNode(md, nametable, (AtomicNode)bsn);
      return;

    case Kind.ContinueBreakNode:
	checkContinueBreakNode(md, nametable, (ContinueBreakNode) bsn);
	return;

    case Kind.SESENode:
      // do nothing, no semantic check for SESEs
      return;
    }

    throw new Error();
  }

  void checkBlockExpressionNode(Descriptor md, SymbolTable nametable, BlockExpressionNode ben) {
    checkExpressionNode(md, nametable, ben.getExpression(), null);
  }

  void checkDeclarationNode(Descriptor md, SymbolTable nametable,  DeclarationNode dn) {
    VarDescriptor vd=dn.getVarDescriptor();
    checkTypeDescriptor(vd.getType());
    Descriptor d=nametable.get(vd.getSymbol());
    if ((d==null)||
        (d instanceof FieldDescriptor)) {
      nametable.add(vd);
    } else
      throw new Error(vd.getSymbol()+" in "+md+" defined a second time");
    if (dn.getExpression()!=null)
      checkExpressionNode(md, nametable, dn.getExpression(), vd.getType());
  }

  void checkTagDeclarationNode(Descriptor md, SymbolTable nametable,  TagDeclarationNode dn) {
    TagVarDescriptor vd=dn.getTagVarDescriptor();
    Descriptor d=nametable.get(vd.getSymbol());
    if ((d==null)||
        (d instanceof FieldDescriptor)) {
      nametable.add(vd);
    } else
      throw new Error(vd.getSymbol()+" defined a second time");
  }

  void checkSubBlockNode(Descriptor md, SymbolTable nametable, SubBlockNode sbn) {
    checkBlockNode(md, nametable, sbn.getBlockNode());
  }

  void checkAtomicNode(Descriptor md, SymbolTable nametable, AtomicNode sbn) {
    checkBlockNode(md, nametable, sbn.getBlockNode());
  }

  void checkContinueBreakNode(Descriptor md, SymbolTable nametable, ContinueBreakNode cbn) {
      if (loopstack.empty())
	  throw new Error("continue/break outside of loop");
      LoopNode ln=(LoopNode)loopstack.peek();
      cbn.setLoop(ln);
  }

  void checkReturnNode(Descriptor d, SymbolTable nametable, ReturnNode rn) {
    if (d instanceof TaskDescriptor)
      throw new Error("Illegal return appears in Task: "+d.getSymbol());
    MethodDescriptor md=(MethodDescriptor)d;
    if (rn.getReturnExpression()!=null)
      if (md.getReturnType()==null)
	throw new Error("Constructor can't return something.");
      else if (md.getReturnType().isVoid())
	throw new Error(md+" is void");
      else
	checkExpressionNode(md, nametable, rn.getReturnExpression(), md.getReturnType());
    else
    if (md.getReturnType()!=null&&!md.getReturnType().isVoid())
      throw new Error("Need to return something for "+md);
  }

  void checkTaskExitNode(Descriptor md, SymbolTable nametable, TaskExitNode ten) {
    if (md instanceof MethodDescriptor)
      throw new Error("Illegal taskexit appears in Method: "+md.getSymbol());
    checkFlagEffects((TaskDescriptor)md, ten.getFlagEffects(),nametable);
    checkConstraintCheck((TaskDescriptor) md, nametable, ten.getChecks());
  }

  void checkIfStatementNode(Descriptor md, SymbolTable nametable, IfStatementNode isn) {
    checkExpressionNode(md, nametable, isn.getCondition(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
    checkBlockNode(md, nametable, isn.getTrueBlock());
    if (isn.getFalseBlock()!=null)
      checkBlockNode(md, nametable, isn.getFalseBlock());
  }

  void checkExpressionNode(Descriptor md, SymbolTable nametable, ExpressionNode en, TypeDescriptor td) {
    switch(en.kind()) {
    case Kind.AssignmentNode:
      checkAssignmentNode(md,nametable,(AssignmentNode)en,td);
      return;

    case Kind.CastNode:
      checkCastNode(md,nametable,(CastNode)en,td);
      return;

    case Kind.CreateObjectNode:
      checkCreateObjectNode(md,nametable,(CreateObjectNode)en,td);
      return;

    case Kind.FieldAccessNode:
      checkFieldAccessNode(md,nametable,(FieldAccessNode)en,td);
      return;

    case Kind.ArrayAccessNode:
      checkArrayAccessNode(md,nametable,(ArrayAccessNode)en,td);
      return;

    case Kind.LiteralNode:
      checkLiteralNode(md,nametable,(LiteralNode)en,td);
      return;

    case Kind.MethodInvokeNode:
      checkMethodInvokeNode(md,nametable,(MethodInvokeNode)en,td);
      return;

    case Kind.NameNode:
      checkNameNode(md,nametable,(NameNode)en,td);
      return;

    case Kind.OpNode:
      checkOpNode(md,nametable,(OpNode)en,td);
      return;

    case Kind.OffsetNode:
      checkOffsetNode(md, nametable, (OffsetNode)en, new TypeDescriptor(TypeDescriptor.OFFSET));
      return;

    case Kind.TertiaryNode:
      checkTertiaryNode(md, nametable, (TertiaryNode)en, td);
      return;
      
    case Kind.InstanceOfNode:
      checkInstanceOfNode(md, nametable, (InstanceOfNode) en, td);
      return;

    case Kind.ArrayInitializerNode:
      checkArrayInitializerNode(md, nametable, (ArrayInitializerNode) en, td);
      return;
    }
    throw new Error();
  }

  void checkCastNode(Descriptor md, SymbolTable nametable, CastNode cn, TypeDescriptor td) {
    /* Get type descriptor */
    if (cn.getType()==null) {
      NameDescriptor typenamed=cn.getTypeName().getName();
      String typename=typenamed.toString();
      TypeDescriptor ntd=new TypeDescriptor(getClass(typename));
      cn.setType(ntd);
    }

    /* Check the type descriptor */
    TypeDescriptor cast_type=cn.getType();
    checkTypeDescriptor(cast_type);

    /* Type check */
    if (td!=null) {
      if (!typeutil.isSuperorType(td,cast_type))
	throw new Error("Cast node returns "+cast_type+", but need "+td);
    }

    ExpressionNode en=cn.getExpression();
    checkExpressionNode(md, nametable, en, null);
    TypeDescriptor etd=en.getType();
    if (typeutil.isSuperorType(cast_type,etd))     /* Cast trivially succeeds */
      return;

    if (typeutil.isSuperorType(etd,cast_type))     /* Cast may succeed */
      return;
    if (typeutil.isCastable(etd, cast_type))
      return;

    /* Different branches */
    /* TODO: change if add interfaces */
    throw new Error("Cast will always fail\n"+cn.printNode(0));
  }

  void checkFieldAccessNode(Descriptor md, SymbolTable nametable, FieldAccessNode fan, TypeDescriptor td) {
    ExpressionNode left=fan.getExpression();
    checkExpressionNode(md,nametable,left,null);
    TypeDescriptor ltd=left.getType();
    String fieldname=fan.getFieldName();

    FieldDescriptor fd=null;
    if (ltd.isArray()&&fieldname.equals("length"))
      fd=FieldDescriptor.arrayLength;
    else
      fd=(FieldDescriptor) ltd.getClassDesc().getFieldTable().get(fieldname);
    if (fd==null)
      throw new Error("Unknown field "+fieldname + " in "+fan.printNode(0)+" in "+md);
    fan.setField(fd);
    if (td!=null)
      if (!typeutil.isSuperorType(td,fan.getType()))
	throw new Error("Field node returns "+fan.getType()+", but need "+td);
  }

  void checkArrayAccessNode(Descriptor md, SymbolTable nametable, ArrayAccessNode aan, TypeDescriptor td) {
    ExpressionNode left=aan.getExpression();
    checkExpressionNode(md,nametable,left,null);

    checkExpressionNode(md,nametable,aan.getIndex(),new TypeDescriptor(TypeDescriptor.INT));
    TypeDescriptor ltd=left.getType();

    if (td!=null)
      if (!typeutil.isSuperorType(td,aan.getType()))
	throw new Error("Field node returns "+aan.getType()+", but need "+td);
  }

  void checkLiteralNode(Descriptor md, SymbolTable nametable, LiteralNode ln, TypeDescriptor td) {
    /* Resolve the type */
    Object o=ln.getValue();
    if (ln.getTypeString().equals("null")) {
      ln.setType(new TypeDescriptor(TypeDescriptor.NULL));
    } else if (o instanceof Integer) {
      ln.setType(new TypeDescriptor(TypeDescriptor.INT));
    } else if (o instanceof Long) {
      ln.setType(new TypeDescriptor(TypeDescriptor.LONG));
    } else if (o instanceof Float) {
      ln.setType(new TypeDescriptor(TypeDescriptor.FLOAT));
    } else if (o instanceof Boolean) {
      ln.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
    } else if (o instanceof Double) {
      ln.setType(new TypeDescriptor(TypeDescriptor.DOUBLE));
    } else if (o instanceof Character) {
      ln.setType(new TypeDescriptor(TypeDescriptor.CHAR));
    } else if (o instanceof String) {
      ln.setType(new TypeDescriptor(getClass(TypeUtil.StringClass)));
    }

    if (td!=null)
      if (!typeutil.isSuperorType(td,ln.getType()))
	throw new Error("Field node returns "+ln.getType()+", but need "+td+" in "+md);
  }

  void checkNameNode(Descriptor md, SymbolTable nametable, NameNode nn, TypeDescriptor td) {
    NameDescriptor nd=nn.getName();
    if (nd.getBase()!=null) {
      /* Big hack */
      /* Rewrite NameNode */
      ExpressionNode en=translateNameDescriptorintoExpression(nd);
      nn.setExpression(en);
      checkExpressionNode(md,nametable,en,td);
    } else {
      String varname=nd.toString();
      Descriptor d=(Descriptor)nametable.get(varname);
      if (d==null) {
	throw new Error("Name "+varname+" undefined in: "+md);
      }
      if (d instanceof VarDescriptor) {
	nn.setVar(d);
      } else if (d instanceof FieldDescriptor) {
	nn.setField((FieldDescriptor)d);
	nn.setVar((VarDescriptor)nametable.get("this"));        /* Need a pointer to this */
      } else if (d instanceof TagVarDescriptor) {
	nn.setVar(d);
      } else throw new Error("Wrong type of descriptor");
      if (td!=null)
	if (!typeutil.isSuperorType(td,nn.getType()))
	  throw new Error("Field node returns "+nn.getType()+", but need "+td);
    }
  }

  void checkOffsetNode(Descriptor md, SymbolTable nameTable, OffsetNode ofn, TypeDescriptor td) {
    TypeDescriptor ltd=ofn.td;
    checkTypeDescriptor(ltd);
    
    String fieldname = ofn.fieldname;
    FieldDescriptor fd=null;
    if (ltd.isArray()&&fieldname.equals("length")) {
      fd=FieldDescriptor.arrayLength;
    } else {
      fd=(FieldDescriptor) ltd.getClassDesc().getFieldTable().get(fieldname);
    }

    ofn.setField(fd);
    checkField(ltd.getClassDesc(), fd);

    if (fd==null)
      throw new Error("Unknown field "+fieldname + " in "+ofn.printNode(1)+" in "+md);

    if (td!=null) {
      if (!typeutil.isSuperorType(td, ofn.getType())) {
	System.out.println(td);
	System.out.println(ofn.getType());
	throw new Error("Type of rside not compatible with type of lside"+ofn.printNode(0));
      }
    }
  }


  void checkTertiaryNode(Descriptor md, SymbolTable nametable, TertiaryNode tn, TypeDescriptor td) {
    checkExpressionNode(md, nametable, tn.getCond(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
    checkExpressionNode(md, nametable, tn.getTrueExpr(), td );
    checkExpressionNode(md, nametable, tn.getFalseExpr(), td );
  }

  void checkInstanceOfNode(Descriptor md, SymbolTable nametable, InstanceOfNode tn, TypeDescriptor td) {
    if (td!=null&&!td.isBoolean())
      throw new Error("Expecting type "+td+"for instanceof expression");
    
    checkTypeDescriptor(tn.getExprType());
    checkExpressionNode(md, nametable, tn.getExpr(), null);
  }

  void checkArrayInitializerNode(Descriptor md, SymbolTable nametable, ArrayInitializerNode ain, TypeDescriptor td) {
    for( int i = 0; i < ain.numVarInitializers(); ++i ) {
      checkExpressionNode(md, nametable, ain.getVarInitializer(i), td); 
    }
  }

  void checkAssignmentNode(Descriptor md, SymbolTable nametable, AssignmentNode an, TypeDescriptor td) {
    boolean postinc=true;
    if (an.getOperation().getBaseOp()==null||
        (an.getOperation().getBaseOp().getOp()!=Operation.POSTINC&&
         an.getOperation().getBaseOp().getOp()!=Operation.POSTDEC))
      postinc=false;
    if (!postinc)      
      checkExpressionNode(md, nametable, an.getSrc(),td);
    //TODO: Need check on validity of operation here
    if (!((an.getDest() instanceof FieldAccessNode)||
          (an.getDest() instanceof ArrayAccessNode)||
          (an.getDest() instanceof NameNode)))
      throw new Error("Bad lside in "+an.printNode(0));
    checkExpressionNode(md, nametable, an.getDest(), null);

    /* We want parameter variables to tasks to be immutable */
    if (md instanceof TaskDescriptor) {
      if (an.getDest() instanceof NameNode) {
	NameNode nn=(NameNode)an.getDest();
	if (nn.getVar()!=null) {
	  if (((TaskDescriptor)md).getParameterTable().contains(nn.getVar().getSymbol()))
	    throw new Error("Can't modify parameter "+nn.getVar()+ " to task "+td.getSymbol());
	}
      }
    }

    if (an.getDest().getType().isString()&&an.getOperation().getOp()==AssignOperation.PLUSEQ) {
      //String add
      ClassDescriptor stringcl=getClass(TypeUtil.StringClass);
      TypeDescriptor stringtd=new TypeDescriptor(stringcl);
      NameDescriptor nd=new NameDescriptor("String");
      NameDescriptor valuend=new NameDescriptor(nd, "valueOf");

      if (!(an.getSrc().getType().isString()&&(an.getSrc() instanceof OpNode))) {
	MethodInvokeNode rightmin=new MethodInvokeNode(valuend);
	rightmin.addArgument(an.getSrc());
	an.right=rightmin;
	checkExpressionNode(md, nametable, an.getSrc(), null);
      }
    }

    if (!postinc&&!typeutil.isSuperorType(an.getDest().getType(),an.getSrc().getType())) {
      throw new Error("Type of rside ("+an.getSrc().getType()+") not compatible with type of lside ("+an.getDest().getType()+")"+an.printNode(0));
    }
  }

  void checkLoopNode(Descriptor md, SymbolTable nametable, LoopNode ln) {
      loopstack.push(ln);
    if (ln.getType()==LoopNode.WHILELOOP||ln.getType()==LoopNode.DOWHILELOOP) {
      checkExpressionNode(md, nametable, ln.getCondition(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
      checkBlockNode(md, nametable, ln.getBody());
    } else {
      //For loop case
      /* Link in the initializer naming environment */
      BlockNode bn=ln.getInitializer();
      bn.getVarTable().setParent(nametable);
      for(int i=0; i<bn.size(); i++) {
	BlockStatementNode bsn=bn.get(i);
	checkBlockStatementNode(md, bn.getVarTable(),bsn);
      }
      //check the condition
      checkExpressionNode(md, bn.getVarTable(), ln.getCondition(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
      checkBlockNode(md, bn.getVarTable(), ln.getBody());
      checkBlockNode(md, bn.getVarTable(), ln.getUpdate());
    }
    loopstack.pop();
  }


  void checkCreateObjectNode(Descriptor md, SymbolTable nametable, CreateObjectNode con, TypeDescriptor td) {
    TypeDescriptor[] tdarray=new TypeDescriptor[con.numArgs()];
    for(int i=0; i<con.numArgs(); i++) {
      ExpressionNode en=con.getArg(i);
      checkExpressionNode(md,nametable,en,null);
      tdarray[i]=en.getType();
    }

    TypeDescriptor typetolookin=con.getType();
    checkTypeDescriptor(typetolookin);

    if (td!=null&&!typeutil.isSuperorType(td, typetolookin))
      throw new Error(typetolookin + " isn't a "+td);

    /* Check flag effects */
    if (con.getFlagEffects()!=null) {
      FlagEffects fe=con.getFlagEffects();
      ClassDescriptor cd=typetolookin.getClassDesc();

      for(int j=0; j<fe.numEffects(); j++) {
	FlagEffect flag=fe.getEffect(j);
	String name=flag.getName();
	FlagDescriptor flag_d=(FlagDescriptor)cd.getFlagTable().get(name);
	//Make sure the flag is declared
	if (flag_d==null)
	  throw new Error("Flag descriptor "+name+" undefined in class: "+cd.getSymbol());
	if (flag_d.getExternal())
	  throw new Error("Attempting to modify external flag: "+name);
	flag.setFlag(flag_d);
      }
      for(int j=0; j<fe.numTagEffects(); j++) {
	TagEffect tag=fe.getTagEffect(j);
	String name=tag.getName();

	Descriptor d=(Descriptor)nametable.get(name);
	if (d==null)
	  throw new Error("Tag descriptor "+name+" undeclared");
	else if (!(d instanceof TagVarDescriptor))
	  throw new Error(name+" is not a tag descriptor");
	tag.setTag((TagVarDescriptor)d);
      }
    }

    if ((!typetolookin.isClass())&&(!typetolookin.isArray()))
      throw new Error("Can't allocate primitive type:"+con.printNode(0));

    if (!typetolookin.isArray()) {
      //Array's don't need constructor calls
      ClassDescriptor classtolookin=typetolookin.getClassDesc();

      Set methoddescriptorset=classtolookin.getMethodTable().getSet(typetolookin.getSymbol());
      MethodDescriptor bestmd=null;
NextMethod:
      for(Iterator methodit=methoddescriptorset.iterator(); methodit.hasNext();) {
	MethodDescriptor currmd=(MethodDescriptor)methodit.next();
	/* Need correct number of parameters */
	if (con.numArgs()!=currmd.numParameters())
	  continue;
	for(int i=0; i<con.numArgs(); i++) {
	  if (!typeutil.isSuperorType(currmd.getParamType(i),tdarray[i]))
	    continue NextMethod;
	}
	/* Local allocations can't call global allocator */
	if (!con.isGlobal()&&currmd.isGlobal())
	  continue;

	/* Method okay so far */
	if (bestmd==null)
	  bestmd=currmd;
	else {
	  if (typeutil.isMoreSpecific(currmd,bestmd)) {
	    bestmd=currmd;
	  } else if (con.isGlobal()&&match(currmd, bestmd)) {
	    if (currmd.isGlobal()&&!bestmd.isGlobal())
	      bestmd=currmd;
	    else if (currmd.isGlobal()&&bestmd.isGlobal())
	      throw new Error();
	  } else if (!typeutil.isMoreSpecific(bestmd, currmd)) {
	    throw new Error("No method is most specific");
	  }

	  /* Is this more specific than bestmd */
	}
      }
      if (bestmd==null)
	throw new Error("No method found for "+con.printNode(0)+" in "+md);
      con.setConstructor(bestmd);
    }
  }


  /** Check to see if md1 is the same specificity as md2.*/

  boolean match(MethodDescriptor md1, MethodDescriptor md2) {
    /* Checks if md1 is more specific than md2 */
    if (md1.numParameters()!=md2.numParameters())
      throw new Error();
    for(int i=0; i<md1.numParameters(); i++) {
      if (!md2.getParamType(i).equals(md1.getParamType(i)))
	return false;
    }
    if (!md2.getReturnType().equals(md1.getReturnType()))
      return false;

    if (!md2.getClassDesc().equals(md1.getClassDesc()))
      return false;

    return true;
  }



  ExpressionNode translateNameDescriptorintoExpression(NameDescriptor nd) {
    String id=nd.getIdentifier();
    NameDescriptor base=nd.getBase();
    if (base==null)
      return new NameNode(nd);
    else
      return new FieldAccessNode(translateNameDescriptorintoExpression(base),id);
  }


  void checkMethodInvokeNode(Descriptor md, SymbolTable nametable, MethodInvokeNode min, TypeDescriptor td) {
    /*Typecheck subexpressions
       and get types for expressions*/

    TypeDescriptor[] tdarray=new TypeDescriptor[min.numArgs()];
    for(int i=0; i<min.numArgs(); i++) {
      ExpressionNode en=min.getArg(i);
      checkExpressionNode(md,nametable,en,null);
      tdarray[i]=en.getType();
    }
    TypeDescriptor typetolookin=null;
    if (min.getExpression()!=null) {
      checkExpressionNode(md,nametable,min.getExpression(),null);
      typetolookin=min.getExpression().getType();
      //if (typetolookin==null)
      //throw new Error(md+" has null return type");

    } else if (min.getBaseName()!=null) {
      String rootname=min.getBaseName().getRoot();
      if (rootname.equals("super")) {
	ClassDescriptor supercd=((MethodDescriptor)md).getClassDesc().getSuperDesc();
	typetolookin=new TypeDescriptor(supercd);
      } else if (nametable.get(rootname)!=null) {
	//we have an expression
	min.setExpression(translateNameDescriptorintoExpression(min.getBaseName()));
	checkExpressionNode(md, nametable, min.getExpression(), null);
	typetolookin=min.getExpression().getType();
      } else {
	//we have a type
	ClassDescriptor cd;
	if (min.getBaseName().getSymbol().equals("System.out"))
	  cd=getClass("System");
	else
	  cd=getClass(min.getBaseName().getSymbol());
	if (cd==null)
	  throw new Error("md = "+ md.toString()+ "  "+min.getBaseName()+" undefined");
	typetolookin=new TypeDescriptor(cd);
      }
    } else if ((md instanceof MethodDescriptor)&&min.getMethodName().equals("super")) {
      ClassDescriptor supercd=((MethodDescriptor)md).getClassDesc().getSuperDesc();
      min.methodid=supercd.getSymbol();
      typetolookin=new TypeDescriptor(supercd);
    } else if (md instanceof MethodDescriptor) {
      typetolookin=new TypeDescriptor(((MethodDescriptor)md).getClassDesc());
    } else {
      /* If this a task descriptor we throw an error at this point */
      throw new Error("Unknown method call to "+min.getMethodName()+"in task"+md.getSymbol());
    }
    if (!typetolookin.isClass())
      throw new Error("Error with method call to "+min.getMethodName());
    ClassDescriptor classtolookin=typetolookin.getClassDesc();
    //System.out.println("Method name="+min.getMethodName());

    Set methoddescriptorset=classtolookin.getMethodTable().getSet(min.getMethodName());
    MethodDescriptor bestmd=null;
NextMethod:
    for(Iterator methodit=methoddescriptorset.iterator(); methodit.hasNext();) {
      MethodDescriptor currmd=(MethodDescriptor)methodit.next();
      /* Need correct number of parameters */
      if (min.numArgs()!=currmd.numParameters())
	continue;
      for(int i=0; i<min.numArgs(); i++) {
	if (!typeutil.isSuperorType(currmd.getParamType(i),tdarray[i]))
	  continue NextMethod;
      }
      /* Method okay so far */
      if (bestmd==null)
	bestmd=currmd;
      else {
	if (typeutil.isMoreSpecific(currmd,bestmd)) {
	  bestmd=currmd;
	} else if (!typeutil.isMoreSpecific(bestmd, currmd))
	  throw new Error("No method is most specific");

	/* Is this more specific than bestmd */
      }
    }
    if (bestmd==null)
      throw new Error("No method found for :"+min.printNode(0)+" in class: " + classtolookin+" in "+md);
    min.setMethod(bestmd);

    if ((td!=null)&&(min.getType()!=null)&&!typeutil.isSuperorType(td,  min.getType()))
      throw new Error(min.getType()+ " is not equal to or a subclass of "+td);
    /* Check whether we need to set this parameter to implied this */
    if (!bestmd.isStatic()) {
      if (min.getExpression()==null) {
	ExpressionNode en=new NameNode(new NameDescriptor("this"));
	min.setExpression(en);
	checkExpressionNode(md, nametable, min.getExpression(), null);
      }
    }
  }


  void checkOpNode(Descriptor md, SymbolTable nametable, OpNode on, TypeDescriptor td) {
    checkExpressionNode(md, nametable, on.getLeft(), null);
    if (on.getRight()!=null)
      checkExpressionNode(md, nametable, on.getRight(), null);
    TypeDescriptor ltd=on.getLeft().getType();
    TypeDescriptor rtd=on.getRight()!=null ? on.getRight().getType() : null;
    TypeDescriptor lefttype=null;
    TypeDescriptor righttype=null;
    Operation op=on.getOp();

    switch(op.getOp()) {
    case Operation.LOGIC_OR:
    case Operation.LOGIC_AND:
      if (!(rtd.isBoolean()))
	throw new Error();
      on.setRightType(rtd);

    case Operation.LOGIC_NOT:
      if (!(ltd.isBoolean()))
	throw new Error();
      //no promotion
      on.setLeftType(ltd);

      on.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
      break;

    case Operation.COMP:
      // 5.6.2 Binary Numeric Promotion
      //TODO unboxing of reference objects
      if (ltd.isDouble())
	throw new Error();
      else if (ltd.isFloat())
	throw new Error();
      else if (ltd.isLong())
	lefttype=new TypeDescriptor(TypeDescriptor.LONG);
      else
	lefttype=new TypeDescriptor(TypeDescriptor.INT);
      on.setLeftType(lefttype);
      on.setType(lefttype);
      break;

    case Operation.BIT_OR:
    case Operation.BIT_XOR:
    case Operation.BIT_AND:
      // 5.6.2 Binary Numeric Promotion
      //TODO unboxing of reference objects
      if (ltd.isDouble()||rtd.isDouble())
	throw new Error();
      else if (ltd.isFloat()||rtd.isFloat())
	throw new Error();
      else if (ltd.isLong()||rtd.isLong())
	lefttype=new TypeDescriptor(TypeDescriptor.LONG);
      // 090205 hack for boolean
      else if (ltd.isBoolean()||rtd.isBoolean())
	lefttype=new TypeDescriptor(TypeDescriptor.BOOLEAN);
      else
	lefttype=new TypeDescriptor(TypeDescriptor.INT);
      righttype=lefttype;

      on.setLeftType(lefttype);
      on.setRightType(righttype);
      on.setType(lefttype);
      break;

    case Operation.ISAVAILABLE:
      if (!(ltd.isPtr())) {
	throw new Error("Can't use isavailable on non-pointers/non-parameters.");
      }
      lefttype=ltd;
      on.setLeftType(lefttype);
      on.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
      break;

    case Operation.EQUAL:
    case Operation.NOTEQUAL:
      // 5.6.2 Binary Numeric Promotion
      //TODO unboxing of reference objects
      if (ltd.isBoolean()||rtd.isBoolean()) {
	if (!(ltd.isBoolean()&&rtd.isBoolean()))
	  throw new Error();
	righttype=lefttype=new TypeDescriptor(TypeDescriptor.BOOLEAN);
      } else if (ltd.isPtr()||rtd.isPtr()) {
	if (!(ltd.isPtr()&&rtd.isPtr()))
	  throw new Error();
	righttype=rtd;
	lefttype=ltd;
      } else if (ltd.isDouble()||rtd.isDouble())
	righttype=lefttype=new TypeDescriptor(TypeDescriptor.DOUBLE);
      else if (ltd.isFloat()||rtd.isFloat())
	righttype=lefttype=new TypeDescriptor(TypeDescriptor.FLOAT);
      else if (ltd.isLong()||rtd.isLong())
	righttype=lefttype=new TypeDescriptor(TypeDescriptor.LONG);
      else
	righttype=lefttype=new TypeDescriptor(TypeDescriptor.INT);

      on.setLeftType(lefttype);
      on.setRightType(righttype);
      on.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
      break;



    case Operation.LT:
    case Operation.GT:
    case Operation.LTE:
    case Operation.GTE:
      // 5.6.2 Binary Numeric Promotion
      //TODO unboxing of reference objects
      if (!ltd.isNumber()||!rtd.isNumber())
	throw new Error();

      if (ltd.isDouble()||rtd.isDouble())
	lefttype=new TypeDescriptor(TypeDescriptor.DOUBLE);
      else if (ltd.isFloat()||rtd.isFloat())
	lefttype=new TypeDescriptor(TypeDescriptor.FLOAT);
      else if (ltd.isLong()||rtd.isLong())
	lefttype=new TypeDescriptor(TypeDescriptor.LONG);
      else
	lefttype=new TypeDescriptor(TypeDescriptor.INT);
      righttype=lefttype;
      on.setLeftType(lefttype);
      on.setRightType(righttype);
      on.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
      break;

    case Operation.ADD:
      if (ltd.isString()||rtd.isString()) {
	ClassDescriptor stringcl=getClass(TypeUtil.StringClass);
	TypeDescriptor stringtd=new TypeDescriptor(stringcl);
	NameDescriptor nd=new NameDescriptor("String");
	NameDescriptor valuend=new NameDescriptor(nd, "valueOf");
	if (!(ltd.isString()&&(on.getLeft() instanceof OpNode))) {
	  MethodInvokeNode leftmin=new MethodInvokeNode(valuend);
	  leftmin.addArgument(on.getLeft());
	  on.left=leftmin;
	  checkExpressionNode(md, nametable, on.getLeft(), null);
	}

	if (!(rtd.isString()&&(on.getRight() instanceof OpNode))) {
	  MethodInvokeNode rightmin=new MethodInvokeNode(valuend);
	  rightmin.addArgument(on.getRight());
	  on.right=rightmin;
	  checkExpressionNode(md, nametable, on.getRight(), null);
	}

	on.setLeftType(stringtd);
	on.setRightType(stringtd);
	on.setType(stringtd);
	break;
      }

    case Operation.SUB:
    case Operation.MULT:
    case Operation.DIV:
    case Operation.MOD:
      // 5.6.2 Binary Numeric Promotion
      //TODO unboxing of reference objects
      if (ltd.isArray()||rtd.isArray()||!ltd.isNumber()||!rtd.isNumber())
	throw new Error("Error in "+on.printNode(0));

      if (ltd.isDouble()||rtd.isDouble())
	lefttype=new TypeDescriptor(TypeDescriptor.DOUBLE);
      else if (ltd.isFloat()||rtd.isFloat())
	lefttype=new TypeDescriptor(TypeDescriptor.FLOAT);
      else if (ltd.isLong()||rtd.isLong())
	lefttype=new TypeDescriptor(TypeDescriptor.LONG);
      else
	lefttype=new TypeDescriptor(TypeDescriptor.INT);
      righttype=lefttype;
      on.setLeftType(lefttype);
      on.setRightType(righttype);
      on.setType(lefttype);
      break;

    case Operation.LEFTSHIFT:
    case Operation.RIGHTSHIFT:
    case Operation.URIGHTSHIFT:
      if (!rtd.isIntegerType())
	throw new Error();
      //5.6.1 Unary Numeric Promotion
      if (rtd.isByte()||rtd.isShort()||rtd.isInt())
	righttype=new TypeDescriptor(TypeDescriptor.INT);
      else
	righttype=rtd;

      on.setRightType(righttype);
      if (!ltd.isIntegerType())
	throw new Error();

    case Operation.UNARYPLUS:
    case Operation.UNARYMINUS:
      /*	case Operation.POSTINC:
          case Operation.POSTDEC:
          case Operation.PREINC:
          case Operation.PREDEC:*/
      if (!ltd.isNumber())
	throw new Error();
      //5.6.1 Unary Numeric Promotion
      if (ltd.isByte()||ltd.isShort()||ltd.isInt())
	lefttype=new TypeDescriptor(TypeDescriptor.INT);
      else
	lefttype=ltd;
      on.setLeftType(lefttype);
      on.setType(lefttype);
      break;

    default:
      throw new Error(op.toString());
    }

    if (td!=null)
      if (!typeutil.isSuperorType(td, on.getType())) {
	System.out.println(td);
	System.out.println(on.getType());
	throw new Error("Type of rside not compatible with type of lside"+on.printNode(0));
      }
  }
}
