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
  protected HeapAnalysis heapAnalysis;
  

  public BCXPointsToCheckVRuntime( BuildCode    buildCode,
                                   HeapAnalysis heapAnalysis ) {
    this.buildCode    = buildCode;
    this.heapAnalysis = heapAnalysis;
  }


  public void additionalIncludesMethodsHeader(PrintWriter outmethodheader) {
    outmethodheader.println("#include \"assert.h\"");
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
    genAssertRuntimePtrVsHeapResults( PrintWriter    output,
                                      FlatMethod     context,
                                      TempDescriptor x,
                                      Set<Alloc>     targetsByAnalysis ) {

    output.println( "" );
    output.println( "// asserts vs. heap results (DEBUG)" );
    
    if( targetsByAnalysis == null ||
        targetsByAnalysis.isEmpty() ) {
      output.println( "assert( "+
                      buildCode.generateTemp( context, x )+
                      " == NULL );\n" );
      
    } else {
      output.print( "assert( "+
                    buildCode.generateTemp( context, x )+
                    " == NULL || " );

      Iterator<Alloc> aItr = targetsByAnalysis.iterator();
      while( aItr.hasNext() ) {
        Alloc a = aItr.next();
        output.print( buildCode.generateTemp( context, x )+
                      "->allocsite == "+
                      a.getUniqueAllocSiteID()
                      );
        if( aItr.hasNext() ) {
          output.print( " || " );
        }
      }

      output.println( " );\n" );      
    }
  }



  protected void 
    genAssertRuntimePtrVsHeapResults( PrintWriter                    output,
                                      FlatMethod                     context,
                                      TempDescriptor                 x,
                                      FieldDescriptor                f, // this null OR
                                      TempDescriptor                 i, // this null
                                      Hashtable< Alloc, Set<Alloc> > targetsByAnalysis ) {

    assert f == null || i == null;

    output.println( "// asserts vs. heap results (DEBUG)" );
    
    if( targetsByAnalysis == null ||
        targetsByAnalysis.isEmpty() ) {
      output.println( "assert( "+
                      buildCode.generateTemp( context, x )+
                      " == NULL );\n" );
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
                      ": assert( allocsiteOneHop == -1" );
        
        Set<Alloc> hopTargets = targetsByAnalysis.get( k );
        if( hopTargets != null ) {

          if( !hopTargets.isEmpty() ) {
            output.print( " || " );
          }

          Iterator<Alloc> aItr = hopTargets.iterator();
          while( aItr.hasNext() ) {
            Alloc a = aItr.next();
            
            output.print( "allocsiteOneHop == "+
                          a.getUniqueAllocSiteID() );
            
            if( aItr.hasNext() ) {
              output.print( " || " );
            }
          }
        }

        output.println( " ); break;" );
      }

      output.println( "    default: assert( 0 ); break;" );
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
}
