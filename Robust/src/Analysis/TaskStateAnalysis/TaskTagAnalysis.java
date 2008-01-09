package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;

public class TaskTagAnalysis {
    State state;
    TagAnalysis taganalysis;
    TypeUtil typeutil;
    FlagInfo flaginfo;
    HashSet<TagState> toprocess;
    Hashtable<TaskDescriptor, TaskQueue> tasktable;
    

    /** 
     * Class Constructor
     *
     */
    public TaskTagAnalysis(State state, TagAnalysis taganalysis) {
	this.state=state;
	this.typeutil=new TypeUtil(state);
	this.taganalysis=taganalysis;
	this.flaginfo=new FlagInfo(state);
	this.toprocess=new HashSet<TagState>();
	this.tasktable=new Hashtable<TaskDescriptor, TaskQueue>();
	for(Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();taskit.hasNext();) {
	    TaskDescriptor td=(TaskDescriptor)taskit.next();
	    tasktable.put(td, new TaskQueue(td));
	}
    }

    private void doAnalysis() {
	toprocess.add(createInitialState());

	while(!toprocess.isEmpty()) {
	    TagState ts=toprocess.iterator().next();
	    toprocess.remove(ts);
	    //Loop through each task
	    for(Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();taskit.hasNext();) {
		TaskDescriptor td=(TaskDescriptor)taskit.next();
		TaskQueue tq=tasktable.get(td);
		processTask(td, tq, ts);
	    }
	}
    }

    private void processTask(TaskDescriptor td, TaskQueue tq, TagState ts) {
	Set<FlagState> flagset=ts.getFS();
	for(Iterator<FlagState> fsit=flagset.iterator();fsit.hasNext();) {
	    FlagState fs=fsit.next();
	    FlagTagState fts=new FlagTagState(ts, fs);
	    for(int i=0;i<td.numParameters();i++) {
		if (canEnqueue(td, i, fs)) {
		    TaskQueueIterator tqi=tq.enqueue(i, fts);
		    while(tqi.hasNext()) {
			processBinding(tqi);
			tqi.next();
		    }
		}
	    }
	}
    }

    private void processBinding(TaskQueueIterator tqi) {
	TaskBinding tb=new TaskBinding(tqi);
	while(tb.hasNext()) {
	    doAnalysis(tb);
	    tb.next();
	}
    }

    private Hashtable<TempDescriptor, Wrapper> computeInitialState(Hashtable<FlatNode, Hashtable<TempDescriptor, Wrapper>> maintable, FlatNode fn) {
	Hashtable<TempDescriptor, Wrapper> table=new Hashtable<TempDescriptor, Wrapper>();
	Hashtable<TagWrapper,TagWrapper> tagtable=new Hashtable<TagWrapper, TagWrapper>();
	for(int i=0;i<fn.numPrev();i++) {
	    FlatNode fnprev=fn.getPrev(i);
	    Hashtable<TempDescriptor, Wrapper> prevtable=maintable.get(fn);

	    //Iterator through the Tags
	    for(Iterator<TempDescriptor> tmpit=prevtable.keySet().iterator();tmpit.hasNext();) {
		TempDescriptor tmp=tmpit.next();
		Wrapper prevtag=prevtable.get(tmp);
		if (prevtag instanceof ObjWrapper)
		    continue;
		if (table.containsKey(tmp)) {
		    //merge tag states
		    TagWrapper currtag=(TagWrapper) table.get(tmp);
		    tagtable.put((TagWrapper)prevtag, currtag);
		    assert(currtag.initts.equals(((TagWrapper)prevtag).initts));
		    for(Iterator<TagState> tagit=((TagWrapper)prevtag).ts.iterator();tagit.hasNext();) {
			TagState tag=tagit.next();
			if (!currtag.ts.contains(tag)) {
			    currtag.ts.add(tag);
			}
		    }
		} else {
		    TagWrapper clonetag=prevtag.clone();
		    tagtable.put(prevtag, clonetag);
		    table.put(tmp, clonetag);
		}
	    }

	    //Iterator through the Objects
	    for(Iterator<TempDescriptor> tmpit=prevtable.keySet().iterator();tmpit.hasNext();) {
		TempDescriptor tmp=tmpit.next();
		Wrapper obj=prevtable.get(tmp);
		if (obj instanceof TagWrapper)
		    continue;
		ObjWrapper prevobj=(ObjWrapper)obj;
		if (table.containsKey(tmp)) {
		    //merge tag states
		    ObjWrapper newobj=new ObjWrapper(prevobj.fs);
		    table.put(tmp, newobj);
		}
		ObjWrapper currobj=(ObjWrapper) table.get(tmp);
		for(int j=0;j<prevobj.tags.size();j++) {
		    TagWrapper t=tagtable.get(prevobj.tags.get(j));
		    if (!currobj.tags.contains(t))
			currobj.tags.add(t);
		}
	    }
	}
	return table;
    }

    private void processFlatFlag(FlatFlagActionNode fn, Hashtable<TempDescriptor, TagState> table) {

    }

    private void processFlatCall(FlatCall fc, Hashtable<TempDescriptor, TagState> table) {

    }

    private void processFlatReturnNode(FlatReturnNode fr, Hashtable<TempDescriptor, TagState> table) {

    }

    private boolean equivalent(Hashtable<TempDescriptor, TagState> table1, Hashtable<TempDescriptor, TagState> table2) {

    }

    private void doAnalysis(TaskBinding tb) {
	TaskDescriptor td=tb.tqi.tq.getTask();
	FlatMethod fm=state.getMethodFlat(td);
	Hashtable<FlatNode, Hashtable<TempDescriptor, Wrapper>> wtable=new Hashtable<FlatNode, Hashtable<TempDescriptor, Wrapper>>();
	wtable.put(fm, buildinittable(tb));
	HashSet<FlatNode> visited=new HashSet<FlatNode>();
	HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
	tovisit.add(fm.getNext(0));
	while(!tovisit.isEmpty()) {
	    FlatNode fn=tovisit.iterator().next();
	    tovisit.remove(fn);
	    visited.add(fn);
	    Hashtable<TempDescriptor, TagState> table=computeInitialState(wtable, fn);
	    switch(fn.kind()) {
	    case FKind.FlatFlagActionNode:
		processFlatFlag((FlatFlagActionNode)fn, table);
		break;
	    case FKind.FlatCall:
		processFlatCall((FlatCall)fn, table);
		break;
	    case FKind.FlatReturnNode:
		processFlatReturnNode((FlatReturn)fn, table);
		break;
	    default:
	    }

	    if (!equivalent(table, wtable.get(fn))) {
		wtable.put(fn, table);
		for(int i=0;i<fn.numNext();i++) {
		    tovisit.add(fn.getNext(i));
		}
	    } else {
		for(int i=0;i<fn.numNext();i++) {
		    if (!visited.contains(fn.getNext(i)))
			tovisit.add(fn.getNext(i));
		}
	    }
	}
	
    }

    private Hashtable<TempDescriptor, Wrapper> buildinittable(TaskBinding tb, FlatMethod fm) {
	Hashtable<TempDescriptor, Wrapper> table=new Hashtable<TempDescriptor, Wrapper>();
	Vector<TempDescriptor> tagtmps=tb.tqi.tq.tags;
	for(int i=0;i<tagtmps.size();i++) {
	    TempDescriptor tmp=tagtmps.get(i);
	    table.put(tmp, tb.getTag(tmp));
	}
	for(int i=0;i<fm.numParameters();i++) {
	    TempDescriptor tmp=fm.getParameter(i);
	    table.put(tmp, tb.getParameter(i));
	}
	return table;
    }

    /*
      method summary:
      new flag states created
      new tag states created
      flag states bound to tag parameters
    */

    public boolean canEnqueue(TaskDescriptor td, int paramnum, FlagState fs) {
	return typeutil.isSuperorType(td.getParamType(paramnum).getClassDesc(),fs.getClassDescriptor())&&
	    isTaskTrigger_flag(td.getFlag(td.getParameter(paramnum)),fs)&&
	    isTaskTrigger_tag(td.getTag(td.getParameter(paramnum)),fs);
    }

    private static boolean isTaskTrigger_flag(FlagExpressionNode fen, FlagState fs) {
	if (fen==null)
	    return true;
	else if (fen instanceof FlagNode)
	    return fs.get(((FlagNode)fen).getFlag());
	else
	    switch (((FlagOpNode)fen).getOp().getOp()) {
	    case Operation.LOGIC_AND:
		return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) && (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));
	    case Operation.LOGIC_OR:
		return ((isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs)) || (isTaskTrigger_flag(((FlagOpNode)fen).getRight(),fs)));
	    case Operation.LOGIC_NOT:
		return !(isTaskTrigger_flag(((FlagOpNode)fen).getLeft(),fs));
	    default:
		return false;
	    }
    }
    
    
    private static boolean isTaskTrigger_tag(TagExpressionList tel, FlagState fs){
        if (tel!=null){
	    for (int i=0;i<tel.numTags() ; i++){
                switch (fs.getTagCount(tel.getType(i))){
		case FlagState.ONETAG:
		case FlagState.MULTITAGS:
		    break;
		case FlagState.NOTAGS:
		    return false;
                }
	    }
        }
        return true;
    }

    TagState createInitialState() {
	ClassDescriptor startupobject=typeutil.getClass(TypeUtil.StartupClass);
	FlagDescriptor fd=(FlagDescriptor)startupobject.getFlagTable().get(FlagDescriptor.InitialFlag);
	FlagState fsstartup=(new FlagState(startupobject)).setFlag(fd,true);
	fsstartup.setAsSourceNode();
	fsstartup=canonical(fsstartup);
	TagState ts=new TagState();
	TagState[] tsarray=ts.addFS(fsstartup);
	return canonical(tsarray[0]);
    }

    FlagState canonical(FlagState fs) {
	return fs;
    }

    TagState canonical(TagState ts) {
	return ts;
    }


}

