package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.SymbolTable;
import IR.VarDescriptor;
import IR.Tree.ArrayAccessNode;
import IR.Tree.AssignmentNode;
import IR.Tree.BlockExpressionNode;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.CastNode;
import IR.Tree.CreateObjectNode;
import IR.Tree.DeclarationNode;
import IR.Tree.ExpressionNode;
import IR.Tree.FieldAccessNode;
import IR.Tree.IfStatementNode;
import IR.Tree.Kind;
import IR.Tree.LiteralNode;
import IR.Tree.LoopNode;
import IR.Tree.MethodInvokeNode;
import IR.Tree.NameNode;
import IR.Tree.OpNode;
import IR.Tree.ReturnNode;
import IR.Tree.SubBlockNode;
import IR.Tree.SwitchBlockNode;
import IR.Tree.SwitchStatementNode;
import IR.Tree.TertiaryNode;

public class LocationInference {

  State state;
  SSJavaAnalysis ssjava;

  List<ClassDescriptor> toanalyzeList;
  List<MethodDescriptor> toanalyzeMethodList;

  boolean debug = true;

  InferGraph graph;

  public LocationInference(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.toanalyzeList = new ArrayList<ClassDescriptor>();
    this.toanalyzeMethodList = new ArrayList<MethodDescriptor>();
    this.graph = new InferGraph();
  }

  public void setupToAnalyze() {
    SymbolTable classtable = state.getClassSymbolTable();
    toanalyzeList.clear();
    toanalyzeList.addAll(classtable.getValueSet());
    Collections.sort(toanalyzeList, new Comparator<ClassDescriptor>() {
      public int compare(ClassDescriptor o1, ClassDescriptor o2) {
        return o1.getClassName().compareToIgnoreCase(o2.getClassName());
      }
    });
  }

  public void setupToAnalazeMethod(ClassDescriptor cd) {

    SymbolTable methodtable = cd.getMethodTable();
    toanalyzeMethodList.clear();
    toanalyzeMethodList.addAll(methodtable.getValueSet());
    Collections.sort(toanalyzeMethodList, new Comparator<MethodDescriptor>() {
      public int compare(MethodDescriptor o1, MethodDescriptor o2) {
        return o1.getSymbol().compareToIgnoreCase(o2.getSymbol());
      }
    });
  }

  public boolean toAnalyzeMethodIsEmpty() {
    return toanalyzeMethodList.isEmpty();
  }

  public boolean toAnalyzeIsEmpty() {
    return toanalyzeList.isEmpty();
  }

  public ClassDescriptor toAnalyzeNext() {
    return toanalyzeList.remove(0);
  }

  public MethodDescriptor toAnalyzeMethodNext() {
    return toanalyzeMethodList.remove(0);
  }

  private void checkDeclarationInClass(ClassDescriptor cd) {
    // Check to see that fields are okay
    for (Iterator field_it = cd.getFields(); field_it.hasNext();) {
      FieldDescriptor fd = (FieldDescriptor) field_it.next();

      if (!(fd.isFinal() && fd.isStatic())) {
        analyzeFieldDeclaration(cd, fd);
      } else {
        // for static final, assign top location by default
        graph.assignTopLocationToDescriptor(fd);
      }
    }
  }

  private void analyzeFieldDeclaration(ClassDescriptor cd, FieldDescriptor fd) {
    graph.assignUniqueIDtoDescriptor(fd);
  }

  public void inference() {

    // 1) assign a unique id to every field & variable
    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      System.out.println("SSJAVA: Location Inference on the class: " + cd);
      checkDeclarationInClass(cd);

      setupToAnalazeMethod(cd);
      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();

        if (ssjava.needTobeAnnotated(md)) {
          // assigns unique id to the method parameters
          assignUniqueIDMethodParamteres(cd, md);

          if (state.SSJAVADEBUG) {
            System.out.println("SSJAVA: Location Inference on the method: " + md);
          }
          assignUniqueIDMethodBody(cd, md);
        }
      }
    }

    // 2) construct value flow graph

    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);
      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        if (ssjava.needTobeAnnotated(md)) {
          if (state.SSJAVADEBUG) {
            System.out.println("SSJAVA: Constructing a flow graph: " + md);
          }
          analyzeMethodBody(cd, md);
        }
      }
    }

  }

  private void analyzeMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    // checkLocationFromBlockNode(md, md.getParameterTable(), bn, constraints);
  }

  private void assignUniqueIDMethodParamteres(ClassDescriptor cd, MethodDescriptor md) {

    List<CompositeLocation> paramList = new ArrayList<CompositeLocation>();
    for (int i = 0; i < md.numParameters(); i++) {
      // process annotations on method parameters
      VarDescriptor vd = (VarDescriptor) md.getParameter(i);
      graph.assignUniqueIDtoDescriptor(vd);
    }

  }

  private void assignUniqueIDMethodBody(ClassDescriptor cd, MethodDescriptor md) {
    BlockNode bn = state.getMethodBody(md);
    assignUniqueIDBlockNode(md, md.getParameterTable(), bn);
  }

  private void assignUniqueIDBlockNode(MethodDescriptor md, SymbolTable nametable, BlockNode bn) {

    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      assignUniqueIDBlockStatementNode(md, bn.getVarTable(), bsn);
    }

  }

  private void assignUniqueIDBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    switch (bsn.kind()) {

    case Kind.DeclarationNode:
      assignUniqueIDDeclarationNode(md, nametable, (DeclarationNode) bsn);
      break;

    case Kind.IfStatementNode:
      assignUniqueIDIfStatementNode(md, nametable, (IfStatementNode) bsn);
      break;

    case Kind.LoopNode:
      assignUniqueIDLoopNode(md, nametable, (LoopNode) bsn);
      break;

    case Kind.SubBlockNode:
      assignUniqueIDSubBlockNode(md, nametable, (SubBlockNode) bsn);
      break;

    case Kind.ContinueBreakNode:
      break;

    case Kind.SwitchStatementNode:
      assignUniqueIDSwitchStatementNode(md, nametable, (SwitchStatementNode) bsn);
    }

  }

  private void assignUniqueIDSwitchStatementNode(MethodDescriptor md, SymbolTable nametable,
      SwitchStatementNode ssn) {
    BlockNode sbn = ssn.getSwitchBody();
    for (int i = 0; i < sbn.size(); i++) {
      SwitchBlockNode node = (SwitchBlockNode) sbn.get(i);
      assignUniqueIDBlockNode(md, nametable, node.getSwitchBlockStatement());
    }
  }

  private void assignUniqueIDSubBlockNode(MethodDescriptor md, SymbolTable nametable,
      SubBlockNode sbn) {
    assignUniqueIDBlockNode(md, nametable, sbn.getBlockNode());
  }

  private void assignUniqueIDLoopNode(MethodDescriptor md, SymbolTable nametable, LoopNode ln) {

    if (ln.getType() == LoopNode.WHILELOOP || ln.getType() == LoopNode.DOWHILELOOP) {
      assignUniqueIDBlockNode(md, nametable, ln.getBody());
    } else {
      // check 'for loop' case
      BlockNode bn = ln.getInitializer();
      bn.getVarTable().setParent(nametable);
      assignUniqueIDBlockNode(md, bn.getVarTable(), ln.getUpdate());
      assignUniqueIDBlockNode(md, bn.getVarTable(), ln.getBody());
    }

  }

  private void assignUniqueIDIfStatementNode(MethodDescriptor md, SymbolTable nametable,
      IfStatementNode isn) {

    assignUniqueIDBlockNode(md, nametable, isn.getTrueBlock());

    if (isn.getFalseBlock() != null) {
      assignUniqueIDBlockNode(md, nametable, isn.getFalseBlock());
    }

  }

  private void assignUniqueIDDeclarationNode(MethodDescriptor md, SymbolTable nametable,
      DeclarationNode dn) {

    VarDescriptor vd = dn.getVarDescriptor();
    graph.assignUniqueIDtoDescriptor(vd);
  }

}
