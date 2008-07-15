package Analysis.OwnershipAnalysis;

public class ReferenceEdgeProperties {

    protected boolean isUnique;
    protected boolean isInitialParamReflexive;

    protected ReachabilitySet beta;
    protected ReachabilitySet betaNew;

    protected OwnershipNode  src;
    protected HeapRegionNode dst;

    public ReferenceEdgeProperties() {
	this( false, false, null );
    }    

    public ReferenceEdgeProperties( boolean isUnique ) {
	this( isUnique, false, null );
    }

    public ReferenceEdgeProperties( boolean isUnique,
				    boolean isInitialParamReflexive ) {
	this( isUnique, isInitialParamReflexive, null );
    }

    public ReferenceEdgeProperties( boolean         isUnique,
				    boolean         isInitialParamReflexive,
				    ReachabilitySet beta) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = isInitialParamReflexive;

	// these members are set by higher-level code
	// when this ReferenceEdgeProperties object is
	// applied to an edge
	this.src = null;
	this.dst = null;

	if( beta != null ) {
	    this.beta = beta;
	} else {
	    this.beta = new ReachabilitySet();
	    this.beta = this.beta.makeCanonical();
	}

	betaNew = new ReachabilitySet();
	betaNew = betaNew.makeCanonical();
    }


    public OwnershipNode getSrc() {
	return src;
    }

    public void setSrc( OwnershipNode on ) {
	assert on != null;
	src = on;
    }

    public HeapRegionNode getDst() {
	return dst;
    }

    public void setDst( HeapRegionNode hrn ) {
	assert hrn != null;
	dst = hrn;
    }


    // copying does not copy source and destination members!
    public ReferenceEdgeProperties copy() {
	return new ReferenceEdgeProperties( isUnique,
					    isInitialParamReflexive,
					    beta );
    }



    public boolean isUnique() {
	return isUnique;
    }
    public void setIsUnique( boolean isUnique ) {
	this.isUnique = isUnique;
    }



    public boolean isInitialParamReflexive() {
	return isInitialParamReflexive;
    }
    public void setIsInitialParamReflexive( boolean isInitialParamReflexive ) {
	this.isInitialParamReflexive = isInitialParamReflexive;
    }



    public ReachabilitySet getBeta() {
	return beta;
    }

    public void setBeta( ReachabilitySet beta ) {
	assert beta != null;
	this.beta = beta;
    }

    public ReachabilitySet getBetaNew() {
	return betaNew;
    }

    public void setBetaNew( ReachabilitySet beta ) {
	assert beta != null;
	this.betaNew = beta;
    }

    public void applyBetaNew() {
	assert betaNew != null;

	beta = betaNew;

	betaNew = new ReachabilitySet();
	betaNew = betaNew.makeCanonical();
    }


    public boolean equals( ReferenceEdgeProperties rep ) {
	assert rep != null;
	
	return isUnique                == rep.isUnique()                &&
	       isInitialParamReflexive == rep.isInitialParamReflexive();
    }

    public String getBetaString() {
	return beta.toStringEscapeNewline();
    }
    
    public String toEdgeLabelString() {
	String edgeLabel = "";
	/*
	if( rep.isUnique() ) {
	  edgeLabel += "Unq";
	}
	*/
	if( isInitialParamReflexive ) {
	    edgeLabel += "Rflx\\n";
	}
	edgeLabel += getBetaString();
	return edgeLabel;
    }
}
