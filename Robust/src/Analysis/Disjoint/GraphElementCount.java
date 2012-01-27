package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;


public class GraphElementCount {

  private long numNodes;           
  private long numEdges;           
  private long numNodeStates;      
  private long numEdgeStates;      
  private long numNodeStateNonzero;
  private long numEdgeStateNonzero;

  public GraphElementCount() {
    numNodes = 0;           
    numEdges = 0;           
    numNodeStates = 0;      
    numEdgeStates = 0;      
    numNodeStateNonzero = 0;
    numEdgeStateNonzero = 0;
  }

  public void nodeInc( long amount ) {
    numNodes += amount;
  }

  public void edgeInc( long amount ) {
    numEdges += amount;
  }

  public void nodeStateInc( long amount ) {
    numNodeStates += amount;
  }

  public void edgeStateInc( long amount ) {
    numEdgeStates += amount;
  }

  public void nodeStateNonzeroInc( long amount ) {
    numNodeStateNonzero += amount;
  }

  public void edgeStateNonzeroInc( long amount ) {
    numEdgeStateNonzero += amount;
  }

  public String toString() {
    return 
      "################################################\n"+
      "Nodes                = "+numNodes+"\n"+
      "Edges                = "+numEdges+"\n"+
      "Node states          = "+numNodeStates+"\n"+
      "Edge states          = "+numEdgeStates+"\n"+
      "Node non-zero tuples = "+numNodeStateNonzero+"\n"+
      "Edge non-zero tuples = "+numEdgeStateNonzero+"\n"+
      "################################################\n";
  }
}
