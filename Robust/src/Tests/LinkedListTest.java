public class LinkedListTest {
  public static main( String[] args ) {

    LinkedList list = new LinkedList();
    System.out.println( "list should have zero elements: "+list.size() );

    list.push( (Object)new Integer( 3 ) );
    list.push( (Object)new Integer( 4 ) );

    System.out.println( "list should have two elements: "+list.size() );

    Integer x = (Integer)list.pop();
    x = (Integer)list.pop();

    System.out.println( "should be a 3: "+x );
  }
}