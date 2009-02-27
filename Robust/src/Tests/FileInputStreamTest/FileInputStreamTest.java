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
    System.out.println( "["+in.read()+"]" );
    System.out.println( "["+in.read()+"]" );
    in.close();

    
    in = new FileInputStream( "charmap.txt" );
    System.out.println( "\n\n\n#####################" );
    line = in.readLine();
    while( line != null ) {
      System.out.println( line );
      for( int i = 0; i < line.length(); ++i ) {
	System.out.print( line.charAt( i )+"." );
      }
      System.out.println( "" );
      line = in.readLine();
    } 
    System.out.println( "#####################" );
    System.out.println( "["+in.read()+"]" );
    System.out.println( "["+in.read()+"]" );
    in.close();


    in = new FileInputStream( "test.txt" );
    System.out.println( "\n\n\n#####################" );
    int c = in.read();
    while( c != -1 ) {
      System.out.print( c+"." );
      c = in.read();
    } 
    System.out.println( "#####################" );
    System.out.println( "["+in.read()+"]" );
    System.out.println( "["+in.read()+"]" );
    in.close();
  }
}