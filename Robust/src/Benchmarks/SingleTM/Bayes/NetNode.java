public class NetNode {
  int id;
  int mark;
  IntList parentIdListPtr;
  IntList childIdListPtr;
  int NET_NODE_MARK_INIT;
  int NET_NODE_MARK_DONE;
  int NET_NODE_MARK_TEST;

  public NetNode() {
    mark = 0;
    NET_NODE_MARK_INIT = 0;
    NET_NODE_MARK_DONE = 1;
    NET_NODE_MARK_TEST = 2;
  }

  /* =============================================================================
   * freeNode
   * =============================================================================
   */
  public void freeNode ()
  {
    childIdListPtr = null;
    parentIdListPtr = null;
  }
}
