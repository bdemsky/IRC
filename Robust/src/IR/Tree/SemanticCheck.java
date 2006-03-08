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
		// Link together Field and Method tables
		cd.getFieldTable().setParent(cd.getSuperDesc().getFieldTable());
		cd.getMethodTable().setParent(cd.getSuperDesc().getMethodTable());
	    }
	    
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
	md.getParameterTable().setParent(cd.getFieldTable());
	md.setClassDesc(cd);
	if (!md.isStatic()) {
	    VarDescriptor thisvd=new VarDescriptor(new TypeDescriptor(cd),"this");
	    md.setThis(thisvd);
	}
    }

    public void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
	BlockNode bn=state.getMethodBody(md);
	checkBlockNode(md, md.getParameterTable(),bn);
    }
    
    public void checkBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {
	/* Link in the naming environment */
	bn.getVarTable().setParent(nametable);
	for(int i=0;i<bn.size();i++) {
	    BlockStatementNode bsn=bn.get(i);
	    checkBlockStatementNode(md, bn.getVarTable(),bsn);
	}
    }
    
    public void checkBlockStatementNode(MethodDescriptor md, SymbolTable nametable, BlockStatementNode bsn) {
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

	case Kind.SubBlockNode:
	    checkSubBlockNode(md, nametable, (SubBlockNode)bsn);
	    return;
	}
	throw new Error();
    }

    void checkBlockExpressionNode(MethodDescriptor md, SymbolTable nametable, BlockExpressionNode ben) {
	checkExpressionNode(md, nametable, ben.getExpression(), null);
    }

    void checkDeclarationNode(MethodDescriptor md, SymbolTable nametable,  DeclarationNode dn) {
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
    
    void checkSubBlockNode(MethodDescriptor md, SymbolTable nametable, SubBlockNode sbn) {
	checkBlockNode(md, nametable, sbn.getBlockNode());
    }

    void checkReturnNode(MethodDescriptor md, SymbolTable nametable, ReturnNode rn) {
	checkExpressionNode(md, nametable, rn.getReturnExpression(), md.getReturnType());
    }

    void checkIfStatementNode(MethodDescriptor md, SymbolTable nametable, IfStatementNode isn) {
	checkExpressionNode(md, nametable, isn.getCondition(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
	checkBlockNode(md, nametable, isn.getTrueBlock());
	checkBlockNode(md, nametable, isn.getFalseBlock());
    }
    
    void checkExpressionNode(MethodDescriptor md, SymbolTable nametable, ExpressionNode en, TypeDescriptor td) {
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

    void checkCastNode(MethodDescriptor md, SymbolTable nametable, CastNode cn, TypeDescriptor td) {
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

    void checkFieldAccessNode(MethodDescriptor md, SymbolTable nametable, FieldAccessNode fan, TypeDescriptor td) {
	ExpressionNode left=fan.getExpression();
	checkExpressionNode(md,nametable,left,null);
	TypeDescriptor ltd=left.getType();
	String fieldname=fan.getFieldName();
	FieldDescriptor fd=(FieldDescriptor) ltd.getClassDesc().getFieldTable().get(fieldname);
	if (fd==null)
	    throw new Error("Unknown field "+fieldname);
	fan.setField(fd);
	if (td!=null)
	    if (!typeutil.isSuperorType(td,fan.getType()))
		throw new Error("Field node returns "+fan.getType()+", but need "+td);
    }

    void checkLiteralNode(MethodDescriptor md, SymbolTable nametable, LiteralNode ln, TypeDescriptor td) {
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

    void checkNameNode(MethodDescriptor md, SymbolTable nametable, NameNode nn, TypeDescriptor td) {
	NameDescriptor nd=nn.getName();
	String varname=nd.toString();
	Descriptor d=(Descriptor)nametable.get(varname);
	if (d==null) {
	    throw new Error("Name "+varname+" undefined");
	}
	if (d instanceof VarDescriptor) {
	    nn.setVar((VarDescriptor)d);
	} else if (d instanceof FieldDescriptor) {
	    nn.setField((FieldDescriptor)d);
	}
	if (td!=null)
	    if (!typeutil.isSuperorType(td,nn.getType()))
		throw new Error("Field node returns "+nn.getType()+", but need "+td);
    }

    void checkAssignmentNode(MethodDescriptor md, SymbolTable nametable, AssignmentNode an, TypeDescriptor td) {
	checkExpressionNode(md, nametable, an.getSrc() ,td);
	//TODO: Need check on validity of operation here
	if (!((an.getDest() instanceof FieldAccessNode)||
	      (an.getDest() instanceof NameNode)))
	    throw new Error("Bad lside in "+an.printNode(0));
	checkExpressionNode(md, nametable, an.getDest(), null);
	if (!typeutil.isSuperorType(an.getDest().getType(),an.getSrc().getType()))
	    throw new Error("Type of rside not compatible with type of lside"+an.printNode(0));
    }

    void checkLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln) {
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


    void checkCreateObjectNode(MethodDescriptor md, SymbolTable nametable, CreateObjectNode con, TypeDescriptor td) {
	TypeDescriptor[] tdarray=new TypeDescriptor[con.numArgs()];
	for(int i=0;i<con.numArgs();i++) {
	    ExpressionNode en=con.getArg(i);
	    checkExpressionNode(md,nametable,en,null);
	    tdarray[i]=en.getType();
	}

	TypeDescriptor typetolookin=con.getType();
	checkTypeDescriptor(typetolookin);
	if (!typetolookin.isClass()) 
	    throw new Error();

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


    void checkMethodInvokeNode(MethodDescriptor md, SymbolTable nametable, MethodInvokeNode min, TypeDescriptor td) {
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
	} else {
	    typetolookin=new TypeDescriptor(md.getClassDesc());
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
    }


    void checkOpNode(MethodDescriptor md, SymbolTable nametable, OpNode on, TypeDescriptor td) {
	checkExpressionNode(md, nametable, on.getLeft(), null);
	if (on.getRight()!=null)
	    checkExpressionNode(md, nametable, on.getRight(), null);
	TypeDescriptor ltd=on.getLeft().getType();
	TypeDescriptor rtd=on.getRight()!=null?on.getRight().getType():null;
	TypeDescriptor thistype=null;
	if (rtd!=null) {
	    // 5.6.2 Binary Numeric Promotion
	    //TODO unboxing of reference objects
	    if (ltd.isDouble()||rtd.isDouble())
		thistype=new TypeDescriptor(TypeDescriptor.DOUBLE);
	    else if (ltd.isFloat()||rtd.isFloat())
		thistype=new TypeDescriptor(TypeDescriptor.FLOAT);
	    else if (ltd.isLong()||rtd.isLong())
		thistype=new TypeDescriptor(TypeDescriptor.LONG);
	    else 
		thistype=new TypeDescriptor(TypeDescriptor.INT);
	    
	} else {
	    //5.6.1 Unary Numeric Promotion
	    if (ltd.isByte()||ltd.isShort()||ltd.isInt())
		thistype=new TypeDescriptor(TypeDescriptor.INT);
	    else
		thistype=ltd;
	}
	on.setType(thistype);
	if (td!=null)
	    if (!typeutil.isSuperorType(td, thistype))
		throw new Error("Type of rside not compatible with type of lside"+on.printNode(0));	
    }


}
