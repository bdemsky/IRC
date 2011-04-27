package Analysis.Disjoint;

import java.util.*;
import java.io.*;

import IR.*;
import IR.Flat.*;
import Util.Pair;

//////////////////////////////////////////////
//
//  StateMachineForEffects describes an intial
//  state and the effect transtions a DFJ
//  traverser should make from the current state
//  when searching for possible runtime conflicts.
//
//////////////////////////////////////////////

public class StateMachineForEffects {
  public final static FlatNode startNode=new FlatNop();
  protected HashMap<Pair<Alloc, FieldDescriptor>, Integer> effectsMap;

  // states in the machine are uniquely identified
  // by a flat node (program point)
  protected Hashtable<FlatNode, SMFEState> fn2state;

  //TODO Jim! Jim! Give me the weakly connected group number here!
  protected Hashtable<FlatNode, Integer> fn2weaklyConnectedGroupID;

  protected SMFEState initialState;
  protected FlatNode fn;
  protected int id=0;

  // We have a situation in which a task can start executing
  // and "evil-ly" destroy the paths to all the objects it will
  // access as it goes along.  If this is the case, a traverser
  // should know which effects these are, and if the traverser
  // is ever running and detects that the task is also running,
  // it should not continue (and therefore hold up any tasks that
  // might come later, because now we might never be able to find
  // the effects that should block later tasks).  Should be rare!
  protected Set<Effect> possiblyEvilEffects;


  public StateMachineForEffects(FlatNode fnInitial) {
    fn2state = new Hashtable<FlatNode, SMFEState>();
    effectsMap = new HashMap<Pair<Alloc, FieldDescriptor>, Integer>();
    initialState = getState(startNode);
    this.fn=fnInitial;
    possiblyEvilEffects = new HashSet<Effect>();
  }

  public Set<SMFEState> getStates() {
    HashSet<SMFEState> set=new HashSet<SMFEState>();
    set.addAll(fn2state.values());
    return set;
  }

  public FlatNode getStallorSESE() {
    return fn;
  }

  public boolean isEmpty() {
    for(FlatNode fn : fn2state.keySet()) {
      SMFEState state=fn2state.get(fn);
      if (!state.getConflicts().isEmpty())
        return false;
    }
    return true;
  }

  public int getEffects(Alloc affectedNode, FieldDescriptor fd) {
    Integer type=effectsMap.get(new Pair<Alloc, FieldDescriptor>(affectedNode, fd));
    if (type==null)
      return 0;
    else
      return type.intValue();
  }

  public void addEffect(FlatNode fnState, Effect e) {
    if (fnState==null)
      fnState=startNode;
    SMFEState state = getState(fnState);
    state.addEffect(e);
    Pair<Alloc, FieldDescriptor> p=new Pair<Alloc, FieldDescriptor>(e.getAffectedAllocSite(), e.getField());
    int type=e.getType();
    if (!effectsMap.containsKey(p))
      effectsMap.put(p, new Integer(type));
    else
      effectsMap.put(p, new Integer(type|effectsMap.get(p).intValue()));
  }

  public void addTransition(FlatNode fnFrom,
                            FlatNode fnTo,
                            Effect e) {
    if (fnFrom==null)
      fnFrom=startNode;

    assert fn2state.containsKey(fnFrom);
    SMFEState stateFrom = getState(fnFrom);
    SMFEState stateTo   = getState(fnTo);

    stateFrom.addTransition(e, stateTo);
  }

  public SMFEState getInitialState() {
    return initialState;
  }


  protected SMFEState getState(FlatNode fn) {
    SMFEState state = fn2state.get(fn);
    if( state == null ) {
      state = new SMFEState(fn,id++);
      fn2state.put(fn, state);
    }
    return state;
  }

  public Integer getWeaklyConnectedGroupID(FlatNode fn) {
    //TODO stubby stubby!
    return 0;
  }


  public void addPossiblyEvilEffect(Effect e) {
    possiblyEvilEffects.add(e);
  }

  public Set<Effect> getPossiblyEvilEffects() {
    return possiblyEvilEffects;
  }


  public void writeAsDOT(String graphName) {
    graphName = graphName.replaceAll("[\\W]", "");

    try {
      BufferedWriter bw =
        new BufferedWriter(new FileWriter(graphName+".dot") );

      bw.write("digraph "+graphName+" {\n");

      Iterator<FlatNode> fnItr = fn2state.keySet().iterator();
      while( fnItr.hasNext() ) {
        SMFEState state = fn2state.get(fnItr.next() );
        bw.write(state.toStringDOT()+"\n");
      }

      bw.write("}\n");
      bw.close();

    } catch( IOException e ) {
      throw new Error("Error writing out DOT graph "+graphName);
    }
  }

}
