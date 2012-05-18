public class Scanner implements Iterator {
  private FileInputStream source;
  private int currentpos;
  private int isHighbits;
  private int fd;

  public Scanner (final String source)	{
    this.source = new FileInputStream(source);
    this.fd = this.source.getfd();
    this.currentpos = 0;
    this.isHighbits = 1;
  }

  public void close () {
      this.source.close();
  }

  public native double nextDouble ();

  public native int nextInt ();
}
