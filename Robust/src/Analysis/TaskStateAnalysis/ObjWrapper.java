package Analysis.TaskStateAnalysis;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;

public class ObjWrapper implements Wrapper {
  FlagState initfs;
  HashSet<FlagState> fs;
  HashSet<TagWrapper> tags;

  public ObjWrapper(FlagState fs) {
    this.initfs=fs;
    this.fs=new HashSet<FlagState>();
    tags=new HashSet<TagWrapper>();
  }

  public ObjWrapper() {
    this.fs=new HashSet<FlagState>();
    this.tags=new HashSet<TagWrapper>();
  }


}
