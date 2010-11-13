package IR.Flat;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import java.util.Iterator;
import java.util.Collection;

import Analysis.MLP.SESEEffectsKey;
import Analysis.MLP.SESEEffectsSet;
import Analysis.MLP.SESEandAgePair;
import Analysis.MLP.VariableSourceToken;
import Analysis.OwnershipAnalysis.HeapRegionNode;
import IR.ClassDescriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.TypeDescriptor;
import IR.Tree.SESENode;

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

  protected static final int ISLEAF_UNINIT = 1;
  protected static final int ISLEAF_FALSE  = 2;
  protected static final int ISLEAF_TRUE   = 3;
  protected int isLeafSESE;

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
  
  private SESEEffectsSet seseEffectsSet;

  // a subset of the in-set variables that shouuld be traversed during
  // the dynamic coarse grained conflict strategy, remember them here so
  // buildcode can be dumb and just gen the traversals
  protected Vector<TempDescriptor> inVarsForDynamicCoarseConflictResolution;

  // scope info for this SESE
  protected FlatMethod       fmEnclosing;
  protected MethodDescriptor mdEnclosing;
  protected ClassDescriptor  cdEnclosing;

  // structures that allow SESE to appear as
  // a normal method to code generation
  protected FlatMethod       fmBogus;
  protected MethodDescriptor mdBogus;

  // used during code generation to calculate an offset
  // into the SESE-specific record, specifically to the
  // first field in a sequence of pointers to other SESE
  // records which is relevant to garbage collection
  protected String firstDepRecField;
  protected int    numDepRecs;
  
  // a set of sese located at the first in transitive call chain 
  // starting from the current sese 
  protected Set<FlatSESEEnterNode> seseChildren;

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
    seseChildren         = new HashSet<FlatSESEEnterNode>();

    inVarsForDynamicCoarseConflictResolution = new Vector<TempDescriptor>();
    
    staticInVar2src = new Hashtable<TempDescriptor, VariableSourceToken>();
    
    seseEffectsSet = new SESEEffectsSet();

    fmEnclosing = null;
    mdEnclosing = null;
    cdEnclosing = null;

    isCallerSESEplaceholder = false;

    isLeafSESE = ISLEAF_UNINIT;

    firstDepRecField = null;
    numDepRecs       = 0;
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
  
  public String toPrettyString() {
    return "sese "+getPrettyIdentifier()+getIdentifier();
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
    if (!inVars.contains(td))
      inVars.add( td );
  }

  public void addOutVar( TempDescriptor td ) {
    outVars.add( td );
  }

  public void addInVarSet( Set<TempDescriptor> s ) {
    inVars.addAll(s);
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

  public void addSESEChildren(FlatSESEEnterNode child){
    seseChildren.add(child);
  }
  
  public void addSESEChildren(Set<FlatSESEEnterNode> children){
    seseChildren.addAll(children);
  }
  
  public Set<FlatSESEEnterNode> getSESEChildren(){
    return seseChildren;
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
  
  public void writeEffects(TempDescriptor td, String fd, TypeDescriptor type, HeapRegionNode hrn, boolean strongUpdate){
	  seseEffectsSet.addWritingVar(td, new SESEEffectsKey(fd, type, hrn.getID(), hrn.getGloballyUniqueIdentifier()));
	  if(strongUpdate){
		  seseEffectsSet.addStrongUpdateVar(td, new SESEEffectsKey(fd, type, hrn.getID(), hrn.getGloballyUniqueIdentifier()));
	  }
  }
  
  public void readEffects(TempDescriptor td, String fd, TypeDescriptor type, HeapRegionNode hrn ){
	  seseEffectsSet.addReadingVar(td, new SESEEffectsKey(fd, type, hrn.getID(), hrn.getGloballyUniqueIdentifier()));
  }
  
  public SESEEffectsSet getSeseEffectsSet(){
	  return seseEffectsSet;
  }


  public void setFirstDepRecField( String field ) {
    firstDepRecField = field;
  }

  public String getFirstDepRecField() {
    return firstDepRecField;
  }

  public void incNumDepRecs() {
    ++numDepRecs;
  }

  public int getNumDepRecs() {
    return numDepRecs;
  }
  
  public Vector<TempDescriptor> getInVarsForDynamicCoarseConflictResolution() {
    return inVarsForDynamicCoarseConflictResolution;
  }
  
  public void addInVarForDynamicCoarseConflictResolution(TempDescriptor inVar) {
    if (!inVarsForDynamicCoarseConflictResolution.contains(inVar))
      inVarsForDynamicCoarseConflictResolution.add(inVar);
  }
  
  public void setIsLeafSESE( boolean isLeaf ) {
    if( isLeaf ) {
      isLeafSESE = ISLEAF_TRUE;
    } else {
      isLeafSESE = ISLEAF_FALSE;
    }
  }

  public boolean getIsLeafSESE() {
    if( isLeafSESE == ISLEAF_UNINIT ) {
      throw new Error( "isLeafSESE uninitialized" );
    }

    return isLeafSESE == ISLEAF_TRUE;
  }
}
