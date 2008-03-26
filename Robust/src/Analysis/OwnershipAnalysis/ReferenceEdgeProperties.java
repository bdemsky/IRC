package Analysis.OwnershipAnalysis;

public class ReferenceEdgeProperties {

    public ReferenceEdgeProperties( boolean isUnique ) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = false;
    }

    public ReferenceEdgeProperties( boolean isUnique,
				    boolean isInitialParamReflexive ) {
	this.isUnique                = isUnique;
	this.isInitialParamReflexive = isInitialParamReflexive;
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


    public boolean equals( ReferenceEdgeProperties rep ) {
	return isUnique                == rep.isUnique()                &&
	       isInitialParamReflexive == rep.isInitialParamReflexive();
    }
}
