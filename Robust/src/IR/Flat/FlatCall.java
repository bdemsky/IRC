package IR.Flat;
import IR.MethodDescriptor;

public class FlatCall extends FlatNode {
  TempDescriptor args[];
  TempDescriptor this_temp;
  TempDescriptor dst;
  MethodDescriptor method;
  boolean isSuper;

  public FlatCall(MethodDescriptor md, TempDescriptor dst, TempDescriptor this_temp, TempDescriptor[] args) {
    this(md, dst, this_temp, args, false);
  }

  public FlatCall(MethodDescriptor md, TempDescriptor dst, TempDescriptor this_temp, TempDescriptor[] args, boolean isSuper) {
    this.method=md;
    this.dst=dst;
    this.this_temp=this_temp;
    this.args=args;
    this.isSuper=isSuper;
  }
  public void rewriteUse(TempMap t) {
    for(int i=0; i<args.length; i++)
      args[i]=t.tempMap(args[i]);
    this_temp=t.tempMap(this_temp);
  }
  public void rewriteDef(TempMap t) {
    dst=t.tempMap(dst);
  }
  public FlatNode clone(TempMap t) {
    TempDescriptor ndst=t.tempMap(dst);
    TempDescriptor nthis=t.tempMap(this_temp);
    TempDescriptor[] nargs=new TempDescriptor[args.length];
    for(int i=0; i<nargs.length; i++)
      nargs[i]=t.tempMap(args[i]);

    return new FlatCall(method, ndst, nthis, nargs);
  }
  public boolean getSuper() {
    return isSuper;
  }

  public MethodDescriptor getMethod() {
    return method;
  }

  public TempDescriptor getThis() {
    return this_temp;
  }

  public TempDescriptor getReturnTemp() {
    return dst;
  }

  public int numArgs() {
    return args.length;
  }

  public TempDescriptor getArg(int i) {
    return args[i];
  }

  public TempDescriptor getArgMatchingParamIndex(FlatMethod fm, int i) {
    // in non-static methods the "this" pointer
    // affects the matching index
    if( method.isStatic() ) {
      assert numArgs()   == fm.numParameters();
    } else {
      assert numArgs()+1 == fm.numParameters();
    }

    if( method.isStatic() ) {
      return args[i];
    }

    if( i == 0 ) {
      return this_temp;
    }

    return args[i-1];
  }

  // return the temp for the argument in caller that
  // becomes the given parameter
  public TempDescriptor getArgMatchingParam(FlatMethod fm,
                                            TempDescriptor tdParam) {
    // in non-static methods the "this" pointer
    // affects the matching index
    if( method.isStatic() ) {
      assert numArgs()   == fm.numParameters();
    } else {
      assert numArgs()+1 == fm.numParameters();
    }

    for( int i = 0; i < fm.numParameters(); ++i ) {
      TempDescriptor tdParamI = fm.getParameter(i);

      if( tdParamI.equals(tdParam) ) {

        if( method.isStatic() ) {
          return args[i];
        }

        if( i == 0 ) {
          return this_temp;
        }

        return args[i-1];
      }
    }

    return null;
  }

  public String toString() {
    String st="FlatCall_";
    if (dst==null) {
      if (method==null)
        st+="null(";
      else
        st+=method.getSymbol()+"(";
    } else
      st+=dst+"="+method.getSymbol()+"(";
    if (this_temp!=null) {
      st+=this_temp;
      if (args.length!=0)
        st+=", ";
    }

    for(int i=0; i<args.length; i++) {
      st+=args[i].toString();
      if ((i+1)<args.length)
        st+=", ";
    }
    return st+")";
  }

  public int kind() {
    return FKind.FlatCall;
  }

  public TempDescriptor [] readsTemps() {
    int size=args.length;
    if (this_temp!=null)
      size++;
    TempDescriptor [] t=new TempDescriptor[size];
    int offset=0;
    if (this_temp!=null)
      t[offset++]=this_temp;
    for(int i=0; i<args.length; i++)
      t[offset++]=args[i];
    return t;
  }

  public TempDescriptor [] writesTemps() {
    if (dst!=null)
      return new TempDescriptor[] {dst};
    else
      return new TempDescriptor[0];
  }
}
