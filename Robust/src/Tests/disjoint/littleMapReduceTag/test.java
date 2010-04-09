task startup( StartupObject s{initialstate} ) {
  Master master = new Master(){reduceoutput};   
  master.assignMap();
  taskexit( s{!initialstate} );
}


task reduceOutput( Master master{reduceoutput} ) {
  master.setPartial( true );
  taskexit( master{!reduceoutput} );
}


public class Master {
  flag reduceoutput;
  boolean partial;

  public Master() {
    this.partial = false;
  }
  
  public boolean isPartial() {
    return this.partial;
  }
  
  public void setPartial( boolean partial ) {
    this.partial = partial || this.partial;
  }
  
  public void assignMap() {}
}
