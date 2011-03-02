package Analysis.Pointer;
import Analysis.Pointer.BasicBlock.BBlock;

public class PPoint {
  BBlock bblock;
  int index;
  public PPoint(BBlock bblock) {
    this.bblock=bblock;
    this.index=-1;
  }

  public PPoint(BBlock bblock, int index) {
    this.bblock=bblock;
    this.index=index;
  }

  public BBlock getBBlock() {
    return bblock;
  }

  public int getIndex() {
    return index;
  }
}