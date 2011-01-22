package Analysis.Pointer;
import Analysis.Disjoint.PointerMethod;
import java.util.*;
import IR.Flat.*;

public class BasicBlock {
  public BBlock start;
  public BBlock exit;

  public BasicBlock(BBlock start, BBlock exit) {
    this.start=start;
    this.exit=exit;
  }

  public BBlock getStart() {
    return start;
  }

  public BBlock getExit() {
    return exit;
  }

  public static class BBlock {
    Vector<FlatNode> nodes;
    Vector<BBlock> prevb;
    Vector<BBlock> nextb;

    public BBlock() {
      nodes=new Vector<FlatNode>();
      prevb=new Vector<BBlock>();
      nextb=new Vector<BBlock>();
    }

    public Vector<FlatNode> nodes() {
      return nodes;
    }
    public Vector<BBlock> next() {
      return nextb;
    }
    public Vector<BBlock> prev() {
      return prevb;
    }
  }

  public static BasicBlock getBBlock(FlatMethod fm) {
    BBlock exit=null;
    Stack<FlatNode> toprocess=new Stack<FlatNode>();
    HashMap<FlatNode, BBlock> map=new HashMap<FlatNode, BBlock>();
    PointerMethod pm=new PointerMethod();
    pm.analyzeMethod(fm);
    toprocess.add(fm);
    map.put(fm, new BBlock());

    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.pop();
      BBlock block=map.get(fn);
      block.nodes.add(fn);
      if (fn.kind()==FKind.FlatExit)
	exit=block;
      do {
	if (pm.numNext(fn)!=1) {
	  for(int i=0;i<pm.numNext(fn);i++) {
	    FlatNode fnext=pm.getNext(fn,i);
	    if (!map.containsKey(fnext)) {
	      map.put(fn, new BBlock());
	      toprocess.add(fn);
	    }
	    //link block in
	    if (!block.nextb.contains(map.get(fn))) {
	      block.nextb.add(map.get(fn));
	      map.get(fn).prevb.add(block);
	    }
	  }
	  break;
	}
	fn=pm.getNext(fn,0);
	if (fn.numPrev()>1) {
	  //new basic block
	  if (!map.containsKey(fn)) {
	    map.put(fn, new BBlock());
	    toprocess.add(fn);
	  }
	  //link block in
	  if (!block.nextb.contains(map.get(fn))) {
	    block.nextb.add(map.get(fn));
	    map.get(fn).prevb.add(block);
	  }
	  break;
	}
	block.nodes.add(fn);
      } while(true);
    }

    return new BasicBlock(map.get(fm), exit);
  }
}
