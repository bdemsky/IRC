package IR.Flat;
import IR.*;
import IR.Tree.*;
import java.util.*;

public class BuildFlat {
    State state;
    Hashtable temptovar;

    public BuildFlat(State st) {
	state=st;
	temptovar=new Hashtable();
    }

    public void buildFlat() {
	Iterator it=state.classset.iterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    flattenClass(cn);
	}
    }
    
    private void flattenClass(ClassDescriptor cn) {
	Iterator methodit=cn.getMethods();
	while(methodit.hasNext()) {
	    MethodDescriptor md=(MethodDescriptor)methodit.next();
	    BlockNode bn=state.getMethodBody(md);
	    FlatNode fn=flattenBlockNode(bn).getBegin();
	    FlatMethod fm=new FlatMethod(md, fn);
	    state.addFlatCode(md,fm);
	}
    }

    private NodePair flattenBlockNode(BlockNode bn) {
	FlatNode begin=null;
	FlatNode end=null;
	for(int i=0;i<bn.size();i++) {
	    NodePair np=flattenBlockStatementNode(bn.get(i));
	    FlatNode np_begin=np.getBegin();
	    FlatNode np_end=np.getEnd();
	    if (begin==null) {
		begin=np_begin;
	    }
	    if (end==null) {
		end=np_end;
	    } else {
		end.addNext(np_begin);
		end=np_end;
	    }
	}
	return new NodePair(begin,end);
    }

    private NodePair flattenBlockExpressionNode(BlockExpressionNode en) {
	TempDescriptor tmp=TempDescriptor.tempFactory("neverused");
	return flattenExpressionNode(en.getExpression(),tmp);
    }

    private NodePair flattenAssignmentNode(AssignmentNode an,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenCastNode(CastNode cn,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenCreateObjectNode(CreateObjectNode con,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenFieldAccessNode(FieldAccessNode fan,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenLiteralNode(LiteralNode ln,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenMethodInvokeNode(MethodInvokeNode min,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenNameNode(NameNode nn,TempDescriptor out_temp) {
	throw new Error();
    }

    private NodePair flattenOpNode(OpNode on,TempDescriptor out_temp) {
	TempDescriptor temp_left=TempDescriptor.tempFactory("leftop");
	TempDescriptor temp_right=TempDescriptor.tempFactory("rightop");
	NodePair left=flattenExpressionNode(on.getLeft(),temp_left);
	NodePair right;
	if (on.getRight()!=null)
	    right=flattenExpressionNode(on.getRight(),temp_right);
	else {
	    FlatNop nop=new FlatNop();
	    right=new NodePair(nop,nop);
	}
	Operation op=on.getOp();
	FlatOpNode fon=new FlatOpNode(out_temp,temp_left,temp_right,op);
	left.getEnd().addNext(right.getBegin());
	right.getEnd().addNext(fon);
	return new NodePair(left.getBegin(),fon);
    }

    private NodePair flattenExpressionNode(ExpressionNode en, TempDescriptor out_temp) {
	switch(en.kind()) {
	case Kind.AssignmentNode:
	    return flattenAssignmentNode((AssignmentNode)en,out_temp);
	case Kind.CastNode:
	    return flattenCastNode((CastNode)en,out_temp);
	case Kind.CreateObjectNode:
	    return flattenCreateObjectNode((CreateObjectNode)en,out_temp);
	case Kind.FieldAccessNode:
	    return flattenFieldAccessNode((FieldAccessNode)en,out_temp);
	case Kind.LiteralNode:
	    return flattenLiteralNode((LiteralNode)en,out_temp);
	case Kind.MethodInvokeNode:
	    return flattenMethodInvokeNode((MethodInvokeNode)en,out_temp);
	case Kind.NameNode:
	    return flattenNameNode((NameNode)en,out_temp);
	case Kind.OpNode:
	    return flattenOpNode((OpNode)en,out_temp);
	}
	throw new Error();
    }

    private NodePair flattenDeclarationNode(DeclarationNode dn) {
	VarDescriptor vd=dn.getVarDescriptor();
	TempDescriptor td=getTempforVar(vd);
	return flattenExpressionNode(dn.getExpression(),td);
    }
        
    private TempDescriptor getTempforVar(VarDescriptor vd) {
	if (temptovar.containsKey(vd))
	    return (TempDescriptor)temptovar.get(vd);
	else {
	    TempDescriptor td=TempDescriptor.tempFactory(vd.getName());
	    temptovar.put(vd,td);
	    return td;
	}
    }

    private NodePair flattenIfStatementNode(IfStatementNode isn) {
	TempDescriptor cond_temp=TempDescriptor.tempFactory("condition");
	NodePair cond=flattenExpressionNode(isn.getCondition(),cond_temp);
	FlatCondBranch fcb=new FlatCondBranch(cond_temp);
	NodePair true_np=flattenBlockNode(isn.getTrueBlock());
	NodePair false_np;
	FlatNop nopend=new FlatNop();

	if (isn.getFalseBlock()!=null)
	    false_np=flattenBlockNode(isn.getFalseBlock());
	else {
	    FlatNop nop=new FlatNop();
	    false_np=new NodePair(nop,nop);
	}

	cond.getEnd().addNext(fcb);
	fcb.addTrueNext(true_np.getBegin());
	fcb.addFalseNext(false_np.getBegin());
	true_np.getEnd().addNext(nopend);
	false_np.getEnd().addNext(nopend);
	return new NodePair(cond.getBegin(), nopend);
    }
	    
    private NodePair flattenLoopNode(LoopNode ln) {
	if (ln.getType()==LoopNode.FORLOOP) {
	    NodePair initializer=flattenBlockNode(ln.getInitializer());
	    TempDescriptor cond_temp=TempDescriptor.tempFactory("condition");
	    NodePair condition=flattenExpressionNode(ln.getCondition(),cond_temp);
	    NodePair update=flattenBlockNode(ln.getUpdate());
	    NodePair body=flattenBlockNode(ln.getBody());
	    FlatNode begin=initializer.getBegin();
	    FlatCondBranch fcb=new FlatCondBranch(cond_temp);
	    FlatNop nopend=new FlatNop();

	    initializer.getEnd().addNext(condition.getBegin());
	    body.getEnd().addNext(update.getBegin());
	    update.getEnd().addNext(condition.getBegin());
	    condition.getEnd().addNext(fcb);
	    fcb.addFalseNext(nopend);
	    fcb.addTrueNext(body.getBegin());
	    return new NodePair(begin,nopend);
	} else if (ln.getType()==LoopNode.WHILELOOP) {
	    TempDescriptor cond_temp=TempDescriptor.tempFactory("condition");
	    NodePair condition=flattenExpressionNode(ln.getCondition(),cond_temp);
	    NodePair body=flattenBlockNode(ln.getBody());
	    FlatNode begin=condition.getBegin();
	    FlatCondBranch fcb=new FlatCondBranch(cond_temp);
	    FlatNop nopend=new FlatNop();

	    body.getEnd().addNext(condition.getBegin());
	    condition.getEnd().addNext(fcb);
	    fcb.addFalseNext(nopend);
	    fcb.addTrueNext(body.getBegin());
	    return new NodePair(begin,nopend);
	} else if (ln.getType()==LoopNode.DOWHILELOOP) {
	    TempDescriptor cond_temp=TempDescriptor.tempFactory("condition");
	    NodePair condition=flattenExpressionNode(ln.getCondition(),cond_temp);
	    NodePair body=flattenBlockNode(ln.getBody());
	    FlatNode begin=body.getBegin();
	    FlatCondBranch fcb=new FlatCondBranch(cond_temp);
	    FlatNop nopend=new FlatNop();

	    body.getEnd().addNext(condition.getBegin());
	    condition.getEnd().addNext(fcb);
	    fcb.addFalseNext(nopend);
	    fcb.addTrueNext(body.getBegin());
	    return new NodePair(begin,nopend);
	} else throw new Error();
    }
	    
    private NodePair flattenReturnNode(ReturnNode rntree) {
	TempDescriptor retval=TempDescriptor.tempFactory("ret_value");
	NodePair cond=flattenExpressionNode(rntree.getReturnExpression(),retval);
	FlatReturnNode rnflat=new FlatReturnNode(retval);
	cond.getEnd().addNext(rnflat);
	return new NodePair(cond.getBegin(),rnflat);
    }
	    
    private NodePair flattenSubBlockNode(SubBlockNode sbn) {
	return flattenBlockNode(sbn.getBlockNode());
    }

    private NodePair flattenBlockStatementNode(BlockStatementNode bsn) {
	switch(bsn.kind()) {
	case Kind.BlockExpressionNode:
	    return flattenBlockExpressionNode((BlockExpressionNode)bsn);
	    
	case Kind.DeclarationNode:
	    return flattenDeclarationNode((DeclarationNode)bsn);
	    
	case Kind.IfStatementNode:
	    return flattenIfStatementNode((IfStatementNode)bsn);
	    
	case Kind.LoopNode:
	    return flattenLoopNode((LoopNode)bsn);
	    
	case Kind.ReturnNode:
	    return flattenReturnNode((IR.Tree.ReturnNode)bsn);
	    
	case Kind.SubBlockNode:
	    return flattenSubBlockNode((SubBlockNode)bsn);
	    
	}
    	throw new Error();
    }
}
