package Analysis.OwnershipAnalysis;

public class ReferenceEdgeProperties {

    protected boolean isUnique;
    protected boolean isInitialParamReflexive;

    protected ReachabilitySet beta;
    protected ReachabilitySet betaNew;


    public ReferenceEdgeProperties() {
	this.isUnique                = false;
	this.isInitialParamReflexive = false;
	this.beta                    = new ReachabilitySet();
	this.betaNew                 = null;
    }    

    public ReferenceEdgeProperties( boolean isUnique ) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = false;
	this.beta                    = new ReachabilitySet();
	this.betaNew                 = null;
    }

    public ReferenceEdgeProperties( boolean isUnique,
				    boolean isInitialParamReflexive ) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = isInitialParamReflexive;
	this.beta                    = new ReachabilitySet();
	this.betaNew                 = null;
    }

    public ReferenceEdgeProperties( boolean         isUnique,
				    boolean         isInitialParamReflexive,
				    ReachabilitySet beta) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = isInitialParamReflexive;
	this.beta                    = beta;
	this.betaNew                 = null;
    }


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
	this.beta = beta;
    }

    public ReachabilitySet getBetaNew() {
	return betaNew;
    }
    public void setBetaNew( ReachabilitySet beta ) {
	this.betaNew = beta;
    }
    public void applyBetaNew() {
	assert betaNew != null;
	beta    = betaNew;
	betaNew = null;
    }


    public boolean equals( ReferenceEdgeProperties rep ) {
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
