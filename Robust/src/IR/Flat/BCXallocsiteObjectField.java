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
  protected HeapAnalysis heapAnalysis;
  

  public BCXallocsiteObjectField( BuildCode    buildCode,
                                  HeapAnalysis heapAnalysis ) {
    this.buildCode    = buildCode;
    this.heapAnalysis = heapAnalysis;
  }
  
  
  public void additionalClassObjectFields(PrintWriter outclassdefs) {
    outclassdefs.println("  int allocsite;");    
  }

  public void additionalCodeForCommandLineArgs(PrintWriter outmethod, String argsVar) {
    outmethod.println(argsVar+"->allocsite = "+
                      heapAnalysis.getCmdLineArgsAlloc().getUniqueAllocSiteID()+
                      ";"
                      );
  }

  public void additionalCodeNewObject(PrintWriter outmethod, String dstVar, FlatNew flatNew) {
    outmethod.println(dstVar+"->allocsite = "+
                      heapAnalysis.getAllocationSiteFromFlatNew( flatNew ).getUniqueAllocSiteID()+
                      ";"
                      );
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