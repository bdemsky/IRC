package Util;

/* Edge *****************/

public class Edge {
    protected GraphNode target;
    protected GraphNode source;
    protected boolean processed = false; 
    

    protected String dotnodeparams = new String();
    
    public Edge(GraphNode target) {
	this.target = target;
    }
    
    public String getLabel() {
	return "";
    }
    
    public void setSource(GraphNode s) {
	this.source=s;
    }
    
    public GraphNode getSource() {
	return source;
    }
    
    public GraphNode getTarget() {
	return target;
    }

    public void setProcessed() {
	processed = true;
    }
    
    public boolean isProcessed(){
	return processed;
    }
    
    public void setDotNodeParameters(String param) {
	if (param == null) {
	    throw new NullPointerException();
	}
        if (dotnodeparams.length() > 0) {
            dotnodeparams += "," + param;
        } else { 
            dotnodeparams = param;
        }
    }
}
