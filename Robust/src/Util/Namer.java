package Util;

public class Namer {
    public Namer() {}

    public String nodeLabel(GraphNode gn) {
	return gn.getTextLabel();
    }
    
    public String nodeOption(GraphNode gn) {
	return gn.dotnodeparams;
    }

    public String edgeLabel(Edge e) {
	return e.getLabel();
    }

    public String edgeOption(Edge e) {
	return e.dotnodeparams;
    }
}
