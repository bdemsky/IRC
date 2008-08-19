public class FileWriter extends OutputStreamWriter {
  public FileWriter(String file, boolean append) {
    super(new FileOutputStream(file, append));
  }

}
