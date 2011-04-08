
/**
 * A class that represents a wrapper around a double value so 
 * that we can use it as an 'out' parameter.  The java.lang.Double
 * class is immutable.
 **/
public class MyDouble
{
  public float value;
  MyDouble(float d)
  {
    value = d;
  }
  /*public String toString()
  {
    return Double.toString(value);
  }*/
}
