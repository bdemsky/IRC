package IR.Flat;
import IR.*;
import IR.Tree.*;
import java.util.*;

public class BuildFlat {
  State state;
  Hashtable temptovar;
  MethodDescriptor currmd;
  TypeUtil typeutil;

  HashSet breakset;
  HashSet continueset;
  FlatExit fe;

  public BuildFlat(State st, TypeUtil typeutil) {
    state=st;
    temptovar=new Hashtable();
    this.typeutil=typeutil;
    this.breakset=new HashSet();
    this.continueset=new HashSet();
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
    NodePair np=flattenBlockNode(bn);
    FlatNode fn=np.getBegin();
    fe=new FlatExit();
    FlatNode fn2=np.getEnd();

    if (fn2!=null&& fn2.kind()!=FKind.FlatReturnNode) {
      FlatReturnNode rnflat=new FlatReturnNode(null);
      rnflat.addNext(fe);
      fn2.addNext(rnflat);
    }

    FlatFlagActionNode ffan=new FlatFlagActionNode(FlatFlagActionNode.PRE);
    ffan.addNext(fn);

    FlatMethod fm=new FlatMethod(td, fe);
    fm.addNext(ffan);

    Hashtable visitedset=new Hashtable();

    for(int i=0; i<td.numParameters(); i++) {
      VarDescriptor paramvd=td.getParameter(i);
      fm.addParameterTemp(getTempforVar(paramvd));
      TagExpressionList tel=td.getTag(paramvd);
      //BUG added next line to fix...to test feed in any task program
      if (tel!=null)
	for(int j=0; j<tel.numTags(); j++) {
	  TagVarDescriptor tvd=(TagVarDescriptor) td.getParameterTable().getFromSameScope(tel.getName(j));
	  TempDescriptor tagtmp=getTempforVar(tvd);
	  if (!visitedset.containsKey(tvd.getName())) {
	    visitedset.put(tvd.getName(),tvd.getTag());
	    fm.addTagTemp(tagtmp);
	  } else {
	    TagDescriptor tmptd=(TagDescriptor) visitedset.get(tvd.getName());
	    if (!tmptd.equals(tvd.getTag()))
	      throw new Error("Two different tag types with same name as parameters to:"+td);
	  }
	  tel.setTemp(j, tagtmp);
	}
    }

    /* Flatten Vector of Flag Effects */
    Vector flags=td.getFlagEffects();
    updateFlagActionNode(ffan,flags);

    state.addFlatCode(td,fm);
  }


  /* This method transforms a vector of FlagEffects into the FlatFlagActionNode */
  private void updateFlagActionNode(FlatFlagActionNode ffan, Vector flags) {
    if (flags==null)     // Do nothing if the flag effects vector is empty
      return;

    for(int i=0; i<flags.size(); i++) {
      FlagEffects fes=(FlagEffects)flags.get(i);
      TempDescriptor flagtemp=getTempforVar(fes.getVar());
      // Process the flags
      for(int j=0; j<fes.numEffects(); j++) {
	FlagEffect fe=fes.getEffect(j);
	ffan.addFlagAction(flagtemp, fe.getFlag(), fe.getStatus());
      }
      // Process the tags
      for(int j=0; j<fes.numTagEffects(); j++) {
	TagEffect te=fes.getTagEffect(j);
	TempDescriptor tagtemp=getTempforVar(te.getTag());

	ffan.addTagAction(flagtemp, te.getTag().getTag(), tagtemp, te.getStatus());
      }
    }
  }

  FlatAtomicEnterNode curran=null;

  private void flattenClass(ClassDescriptor cn) {
    Iterator methodit=cn.getMethods();
    while(methodit.hasNext()) {
      fe=new FlatExit();
      currmd=(MethodDescriptor)methodit.next();
      BlockNode bn=state.getMethodBody(currmd);

      if (state.DSM&&currmd.getModifiers().isAtomic()) {
	curran=new FlatAtomicEnterNode();
      } else
	curran=null;
      NodePair np=flattenBlockNode(bn);
      FlatNode fn=np.getBegin();
      if (state.THREAD&&currmd.getModifiers().isSynchronized()) {
	MethodDescriptor memd=(MethodDescriptor)typeutil.getClass("Object").getMethodTable().get("MonitorEnter");
	TempDescriptor thistd=getTempforVar(currmd.getThis());
	FlatCall fc=new FlatCall(memd, null, thistd, new TempDescriptor[0]);
	fc.addNext(fn);
	fn=fc;
	if (np.getEnd()!=null&&np.getEnd().kind()!=FKind.FlatReturnNode) {
	  MethodDescriptor memdex=(MethodDescriptor)typeutil.getClass("Object").getMethodTable().get("MonitorExit");
	  FlatCall fcunlock=new FlatCall(memdex, null, thistd, new TempDescriptor[0]);
	  np.getEnd().addNext(fcunlock);
	  FlatReturnNode rnflat=new FlatReturnNode(null);
	  fcunlock.addNext(rnflat);
	  rnflat.addNext(fe);
	}
      } else if (state.DSM&&currmd.getModifiers().isAtomic()) {
	curran.addNext(fn);
	fn=curran;
	if (np.getEnd()!=null&&np.getEnd().kind()!=FKind.FlatReturnNode) {
	  FlatAtomicExitNode aen=new FlatAtomicExitNode(curran);
	  np.getEnd().addNext(aen);
	  FlatReturnNode rnflat=new FlatReturnNode(null);
	  aen.addNext(rnflat);
	  rnflat.addNext(fe);
	}
      } else if (np.getEnd()!=null&&np.getEnd().kind()!=FKind.FlatReturnNode) {
	FlatReturnNode rnflat=new FlatReturnNode(null);
	np.getEnd().addNext(rnflat);
	rnflat.addNext(fe);
      }

      FlatMethod fm=new FlatMethod(currmd, fe);
      fm.addNext(fn);
      if (!currmd.isStatic())
	fm.addParameterTemp(getTempforParam(currmd.getThis()));
      for(int i=0; i<currmd.numParameters(); i++) {
	fm.addParameterTemp(getTempforParam(currmd.getParameter(i)));
      }
      state.addFlatCode(currmd,fm);
    }
  }

  private NodePair flattenBlockNode(BlockNode bn) {
    FlatNode begin=null;
    FlatNode end=null;
    for(int i=0; i<bn.size(); i++) {
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
	if (np_end==null) {
	    return new NodePair(begin, null);
	} else
	    end=np_end;
      }
    }
    if (begin==null) {
      end=begin=new FlatNop();
    }
    return new NodePair(begin,end);
  }

  private NodePair flattenBlockExpressionNode(BlockExpressionNode en) {
    //System.out.println("DEBUG -> inside flattenBlockExpressionNode\n");
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

  private NodePair flattenOffsetNode(OffsetNode ofn, TempDescriptor out_temp) {
    FlatOffsetNode fln = new FlatOffsetNode(ofn.getClassType(), ofn.getField(), out_temp);
    return new NodePair(fln, fln);
  }

  private NodePair flattenCreateObjectNode(CreateObjectNode con,TempDescriptor out_temp) {
    TypeDescriptor td=con.getType();
    if (!td.isArray()) {
      FlatNew fn=new FlatNew(td, out_temp, con.isGlobal(), con.getDisjointId());
      TempDescriptor[] temps=new TempDescriptor[con.numArgs()];
      FlatNode last=fn;
      // Build arguments
      for(int i=0; i<con.numArgs(); i++) {
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
      if (td.getClassDesc().hasFlags()) {
	//	    if (con.getFlagEffects()!=null) {
	FlatFlagActionNode ffan=new FlatFlagActionNode(FlatFlagActionNode.NEWOBJECT);
	FlagEffects fes=con.getFlagEffects();
	TempDescriptor flagtemp=out_temp;
	if (fes!=null) {
	  for(int j=0; j<fes.numEffects(); j++) {
	    FlagEffect fe=fes.getEffect(j);
	    ffan.addFlagAction(flagtemp, fe.getFlag(), fe.getStatus());
	  }
	  for(int j=0; j<fes.numTagEffects(); j++) {
	    TagEffect te=fes.getTagEffect(j);
	    TempDescriptor tagtemp=getTempforVar(te.getTag());

	    ffan.addTagAction(flagtemp, te.getTag().getTag(), tagtemp, te.getStatus());
	  }
	} else {
	  ffan.addFlagAction(flagtemp, null, false);
	}
	last.addNext(ffan);
	last=ffan;
      }
      return new NodePair(fn,last);
    } else {
      FlatNode first=null;
      FlatNode last=null;
      TempDescriptor[] temps=new TempDescriptor[con.numArgs()];
      for (int i=0; i<con.numArgs(); i++) {
	ExpressionNode en=con.getArg(i);
	TempDescriptor tmp=TempDescriptor.tempFactory("arg",en.getType());
	temps[i]=tmp;
	NodePair np=flattenExpressionNode(en, tmp);
	if (first==null)
	  first=np.getBegin();
	else
	  last.addNext(np.getBegin());
	last=np.getEnd();

	TempDescriptor tmp2=(i==0) ?
	                     out_temp :
	                     TempDescriptor.tempFactory("arg",en.getType());
      }
      FlatNew fn=new FlatNew(td, out_temp, temps[0], con.isGlobal(), con.getDisjointId());
      last.addNext(fn);
      if (temps.length>1) {
	NodePair np=generateNewArrayLoop(temps, td.dereference(), out_temp, 0, con.isGlobal());
	fn.addNext(np.getBegin());
	return new NodePair(first,np.getEnd());
      } else if (td.isArray()&&td.dereference().iswrapper()) {
	NodePair np=generateNewArrayLoop(temps, td.dereference(), out_temp, 0, con.isGlobal());
	fn.addNext(np.getBegin());
	return new NodePair(first,np.getEnd());
      } else
	return new NodePair(first, fn);
    }
  }

  private NodePair generateNewArrayLoop(TempDescriptor[] temparray, TypeDescriptor td, TempDescriptor tmp, int i, boolean isglobal) {
    TempDescriptor index=TempDescriptor.tempFactory("index",new TypeDescriptor(TypeDescriptor.INT));
    TempDescriptor tmpone=TempDescriptor.tempFactory("index",new TypeDescriptor(TypeDescriptor.INT));
    FlatNop fnop=new FlatNop();    //last node

    //index=0
    FlatLiteralNode fln=new FlatLiteralNode(index.getType(),new Integer(0),index);
    //tmpone=1
    FlatLiteralNode fln2=new FlatLiteralNode(tmpone.getType(),new Integer(1),tmpone);

    TempDescriptor tmpbool=TempDescriptor.tempFactory("comp",new TypeDescriptor(TypeDescriptor.BOOLEAN));

    FlatOpNode fcomp=new FlatOpNode(tmpbool,index,temparray[i],new Operation(Operation.LT));
    FlatCondBranch fcb=new FlatCondBranch(tmpbool);
    fcb.setTrueProb(State.TRUEPROB);
    fcb.setLoop();
    //is index<temp[i]
    TempDescriptor new_tmp=TempDescriptor.tempFactory("tmp",td);
    FlatNew fn=td.iswrapper()?new FlatNew(td, new_tmp, isglobal):new FlatNew(td, new_tmp, temparray[i+1], isglobal);
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
      NodePair np2=generateNewArrayLoop(temparray, td.dereference(), new_tmp, i+1, isglobal);
      fsen.addNext(np2.getBegin());
      np2.getEnd().addNext(fon);
    } else if (td.isArray()&&td.dereference().iswrapper()) {
      NodePair np2=generateNewArrayLoop(temparray, td.dereference(), new_tmp, i+1, isglobal);
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
    for(int i=0; i<min.numArgs(); i++) {
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
    TempDescriptor arraytmp=out_temp;
    if (aan.iswrapper()) {
      //have wrapper
      arraytmp=TempDescriptor.tempFactory("temp", aan.getExpression().getType().dereference());
    }
    FlatNode fn=new FlatElementNode(tmp,tmpindex,arraytmp);
    npe.getEnd().addNext(npi.getBegin());
    npi.getEnd().addNext(fn);
    if (aan.iswrapper()) {
      FlatFieldNode ffn=new FlatFieldNode((FieldDescriptor)aan.getExpression().getType().dereference().getClassDesc().getFieldTable().get("value") ,arraytmp,out_temp);
      fn.addNext(ffn);
      fn=ffn;
    }
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
      base=base.getOp()==Operation.POSTINC ? new Operation(Operation.ADD) : new Operation(Operation.SUB);
    }
    FlatNode first=null;
    FlatNode last=null;
    TempDescriptor src_tmp = src_tmp=an.getSrc()==null ? TempDescriptor.tempFactory("srctmp",an.getDest().getType()) : TempDescriptor.tempFactory("srctmp",an.getSrc().getType());

    //Get src value
    if (an.getSrc()!=null) {
      NodePair np_src=flattenExpressionNode(an.getSrc(),src_tmp);
      first=np_src.getBegin();
      last=np_src.getEnd();
    } else if (!pre) {
      FlatLiteralNode fln=new FlatLiteralNode(new TypeDescriptor(TypeDescriptor.INT),new Integer(1),src_tmp);
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
	TempDescriptor src_tmp2=pre ? TempDescriptor.tempFactory("src",an.getDest().getType()) : out_temp;
	TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3_",an.getDest().getType());
	FlatFieldNode ffn=new FlatFieldNode(fan.getField(), dst_tmp, src_tmp2);
	last.addNext(ffn);
	last=ffn;

	if (base.getOp()==Operation.ADD&&an.getDest().getType().isString()) {
	  ClassDescriptor stringcd=typeutil.getClass(TypeUtil.StringClass);
	  MethodDescriptor concatmd=typeutil.getMethod(stringcd, "concat2", new TypeDescriptor[] {new TypeDescriptor(stringcd), new TypeDescriptor(stringcd)});
	  FlatCall fc=new FlatCall(concatmd, tmp, null, new TempDescriptor[] {src_tmp2, src_tmp});
	  src_tmp=tmp;
	  last.addNext(fc);
	  last=fc;
	} else {
	  FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
	  src_tmp=tmp;
	  last.addNext(fon);
	  last=fon;
	}
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
	TempDescriptor src_tmp2=pre ? TempDescriptor.tempFactory("src",an.getDest().getType()) : out_temp;
	TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3_",an.getDest().getType());

	if (aan.iswrapper()) {
	  TypeDescriptor arrayeltype=aan.getExpression().getType().dereference();
	  TempDescriptor src_tmp3=TempDescriptor.tempFactory("src3",arrayeltype);
	  FlatElementNode fen=new FlatElementNode(dst_tmp, index_tmp, src_tmp3);
	  FlatFieldNode ffn=new FlatFieldNode((FieldDescriptor)arrayeltype.getClassDesc().getFieldTable().get("value"),src_tmp3,src_tmp2);
	  last.addNext(fen);
	  fen.addNext(ffn);
	  last=ffn;
	} else {
	  FlatElementNode fen=new FlatElementNode(dst_tmp, index_tmp, src_tmp2);
	  last.addNext(fen);
	  last=fen;
	}
	if (base.getOp()==Operation.ADD&&an.getDest().getType().isString()) {
	  ClassDescriptor stringcd=typeutil.getClass(TypeUtil.StringClass);
	  MethodDescriptor concatmd=typeutil.getMethod(stringcd, "concat2", new TypeDescriptor[] {new TypeDescriptor(stringcd), new TypeDescriptor(stringcd)});
	  FlatCall fc=new FlatCall(concatmd, tmp, null, new TempDescriptor[] {src_tmp2, src_tmp});
	  src_tmp=tmp;
	  last.addNext(fc);
	  last=fc;
	} else {
	  FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
	  src_tmp=tmp;
	  last.addNext(fon);
	  last=fon;
	}
      }

      if (aan.iswrapper()) { 
	TypeDescriptor arrayeltype=aan.getExpression().getType().dereference();
	TempDescriptor src_tmp3=TempDescriptor.tempFactory("src3",arrayeltype);
	FlatElementNode fen=new FlatElementNode(dst_tmp, index_tmp, src_tmp3);
	FlatSetFieldNode fsfn=new FlatSetFieldNode(src_tmp3,(FieldDescriptor)arrayeltype.getClassDesc().getFieldTable().get("value"),src_tmp);
	last.addNext(fen);
	fen.addNext(fsfn);
	last=fsfn;
      } else { 
	FlatSetElementNode fsen=new FlatSetElementNode(dst_tmp, index_tmp, src_tmp);
	last.addNext(fsen);
	last=fsen;
      }
      if (pre) {
	FlatOpNode fon2=new FlatOpNode(out_temp, src_tmp, null, new Operation(Operation.ASSIGN));
	last.addNext(fon2);
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
	  TempDescriptor src_tmp2=pre ? TempDescriptor.tempFactory("src",an.getDest().getType()) : out_temp;
	  TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3_",an.getDest().getType());

	  FlatFieldNode ffn=new FlatFieldNode(fan.getField(), dst_tmp, src_tmp2);
	  last.addNext(ffn);
	  last=ffn;


	  if (base.getOp()==Operation.ADD&&an.getDest().getType().isString()) {
	    ClassDescriptor stringcd=typeutil.getClass(TypeUtil.StringClass);
	    MethodDescriptor concatmd=typeutil.getMethod(stringcd, "concat2", new TypeDescriptor[] {new TypeDescriptor(stringcd), new TypeDescriptor(stringcd)});
	    FlatCall fc=new FlatCall(concatmd, tmp, null, new TempDescriptor[] {src_tmp2, src_tmp});
	    src_tmp=tmp;
	    last.addNext(fc);
	    last=fc;
	  } else {
	    FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
	    src_tmp=tmp;
	    last.addNext(fon);
	    last=fon;
	  }
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
	    TempDescriptor src_tmp2=pre ? TempDescriptor.tempFactory("src",an.getDest().getType()) : out_temp;
	    TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3_",an.getDest().getType());

	    FlatFieldNode ffn=new FlatFieldNode(nn.getField(), getTempforVar(nn.getVar()), src_tmp2);
	    if (first==null)
	      first=ffn;
	    else {
	      last.addNext(ffn);
	    }
	    last=ffn;


	    if (base.getOp()==Operation.ADD&&an.getDest().getType().isString()) {
	      ClassDescriptor stringcd=typeutil.getClass(TypeUtil.StringClass);
	      MethodDescriptor concatmd=typeutil.getMethod(stringcd, "concat2", new TypeDescriptor[] {new TypeDescriptor(stringcd), new TypeDescriptor(stringcd)});
	      FlatCall fc=new FlatCall(concatmd, tmp, null, new TempDescriptor[] {src_tmp2, src_tmp});
	      src_tmp=tmp;
	      last.addNext(fc);
	      last=fc;
	    } else {
	      FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
	      src_tmp=tmp;
	      last.addNext(fon);
	      last=fon;
	    }
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
	    TempDescriptor tmp=TempDescriptor.tempFactory("srctmp3_",an.getDest().getType());
	    if (!pre) {
	      FlatOpNode fon=new FlatOpNode(out_temp, src_tmp2, null, new Operation(Operation.ASSIGN));
	      if (first==null)
		first=fon;
	      else
		last.addNext(fon);
	      last=fon;
	    }


	    if (base.getOp()==Operation.ADD&&an.getDest().getType().isString()) {
	      ClassDescriptor stringcd=typeutil.getClass(TypeUtil.StringClass);
	      MethodDescriptor concatmd=typeutil.getMethod(stringcd, "concat2", new TypeDescriptor[] {new TypeDescriptor(stringcd), new TypeDescriptor(stringcd)});
	      FlatCall fc=new FlatCall(concatmd, tmp, null, new TempDescriptor[] {src_tmp2, src_tmp});
	      if (first==null)
		first=fc;
	      else
		last.addNext(fc);
	      src_tmp=tmp;
	      last=fc;
	    } else {
	      FlatOpNode fon=new FlatOpNode(tmp, src_tmp2, src_tmp, base);
	      if (first==null)
		first=fon;
	      else
		last.addNext(fon);
	      src_tmp=tmp;
	      last=fon;
	    }
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
	} //end of else
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
      TempDescriptor tmp=getTempforVar(nn.isTag() ? nn.getTagVar() : nn.getVar());
      if (nn.isTag()) {
	//propagate tag
	out_temp.setTag(tmp.getTag());
      }
      FlatOpNode fon=new FlatOpNode(out_temp, tmp, null, new Operation(Operation.ASSIGN));
      return new NodePair(fon,fon);
    }
  }

  private NodePair flattenOpNode(OpNode on,TempDescriptor out_temp) {
    TempDescriptor temp_left=TempDescriptor.tempFactory("leftop",on.getLeft().getType());
    TempDescriptor temp_right=null;

    Operation op=on.getOp();

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
    } else if (op.getOp()==Operation.ADD&&on.getLeft().getType().isString()) {
      //We have a string concatenate
      ClassDescriptor stringcd=typeutil.getClass(TypeUtil.StringClass);
      MethodDescriptor concatmd=typeutil.getMethod(stringcd, "concat", new TypeDescriptor[] {new TypeDescriptor(stringcd)});
      FlatCall fc=new FlatCall(concatmd, out_temp, temp_left, new TempDescriptor[] {temp_right});
      left.getEnd().addNext(right.getBegin());
      right.getEnd().addNext(fc);
      return new NodePair(left.getBegin(), fc);
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

    case Kind.OffsetNode:
      return flattenOffsetNode((OffsetNode)en,out_temp);

    case Kind.TertiaryNode:
      return flattenTertiaryNode((TertiaryNode)en,out_temp);

    case Kind.InstanceOfNode:
      return flattenInstanceOfNode((InstanceOfNode)en,out_temp);

    case Kind.ArrayInitializerNode:
      return flattenArrayInitializerNode((ArrayInitializerNode)en,out_temp);
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

  private NodePair flattenTagDeclarationNode(TagDeclarationNode dn) {
    TagVarDescriptor tvd=dn.getTagVarDescriptor();
    TagDescriptor tag=tvd.getTag();
    TempDescriptor tmp=getTempforVar(tvd);
    FlatTagDeclaration ftd=new FlatTagDeclaration(tag, tmp);
    return new NodePair(ftd,ftd);
  }

  private TempDescriptor getTempforParam(Descriptor d) {
    if (temptovar.containsKey(d))
      return (TempDescriptor)temptovar.get(d);
    else {
      if (d instanceof VarDescriptor) {
	VarDescriptor vd=(VarDescriptor)d;
	TempDescriptor td=TempDescriptor.paramtempFactory(vd.getName(),vd.getType());
	temptovar.put(vd,td);
	return td;
      } else if (d instanceof TagVarDescriptor) {
	TagVarDescriptor tvd=(TagVarDescriptor)d;
	TypeDescriptor tagtype=new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass));
	TempDescriptor td=TempDescriptor.paramtempFactory(tvd.getName(), tagtype, tvd.getTag());
	temptovar.put(tvd,td);
	return td;
      } else throw new Error("Unreconized Descriptor");
    }
  }

  private TempDescriptor getTempforVar(Descriptor d) {
    if (temptovar.containsKey(d))
      return (TempDescriptor)temptovar.get(d);
    else {
      if (d instanceof VarDescriptor) {
	VarDescriptor vd=(VarDescriptor)d;
	TempDescriptor td=TempDescriptor.tempFactory(vd.getName(), vd.getType());
	temptovar.put(vd,td);
	return td;
      } else if (d instanceof TagVarDescriptor) {
	TagVarDescriptor tvd=(TagVarDescriptor)d;
	//BUGFIX TAGTYPE - add next line, modify following
	//line to tag this new type descriptor, modify
	//TempDescriptor constructor & factory to set type
	//using this Type To test, use any program with tags
	TypeDescriptor tagtype=new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass));
	TempDescriptor td=TempDescriptor.tempFactory(tvd.getName(),tagtype, tvd.getTag());
	temptovar.put(tvd,td);
	return td;
      } else throw new Error("Unrecognized Descriptor");
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
    if (true_np.getEnd()!=null)
	true_np.getEnd().addNext(nopend);
    if (false_np.getEnd()!=null)
	false_np.getEnd().addNext(nopend);
    return new NodePair(cond.getBegin(), nopend);
  }

  private NodePair flattenLoopNode(LoopNode ln) {
    HashSet oldbs=breakset;
    HashSet oldcs=continueset;
    breakset=new HashSet();
    continueset=new HashSet();
    
    if (ln.getType()==LoopNode.FORLOOP) {
      NodePair initializer=flattenBlockNode(ln.getInitializer());
      TempDescriptor cond_temp=TempDescriptor.tempFactory("condition", new TypeDescriptor(TypeDescriptor.BOOLEAN));
      NodePair condition=flattenExpressionNode(ln.getCondition(),cond_temp);
      NodePair update=flattenBlockNode(ln.getUpdate());
      NodePair body=flattenBlockNode(ln.getBody());
      FlatNode begin=initializer.getBegin();
      FlatCondBranch fcb=new FlatCondBranch(cond_temp);
      fcb.setTrueProb(State.TRUEPROB);
      fcb.setLoop();
      FlatNop nopend=new FlatNop();
      FlatBackEdge backedge=new FlatBackEdge();

      FlatNop nop2=new FlatNop();
      initializer.getEnd().addNext(nop2);
      nop2.addNext(condition.getBegin());
      if (body.getEnd()!=null)
	  body.getEnd().addNext(update.getBegin());
      update.getEnd().addNext(backedge);
      backedge.addNext(condition.getBegin());
      condition.getEnd().addNext(fcb);
      fcb.addFalseNext(nopend);
      fcb.addTrueNext(body.getBegin());
      for(Iterator contit=continueset.iterator();contit.hasNext();) {
	  FlatNode fn=(FlatNode)contit.next();
	  contit.remove();
	  fn.addNext(update.getBegin());
      }
      for(Iterator breakit=breakset.iterator();breakit.hasNext();) {
	  FlatNode fn=(FlatNode)breakit.next();
	  breakit.remove();
	  fn.addNext(nopend);
      }
      breakset=oldbs;
      continueset=oldcs;
      return new NodePair(begin,nopend);
    } else if (ln.getType()==LoopNode.WHILELOOP) {
      TempDescriptor cond_temp=TempDescriptor.tempFactory("condition", new TypeDescriptor(TypeDescriptor.BOOLEAN));
      NodePair condition=flattenExpressionNode(ln.getCondition(),cond_temp);
      NodePair body=flattenBlockNode(ln.getBody());
      FlatNode begin=condition.getBegin();
      FlatCondBranch fcb=new FlatCondBranch(cond_temp);
      fcb.setTrueProb(State.TRUEPROB);
      fcb.setLoop();
      FlatNop nopend=new FlatNop();
      FlatBackEdge backedge=new FlatBackEdge();

      if (body.getEnd()!=null)
	body.getEnd().addNext(backedge);
      backedge.addNext(condition.getBegin());

      condition.getEnd().addNext(fcb);
      fcb.addFalseNext(nopend);
      fcb.addTrueNext(body.getBegin());

      for(Iterator contit=continueset.iterator();contit.hasNext();) {
	  FlatNode fn=(FlatNode)contit.next();
	  contit.remove();
	  fn.addNext(backedge);
      }
      for(Iterator breakit=breakset.iterator();breakit.hasNext();) {
	  FlatNode fn=(FlatNode)breakit.next();
	  breakit.remove();
	  fn.addNext(nopend);
      }
      breakset=oldbs;
      continueset=oldcs;
      return new NodePair(begin,nopend);
    } else if (ln.getType()==LoopNode.DOWHILELOOP) {
      TempDescriptor cond_temp=TempDescriptor.tempFactory("condition", new TypeDescriptor(TypeDescriptor.BOOLEAN));
      NodePair condition=flattenExpressionNode(ln.getCondition(),cond_temp);
      NodePair body=flattenBlockNode(ln.getBody());
      FlatNode begin=body.getBegin();
      FlatCondBranch fcb=new FlatCondBranch(cond_temp);
      fcb.setTrueProb(State.TRUEPROB);
      fcb.setLoop();
      FlatNop nopend=new FlatNop();
      FlatBackEdge backedge=new FlatBackEdge();

      if (body.getEnd()!=null)
	body.getEnd().addNext(condition.getBegin());
      condition.getEnd().addNext(fcb);
      fcb.addFalseNext(nopend);
      fcb.addTrueNext(backedge);
      backedge.addNext(body.getBegin());

      for(Iterator contit=continueset.iterator();contit.hasNext();) {
	  FlatNode fn=(FlatNode)contit.next();
	  contit.remove();
	  fn.addNext(condition.getBegin());
      }
      for(Iterator breakit=breakset.iterator();breakit.hasNext();) {
	  FlatNode fn=(FlatNode)breakit.next();
	  breakit.remove();
	  fn.addNext(nopend);
      }
      breakset=oldbs;
      continueset=oldcs;
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
    rnflat.addNext(fe);
    FlatNode ln=rnflat;
    if (state.THREAD&&currmd.getModifiers().isSynchronized()) {
      MethodDescriptor memd=(MethodDescriptor)typeutil.getClass("Object").getMethodTable().get("MonitorExit");
      TempDescriptor thistd=getTempforVar(currmd.getThis());
      FlatCall fc=new FlatCall(memd, null, thistd, new TempDescriptor[0]);
      fc.addNext(ln);
      ln=fc;
    }
    if (state.DSM&&currmd.getModifiers().isAtomic()) {
      FlatAtomicExitNode faen=new FlatAtomicExitNode(curran);
      faen.addNext(ln);
      ln=faen;
    }

    if (cond!=null) {
      cond.getEnd().addNext(ln);
      return new NodePair(cond.getBegin(),null);
    } else
      return new NodePair(ln,null);
  }

  private NodePair flattenTaskExitNode(TaskExitNode ten) {
    FlatFlagActionNode ffan=new FlatFlagActionNode(FlatFlagActionNode.TASKEXIT);
    ffan.setTaskExitIndex(ten.getTaskExitIndex());
    updateFlagActionNode(ffan, ten.getFlagEffects());
    NodePair fcn=flattenConstraintCheck(ten.getChecks());
    ffan.addNext(fcn.getBegin());
    FlatReturnNode rnflat=new FlatReturnNode(null);
    rnflat.addNext(fe);
    fcn.getEnd().addNext(rnflat);
    return new NodePair(ffan, null);
  }

  private NodePair flattenConstraintCheck(Vector ccs) {
    FlatNode begin=new FlatNop();
    if (ccs==null)
      return new NodePair(begin,begin);
    FlatNode last=begin;
    for(int i=0; i<ccs.size(); i++) {
      ConstraintCheck cc=(ConstraintCheck) ccs.get(i);
      /* Flatten the arguments */
      TempDescriptor[] temps=new TempDescriptor[cc.numArgs()];
      String[] vars=new String[cc.numArgs()];
      for(int j=0; j<cc.numArgs(); j++) {
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

  private NodePair flattenAtomicNode(AtomicNode sbn) {
    NodePair np=flattenBlockNode(sbn.getBlockNode());
    FlatAtomicEnterNode faen=new FlatAtomicEnterNode();
    FlatAtomicExitNode faexn=new FlatAtomicExitNode(faen);
    faen.addNext(np.getBegin());
    np.getEnd().addNext(faexn);
    return new NodePair(faen, faexn);
  }

  private NodePair flattenSESENode(SESENode sn) {
    if( sn.isStart() ) {
      FlatSESEEnterNode fsen=new FlatSESEEnterNode(sn);
      sn.setFlatEnter(fsen);
      return new NodePair(fsen, fsen);
    }

    FlatSESEExitNode fsexn=new FlatSESEExitNode(sn);
    sn.setFlatExit(fsexn);
    FlatSESEEnterNode fsen=sn.getStart().getFlatEnter();
    fsexn.setFlatEnter(fsen);    
    sn.getStart().getFlatEnter().setFlatExit( fsexn );
    return new NodePair(fsexn, fsexn);
  }

  private NodePair flattenContinueBreakNode(ContinueBreakNode cbn) {
      FlatNop fn=new FlatNop();
      if (cbn.isBreak())
	  breakset.add(fn);
      else
	  continueset.add(fn);
      return new NodePair(fn,null);
  }

  private NodePair flattenInstanceOfNode(InstanceOfNode tn, TempDescriptor out_temp) {
    TempDescriptor expr_temp=TempDescriptor.tempFactory("expr",tn.getExpr().getType());
    NodePair cond=flattenExpressionNode(tn.getExpr(), expr_temp);
    FlatInstanceOfNode fion=new FlatInstanceOfNode(tn.getExprType(), expr_temp, out_temp);
    cond.getEnd().addNext(fion);
    return new NodePair(cond.getBegin(),fion);
  }

  private NodePair flattenArrayInitializerNode(ArrayInitializerNode ain, TempDescriptor out_temp) {
    /*
    TempDescriptor expr_temp=TempDescriptor.tempFactory("arry_init",ain.getType());

    // create a new array of size equal to the array initializer
    //FlatNode first=null;
    //FlatNode last=null;
    TempDescriptor[] temps=new TempDescriptor[ain.numVarInitializers()];

      for (int i=0; i<con.numArgs(); i++) {
	ExpressionNode en=con.getArg(i);
	TempDescriptor tmp=TempDescriptor.tempFactory("arg",en.getType());
	temps[i]=tmp;
	NodePair np=flattenExpressionNode(en, tmp);
	if (first==null)
	  first=np.getBegin();
	else
	  last.addNext(np.getBegin());
	last=np.getEnd();

	TempDescriptor tmp2=(i==0) ?
	                     out_temp :
	                     TempDescriptor.tempFactory("arg",en.getType());
      }
      FlatNew fn=new FlatNew(td, out_temp, temps[0], con.isGlobal(), con.getDisjointId());
      last.addNext(fn);


    // assign each element of the new array to the flattened expression


    FlatOpNode fonAssignArray=new FlatOpNode(out_temp, newarry_temp, null, new Operation(Operation.ASSIGN));
    */
    //return new NodePair( , fonAssignArray );
    ain.printNode(0);
    System.out.println( "Array initializers not implemented yet." );
    System.exit( -1 );
    return null;
  }

  private NodePair flattenTertiaryNode(TertiaryNode tn, TempDescriptor out_temp) {
    TempDescriptor cond_temp=TempDescriptor.tempFactory("tert_cond",new TypeDescriptor(TypeDescriptor.BOOLEAN));
    TempDescriptor true_temp=TempDescriptor.tempFactory("tert_true",tn.getTrueExpr().getType());
    TempDescriptor fals_temp=TempDescriptor.tempFactory("tert_fals",tn.getFalseExpr().getType());

    NodePair cond=flattenExpressionNode(tn.getCond(),cond_temp);
    FlatCondBranch fcb=new FlatCondBranch(cond_temp);

    NodePair trueExpr=flattenExpressionNode(tn.getTrueExpr(),true_temp);
    FlatOpNode fonT=new FlatOpNode(out_temp, true_temp, null, new Operation(Operation.ASSIGN));

    NodePair falseExpr=flattenExpressionNode(tn.getFalseExpr(),fals_temp);
    FlatOpNode fonF=new FlatOpNode(out_temp, fals_temp, null, new Operation(Operation.ASSIGN));

    FlatNop nopend=new FlatNop();

    cond.getEnd().addNext(fcb);

    fcb.addTrueNext(trueExpr.getBegin());
    fcb.addFalseNext(falseExpr.getBegin());

    trueExpr.getEnd().addNext(fonT);
    fonT.addNext(nopend);

    falseExpr.getEnd().addNext(fonF);
    fonF.addNext(nopend);
    
    return new NodePair(cond.getBegin(), nopend);
  }

  private NodePair flattenBlockStatementNode(BlockStatementNode bsn) {
    switch(bsn.kind()) {
    case Kind.BlockExpressionNode:
      return flattenBlockExpressionNode((BlockExpressionNode)bsn);

    case Kind.DeclarationNode:
      return flattenDeclarationNode((DeclarationNode)bsn);

    case Kind.TagDeclarationNode:
      return flattenTagDeclarationNode((TagDeclarationNode)bsn);

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

    case Kind.AtomicNode:
      return flattenAtomicNode((AtomicNode)bsn);

    case Kind.SESENode:
      return flattenSESENode((SESENode)bsn);

    case Kind.ContinueBreakNode:
      return flattenContinueBreakNode((ContinueBreakNode)bsn);
    }
    throw new Error();
  }
}
