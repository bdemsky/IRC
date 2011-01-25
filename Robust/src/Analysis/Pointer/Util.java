package Analysis.Pointer;
import java.util.HashSet;
import java.util.Set;

public class Util {
  public static <T> HashSet<T> setSubtract(Set <T> orig, Set<T> sub) {
    HashSet<T> newset=new HashSet<T>();
    for(T e: orig) {
      if (!sub.contains(e))
	newset.add(e);
    }
    return newset;
  }

}