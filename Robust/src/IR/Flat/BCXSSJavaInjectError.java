package IR.Flat;
import IR.*;
import IR.Tree.*;

import java.util.*;
import java.io.*;

import Util.*;


public class BCXSSJavaInjectError implements BuildCodeExtension {

  private State state;
  private BuildCode buildCode;
  private String nStr             = "__ssjava_inv_error_prob__";
  private String errorInjectedStr = "__ssjava_error_has_been_injected__";

  public BCXSSJavaInjectError( State state, BuildCode buildCode ) {
    this.state     = state;
    this.buildCode = buildCode;
  }


  // the reason for errorInjectionInit is that some code (like static initializers
  // in the compiled program) actually run before the GENERATED MAIN runs!  Not the
  // complied program's main, either!  So just rig it so no error injection code runs
  // until we're sure the random seed is initialized.

  public void additionalCodeGen( PrintWriter outmethodheader,
                                 PrintWriter outstructs,
                                 PrintWriter outmethod ) {
    outmethodheader.println("extern int "+nStr+";");
    outmethodheader.println("extern int "+errorInjectedStr+";");
    outmethodheader.println("extern int errorInjectionInit;");

    outmethod.println("int "+nStr+" = "+state.SSJAVA_INV_ERROR_PROB+";");
    outmethod.println("int "+errorInjectedStr+" = 0;");
    outmethod.println("int errorInjectionInit = 0;");
  }

  public void additionalCodeAtTopOfMain( PrintWriter outmethod ) {
    outmethod.println("  srand("+state.SSJAVA_ERROR_SEED+");");
    outmethod.println("  errorInjectionInit = 1;");
  }
  
  public void additionalCodePostNode( FlatMethod fm, FlatNode fn, PrintWriter output ) {
    
    TempDescriptor injectTarget = null;
    
    switch( fn.kind() ) {
      case FKind.FlatOpNode:
        FlatOpNode fon = (FlatOpNode) fn;
        if( fon.getOp().getOp() == Operation.DIV ) {
          injectTarget = fon.getDest();
        }
        break;
      
      case FKind.FlatFieldNode:
        injectTarget = ((FlatFieldNode) fn).getDst();
        break;
        
      case FKind.FlatElementNode:
        injectTarget = ((FlatElementNode) fn).getDst();
        break;
    }

    if( injectTarget != null ) {
      output.println("if( errorInjectionInit ) {");
      output.println("  int roll = rand() % "+nStr+";");
      output.println("  if( !"+errorInjectedStr+" && roll == 0 ) {" );
      output.println("    "+errorInjectedStr+" = 1;" );
      output.println("    "+buildCode.generateTemp( fm, injectTarget )+" = 0;" );
      output.println("    printf(\"SSJAVA: Injecting error ["+injectTarget+
                     "=%d] at file:%s, func:%s, line:%d \\n\"" + 
                     ", 0, __FILE__, __func__, __LINE__);");
      output.println("  }" );
      output.println("}");
    }
  }



  public void additionalIncludesMethodsImplementation( PrintWriter outmethod ){}
  public void printExtraArrayFields(PrintWriter outclassdefs){}
  public void outputTransCode(PrintWriter output){}
  public void buildCodeSetup(){}
  public void generateSizeArrayExtensions(PrintWriter outclassdefs){}
  public void preCodeGenInitialization(){}
  public void postCodeGenCleanUp(){}
  public void additionalIncludesMethodsHeader(PrintWriter outmethodheader){}
  public void additionalIncludesStructsHeader(PrintWriter outstructs){}
  public void additionalClassObjectFields(PrintWriter outclassdefs){}
  public void additionalCodeForCommandLineArgs(PrintWriter outmethod, String argsVar){}
  public void additionalCodeAtBottomOfMain(PrintWriter outmethod){}
  public void additionalCodeAtTopMethodsImplementation(PrintWriter outmethod){}
  public void additionalCodeAtTopFlatMethodBody(PrintWriter output, FlatMethod fm){}
  public void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output){}
  public void additionalCodeNewObject(PrintWriter outmethod, String dstVar, FlatNew flatNew){}
  public void additionalCodeNewStringLiteral(PrintWriter output, String dstVar){}
}
