package Util;

/* Edge *****************/

public class Edge {
    protected GraphNode target;
    protected GraphNode source;

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
    
    public static final EdgeStatus UNVISITED = new EdgeStatus("UNVISITED");
    public static final EdgeStatus PROCESSING = new EdgeStatus("PROCESSING");
    public static final EdgeStatus FINISHED = new EdgeStatus("FINISHED");
    
    public static class EdgeStatus {
        private static String name;
        private EdgeStatus(String name) { this.name = name; }
        public String toString() { return name; }
    }
    
    int discoverytime = -1;
    int finishingtime = -1; /* used for searches */
    EdgeStatus status = UNVISITED;
    
    void reset() {
	    discoverytime = -1;
	    finishingtime = -1;
	    status = UNVISITED;
    }
    
    void discover(int time) {
    	discoverytime = time;
    	status = PROCESSING;
    }

    void finish(int time) {
        assert status == PROCESSING;
    	finishingtime = time;
        status = FINISHED;
    }
}
