public class File {
  String path;

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
}
