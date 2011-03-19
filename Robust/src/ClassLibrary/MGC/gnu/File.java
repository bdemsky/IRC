import java.io.FileSystem;
import java.io.FilenameFilter;
import java.util.ArrayList;

public class File {
  String path;
  private static final char separator = '\n';
  private static final char separatorChar = '\n';
  private static final char pathSeparatorChar = ';';

  public File(String path) {
    this.path=path;
  }

  String getPath() {
    return path;
  }

  long length() {
    return nativeLength(path.getBytes());
  }

  private static native long nativeLength(byte[] pathname);
  
  public boolean exists() {
    // TODO System.println("Unimplemented File.exists()");
    return false;
  }
  
  public boolean isDirectory() {
    // TODO System.println("Unimplemented File.isDirectory()");
    return false;
  }
  
  public boolean mkdirs() {
    // TODO System.println("Unimplemented File.mkdirs()");
    return false;
  }
  
  public boolean delete() {
    // TODO System.println("Unimplemented File.delete()");
    return false;
  }
  
  public String[] list(FilenameFilter filter) {
    /*String names[] = list();
    if ((names == null) || (filter == null)) {
      return names;
    }
    ArrayList v = new ArrayList();
    for (int i = 0 ; i < names.length ; i++) {
      if (filter.accept(this, names[i])) {
        v.add(names[i]);
      }
    }
    return (String[])(v.toArray(new String[0]));*/
    // TODO System.println("Unimplemented File.list()");
    return null;
  }
}
