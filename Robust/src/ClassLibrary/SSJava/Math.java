@LATTICE("B<T")
@METHODDEFAULT("OUT<IN")
public class Math {
  @LOC("T") static final double PI=3.14159265358979323846;

  // an alias for setPI()
  public static double PI() {
    double PI = 3.14159265358979323846;
    return PI;
  }

  public static int abs(int x) {
    return (x<0)?-x:x;
  }

  public static long abs(long x) {
    return (x<0)?-x:x;
  }

  public static double abs(double x) {
    return (x<0)?-x:x;
  }

  public static float abs(float x) {
    return (x<0)?-x:x;
  }

  public static double max(double a, double b) {
    return (a>b)?a:b;
  }

  public static float max(float a, float b) {
    return (a>b)?a:b;
  }

  public static int max(int a, int b) {
    return (a>b)?a:b;
  }

  public static long max(long a, long b) {
    return (a>b)?a:b;
  }
  
  @RETURNLOC("IN")
  public static double min(@LOC("IN") double a, @LOC("IN") double b) {
    return (a<b)?a:b;
  }

  @RETURNLOC("IN")
  public static float min(@LOC("IN") float a, @LOC("IN") float b) {
    return (a<b)?a:b;
  }

  @RETURNLOC("IN")
  public static int min(@LOC("IN") int a, @LOC("IN") int b) {
    return (a<b)?a:b;
  }

  @RETURNLOC("IN")
  public static long min(@LOC("IN") long a, @LOC("IN") long b) {
    return (a<b)?a:b;
  }

  /** sqrt(a^2 + b^2) without under/overflow. **/
  public static double hypot(double a, double b) {
    double r;
    if (abs(a) > abs(b)) {
      r = b/a;
      r = abs(a)*sqrt(1+r*r);
    } else if (b != 0) {
      r = a/b;
      r = abs(b)*sqrt(1+r*r);
    } else {
      r = 0.0;
    }
    return r;
  }

  public static double rint(double x) {
    double y = ceil(x);
    double d = y - x;
    if( d == 0.5 ) {
      if( ((int)y)%2 == 0 ) {
        return y;
      } else {
        return y - 1.0;
      }
    } else if( d < 0.5 ) {
      return y;
    }
    return y - 1.0;
  }

  public static native double sin(double a);
  public static native double cos(double a);
  public static native double asin(double a);
  public static native double acos(double a);
  public static native double tan(double a);
  public static native double atan(double a);
  public static native double atan2(double a, double b);
  public static native double exp(double a);
  public static native double sqrt(double a);
  public static native double log(double a);
  public static native double pow(double a, double b);

  public static native double ceil(double a);
  public static native double floor(double a);

  public static native float sinf(float a);
  public static native float cosf(float a);
  public static native float expf(float a);
  public static native float sqrtf(float a);
  public static native float logf(float a);
  public static native float powf(float a, float b);
  public static native float ceilf(float a);
  public static native float IEEEremainder(float f1, float f2);
}
