package Analysis.TaskStateAnalysis;
import IR.*;
import Util.Namer;
import java.util.*;
import Util.GraphNode;
import Util.Edge;

public class GarbageAnalysis extends Namer {
  State state;
  TaskAnalysis taskanalysis;
  HashSet garbagestates;
  HashSet possiblegarbagestates;


  public GarbageAnalysis(State state, TaskAnalysis taskanalysis) {
    this.state=state;
    this.taskanalysis=taskanalysis;
    this.garbagestates=new HashSet();
    this.possiblegarbagestates=new HashSet();
    doAnalysis();
  }

  public void doAnalysis() {
    for(Iterator it=state.getClassSymbolTable().getDescriptorsIterator(); it.hasNext();) {
      ClassDescriptor cd=(ClassDescriptor) it.next();
      if (taskanalysis.getFlagStates(cd)==null)
	continue;
      analyzeClass(cd);
    }
  }

  public void analyzeClass(ClassDescriptor cd) {
    Set flagstatenodes=taskanalysis.getFlagStates(cd);
    HashSet garbage=new HashSet();
    HashSet possiblegarbage=new HashSet();

    for(Iterator fsit=flagstatenodes.iterator(); fsit.hasNext();) {
      FlagState fs=(FlagState)fsit.next();
      if (fs.numedges()==0)
	garbage.add(fs);
    }

    Stack tovisit=new Stack();
    tovisit.addAll(garbage);
    possiblegarbage.addAll(garbage);
    while(!tovisit.isEmpty()) {
      FlagState fs=(FlagState)tovisit.pop();
      for(int i=0; i<fs.numinedges(); i++) {
	Edge e=fs.getinedge(i);
	FlagState fsnew=(FlagState) e.getSource();
	if (!possiblegarbage.contains(fsnew)) {
	  possiblegarbage.add(fsnew);
	  tovisit.push(fsnew);
	}
      }
    }
    garbagestates.addAll(garbage);
    possiblegarbagestates.addAll(possiblegarbage);
  }

  public String nodeLabel(GraphNode gn) {
    return "";
  }

  public String nodeOption(GraphNode gn) {
    if (garbagestates.contains(gn)) {
      return "color=green";
    } else if (possiblegarbagestates.contains(gn)) {
      return "color=blue";
    } else
      return "color=red";
  }

  public String edgeLabel(Edge e) {
    return "";
  }

  public String edgeOption(Edge e) {
    return "";
  }
}
