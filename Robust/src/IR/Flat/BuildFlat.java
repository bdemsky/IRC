package IR.Flat;
import IR.*;
import IR.Tree.*;
import java.util.*;

public class BuildFlat {
    State state;
    public BuildFlat(State st) {
	state=st;
    }

    public void buildflat() {
	Iterator it=state.classset.iterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    flattenClass(cn);
	}
    }
    
    private void flattenClass(ClassDescriptor cn) {
	Iterator methodit=cn.getMethods();
	while(methodit.hasNext()) {
	    MethodDescriptor md=(MethodDescriptor)methodit.next();
	    BlockNode bn=state.getMethodBody(md);
	    FlatNode fn=flattenBlockNode(bn).getBegin();
	    FlatMethod fm=new FlatMethod(md, fn);
	    state.addFlatCode(md,fm);
	}
    }

    private NodePair flattenBlockNode(BlockNode bn) {
	FlatNode begin=null;
	FlatNode end=null;
	for(int i=0;i<bn.size();i++) {
	    NodePair np=flattenBlockStatementNode(bn.get(i));
	    FlatNode np_begin=np.getBegin();
	    FlatNode np_end=np.getEnd();
	    if (begin==null) {
		begin=np_begin;
	    }
	    if (end==null) {
		end=np_end;
	    } else {
		end.addNext(np_begin);
		end=np_end;
	    }
	}
	return new NodePair(begin,end);
    }

    private NodePair flattenExpressionNode(ExpressionNode en) {

    }

    private NodePair flattenDeclarationNode(DeclarationNode dn) {

    }
        
    private NodePair flattenIfStatementNode(IfStatementNode isn) {

    }
	    
    private NodePair flattenLoopNode(LoopNode ln) {

    }
	    
    private NodePair flattenReturnNode(ReturnNode rn) {

    }
	    
    private NodePair flattenSubBlockNode(SubBlockNode sbn) {

    }

    private NodePair flattenBlockStatementNode(BlockStatementNode bsn) {
	switch(bsn.kind()) {
	case Kind.BlockExpressionNode:
	    return flattenExpressionNode((ExpressionNode)bsn);
	    
	case Kind.DeclarationNode:
	    return flattenDeclarationNode((DeclarationNode)bsn);
	    
	case Kind.IfStatementNode:
	    return flattenIfStatementNode((IfStatementNode)bsn);
	    
	case Kind.LoopNode:
	    return flattenLoopNode((LoopNode)bsn);
	    
	case Kind.ReturnNode:
	    return flattenReturnNode((ReturnNode)bsn);
	    
	case Kind.SubBlockNode:
	    return flattenSubBlockNode((SubBlockNode)bsn);
	    
	}
    	throw new Error();
    }
}
