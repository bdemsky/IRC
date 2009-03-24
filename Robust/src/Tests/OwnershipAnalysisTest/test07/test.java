public class Engine {
  LinkedList[] actions;

  public Engine() {
    actions = new LinkedList[10];
    for( int i = 0; i < 10; ++i ) {
      actions[i] = disjoint blah new LinkedList();
    }
  }

  public add( Action a, int list ) {
    actions[list].addFirst( a );
  }
}

public class StandardEngine extends Engine {
  public StandardEngine( Gen gen ) {
    Engine();

    Action a = new AntherAction( gen );
    add( a, 0 );
    //add( a, 1 );
   
    Action b = new AntherAction( gen );    
    add( b, 0 );    
  }
}

public class Action {
  Gen gen;
  public Action( Gen g ) {
    gen = g;
  }
}

public class AntherAction extends Action {
  public AntherAction( Gen g ) {
    Action( g );
  }
}

public class Gen {
  public Gen() {}
}

public class Test {

  static public void main( String[] args ) {
    Gen gen = new Gen();
    StandardEngine se = new StandardEngine( gen );
  }
}
