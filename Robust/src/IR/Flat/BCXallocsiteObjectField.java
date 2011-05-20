package IR.Flat;

import Analysis.Disjoint.HeapAnalysis;
import Analysis.Disjoint.Alloc;
import IR.*;

import java.io.*;
import java.util.*;


// This BuildCode Extension (BCX) takes a heap analysis
// with points-to information and adds a field to objects
// at runtime that specifies which allocation site it is
// from.  This extension supports other extensions.


public class BCXallocsiteObjectField implements BuildCodeExtension {
  
  protected BuildCode    buildCode;
  protected TypeUtil     typeUtil;
  protected HeapAnalysis heapAnalysis;
  
  protected ClassDescriptor cdString;
  protected FieldDescriptor strBytes;


  public BCXallocsiteObjectField( BuildCode    buildCode,
                                  TypeUtil     typeUtil,
                                  HeapAnalysis heapAnalysis ) {
    this.buildCode    = buildCode;
    this.typeUtil     = typeUtil;
    this.heapAnalysis = heapAnalysis;

    cdString = typeUtil.getClass( typeUtil.StringClass );
    assert cdString != null;

    strBytes = null;
    Iterator sFieldsItr = cdString.getFields();
    while( sFieldsItr.hasNext() ) {
      FieldDescriptor fd = (FieldDescriptor) sFieldsItr.next();
      if( fd.getSymbol().equals( typeUtil.StringClassValueField ) ) {
        strBytes = fd;
        break;
      }
    }
    assert strBytes != null;
  }


  protected Integer getIDtoGen( Alloc a, int idWhenNull ) {
    if( a == null ) {
      return idWhenNull;
    }
    return a.getUniqueAllocSiteID();
  }

  
  
  public void additionalClassObjectFields(PrintWriter outclassdefs) {
    outclassdefs.println("  int allocsite;");    
  }


  public void additionalCodeForCommandLineArgs(PrintWriter outmethod, String argsVar) {

    String argsAccess = "((struct "+cdString.getSafeSymbol()+
      " **)(((char *)& "+argsVar+"->___length___)+sizeof(int)))";

    Integer argsAllocID     = getIDtoGen( heapAnalysis.getCmdLineArgsAlloc(),     -109 ); 
    Integer argAllocID      = getIDtoGen( heapAnalysis.getCmdLineArgAlloc(),      -119 ); 
    Integer argBytesAllocID = getIDtoGen( heapAnalysis.getCmdLineArgBytesAlloc(), -129 ); 
    
    outmethod.println(argsVar+"->allocsite = "+argsAllocID+";");
    outmethod.println("{");
    outmethod.println("  int i;" );
    outmethod.println("  for( i = 0; i < "+argsVar+"->___length___; ++i ) {");    
    outmethod.println("    "+argsAccess+"[i]->allocsite = "+argAllocID+";");
    outmethod.println("    "+argsAccess+"[i]->"+strBytes.getSafeSymbol()+
                      "->allocsite = "+argBytesAllocID+";");
    outmethod.println("  }");
    outmethod.println("}");
    outmethod.println("");
  }


  public void additionalCodeNewObject(PrintWriter outmethod, String dstVar, FlatNew flatNew) {

    Integer allocID = getIDtoGen( heapAnalysis.getAllocationSiteFromFlatNew( flatNew ), -199 ); 

    outmethod.println(dstVar+"->allocsite = "+allocID+";");
  }


  public void additionalCodeNewStringLiteral(PrintWriter output, String dstVar) {

    Integer stringAllocID      = getIDtoGen( heapAnalysis.getNewStringLiteralAlloc(),      -29 ); 
    Integer stringBytesAllocID = getIDtoGen( heapAnalysis.getNewStringLiteralBytesAlloc(), -39 ); 

    output.println(dstVar+"->allocsite = "+stringAllocID+";");    
    output.println(dstVar+"->"+strBytes.getSafeSymbol()+
                   "->allocsite = "+stringBytesAllocID+";");
  }



  public void printExtraArrayFields(PrintWriter outclassdefs) {}
  public void outputTransCode(PrintWriter output) {}
  public void buildCodeSetup() {}
  public void generateSizeArrayExtensions(PrintWriter outclassdefs) {}
  public void preCodeGenInitialization() {}
  public void postCodeGenCleanUp() {}
  public void additionalCodeGen(PrintWriter outmethodheader,
                                   PrintWriter outstructs,
                                   PrintWriter outmethod) {}
  public void additionalCodeAtTopOfMain(PrintWriter outmethod) {}
  public void additionalCodeAtBottomOfMain(PrintWriter outmethod) {}
  public void additionalIncludesMethodsImplementation(PrintWriter outmethod) {}
  public void additionalIncludesStructsHeader(PrintWriter outstructs) {}
  public void additionalCodeAtTopMethodsImplementation(PrintWriter outmethod) {}
  public void additionalIncludesMethodsHeader(PrintWriter outmethodheader) {}
  public void additionalCodeAtTopFlatMethodBody(PrintWriter output, FlatMethod fm) {}
  public void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output) {}
  public void additionalCodePostNode(FlatMethod fm, FlatNode fn, PrintWriter output) {}
}