package IR.Flat;

import IR.*;
import IR.Tree.*;

import java.util.*;
import java.io.*;

import Util.*;

public class BCXSSJavaInjectError implements BuildCodeExtension {

  private State state;
  private BuildCode buildCode;
  private String nStr = "__ssjava_inv_error_prob__";
  private String errorInjectedStr = "__ssjava_error_has_been_injected__";
  private String errorInjectionStarted = "__ssjava_error_injection_started__";
  private boolean agg = false;

  public BCXSSJavaInjectError(State state, BuildCode buildCode) {
    this.state = state;
    this.buildCode = buildCode;
  }

  public void additionalIncludesMethodsImplementation(PrintWriter outmethod) {
    outmethod.println("#include <stdlib.h>");
    outmethod.println("#include <stdio.h>");
  }

  // the reason for errorInjectionInit is that some code (like static
  // initializers
  // in the compiled program) actually run before the GENERATED MAIN runs! Not
  // the
  // complied program's main, either! So just rig it so no error injection code
  // runs
  // until we're sure the random seed is initialized.

  public void additionalCodeGen(PrintWriter outmethodheader, PrintWriter outstructs,
      PrintWriter outmethod) {
    outmethodheader.println("extern int " + nStr + ";");
    outmethodheader.println("extern int " + errorInjectedStr + ";");
    outmethodheader.println("extern int errorInjectionInit;");
    outmethodheader.println("extern int " + errorInjectionStarted + ";");
    outmethodheader.println("extern int errorInjectionMax;");

    outmethod.println("int " + nStr + " = " + state.SSJAVA_INV_ERROR_PROB + ";");
    outmethod.println("int " + errorInjectedStr + " = 0;");
    outmethod.println("int " + errorInjectionStarted + " = 0;");
    outmethod.println("int errorInjectionMax = 1;");
    outmethod.println("int errorInjectionInit = 0;");
  }

  public void additionalCodeAtTopOfMain(PrintWriter outmethod) {
    outmethod.println("  srand(" + state.SSJAVA_ERROR_SEED + ");");
    outmethod.println("  errorInjectionInit = 1;");
  }

  public void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
  }

  public void additionalCodePostNode(FlatMethod fm, FlatNode fn, PrintWriter output) {

    TempDescriptor injectTarget = null;
    FieldDescriptor injectField = null;

    if ((!state.getAnnotationRequireSet().contains(fm.getMethod()))) {
      return;
    }

    if (fm.getMethod().getClassDesc().getClassName().equals("String")) {
      return;
    }

    switch (fn.kind()) {
    case FKind.FlatOpNode:
      FlatOpNode fon = (FlatOpNode) fn;
      injectTarget = fon.getDest();

      int op = fon.getOp().getOp();
      if (injectTarget.getType().isPrimitive()
          && (op == Operation.DIV || (agg && (op == Operation.ADD || op == Operation.SUB
              || op == Operation.MULT || op == Operation.MOD || op == Operation.LOGIC_AND
              || op == Operation.LOGIC_OR || op == Operation.LOGIC_NOT || op == Operation.NOTEQUAL
              || op == Operation.BIT_AND || op == Operation.BIT_OR || op == Operation.BIT_XOR/*|| op == Operation.EQUAL*/)))) {
        // inject a random value
        initializeInjection(output);
        output.println("    " + buildCode.generateTemp(fm, injectTarget) + " = ("
            + injectTarget.getType().getSafeSymbol() + ") rand();");
        closingInjection(output);
      }
      break;

    // case FKind.FlatFieldNode:
    // injectTarget = ((FlatFieldNode) fn).getDst();
    // break;
    //
    // case FKind.FlatElementNode:
    // injectTarget = ((FlatElementNode) fn).getDst();
    // break;

    case FKind.FlatSetFieldNode:
      FlatSetFieldNode fsn = (FlatSetFieldNode) fn;
      injectTarget = fsn.getDst();
      injectField = fsn.getField();

      if (injectTarget != null && injectField != null && injectField.getType().isPrimitive()
          && !injectTarget.getType().isArray() && !injectField.isStatic()) {
        initializeInjection(output);
        // inject a random value
        output.println("    " + buildCode.generateTemp(fm, injectTarget) + "->"
            + injectField.getSafeSymbol() + " = (" + injectField.getType().getSafeSymbol()
            + ") rand();");
        closingInjection(output);

      }

      break;

    case FKind.FlatSetElementNode:
      FlatSetElementNode fsen = (FlatSetElementNode) fn;
      injectTarget = fsen.getDst();

      if (injectTarget != null && injectTarget.getType().isPrimitive()) {
        initializeInjection(output);

        String type;
        TypeDescriptor elementtype = fsen.getDst().getType().dereference();
        if (elementtype.isClass() && elementtype.getClassDesc().isEnum()) {
          type = "int ";
        } else if (elementtype.isArray() || elementtype.isClass() || (elementtype.isNull()))
          type = "void *";
        else
          type = elementtype.getSafeSymbol() + " ";

        output.println("((" + type + "*)(((char *) &(" + buildCode.generateTemp(fm, injectTarget)
            + "->___length___))+sizeof(int)))[" + buildCode.generateTemp(fm, fsen.getIndex())
            + "] = (" + type + ") rand();");

        closingInjection(output);
      }
      break;

    }

  }

  private void initializeInjection(PrintWriter output) {
    output.println("if( errorInjectionInit ) {");
    output.println("  int roll = rand() % " + nStr + ";");
    output.println("  if( ( " + errorInjectedStr + " && " + errorInjectionStarted
        + " < errorInjectionMax ) || (!" + errorInjectedStr + " && roll == 0) ) {");
    output.println("    " + errorInjectedStr + " = 1;");
    output.println("    " + errorInjectionStarted + " += 1;");
  }

  private void closingInjection(PrintWriter output) {
    output.println("    printf(\"SSJAVA: Injecting error at file:%s, func:%s, line:%d \\n\""
        + ", __FILE__, __func__, __LINE__);");
    output.println("  }");
    output.println("}");
  }

  public void printExtraArrayFields(PrintWriter outclassdefs) {
  }

  public void outputTransCode(PrintWriter output) {
  }

  public void buildCodeSetup() {
  }

  public void generateSizeArrayExtensions(PrintWriter outclassdefs) {
  }

  public void preCodeGenInitialization() {
  }

  public void postCodeGenCleanUp() {
  }

  public void additionalIncludesMethodsHeader(PrintWriter outmethodheader) {
  }

  public void additionalIncludesStructsHeader(PrintWriter outstructs) {
  }

  public void additionalClassObjectFields(PrintWriter outclassdefs) {
  }

  public void additionalCodeForCommandLineArgs(PrintWriter outmethod, String argsVar) {
  }

  public void additionalCodeAtBottomOfMain(PrintWriter outmethod) {
  }

  public void additionalCodeAtTopMethodsImplementation(PrintWriter outmethod) {
  }

  public void additionalCodeAtTopFlatMethodBody(PrintWriter output, FlatMethod fm) {
  }

  public void additionalCodeNewObject(PrintWriter outmethod, String dstVar, FlatNew flatNew) {
  }

  public void additionalCodeNewStringLiteral(PrintWriter output, String dstVar) {
  }
}
