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
		if (!table.containsKey(tmp)) {
		    //merge tag states
		    ObjWrapper newobj=new ObjWrapper();
		    newobj.initfs=prevobj.initfs;
		    table.put(tmp, newobj);
		}
		ObjWrapper currobj=(ObjWrapper) table.get(tmp);
		assert(currobj.initfs.equals(prevobj.initfs));
		for(Iterator<TagWrapper> tagit=prevobj.tags.iterator();tagit.hasNext();) {
		    TagWrapper tprev=tagit.next();
		    TagWrapper t=tagtable.get(tprev);
		    currobj.tags.add(t);
		}
		for(Iterator<FlagState> flagit=prevobj.fs.iterator();flagit.hasNext();) {
		    FlagState fs=flagit.nexT();
		    currobj.fs.add(fs);
		}
	    }
	}
	return table;
    }

    private void processFlatFlag(FlatFlagActionNode fn, Hashtable<TempDescriptor, Wrapper> table) {
	if (fn.getTaskType()==FlatFlagActionNode.PRE) {
	    throw new Error("Unsupported");
	} else if (fn.getTaskType()==FlatFlagActionNode.TASKEXIT) {
	    evalTaskExitNode(fn, table);
	    
	} else if (fn.getTaskType()==FlatFlagActionNode.NEW) {
	}
    }

    private void setFlag(ObjWrapper ow, FlagDescriptor fd, boolean value) {
	HashSet<FlagState> newstate=new HashSet<FlagState>();
	Hastable<FlagState, FlagState> flagmap=new Hashtable<FlagState, FlagState>();
	for(Iterator<FlagState> flagit=ow.fs.iterator();flagit.hasNext();) {
	    FlagState fs=flagit.next();
	    FlagState fsnew=canonical(fs.setFlag(fd, value));
	    newstate.add(fsnew);
	    flagmap.put(fs, fsnew);
	}
	
	for(Iterator<TagWrapper> tagit=ow.tags.iterator();tagit.hasNext();) {
	    TagWrapper tw=tagit.next();
	    HashSet<TagState> newstates=new HashSet<TagState>();
	    for(Iterator<TagState> tgit=tw.ts.iterator();tgit.hasNext();) {
		TagState ts=tgit.next();
		for(Iterator<FlagState> flagit=ts.flags.keySet();flagit.hasNext();) {
		    FlagState fs=flagit.next();
		    if (flagmap.containsKey(fs)) {
			if (flagmap.get(fs).equals(fs)) {
			    newstates.add(ts);
			} else {
			    TagState tsarray[]=ts.clearFS(fs);
			    //Can do strong update here because these
			    //must be parameter objects...therefore
			    //all possible aliasing relationships are
			    //explored
			    for(int i=0;i<tsarray.length;i++) {
				newstates.addAll(Arrays.asList(tsarray[i].addnewFS(flagmap.get(fs))));
			    }
			}
		    }
		}
	    }
	    tw.ts=newstates;
	}
	
    }

    private void setTag(ObjWrapper ow, TagWrapper tw, boolean value) {


    }

    private void evalTaskExitNode(FlatFlagActionNode fn, Hashtable<TempDescriptor, Wrapper> table) {
	//Process clears first
	for(Iterator it_ttp=ffan.getTempTagPairs();it_ttp.hasNext();) {
	    TempTagPair ttp=(TempTagPair)it_ttp.next();
	    TempDescriptor tmp=ttp.getTemp();
	    TempDescriptor tagtmp=ttp.getTagTemp();
	    TagWrapper tagw=(TagWrapper)table.get(tagtmp)
	    boolean newtagstate=fn.getTagChange(ttp);
	    ObjWrapper ow=(ObjWrapper)table.get(tmp);
	    if (!newtagstate)
		setTag(ow, tagw, newtagstate);
	}

	//Process sets next
	for(Iterator it_ttp=ffan.getTempTagPairs();it_ttp.hasNext();) {
	    TempTagPair ttp=(TempTagPair)it_ttp.next();
	    TempDescriptor tmp=ttp.getTemp();
	    TempDescriptor tagtmp=ttp.getTagTemp();
	    TagWrapper tagw=(TagWrapper)table.get(tagtmp)
	    boolean newtagstate=fn.getTagChange(ttp);
	    ObjWrapper ow=(ObjWrapper)table.get(tmp);
	    if (newtagstate)
		setTag(ow, tagw, newtagstate);
	}

	//Do the flags last
	for(Iterator<TempFlagPair> it_tfp=fn.getTempFlagPairs();it_tfp.hasNext();) {
	    TempFlagPair tfp=it_tfp.next();
	    TempDescriptor tmp=tfp.getTemp();
	    FlagDescriptor fd=tfp.getFlag();
	    boolean newflagstate=fn.getFlagChange(tfp);
	    ObjWrapper ow=(ObjWrapper)table.get(tmp);
	    setFlag(ow, fd, newflagstate);
	}
    }

    private void processFlatTag(FlatTagDeclaration fn, Hashtable<TempDescriptor, Wrapper> table) {
	TempDescriptor tmp=fn.getDst();
	if (table.containsKey(tmp)) {
	    recordtagchange(table.get(tmp));
	}
	TagDescriptor tag=fn.getTag();
	TagState ts=canonical(new TagState(tag));
	TagWrapper tw=new TagWrapper(ts);
	tw.initts=null;
	table.put(tmp, tw);
    }
      
    private void processFlatCall(FlatCall fc, Hashtable<TempDescriptor, Wrapper> table) {
	//Do nothing for now
    }

    private void processFlatReturnNode(FlatReturnNode fr, Hashtable<TempDescriptor, Wrapper> table) {

    }

    private boolean equivalent(Hashtable<TempDescriptor, Wrapper> table1, Hashtable<TempDescriptor, Wrapper> table2) {
	Hashtable<Wrapper, Wrapper> emap=new Hashtable<Wrapper, Wrapper>;

	if (table1.keySet().size()!=table2.keySet().size())
	    return false;

	for(Iterator<TempDescriptor> tmpit=table1.keySet().iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    if (table2.containsKey(tmp)) {
		emap.put(table1.get(tmp), table2.get(tmp));
	    } else return false;
	}
	
	for(Iterator<TempDescriptor> tmpit=table1.keySet().iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    Wrapper w1=table1.get(tmp);
	    Wrapper w2=table2.get(tmp);
	    if (w1 instanceof TagWrapper) {
		TagWrapper t1=(TagWrapper)w1;
		TagWrapper t2=(TagWrapper)w2;
		if (!t1.ts.equals(t2.ts))
		    return false;
		
	    } else {
		ObjWrapper t1=(ObjWrapper)w1;
		ObjWrapper t2=(ObjWrapper)w2;
		if (!t1.fs.equals(t2.fs))
		    return false;
		if (t1.tags.size()!=t2.tags.size())
		    return false;
		for(Iterator<TagWrapper> twit=t1.tags.iterator();twit.hasNext();) {
		    TagWrapper tw1=twit.next();
		    if (!t2.tags.contains(emap.get(tw1)))
			return false;
		}
	    }
	}
	return true;
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
	    Hashtable<TempDescriptor, Wrapper> table=computeInitialState(wtable, fn);
	    switch(fn.kind()) {
	    case FKind.FlatFlagActionNode:
		processFlatFlag((FlatFlagActionNode)fn, table);
		break;
	    case FKind.FlatTagDeclaration:
		processFlatTag((FlatTagDeclaration)fn, table);
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

