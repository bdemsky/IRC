package Analysis.Disjoint;

import java.util.*;
import java.util.Map.Entry;
import java.io.*;

import IR.*;
import IR.Flat.*;
import Analysis.Pointer.Edge;
import Analysis.Pointer.AllocFactory.AllocNode;

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

  // the effects analysis should combine taints
  // that match except for predicates--preds just
  // support interprocedural analysis
  private Hashtable<Taint, Set<Effect>> taint2effects;

  // redundant views of the effect set for
  // efficient retrieval
  private Hashtable<FlatSESEEnterNode, Hashtable<Taint, Set<Effect>> > sese2te;
  private Hashtable<FlatNode,          Hashtable<Taint, Set<Effect>> > stallSite2te;

  public static State              state;
  public static BuildStateMachines buildStateMachines;


  public EffectsAnalysis() {
    taint2effects = new Hashtable<Taint, Set<Effect>>();

    sese2te      = new Hashtable<FlatSESEEnterNode, Hashtable<Taint, Set<Effect>> >();
    stallSite2te = new Hashtable<FlatNode,          Hashtable<Taint, Set<Effect>> >();
  }


  public Set<Effect> getEffects(Taint t) {
    Taint tNoPreds = Canonical.changePredsTo( t,
                                              ReachGraph.predsEmpty
                                              );
    return taint2effects.get(tNoPreds);
  }

  public Iterator iteratorTaintEffectPairs() {
    return taint2effects.entrySet().iterator();
  }

  protected void add(Taint t, Effect e, FlatNode currentProgramPoint) {
    Taint tNoPreds = Canonical.changePredsTo( t,
                                              ReachGraph.predsEmpty
                                              );

    if( state.RCR ) {
      buildStateMachines.addToStateMachine( t, e, currentProgramPoint );
    }

    // add to the global bag
    Set<Effect> effectSet = taint2effects.get(tNoPreds);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);
    taint2effects.put(tNoPreds, effectSet);

    // add to the alternate retrieval bags
    if( t.getSESE() != null ) {
      FlatSESEEnterNode sese = t.getSESE();

      Hashtable<Taint, Set<Effect>> te = sese2te.get( sese );
      if( te == null ) {
        te = new Hashtable<Taint, Set<Effect>>();
      }

      Set<Effect> effects = te.get(tNoPreds);
      if (effects == null) {
        effects = new HashSet<Effect>();
      }
      effects.add(e);
      te.put(tNoPreds, effects);

      sese2te.put(sese, te);

    } else {
      assert t.getStallSite() != null;
      FlatNode stallSite = t.getStallSite();

      Hashtable<Taint, Set<Effect>> te = stallSite2te.get( stallSite );
      if( te == null ) {
        te = new Hashtable<Taint, Set<Effect>>();
      }

      Set<Effect> effects = te.get(tNoPreds);
      if (effects == null) {
        effects = new HashSet<Effect>();
      }
      effects.add(e);
      te.put(tNoPreds, effects);
      stallSite2te.put(stallSite, te);
    }    
  }


  public Hashtable<Taint, Set<Effect>> get( FlatSESEEnterNode sese ) {
    return sese2te.get(sese);
  }

  public Hashtable<Taint, Set<Effect>> get( FlatNode stallSite ) {
    return stallSite2te.get(stallSite);
  }



  public void analyzeFlatFieldNode(ReachGraph rg, TempDescriptor rhs, FieldDescriptor fld, FlatNode currentProgramPoint) {

    VariableNode vn = rg.td2vn.get(rhs);
    if( vn == null ) {
      return;
    }

    for (Iterator<RefEdge> iterator = vn.iteratorToReferencees(); iterator.hasNext();) {
      RefEdge   edge          = iterator.next();
      TaintSet  taintSet      = edge.getTaints();
      AllocSite affectedAlloc = edge.getDst().getAllocSite();
      Effect    effect        = new Effect(affectedAlloc, Effect.read, fld);

      for (Iterator<Taint> taintSetIter = taintSet.iterator(); taintSetIter.hasNext();) {
        Taint taint = taintSetIter.next();        
        add(taint, effect, currentProgramPoint);
      }
    }
  }

  public void analyzeFlatFieldNode(Set<Edge> sources, FieldDescriptor fld, FlatNode currentProgramPoint) {
    for (Edge edge:sources) {
      TaintSet  taintSet      = edge.getTaints();
      Alloc     affectedAlloc = edge.getDst().getAllocSite();
      Effect    effect        = new Effect(affectedAlloc, Effect.read, fld);

      if (taintSet!=null)
	for (Taint taint:taintSet.getTaints()) {
	  add(taint, effect, currentProgramPoint);
	}
    }
  }

  public void analyzeFlatSetFieldNode(ReachGraph rg, TempDescriptor lhs, FieldDescriptor fld, FlatNode currentProgramPoint, boolean strongUpdate) {

    VariableNode vn = rg.td2vn.get(lhs);
    if( vn == null ) {
      return;
    }

    for (Iterator<RefEdge> iterator = vn.iteratorToReferencees(); iterator.hasNext();) {
      RefEdge   edge          = iterator.next();
      TaintSet  taintSet      = edge.getTaints();
      AllocSite affectedAlloc = edge.getDst().getAllocSite();
      Effect    effect        = new Effect(affectedAlloc, Effect.write, fld);       
      Effect    effectSU      = null;

      if (strongUpdate) {
        effectSU = new Effect(affectedAlloc, Effect.strongupdate, fld);
      }

      for (Iterator<Taint> taintSetIter = taintSet.iterator(); taintSetIter.hasNext();) {
        Taint taint = taintSetIter.next();
        add( taint, effect, currentProgramPoint );

        if (strongUpdate) {
          add( taint, effectSU, currentProgramPoint );
        }
      }
    }
  }


  public void analyzeFlatSetFieldNode(Set<Edge> dstedges, FieldDescriptor fld, FlatNode currentProgramPoint) {
    for (Edge edge:dstedges) {
      TaintSet taintSet = edge.getTaints();
      Alloc affectedAlloc = edge.getDst().getAllocSite();
      Effect effect = new Effect(affectedAlloc, Effect.write, fld);
      if (taintSet!=null)
	for (Taint taint:taintSet.getTaints()) {
	  add(taint, effect, currentProgramPoint );
	}
    }
  }


  public String toString() {
    return taint2effects.toString();    
  }

  public void writeEffects( String outfile ) {
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
      
      bw.write( "Effects\n---------------\n\n" );

      Iterator meItr = taint2effects.entrySet().iterator();
      while( meItr.hasNext() ) {
        Map.Entry   me      = (Map.Entry)   meItr.next();
        Taint       taint   = (Taint)       me.getKey();
        Set<Effect> effects = (Set<Effect>) me.getValue();

        Iterator<Effect> eItr = effects.iterator();
        while( eItr.hasNext() ) {
          Effect e = eItr.next();
            
          bw.write( taint+"-->"+e+"\n" );          
        }
      }

      bw.close();
    } catch( IOException e ) {}
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
  
  public Hashtable<Taint, Set<Effect>> getAllEffects() {
    return taint2effects;
  }
}
