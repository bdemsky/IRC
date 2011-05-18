package IR.Flat;

import Analysis.Disjoint.HeapAnalysis;
import Analysis.Disjoint.Alloc;
import IR.*;

import java.io.*;
import java.util.*;


// This BuildCode Extension (BCX) takes a heap analysis
// with points-to information and generates checks at runtime
// verifies the allocation site of objects pointed-to.  It
// doesn't fully verify an analysis but it can reveal bugs!


public class BCXPointsToCheckVRuntime implements BuildCodeExtension {
  
  protected BuildCode    buildCode;
  protected TypeUtil     typeUtil;
  protected HeapAnalysis heapAnalysis;
  
  protected ClassDescriptor cdObject;


  public BCXPointsToCheckVRuntime( BuildCode    buildCode,
                                   TypeUtil     typeUtil,
                                   HeapAnalysis heapAnalysis ) {
    this.buildCode    = buildCode;
    this.typeUtil     = typeUtil;
    this.heapAnalysis = heapAnalysis;
  }


  public void additionalIncludesMethodsHeader(PrintWriter outmethodheader) {    
    outmethodheader.println( "#include<stdio.h>" );
    outmethodheader.println( "#include<execinfo.h>" );
  }


  public void additionalCodeAtTopFlatMethodBody(PrintWriter output, FlatMethod fm) {
    
    for( int i = 0; i < fm.numParameters(); ++i ) {
      TempDescriptor td   = fm.getParameter( i );
      TypeDescriptor type = td.getType();
      if( type.isPtr() ) {
        genAssertRuntimePtrVsHeapResults( output,
                                          fm,
                                          td,
                                          heapAnalysis.canPointToAfter( td, fm )
                                          );
      }
    }
  }


  public void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
    
    TempDescriptor  lhs;
    TempDescriptor  rhs;
    FieldDescriptor fld;
    TempDescriptor  idx;
    TypeDescriptor  type;
    
    
    switch( fn.kind() ) {
    
      case FKind.FlatOpNode: {
        FlatOpNode fon = (FlatOpNode) fn;
        if( fon.getOp().getOp() == Operation.ASSIGN ) {
          lhs = fon.getDest();
          rhs = fon.getLeft();
      
          type = lhs.getType();
          if( type.isPtr() ) {
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              lhs,
                                              heapAnalysis.canPointToAt( lhs, fn )
                                              );
      
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              rhs,
                                              heapAnalysis.canPointToAt( rhs, fn )
                                              );
          }
        }
      } break;
      
      
      case FKind.FlatCastNode: {
        FlatCastNode fcn = (FlatCastNode) fn;
        lhs = fcn.getDst();
        rhs = fcn.getSrc();
      
        type = fcn.getType();
        if( type.isPtr() ) {
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAt( lhs, fn )
                                            );
      
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            rhs,
                                            heapAnalysis.canPointToAt( rhs, fn )
                                            );
        }
      } break;
      
      
      case FKind.FlatFieldNode: {
        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getDst();
        rhs = ffn.getSrc();
        fld = ffn.getField();
      
        type = lhs.getType();
        if( type.isPtr() ) {
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAt( lhs, fn )
                                            );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            rhs,
                                            heapAnalysis.canPointToAt( rhs, fn )
                                            );
      
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            rhs,
                                            fld,
                                            null,
                                            heapAnalysis.canPointToAt( rhs, fld, fn )
                                            );
        }
      } break;
      
      
      case FKind.FlatElementNode: {
        FlatElementNode fen = (FlatElementNode) fn;
        lhs = fen.getDst();
        rhs = fen.getSrc();
        idx = fen.getIndex();
      
        type = lhs.getType();
        if( type.isPtr() ) {
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAt( lhs, fn )
                                            );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            rhs,
                                            heapAnalysis.canPointToAt( rhs, fn )
                                            );
      
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            rhs,
                                            null,
                                            idx,
                                            heapAnalysis.canPointToAtElement( rhs, fn )
                                            );
        }
      } break;
    
    }

  }


  public void additionalCodePostNode(FlatMethod fm,
                                     FlatNode fn,
                                     PrintWriter output) {
    switch( fn.kind() ) {
      case FKind.FlatCall: {
        FlatCall       fc = (FlatCall) fn;
        TempDescriptor td = fc.getReturnTemp();
         
        if( td != null ) {
          TypeDescriptor type = td.getType();
          if( type.isPtr() ) {
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              td,
                                              heapAnalysis.canPointToAfter( td, fn )
                                              );
          }
        }
      } break;
    }
  }



  protected void 
    printConditionFailed( PrintWriter output,
                          String      condition,
                          String      pointer ) {

    // don't do this in the constructor of this extension object because
    // build code hasn't created any types or classes yet!
    if( cdObject == null ) { 
      cdObject = typeUtil.getClass( typeUtil.ObjectClass );
      assert cdObject != null;
    }

    output.println( "printf(\"[[[ CHECK VS HEAP RESULTS FAILED ]]] Condition for failure( "+
                    condition+" ) allocsite=%d at %s:%d\\n\", ((struct "+
                    cdObject.getSafeSymbol()+"*)"+
                    pointer+")->allocsite, __FILE__, __LINE__ );" );

    // spit out the stack trace (so fancy!)
    output.println( "{" );
    output.println( "void* buffer[100];" );
    output.println( "char** strings;" );
    output.println( "int nptrs,j;" );
    output.println( "nptrs = backtrace(buffer, 100);" );
    output.println( "strings = backtrace_symbols(buffer, nptrs);" );
    output.println( "if (strings == NULL) {" );
    output.println( "  perror(\"backtrace_symbols\");" );
    output.println( "}" );
    output.println( "for (j = 0; j < nptrs; j++) {" );
    output.println( "  printf(\"%s\\n\", strings[j]);" );
    output.println( "}" );
    output.println( "}" );
  }
                          



  protected void 
    genAssertRuntimePtrVsHeapResults( PrintWriter    output,
                                      FlatMethod     context,
                                      TempDescriptor x,
                                      Set<Alloc>     targetsByAnalysis ) {

    assert targetsByAnalysis != null;

    output.println( "" );
    output.println( "// checks vs. heap results (DEBUG) for "+x );
    

    if( targetsByAnalysis == HeapAnalysis.DONTCARE_PTR ) {
      output.println( "// ANALYSIS DIDN'T CARE WHAT "+x+" POINTS-TO" );
      return;
    }

    String ptr = buildCode.generateTemp( context, x );

    String condition;
    
    if( targetsByAnalysis.isEmpty() ) {
      condition = ptr+" != NULL";      
      
    } else {
      condition = ptr+" != NULL &&";
      
      Iterator<Alloc> aItr = targetsByAnalysis.iterator();
      while( aItr.hasNext() ) {
        Alloc a = aItr.next();
        condition += ptr+"->allocsite != "+a.getUniqueAllocSiteID();

        if( aItr.hasNext() ) {
          condition += " &&";
        }
      }      
    }

    output.println( "if( "+condition+" ) {" );
    printConditionFailed( output, condition, ptr );
    output.println( "}\n" );
  }



  protected void 
    genAssertRuntimePtrVsHeapResults( PrintWriter                    output,
                                      FlatMethod                     context,
                                      TempDescriptor                 x,
                                      FieldDescriptor                f, // this null OR
                                      TempDescriptor                 i, // this null
                                      Hashtable< Alloc, Set<Alloc> > targetsByAnalysis ) {
    // I assume when you invoke this method you already
    // invoked a check on just what x points to, don't duplicate
    // AND assume the check to x passed

    assert targetsByAnalysis != null;
    
    assert f == null || i == null;
    
    if( f != null ) {
      output.println( "// checks vs. heap results (DEBUG) for "+x+"."+f );
    } else {
      output.println( "// checks vs. heap results (DEBUG) for "+x+"["+i+"]" );
    }


    if( targetsByAnalysis == HeapAnalysis.DONTCARE_DREF ) {
      output.println( "// ANALYSIS DIDN'T CARE WHAT "+x+" POINTS-TO" );
      return;
    }
    
    
    if( targetsByAnalysis.isEmpty() ) {
      output.println( "// Should have already checked "+x+" is NULL" );

    } else {
      output.println( "{" );
      
      // if the ptr is null, that's ok, if not check allocsite
      output.println( "if( "+
                      buildCode.generateTemp( context, x )+
                      " != NULL ) {" );
      
      // precompute the allocsite, if any, of the object we will
      // get from hopping through the first ptr
      output.println( "int allocsiteOneHop = -1;" );
      output.println( buildCode.strObjType+"* objOneHop;" );
      
      if( f != null ) {
        output.println( "objOneHop = ("+buildCode.strObjType+"*) "+
                        buildCode.generateTemp( context, x )+
                        "->"+f.getSafeSymbol()+";");
      } else {
        output.println( "objOneHop = ("+buildCode.strObjType+"*) "+
                        "((struct "+x.getType().dereference().getSafeSymbol()+"**)"+
                        "(((void*) &("+buildCode.generateTemp( context, x )+"->___length___))+sizeof(int)))"+
                        "["+buildCode.generateTemp( context, i )+"];" );
      }
      
      output.println( "if( objOneHop != NULL ) { allocsiteOneHop = objOneHop->allocsite; }" );
      
      output.println( "switch( "+
                      buildCode.generateTemp( context, x )+
                      "->allocsite ) {" );
      
      Iterator<Alloc> kItr = targetsByAnalysis.keySet().iterator();
      while( kItr.hasNext() ) {
        Alloc k = kItr.next();
        
        output.print( "case "+
                      k.getUniqueAllocSiteID()+
                      ":" );

        Set<Alloc> hopTargets = targetsByAnalysis.get( k );
        if( hopTargets == HeapAnalysis.DONTCARE_PTR ) {
          output.print( "/* ANALYSIS DOESN'T CARE */" );
      
        } else {
          String condition = "allocsiteOneHop != -1";
      
          if( !hopTargets.isEmpty() ) {
            condition += " && ";
          }
      
          Iterator<Alloc> aItr = hopTargets.iterator();
          while( aItr.hasNext() ) {
            Alloc a = aItr.next();
            
            condition += 
              "allocsiteOneHop != "+
              a.getUniqueAllocSiteID();
            
            if( aItr.hasNext() ) {
              condition += " && ";
            }
          }
      
          output.println( "if( "+condition+" ) {" );
          printConditionFailed( output, condition, "objOneHop" );
          output.println( "}" );
        }
        
        output.println( "break;" );
      }
      
      output.println( "default:" );
      output.println( "// the previous condition for "+x+
                      " should already report this failure point" );
      output.println( "break;" );
      output.println( "}" );
      output.println( "}" );
      output.println( "}\n" );
    }
  }




  public void printExtraArrayFields(PrintWriter outclassdefs) {}
  public void outputTransCode(PrintWriter output) {}
  public void buildCodeSetup() {}
  public void generateSizeArrayExtensions(PrintWriter outclassdefs) {}
  public void preCodeGenInitialization() {}
  public void postCodeGenCleanUp() {}
  public void additionalClassObjectFields(PrintWriter outclassdefs) {}
  public void additionalCodeGen(PrintWriter outmethodheader,
                                   PrintWriter outstructs,
                                   PrintWriter outmethod) {}
  public void additionalCodeAtTopOfMain(PrintWriter outmethod) {}
  public void additionalCodeForCommandLineArgs(PrintWriter outmethod, String argsVar) {}
  public void additionalCodeAtBottomOfMain(PrintWriter outmethod) {}
  public void additionalIncludesMethodsImplementation(PrintWriter outmethod) {}
  public void additionalIncludesStructsHeader(PrintWriter outstructs) {}
  public void additionalCodeAtTopMethodsImplementation(PrintWriter outmethod) {}
  public void additionalCodeNewObject(PrintWriter outmethod, String dstVar, FlatNew flatNew) {}
  public void additionalCodeNewStringLiteral(PrintWriter output, String dstVar) {}
}
