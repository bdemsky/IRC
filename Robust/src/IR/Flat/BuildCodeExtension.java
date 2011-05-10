package IR.Flat;

import java.io.*;

// implement these methods and register the extension
// object with BuildCode.  BuildCode will then invoke the
// methods below at the extension points.

public interface BuildCodeExtension {

  public void printExtraArrayFields(PrintWriter outclassdefs);  
  public void outputTransCode(PrintWriter output);
  public void buildCodeSetup();
  public void generateSizeArrayExtensions(PrintWriter outclassdefs);

  public void preCodeGenInitialization();
  public void postCodeGenCleanUp();

  public void additionalCodeGen(PrintWriter outmethodheader,
                         PrintWriter outstructs,
                         PrintWriter outmethod);

  public void additionalIncludesMethodsHeader(PrintWriter outmethodheader);
  public void additionalIncludesMethodsImplementation(PrintWriter outmethod);
  public void additionalIncludesStructsHeader(PrintWriter outstructs);

  public void additionalClassObjectFields(PrintWriter outclassdefs);

  public void additionalCodeAtTopOfMain(PrintWriter outmethod);
  public void additionalCodeForCommandLineArgs(PrintWriter outmethod, String argsVar);
  public void additionalCodeAtBottomOfMain(PrintWriter outmethod);
  public void additionalCodeAtTopMethodsImplementation(PrintWriter outmethod);
  public void additionalCodeAtTopFlatMethodBody(PrintWriter output, FlatMethod fm);
  public void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output);
  public void additionalCodePostNode(FlatMethod fm, FlatNode fn, PrintWriter output);
  public void additionalCodeNewObject(PrintWriter outmethod, String dstVar, FlatNew flatNew);
}
