package Analysis.Disjoint;

import java.util.*;
import java.io.*;

import IR.FieldDescriptor;
import IR.Flat.FlatCall;
import IR.Flat.FlatMethod;
import IR.Flat.TempDescriptor;
import IR.Flat.FlatSESEEnterNode;

/////////////////////////////////////////////
// 
//  Effects analysis computes read/write/strong
//  update and other sorts of effects for the
//  scope of a method or rblock.  The effects
//  are associated with the heap roots through
//  which a reference to the effect target was
//  obtained.
//
//  The effects analysis piggy-backs
//  on the disjoint reachability analysis,
//  if requested, to support OoOJava and
//  potentially other analysis clients.
//
/////////////////////////////////////////////

public class EffectsAnalysis {

  private Hashtable<FlatMethod,        EffectSet> fm2effectSet;
  private Hashtable<FlatSESEEnterNode, EffectSet> sese2effectSet;

  public EffectsAnalysis() {
    fm2effectSet   = new Hashtable<FlatMethod,        EffectSet>();
    sese2effectSet = new Hashtable<FlatSESEEnterNode, EffectSet>();
  }

  public void analyzeFlatFieldNode(FlatMethod fmContaining, 
                                   FlatSESEEnterNode seseContaining,
                                   ReachGraph rg, TempDescriptor rhs, FieldDescriptor fld) {

    VariableNode vn = rg.td2vn.get(rhs);
    if( vn == null ) {
      return;
    }

    for (Iterator<RefEdge> iterator = vn.iteratorToReferencees(); iterator.hasNext();) {
      RefEdge edge = iterator.next();
      TaintSet taintSet = edge.getTaints();
      AllocSite affectedAlloc = edge.getDst().getAllocSite();
      for (Iterator<Taint> taintSetIter = taintSet.iterator(); taintSetIter.hasNext();) {
        Taint taint = taintSetIter.next();

        EffectSet effectSet = fm2effectSet.get(fmContaining);
        if (effectSet == null) {
          effectSet = new EffectSet();
        }
        
        Effect effect = new Effect(affectedAlloc, Effect.read, fld);

        add( fmContaining,   taint, effect );
        add( seseContaining, taint, effect );        
      }
    }
  }

  public void analyzeFlatSetFieldNode(FlatMethod fmContaining,
                                      FlatSESEEnterNode seseContaining,
                                      ReachGraph rg, TempDescriptor lhs, FieldDescriptor fld, boolean strongUpdate) {

    VariableNode vn = rg.td2vn.get(lhs);
    if( vn == null ) {
      return;
    }

    for (Iterator<RefEdge> iterator = vn.iteratorToReferencees(); iterator.hasNext();) {
      RefEdge edge = iterator.next();
      TaintSet taintSet = edge.getTaints();
      AllocSite affectedAlloc = edge.getDst().getAllocSite();
      for (Iterator<Taint> taintSetIter = taintSet.iterator(); taintSetIter.hasNext();) {
        Taint taint = taintSetIter.next();
        
        Effect effect = new Effect(affectedAlloc, Effect.write, fld);       
        add( fmContaining,   taint, effect );       
        add( seseContaining, taint, effect );
        
        if (strongUpdate) {
          Effect effectSU = new Effect(affectedAlloc, Effect.strongupdate, fld);          
          add( fmContaining,   taint, effect );          
          add( seseContaining, taint, effect );
        }
      }
    }
  }

  public void analyzeFlatCall(FlatMethod fmContaining, FlatSESEEnterNode seseContaining, 
                              FlatMethod fmCallee, Hashtable<Taint, TaintSet> tCallee2tsCaller) {
        
    EffectSet esCallee = getEffectSet(fmCallee);
    if( esCallee == null ) {
      esCallee = new EffectSet();
    }

    Iterator meItr = esCallee.getAllEffectPairs();
    while( meItr.hasNext() ) {
      Map.Entry       me      = (Map.Entry)       meItr.next();
      Taint           tCallee = (Taint)           me.getKey();
      HashSet<Effect> effects = (HashSet<Effect>) me.getValue();

      if( tCallee2tsCaller.containsKey( tCallee ) ) {

        Iterator<Taint> tItr = tCallee2tsCaller.get( tCallee ).iterator();
        while( tItr.hasNext() ) {
          Taint tCaller = tItr.next();
          
          EffectSet esCaller = new EffectSet();

          Iterator<Effect> eItr = effects.iterator();
          while( eItr.hasNext() ) {
            Effect e = eItr.next();
            
            esCaller.addEffect( tCaller, e );
          }

          add( fmContaining,   tCaller, esCaller );
          add( seseContaining, tCaller, esCaller );    
        }
      }
    }
  }

  public EffectSet getEffectSet(FlatMethod fm) {
    return fm2effectSet.get(fm);
  }

  public void writeEffectsPerMethodAndRBlock( String outfile ) {
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
      
      bw.write( "Effects Per Method\n\n" );

      Iterator meItr1 = fm2effectSet.entrySet().iterator();
      while( meItr1.hasNext() ) {
        Map.Entry  me1 = (Map.Entry)  meItr1.next();
        FlatMethod fm  = (FlatMethod) me1.getKey();
        EffectSet  es  = (EffectSet)  me1.getValue();

        bw.write( "\n"+fm+"\n--------------\n" );

        Iterator meItr2 = es.getAllEffectPairs();
        while( meItr2.hasNext() ) {
          Map.Entry       me2     = (Map.Entry)       meItr2.next();
          Taint           taint   = (Taint)           me2.getKey();
          HashSet<Effect> effects = (HashSet<Effect>) me2.getValue();

          Iterator<Effect> eItr = effects.iterator();
          while( eItr.hasNext() ) {
            Effect e = eItr.next();
            
            bw.write( "  "+taint+"-->"+e+"\n" );
          }
        }
      }

      
      bw.write( "\n\nEffects Per RBlock\n\n" );

      meItr1 = sese2effectSet.entrySet().iterator();
      while( meItr1.hasNext() ) {
        Map.Entry         me1  = (Map.Entry)         meItr1.next();
        FlatSESEEnterNode sese = (FlatSESEEnterNode) me1.getKey();
        EffectSet         es   = (EffectSet)         me1.getValue();

        bw.write( "\n"+sese.toPrettyString()+"\n--------------\n" );

        Iterator meItr2 = es.getAllEffectPairs();
        while( meItr2.hasNext() ) {
          Map.Entry       me2     = (Map.Entry)       meItr2.next();
          Taint           taint   = (Taint)           me2.getKey();
          HashSet<Effect> effects = (HashSet<Effect>) me2.getValue();

          Iterator<Effect> eItr = effects.iterator();
          while( eItr.hasNext() ) {
            Effect e = eItr.next();
            
            bw.write( "  "+taint+"-->"+e+"\n" );
          }
        }
      }

      bw.close();
    } catch( IOException e ) {}
  }

  protected void add( FlatMethod fm, Taint t, Effect e ) {
    EffectSet es = fm2effectSet.get( fm );    
    if( es == null ) {
      es = new EffectSet();
    }
    es.addEffect( t, e );
    
    fm2effectSet.put( fm, es );
  }

  protected void add( FlatSESEEnterNode sese, Taint t, Effect e ) {

    if( sese.getIsCallerSESEplaceholder() ) {
      return;
    }

    EffectSet es = sese2effectSet.get( sese );    
    if( es == null ) {
      es = new EffectSet();
    }
    es.addEffect( t, e );
    
    sese2effectSet.put( sese, es );
  }

  protected void add( FlatMethod fm, Taint t, EffectSet es ) {
    EffectSet esExisting = fm2effectSet.get( fm );    
    if( esExisting == null ) {
      esExisting = new EffectSet();
    }

    Iterator meItr = es.getAllEffectPairs();
    while( meItr.hasNext() ) {
      Map.Entry       me      = (Map.Entry)       meItr.next();
      Taint           taint   = (Taint)           me.getKey();
      HashSet<Effect> effects = (HashSet<Effect>) me.getValue();
      
      Iterator<Effect> eItr = effects.iterator();
      while( eItr.hasNext() ) {
        Effect e = eItr.next();

        esExisting.addEffect( taint, e );
      }      
    }
    
    fm2effectSet.put( fm, esExisting );
  }

  protected void add( FlatSESEEnterNode sese, Taint t, EffectSet es ) {

    if( sese.getIsCallerSESEplaceholder() ) {
      return;
    }

    EffectSet esExisting = sese2effectSet.get( sese );    

    if( esExisting == null ) {
      esExisting = new EffectSet();
    }

    Iterator meItr = es.getAllEffectPairs();
    while( meItr.hasNext() ) {
      Map.Entry       me      = (Map.Entry)       meItr.next();
      Taint           taint   = (Taint)           me.getKey();
      HashSet<Effect> effects = (HashSet<Effect>) me.getValue();
      
      Iterator<Effect> eItr = effects.iterator();
      while( eItr.hasNext() ) {
        Effect e = eItr.next();

        esExisting.addEffect( taint, e );
      }      
    }

    sese2effectSet.put( sese, esExisting );
  }


  /*
   * public MethodEffects getMethodEffectsByMethodContext(MethodContext mc){
   * return mapMethodContextToMethodEffects.get(mc); }
   * 
   * public void createNewMapping(MethodContext mcNew) { if(!methodeffects)
   * return; if (!mapMethodContextToMethodEffects.containsKey(mcNew)) {
   * MethodEffects meNew = new MethodEffects();
   * mapMethodContextToMethodEffects.put(mcNew, meNew); } }
   */

  /*
   * public void analyzeFlatCall(OwnershipGraph calleeOG, MethodContext
   * calleeMC, MethodContext callerMC, FlatCall fc) { if(!methodeffects) return;
   * MethodEffects me = mapMethodContextToMethodEffects.get(callerMC);
   * MethodEffects meFlatCall = mapMethodContextToMethodEffects .get(calleeMC);
   * me.analyzeFlatCall(calleeOG, fc, callerMC, meFlatCall);
   * mapMethodContextToMethodEffects.put(callerMC, me); }
   */

  /*
   * public void analyzeFlatFieldNode(MethodContext mc, OwnershipGraph og,
   * TempDescriptor srcDesc, FieldDescriptor fieldDesc) { if(!methodeffects)
   * return; MethodEffects me = mapMethodContextToMethodEffects.get(mc);
   * me.analyzeFlatFieldNode(og, srcDesc, fieldDesc);
   * mapMethodContextToMethodEffects.put(mc, me); }
   * 
   * public void analyzeFlatSetFieldNode(MethodContext mc, OwnershipGraph og,
   * TempDescriptor dstDesc, FieldDescriptor fieldDesc) { if(!methodeffects)
   * return; MethodEffects me = mapMethodContextToMethodEffects.get(mc);
   * me.analyzeFlatSetFieldNode(og, dstDesc, fieldDesc);
   * mapMethodContextToMethodEffects.put(mc, me); }
   * 
   * public void analyzeFlatSetElementNode(MethodContext mc, OwnershipGraph og,
   * TempDescriptor dstDesc, FieldDescriptor fieldDesc) { if(!methodeffects)
   * return; MethodEffects me = mapMethodContextToMethodEffects.get(mc);
   * me.analyzeFlatSetElementNode(og, dstDesc, fieldDesc);
   * mapMethodContextToMethodEffects.put(mc, me); }
   * 
   * public void analyzeFlatElementNode(MethodContext mc, OwnershipGraph og,
   * TempDescriptor dstDesc, FieldDescriptor fieldDesc) { if(!methodeffects)
   * return; MethodEffects me = mapMethodContextToMethodEffects.get(mc);
   * me.analyzeFlatElementNode(og, dstDesc, fieldDesc);
   * mapMethodContextToMethodEffects.put(mc, me); }
   * 
   * 
   * public void writeMethodEffectsResult() throws IOException {
   * 
   * try { BufferedWriter bw = new BufferedWriter(new FileWriter(
   * "MethodEffects_report.txt"));
   * 
   * Set<MethodContext> mcSet = mapMethodContextToMethodEffects.keySet();
   * Iterator<MethodContext> mcIter = mcSet.iterator(); while (mcIter.hasNext())
   * { MethodContext mc = mcIter.next(); MethodDescriptor md =
   * (MethodDescriptor) mc.getDescriptor();
   * 
   * int startIdx = 0; if (!md.isStatic()) { startIdx = 1; }
   * 
   * MethodEffects me = mapMethodContextToMethodEffects.get(mc); EffectsSet
   * effectsSet = me.getEffects();
   * 
   * bw.write("Method " + mc + " :\n"); for (int i = startIdx; i <
   * md.numParameters() + startIdx; i++) {
   * 
   * String paramName = md.getParamName(i - startIdx);
   * 
   * Set<EffectsKey> effectSet = effectsSet.getReadingSet(i); String keyStr =
   * "{"; if (effectSet != null) { Iterator<EffectsKey> effectIter =
   * effectSet.iterator(); while (effectIter.hasNext()) { EffectsKey key =
   * effectIter.next(); keyStr += " " + key; } } keyStr += " }";
   * bw.write("  Paramter " + paramName + " ReadingSet=" + keyStr + "\n");
   * 
   * effectSet = effectsSet.getWritingSet(new Integer(i)); keyStr = "{"; if
   * (effectSet != null) { Iterator<EffectsKey> effectIter =
   * effectSet.iterator(); while (effectIter.hasNext()) { EffectsKey key =
   * effectIter.next(); keyStr += " " + key; } }
   * 
   * keyStr += " }"; bw.write("  Paramter " + paramName + " WritingngSet=" +
   * keyStr + "\n");
   * 
   * } bw.write("\n");
   * 
   * }
   * 
   * bw.close(); } catch (IOException e) { System.err.println(e); }
   * 
   * }
   */
}
