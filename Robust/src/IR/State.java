package IR;
import IR.Tree.*;
import IR.Flat.*;
import IR.*;
import java.util.*;

public class State {
    public State() {
	this.classes=new SymbolTable();
	this.tasks=new SymbolTable();
	this.treemethodmap=new Hashtable();
	this.flatmethodmap=new Hashtable();
	this.parsetrees=new HashSet();
	this.arraytypes=new HashSet();
	this.arraytonumber=new Hashtable();
	this.tagmap=new Hashtable();
    }

    public void addParseNode(ParseNode parsetree) {
	parsetrees.add(parsetree);
    }

    /** Boolean flag which indicates whether compiler is compiling a task-based
     * program. */
    public boolean WEBINTERFACE;
    public boolean TASK;
    public boolean TASKSTATE=false;
    public boolean THREAD=false;
    public boolean INSTRUCTIONFAILURE=false;
    public String structfile;
    public String main;
    public boolean CONSCHECK=false;

    public SymbolTable classes;
    public SymbolTable tasks;
    public Set parsetrees;
    public Hashtable treemethodmap;
    public Hashtable flatmethodmap;
    private HashSet arraytypes;
    public Hashtable arraytonumber;
    private int numclasses=0;
    private int numtasks=0;
    private int arraycount=0;

    private Hashtable tagmap;
    private int numtags=0;

    public void addArrayType(TypeDescriptor td) {
	if (!arraytypes.contains(td)) {
	    arraytypes.add(td);
	    arraytonumber.put(td,new Integer(arraycount++));
	}
    }

    public Iterator getArrayIterator() {
	return arraytypes.iterator();
    }

    public int getTagId(TagDescriptor tag) {
	if (tagmap.containsKey(tag)) {
	    return ((Integer) tagmap.get(tag)).intValue();
	} else {
	    tagmap.put(tag, new Integer(numtags));
	    return numtags++;
	}
    }

    public int getArrayNumber(TypeDescriptor td) {
	return ((Integer)arraytonumber.get(td)).intValue();
    }

    public int numArrays() {
	return arraytypes.size();
    }

    public static TypeDescriptor getTypeDescriptor(int t) {
	TypeDescriptor td=new TypeDescriptor(t);
	return td;
    }

    public static TypeDescriptor getTypeDescriptor(NameDescriptor n) {
	TypeDescriptor td=new TypeDescriptor(n);
	return td;
    }

    public void addClass(ClassDescriptor tdn) {
	if (classes.contains(tdn.getSymbol()))
	    throw new Error("Class "+tdn.getSymbol()+" defined twice");
	classes.add(tdn);
	numclasses++;
    }

    public int numClasses() {
	return numclasses;
    }

    public BlockNode getMethodBody(MethodDescriptor md) {
	return (BlockNode)treemethodmap.get(md);
    }

    public BlockNode getMethodBody(TaskDescriptor td) {
	return (BlockNode)treemethodmap.get(td);
    }

    public SymbolTable getClassSymbolTable() {
	return classes;
    }

    public SymbolTable getTaskSymbolTable() {
	return tasks;
    }

    /** Returns Flat IR representation of MethodDescriptor md. */

    public FlatMethod getMethodFlat(MethodDescriptor md) {
	return (FlatMethod)flatmethodmap.get(md);
    }

    /** Returns Flat IR representation of TaskDescriptor td. */

    public FlatMethod getMethodFlat(TaskDescriptor td) {
	return (FlatMethod)flatmethodmap.get(td);
    }

    public void addTreeCode(MethodDescriptor md, BlockNode bn) {
	treemethodmap.put(md,bn);
    }

    public void addTreeCode(TaskDescriptor td, BlockNode bn) {
	treemethodmap.put(td,bn);
    }

    public void addFlatCode(MethodDescriptor md, FlatMethod bn) {
	flatmethodmap.put(md,bn);
    }

    public void addFlatCode(TaskDescriptor td, FlatMethod bn) {
	flatmethodmap.put(td,bn);
    }

    public void addTask(TaskDescriptor td) {
	if (tasks.contains(td.getSymbol()))
	    throw new Error("Task "+td.getSymbol()+" defined twice");
	tasks.add(td);
	numtasks++;
    }
}
