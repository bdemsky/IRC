// command line player for MPEG audio file
public class MP3Player {

  private String filename = null;

  public static void main(String args[]) {

    MP3Player player = new MP3Player();
    player.init(args);

  }

  private void init(String[] args) {
    if (args.length == 1) {
      filename = args[0];
    }
  }

  /**
   * Playing file from FileInputStream.
   */
  protected InputStream getInputStream() throws IOException {
    FileInputStream fin = new FileInputStream(filename);
    BufferedInputStream bin = new BufferedInputStream(fin);
    return bin;
  }

  public void play() throws JavaLayerException {
    try {
      System.out.println("playing " + filename + "...");
      InputStream in = getInputStream();
      AudioDevice dev = new AudioDevice();
      Player player = new Player(in, dev);
      player.play();
    } catch (IOException ex) {
      throw new JavaLayerException("Problem playing file " + filename, ex);
    } catch (Exception ex) {
      throw new JavaLayerException("Problem playing file " + filename, ex);
    }
  }

}