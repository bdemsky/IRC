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
    HashSet<TagState> discovered;
    Hashtable<TaskDescriptor, TaskQueue> tasktable;
    Hashtable<TagDescriptor, Set<TagState>> tsresults;
    Hashtable<ClassDescriptor, Set<TagState>> fsresults;


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
	this.discovered=new HashSet<TagState>();
	this.tasktable=new Hashtable<TaskDescriptor, TaskQueue>();
	this.tsresults=new Hashtable<TagDescriptor, Set<TagState>>();
	this.fsresults=new Hashtable<ClassDescriptor, Set<TagState>>();
	

	for(Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();taskit.hasNext();) {
	    TaskDescriptor td=(TaskDescriptor)taskit.next();
	    tasktable.put(td, new TaskQueue(td));
	}
	doAnalysis();
	doOutput();
    }

    private void doOutput() {
	try {
	for(Iterator<TagDescriptor> tagit=tsresults.keySet().iterator();tagit.hasNext();) {
	    TagDescriptor tag=tagit.next();
	    Set<TagState> set=tsresults.get(tag);
	    File dotfile_flagstates= new File("tag"+tag.getSymbol()+".dot");
	    FileOutputStream dotstream=new FileOutputStream(dotfile_flagstates,false);
	    TagState.DOTVisitor.visit(dotstream,set);
	}
	for(Iterator<ClassDescriptor> cdit=fsresults.keySet().iterator();cdit.hasNext();) {
	    ClassDescriptor cd=cdit.next();
	    Set<TagState> set=fsresults.get(cd);
	    File dotfile_flagstates= new File("class"+cd.getSymbol()+".dot");
	    FileOutputStream dotstream=new FileOutputStream(dotfile_flagstates,false);
	    TagState.DOTVisitor.visit(dotstream,set);
	}
	} catch (Exception e) {
	    e.printStackTrace();
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
		System.out.println("Trying to enqueue "+td);
		if (canEnqueue(td, i, fs)) {
		    System.out.println("Enqueued");
		    TaskQueueIterator tqi=tq.enqueue(i, fts);
		    while(tqi.hasNext()) {
			System.out.println("binding");
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
		    TagWrapper clonetag=((TagWrapper)prevtag).clone();
		    tagtable.put((TagWrapper)prevtag, clonetag);
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
		    FlagState fs=flagit.next();
		    currobj.fs.add(fs);
		}
	    }
	}
	return table;
    }

    private void processFlatFlag(FlatFlagActionNode fn, Hashtable<TempDescriptor, Wrapper> table, TaskDescriptor td) {
	if (fn.getTaskType()==FlatFlagActionNode.PRE) {
	    throw new Error("Unsupported");
	} else if (fn.getTaskType()==FlatFlagActionNode.TASKEXIT) {
	    evalTaskExitNode(fn, table);
	} else if (fn.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
	    evalNewNode(fn, table, td);
	}
    }

    private void setFlag(ObjWrapper ow, FlagDescriptor fd, boolean value) {
	HashSet<FlagState> newstate=new HashSet<FlagState>();
	Hashtable<FlagState, FlagState> flagmap=new Hashtable<FlagState, FlagState>();
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
		for(Iterator<FlagState> flagit=ts.getFS().iterator();flagit.hasNext();) {
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
				TagState ts2=canonical(tsarray[i]);
				TagState tsarray2[]=ts2.addnewFS(flagmap.get(fs));
				for(int j=0;j<tsarray2.length;j++)
				    newstates.add(canonical(tsarray2[j]));
			    }
			}
		    }
		}
	    }
	    tw.ts=newstates;
	}
    }

    private void setTag(ObjWrapper ow, TagWrapper twnew, TagDescriptor tag, boolean value) {
	if (value) {
	    if (ow.tags.contains(twnew)) {
		System.out.println("Tag already bound to object.");
		return;
	    }
	} else {
	    if (!ow.tags.contains(twnew)) {
		System.out.println("Tag not bound to object.");
		return;
	    }
	}
	HashSet<FlagState> newfsstates=new HashSet<FlagState>();
	Hashtable<FlagState, FlagState[]> flagmap=new Hashtable<FlagState, FlagState[]>();
	//Change the flag states
	for(Iterator<FlagState> fsit=ow.fs.iterator();fsit.hasNext();) {
	    FlagState fs=fsit.next();
	    FlagState[] fsnew=canonical(fs.setTag(tag, value));
	    flagmap.put(fs, fsnew);
	    newfsstates.addAll(Arrays.asList(fsnew));
	}
	for(Iterator<TagWrapper> tagit=ow.tags.iterator();tagit.hasNext();) {
	    TagWrapper tw=tagit.next();
	    HashSet<TagState> newstates=new HashSet<TagState>();
	    for(Iterator<TagState> tgit=tw.ts.iterator();tgit.hasNext();) {
		TagState ts=tgit.next();
		for(Iterator<FlagState> flagit=ts.getFS().iterator();flagit.hasNext();) {
		    FlagState fs=flagit.next();
		    if (flagmap.containsKey(fs)) {
			FlagState[] fmap=flagmap.get(fs);
			for(int i=0;i<fmap.length;i++) {
			    FlagState fsnew=fmap[i];
			    if (fsnew.equals(fs)) {
				newstates.add(ts);
			    } else {
				TagState tsarray[]=ts.clearFS(fs);
				//Can do strong update here because
				//these must be parameter
				//objects...therefore all possible
				//aliasing relationships are explored
				for(int j=0;j<tsarray.length;j++) {
				    TagState ts2=canonical(tsarray[j]);
				    TagState tsarray2[]=ts2.addnewFS(fsnew);
				    for(int k=0;k<tsarray2.length;k++)
					newstates.add(canonical(tsarray2[k]));
				}
			    }
			}
		    }
		}
	    }
	    tw.ts=newstates;
	}
	
	{
	    HashSet<TagState> newstates=new HashSet<TagState>();
	    for(Iterator<TagState> tgit=twnew.ts.iterator();tgit.hasNext();) {
		TagState ts=tgit.next();
		for(Iterator<FlagState> flagit=newfsstates.iterator();flagit.hasNext();) {
		    FlagState fsnew=flagit.next();
		    //Can do strong update here because these must
		    //be parameter objects...therefore all
		    //possible aliasing relationships are explored
		    TagState tsarray2[];
		    if (value) 
			tsarray2=ts.addnewFS(fsnew);
		    else 
			tsarray2=ts.clearFS(fsnew);
		    for(int j=0;j<tsarray2.length;j++)
			newstates.add(canonical(tsarray2[j]));
		}
	    }
	    twnew.ts=newstates;
	}
	
	if (value)
	    ow.tags.add(twnew);
	else
	    ow.tags.remove(twnew);
	ow.fs=newfsstates;
    }

    private void evalTaskExitNode(FlatFlagActionNode fn, Hashtable<TempDescriptor, Wrapper> table) {
	//Process clears first
	for(Iterator<TempTagPair> it_ttp=fn.getTempTagPairs();it_ttp.hasNext();) {
	    TempTagPair ttp=it_ttp.next();
	    TempDescriptor tmp=ttp.getTemp();
	    TagDescriptor tag=ttp.getTag();
	    TempDescriptor tagtmp=ttp.getTagTemp();
	    TagWrapper tagw=(TagWrapper)table.get(tagtmp);
	    boolean newtagstate=fn.getTagChange(ttp);
	    ObjWrapper ow=(ObjWrapper)table.get(tmp);
	    if (!newtagstate)
		setTag(ow, tagw, tag, newtagstate);
	}

	//Do the flags next
	for(Iterator<TempFlagPair> it_tfp=fn.getTempFlagPairs();it_tfp.hasNext();) {
	    TempFlagPair tfp=it_tfp.next();
	    TempDescriptor tmp=tfp.getTemp();
	    FlagDescriptor fd=tfp.getFlag();
	    boolean newflagstate=fn.getFlagChange(tfp);
	    ObjWrapper ow=(ObjWrapper)table.get(tmp);
	    setFlag(ow, fd, newflagstate);
	}

	//Process sets last
	for(Iterator it_ttp=fn.getTempTagPairs();it_ttp.hasNext();) {
	    TempTagPair ttp=(TempTagPair)it_ttp.next();
	    TempDescriptor tmp=ttp.getTemp();
	    TagDescriptor tag=ttp.getTag();
	    TempDescriptor tagtmp=ttp.getTagTemp();
	    TagWrapper tagw=(TagWrapper)table.get(tagtmp);
	    boolean newtagstate=fn.getTagChange(ttp);
	    ObjWrapper ow=(ObjWrapper)table.get(tmp);
	    if (newtagstate)
		setTag(ow, tagw, tag, newtagstate);
	}
    }

    private void evalNewNode(FlatFlagActionNode fn, Hashtable<TempDescriptor, Wrapper> table, TaskDescriptor td) {
	TempDescriptor fntemp=null;
	{
	    /* Compute type */
	    Iterator it=fn.getTempFlagPairs();
	    if (it.hasNext()) {
		TempFlagPair tfp=(TempFlagPair)it.next();
		fntemp=tfp.getTemp();
	    } else {
		it=fn.getTempTagPairs();
		if (!it.hasNext())
		    throw new Error();
		TempTagPair ttp=(TempTagPair)it.next();
		fntemp=ttp.getTemp();
	    }
	}
	FlagState fs=canonical(new FlagState(fntemp.getType().getClassDesc()));
	ObjWrapper ow=new ObjWrapper();
	ow.fs.add(fs);
	table.put(fntemp, ow);
	//Do the flags first
	for(Iterator<TempFlagPair> it_tfp=fn.getTempFlagPairs();it_tfp.hasNext();) {
	    TempFlagPair tfp=it_tfp.next();
	    TempDescriptor tmp=tfp.getTemp();
	    FlagDescriptor fd=tfp.getFlag();
	    boolean newflagstate=fn.getFlagChange(tfp);
	    assert(ow==table.get(tmp));
	    setFlag(ow, fd, newflagstate);
	}
	//Process sets next
	for(Iterator it_ttp=fn.getTempTagPairs();it_ttp.hasNext();) {
	    TempTagPair ttp=(TempTagPair)it_ttp.next();
	    TempDescriptor tmp=ttp.getTemp();
	    TagDescriptor tag=ttp.getTag();
	    TempDescriptor tagtmp=ttp.getTagTemp();
	    TagWrapper tagw=(TagWrapper)table.get(tagtmp);
	    boolean newtagstate=fn.getTagChange(ttp);
	    assert(ow==table.get(tmp));
	    if (newtagstate)
		setTag(ow, tagw, tag, newtagstate);
	    else
		throw new Error("Can't clear tag in newly allocated object");
	}
	for(Iterator<FlagState> fsit=ow.fs.iterator();fsit.hasNext();) {
	    FlagState fs2=fsit.next();
	    fs2.addAllocatingTask(td);
	    TagState ts2=new TagState(fs2.getClassDescriptor());
	    ts2.addFS(fs2);
	    ts2=canonical(ts2);
	    ts2.addSource(td);
	    addresult(fs2.getClassDescriptor(), ts2);
	    if (!discovered.contains(ts2)) {
		discovered.add(ts2);
		toprocess.add(ts2);
	    }
	}
    }

    private void processFlatTag(FlatTagDeclaration fn, Hashtable<TempDescriptor, Wrapper> table, TaskDescriptor td) {
	TempDescriptor tmp=fn.getDst();
	if (table.containsKey(tmp)) {
	    recordtagchange((TagWrapper)table.get(tmp), td);
	}
	TagDescriptor tag=fn.getType();
	TagState ts=canonical(new TagState(tag));
	TagWrapper tw=new TagWrapper(ts);
	tw.initts=null;
	table.put(tmp, tw);
    }

    private void addresult(TagDescriptor td, TagState ts) {
	if (!tsresults.containsKey(td))
	    tsresults.put(td, new HashSet<TagState>());
	tsresults.get(td).add(ts);
    }

    private void addresult(ClassDescriptor cd, TagState ts) {
	if (!fsresults.containsKey(cd))
	    fsresults.put(cd, new HashSet<TagState>());
	fsresults.get(cd).add(ts);
    }

    public void recordtagchange(TagWrapper tw, TaskDescriptor td) {
	TagState init=tw.initts;
	for(Iterator<TagState> tsit=tw.ts.iterator(); tsit.hasNext();) {
	    TagState ts=tsit.next();
	    if (init==null) {
		ts.addSource(td);
	    } else {
		TagEdge te=new TagEdge(ts, td);
		if (!init.containsEdge(te)) {
		    init.addEdge(te);
		}
	    }
	    if (ts.getTag()!=null)
		addresult(ts.getTag(), ts);
	    else
		addresult(ts.getClassDesc(), ts);
	    if (!discovered.contains(ts)) {
		discovered.add(ts);
		toprocess.add(ts);
	    }
	}
    }

    private void recordobj(ObjWrapper ow, TaskDescriptor td) {
	for(Iterator<TagWrapper> twit=ow.tags.iterator();twit.hasNext();) {
	    TagWrapper tw=twit.next();
	    recordtagchange(tw, td);
	}
    }

    private void processFlatCall(FlatCall fc, Hashtable<TempDescriptor, Wrapper> table) {
	//Do nothing for now
    }
    
    private void processFlatReturnNode(FlatReturnNode fr, Hashtable<TempDescriptor, Wrapper> table, TaskDescriptor td) {
	for(Iterator<TempDescriptor> tmpit=table.keySet().iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    Wrapper w=table.get(tmp);
	    if (w instanceof TagWrapper) {
		TagWrapper tw=(TagWrapper)w;
		recordtagchange(tw, td);
	    } else {
		ObjWrapper ow=(ObjWrapper)w;
		recordobj(ow, td);
	    }
	}
    }

    private boolean equivalent(Hashtable<TempDescriptor, Wrapper> table1, Hashtable<TempDescriptor, Wrapper> table2) {
	Hashtable<Wrapper, Wrapper> emap=new Hashtable<Wrapper, Wrapper>();

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
	wtable.put(fm, buildinittable(tb, fm));
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
		processFlatFlag((FlatFlagActionNode)fn, table, td);
		break;
	    case FKind.FlatTagDeclaration:
		processFlatTag((FlatTagDeclaration)fn, table, td);
		break;
	    case FKind.FlatCall:
		processFlatCall((FlatCall)fn, table);
		break;
	    case FKind.FlatReturnNode:
		processFlatReturnNode((FlatReturnNode)fn, table, td);
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
	TagState ts=new TagState(startupobject);
	TagState[] tsarray=ts.addFS(fsstartup);
	return canonical(tsarray[0]);
    }

    FlagState[] canonical(FlagState[] fs) {
	FlagState[] fsarray=new FlagState[fs.length];
	for(int i=0;i<fs.length;i++)
	    fsarray[i]=canonical(fs[i]);
	return fsarray;
    }

    FlagState canonical(FlagState fs) {
	return fs;
    }

    TagState canonical(TagState ts) {
	return ts;
    }


}

