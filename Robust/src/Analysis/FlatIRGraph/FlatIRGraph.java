package Analysis.FlatIRGraph;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class FlatIRGraph {

  private State state;

  private BufferedWriter flatbw;

  private HashSet<FlatNode> visited;
  private HashSet<FlatNode> toVisit;

  private int labelindex;
  private Hashtable<FlatNode, Integer> flatnodetolabel;

  public FlatIRGraph(State state, boolean tasks, boolean usermethods, boolean libmethods) throws java.io.IOException {
    this.state=state;

    if( tasks )
      graphTasks();

    if( usermethods || libmethods )
      graphMethods();
  }

  private void graphTasks() throws java.io.IOException {
    for(Iterator it_tasks=state.getTaskSymbolTable().getDescriptorsIterator(); it_tasks.hasNext(); ) {
      TaskDescriptor td = (TaskDescriptor)it_tasks.next();
      FlatMethod fm = state.getMethodFlat(td);
      writeFlatIRGraph(fm,"task"+td.getSymbol());
    }
  }

  private void graphMethods() throws java.io.IOException {
    for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext(); ) {
      ClassDescriptor cd = (ClassDescriptor)it_classes.next();
      for(Iterator it_methods=cd.getMethods(); it_methods.hasNext(); ) {
        MethodDescriptor md = (MethodDescriptor)it_methods.next();
        FlatMethod fm = state.getMethodFlat(md);
        writeFlatIRGraph(fm,cd.getSymbol()+"."+md.getSymbol());
      }
    }
  }
  
  public void writeFlatIRGraph(FlatMethod fm, String graphname) throws java.io.IOException {
    // give every node in the flat IR graph a unique label
    // so a human being can inspect the graph and verify
    // correctness
    flatnodetolabel=new Hashtable<FlatNode, Integer>();
    visited=new HashSet<FlatNode>();
    labelindex=0;
    labelFlatNodes(fm);

    // take symbols out of graphname that cause dot to fail
    graphname = graphname.replaceAll("[\\W]", "");

    flatbw=new BufferedWriter(new FileWriter(graphname+"_flatIRGraph.dot") );
    flatbw.write("digraph "+graphname+" {\n");

    visited=new HashSet<FlatNode>();
    toVisit=new HashSet<FlatNode>();
    toVisit.add(fm);

    while( !toVisit.isEmpty() ) {
      FlatNode fn=(FlatNode)toVisit.iterator().next();
      toVisit.remove(fn);
      visited.add(fn);

      if( fn.kind() == FKind.FlatMethod ) {
        // FlatMethod does not have toString
        flatbw.write(makeDotNodeDec(graphname, flatnodetolabel.get(fn), fn.getClass().getName(), "FlatMethod") );
      } else {
        flatbw.write(makeDotNodeDec(graphname, flatnodetolabel.get(fn), fn.getClass().getName(), fn.toString() ) );
      }

      for(int i=0; i<fn.numNext(); i++) {
        FlatNode nn=fn.getNext(i);
        flatbw.write("  node"+flatnodetolabel.get(fn)+" -> node"+flatnodetolabel.get(nn)+";\n");

        if( !visited.contains(nn) ) {
          toVisit.add(nn);
        }
      }
    }

    flatbw.write("}\n");
    flatbw.close();
  }

  private void labelFlatNodes(FlatNode fn) {
    visited.add(fn);
    flatnodetolabel.put(fn,new Integer(labelindex++));
    for(int i=0; i<fn.numNext(); i++) {
      FlatNode nn=fn.getNext(i);
      if(!visited.contains(nn)) {
        labelFlatNodes(nn);
      }
    }
  }

  private String makeNodeName(String graphname, Integer id, String type) {
    String s = String.format("%05d", id);
    return "FN"+s+"_"+type;
  }

  private String makeDotNodeDec(String graphname, Integer id, String type, String details) {
    if( details == null ) {
      return "  node"+id+"[label=\""+makeNodeName(graphname,id,type)+"\"];\n";
    } else {
      return "  node"+id+"[label=\""+makeNodeName(graphname,id,type)+"\\n"+details+"\"];\n";
    }
  }
}
