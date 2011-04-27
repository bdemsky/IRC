package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;


public class FlagInfo {
  private Hashtable<ClassDescriptor, FlagDescriptor[]> flags;
  private State state;

  public FlagInfo(State state) {
    this.state=state;
    flags=new Hashtable<ClassDescriptor, FlagDescriptor[]>();
    getFlagsfromClasses();
  }

  public FlagDescriptor[] getFlags(ClassDescriptor cd) {
    return flags.get(cd);
  }

  /** Builds a table of flags for each class in the Bristlecone
   *	program.  It creates one hashtables: one which holds the
   *	ClassDescriptors and arrays of * FlagDescriptors as key-value
   *	pairs. */

  private void getFlagsfromClasses() {
    for(Iterator it_classes=state.getClassSymbolTable().getDescriptorsIterator(); it_classes.hasNext(); ) {
      ClassDescriptor cd = (ClassDescriptor)it_classes.next();
      Vector vFlags=new Vector();
      FlagDescriptor flag[];
      int ctr=0;

      /* Adding the flags of the super class */
      ClassDescriptor tmp=cd;
      while(tmp!=null) {
        for(Iterator it_cflags=tmp.getFlags(); it_cflags.hasNext(); ) {
          FlagDescriptor fd = (FlagDescriptor)it_cflags.next();
          vFlags.add(fd);
        }
        tmp=tmp.getSuperDesc();
      }

      flag=new FlagDescriptor[vFlags.size()];

      flags.put(cd,flag);
    }
  }
}