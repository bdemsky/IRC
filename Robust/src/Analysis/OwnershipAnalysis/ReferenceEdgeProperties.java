package Analysis.OwnershipAnalysis;

public class ReferenceEdgeProperties {

    public ReferenceEdgeProperties( boolean isUnique ) {
	this.isUnique = isUnique;
    }

    public ReferenceEdgeProperties copy() {
	return new ReferenceEdgeProperties( isUnique );
    }


    /////////////////
    // equality  
    /////////////////
    protected boolean isUnique;

    public boolean isUnique() {
	return isUnique;
    }

    public boolean equals( ReferenceEdgeProperties rep ) {
	return isUnique == rep.isUnique();
    }
    /////////////////
    // end equality  
    /////////////////


    public void setIsUnique( boolean isUnique ) {
	this.isUnique = isUnique;
    }
}
