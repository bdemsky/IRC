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
  
  protected State        state;
  protected BuildCode    buildCode;
  protected TypeUtil     typeUtil;
  protected HeapAnalysis heapAnalysis;
  
  protected ClassDescriptor cdObject;

  protected TypeDescriptor stringType;

  private boolean DEBUG = false;



  public BCXPointsToCheckVRuntime( State        state,
                                   BuildCode    buildCode,
                                   TypeUtil     typeUtil,
                                   HeapAnalysis heapAnalysis ) {
    this.state        = state;
    this.buildCode    = buildCode;
    this.typeUtil     = typeUtil;
    this.heapAnalysis = heapAnalysis;

    ClassDescriptor cdString = typeUtil.getClass( typeUtil.StringClass );
    assert cdString != null;
    stringType = new TypeDescriptor( cdString );
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
        output.println( "// Generating points-to checks for method params" );

        genAssertRuntimePtrVsHeapResults( output,
                                          fm,
                                          td,
                                          heapAnalysis.canPointToAfter( td, fm )
                                          );

        output.println( "// end method params" );

        if( DEBUG ) {
          System.out.println( "\nGenerating code for "+fm );
          System.out.println( "  arg "+td+" can point to "+heapAnalysis.canPointToAfter( td, fm ) );
        }
      }
    }
  }


  public void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
    
    TempDescriptor  lhs;
    TempDescriptor  rhs;
    FieldDescriptor fld;
    TempDescriptor  idx;
    TypeDescriptor  type;
    TempDescriptor  arg;
    TempDescriptor  ret;
    

    // for PRE-NODE checks, only look at pointers we are reading from because
    // pointers about to be set may be undefined and don't pass runtime checks nicely

    
    switch( fn.kind() ) {
    
      /*
      case FKind.FlatLiteralNode: {
        FlatLiteralNode fln = (FlatLiteralNode) fn;
        
        if( fln.getType().equals( stringType ) ) {
          lhs = fln.getDst();

          output.println( "// Generating points-to checks for pre-node string literal" );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAt( lhs, fn )
                                            );
      
          output.println( "// end pre-node string literal" );


          if( DEBUG ) {
            System.out.println( "  before "+fn );
            System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAt( lhs, fn ) );
          }            
        }  
      } break;
      */

      case FKind.FlatOpNode: {
        FlatOpNode fon = (FlatOpNode) fn;
        if( fon.getOp().getOp() == Operation.ASSIGN ) {
          lhs = fon.getDest();
          rhs = fon.getLeft();
      
          type = lhs.getType();
          if( type.isPtr() ) {

            output.println( "// Generating points-to checks for pre-node op assign" );
            

            //genAssertRuntimePtrVsHeapResults( output,
            //                                  fm,
            //                                  lhs,
            //                                  heapAnalysis.canPointToAt( lhs, fn )
            //                                  );
      
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              rhs,
                                              heapAnalysis.canPointToAt( rhs, fn )
                                              );

            output.println( "// end pre-node op assign" );

            
            if( DEBUG ) {
              System.out.println( "  before "+fn );
              //System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAt( lhs, fn ) );
              System.out.println( "    "+rhs+" can point to "+heapAnalysis.canPointToAt( rhs, fn ) );
            }            
          }
        }
      } break;
      
      
      case FKind.FlatCastNode: {
        FlatCastNode fcn = (FlatCastNode) fn;
        lhs = fcn.getDst();
        rhs = fcn.getSrc();
      
        type = fcn.getType();
        if( type.isPtr() ) {

          output.println( "// Generating points-to checks for pre-node cast" );

          //genAssertRuntimePtrVsHeapResults( output,
          //                                  fm,
          //                                  lhs,
          //                                  heapAnalysis.canPointToAt( lhs, fn )
          //                                  );
      
          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            rhs,
                                            heapAnalysis.canPointToAt( rhs, fn )
                                            );

          output.println( "// end pre-node cast" );


          if( DEBUG ) {
            System.out.println( "  before "+fn );
            //System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAt( lhs, fn ) );
            System.out.println( "    "+rhs+" can point to "+heapAnalysis.canPointToAt( rhs, fn ) );
          }            
        }
      } break;
      
      
      case FKind.FlatFieldNode: {
        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getDst();
        rhs = ffn.getSrc();
        fld = ffn.getField();
      
        type = lhs.getType();
        if( type.isPtr() ) {

          output.println( "// Generating points-to checks for pre-node field" );

          //genAssertRuntimePtrVsHeapResults( output,
          //                                  fm,
          //                                  lhs,
          //                                  heapAnalysis.canPointToAt( lhs, fn )
          //                                  );

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

          output.println( "// end pre-node field" );


          if( DEBUG ) {
            System.out.println( "  before "+fn );
            //System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAt( lhs, fn ) );
            System.out.println( "    "+rhs+" can point to "+heapAnalysis.canPointToAt( rhs, fn ) );
            System.out.println( "    "+rhs+"."+fld+" can point to "+heapAnalysis.canPointToAt( rhs, fld, fn ) );
          }            
        }
      } break;
      
      
      case FKind.FlatElementNode: {
        FlatElementNode fen = (FlatElementNode) fn;
        lhs = fen.getDst();
        rhs = fen.getSrc();
        idx = fen.getIndex();
      
        type = lhs.getType();
        if( type.isPtr() ) {

          output.println( "// Generating points-to checks for pre-node element" );

          //genAssertRuntimePtrVsHeapResults( output,
          //                                  fm,
          //                                  lhs,
          //                                  heapAnalysis.canPointToAt( lhs, fn )
          //                                  );

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

          output.println( "// end pre-node element" );


          if( DEBUG ) {
            System.out.println( "  before "+fn );
            //System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAt( lhs, fn ) );
            System.out.println( "    "+rhs+" can point to "+heapAnalysis.canPointToAt( rhs, fn ) );
            System.out.println( "    "+rhs+"["+idx+"] can point to "+heapAnalysis.canPointToAtElement( rhs, fn ) );
          }            
        }
      } break;


      case FKind.FlatCall: {
        FlatCall fc = (FlatCall) fn;
        //ret = fc.getReturnTemp();
        
        FlatMethod fmCallee = state.getMethodFlat( fc.getMethod() );

        boolean somethingChecked = false;

        output.println( "// Generating points-to checks for pre-node call" );

        for( int i = 0; i < fmCallee.numParameters(); ++i ) {
          arg  = fc.getArgMatchingParamIndex( fmCallee, i );
          type = arg.getType();
          if( type.isPtr() ) {
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              arg,
                                              heapAnalysis.canPointToAt( arg, fn )
                                              );
            somethingChecked = true;
          }
        }
        
        //if( ret != null ) {
        //  type = ret.getType();
        //  if( type.isPtr() ) {
        //    genAssertRuntimePtrVsHeapResults( output,
        //                                      fm,
        //                                      ret,
        //                                      heapAnalysis.canPointToAt( ret, fn )
        //                                      );
        //    somethingChecked = true;
        //  }
        //}
         
        output.println( "// end pre-node call" );

        if( DEBUG && somethingChecked ) {

          System.out.println( "  before "+fn+":" );
              
          for( int i = 0; i < fmCallee.numParameters(); ++i ) {
            arg  = fc.getArgMatchingParamIndex( fmCallee, i );
            type = arg.getType();
            if( type.isPtr() ) {
              System.out.println( "    arg "+arg+" can point to "+heapAnalysis.canPointToAt( arg, fn ) );
            }
          }  

          //if( ret != null ) {
          //  type = ret.getType();
          //  if( type.isPtr() ) {
          //    System.out.println( "    return temp "+ret+" can point to "+heapAnalysis.canPointToAt( ret, fn ) );
          //  }
          //}
          
        }
      } break;    
    }
  }



  public void additionalCodePostNode(FlatMethod fm,
                                     FlatNode fn,
                                     PrintWriter output) {

    TempDescriptor  lhs;
    TempDescriptor  rhs;
    FieldDescriptor fld;
    TempDescriptor  idx;
    TypeDescriptor  type;
    TempDescriptor  arg;
    TempDescriptor  ret;



    switch( fn.kind() ) {


      case FKind.FlatLiteralNode: {
        FlatLiteralNode fln = (FlatLiteralNode) fn;
        
        if( fln.getType().equals( stringType ) ) {
          lhs = fln.getDst();
          
          output.println( "// Generating points-to checks for post-node string literal" );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAfter( lhs, fn )
                                            );
      
          output.println( "// end post-node string literal" );


          if( DEBUG ) {
            System.out.println( "  after "+fn );
            System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAfter( lhs, fn ) );
          }            
        }  
      } break;


      case FKind.FlatOpNode: {
        FlatOpNode fon = (FlatOpNode) fn;
        if( fon.getOp().getOp() == Operation.ASSIGN ) {
          lhs = fon.getDest();
      
          type = lhs.getType();
          if( type.isPtr() ) {

            output.println( "// Generating points-to checks for post-node op assign" );
            
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              lhs,
                                              heapAnalysis.canPointToAfter( lhs, fn )
                                              );
      
            output.println( "// end post-node op assign" );

            
            if( DEBUG ) {
              System.out.println( "  after "+fn );
              System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAfter( lhs, fn ) );
            }            
          }
        }
      } break;
      
      
      case FKind.FlatCastNode: {
        FlatCastNode fcn = (FlatCastNode) fn;
        lhs = fcn.getDst();
      
        type = fcn.getType();
        if( type.isPtr() ) {

          output.println( "// Generating points-to checks for post-node cast" );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAfter( lhs, fn )
                                            );
      
          output.println( "// end post-node cast" );


          if( DEBUG ) {
            System.out.println( "  after "+fn );
            System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAfter( lhs, fn ) );
          }            
        }
      } break;
      
      
      case FKind.FlatFieldNode: {
        FlatFieldNode ffn = (FlatFieldNode) fn;
        lhs = ffn.getDst();
      
        type = lhs.getType();
        if( type.isPtr() ) {

          output.println( "// Generating points-to checks for post-node field" );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAfter( lhs, fn )
                                            );

          output.println( "// end post-node field" );


          if( DEBUG ) {
            System.out.println( "  after "+fn );
            System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAfter( lhs, fn ) );
          }            
        }
      } break;
      
      
      case FKind.FlatElementNode: {
        FlatElementNode fen = (FlatElementNode) fn;
        lhs = fen.getDst();
      
        type = lhs.getType();
        if( type.isPtr() ) {

          output.println( "// Generating points-to checks for post-node element" );

          genAssertRuntimePtrVsHeapResults( output,
                                            fm,
                                            lhs,
                                            heapAnalysis.canPointToAfter( lhs, fn )
                                            );

          output.println( "// end post-node element" );


          if( DEBUG ) {
            System.out.println( "  after "+fn );
            System.out.println( "    "+lhs+" can point to "+heapAnalysis.canPointToAfter( lhs, fn ) );
          }            
        }
      } break;


      case FKind.FlatCall: {
        FlatCall fc = (FlatCall) fn;
        ret = fc.getReturnTemp();
        
        FlatMethod fmCallee = state.getMethodFlat( fc.getMethod() );

        boolean somethingChecked = false;

        output.println( "// Generating points-to checks for post-node call" );

        for( int i = 0; i < fmCallee.numParameters(); ++i ) {
          arg  = fc.getArgMatchingParamIndex( fmCallee, i );
          type = arg.getType();
          if( type.isPtr() ) {
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              arg,
                                              heapAnalysis.canPointToAfter( arg, fn )
                                              );
            somethingChecked = true;
          }
        }
        
        if( ret != null ) {
          type = ret.getType();
          if( type.isPtr() ) {
            genAssertRuntimePtrVsHeapResults( output,
                                              fm,
                                              ret,
                                              heapAnalysis.canPointToAfter( ret, fn )
                                              );
            somethingChecked = true;
          }
        }
         
        output.println( "// end post-node call" );

        if( DEBUG && somethingChecked ) {

          System.out.println( "  after "+fn+":" );
              
          for( int i = 0; i < fmCallee.numParameters(); ++i ) {
            arg  = fc.getArgMatchingParamIndex( fmCallee, i );
            type = arg.getType();
            if( type.isPtr() ) {
              System.out.println( "    arg "+arg+" can point to "+heapAnalysis.canPointToAfter( arg, fn ) );
            }
          }  

          if( ret != null ) {
            type = ret.getType();
            if( type.isPtr() ) {
              System.out.println( "    return temp "+ret+" can point to "+heapAnalysis.canPointToAfter( ret, fn ) );
            }
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
