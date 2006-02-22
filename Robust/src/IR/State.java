package IR;
import IR.Tree.*;
import IR.Flat.*;
import IR.*;
import java.util.*;

public class State {
    public State(ParseNode parsetree) {
	this.parsetree=parsetree;
	this.classes=new SymbolTable();
	this.treemethodmap=new Hashtable();
	this.flatmethodmap=new Hashtable();
    }

    public SymbolTable classes;
    public ParseNode parsetree;
    public Hashtable treemethodmap;
    public Hashtable flatmethodmap;

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
