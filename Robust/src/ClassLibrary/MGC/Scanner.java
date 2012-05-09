public class Scanner implements Iterator {
  private String sourcename;
  private int currentpos;

  public Scanner (final String source)	{
    this.sourcename = source;
    this.currentpos = 0;
  }

  public void close () {
  }

  public double nextDouble () {
      return Double.parseDouble(new String(next()));
  }

  public int nextInt () {
      return Integer.parseInt(new String(next()));
  }
  
  private native char[] next();
}
