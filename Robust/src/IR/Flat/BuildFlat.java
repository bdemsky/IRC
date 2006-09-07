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

    public Hashtable getMap() {
	return temptovar;
    }

    public void buildFlat() {
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    flattenClass(cn);
	}
	
	Iterator task_it=state.getTaskSymbolTable().getDescriptorsIterator();
	while(task_it.hasNext()) {
	    TaskDescriptor td=(TaskDescriptor)task_it.next();
	    flattenTask(td);
	}
    }
    
    private void flattenTask(TaskDescriptor td) {
	BlockNode bn=state.getMethodBody(td);
	FlatNode fn=flattenBlockNode(bn).getBegin();
	FlatFlagActionNode ffan=new FlatFlagActionNode(false);
	ffan.addNext(fn);
	FlatMethod fm=new FlatMethod(td, ffan);

	for(int i=0;i<td.numParameters();i++) {
	    fm.addParameterTemp(getTempforVar(td.getParameter(i)));
	}

	/* Flatten Vector of Flag Effects */
	Vector flags=td.getFlagEffects();
	updateFlagActionNode(ffan,flags);

	state.addFlatCode(td,fm);
    }


    /* This method transforms a vector of FlagEffects into the FlatFlagActionNode */
    private void updateFlagActionNode(FlatFlagActionNode ffan, Vector flags) {
	if (flags==null) // Do nothing if the flag effects vector is empty
	    return;

	for(int i=0;i<flags.size();i++) {
	    FlagEffects fes=(FlagEffects)flags.get(i);
	    TempDescriptor flagtemp=getTempforVar(fes.getVar());
	    for(int j=0;j<fes.numEffects();j++) {
		FlagEffect fe=fes.getEffect(j);
		ffan.addFlagAction(flagtemp, fe.getFlag(), fe.getStatus());
	    }
	}
    }

    private void flattenClass(ClassDescriptor cn) {
	Iterator methodit=cn.getMethods();
	while(methodit.hasNext()) {
	    MethodDescriptor md=(MethodDescriptor)methodit.next();
	    BlockNode bn=state.getMethodBody(md);
	    FlatNode fn=flattenBlockNode(bn).getBegin();
	    FlatMethod fm=new FlatMethod(md, fn);
	    if (!md.isStatic())
		fm.addParameterTemp(getTempforVar(md.getThis()));
	    for(int i=0;i<md.numParameters();i++) {
		fm.addParameterTemp(getTempforVar(md.getParameter(i)));
	    }
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
	if (begin==null) {
	    end=begin=new FlatNop();
	}
    	return new NodePair(begin,end);
    }

    private NodePair flattenBlockExpressionNode(BlockExpressionNode en) {
	TempDescriptor tmp=TempDescriptor.tempFactory("neverused",en.getExpression().getType());
	return flattenExpressionNode(en.getExpression(),tmp);
    }

    private NodePair flattenCastNode(CastNode cn,TempDescriptor out_temp) {
	TempDescriptor tmp=TempDescriptor.tempFactory("tocast",cn.getExpression().getType());
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
	if (!td.isArray()) {
	    FlatNew fn=new FlatNew(td, out_temp);
	    TempDescriptor[] temps=new TempDescriptor[con.numArgs()];
	    FlatNode last=fn;

	    if (con.getFlagEffects()!=null) {
		FlatFlagActionNode ffan=new FlatFlagActionNode(false);
		FlagEffects fes=con.getFlagEffects();
		TempDescriptor flagtemp=out_temp;
		for(int j=0;j<fes.numEffects();j++) {
		    FlagEffect fe=fes.getEffect(j);
		    ffan.addFlagAction(flagtemp, fe.getFlag(), fe.getStatus());
		}
		last.addNext(ffan);
		last=ffan;
	    }
	    //Build arguments
	    for(int i=0;i<con.numArgs();i++) {
		ExpressionNode en=con.getArg(i);
		TempDescriptor tmp=TempDescriptor.tempFactory("arg",en.getType());
		temps[i]=tmp;
		NodePair np=flattenExpressionNode(en, tmp);
		last.addNext(np.getBegin());
		last=np.getEnd();
	    }
	    MethodDescriptor md=con.getConstructor();
	    //Call to constructor
	    FlatCall fc=new FlatCall(md, null, out_temp, temps);
	    last.addNext(fc);
	    last=fc;
	    return new NodePair(fn,last); 
	} else {
	    FlatNode first=null;
	    FlatNode last=null;
	    TempDescriptor[] temps=new TempDescriptor[con.numArgs()];
	    for (int i=0;i<con.numArgs();i++) {
		ExpressionNode en=con.getArg(i);
		TempDescriptor tmp=TempDescriptor.tempFactory("arg",en.getType());
		temps[i]=tmp;		
		NodePair np=flattenExpressionNode(en, tmp);
		if (first==null)
		    first=np.getBegin();
		else
		    last.addNext(np.getBegin());
		last=np.getEnd();
		
		TempDescriptor tmp2=(i==0)?
		    out_temp:
		TempDescriptor.tempFactory("arg",en.getType());
	    }
	    FlatNew fn=new FlatNew(td, out_temp, temps[0]);
	    last.addNext(fn);
	    if (temps.length>1) {
		NodePair np=generateNewArrayLoop(temps, td.dereference(), out_temp, 0);
		fn.addNext(np.getBegin());
		return new NodePair(first,np.getEnd()); 
	    } else
		return new NodePair(first, fn);
	}
    }

    private NodePair generateNewArrayLoop(TempDescriptor[] temparray, TypeDescriptor td, TempDescriptor tmp, int i) {
	TempDescriptor index=TempDescriptor.tempFactory("index",new TypeDescriptor(TypeDescriptor.INT));
	TempDescriptor tmpone=TempDescriptor.tempFactory("index",new TypeDescriptor(TypeDescriptor.INT));
	FlatNop fnop=new FlatNop();//last node

	//index=0
	FlatLiteralNode fln=new FlatLiteralNode(index.getType(),new Integer(0),index);
	//tmpone=1
	FlatLiteralNode fln2=new FlatLiteralNode(tmpone.getType(),new Integer(1),tmpone);

	TempDescriptor tmpbool=TempDescriptor.tempFactory("comp",new TypeDescriptor(TypeDescriptor.BOOLEAN));

	FlatOpNode fcomp=new FlatOpNode(tmpbool,index,temparray[i],new Operation(Operation.LT));
	FlatCondBranch fcb=new FlatCondBranch(tmpbool);
	//is index<temp[i]
	TempDescriptor new_tmp=TempDescriptor.tempFactory("tmp",td);
	FlatNew fn=new FlatNew(td, new_tmp, temparray[i+1]);
	FlatSetElementNode fsen=new FlatSetElementNode(tmp,index,new_tmp);
	// index=index+1
	FlatOpNode fon=new FlatOpNode(index,index,tmpone,new Operation(Operation.ADD));
	//jump out
	fln.addNext(fln2);
	fln2.addNext(fcomp);
	fcomp.addNext(fcb);
	fcb.addTrueNext(fn);
	fcb.addFalseNext(fnop);
	fn.addNext(fsen);
	//Recursive call here
	if ((i+2)<temparray.length) {
	    NodePair np2=generateNewArrayLoop(temparray, td.dereference(), new_tmp, i+1);
	    fsen.addNext(np2.getBegin());
	    np2.getEnd().addNext(fon);
	} else {
	    fsen.addNext(fon);
	}
	fon.addNext(fcomp);
	return new NodePair(fln, fnop);
    }

    private NodePair flattenMethodInvokeNode(MethodInvokeNode min,TempDescriptor out_temp) {
	TempDescriptor[] temps=new TempDescriptor[min.numArgs()];
	FlatNode first=null;
	FlatNode last=null;
	TempDescriptor thisarg=null;

	if (min.getExpression()!=null) {
	    thisarg=TempDescriptor.tempFactory("thisarg",min.getExpression().getType());
	    NodePair np=flattenExpressionNode(min.getExpression(),thisarg);
	    first=np.getBegin();
	    last=np.getEnd();
	}
	
	//Build arguments
	for(int i=0;i<min.numArgs();i++) {
	    ExpressionNode en=min.getArg(i);
	    TempDescriptor td=TempDescriptor.tempFactory("arg",en.getType());
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
	
	FlatCall fc;
	if(md.getReturnType()==null||md.getReturnType().isVoid())
	    fc=new FlatCall(md, null, thisarg, temps);
	else 
	    fc=new FlatCall(md, out_temp, thisarg, temps);
	if (first==null) {
	    first=fc;
	} else
	    last.addNext(fc);
	return new NodePair(first,fc);
    }

    private NodePair flattenFieldAccessNode(FieldAccessNode fan,TempDescriptor out_temp) {
	TempDescriptor tmp=TempDescriptor.tempFactory("temp",fan.getExpression().getType());
	NodePair npe=flattenExpressionNode(fan.getExpression(),tmp);
	FlatFieldNode fn=new FlatFieldNode(fan.getField(),tmp,out_temp);
	npe.getEnd().addNext(fn);
	return new NodePair(npe.getBegin(),fn);
    }

    private NodePair flattenArrayAccessNode(ArrayAccessNode aan,TempDescriptor out_temp) {
	TempDescriptor tmp=TempDescriptor.tempFactory("temp",aan.getExpression().getType());
	TempDescriptor tmpindex=TempDescriptor.tempFactory("temp",aan.getIndex().getType());
	NodePair npe=flattenExpressionNode(aan.getExpression(),tmp);
	NodePair npi=flattenExpressionNode(aan.getIndex(),tmpindex);
	FlatElementNode fn=new FlatElementNode(tmp,tmpindex,out_temp);
	npe.getEnd().addNext(npi.getBegin());
	npi.getEnd().addNext(fn);
	return new NodePair(npe.getBegin(),fn);
    }

    private NodePair flattenAssignmentNode(AssignmentNode an,TempDescriptor out_temp) {
	// Three cases:
	// left side is variable
	// left side is field
	// left side is array
	
	Operation base=an.getOperation().getBaseOp();
	boolean pre=base==null||(base.getOp()!=Operation.POSTINC&&base.getOp()!=Operation.POSTDEC);
	
	if (!pre) {
	    //rewrite the base operation
	    base=base.getOp()==Operation.POSTINC?new Operation(Operation.ADD):new Operation(Operation.SUB);
	}
	FlatNode first=null;
	FlatNode last=null;
	TempDescriptor src_tmp=an.getSrc()==null?TempDescriptor.tempFactory("srctmp",an.getDest().getType()):TempDescriptor.tempFactory("srctmp",an.getSrc().getType());

	//Get src value
	if (an.getSrc()!=null) {
	    NodePair np_src=flattenExpressionNode(an.getSrc(),src_tmp);
	    first=np_src.getBegin();
	    last=np_src.getEnd();
	} else if (!pre) {
	    FlatLiteralNode fln=new FlatLiteralNode(new TypeDescriptor(TypeDescriptor.INT) ,new Integer(1),src_tmp);
	    first=fln;
	    last=fln;
	}
	
	if (an.getDest().kind()==Kind.FieldAccessNode) {
	    //We are assigning an object field

	    FieldAccessNode fan=(FieldAccessNode)an.getDest();
	    ExpressionNode en=fan.getExpression();
	    TempDescriptor dst_tmp=TempDescriptor.tempFactory("dst",en.getType());
	    NodePair np_baseexp=flattenExpressionNode(en, dst_tmp);
	    if (first==null)
		first=np_baseexp.getBegin();
	    else
		last.addNext(np_baseexp.getBegin());
	    last=np_baseexp.getEnd();

	    //See if we need to perform an operation
	    if (base!=null) {
		//If it is a preinc we need to store the initial value
		TempDescriptor src_tmp2=pre?TempDescriptor.tempFactory("src",an.getDest().getType()):out_temp;
		TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3",an.getDest().getType());

		FlatFieldNode ffn=new FlatFieldNode(fan.getField(), dst_tmp, src_tmp2);
		last.addNext(ffn);
		last=ffn;
		FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
		src_tmp=tmp;
		last.addNext(fon);
		last=fon;
	    }

	    FlatSetFieldNode fsfn=new FlatSetFieldNode(dst_tmp, fan.getField(), src_tmp);
	    last.addNext(fsfn);
	    last=fsfn;
	    if (pre) {
		FlatOpNode fon2=new FlatOpNode(out_temp, src_tmp, null, new Operation(Operation.ASSIGN));
		fsfn.addNext(fon2);
		last=fon2;
	    }
	    return new NodePair(first, last);
	} else if (an.getDest().kind()==Kind.ArrayAccessNode) {
	    //We are assigning an array element


	    ArrayAccessNode aan=(ArrayAccessNode)an.getDest();
	    ExpressionNode en=aan.getExpression();
	    ExpressionNode enindex=aan.getIndex();
	    TempDescriptor dst_tmp=TempDescriptor.tempFactory("dst",en.getType());
	    TempDescriptor index_tmp=TempDescriptor.tempFactory("index",enindex.getType());
	    NodePair np_baseexp=flattenExpressionNode(en, dst_tmp);
	    NodePair np_indexexp=flattenExpressionNode(enindex, index_tmp);
	    if (first==null)
		first=np_baseexp.getBegin();
	    else
		last.addNext(np_baseexp.getBegin());
	    np_baseexp.getEnd().addNext(np_indexexp.getBegin());
	    last=np_indexexp.getEnd();

	    //See if we need to perform an operation
	    if (base!=null) {
		//If it is a preinc we need to store the initial value
		TempDescriptor src_tmp2=pre?TempDescriptor.tempFactory("src",an.getDest().getType()):out_temp;
		TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3",an.getDest().getType());

		FlatElementNode fen=new FlatElementNode(dst_tmp, index_tmp, src_tmp2);
		last.addNext(fen);
		last=fen;
		FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
		src_tmp=tmp;
		last.addNext(fon);
		last=fon;
	    }


	    FlatSetElementNode fsen=new FlatSetElementNode(dst_tmp, index_tmp, src_tmp);
	    last.addNext(fsen);
	    last=fsen;
	    if (pre) {
		FlatOpNode fon2=new FlatOpNode(out_temp, src_tmp, null, new Operation(Operation.ASSIGN));
		fsen.addNext(fon2);
		last=fon2;
	    }
	    return new NodePair(first, last);
	} else if (an.getDest().kind()==Kind.NameNode) {
	    //We could be assigning a field or variable
	    NameNode nn=(NameNode)an.getDest();
	    if (nn.getExpression()!=null) {
		//It is a field
		FieldAccessNode fan=(FieldAccessNode)nn.getExpression();
		ExpressionNode en=fan.getExpression();
		TempDescriptor dst_tmp=TempDescriptor.tempFactory("dst",en.getType());
		NodePair np_baseexp=flattenExpressionNode(en, dst_tmp);
		if (first==null)
		    first=np_baseexp.getBegin();
		else
		    last.addNext(np_baseexp.getBegin());
		last=np_baseexp.getEnd();

		//See if we need to perform an operation
		if (base!=null) {
		    //If it is a preinc we need to store the initial value
		    TempDescriptor src_tmp2=pre?TempDescriptor.tempFactory("src",an.getDest().getType()):out_temp;
		    TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3",an.getDest().getType());
		    
		    FlatFieldNode ffn=new FlatFieldNode(fan.getField(), dst_tmp, src_tmp2);
		    last.addNext(ffn);
		    last=ffn;
		    FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
		    src_tmp=tmp;
		    last.addNext(fon);
		    last=fon;
		}


		FlatSetFieldNode fsfn=new FlatSetFieldNode(dst_tmp, fan.getField(), src_tmp);
		last.addNext(fsfn);
		last=fsfn;
		if (pre) {
		    FlatOpNode fon2=new FlatOpNode(out_temp, src_tmp, null, new Operation(Operation.ASSIGN));
		    fsfn.addNext(fon2);
		    last=fon2;
		}
		return new NodePair(first, last);
	    } else {
		if (nn.getField()!=null) {
		    //It is a field
		    //Get src value

		    //See if we need to perform an operation
		    if (base!=null) {
			//If it is a preinc we need to store the initial value
			TempDescriptor src_tmp2=pre?TempDescriptor.tempFactory("src",an.getDest().getType()):out_temp;
			TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3",an.getDest().getType());
			
			FlatFieldNode ffn=new FlatFieldNode(nn.getField(), getTempforVar(nn.getVar()), src_tmp2);
			if (first==null)
			    first=ffn;
			else {
			    last.addNext(ffn);
			}
			last=ffn;
			FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
			src_tmp=tmp;
			last.addNext(fon);
			last=fon;
		    }		    

		    FlatSetFieldNode fsfn=new FlatSetFieldNode(getTempforVar(nn.getVar()), nn.getField(), src_tmp);
		    if (first==null) {
			first=fsfn;
		    } else {
			last.addNext(fsfn);
		    }
		    last=fsfn;
		    if (pre) {
			FlatOpNode fon2=new FlatOpNode(out_temp, src_tmp, null, new Operation(Operation.ASSIGN));
			fsfn.addNext(fon2);
			last=fon2;
		    }
		    return new NodePair(first, last);
		} else {
		    //It is a variable
		    //See if we need to perform an operation

		    if (base!=null) {
			//If it is a preinc we need to store the initial value
			TempDescriptor src_tmp2=getTempforVar(nn.getVar());
			TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3",an.getDest().getType());
			if (!pre) {
			    FlatOpNode fon=new FlatOpNode(out_temp, src_tmp2, null, new Operation(Operation.ASSIGN));
			    if (first==null)
				first=fon;
			    else
				last.addNext(fon);
			    last=fon;
			}

			FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
			if (first==null) 
			    first=fon;
			else 
			    last.addNext(fon);
			src_tmp=tmp;
			last=fon;
		    }

		    FlatOpNode fon=new FlatOpNode(getTempforVar(nn.getVar()), src_tmp, null, new Operation(Operation.ASSIGN));
		    last.addNext(fon);
		    last=fon;
		    if (pre) {
			FlatOpNode fon2=new FlatOpNode(out_temp, src_tmp, null, new Operation(Operation.ASSIGN));
			fon.addNext(fon2);
			last=fon2;
		    }
		    return new NodePair(first, last);
		}
	    }
	} 
	throw new Error();
    }

    private NodePair flattenNameNode(NameNode nn,TempDescriptor out_temp) {
	if (nn.getExpression()!=null) {
	    /* Hack - use subtree instead */
	    return flattenExpressionNode(nn.getExpression(),out_temp);
	} else if (nn.getField()!=null) {
	    TempDescriptor tmp=getTempforVar(nn.getVar());
	    FlatFieldNode ffn=new FlatFieldNode(nn.getField(), tmp, out_temp); 
	    return new NodePair(ffn,ffn);
	} else {
	    TempDescriptor tmp=getTempforVar(nn.getVar());
	    FlatOpNode fon=new FlatOpNode(out_temp, tmp, null, new Operation(Operation.ASSIGN));
	    return new NodePair(fon,fon);
	}
    }

    private NodePair flattenOpNode(OpNode on,TempDescriptor out_temp) {
	TempDescriptor temp_left=TempDescriptor.tempFactory("leftop",on.getLeft().getType());
	TempDescriptor temp_right=null;

	Operation op=on.getOp();
	/* We've moved this to assignment nodes

	if (op.getOp()==Operation.POSTINC||
	    op.getOp()==Operation.POSTDEC||
	    op.getOp()==Operation.PREINC||
	    op.getOp()==Operation.PREDEC) {
	    LiteralNode ln=new LiteralNode("int",new Integer(1));
	    ln.setType(new TypeDescriptor(TypeDescriptor.INT));
	    
	    AssignmentNode an=new AssignmentNode(on.getLeft(),
						 new OpNode(on.getLeft(),ln, 
							    new Operation((op.getOp()==Operation.POSTINC||op.getOp()==Operation.PREINC)?Operation.PLUS:Operation.MINUS))
						 );
	    if (op.getOp()==Operation.POSTINC||
		op.getOp()==Operation.POSTDEC) {
		//Can't do, this could have side effects
		NodePair left=flattenExpressionNode(on.getLeft(),out_temp);
		NodePair assign=flattenAssignmentNode(an,temp_left);
		left.getEnd().addNext(assign.getBegin());
		return new NodePair(left.getBegin(),assign.getEnd());
	    } else {
		NodePair assign=flattenAssignmentNode(an,out_temp);
		return assign;
	    }
	    } */
	
	NodePair left=flattenExpressionNode(on.getLeft(),temp_left);
	NodePair right;
	if (on.getRight()!=null) {
	    temp_right=TempDescriptor.tempFactory("rightop",on.getRight().getType());
	    right=flattenExpressionNode(on.getRight(),temp_right);
	} else {
	    FlatNop nop=new FlatNop();
	    right=new NodePair(nop,nop);
	}

	if (op.getOp()==Operation.LOGIC_OR) {
	    /* Need to do shortcircuiting */
	    FlatCondBranch fcb=new FlatCondBranch(temp_left);
	    FlatOpNode fon1=new FlatOpNode(out_temp,temp_left,null,new Operation(Operation.ASSIGN));
	    FlatOpNode fon2=new FlatOpNode(out_temp,temp_right,null,new Operation(Operation.ASSIGN));
	    FlatNop fnop=new FlatNop();
	    left.getEnd().addNext(fcb);
	    fcb.addFalseNext(right.getBegin());
	    right.getEnd().addNext(fon2);
	    fon2.addNext(fnop);
	    fcb.addTrueNext(fon1);
	    fon1.addNext(fnop);
 	    return new NodePair(left.getBegin(), fnop);
	} else if (op.getOp()==Operation.LOGIC_AND) {
	    /* Need to do shortcircuiting */
	    FlatCondBranch fcb=new FlatCondBranch(temp_left);
	    FlatOpNode fon1=new FlatOpNode(out_temp,temp_left,null,new Operation(Operation.ASSIGN));
	    FlatOpNode fon2=new FlatOpNode(out_temp,temp_right,null,new Operation(Operation.ASSIGN));
	    FlatNop fnop=new FlatNop();
	    left.getEnd().addNext(fcb);
	    fcb.addTrueNext(right.getBegin());
	    right.getEnd().addNext(fon2);
	    fon2.addNext(fnop);
	    fcb.addFalseNext(fon1);
	    fon1.addNext(fnop);
 	    return new NodePair(left.getBegin(), fnop);
	}

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
	case Kind.ArrayAccessNode:
	    return flattenArrayAccessNode((ArrayAccessNode)en,out_temp);
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
	if (dn.getExpression()!=null)
	    return flattenExpressionNode(dn.getExpression(),td);
	else {
	    FlatNop fn=new FlatNop();
	    return new NodePair(fn,fn);
	}
    }
        
    private TempDescriptor getTempforVar(VarDescriptor vd) {
	if (temptovar.containsKey(vd))
	    return (TempDescriptor)temptovar.get(vd);
	else {
	    TempDescriptor td=TempDescriptor.tempFactory(vd.getName(),vd.getType());
	    temptovar.put(vd,td);
	    return td;
	}
    }

    private NodePair flattenIfStatementNode(IfStatementNode isn) {
	TempDescriptor cond_temp=TempDescriptor.tempFactory("condition",new TypeDescriptor(TypeDescriptor.BOOLEAN));
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
	    TempDescriptor cond_temp=TempDescriptor.tempFactory("condition", new TypeDescriptor(TypeDescriptor.BOOLEAN));
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
	    TempDescriptor cond_temp=TempDescriptor.tempFactory("condition", new TypeDescriptor(TypeDescriptor.BOOLEAN));
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
	    TempDescriptor cond_temp=TempDescriptor.tempFactory("condition", new TypeDescriptor(TypeDescriptor.BOOLEAN));
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
	TempDescriptor retval=null;
	NodePair cond=null;
	if (rntree.getReturnExpression()!=null) {
	    retval=TempDescriptor.tempFactory("ret_value", rntree.getReturnExpression().getType());
	    cond=flattenExpressionNode(rntree.getReturnExpression(),retval);
	}

	FlatReturnNode rnflat=new FlatReturnNode(retval);

	if (cond!=null) {
	    cond.getEnd().addNext(rnflat);
	    return new NodePair(cond.getBegin(),rnflat);
	} else
	    return new NodePair(rnflat,rnflat);
    }

    private NodePair flattenTaskExitNode(TaskExitNode ten) {
	FlatFlagActionNode ffan=new FlatFlagActionNode(true);
	updateFlagActionNode(ffan, ten.getFlagEffects());
	NodePair fcn=flattenConstraintCheck(ten.getChecks());
	ffan.addNext(fcn.getBegin());
	return new NodePair(ffan, fcn.getEnd());
    }

    private NodePair flattenConstraintCheck(Vector ccs) {
	FlatNode begin=new FlatNop();
	if (ccs==null)
	    return new NodePair(begin,begin);
	FlatNode last=begin;
	for(int i=0;i<ccs.size();i++) {
	    ConstraintCheck cc=(ConstraintCheck) ccs.get(i);
	    /* Flatten the arguments */
	    TempDescriptor[] temps=new TempDescriptor[cc.numArgs()];
	    String[] vars=new String[cc.numArgs()];
	    for(int j=0;j<cc.numArgs();j++) {
		ExpressionNode en=cc.getArg(j);
		TempDescriptor td=TempDescriptor.tempFactory("arg",en.getType());
		temps[j]=td;
		vars[j]=cc.getVar(j);
		NodePair np=flattenExpressionNode(en, td);
		last.addNext(np.getBegin());
		last=np.getEnd();
	    }
	    
	    FlatCheckNode fcn=new FlatCheckNode(cc.getSpec(), vars, temps);
	    last.addNext(fcn);
	    last=fcn;
	}
	return new NodePair(begin,last);
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

	case Kind.TaskExitNode:
	    return flattenTaskExitNode((IR.Tree.TaskExitNode)bsn);
	    
	case Kind.SubBlockNode:
	    return flattenSubBlockNode((SubBlockNode)bsn);
	    
	}
    	throw new Error();
    }
}
