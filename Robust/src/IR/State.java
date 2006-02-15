package IR;
import IR.Tree.*;
import java.util.*;

public class State {
    public State(ParseNode parsetree) {
	globals=new SymbolTable();
	this.parsetree=parsetree;
	this.set=new HashSet();
    }
    public SymbolTable globals;
    public ParseNode parsetree;
    public HashSet set;

    public static TypeDescriptor getTypeDescriptor(int t) {
	TypeDescriptor td=new TypeDescriptor(t);
	return td;
    }
    public static TypeDescriptor getTypeDescriptor(NameDescriptor n) {
	TypeDescriptor td=new TypeDescriptor(n);
	return td;
    }
    public void addClass(ClassNode tdn) {
	set.add(tdn);
    }
}
