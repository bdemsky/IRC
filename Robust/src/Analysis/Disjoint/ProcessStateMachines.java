package Analysis.Disjoint;
import java.util.*;

import Analysis.OoOJava.*;
import IR.FieldDescriptor;
import IR.Flat.*;
import Util.Pair;

public class ProcessStateMachines {
  protected HashMap<FlatSESEEnterNode, Set<StateMachineForEffects>> groupMap;
  protected BuildStateMachines bsm;
  protected RBlockRelationAnalysis taskAnalysis;

  public ProcessStateMachines(BuildStateMachines bsm, RBlockRelationAnalysis taskAnalysis) {
    this.bsm=bsm;
    this.taskAnalysis=taskAnalysis;
    groupMap=new HashMap<FlatSESEEnterNode, Set<StateMachineForEffects>>();
  }

  public void doProcess() {
    groupStateMachines();
    computeConflictEffects();
    prune();
    merge();
  }

  private void merge() {
    for(Pair<FlatNode, TempDescriptor> machinepair: bsm.getAllMachineNames()) {
      StateMachineForEffects sm=bsm.getStateMachine(machinepair);
      merge(sm);
    }
  }


  private void merge(StateMachineForEffects sm) {
    HashMap<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>> backMap=buildBackMap(sm);
    boolean mergeAgain=false;
    do {
      mergeAgain=false;
      HashMap<Pair<SMFEState, FieldDescriptor>, Set<SMFEState>> revMap=buildReverse(backMap);
      for(Map.Entry<Pair<SMFEState,FieldDescriptor>, Set<SMFEState>> entry:revMap.entrySet()) {
	if (entry.getValue().size()>1) {
	  SMFEState first=null;
	  for(SMFEState state:entry.getValue()) {
	    if (first==null) {
	      first=state;
	    } else {
	      mergeAgain=true;
	      System.out.println("MERGING:"+first+" and "+state);
	      //Make sure we don't merge the initial state someplace else
	      if (state==sm.initialState) {
		state=first;
		first=sm.initialState;
	      }
	      mergeTwoStates(first, state, backMap);
	      sm.fn2state.remove(state.whereDefined);
	    }
	  }
	}
      }
    } while(mergeAgain);
  }


  private HashMap<Pair<SMFEState, FieldDescriptor>, Set<SMFEState>> buildReverse(HashMap<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>> backMap) {
    HashMap<Pair<SMFEState, FieldDescriptor>, Set<SMFEState>> revMap=new HashMap<Pair<SMFEState, FieldDescriptor>, Set<SMFEState>>();
    for(Map.Entry<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>>entry:backMap.entrySet()) {
      SMFEState state=entry.getKey();
      for(Pair<SMFEState, FieldDescriptor> pair:entry.getValue()) {
	if (!revMap.containsKey(pair))
	  revMap.put(pair, new HashSet<SMFEState>());
	revMap.get(pair).add(state);
      }
    }
    return revMap;
  }

  private void mergeTwoStates(SMFEState state1, SMFEState state2, HashMap<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>> backMap) {
    //Merge effects and conflicts
    state1.effects.addAll(state2.effects);
    state1.conflicts.addAll(state2.conflicts);

    //fix up our backmap
    backMap.get(state1).addAll(backMap.get(state2));

    //merge outgoing transitions
    for(Map.Entry<Effect, Set<SMFEState>> entry:state2.e2states.entrySet()) {
      Effect e=entry.getKey();
      Set<SMFEState> states=entry.getValue();
      if (state1.e2states.containsKey(e)) {
	for(SMFEState statetoadd:states) {
	  if (!state1.e2states.get(e).add(statetoadd)) {
	    //already added...reduce reference count
	    statetoadd.refCount--;
	  }
	}
      } else {
	state1.e2states.put(e, states);
      }
      Set<SMFEState> states1=state1.e2states.get(e);

      //move now-self edges
      if (states1.contains(state2)) {
	states1.remove(state2);
	states1.add(state1);
      }

      //fix up the backmap of the edges we point to
      for(SMFEState st:states1) {
	HashSet<Pair<SMFEState, FieldDescriptor>> toRemove=new HashSet<Pair<SMFEState, FieldDescriptor>>();
	HashSet<Pair<SMFEState, FieldDescriptor>> toAdd=new HashSet<Pair<SMFEState, FieldDescriptor>>();
	for(Pair<SMFEState, FieldDescriptor> backpair:backMap.get(st)) {
	  if (backpair.getFirst()==state2) {
	    Pair<SMFEState, FieldDescriptor> newpair=new Pair<SMFEState, FieldDescriptor>(state1, backpair.getSecond());
	    toRemove.add(backpair);
	    toAdd.add(newpair);
	  }
	}
	backMap.get(st).removeAll(toRemove);
	backMap.get(st).addAll(toAdd);
      }
    }

    //Fix up our new incoming edges
    for(Pair<SMFEState,FieldDescriptor> fromStatePair:backMap.get(state2)) {
      SMFEState fromState=fromStatePair.getFirst();
      for(Map.Entry<Effect, Set<SMFEState>> fromEntry:fromState.e2states.entrySet()) {
	Effect e=fromEntry.getKey();
	Set<SMFEState> states=fromEntry.getValue();
	if (states.contains(state2)) {
	  states.remove(state2);
	  states.add(state1);
	}
      }
    }
    //Clear out unreachable state's backmap
    backMap.remove(state2);
  }


  private void prune() {
    for(Pair<FlatNode, TempDescriptor> machinepair: bsm.getAllMachineNames()) {
      StateMachineForEffects sm=bsm.getStateMachine(machinepair);
      pruneNonConflictingStates(sm);
      pruneEffects(sm);
    }
  }

  private void pruneEffects(StateMachineForEffects sm) {
    for(Iterator<FlatNode> fnit=sm.fn2state.keySet().iterator(); fnit.hasNext();) {
      FlatNode fn=fnit.next();
      SMFEState state=sm.fn2state.get(fn);
      for(Iterator<Effect> efit=state.effects.iterator();efit.hasNext();) {
	Effect e=efit.next();
	//Is it a conflicting effecting
	if (state.getConflicts().contains(e))
	  continue;
	//Does it still have transitions
	if (state.e2states.containsKey(e))
	  continue;
	//If no to both, remove it
	efit.remove();
      }
    }
  }

  private void pruneNonConflictingStates(StateMachineForEffects sm) {
    Set<SMFEState> canReachConflicts=buildConflictsAndMap(sm);
    for(Iterator<FlatNode> fnit=sm.fn2state.keySet().iterator(); fnit.hasNext();) {
      FlatNode fn=fnit.next();
      SMFEState state=sm.fn2state.get(fn);
      if (canReachConflicts.contains(state)) {
	for(Iterator<Effect> efit=state.e2states.keySet().iterator(); efit.hasNext();) {
	  Effect e=efit.next();
	  Set<SMFEState> stateset=state.e2states.get(e);
	  for(Iterator<SMFEState> stit=stateset.iterator(); stit.hasNext();) {
	    SMFEState tostate=stit.next();
	    if(!canReachConflicts.contains(tostate))
	      stit.remove();
	  }
	  if (stateset.isEmpty())
	    efit.remove();
	}
      } else {
	fnit.remove();
      }
    }
  }
  
  private HashMap<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>> buildBackMap(StateMachineForEffects sm) {
    return buildBackMap(sm, null);
  }

  private HashMap<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>> buildBackMap(StateMachineForEffects sm, Set<SMFEState> conflictStates) {
    Stack<SMFEState> toprocess=new Stack<SMFEState>();
    HashMap<SMFEState, Set<Pair<SMFEState, FieldDescriptor>>> backMap=new HashMap<SMFEState, Set<Pair<SMFEState,FieldDescriptor>>>();
    toprocess.add(sm.initialState);
    backMap.put(sm.initialState, new HashSet<Pair<SMFEState, FieldDescriptor>>());
    while(!toprocess.isEmpty()) {
      SMFEState state=toprocess.pop();
      if (!state.getConflicts().isEmpty()&&conflictStates!=null) {
	conflictStates.add(state);
      }
      for(Effect e:state.getEffectsAllowed()) {
	for(SMFEState stateout:state.transitionsTo(e)) {
	  if (!backMap.containsKey(stateout)) {
	    toprocess.add(stateout);
	    backMap.put(stateout, new HashSet<Pair<SMFEState,FieldDescriptor>>());
	  }
	  Pair<SMFEState, FieldDescriptor> p=new Pair<SMFEState, FieldDescriptor>(state, e.getField());
	  backMap.get(stateout).add(p);
	}
      }
    }
    return backMap;
  }

  
  private Set<SMFEState> buildConflictsAndMap(StateMachineForEffects sm) {
    Set<SMFEState> conflictStates=new HashSet<SMFEState>();
    HashMap<SMFEState, Set<Pair<SMFEState,FieldDescriptor>>> backMap=buildBackMap(sm, conflictStates);

    Stack<SMFEState> toprocess=new Stack<SMFEState>();
    Set<SMFEState> canReachConflicts=new HashSet<SMFEState>();
    toprocess.addAll(conflictStates);
    canReachConflicts.addAll(conflictStates);
    while(!toprocess.isEmpty()) {
      SMFEState state=toprocess.pop();

      for(Pair<SMFEState,FieldDescriptor> instatepair:backMap.get(state)) {
	SMFEState instate=instatepair.getFirst();
	if (!canReachConflicts.contains(instate)) {
	  toprocess.add(instate);
	  canReachConflicts.add(instate);
	}
      }
    }
    return canReachConflicts;
  }
  
  private void groupStateMachines() {
    for(Pair<FlatNode, TempDescriptor> machinePair: bsm.getAllMachineNames()) {
      FlatNode fn=machinePair.getFirst();
      StateMachineForEffects sm=bsm.getStateMachine(machinePair);
      Set<FlatSESEEnterNode> taskSet=taskAnalysis.getPossibleExecutingRBlocks(fn);
      for(FlatSESEEnterNode sese:taskSet) {
	if (!groupMap.containsKey(sese))
	  groupMap.put(sese, new HashSet<StateMachineForEffects>());
	groupMap.get(sese).add(sm);
      }
    }
  }

  private void computeConflictEffects() {
    //Loop through all state machines
    for(Pair<FlatNode, TempDescriptor> machinePair: bsm.getAllMachineNames()) {
      FlatNode fn=machinePair.getFirst();
      StateMachineForEffects sm=bsm.getStateMachine(machinePair);
      Set<FlatSESEEnterNode> taskSet=taskAnalysis.getPossibleExecutingRBlocks(fn);
      for(FlatSESEEnterNode sese:taskSet) {
	Set<StateMachineForEffects> smgroup=groupMap.get(sese);
	computeConflictingEffects(sm, smgroup);
      }
    }
  }
  
  private void computeConflictingEffects(StateMachineForEffects sm, Set<StateMachineForEffects> smgroup) {
    boolean isStall=sm.getStallorSESE().kind()!=FKind.FlatSESEEnterNode;
    for(SMFEState state:sm.getStates()) {
      for(Effect e:state.getEffectsAllowed()) {
	Alloc a=e.getAffectedAllocSite();
	FieldDescriptor fd=e.getField();
	int type=e.getType();
	boolean hasConflict=false;
	if (!isStall&&Effect.isWrite(type)) {
	  hasConflict=true;
	} else {
	  for(StateMachineForEffects othersm:smgroup) {
	    boolean otherIsStall=othersm.getStallorSESE().kind()!=FKind.FlatSESEEnterNode;
	    //Stall sites can't conflict with each other
	    if (isStall&&otherIsStall) continue;

	    int effectType=othersm.getEffects(a, fd);
	    if (Effect.isWrite(type)&&effectType!=0) {
	      //Original effect is a write and we have some effect on the same field/alloc site
	      hasConflict=true;
	      break;
	    }
	    if (Effect.isWrite(effectType)) {
	      //We are a write
	      hasConflict=true;
	      break;
	    }
	  }
	}
	if (hasConflict) {
	  state.addConflict(e);
	}
      }
    }
  }
}