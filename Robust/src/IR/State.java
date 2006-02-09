package IR;
import IR.Tree.*;

public class State {
    public State(ParseNode parsetree) {
	globals=new SymbolTable();
	this.parsetree=parsetree;
    }
    public SymbolTable globals;
    public ParseNode parsetree;

}
