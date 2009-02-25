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

    list.addLast( (Object)new Integer( 6 ) );
    list.addLast( (Object)new Integer( 5 ) );
    list.addLast( (Object)new Integer( 4 ) );
    list.addLast( (Object)new Integer( 3 ) );

    System.out.println( "Looking for list 6, 5, 4, 3: " );
    System.out.print( "  " );
    Iterator i = list.iterator();
    while( i.hasNext() ) {
      System.out.print( i.next() + ", " );
    }
    System.out.println( "" );

    i = list.iterator();
    i.next();
    i.next();
    i.remove();

    System.out.println( "Removed 5, looking for list 6, 4, 3: " );
    System.out.print( "  " );
    i = list.iterator();
    while( i.hasNext() ) {
      System.out.print( i.next() + ", " );
    }
    System.out.println( "" );
  }
}