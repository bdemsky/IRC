
public class Test {
  static public void main( String[] args ) {

    FileInputStream in = new FileInputStream( "input.txt" );

    String strLine = in.readLine();

    while( strLine != null ) {

      System.out.println( "Read line:["+strLine+"]" );
      System.out.print  ( "  with tokens: " );

      StringTokenizer tokenizer = 
        new StringTokenizer( strLine, // string to tokenize
                             " " );   // delimiters

      while( tokenizer.hasMoreTokens() ) {
        String token = tokenizer.nextToken();
        System.out.print( "("+token+"), " );
      }

      System.out.println( "" );
      
      strLine = in.readLine();
    }

    in.close();
  }   
}
