public class TestDoubleToString {
  public TestDoubleToString() {

  }
  public static void main(String args[]) {
    TestDoubleToString td = new TestDoubleToString();
    double t = 35.21182666751214;
    String test = td.alpha(t);
    System.out.println("t= " + test);
    t = t/ 10.0d;
    test = td.alpha(t);
    System.out.println("t= " + test);
  }

  //function: converts a number into a string
  String alpha(double value)
  { 
    int i = 0, j = 0, k = 0;
    long nodecimal = 0;
    double decimal = 1.0d, valueA = 0.0d;
    StringBuffer output = new StringBuffer();

    for(i = 0; decimal != nodecimal; i++)
    {
      nodecimal = (long) (value*basePower(10, i));
      decimal = value*basePower(10, i);
    } //i = place counted from right that decimal point appears

    valueA = nodecimal; //valueA = value with no decimal point (value*10^i)

    for(j = 0; decimal >= 0; j++)
    {
      nodecimal = (long) (valueA - basePower(10, j));
      decimal = (double) nodecimal;
    } //j-1 = number of digits

    i--;
    j--;
    decimal = 0;

    for(k = j; k > 0; k--)
    {
      if(k == i) //if a decimal point was previously found
      {      //insert it where its meant to be
        //output += (char)46;
        output.append((char)46);
      }
      nodecimal = ((long) (valueA - decimal) / basePower(10, k-1));
      decimal += nodecimal*basePower(10, k-1);
      //output += (char)(48 + nodecimal);
      output.append((char)(48 + nodecimal));
    }

    System.out.println("output= " + output.toString());

    return output.toString();
  }

  long basePower(int x, int y) {
    long t = 1;
    for(int i=0; i<y; i++) {
      t *= x;
    }
    return t;
  }
}
