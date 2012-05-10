public class Scanner implements Iterator {
  private String sourcename;
  private int currentpos;
  private int filearray;

  public Scanner (final String source)	{
    this.sourcename = source;
    this.currentpos = 0;
  }

  public void close () {
  }

  public native double nextDouble ();

  public native int nextInt ();
}
