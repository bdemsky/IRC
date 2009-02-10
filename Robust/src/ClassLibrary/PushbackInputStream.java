public class PushbackInputStream {
  // A pushback input stream lets you throw things
  // back into the stream to be read again.  Read
  // characters from a normal input stream, but keep
  // them in a ring buffer also.  Then, when unread()
  // is called, throw characters from the ring buffer
  // onto a stack.  During read() first take characters
  // off the stack if they are present, otherwise get
  // them from the normal input stream.

  private FileInputStream in;

  private int max;

  private int index;
  private int[] ring;

  private int top;
  private int bottom;
  private int[] stack;

  
  public PushbackInputStream(FileInputStream fis) {
    in = fis;
    max = 1000;

    index = 0;
    ring = new int[max];

    bottom = -1;
    top = bottom;
    stack = new int[max];
  }

  
  public int read() {
    int v;

    // get next value from stack or if empty
    // then from the input stream
    if( top > bottom ) {
      v = stack[top];
      --top;
    } else {
      v = in.read();
    }

    // put whatever it is in the ring buffer
    ring[index] = v;
    
    // keep ring buffer index
    ++index; 
    if( index == max ) { 
      index = 0; 
    }

    // user gets what they want
    return v;
  }


  public void unread(int v) {
    ++top;

    // the unread stack can only get so high
    if( top == max ) {
      System.printString( "PushbackInputStream: max reached" );
      System.exit( -1 );
    }

    // put it on the unread stack
    stack[top] = ring[index];
  }    
}