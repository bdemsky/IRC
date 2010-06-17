package Analysis.OoOJava;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Map.Entry;
import Analysis.Liveness;
import Analysis.ArrayReferencees;
import Analysis.CallGraph.CallGraph;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Operation;
import IR.State;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.Flat.FKind;
import IR.Flat.FlatCall;
import IR.Flat.FlatCondBranch;
import IR.Flat.FlatEdge;
import IR.Flat.FlatElementNode;
import IR.Flat.FlatFieldNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNew;
import IR.Flat.FlatNode;
import IR.Flat.FlatOpNode;
import IR.Flat.FlatReturnNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.FlatSESEExitNode;
import IR.Flat.FlatSetElementNode;
import IR.Flat.FlatSetFieldNode;
import IR.Flat.FlatWriteDynamicVarNode;
import IR.Flat.TempDescriptor;


public class OoOJavaAnalysis {

  // data from the compiler
  private State             state;
  private TypeUtil          typeUtil;
  private CallGraph         callGraph;



  public OoOJavaAnalysis( State            state,
                          TypeUtil         tu,
                          CallGraph        callGraph,
                          Liveness         liveness,
                          ArrayReferencees arrayReferencees
                          ) {
  }
}
