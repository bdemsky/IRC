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
	while(it.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)it.next();
	    System.out.println("Checking class: "+cd);
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
	if (!md.getReturnType().isVoid())
	    checkTypeDescriptor(md.getReturnType());
	for(int i=0;i<md.numParameters();i++) {
	    TypeDescriptor param_type=md.getParamType(i);
	    checkTypeDescriptor(param_type);
	}
	BlockNode bn=state.getMethodBody(md);
	/* Link the naming environments */
	md.getParameterTable().setParent(cd.getFieldTable());
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

    void checkAssignmentNode(MethodDescriptor md, SymbolTable nametable, AssignmentNode an, TypeDescriptor td) {
	
    }

    void checkCastNode(MethodDescriptor md, SymbolTable nametable, CastNode cn, TypeDescriptor td) {
    }

    void checkCreateObjectNode(MethodDescriptor md, SymbolTable nametable, CreateObjectNode con, TypeDescriptor td) {
    }

    void checkFieldAccessNode(MethodDescriptor md, SymbolTable nametable, FieldAccessNode fan, TypeDescriptor td) {
    }

    void checkLiteralNode(MethodDescriptor md, SymbolTable nametable, LiteralNode ln, TypeDescriptor td) {
	
    }

    void checkMethodInvokeNode(MethodDescriptor md, SymbolTable nametable, MethodInvokeNode min, TypeDescriptor td) {
    }

    void checkNameNode(MethodDescriptor md, SymbolTable nametable, NameNode nn, TypeDescriptor td) {
    }

    void checkOpNode(MethodDescriptor md, SymbolTable nametable, OpNode on,TypeDescriptor td) {
    }

    void checkLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln) {
    }
}
