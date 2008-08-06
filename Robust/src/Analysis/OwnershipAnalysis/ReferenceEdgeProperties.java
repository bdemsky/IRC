package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;


public class ReferenceEdgeProperties {

    // a null field descriptor means "any field"
    protected FieldDescriptor fieldDesc;

    protected boolean isInitialParamReflexive;

    protected ReachabilitySet beta;
    protected ReachabilitySet betaNew;

    protected OwnershipNode  src;
    protected HeapRegionNode dst;

    public ReferenceEdgeProperties() {
	this( null, false, null );
    }    

    public ReferenceEdgeProperties( FieldDescriptor fieldDesc, 
				    boolean         isInitialParamReflexive,
				    ReachabilitySet beta ) {

	this.fieldDesc               = fieldDesc;
	this.isInitialParamReflexive = isInitialParamReflexive;	

	if( beta != null ) {
	    this.beta = beta;
	} else {
	    this.beta = new ReachabilitySet();
	    this.beta = this.beta.makeCanonical();
	}

	// these members are set by higher-level code
	// when this ReferenceEdgeProperties object is
	// applied to an edge
	this.src = null;
	this.dst = null;

	// when edges are not undergoing a transitional operation
	// that is changing beta info, betaNew is always empty
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


    // copying does not copy source and destination members! or betaNew
    public ReferenceEdgeProperties copy() {
	return new ReferenceEdgeProperties( fieldDesc,
					    isInitialParamReflexive,
					    beta );
    }


    public FieldDescriptor getFieldDesc() {
	return fieldDesc;
    }

    public void setFieldDesc( FieldDescriptor fieldDesc ) {
	this.fieldDesc = fieldDesc;
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


    public boolean equals( Object o ) {
	if( o == null ) {
	    return false;
	}

	if( !(o instanceof ReferenceEdgeProperties) ) {
	    return false;
	}
	
	ReferenceEdgeProperties rep = (ReferenceEdgeProperties) o;

	// field descriptors maintain the invariant that they are reference comparable
	return fieldDesc               == rep.fieldDesc               &&
	       isInitialParamReflexive == rep.isInitialParamReflexive &&
	       beta.equals( rep.beta );
    }

    public int hashCode() {
	int hash = 0;
	if( fieldDesc != null ) {
	    hash += fieldDesc.getType().hashCode();
	}
	hash += beta.hashCode();
	return hash;
    }


    public String getBetaString() {
	return beta.toStringEscapeNewline();
    }
    
    public String toEdgeLabelString() {
	String edgeLabel = "";
	if( fieldDesc != null ) {
	    edgeLabel += fieldDesc.toStringBrief() + "\\n";
	}
	if( isInitialParamReflexive ) {
	    edgeLabel += "Rflx\\n";
	}
	edgeLabel += getBetaString();
	return edgeLabel;
    }
}
