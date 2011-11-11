package IR.Tree;

import java.util.*;

import IR.*;

public class SemanticCheck {
  State state;
  TypeUtil typeutil;
  Stack loopstack;
  HashSet toanalyze;
  HashMap<ClassDescriptor, Integer> completed;

  public static final int NOCHECK=0;
  public static final int REFERENCE=1;
  public static final int INIT=2;

  boolean checkAll;

  public boolean hasLayout(ClassDescriptor cd) {
    return completed.get(cd)!=null&&completed.get(cd)==INIT;
  }

  public SemanticCheck(State state, TypeUtil tu) {
    this(state, tu, true);
  }

  public SemanticCheck(State state, TypeUtil tu, boolean checkAll) {
    this.state=state;
    this.typeutil=tu;
    this.loopstack=new Stack();
    this.toanalyze=new HashSet();
    this.completed=new HashMap<ClassDescriptor, Integer>();
    this.checkAll=checkAll;
  }

  public ClassDescriptor getClass(ClassDescriptor context, String classname) {
    return getClass(context, classname, INIT);
  }
  public ClassDescriptor getClass(ClassDescriptor context, String classnameIn, int fullcheck) {
    ClassDescriptor cd=typeutil.getClass(context, classnameIn, toanalyze);
    checkClass(cd, fullcheck);
    return cd;
  }

  public void checkClass(ClassDescriptor cd) {
    checkClass(cd, INIT);
  }

  public void checkClass(ClassDescriptor cd, int fullcheck) {
    if (!completed.containsKey(cd)||completed.get(cd)<fullcheck) {
      int oldstatus=completed.containsKey(cd)?completed.get(cd):0;
      completed.put(cd, fullcheck);

      if (fullcheck>=REFERENCE&&oldstatus<INIT) {
        //Set superclass link up
        if (cd.getSuper()!=null) {
	  ClassDescriptor superdesc=getClass(cd, cd.getSuper(), fullcheck);
	  if (superdesc.isInnerClass()) {
	    cd.setAsInnerClass();
	  }
	  if (superdesc.isInterface()) {
	    if (cd.getInline()) {
	      cd.setSuper(null);
	      cd.getSuperInterface().add(superdesc.getSymbol());
	    } else {
	      throw new Error("Error! Class " + cd.getSymbol() + " extends interface " + cd.getSuper());
	    }
	  } else {
	    cd.setSuperDesc(superdesc);

	    // Link together Field, Method, and Flag tables so classes
	    // inherit these from their superclasses
	    if (oldstatus<REFERENCE) {
	      cd.getFieldTable().setParent(cd.getSuperDesc().getFieldTable());
	      cd.getMethodTable().setParent(cd.getSuperDesc().getMethodTable());
	      cd.getFlagTable().setParent(cd.getSuperDesc().getFlagTable());
	    }
	  }
        }
        // Link together Field, Method tables do classes inherit these from
        // their ancestor interfaces
        Vector<String> sifv = cd.getSuperInterface();
        for(int i = 0; i < sifv.size(); i++) {
          ClassDescriptor superif = getClass(cd, sifv.elementAt(i), fullcheck);
          if(!superif.isInterface()) {
            throw new Error("Error! Class " + cd.getSymbol() + " implements non-interface " + superif.getSymbol());
          }
          if (oldstatus<REFERENCE) {
            cd.addSuperInterfaces(superif);
            cd.getMethodTable().addParentIF(superif.getMethodTable());
            cd.getFieldTable().addParentIF(superif.getFieldTable());
          }
        }
      }
      if (oldstatus<INIT&&fullcheck>=INIT) {
       if (cd.isInnerClass()) {
          Modifiers fdmodifiers=new Modifiers();
         FieldDescriptor enclosingfd=new FieldDescriptor(fdmodifiers,new TypeDescriptor(cd.getSurroundingDesc()),"this___enclosing",null,false);
         cd.addField(enclosingfd);
       }

        /* Check to see that fields are well typed */
        for(Iterator field_it=cd.getFields(); field_it.hasNext(); ) {
          FieldDescriptor fd=(FieldDescriptor)field_it.next();
	  try {
          checkField(cd,fd);
	  } catch (Error e) {
	    System.out.println("Class/Field in "+cd+":"+fd);
	    throw e;
	  }
        }
        for(Iterator method_it=cd.getMethods(); method_it.hasNext(); ) {
          MethodDescriptor md=(MethodDescriptor)method_it.next();
	  try {
          checkMethod(cd,md);
	  } catch (Error e) {
	    System.out.println("Class/Method in "+cd+":"+md);
	    throw e;
	  }
        }
      }
    }
  }

  public void semanticCheck() {
    SymbolTable classtable = state.getClassSymbolTable();
    toanalyze.addAll(classtable.getValueSet());
    toanalyze.addAll(state.getTaskSymbolTable().getValueSet());

    // Do methods next
    while (!toanalyze.isEmpty()) {
      Object obj = toanalyze.iterator().next();
      if (obj instanceof TaskDescriptor) {
        toanalyze.remove(obj);
        TaskDescriptor td = (TaskDescriptor) obj;
        try {
          checkTask(td);
        } catch (Error e) {
          System.out.println("Error in " + td);
          throw e;
        }
      } else {
        ClassDescriptor cd = (ClassDescriptor) obj;
        toanalyze.remove(cd);

        // need to initialize typeutil object here...only place we can
        // get class descriptors without first calling getclass
        getClass(cd, cd.getSymbol());
        for (Iterator method_it = cd.getMethods(); method_it.hasNext(); ) {
          MethodDescriptor md = (MethodDescriptor) method_it.next();
          try {
            checkMethodBody(cd, md);
          } catch (Error e) {
            System.out.println("Error in " + md);
            throw e;
          }
        }
      }
    }
  }

  private void checkTypeDescriptor(ClassDescriptor cd, TypeDescriptor td) {
    if (td.isPrimitive())
      return;       /* Done */
    else if (td.isClass()) {
      String name=td.toString();
      ClassDescriptor field_cd=checkAll?getClass(cd, name):getClass(cd, name, REFERENCE);

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
    checkTypeDescriptor(cd, fd.getType());
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
      checkTypeDescriptor(null, param_type);

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
    /* Check for abstract methods */
    if(md.isAbstract()) {
      if(!cd.isAbstract() && !cd.isInterface()) {
        throw new Error("Error! The non-abstract Class " + cd.getSymbol() + " contains an abstract method " + md.getSymbol());
      }
    }

    /* Check return type */
    if (!md.isConstructor() && !md.isStaticBlock())
      if (!md.getReturnType().isVoid()) {
        checkTypeDescriptor(cd, md.getReturnType());
      }
    for(int i=0; i<md.numParameters(); i++) {
      TypeDescriptor param_type=md.getParamType(i);
      checkTypeDescriptor(cd, param_type);
    }
    /* Link the naming environments */
    if (!md.isStatic())     /* Fields aren't accessible directly in a static method, so don't link in this table */
      md.getParameterTable().setParent(cd.getFieldTable());
    md.setClassDesc(cd);
    if (!md.isStatic()) {
      VarDescriptor thisvd=new VarDescriptor(new TypeDescriptor(cd),"this");
      md.setThis(thisvd);
    }
    if(md.isDefaultConstructor() && (cd.getSuperDesc() != null)) {
      // add the construction of it super class, can only be super()
      NameDescriptor nd=new NameDescriptor("super");
      MethodInvokeNode min=new MethodInvokeNode(nd);
      BlockExpressionNode ben=new BlockExpressionNode(min);
      BlockNode bn = state.getMethodBody(md);
      bn.addFirstBlockStatement(ben);
    }
  }

  public void checkMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    ClassDescriptor superdesc=cd.getSuperDesc();
    if (superdesc!=null) {
      Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
      for(Iterator methodit=possiblematches.iterator(); methodit.hasNext(); ) {
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

    case Kind.SwitchStatementNode:
      checkSwitchStatementNode(md, nametable, (SwitchStatementNode)bsn);
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

    case Kind.SynchronizedNode:
      checkSynchronizedNode(md, nametable, (SynchronizedNode)bsn);
      return;

    case Kind.ContinueBreakNode:
      checkContinueBreakNode(md, nametable, (ContinueBreakNode) bsn);
      return;

    case Kind.SESENode:
    case Kind.GenReachNode:
    case Kind.GenDefReachNode:
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
    checkTypeDescriptor(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), vd.getType());
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

  void checkSynchronizedNode(Descriptor md, SymbolTable nametable, SynchronizedNode sbn) {
    checkBlockNode(md, nametable, sbn.getBlockNode());
    //todo this could be Object
    checkExpressionNode(md, nametable, sbn.getExpr(), null);
  }

  void checkContinueBreakNode(Descriptor md, SymbolTable nametable, ContinueBreakNode cbn) {
    if (loopstack.empty())
      throw new Error("continue/break outside of loop");
    Object o = loopstack.peek();
    if(o instanceof LoopNode) {
      LoopNode ln=(LoopNode)o;
      cbn.setLoop(ln);
    }
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

  void checkSwitchStatementNode(Descriptor md, SymbolTable nametable, SwitchStatementNode ssn) {
    checkExpressionNode(md, nametable, ssn.getCondition(), new TypeDescriptor(TypeDescriptor.INT));

    BlockNode sbn = ssn.getSwitchBody();
    boolean hasdefault = false;
    for(int i = 0; i < sbn.size(); i++) {
      boolean containdefault = checkSwitchBlockNode(md, nametable, (SwitchBlockNode)sbn.get(i));
      if(hasdefault && containdefault) {
        throw new Error("Error: duplicate default branch in switch-case statement in Method: " + md.getSymbol());
      }
      hasdefault = containdefault;
    }
  }

  boolean checkSwitchBlockNode(Descriptor md, SymbolTable nametable, SwitchBlockNode sbn) {
    Vector<SwitchLabelNode> slnv = sbn.getSwitchConditions();
    int defaultb = 0;
    for(int i = 0; i < slnv.size(); i++) {
      if(slnv.elementAt(i).isdefault) {
        defaultb++;
      } else {
        checkConstantExpressionNode(md, nametable, slnv.elementAt(i).getCondition(), new TypeDescriptor(TypeDescriptor.INT));
      }
    }
    if(defaultb > 1) {
      throw new Error("Error: duplicate default branch in switch-case statement in Method: " + md.getSymbol());
    } else {
      loopstack.push(sbn);
      checkBlockNode(md, nametable, sbn.getSwitchBlockStatement());
      loopstack.pop();
      return (defaultb > 0);
    }
  }

  void checkConstantExpressionNode(Descriptor md, SymbolTable nametable, ExpressionNode en, TypeDescriptor td) {
    switch(en.kind()) {
    case Kind.FieldAccessNode:
      checkFieldAccessNode(md,nametable,(FieldAccessNode)en,td);
      return;

    case Kind.LiteralNode:
      checkLiteralNode(md,nametable,(LiteralNode)en,td);
      return;

    case Kind.NameNode:
      checkNameNode(md,nametable,(NameNode)en,td);
      return;

    case Kind.OpNode:
      checkOpNode(md, nametable, (OpNode)en, td);
      return;
    }
    throw new Error();
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
      checkOffsetNode(md, nametable, (OffsetNode)en, td);
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

    case Kind.ClassTypeNode:
      checkClassTypeNode(md, nametable, (ClassTypeNode) en, td);
      return;
    }
    throw new Error();
  }

  void checkClassTypeNode(Descriptor md, SymbolTable nametable, ClassTypeNode tn, TypeDescriptor td) {
    checkTypeDescriptor(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), tn.getType());
  }

  void checkCastNode(Descriptor md, SymbolTable nametable, CastNode cn, TypeDescriptor td) {
    /* Get type descriptor */
    if (cn.getType()==null) {
      NameDescriptor typenamed=cn.getTypeName().getName();
      TypeDescriptor ntd=new TypeDescriptor(getClass(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), typenamed.toString()));
      cn.setType(ntd);
    }

    /* Check the type descriptor */
    TypeDescriptor cast_type=cn.getType();
    checkTypeDescriptor(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null),cast_type);

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

    //rough hack to handle interfaces...should clean up
    if (etd.isClass()&&cast_type.isClass()) {
      ClassDescriptor cdetd=etd.getClassDesc();
      ClassDescriptor cdcast_type=cast_type.getClassDesc();

      if (cdetd.isInterface()&&!cdcast_type.getModifier().isFinal())
	return;
      
      if (cdcast_type.isInterface()&&!cdetd.getModifier().isFinal())
	return;
    }
    /* Different branches */
    /* TODO: change if add interfaces */
    throw new Error("Cast will always fail\n"+cn.printNode(0));
  }

  //FieldDescriptor checkFieldAccessNodeForParentNode( Descriptor md, SymbolTable na )
  void checkFieldAccessNode(Descriptor md, SymbolTable nametable, FieldAccessNode fan, TypeDescriptor td) {
    ExpressionNode left=fan.getExpression();
    checkExpressionNode(md,nametable,left,null);
    TypeDescriptor ltd=left.getType();
    if (!ltd.isArray())
      checkClass(ltd.getClassDesc(), INIT);
    String fieldname=fan.getFieldName();

    FieldDescriptor fd=null;
    if (ltd.isArray()&&fieldname.equals("length"))
      fd=FieldDescriptor.arrayLength;
    else if(((left instanceof NameNode) && ((NameNode)left).isSuper())
	    ||((left instanceof FieldAccessNode) && ((FieldAccessNode)left).isSuper())){
      fd = (FieldDescriptor) ltd.getClassDesc().getSuperDesc().getFieldTable().get(fieldname);
    } else {
      fd=(FieldDescriptor) ltd.getClassDesc().getFieldTable().get(fieldname);
    }
    if(ltd.isClassNameRef()) {
      // the field access is using a class name directly
      if (fd==null) {
       // check if it is to access a surrounding class in an inner class
       if(fieldname.equals("this") || fieldname.equals("super")) {
	   ClassDescriptor icd = ((VarDescriptor)nametable.get("this")).getType().getClassDesc();
	   if(icd.isInnerClass()) {
	       NameNode nn = new NameNode(new NameDescriptor("this"));
	       nn.setVar((VarDescriptor)nametable.get("this"));
	       fan.setExpression(nn);
	       if(icd.getSurroundingDesc()==ltd.getClassDesc()) {
		   // this is a surrounding class access inside an inner class
		   fan.setExpression(nn);
		   fan.setFieldName("this$0");
		   fd = (FieldDescriptor)icd.getFieldTable().get("this$0");
	       } else if(icd==ltd.getClassDesc()) {
		   // this is an inner class this operation 
		   fd = new FieldDescriptor(new Modifiers(),new TypeDescriptor(icd),"this",null,false);
	       }
	       if(fieldname.equals("super")) {
		   fan.setIsSuper();
	       }
	       fan.setField(fd);
	       return;
	   }
       } 
       ClassDescriptor surroundingCls=ltd.getClassDesc().getSurroundingDesc();
       
       while(surroundingCls!=null) {
         fd=(FieldDescriptor) surroundingCls.getFieldTable().get(fieldname);
         if (fd!=null) {
           fan.left=new ClassTypeNode(new TypeDescriptor(surroundingCls));
           break;
         }
         surroundingCls=surroundingCls.getSurroundingDesc();
       }
      }

      if(ltd.getClassDesc().isEnum()) {
        int value = ltd.getClassDesc().getEnumConstant(fieldname);
        if(-1 == value) {
          // check if this field is an enum constant
          throw new Error(fieldname + " is not an enum constant in "+fan.printNode(0)+" in "+md);
        }
        fd = new FieldDescriptor(new Modifiers(Modifiers.PUBLIC|Modifiers.FINAL), new TypeDescriptor(TypeDescriptor.INT), fieldname, null, false);
        fd.setAsEnum();
        fd.setEnumValue(value);
      } else if(fd == null) {
        throw new Error("Could not find field "+ fieldname + " in "+fan.printNode(0)+" in "+md + " (Line: "+fan.getNumLine()+")");
      } else if(fd.isStatic()) {
        // check if this field is a static field
        if(fd.getExpressionNode() != null) {
          checkExpressionNode(md,nametable,fd.getExpressionNode(),null);
        }
      } else {
        throw new Error("Dereference of the non-static field "+ fieldname + " in "+fan.printNode(0)+" in "+md);
      }
    }

    if (fd==null){
	if((md instanceof MethodDescriptor) && false == ((MethodDescriptor)md).isStaticBlock()) {
		ClassDescriptor cd = ((MethodDescriptor)md).getClassDesc();
		FieldAccessNode theFieldNode = 	fieldAccessExpression( cd, fieldname, fan.getNumLine() );
		if( null != theFieldNode ) {
			//fan = theFieldNode;
      			checkFieldAccessNode( md, nametable, theFieldNode, td );
			fan.setField( theFieldNode.getField() );
			fan.setExpression( theFieldNode.getExpression() );
			//TypeDescriptor td1 = fan.getType();
			//td1.toString();
			return;		
		}	
		}
	throw new Error("Unknown field "+fieldname + " in "+fan.printNode(0)+" in "+md);
      }
    if (fd==null) {
      ClassDescriptor surroundingCls=ltd.getClassDesc().getSurroundingDesc();
      int numencloses=1;
      while(surroundingCls!=null) {
       fd=(FieldDescriptor)surroundingCls.getFieldTable().get(fieldname);
       if (fd!=null) {
         surroundingCls=ltd.getClassDesc().getSurroundingDesc();
         FieldAccessNode ftmp=fan;
         for(;numencloses>0;numencloses--) {
           FieldAccessNode fnew=new FieldAccessNode(ftmp.left, "this___enclosing");
           fnew.setField((FieldDescriptor)surroundingCls.getFieldTable().get("this___enclosing"));
           ftmp.left=fnew;
           ftmp=fnew;
           surroundingCls=surroundingCls.getSurroundingDesc();
         }
         break;
       }
       surroundingCls=surroundingCls.getSurroundingDesc();
       numencloses++;
      }

      if (fd==null)
       throw new Error("Unknown field "+fieldname + " in "+fan.printNode(0)+" in "+md);
    }


    if (fd.getType().iswrapper()) {
      FieldAccessNode fan2=new FieldAccessNode(left, fieldname);
      fan2.setNumLine(left.getNumLine());
      fan2.setField(fd);
      fan.left=fan2;
      fan.fieldname="value";

      ExpressionNode leftwr=fan.getExpression();
      TypeDescriptor ltdwr=leftwr.getType();
      String fieldnamewr=fan.getFieldName();
      FieldDescriptor fdwr=(FieldDescriptor) ltdwr.getClassDesc().getFieldTable().get(fieldnamewr);
      fan.setField(fdwr);
      if (fdwr==null)
        throw new Error("Unknown field "+fieldnamewr + " in "+fan.printNode(0)+" in "+md);
    } else {
      fan.setField(fd);
    }
    if (td!=null) {
      if (!typeutil.isSuperorType(td,fan.getType()))
        throw new Error("Field node returns "+fan.getType()+", but need "+td);
    }
  }

  void checkArrayAccessNode(Descriptor md, SymbolTable nametable, ArrayAccessNode aan, TypeDescriptor td) {
    ExpressionNode left=aan.getExpression();
    checkExpressionNode(md,nametable,left,null);

    checkExpressionNode(md,nametable,aan.getIndex(),new TypeDescriptor(TypeDescriptor.INT));
    TypeDescriptor ltd=left.getType();
    if (ltd.dereference().iswrapper()) {
      aan.wrappertype=((FieldDescriptor)ltd.dereference().getClassDesc().getFieldTable().get("value")).getType();
    }

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
      ln.setType(new TypeDescriptor(getClass(null, TypeUtil.StringClass)));
    }

    if (td!=null)
      if (!typeutil.isSuperorType(td,ln.getType())) {
        Long l = ln.evaluate();
        if((ln.getType().isByte() || ln.getType().isShort()
            || ln.getType().isChar() || ln.getType().isInt())
           && (l != null)
           && (td.isByte() || td.isShort() || td.isChar()
               || td.isInt() || td.isLong())) {
          long lnvalue = l.longValue();
          if((td.isByte() && ((lnvalue > 127) || (lnvalue < -128)))
             || (td.isShort() && ((lnvalue > 32767) || (lnvalue < -32768)))
             || (td.isChar() && ((lnvalue > 65535) || (lnvalue < 0)))
             || (td.isInt() && ((lnvalue > 2147483647) || (lnvalue < -2147483648)))
             || (td.isLong() && ((lnvalue > 9223372036854775807L) || (lnvalue < -9223372036854775808L)))) {
            throw new Error("Field node returns "+ln.getType()+", but need "+td+" in "+md);
          }
        } else {
          throw new Error("Field node returns "+ln.getType()+", but need "+td+" in "+md);
        }
      }
  }

  FieldDescriptor recurseSurroundingClasses( ClassDescriptor icd, String varname ) {
        if( null == icd || false == icd.isInnerClass() )
	    return null;
      
        ClassDescriptor surroundingDesc = icd.getSurroundingDesc();
        if( null == surroundingDesc )
	    return null;
      
        SymbolTable fieldTable = surroundingDesc.getFieldTable();
        FieldDescriptor fd = ( FieldDescriptor ) fieldTable.get( varname );
        if( null != fd )
	    return fd;
        return recurseSurroundingClasses( surroundingDesc, varname );
  }
  
  FieldAccessNode fieldAccessExpression( ClassDescriptor icd, String varname, int linenum ) {
        FieldDescriptor fd = recurseSurroundingClasses( icd, varname );
	if( null == fd )
		return null;

	ClassDescriptor cd = fd.getClassDescriptor();
	int depth = 1;
	int startingDepth = icd.getInnerDepth();

	if( true == cd.isInnerClass() ) 
		depth = cd.getInnerDepth();

	String composed = "this";
	NameDescriptor runningDesc = new NameDescriptor( "this" );;
	
	for ( int index = startingDepth; index > depth; --index ) {
		composed = "this$" + String.valueOf( index - 1  );	
		runningDesc = new NameDescriptor( runningDesc, composed );
	}
	if( false == cd.isInnerClass() )
		runningDesc = new NameDescriptor( runningDesc, "this$" + String.valueOf(0) ); //all the way up.
	NameDescriptor idDesc = new NameDescriptor( runningDesc, varname );
	
	
	FieldAccessNode theFieldNode = ( FieldAccessNode )translateNameDescriptorintoExpression( idDesc, linenum );
	return theFieldNode;
  }

  void checkNameNode(Descriptor md, SymbolTable nametable, NameNode nn, TypeDescriptor td) {
    NameDescriptor nd=nn.getName();
    if (nd.getBase()!=null) {
      /* Big hack */
      /* Rewrite NameNode */
      ExpressionNode en=translateNameDescriptorintoExpression(nd,nn.getNumLine());
      nn.setExpression(en);
      checkExpressionNode(md,nametable,en,td);
    } else {
      String varname=nd.toString();
      if(varname.equals("this") || varname.equals("super")) {
        // "this"
        nn.setVar((VarDescriptor)nametable.get("this"));
        if(varname.equals("super")) {
            nn.setIsSuper();
        }
        return;
      }
      Descriptor d=(Descriptor)nametable.get(varname);
      if (d==null) {
        ClassDescriptor cd = null;
	//check the inner class case first.
	if((md instanceof MethodDescriptor) && false == ((MethodDescriptor)md).isStaticBlock()) {
		cd = ((MethodDescriptor)md).getClassDesc();
		FieldAccessNode theFieldNode = 	fieldAccessExpression( cd, varname, nn.getNumLine() );
		if( null != theFieldNode ) {
			nn.setExpression(( ExpressionNode )theFieldNode);
      			checkExpressionNode(md,nametable,( ExpressionNode )theFieldNode,td);
			return;		
		}		
	}
        if((md instanceof MethodDescriptor) && ((MethodDescriptor)md).isStaticBlock()) {
          // this is a static block, all the accessed fields should be static field
          cd = ((MethodDescriptor)md).getClassDesc();
          SymbolTable fieldtbl = cd.getFieldTable();
          FieldDescriptor fd=(FieldDescriptor)fieldtbl.get(varname);
          if((fd == null) || (!fd.isStatic())) {
            // no such field in the class, check if this is a class
            if(varname.equals("this")) {
              throw new Error("Error: access this obj in a static block");
            }
            cd=getClass(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), varname);
            if(cd != null) {
              // this is a class name
              nn.setClassDesc(cd);
              return;
            } else {
              throw new Error("Name "+varname+" should not be used in static block: "+md);
            }
          } else {
            // this is a static field
            nn.setField(fd);
            nn.setClassDesc(cd);
            return;
          }
        } else {
          // check if the var is a static field of the class
          if(md instanceof MethodDescriptor) {
            cd = ((MethodDescriptor)md).getClassDesc();
            FieldDescriptor fd = (FieldDescriptor)cd.getFieldTable().get(varname);
            if((fd != null) && (fd.isStatic())) {
              nn.setField(fd);
              nn.setClassDesc(cd);
              if (td!=null)
                if (!typeutil.isSuperorType(td,nn.getType()))
                  throw new Error("Field node returns "+nn.getType()+", but need "+td);
              return;
            } else if(fd != null) {
              throw new Error("Name "+varname+" should not be used in " + md);
            }
          }
          cd=getClass(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), varname);
          if(cd != null) {
            // this is a class name
            nn.setClassDesc(cd);
            return;
          } else {
            throw new Error("Name "+varname+" undefined in: "+md);	    
          }
        }
      }
      if (d instanceof VarDescriptor) {
        nn.setVar(d);
      } else if (d instanceof FieldDescriptor) {
        FieldDescriptor fd=(FieldDescriptor)d;
        if (fd.getType().iswrapper()) {
          String id=nd.getIdentifier();
          NameDescriptor base=nd.getBase();
          NameNode n=new NameNode(nn.getName());
          n.setNumLine(nn.getNumLine());
          n.setField(fd);
          n.setVar((VarDescriptor)nametable.get("this"));        /* Need a pointer to this */
          FieldAccessNode fan=new FieldAccessNode(n,"value");
          fan.setNumLine(n.getNumLine());
          FieldDescriptor fdval=(FieldDescriptor) fd.getType().getClassDesc().getFieldTable().get("value");
          fan.setField(fdval);
          nn.setExpression(fan);
        } else {
          nn.setField(fd);
          nn.setVar((VarDescriptor)nametable.get("this"));        /* Need a pointer to this */
        }
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
    checkTypeDescriptor(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null),ltd);

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
        throw new Error("Type of rside not compatible with type of lside"+ofn.printNode(0));
      }
    }
  }


  void checkTertiaryNode(Descriptor md, SymbolTable nametable, TertiaryNode tn, TypeDescriptor td) {
    checkExpressionNode(md, nametable, tn.getCond(), new TypeDescriptor(TypeDescriptor.BOOLEAN));
    checkExpressionNode(md, nametable, tn.getTrueExpr(), td);
    checkExpressionNode(md, nametable, tn.getFalseExpr(), td);
  }

  void checkInstanceOfNode(Descriptor md, SymbolTable nametable, InstanceOfNode tn, TypeDescriptor td) {
    if (td!=null&&!td.isBoolean())
      throw new Error("Expecting type "+td+"for instanceof expression");

    checkTypeDescriptor(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), tn.getExprType());
    checkExpressionNode(md, nametable, tn.getExpr(), null);
  }

  void checkArrayInitializerNode(Descriptor md, SymbolTable nametable, ArrayInitializerNode ain, TypeDescriptor td) {
    Vector<TypeDescriptor> vec_type = new Vector<TypeDescriptor>();
    for( int i = 0; i < ain.numVarInitializers(); ++i ) {
      checkExpressionNode(md, nametable, ain.getVarInitializer(i), td.dereference());
      vec_type.add(ain.getVarInitializer(i).getType());
    }
    if (td==null)
      throw new Error();

    ain.setType(td);
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
      ClassDescriptor stringcl=getClass(null, TypeUtil.StringClass);
      TypeDescriptor stringtd=new TypeDescriptor(stringcl);
      NameDescriptor nd=new NameDescriptor("String");
      NameDescriptor valuend=new NameDescriptor(nd, "valueOf");

      if (!(an.getSrc().getType().isString()&&(an.getSrc() instanceof OpNode))) {
        MethodInvokeNode rightmin=new MethodInvokeNode(valuend);
        rightmin.setNumLine(an.getSrc().getNumLine());
        rightmin.addArgument(an.getSrc());
        an.right=rightmin;
        checkExpressionNode(md, nametable, an.getSrc(), null);
      }
    }

    if (!postinc&&!typeutil.isSuperorType(an.getDest().getType(),an.getSrc().getType())) {
      TypeDescriptor dt = an.getDest().getType();
      TypeDescriptor st = an.getSrc().getType();
      if(an.getSrc().kind() == Kind.ArrayInitializerNode) {
        if(dt.getArrayCount() != st.getArrayCount()) {
          throw new Error("Type of rside ("+an.getSrc().getType().toPrettyString()+") not compatible with type of lside ("+an.getDest().getType().toPrettyString()+")"+an.printNode(0));
        } else {
          do {
            dt = dt.dereference();
            st = st.dereference();
          } while(dt.isArray());
          if((st.isByte() || st.isShort() || st.isChar() || st.isInt())
             && (dt.isByte() || dt.isShort() || dt.isChar() || dt.isInt() || dt.isLong())) {
            return;
          } else {
            throw new Error("Type of rside ("+an.getSrc().getType().toPrettyString()+") not compatible with type of lside ("+an.getDest().getType().toPrettyString()+")"+an.printNode(0));
          }
        }
      } else {
        Long l = an.getSrc().evaluate();
        if((st.isByte() || st.isShort() || st.isChar() || st.isInt())
           && (l != null)
           && (dt.isByte() || dt.isShort() || dt.isChar() || dt.isInt() || dt.isLong())) {
          long lnvalue = l.longValue();
          if((dt.isByte() && ((lnvalue > 127) || (lnvalue < -128)))
             || (dt.isShort() && ((lnvalue > 32767) || (lnvalue < -32768)))
             || (dt.isChar() && ((lnvalue > 65535) || (lnvalue < 0)))
             || (dt.isInt() && ((lnvalue > 2147483647) || (lnvalue < -2147483648)))
             || (dt.isLong() && ((lnvalue > 9223372036854775807L) || (lnvalue < -9223372036854775808L)))) {
            throw new Error("Type of rside ("+an.getSrc().getType().toPrettyString()+") not compatible with type of lside ("+an.getDest().getType().toPrettyString()+")"+an.printNode(0));
          }
        } else {
          throw new Error("Type of rside ("+an.getSrc().getType().toPrettyString()+") not compatible with type of lside ("+an.getDest().getType().toPrettyString()+")"+an.printNode(0));
        }
      }
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

  void InnerClassAddParamToCtor( MethodDescriptor md, ClassDescriptor cd, SymbolTable nametable, 
				 CreateObjectNode con, TypeDescriptor td ) {
	
	TypeDescriptor cdsType = new TypeDescriptor( cd );
	ExpressionNode conExp = con.getSurroundingClassExpression();
	//System.out.println( "The surrounding class expression si " + con );
	if( null == conExp ) {
		if( md.isStatic() ) {
			throw new Error("trying to instantiate inner class: " +  con.getType() + " in a static scope" );
		}
		VarDescriptor thisVD = md.getThis();
		if( null == thisVD ) {
			throw new Error( "this pointer is not defined in a non static scope" );	
		}			
		if( cdsType.equals( thisVD.getType() ) == false ) {
			throw new Error( "the type of this pointer is different than the type expected for inner class constructor. Initializing the inner class: " 				+  con.getType() + " in the wrong scope" );		
		}	
		//make this into an expression node.
		NameNode nThis=new NameNode( new NameDescriptor( "this" ) );
		con.addArgument( nThis );
	}
	else {
		//REVISIT : here i am storing the expression as an expressionNode which does not implement type, there is no way for me to semantic check this argument.
		con.addArgument( conExp );
	}
	//System.out.println( " the modified createObjectNode is " + con.printNode( 0 ) + "\n" );
}
  void checkCreateObjectNode(Descriptor md, SymbolTable nametable, CreateObjectNode con,
                             TypeDescriptor td) {
    TypeDescriptor[] tdarray = new TypeDescriptor[con.numArgs()];
    for (int i = 0; i < con.numArgs(); i++) {
      ExpressionNode en = con.getArg(i);
      checkExpressionNode(md, nametable, en, null);
      tdarray[i] = en.getType();
    }

    TypeDescriptor typetolookin = con.getType();
    checkTypeDescriptor(((md instanceof MethodDescriptor)?((MethodDescriptor)md).getClassDesc():null), typetolookin);

    if (td != null && !typeutil.isSuperorType(td, typetolookin))
      throw new Error(typetolookin + " isn't a " + td);

    /* Check Array Initializers */
    if ((con.getArrayInitializer() != null)) {
      checkArrayInitializerNode(md, nametable, con.getArrayInitializer(), typetolookin);
    }

    /* Check flag effects */
    if (con.getFlagEffects() != null) {
      FlagEffects fe = con.getFlagEffects();
      ClassDescriptor cd = typetolookin.getClassDesc();

      for (int j = 0; j < fe.numEffects(); j++) {
        FlagEffect flag = fe.getEffect(j);
        String name = flag.getName();
        FlagDescriptor flag_d = (FlagDescriptor) cd.getFlagTable().get(name);
        // Make sure the flag is declared
        if (flag_d == null)
          throw new Error("Flag descriptor " + name + " undefined in class: " + cd.getSymbol());
        if (flag_d.getExternal())
          throw new Error("Attempting to modify external flag: " + name);
        flag.setFlag(flag_d);
      }
      for (int j = 0; j < fe.numTagEffects(); j++) {
        TagEffect tag = fe.getTagEffect(j);
        String name = tag.getName();

        Descriptor d = (Descriptor) nametable.get(name);
        if (d == null)
          throw new Error("Tag descriptor " + name + " undeclared");
        else if (!(d instanceof TagVarDescriptor))
          throw new Error(name + " is not a tag descriptor");
        tag.setTag((TagVarDescriptor) d);
      }
    }

    if ((!typetolookin.isClass()) && (!typetolookin.isArray()))
      throw new Error("Can't allocate primitive type:" + con.printNode(0));

    if (!typetolookin.isArray()) {
      // Array's don't need constructor calls
      ClassDescriptor classtolookin = typetolookin.getClassDesc();
      checkClass(classtolookin, INIT);
      if( classtolookin.isInnerClass() ) {
      	InnerClassAddParamToCtor( (MethodDescriptor)md, ((MethodDescriptor)md).getClassDesc() , nametable, con, td );
	tdarray = new TypeDescriptor[con.numArgs()];
    	for (int i = 0; i < con.numArgs(); i++) {
      		ExpressionNode en = con.getArg(i);
     		checkExpressionNode(md, nametable, en, null);
     		tdarray[i] = en.getType();
   	 }
      }
      Set methoddescriptorset = classtolookin.getMethodTable().getSet(classtolookin.getSymbol());
      MethodDescriptor bestmd = null;
NextMethod: for (Iterator methodit = methoddescriptorset.iterator(); methodit.hasNext(); ) {
        MethodDescriptor currmd = (MethodDescriptor) methodit.next();
        /* Need correct number of parameters */
        if (con.numArgs() != currmd.numParameters())
          continue;
        for (int i = 0; i < con.numArgs(); i++) {
          if (!typeutil.isSuperorType(currmd.getParamType(i), tdarray[i]))
            continue NextMethod;
        }
        /* Local allocations can't call global allocator */
        if (!con.isGlobal() && currmd.isGlobal())
          continue;

        /* Method okay so far */
        if (bestmd == null)
          bestmd = currmd;
        else {
          if (typeutil.isMoreSpecific(currmd, bestmd)) {
            bestmd = currmd;
          } else if (con.isGlobal() && match(currmd, bestmd)) {
            if (currmd.isGlobal() && !bestmd.isGlobal())
              bestmd = currmd;
            else if (currmd.isGlobal() && bestmd.isGlobal())
              throw new Error();
          } else if (!typeutil.isMoreSpecific(bestmd, currmd)) {
            throw new Error("No method is most specific:" + bestmd + " and " + currmd);
          }

          /* Is this more specific than bestmd */
        }
      }
      if (bestmd == null) {
        throw new Error("No method found for " + con.printNode(0) + " in " + md);
      }
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



  ExpressionNode translateNameDescriptorintoExpression(NameDescriptor nd, int numLine) {
    String id=nd.getIdentifier();
    NameDescriptor base=nd.getBase();
    if (base==null) {
      NameNode nn=new NameNode(nd);
      nn.setNumLine(numLine);
      return nn;
    } else {
      FieldAccessNode fan=new FieldAccessNode(translateNameDescriptorintoExpression(base,numLine),id);
      fan.setNumLine(numLine);
      return fan;
    }
  }

  MethodDescriptor recurseSurroundingClassesM( ClassDescriptor icd, String varname ) {
      if( null == icd || false == icd.isInnerClass() )
	    return null;
    
      ClassDescriptor surroundingDesc = icd.getSurroundingDesc();
      if( null == surroundingDesc )
	    return null;
    
      SymbolTable methodTable = surroundingDesc.getMethodTable();
      MethodDescriptor md = ( MethodDescriptor ) methodTable.get( varname );
      if( null != md )
	    return md;
      return recurseSurroundingClassesM( surroundingDesc, varname );
  }

  ExpressionNode methodInvocationExpression( ClassDescriptor icd, MethodDescriptor md, int linenum ) {
	ClassDescriptor cd = md.getClassDesc();
	int depth = 1;
	int startingDepth = icd.getInnerDepth();

	if( true == cd.isInnerClass() ) 
		depth = cd.getInnerDepth();

	String composed = "this";
	NameDescriptor runningDesc = new NameDescriptor( "this" );;
	
	for ( int index = startingDepth; index > depth; --index ) {
		composed = "this$" + String.valueOf( index - 1  );	
		runningDesc = new NameDescriptor( runningDesc, composed );
	}
	if( false == cd.isInnerClass() )
		runningDesc = new NameDescriptor( runningDesc, "this$" + String.valueOf(0) ); //all the way up.

	return new NameNode(runningDesc);
}

  void checkMethodInvokeNode(Descriptor md, SymbolTable nametable, MethodInvokeNode min, TypeDescriptor td) {
    /*Typecheck subexpressions
       and get types for expressions*/

    boolean isstatic = false;
    if((md instanceof MethodDescriptor) && ((MethodDescriptor)md).isStatic()) {
      isstatic = true;
    }
    TypeDescriptor[] tdarray=new TypeDescriptor[min.numArgs()];
    for(int i=0; i<min.numArgs(); i++) {
      ExpressionNode en=min.getArg(i);
      checkExpressionNode(md,nametable,en,null);
      tdarray[i]=en.getType();

      if(en.getType().isClass() && en.getType().getClassDesc().isEnum()) {
        tdarray[i] = new TypeDescriptor(TypeDescriptor.INT);
      }
    }
    TypeDescriptor typetolookin=null;
    if (min.getExpression()!=null) {
      checkExpressionNode(md,nametable,min.getExpression(),null);
      typetolookin=min.getExpression().getType();
    } else if (min.getBaseName()!=null) {
      String rootname=min.getBaseName().getRoot();
      if (rootname.equals("super")) {
        ClassDescriptor supercd=((MethodDescriptor)md).getClassDesc().getSuperDesc();
        typetolookin=new TypeDescriptor(supercd);
        min.setSuper();
      } else if (rootname.equals("this")) {
        if(isstatic) {
          throw new Error("use this object in static method md = "+ md.toString());
        }
        ClassDescriptor cd=((MethodDescriptor)md).getClassDesc();
        typetolookin=new TypeDescriptor(cd);
      } else if (nametable.get(rootname)!=null) {
        //we have an expression
        min.setExpression(translateNameDescriptorintoExpression(min.getBaseName(),min.getNumLine()));
        checkExpressionNode(md, nametable, min.getExpression(), null);
        typetolookin=min.getExpression().getType();
      } else {
        if(!min.getBaseName().getSymbol().equals("System.out")||state.JNI) {
          ExpressionNode nn = translateNameDescriptorintoExpression(min.getBaseName(),min.getNumLine());
          checkExpressionNode(md, nametable, nn, null);
          typetolookin = nn.getType();
          if(!((nn.kind()== Kind.NameNode) && (((NameNode)nn).getField() == null)
               && (((NameNode)nn).getVar() == null) && (((NameNode)nn).getExpression() == null))) {
            // this is not a pure class name, need to add to
            min.setExpression(nn);
          }
        } else {
          //we have a type
          ClassDescriptor cd = getClass(null, "System");

          if (cd==null)
            throw new Error("md = "+ md.toString()+ "  "+min.getBaseName()+" undefined");
          typetolookin=new TypeDescriptor(cd);
        }
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
      throw new Error("Error with method call to "+min.getMethodName()+" in class "+typetolookin);
    ClassDescriptor classtolookin=typetolookin.getClassDesc();
    checkClass(classtolookin, REFERENCE);
    Set methoddescriptorset=classtolookin.getMethodTable().getSet(min.getMethodName());
    MethodDescriptor bestmd=null;
NextMethod:
    for(Iterator methodit=methoddescriptorset.iterator(); methodit.hasNext(); ) {
      MethodDescriptor currmd=(MethodDescriptor)methodit.next();
      /* Need correct number of parameters */
      if (min.numArgs()!=currmd.numParameters())
        continue;
      for(int i=0; i<min.numArgs(); i++) {
        if (!typeutil.isSuperorType(currmd.getParamType(i),tdarray[i]))
          if(((!tdarray[i].isArray() &&(tdarray[i].isInt() || tdarray[i].isLong()))
              && currmd.getParamType(i).isClass() && currmd.getParamType(i).getClassDesc().getSymbol().equals("Object"))) {
            // primitive parameters vs object
          } else {
            continue NextMethod;
          }
      }
      /* Method okay so far */
      if (bestmd==null)
        bestmd=currmd;
      else {
        if (typeutil.isMoreSpecific(currmd,bestmd)) {
          bestmd=currmd;
        } else if (!typeutil.isMoreSpecific(bestmd, currmd))
          throw new Error("No method is most specific:"+bestmd+" and "+currmd);

        /* Is this more specific than bestmd */
      }
    }
    if (bestmd==null) {
      // if this is an inner class, need to check the method table for the surrounding class
      bestmd = recurseSurroundingClassesM(classtolookin, min.getMethodName());
      if(bestmd == null)
	  throw new Error("No method found for :"+min.printNode(0)+" in class: " + classtolookin+" in "+md);
      else {
	  // set the correct "this" expression here
	  ExpressionNode en=methodInvocationExpression(classtolookin, bestmd, min.getNumLine());
	  min.setExpression(en);
	  checkExpressionNode(md, nametable, min.getExpression(), null);
      }
    }
    min.setMethod(bestmd);

    if ((td!=null)&&(min.getType()!=null)&&!typeutil.isSuperorType(td,  min.getType()))
      throw new Error(min.getType()+ " is not equal to or a subclass of "+td);
    /* Check whether we need to set this parameter to implied this */
    if (!isstatic && !bestmd.isStatic()) {
      if (min.getExpression()==null) {
        ExpressionNode en=new NameNode(new NameDescriptor("this"));
        min.setExpression(en);
        checkExpressionNode(md, nametable, min.getExpression(), null);
      }
    }

    /* Check if we need to wrap primitive paratmeters to objects */
    for(int i=0; i<min.numArgs(); i++) {
      if(!tdarray[i].isArray() && (tdarray[i].isInt() || tdarray[i].isLong())
         && min.getMethod().getParamType(i).isClass() && min.getMethod().getParamType(i).getClassDesc().getSymbol().equals("Object")) {
        // Shall wrap this primitive parameter as a object
        ExpressionNode exp = min.getArg(i);
        TypeDescriptor ptd = null;
        NameDescriptor nd=null;
        if(exp.getType().isInt()) {
          nd = new NameDescriptor("Integer");
          ptd = state.getTypeDescriptor(nd);
        } else if(exp.getType().isLong()) {
          nd = new NameDescriptor("Long");
          ptd = state.getTypeDescriptor(nd);
        }
        boolean isglobal = false;
        String disjointId = null;
        CreateObjectNode con=new CreateObjectNode(ptd, isglobal, disjointId);
        con.addArgument(exp);
        checkExpressionNode(md, nametable, con, null);
        min.setArgument(con, i);
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
        if (!(ltd.isPtr()&&rtd.isPtr())) {
          if(!rtd.isEnum()) {
            throw new Error();
          }
        }
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
      if (!ltd.isNumber()||!rtd.isNumber()) {
        if (!ltd.isNumber())
          throw new Error("Leftside is not number"+on.printNode(0)+"type="+ltd.toPrettyString());
        if (!rtd.isNumber())
          throw new Error("Rightside is not number"+on.printNode(0));
      }

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
        ClassDescriptor stringcl=getClass(null, TypeUtil.StringClass);
        TypeDescriptor stringtd=new TypeDescriptor(stringcl);
        NameDescriptor nd=new NameDescriptor("String");
        NameDescriptor valuend=new NameDescriptor(nd, "valueOf");
        if (!(ltd.isString()&&(on.getLeft() instanceof OpNode))) {
          MethodInvokeNode leftmin=new MethodInvokeNode(valuend);
          leftmin.setNumLine(on.getLeft().getNumLine());
          leftmin.addArgument(on.getLeft());
          on.left=leftmin;
          checkExpressionNode(md, nametable, on.getLeft(), null);
        }

        if (!(rtd.isString()&&(on.getRight() instanceof OpNode))) {
          MethodInvokeNode rightmin=new MethodInvokeNode(valuend);
          rightmin.setNumLine(on.getRight().getNumLine());
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
        throw new Error("Type of rside not compatible with type of lside"+on.printNode(0));
      }
  }

}
