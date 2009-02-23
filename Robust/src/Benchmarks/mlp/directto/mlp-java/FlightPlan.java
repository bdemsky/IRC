// this class implements a flight plan

class FlightPlan {
  public double cruiseAlt, cruiseSpeed; // cruising altitude and speed

  public Route r; // the route (given by fixes)
  
  public FlightPlan() {
    cruiseAlt=0;
    cruiseSpeed=0;
  }

  public FlightPlan (FlightPlan fp) {
    cruiseAlt=fp.cruiseAlt;
    cruiseSpeed=fp.cruiseSpeed;
  }

  public void setCruiseParam(double crAlt, double crSp) {
    cruiseAlt=crAlt;cruiseSpeed=crSp;    
  }

  public void setRoute(Route route) {
    this.r=route;
  }

  public void setCurrentFix(String nameFix) {
    int i=r.getIndexOf(nameFix);
    System.out.println("name of the fix: "+nameFix+" index:"+i);
    r.setCurrent(i);
  }	
}
