package IR;
import IR.Tree.*;

public class State {
    public State(ParseNode parsetree) {
	globals=new SymbolTable();
	this.parsetree=parsetree;
    }
    public SymbolTable globals;
    public ParseNode parsetree;

    public static TypeDescriptor getTypeDescriptor(int t) {
	TypeDescriptor td=new TypeDescriptor(t);
	return td;
    }
    public static TypeDescriptor getTypeDescriptor(NameDescriptor n) {
	TypeDescriptor td=new TypeDescriptor(n);
	return td;
    }
}
