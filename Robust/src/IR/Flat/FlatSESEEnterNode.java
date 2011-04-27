package IR.Flat;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import java.util.Iterator;
import java.util.Collection;
import Analysis.OoOJava.VariableSourceToken;
import Analysis.OoOJava.SESEandAgePair;
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

  private int id;
  protected FlatSESEExitNode exit;
  protected SESENode treeNode;

  // a leaf tasks simply has no children, ever
  protected static final int ISLEAF_UNINIT = 1;
  protected static final int ISLEAF_FALSE  = 2;
  protected static final int ISLEAF_TRUE   = 3;
  protected int isLeafSESE;

  // there is only one main sese that is implicit
  // (spliced in by the compiler around whole program)
  protected boolean isMainSESE;

  // this is a useful static name for whichever task
  // invoked the current local method context
  protected boolean isCallerProxySESE;

  // all children tasks, INCLUDING those that are reachable
  // by calling methods
  protected Set<FlatSESEEnterNode> children;

  // all possible parents
  protected Set<FlatSESEEnterNode> parents;

  // sometimes it is useful to know the locally defined
  // parent or children of an SESE for various analysis,
  // and by local it is one SESE nested within another
  // in a single method context
  protected Set<FlatSESEEnterNode> localChildren;
  protected FlatSESEEnterNode localParent;


  protected Set<TempDescriptor> inVars;
  protected Set<TempDescriptor> outVars;


  // for in-vars, classify them by source type to drive
  // code gen for issuing this task
  protected Set<TempDescriptor> readyInVars;
  protected Set<TempDescriptor> staticInVars;
  protected Set<TempDescriptor> dynamicInVars;
  protected Set<SESEandAgePair> staticInVarSrcs;
  protected Hashtable<TempDescriptor, VariableSourceToken> staticInVar2src;

  // for out-vars, classify them by source type to drive
  // code gen for when this task exits: if the exiting task
  // has to assume the values from any of its children, it needs
  // to know how to acquire those values before it can truly exit
  protected Set<TempDescriptor> readyOutVars;
  protected Set<TempDescriptor> staticOutVars;
  protected Set<TempDescriptor> dynamicOutVars;
  protected Set<SESEandAgePair> staticOutVarSrcs;
  protected Hashtable<TempDescriptor, VariableSourceToken> staticOutVar2src;



  // get the oldest age of this task that other contexts
  // have a static name for when tracking variables
  protected Integer oldestAgeToTrack;


  // a subset of the in-set variables that shouuld be traversed during
  // the dynamic coarse grained conflict strategy, remember them here so
  // buildcode can be dumb and just gen the traversals
  protected Vector<TempDescriptor> inVarsForDynamicCoarseConflictResolution;


  // scope info for this SESE
  protected FlatMethod fmEnclosing;
  protected MethodDescriptor mdEnclosing;
  protected ClassDescriptor cdEnclosing;

  // structures that allow SESE to appear as
  // a normal method to code generation
  protected FlatMethod fmBogus;
  protected MethodDescriptor mdBogus;

  // used during code generation to calculate an offset
  // into the SESE-specific record, specifically to the
  // first field in a sequence of pointers to other SESE
  // records which is relevant to garbage collection
  protected String firstDepRecField;
  protected int numDepRecs;


  public FlatSESEEnterNode(SESENode sn) {
    this.id              = identifier++;
    treeNode             = sn;
    children             = new HashSet<FlatSESEEnterNode>();
    parents              = new HashSet<FlatSESEEnterNode>();
    localChildren        = new HashSet<FlatSESEEnterNode>();
    localParent          = null;
    inVars               = new HashSet<TempDescriptor>();
    outVars              = new HashSet<TempDescriptor>();
    readyInVars          = new HashSet<TempDescriptor>();
    staticInVars         = new HashSet<TempDescriptor>();
    dynamicInVars        = new HashSet<TempDescriptor>();
    staticInVarSrcs      = new HashSet<SESEandAgePair>();
    readyOutVars         = new HashSet<TempDescriptor>();
    staticOutVars        = new HashSet<TempDescriptor>();
    dynamicOutVars       = new HashSet<TempDescriptor>();
    staticOutVarSrcs     = new HashSet<SESEandAgePair>();
    oldestAgeToTrack     = new Integer(0);

    staticInVar2src  = new Hashtable<TempDescriptor, VariableSourceToken>();
    staticOutVar2src = new Hashtable<TempDescriptor, VariableSourceToken>();

    inVarsForDynamicCoarseConflictResolution = new Vector<TempDescriptor>();


    fmEnclosing = null;
    mdEnclosing = null;
    cdEnclosing = null;

    isLeafSESE = ISLEAF_UNINIT;

    isMainSESE        = false;
    isCallerProxySESE = false;

    firstDepRecField = null;
    numDepRecs       = 0;
  }

  public void rewriteUse() {
  }

  public void rewriteDef() {
  }

  public void setFlatExit(FlatSESEExitNode fsexn) {
    exit = fsexn;
  }

  public FlatSESEExitNode getFlatExit() {
    return exit;
  }

  public void setIsMainSESE() {
    isMainSESE = true;
  }

  public boolean getIsMainSESE() {
    return isMainSESE;
  }

  public void setIsCallerProxySESE() {
    isCallerProxySESE = true;
  }

  public boolean getIsCallerProxySESE() {
    return isCallerProxySESE;
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
    if(isCallerProxySESE) {
      return "proxy";
    }
    if( treeNode != null && treeNode.getID() != null ) {
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


  public void mustTrackAtLeastAge(Integer age) {
    if( age > oldestAgeToTrack ) {
      oldestAgeToTrack = new Integer(age);
    }
  }

  public Integer getOldestAgeToTrack() {
    return oldestAgeToTrack;
  }


  public void addParent(FlatSESEEnterNode parent) {
    parents.add(parent);
  }

  public Set<FlatSESEEnterNode> getParents() {
    return parents;
  }

  public void setLocalParent(FlatSESEEnterNode parent) {
    localParent = parent;
  }

  public FlatSESEEnterNode getLocalParent() {
    return localParent;
  }

  public void addChild(FlatSESEEnterNode child) {
    children.add(child);
  }

  public void addChildren(Set<FlatSESEEnterNode> batch) {
    children.addAll(batch);
  }

  public Set<FlatSESEEnterNode> getChildren() {
    return children;
  }

  public void addLocalChild(FlatSESEEnterNode child) {
    localChildren.add(child);
  }

  public Set<FlatSESEEnterNode> getLocalChildren() {
    return localChildren;
  }



  public void addInVar(TempDescriptor td) {
    if (!inVars.contains(td))
      inVars.add(td);
  }

  public void addOutVar(TempDescriptor td) {
    outVars.add(td);
  }

  public void addInVarSet(Set<TempDescriptor> s) {
    inVars.addAll(s);
  }

  public void addOutVarSet(Set<TempDescriptor> s) {
    outVars.addAll(s);
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

  public void addStaticInVarSrc(SESEandAgePair p) {
    staticInVarSrcs.add(p);
  }

  public Set<SESEandAgePair> getStaticInVarSrcs() {
    return staticInVarSrcs;
  }

  public void addReadyInVar(TempDescriptor td) {
    readyInVars.add(td);
  }

  public Set<TempDescriptor> getReadyInVarSet() {
    return readyInVars;
  }

  public void addStaticInVar(TempDescriptor td) {
    staticInVars.add(td);
  }

  public Set<TempDescriptor> getStaticInVarSet() {
    return staticInVars;
  }

  public void putStaticInVar2src(TempDescriptor staticInVar,
                                 VariableSourceToken vst) {
    staticInVar2src.put(staticInVar, vst);
  }

  public VariableSourceToken getStaticInVarSrc(TempDescriptor staticInVar) {
    return staticInVar2src.get(staticInVar);
  }

  public void addDynamicInVar(TempDescriptor td) {
    dynamicInVars.add(td);
  }

  public Set<TempDescriptor> getDynamicInVarSet() {
    return dynamicInVars;
  }



  public void addReadyOutVar(TempDescriptor td) {
    readyOutVars.add(td);
  }

  public Set<TempDescriptor> getReadyOutVarSet() {
    return readyOutVars;
  }

  public void addStaticOutVarSrc(SESEandAgePair p) {
    staticOutVarSrcs.add(p);
  }

  public Set<SESEandAgePair> getStaticOutVarSrcs() {
    return staticOutVarSrcs;
  }

  public void addStaticOutVar(TempDescriptor td) {
    staticOutVars.add(td);
  }

  public Set<TempDescriptor> getStaticOutVarSet() {
    return staticOutVars;
  }

  public void putStaticOutVar2src(TempDescriptor staticOutVar,
                                  VariableSourceToken vst) {
    staticOutVar2src.put(staticOutVar, vst);
  }

  public VariableSourceToken getStaticOutVarSrc(TempDescriptor staticOutVar) {
    return staticOutVar2src.get(staticOutVar);
  }

  public void addDynamicOutVar(TempDescriptor td) {
    dynamicOutVars.add(td);
  }

  public Set<TempDescriptor> getDynamicOutVarSet() {
    return dynamicOutVars;
  }




  public void setfmEnclosing(FlatMethod fm) {
    fmEnclosing = fm;
  }
  public FlatMethod getfmEnclosing() {
    return fmEnclosing;
  }

  public void setmdEnclosing(MethodDescriptor md) {
    mdEnclosing = md;
  }
  public MethodDescriptor getmdEnclosing() {
    return mdEnclosing;
  }

  public void setcdEnclosing(ClassDescriptor cd) {
    cdEnclosing = cd;
  }
  public ClassDescriptor getcdEnclosing() {
    return cdEnclosing;
  }

  public void setfmBogus(FlatMethod fm) {
    fmBogus = fm;
  }
  public FlatMethod getfmBogus() {
    return fmBogus;
  }

  public void setmdBogus(MethodDescriptor md) {
    mdBogus = md;
  }
  public MethodDescriptor getmdBogus() {
    return mdBogus;
  }

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

  public boolean equals(Object o) {
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



  public void setFirstDepRecField(String field) {
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

  public void setIsLeafSESE(boolean isLeaf) {
    if( isLeaf ) {
      isLeafSESE = ISLEAF_TRUE;
    } else {
      isLeafSESE = ISLEAF_FALSE;
    }
  }

  public boolean getIsLeafSESE() {
    if( isLeafSESE == ISLEAF_UNINIT ) {
      throw new Error("isLeafSESE uninitialized");
    }

    return isLeafSESE == ISLEAF_TRUE;
  }

}
