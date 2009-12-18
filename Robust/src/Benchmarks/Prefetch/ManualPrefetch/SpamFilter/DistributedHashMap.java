public class DistributedHashMap {
  public DistributedHashEntry[] table;
  public float loadFactor;

  public DistributedHashMap(int initialCapacity, float loadFactor) {
    init(initialCapacity, loadFactor);
  }

  private void init(int initialCapacity, float loadFactor) {
    table=global new DistributedHashEntry[initialCapacity];
    this.loadFactor=loadFactor;
  }

  public int hash1(int hashcode, int length) {
    int value=hashcode%length;
    if (value<0)
      return -value;
    else
      return value;
  }
}

class DistributedHashEntry {
  public DistributedHashEntry() {
  }
  int count;
  DHashEntry array;
}


class DHashEntry {
  public DHashEntry() {
  }
  int hashval;
  HashEntry key;
  FilterStatistic value;
  DHashEntry next;
}
