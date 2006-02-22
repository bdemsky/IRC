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
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
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

    private NodePair flattenCastNode(CastNode cn,TempDescriptor out_temp) {
	TempDescriptor tmp=TempDescriptor.tempFactory("tocast");
	NodePair np=flattenExpressionNode(cn.getExpression(), tmp);
	FlatCastNode fcn=new FlatCastNode(cn.getType(), tmp, out_temp);
	np.getEnd().addNext(fcn);
	return new NodePair(np.getBegin(),fcn);
    }

    private NodePair flattenLiteralNode(LiteralNode ln,TempDescriptor out_temp) {
	FlatLiteralNode fln=new FlatLiteralNode(ln.getType(), ln.getValue(), out_temp);
	return new NodePair(fln,fln);
    }


    private NodePair flattenCreateObjectNode(CreateObjectNode con,TempDescriptor out_temp) {
	TypeDescriptor td=con.getType();
	FlatNew fn=new FlatNew(td, out_temp);
	TempDescriptor[] temps=new TempDescriptor[con.numArgs()];
	FlatNode last=fn;
	//Build arguments
	for(int i=0;i<con.numArgs();i++) {
	    ExpressionNode en=con.getArg(i);
	    TempDescriptor tmp=TempDescriptor.tempFactory("arg");
	    temps[i]=tmp;
	    NodePair np=flattenExpressionNode(en, tmp);
	    last.addNext(np.getBegin());
	    last=np.getEnd();
	}
	MethodDescriptor md=con.getConstructor();
	//Call to constructor
	FlatCall fc=new FlatCall(md, null, out_temp, temps);
	last.addNext(fc);
	return new NodePair(fn,fc);
    }

    private NodePair flattenMethodInvokeNode(MethodInvokeNode min,TempDescriptor out_temp) {
	TempDescriptor[] temps=new TempDescriptor[min.numArgs()];
	FlatNode first=null;
	FlatNode last=null;
	TempDescriptor thisarg=null;

	if (min.getExpression()==null) {
	    thisarg=TempDescriptor.tempFactory("thisarg");
	    NodePair np=flattenExpressionNode(min.getExpression(),temps[0]);
	    first=np.getBegin();
	    last=np.getEnd();
	}
	
	//Build arguments
	for(int i=0;i<min.numArgs();i++) {
	    ExpressionNode en=min.getArg(i);
	    TempDescriptor td=TempDescriptor.tempFactory("arg");
	    temps[i]=td;
	    NodePair np=flattenExpressionNode(en, td);
	    if (first==null)
		first=np.getBegin();
	    else 
		last.addNext(np.getBegin());
	    last=np.getEnd();
	}

	MethodDescriptor md=min.getMethod();
	
	//Call to constructor
	FlatCall fc=new FlatCall(md, out_temp, thisarg, temps);
	if (first==null) {
	    first=fc;
	} else
	    last.addNext(fc);
	return new NodePair(first,fc);
    }

    private NodePair flattenFieldAccessNode(FieldAccessNode fan,TempDescriptor out_temp) {
	TempDescriptor tmp=TempDescriptor.tempFactory("temp");
	NodePair npe=flattenExpressionNode(fan.getExpression(),tmp);
	FlatFieldNode fn=new FlatFieldNode(fan.getField(),tmp,out_temp);
	npe.getEnd().addNext(fn);
	return new NodePair(npe.getBegin(),fn);
    }

    private NodePair flattenAssignmentNode(AssignmentNode an,TempDescriptor out_temp) {
	// Two cases:
	// left side is variable
	// left side is field
	
	Operation base=an.getOperation().getBaseOp();
	TempDescriptor src_tmp=TempDescriptor.tempFactory("src");
	NodePair np_src=flattenExpressionNode(an.getSrc(),src_tmp);
	FlatNode last=np_src.getEnd();
	if (base!=null) {
	    TempDescriptor src_tmp2=TempDescriptor.tempFactory("tmp");
	    NodePair np_dst_init=flattenExpressionNode(an.getDest(),src_tmp2);
	    last.addNext(np_dst_init.getBegin());
	    TempDescriptor dst_tmp=TempDescriptor.tempFactory("dst_tmp");
	    FlatOpNode fon=new FlatOpNode(dst_tmp, src_tmp,src_tmp2, base);
	    np_dst_init.getEnd().addNext(fon);
	    last=fon;
	    src_tmp=dst_tmp;
	}
	
	if (an.getDest().kind()==Kind.FieldAccessNode) {
	    FieldAccessNode fan=(FieldAccessNode)an.getDest();


	    // Need to assign field
	} else if (an.getDest().kind()==Kind.NameNode) {
	    
	} 
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
