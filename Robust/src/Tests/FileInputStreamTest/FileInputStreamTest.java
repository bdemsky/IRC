public class FileInputStreamTest {
  static public void main( String[] args ) {
    FileInputStream in = new FileInputStream( "test.txt" );

    System.out.println( "#####################" );
    String line = in.readLine();
    while( line != null ) {
      System.out.println( line );
      line = in.readLine();
    } 
    System.out.println( "#####################" );
    in.close();
  }
}