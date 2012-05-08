public class Scanner implements Iterator {
  private String sourcename;

  public Scanner (final String source)	{
    this.sourcename = source;
  }

  public void close () {
  }

  public native double nextDouble ();

  public native int nextInt ();
}