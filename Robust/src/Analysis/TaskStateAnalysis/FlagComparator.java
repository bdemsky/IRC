package Analysis.TaskStateAnalysis;
import java.util.Hashtable;
import java.util.Comparator;
import java.util.Iterator;
import IR.FlagDescriptor;

/**Note: this comparator imposes orderings that are inconsistent with equals.*/

public class FlagComparator implements Comparator {
  Hashtable flaginfo;
  public FlagComparator(Hashtable flaginfo) {
    this.flaginfo=flaginfo;
  }

  public int compare(Object o1, Object o2) {
    int fs1=getFlagInt((FlagState)o1);
    int fs2=getFlagInt((FlagState)o2);
    return fs1-fs2;
  }

  public int getFlagInt(FlagState fs) {
    int flagid=0;
    for(Iterator flags = fs.getFlags(); flags.hasNext(); ) {
      FlagDescriptor flagd = (FlagDescriptor)flags.next();
      int id=1<<((Integer)flaginfo.get(flagd)).intValue();
      flagid|=id;
    }
    return flagid;
  }
}
