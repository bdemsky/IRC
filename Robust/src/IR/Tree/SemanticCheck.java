package IR.Tree;

import java.util.*;
import IR.*;

public class SemanticCheck {
    State state;
    TypeUtil typeutil;

    public SemanticCheck(State state, TypeUtil tu) {
	this.state=state;
	this.typeutil=tu;
    }

    public void semanticCheck() {
	SymbolTable classtable=state.getClassSymbolTable();
	Iterator it=classtable.getDescriptorsIterator();
	// Do descriptors first
	while(it.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)it.next();
	    System.out.println("Checking class: "+cd);
	    //Set superclass link up
	    if (cd.getSuper()!=null) {
		cd.setSuper(typeutil.getClass(cd.getSuper()));
		// Link together Field, Method, and Flag tables so classes
		// inherit these from their superclasses
		cd.getFieldTable().setParent(cd.getSuperDesc().getFieldTable());
		cd.getMethodTable().setParent(cd.getSuperDesc().getMethodTable());
		cd.getFlagTable().setParent(cd.getSuperDesc().getFlagTable());
	    }
	    
	    /* Check to see that fields are well typed */
	    for(Iterator field_it=cd.getFields();field_it.hasNext();) {
		FieldDescriptor fd=(FieldDescriptor)field_it.next();
		System.out.println("Checking field: "+fd);
		checkField(cd,fd);
	    }

	    for(Iterator method_it=cd.getMethods();method_it.hasNext();) {
		MethodDescriptor md=(MethodDescriptor)method_it.next();
		checkMethod(cd,md);
	    }
	}

	it=classtable.getDescriptorsIterator();
	// Do descriptors first
	while(it.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)it.next();
	    for(Iterator method_it=cd.getMethods();method_it.hasNext();) {
		MethodDescriptor md=(MethodDescriptor)method_it.next();
		checkMethodBody(cd,md);
	    }
	}

	for(Iterator task_it=state.getTaskSymbolTable().getDescriptorsIterator();task_it.hasNext();) {
	    TaskDescriptor td=(TaskDescriptor)task_it.next();
	    checkTask(td);
	    
	}

    }

    public void checkTypeDescriptor(TypeDescriptor td) {
	if (td.isPrimitive())
	    return; /* Done */
	if (td.isClass()) {
	    String name=td.toString();
	    ClassDescriptor field_cd=(ClassDescriptor)state.getClassSymbolTable().get(name);
	    if (field_cd==null)
		throw new Error("Undefined class "+name);
	    td.setClassDescriptor(field_cd);
	    return;
	}
	throw new Error();
    }

    public void checkField(ClassDescriptor cd, FieldDescriptor fd) {
	checkTypeDescriptor(fd.getType());
    }

    public void checkFlagEffects(TaskDescriptor td, Vector vfe) {
	for(int i=0;i<vfe.size();i++) {
	    FlagEffects fe=(FlagEffects) vfe.get(i);
	    String varname=fe.getName();
	    //Make sure the variable is declared as a parameter to the task
	    VarDescriptor vd=(VarDescriptor)td.getParameterTable().get(varname);
	    if (vd==null)
		throw new Error("Parameter "+varname+" in Flag Effects not declared");
	    fe.setVar(vd);

	    //Make sure it correspods to a class
	    TypeDescriptor type_d=vd.getType();
	    if (!type_d.isClass())
		throw new Error("Cannot have non-object argument for flag_effect");

	    ClassDescriptor cd=type_d.getClassDesc();
	    for(int j=0;j<fe.numEffects();j++) {
		FlagEffect flag=fe.getEffect(j);
		String name=flag.getName();
		FlagDescriptor flag_d=(FlagDescriptor)cd.getFlagTable().get(name);
		//Make sure the flag is declared
		if (flag_d==null)
		    throw new Error("Flag descriptor "+name+" undefined in class: "+cd.getSymbol());
		flag.setFlag(flag_d);
	    }
	}
    }

    public void checkTask(TaskDescriptor td) {
	for(int i=0;i<td.numParameters();i++) {
	    /* Check that parameter is well typed */
	    TypeDescriptor param_type=td.getParamType(i);
	    checkTypeDescriptor(param_type);

	    /* Check the parameter's flag expression is well formed */
	    FlagExpressionNode fen=td.getFlag(td.getParameter(i));
	    if (!param_type.isClass())
		throw new Error("Cannot have non-object argument to a task");
	    ClassDescriptor cd=param_type.getClassDesc();
	    checkFlagExpressionNode(cd, fen);
	    checkFlagEffects(td, td.getFlagEffects());

	    /* Check that the task code is valid */
	    BlockNode bn=state.getMethodBody(td);
	    checkBlockNode(td, td.getParameterTable(),bn);
	}
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

	for(int i=0;i<md.numParameters();i++) {
	    TypeDescriptor param_type=md.getParamType(i);
	    checkTypeDescriptor(param_type);
	}
	/* Link the naming environments */
	if (!md.isStatic()) /* Fields aren't accessible directly in a static method, so don't link in this table */
	    md.getParameterTable().setParent(cd.getFieldTable());
	md.setClassDesc(cd);
	if (!md.isStatic()) {
	    VarDescriptor thisvd=new VarDescriptor(new TypeDescriptor(cd),"this");
	    md.setThis(thisvd);
	}
    }

    public void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
	System.out.println("Processing method:"+md);
	BlockNode bn=state.getMethodBody(md);
	checkBlockNode(md, md.getParameterTable(),bn);
    }
    
    public void checkBlockNode(Descriptor md, SymbolTable nametable, BlockNode bn) {
	/* Link in the naming environment */
	bn.getVarTable().setParent(nametable);
	for(int i=0;i<bn.size();i++) {
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
	    throw new Error(vd.getSymbol()+" defined a second time");
	if (dn.getExpression()!=null)
	    checkExpressionNode(md, nametable, dn.getExpression(), vd.getType());
    }
    
    void checkSubBlockNode(Descriptor md, SymbolTable nametable, SubBlockNode sbn) {
	checkBlockNode(md, nametable, sbn.getBlockNode());
    }

    void checkReturnNode(Descriptor d, SymbolTable nametable, ReturnNode rn) {
	if (d instanceof TaskDescriptor)
	    throw new Error("Illegal return appears in Task: "+d.getSymbol());
	MethodDescriptor md=(MethodDescriptor)d;
	if (rn.getReturnExpression()!=null)
	    checkExpressionNode(md, nametable, rn.getReturnExpression(), md.getReturnType());
	else
	    if (md.getReturnType()!=null&&!md.getReturnType().isVoid())
		throw new Error("Need to return something for "+md);
    }

    void checkTaskExitNode(Descriptor md, SymbolTable nametable, TaskExitNode ten) {
	if (md instanceof MethodDescriptor)
	    throw new Error("Illegal taskexit appears in Method: "+md.getSymbol());
	checkFlagEffects((TaskDescriptor)md, ten.getFlagEffects());
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
        }
	throw new Error();
    }

    void checkCastNode(Descriptor md, SymbolTable nametable, CastNode cn, TypeDescriptor td) {
	/* Get type descriptor */
	if (cn.getType()==null) {
	    NameDescriptor typenamed=cn.getTypeName().getName();
	    String typename=typenamed.toString();
	    TypeDescriptor ntd=new TypeDescriptor(typeutil.getClass(typename));
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
	if (typeutil.isSuperorType(cast_type,etd)) /* Cast trivially succeeds */
	    return;

	if (typeutil.isSuperorType(etd,cast_type)) /* Cast may succeed */
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
	    throw new Error("Unknown field "+fieldname);
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
	    ln.setType(new TypeDescriptor(typeutil.getClass(TypeUtil.StringClass)));
      	}

	if (td!=null)
	    if (!typeutil.isSuperorType(td,ln.getType()))
		throw new Error("Field node returns "+ln.getType()+", but need "+td);
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
		throw new Error("Name "+varname+" undefined");
	    }
	    if (d instanceof VarDescriptor) {
		nn.setVar((VarDescriptor)d);
	    } else if (d instanceof FieldDescriptor) {
		nn.setField((FieldDescriptor)d);
		nn.setVar((VarDescriptor)nametable.get("this")); /* Need a pointer to this */
	    }
	    if (td!=null)
		if (!typeutil.isSuperorType(td,nn.getType()))
		    throw new Error("Field node returns "+nn.getType()+", but need "+td);
	}
    }

    void checkAssignmentNode(Descriptor md, SymbolTable nametable, AssignmentNode an, TypeDescriptor td) {
	checkExpressionNode(md, nametable, an.getSrc() ,td);
	//TODO: Need check on validity of operation here
	if (!((an.getDest() instanceof FieldAccessNode)||
	      (an.getDest() instanceof ArrayAccessNode)||
	      (an.getDest() instanceof NameNode)))
	    throw new Error("Bad lside in "+an.printNode(0));
	checkExpressionNode(md, nametable, an.getDest(), null);
	if (!typeutil.isSuperorType(an.getDest().getType(),an.getSrc().getType())) {
	    throw new Error("Type of rside ("+an.getSrc().getType()+") not compatible with type of lside ("+an.getDest().getType()+")"+an.printNode(0));
	}
    }

    void checkLoopNode(Descriptor md, SymbolTable nametable, LoopNode ln) {
	if (ln.getType()==LoopNode.WHILELOOP||ln.getType()==LoopNode.DOWHILELOOP) {
	    checkExpressionNode(md, nametable, ln.getCondition(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
	    checkBlockNode(md, nametable, ln.getBody());
	} else {
	    //For loop case
	    /* Link in the initializer naming environment */
	    BlockNode bn=ln.getInitializer();
	    bn.getVarTable().setParent(nametable);
	    for(int i=0;i<bn.size();i++) {
		BlockStatementNode bsn=bn.get(i);
		checkBlockStatementNode(md, bn.getVarTable(),bsn);
	    }
	    //check the condition
	    checkExpressionNode(md, bn.getVarTable(), ln.getCondition(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
	    checkBlockNode(md, bn.getVarTable(), ln.getBody());
	    checkBlockNode(md, bn.getVarTable(), ln.getUpdate());
	}
    }


    void checkCreateObjectNode(Descriptor md, SymbolTable nametable, CreateObjectNode con, TypeDescriptor td) {
	TypeDescriptor[] tdarray=new TypeDescriptor[con.numArgs()];
	for(int i=0;i<con.numArgs();i++) {
	    ExpressionNode en=con.getArg(i);
	    checkExpressionNode(md,nametable,en,null);
	    tdarray[i]=en.getType();
	}

	TypeDescriptor typetolookin=con.getType();
	checkTypeDescriptor(typetolookin);
	if ((!typetolookin.isClass())&&(!typetolookin.isArray())) 
	    throw new Error("Can't allocate primitive type:"+con.printNode(0));

	if (!typetolookin.isArray()) {
	    //Array's don't need constructor calls
	    ClassDescriptor classtolookin=typetolookin.getClassDesc();
	    System.out.println("Looking for "+typetolookin.getSymbol());
	    System.out.println(classtolookin.getMethodTable());
	    
	    Set methoddescriptorset=classtolookin.getMethodTable().getSet(typetolookin.getSymbol());
	    MethodDescriptor bestmd=null;
	NextMethod:
	    for(Iterator methodit=methoddescriptorset.iterator();methodit.hasNext();) {
		MethodDescriptor currmd=(MethodDescriptor)methodit.next();
		/* Need correct number of parameters */
		System.out.println("Examining: "+currmd);
		if (con.numArgs()!=currmd.numParameters())
		    continue;
		for(int i=0;i<con.numArgs();i++) {
		    if (!typeutil.isSuperorType(currmd.getParamType(i),tdarray[i]))
			continue NextMethod;
		}
		/* Method okay so far */
		if (bestmd==null)
		    bestmd=currmd;
		else {
		    if (isMoreSpecific(currmd,bestmd)) {
			bestmd=currmd;
		    } else if (!isMoreSpecific(bestmd, currmd))
			throw new Error("No method is most specific");
		    
		    /* Is this more specific than bestmd */
		}
	    }
	    if (bestmd==null)
		throw new Error("No method found for "+con.printNode(0));
	    con.setConstructor(bestmd);
	}
    }


    /** Check to see if md1 is more specific than md2...  Informally
	if md2 could always be called given the arguments passed into
	md1 */

    boolean isMoreSpecific(MethodDescriptor md1, MethodDescriptor md2) {
	/* Checks if md1 is more specific than md2 */
	if (md1.numParameters()!=md2.numParameters())
	    throw new Error();
	for(int i=0;i<md1.numParameters();i++) {
	    if (!typeutil.isSuperorType(md2.getParamType(i), md1.getParamType(i)))
		return false;
	}
	if (!typeutil.isSuperorType(md2.getReturnType(), md1.getReturnType()))
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
	for(int i=0;i<min.numArgs();i++) {
	    ExpressionNode en=min.getArg(i);
	    checkExpressionNode(md,nametable,en,null);
	    tdarray[i]=en.getType();
	}
	TypeDescriptor typetolookin=null;
	if (min.getExpression()!=null) {
	    checkExpressionNode(md,nametable,min.getExpression(),null);
	    typetolookin=min.getExpression().getType();
	} else if (min.getBaseName()!=null) {
	    String rootname=min.getBaseName().getRoot();
	    if (nametable.get(rootname)!=null) {
		//we have an expression
		min.setExpression(translateNameDescriptorintoExpression(min.getBaseName()));
		checkExpressionNode(md, nametable, min.getExpression(), null);
		typetolookin=min.getExpression().getType();
	    } else {
		//we have a type
		ClassDescriptor cd=typeutil.getClass(min.getBaseName().getSymbol());
		if (cd==null)
		    throw new Error(min.getBaseName()+" undefined");
		typetolookin=new TypeDescriptor(cd);
	    }
	} else if (md instanceof MethodDescriptor) {
	    typetolookin=new TypeDescriptor(((MethodDescriptor)md).getClassDesc());
	} else {
	    /* If this a task descriptor we throw an error at this point */
	    throw new Error("Unknown method call to "+min.getMethodName()+"in task"+md.getSymbol());
	}
	if (!typetolookin.isClass()) 
	    throw new Error();
	ClassDescriptor classtolookin=typetolookin.getClassDesc();
	System.out.println("Method name="+min.getMethodName());
	Set methoddescriptorset=classtolookin.getMethodTable().getSet(min.getMethodName());
	MethodDescriptor bestmd=null;
	NextMethod:
	for(Iterator methodit=methoddescriptorset.iterator();methodit.hasNext();) {
	    MethodDescriptor currmd=(MethodDescriptor)methodit.next();
	    /* Need correct number of parameters */
	    if (min.numArgs()!=currmd.numParameters())
		continue;
	    for(int i=0;i<min.numArgs();i++) {
		if (!typeutil.isSuperorType(currmd.getParamType(i),tdarray[i]))
		    continue NextMethod;
	    }
	    /* Method okay so far */
	    if (bestmd==null)
		bestmd=currmd;
	    else {
		if (isMoreSpecific(currmd,bestmd)) {
		    bestmd=currmd;
		} else if (!isMoreSpecific(bestmd, currmd))
		    throw new Error("No method is most specific");
		
		/* Is this more specific than bestmd */
	    }
	}
	if (bestmd==null)
	    throw new Error("No method found for :"+min.printNode(0));
	min.setMethod(bestmd);
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
	TypeDescriptor rtd=on.getRight()!=null?on.getRight().getType():null;
	TypeDescriptor lefttype=null;
	TypeDescriptor righttype=null;
	Operation op=on.getOp();

	switch(op.getOp()) {
	case Operation.LOGIC_OR:
	case Operation.LOGIC_AND:
	    if (!(ltd.isBoolean()&&rtd.isBoolean()))
		throw new Error();
	    //no promotion
	    on.setLeftType(ltd);
	    on.setRightType(rtd);
	    on.setType(new TypeDescriptor(TypeDescriptor.BOOLEAN));
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
	    else 
		lefttype=new TypeDescriptor(TypeDescriptor.INT);
	    righttype=lefttype;

	    on.setLeftType(lefttype);
	    on.setRightType(righttype);
	    on.setType(lefttype);
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
	    //TODO: Need special case for strings eventually
	    
	    
	case Operation.SUB:
	case Operation.MULT:
	case Operation.DIV:
	case Operation.MOD:
	    // 5.6.2 Binary Numeric Promotion
	    //TODO unboxing of reference objects
	    if (!ltd.isNumber()||!rtd.isNumber())
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
	case Operation.POSTINC:
	case Operation.POSTDEC:
	case Operation.PREINC:
	case Operation.PREDEC:
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
	    throw new Error();
	}

     

	if (td!=null)
	    if (!typeutil.isSuperorType(td, on.getType())) {
		System.out.println(td);
		System.out.println(on.getType());
		throw new Error("Type of rside not compatible with type of lside"+on.printNode(0));	
	    }
    }
}
