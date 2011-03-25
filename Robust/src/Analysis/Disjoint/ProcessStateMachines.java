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
  }

  public void doProcess() {
    groupStateMachines();
    prune();
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
	if (state.e2states.contains(e))
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


  private Set<SMFEState> buildConflictsAndMap(StateMachineForEffects sm) {
    Set<SMFEState> conflictStates=new HashSet<SMFEState>();
    HashMap<SMFEState, Set<SMFEState>> backMap=new HashMap<SMFEState, Set<SMFEState>>();
    Stack<SMFEState> toprocess=new Stack<SMFEState>();
    toprocess.add(sm.initialState);
    backMap.put(sm.initialState, new HashSet<SMFEState>());
    while(!toprocess.isEmpty()) {
      SMFEState state=toprocess.pop();
      if (!state.getConflicts().isEmpty())
	conflictStates.add(state);
      for(SMFEState stateout:state.transitionsTo()) {
	if (!backMap.containsKey(stateout)) {
	  toprocess.add(stateout);
	  backMap.put(stateout, new HashSet<SMFEState>());	
	}
	backMap.get(stateout).add(state);
      }
    }
    Set<SMFEState> canReachConflicts=new HashSet<SMFEState>();
    toprocess.addAll(conflictStates);
    canReachConflicts.addAll(conflictStates);
    while(!toprocess.isEmpty()) {
      SMFEState state=toprocess.pop();

      for(SMFEState instate:backMap.get(state)) {
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