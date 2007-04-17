package Analysis.TaskStateAnalysis;
import IR.*;
import Analysis.TaskStateAnalysis.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;


/* Edge *****************/

public static class Edge {

    private String label;
    private FlagState target;
    private FlagState source;
    private String dotnodeparams = new String();
    
    public Edge(FlagState target, String label) {
	this.label = label;
	this.target = target;
    }
    
    public String getLabel() {
	return label;
    }
    
    public void setSource(FlagState s) {
	this.source=s;
    }
    
    public FlagState getSource() {
	return source;
    }
    
    public FlagState getTarget() {
	return target;
    }
    
    public void setDotNodeParameters(String param) {
	if (param == null) {
	    throw new NullPointerException();
	}
	if (param.length() > 0) {
	    dotnodeparams = "," + param;
	} else {
	    dotnodeparams = new String();
	}
    }
}
