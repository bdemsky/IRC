package Analysis.SSJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.SymbolTable;
import IR.VarDescriptor;
import IR.Tree.BlockNode;
import IR.Tree.BlockStatementNode;
import IR.Tree.DeclarationNode;
import IR.Tree.Kind;

public class LocationInference {

  State state;
  SSJavaAnalysis ssjava;

  List<ClassDescriptor> toanalyzeList;
  List<MethodDescriptor> toanalyzeMethodList;

  public LocationInference(SSJavaAnalysis ssjava, State state) {
    this.ssjava = ssjava;
    this.state = state;
    this.toanalyzeList = new ArrayList<ClassDescriptor>();
    this.toanalyzeMethodList = new ArrayList<MethodDescriptor>();
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

  public void inference() {

    setupToAnalyze();

    while (!toAnalyzeIsEmpty()) {
      ClassDescriptor cd = toAnalyzeNext();

      setupToAnalazeMethod(cd);
      while (!toAnalyzeMethodIsEmpty()) {
        MethodDescriptor md = toAnalyzeMethodNext();
        if (ssjava.needTobeAnnotated(md)) {
          if (state.SSJAVADEBUG) {
            System.out.println("SSJAVA: Location Inference: " + md);
          }
          analyzeMethodBody(cd, md, null);
        }
      }
    }

  }

  private void analyzeMethodBody(ClassDescriptor cd, MethodDescriptor md,
      CompositeLocation constraints) {
    BlockNode bn = state.getMethodBody(md);
    analyzeBlockNode(md, md.getParameterTable(), bn);
  }

  private CompositeLocation analyzeBlockNode(MethodDescriptor md, SymbolTable nametable,
      BlockNode bn) {

    bn.getVarTable().setParent(nametable);
    for (int i = 0; i < bn.size(); i++) {
      BlockStatementNode bsn = bn.get(i);
      analyzeBlockStatementNode(md, bn.getVarTable(), bsn);
    }
    return new CompositeLocation();

  }

  private void analyzeBlockStatementNode(MethodDescriptor md, SymbolTable nametable,
      BlockStatementNode bsn) {

    switch (bsn.kind()) {
    case Kind.BlockExpressionNode:
      // checkBlockExpressionNode(md,(BlockExpressionNode)bsn);
      break;

    case Kind.DeclarationNode:
      analyzeDeclarationNode(md, nametable, (DeclarationNode) bsn);
      break;

    case Kind.IfStatementNode:
      break;

    case Kind.LoopNode:
      break;

    case Kind.ReturnNode:
      break;

    case Kind.SubBlockNode:
      break;

    case Kind.ContinueBreakNode:
      break;

    case Kind.SwitchStatementNode:
      break;

    }
  }

  private void analyzeDeclarationNode(MethodDescriptor md, SymbolTable nametable, DeclarationNode dn) {

    VarDescriptor vd = dn.getVarDescriptor();

  }

}
