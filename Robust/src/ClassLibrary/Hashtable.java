public class Hashtable extends HashMap
{
  public Hashtable() {
    HashMap(16, 0.75f);    
  }

  public Hashtable(int initialCapacity) {
    HashMap(initialCapacity, 0.75f);
  }

  public Hashtable(int initialCapacity, float loadFactor) {
    HashMap(initialCapacity, loadFactor);
  }
}
