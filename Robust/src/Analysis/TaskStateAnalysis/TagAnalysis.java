package Analysis.TaskStateAnalysis;

import java.util.Hashtable;
import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Arrays;
import Util.Edge;
import Analysis.CallGraph.CallGraph;
import IR.SymbolTable;
import IR.State;
import IR.TagDescriptor;
import IR.TaskDescriptor;
import IR.MethodDescriptor;
import IR.Flat.*;

public class TagAnalysis {
    State state;
    Hashtable flagmap;
    Stack tovisit;
    Hashtable discovered;
    Hashtable tasktotagbindings;
    Hashtable tasktoflagstates;
    CallGraph callgraph;

    public TagAnalysis(State state, CallGraph callgraph) {
	this.state=state;
	this.flagmap=new Hashtable();
	this.discovered=new Hashtable();
	this.tovisit=new Stack();
	this.tasktoflagstates=new Hashtable();
	this.tasktotagbindings=new Hashtable();
	this.callgraph=callgraph;
	doAnalysis();
    }

    public Set getFlagStates(TaskDescriptor task) {
	return (Set)tasktoflagstates.get(task);
    }

    private void doAnalysis() {
	Set rootset=computeRootSet();
	computeTagBindings(rootset);
	TagBinding.SCC scc=TagBinding.DFS.computeSCC(discovered.keySet());
	for(int i=0;i<scc.numSCC();i++) {
	    Set component=scc.getSCC(i);
	    HashSet flagset=new HashSet();
	    for(Iterator compit=component.iterator();compit.hasNext();) {
		TagBinding tb=(TagBinding)compit.next();
		flagset.addAll(tb.getAllocations());
		for(Iterator edgeit=tb.edges();edgeit.hasNext();) {
		    Edge e=(Edge)edgeit.next();
		    TagBinding tb2=(TagBinding)e.getTarget();
		    flagset.addAll(tb2.getAllocations());
		}
	    }
	    for(Iterator compit=component.iterator();compit.hasNext();) {
		TagBinding tb=(TagBinding)compit.next();
		tb.getAllocations().addAll(flagset);
	    }
	}

	SymbolTable tasktable=state.getTaskSymbolTable();
	for(Iterator taskit=tasktable.getDescriptorsIterator();taskit.hasNext();) {
	    TaskDescriptor task=(TaskDescriptor)taskit.next();
	    HashSet roottags=(HashSet)tasktotagbindings.get(task);
	    HashSet taskflags=(HashSet)tasktoflagstates.get(task);
	    for(Iterator tagit=roottags.iterator();tagit.hasNext();) {
		TagBinding tb=(TagBinding)tagit.next();
		taskflags.addAll(tb.getAllocations());
	    }
	}
    }
    
    private Set computeRootSet() {
	HashSet rootset=new HashSet();
	SymbolTable tasktable=state.getTaskSymbolTable();
	for(Iterator taskit=tasktable.getDescriptorsIterator();taskit.hasNext();) {
	    TaskDescriptor task=(TaskDescriptor)taskit.next();
	    HashSet roottags=new HashSet();
	    HashSet taskflags=new HashSet();
	    FlatMethod fm=state.getMethodFlat(task);
	    computeCallsFlags(fm, null, roottags, taskflags);
	    rootset.addAll(roottags);
	    tasktotagbindings.put(task,roottags);
	    tasktoflagstates.put(task,taskflags);
	}
	return rootset;
    }

private void computeCallsFlags(FlatMethod fm, Hashtable parammap, Set tagbindings, Set newflags) {
    Set nodeset=fm.getNodeSet();
    for(Iterator nodeit=nodeset.iterator();nodeit.hasNext();) {
	FlatNode fn=(FlatNode)nodeit.next();
	if(fn.kind()==FKind.FlatCall) {
	    FlatCall fc=(FlatCall)fn;
	    MethodDescriptor nodemd=fc.getMethod();
	    Set methodset=fc.getThis()==null?callgraph.getMethods(nodemd):
		callgraph.getMethods(nodemd, fc.getThis().getType());
	    
	    for(Iterator methodit=methodset.iterator();methodit.hasNext();) {
		MethodDescriptor md=(MethodDescriptor) methodit.next();
		TagBinding nodetb=new TagBinding(md);
		for(int i=0;i<md.numParameters();i++) {
		    TempDescriptor temp=fc.getArg(i);
		    TagDescriptor tag=temp.getTag();
		    if (tag==null&&parammap!=null&&parammap.containsKey(temp)) {
			tag=(TagDescriptor)parammap.get(temp);
		    }
		    if (tag!=null)
			nodetb.setBinding(i,tag);
		}
		if (!discovered.containsKey(nodetb)) {
		    discovered.put(nodetb,nodetb);
		    tovisit.add(nodetb);
		} else
		    nodetb=(TagBinding)discovered.get(nodetb);
		tagbindings.add(nodetb);
	    }
	} else if (fn.kind()==FKind.FlatFlagActionNode) {
	    FlatFlagActionNode ffan=(FlatFlagActionNode)fn;
	    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
		TempDescriptor ffantemp=null;
		{
		    /* Compute type */

		    Iterator it=ffan.getTempFlagPairs();
		    if (it.hasNext()) {
			TempFlagPair tfp=(TempFlagPair)it.next();
			ffantemp=tfp.getTemp();
		    } else {
			it=ffan.getTempTagPairs();
			if (!it.hasNext())
			    throw new Error();
			TempTagPair ttp=(TempTagPair)it.next();
			ffantemp=ttp.getTemp();
		    }
		}
		FlagState fs=new FlagState(ffantemp.getType().getClassDesc());
		for(Iterator it=ffan.getTempFlagPairs();it.hasNext();) {
		    TempFlagPair tfp=(TempFlagPair)it.next();
		    if (ffan.getFlagChange(tfp))
			fs=fs.setFlag(tfp.getFlag(), true);
		    else
			fs=fs.setFlag(tfp.getFlag(), false);
		}
		
		HashSet fsset=new HashSet();
		fsset.add(fs);

		for(Iterator it=ffan.getTempTagPairs();it.hasNext();) {
		    HashSet oldfsset=fsset;
		    fsset=new HashSet();
		    
		    TempTagPair ttp=(TempTagPair)it.next();
		    if (ffan.getTagChange(ttp)) {
			TagDescriptor tag=ttp.getTag();
			if (tag==null&&parammap!=null&&parammap.containsKey(ttp.getTagTemp())) {
			    tag=(TagDescriptor)parammap.get(ttp.getTagTemp());
			}
			for(Iterator setit=oldfsset.iterator();setit.hasNext();) {
			    FlagState fs2=(FlagState)setit.next();
			    fsset.addAll(Arrays.asList(fs2.setTag(tag)));
			}
		    } else
			throw new Error("Don't clear tag in new object allocation");
		}

		for(Iterator setit=fsset.iterator();setit.hasNext();) {
		    FlagState fs2=(FlagState)setit.next();
		    if (!flagmap.containsKey(fs2))
			flagmap.put(fs2,fs2);
		    else
			fs2=(FlagState) flagmap.get(fs2);
		    newflags.add(fs2);
		}
	    }
	}
    }
}
    
    private void computeTagBindings(Set roots) {
	tovisit.addAll(roots);
	
	for(Iterator it=roots.iterator();it.hasNext();) {
	    TagBinding tb=(TagBinding)it.next();
	    discovered.put(tb,tb);
	}

	while(!tovisit.empty()) {
	    TagBinding tb=(TagBinding) tovisit.pop();
	    MethodDescriptor md=tb.getMethod();
	    FlatMethod fm=state.getMethodFlat(md);
	    /* Build map from temps -> tagdescriptors */
	    Hashtable parammap=new Hashtable();
	    int offset=md.isStatic()?0:1;


	    for(int i=0;i<fm.numParameters();i++) {
		TempDescriptor temp=fm.getParameter(i);
		int offsetindex=i-offset;
		if (offsetindex>=0) {
		    TagDescriptor tag=tb.getBinding(offsetindex);

		    if (tag!=null) {
			parammap.put(temp,tag);
		    }
		}
	    }

	    HashSet newtags=new HashSet();
	
	    computeCallsFlags(fm, parammap, newtags, tb.getAllocations());

	    for(Iterator tagit=newtags.iterator();tagit.hasNext();) {
		TagBinding newtag=(TagBinding)tagit.next();
		Edge e=new Edge(newtag);
		tb.addEdge(e);
	    }
	}
    }
}
