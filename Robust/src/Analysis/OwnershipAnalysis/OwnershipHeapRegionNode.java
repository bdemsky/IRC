package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipHeapRegionNode extends OwnershipNode {
    
    protected Vector<TempDescriptor> analysisRegionAliases;

    public OwnershipHeapRegionNode( Integer id ) {
	super( id );
	analysisRegionAliases = new Vector<TempDescriptor>();
    }

    public void addAnalysisRegionAlias( TempDescriptor td ) {
	analysisRegionAliases.add( td );
    }

    public Vector<TempDescriptor> getAnalysisRegionAliases() {
	return analysisRegionAliases;
    }

    public String toString() {
	return "OHRN"+getIDString();
    }
}
