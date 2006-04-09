package IR;
import IR.Tree.*;
import IR.Flat.*;
import IR.*;
import java.util.*;

public class State {
    public String main;

    public State() {
	this.classes=new SymbolTable();
	this.treemethodmap=new Hashtable();
	this.flatmethodmap=new Hashtable();
	this.parsetrees=new HashSet();
	this.arraytypes=new HashSet();
	this.arraytonumber=new Hashtable();
    }

    public void addParseNode(ParseNode parsetree) {
	parsetrees.add(parsetree);
    }

    public SymbolTable classes;
    public Set parsetrees;
    public Hashtable treemethodmap;
    public Hashtable flatmethodmap;
    private HashSet arraytypes;
    public Hashtable arraytonumber;
    private int numclasses=0;
    private int arraycount=0;

    public void addArrayType(TypeDescriptor td) {
	if (!arraytypes.contains(td)) {
	    arraytypes.add(td);
	    arraytonumber.put(td,new Integer(arraycount++));
	}
    }

    public Iterator getArrayIterator() {
	return arraytypes.iterator();
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

    public SymbolTable getClassSymbolTable() {
	return classes;
    }

    public FlatMethod getMethodFlat(MethodDescriptor md) {
	return (FlatMethod)flatmethodmap.get(md);
    }

    public void addTreeCode(MethodDescriptor md, BlockNode bn) {
	treemethodmap.put(md,bn);
    }

    public void addFlatCode(MethodDescriptor md, FlatMethod bn) {
	flatmethodmap.put(md,bn);
    }
}
