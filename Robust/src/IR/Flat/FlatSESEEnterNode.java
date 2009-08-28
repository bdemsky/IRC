package IR.Flat;
import Analysis.MLP.VariableSourceToken;
import Analysis.MLP.VarSrcTokTable;
import Analysis.MLP.SESEandAgePair;
import IR.MethodDescriptor;
import IR.ClassDescriptor;
import IR.TypeDescriptor;
import IR.Tree.SESENode;
import java.util.*;

public class FlatSESEEnterNode extends FlatNode {
  
  // SESE class identifiers should be numbered
  // sequentially from 0 to 1-(total # SESE's)
  private static int identifier=0;

  private   int               id;
  protected FlatSESEExitNode  exit;
  protected SESENode          treeNode;
  protected FlatSESEEnterNode parent;
  protected Integer           oldestAgeToTrack;
  protected boolean           isCallerSESEplaceholder;

  protected Set<FlatSESEEnterNode> children;

  protected Set<TempDescriptor> inVars;
  protected Set<TempDescriptor> outVars;

  protected Set<SESEandAgePair> needStaticNameInCode;

  protected Set<SESEandAgePair> staticInVarSrcs;

  protected Set<TempDescriptor> readyInVars;
  protected Set<TempDescriptor> staticInVars;
  protected Set<TempDescriptor> dynamicInVars;  

  protected Set<TempDescriptor> dynamicVars;

  protected Hashtable<TempDescriptor, VariableSourceToken> staticInVar2src;
  

  // scope info for this SESE
  protected FlatMethod       fmEnclosing;
  protected MethodDescriptor mdEnclosing;
  protected ClassDescriptor  cdEnclosing;

  // structures that allow SESE to appear as
  // a normal method to code generation
  protected FlatMethod       fmBogus;
  protected MethodDescriptor mdBogus;


  public FlatSESEEnterNode( SESENode sn ) {
    this.id              = identifier++;
    treeNode             = sn;
    parent               = null;
    oldestAgeToTrack     = new Integer( 0 );

    children             = new HashSet<FlatSESEEnterNode>();
    inVars               = new HashSet<TempDescriptor>();
    outVars              = new HashSet<TempDescriptor>();
    needStaticNameInCode = new HashSet<SESEandAgePair>();
    staticInVarSrcs      = new HashSet<SESEandAgePair>();
    readyInVars          = new HashSet<TempDescriptor>();
    staticInVars         = new HashSet<TempDescriptor>();
    dynamicInVars        = new HashSet<TempDescriptor>();
    dynamicVars          = new HashSet<TempDescriptor>();

    staticInVar2src = new Hashtable<TempDescriptor, VariableSourceToken>();

    fmEnclosing = null;
    mdEnclosing = null;
    cdEnclosing = null;

    isCallerSESEplaceholder = false;
  }

  public void rewriteUse() {
  }

  public void rewriteDef() {
  }

  public void setFlatExit( FlatSESEExitNode fsexn ) {
    exit = fsexn;
  }

  public FlatSESEExitNode getFlatExit() {
    return exit;
  }

  public int kind() {
    return FKind.FlatSESEEnterNode;
  }

  public SESENode getTreeNode() {
    return treeNode;
  }

  public int getIdentifier() {
    return id;
  }

  public String getPrettyIdentifier() {    
    if( treeNode.getID() != null ) {
      return treeNode.getID();
    }     
    return ""+id;
  }

  public String toString() {
    return "sese "+getPrettyIdentifier()+" enter";
  }

  public void setParent( FlatSESEEnterNode parent ) {
    this.parent = parent;
  }

  public FlatSESEEnterNode getParent() {
    return parent;
  }

  public void addChild( FlatSESEEnterNode child ) {
    children.add( child );
  }

  public Set<FlatSESEEnterNode> getChildren() {
    return children;
  }

  public void addInVar( TempDescriptor td ) {
    inVars.add( td );
  }

  public void addOutVar( TempDescriptor td ) {
    outVars.add( td );
  }

  public void addInVarSet( Set<TempDescriptor> s ) {
    inVars.addAll( s );
  }

  public void addOutVarSet( Set<TempDescriptor> s ) {
    outVars.addAll( s );
  }

  public Set<TempDescriptor> getInVarSet() {
    return inVars;
  }

  Vector<TempDescriptor> vecinVars;
  void buildvarVec() {
    HashSet<TempDescriptor> paramset=new HashSet<TempDescriptor>();
    paramset.addAll(inVars);
    paramset.addAll(outVars);
    vecinVars=new Vector<TempDescriptor>();
    vecinVars.addAll(paramset);
  }

  public TempDescriptor getParameter(int i) {
    if (vecinVars==null) {
      buildvarVec();
    }
    return vecinVars.get(i);
  }

  public int numParameters() {
    if (vecinVars==null) {
      buildvarVec();
    }
    return vecinVars.size();
  }

  public Set<FlatNode> getNodeSet() {
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    tovisit.add(this);
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);
      
      if (fn!=exit) {
	for(int i=0; i<fn.numNext(); i++) {
	  FlatNode nn=fn.getNext(i);
	  if (!visited.contains(nn))
	    tovisit.add(nn);
	}
      }
    }
    return visited;
  }

  public Set<TempDescriptor> getOutVarSet() {
    return outVars;
  }

  public void addNeededStaticName( SESEandAgePair p ) {
    needStaticNameInCode.add( p );
  }

  public Set<SESEandAgePair> getNeededStaticNames() {
    return needStaticNameInCode;
  }

  public void addStaticInVarSrc( SESEandAgePair p ) {
    staticInVarSrcs.add( p );
  }

  public Set<SESEandAgePair> getStaticInVarSrcs() {
    return staticInVarSrcs;
  }

  public void addReadyInVar( TempDescriptor td ) {
    readyInVars.add( td );
  }

  public Set<TempDescriptor> getReadyInVarSet() {
    return readyInVars;
  }

  public void addStaticInVar( TempDescriptor td ) {
    staticInVars.add( td );
  }

  public Set<TempDescriptor> getStaticInVarSet() {
    return staticInVars;
  }

  public void putStaticInVar2src( TempDescriptor staticInVar,
				  VariableSourceToken vst ) {
    staticInVar2src.put( staticInVar, vst );
  }

  public VariableSourceToken getStaticInVarSrc( TempDescriptor staticInVar ) {
    return staticInVar2src.get( staticInVar );
  }

  public void addDynamicInVar( TempDescriptor td ) {
    dynamicInVars.add( td );
  }

  public Set<TempDescriptor> getDynamicInVarSet() {
    return dynamicInVars;
  }

  public void addDynamicVar( TempDescriptor td ) {
    dynamicVars.add( td );
  }

  public Set<TempDescriptor> getDynamicVarSet() {
    return dynamicVars;
  }

  public void mustTrackAtLeastAge( Integer age ) {
    if( age > oldestAgeToTrack ) {
      oldestAgeToTrack = new Integer( age );
    }    
  }

  public Integer getOldestAgeToTrack() {
    return oldestAgeToTrack;
  }

  public void setfmEnclosing( FlatMethod fm ) { fmEnclosing = fm; }
  public FlatMethod getfmEnclosing() { return fmEnclosing; }

  public void setmdEnclosing( MethodDescriptor md ) { mdEnclosing = md; }
  public MethodDescriptor getmdEnclosing() { return mdEnclosing; }

  public void setcdEnclosing( ClassDescriptor cd ) { cdEnclosing = cd; }
  public ClassDescriptor getcdEnclosing() { return cdEnclosing; }

  public void setfmBogus( FlatMethod fm ) { fmBogus = fm; }
  public FlatMethod getfmBogus() { return fmBogus; }

  public void setmdBogus( MethodDescriptor md ) { mdBogus = md; }
  public MethodDescriptor getmdBogus() { return mdBogus; }

  public String getSESEmethodName() {
    assert cdEnclosing != null;
    assert mdBogus != null;

    return 
      cdEnclosing.getSafeSymbol()+
      mdBogus.getSafeSymbol()+
      "_"+
      mdBogus.getSafeMethodDescriptor();
  }

  public String getSESErecordName() {
    assert cdEnclosing != null;
    assert mdBogus != null;

    return
      "struct "+
      cdEnclosing.getSafeSymbol()+
      mdBogus.getSafeSymbol()+
      "_"+
      mdBogus.getSafeMethodDescriptor()+
      "_SESErec";
  }

  public void setCallerSESEplaceholder() {
    isCallerSESEplaceholder = true;
  }

  public boolean getIsCallerSESEplaceholder() {
    return isCallerSESEplaceholder;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof FlatSESEEnterNode) ) {
      return false;
    }

    FlatSESEEnterNode fsen = (FlatSESEEnterNode) o;
    return id == fsen.id;
  }

  public int hashCode() {
    return 31*id;
  }
}
