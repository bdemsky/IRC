task startup( StartupObject s{initialstate} ) {
  Master master = new Master(){reduceoutput};   
  master.assignMap();
  taskexit( s{!initialstate} );
}

/*
task reduceOutput( Master master{reduceoutput} ) {
  if( false ) {
    master.addInterOutput();
  } else {
    master.setPartial( true );
    taskexit( master{!reduceoutput} );
  }

  taskexit( master{!reduceoutput} );
}
*/

public class Master {
  flag reduceoutput;

  boolean  partial;
  Vector[] interoutputs;

  public Master() {
    this.partial      = false;
    this.interoutputs = new Vector[1];
  }
  
  public boolean isPartial() {
    return this.partial;
  }
  
  public void setPartial( boolean partial ) {
    this.partial = partial || this.partial;
  }

  public void addInterOutput() {
    interoutputs[0].addElement( new Vector() );
  }
  
  public void assignMap() {
    assignMap2();
  }

  public void assignMap2() {
    assignMap3();
  }

  public void assignMap3() {}

}
