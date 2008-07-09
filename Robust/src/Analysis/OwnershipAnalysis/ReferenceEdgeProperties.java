package Analysis.OwnershipAnalysis;

public class ReferenceEdgeProperties {

    public ReferenceEdgeProperties() {
	this.isUnique                = false;
	this.isInitialParamReflexive = false;
	this.beta                    = new ReachabilitySet();
    }    

    public ReferenceEdgeProperties( boolean isUnique ) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = false;
	this.beta                    = new ReachabilitySet();
    }

    public ReferenceEdgeProperties( boolean isUnique,
				    boolean isInitialParamReflexive ) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = isInitialParamReflexive;
	this.beta                    = new ReachabilitySet();
    }

    public ReferenceEdgeProperties( boolean         isUnique,
				    boolean         isInitialParamReflexive,
				    ReachabilitySet beta) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = isInitialParamReflexive;
	this.beta                    = beta;
    }


    public ReferenceEdgeProperties copy() {
	return new ReferenceEdgeProperties( isUnique,
					    isInitialParamReflexive );
    }


    protected boolean isUnique;
    public boolean isUnique() {
	return isUnique;
    }
    public void setIsUnique( boolean isUnique ) {
	this.isUnique = isUnique;
    }


    protected boolean isInitialParamReflexive;
    public boolean isInitialParamReflexive() {
	return isInitialParamReflexive;
    }
    public void setIsInitialParamReflexive( boolean isInitialParamReflexive ) {
	this.isInitialParamReflexive = isInitialParamReflexive;
    }


    protected ReachabilitySet beta;
    public ReachabilitySet getBeta() {
	return beta;
    }
    public void setBeta( ReachabilitySet beta ) {
	this.beta = beta;
    }
    public String getBetaString() {
	return beta.toStringEscapeNewline();
    }


    public boolean equals( ReferenceEdgeProperties rep ) {
	return isUnique                == rep.isUnique()                &&
	       isInitialParamReflexive == rep.isInitialParamReflexive();
    }
}
