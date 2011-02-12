public class Math {

  public static double setPI() {
    double PI = 3.14159265358979323846;
    return PI;
  }

  // an alias for setPI()
  public static double PI() {
    double PI = 3.14159265358979323846;
    return PI;
  }

  public static int abs(int x) {
    if (x < 0) {
      return -x;
    } else {
      return x;
    }
  }

  public static double fabs(double x) {
    if (x < 0) {
      return -x;
    } else {
      return x;
    }
  }

  public static double abs(double x) {
    if (x < 0) {
      return -x;
    } else {
      return x;
    }
  }

  public static float abs(float a) {
    if (a<0)
      return -a;
    else return a;
  }

  public static double max(double a, double b) {
    if(a == b)
      return a;
    if(a > b) {
      return a;
    } else {
      return b;
    }
  }

  public static int max(int a, int b) {
    if(a == b)
      return a;
    if(a > b) {
      return a;
    } else {
      return b;
    }
  }

  public static int imax(int a, int b) {
    if(a == b)
      return a;
    if(a > b) {
      return a;
    } else {
      return b;
    }
  }

  public static int imin(int a, int b) {
    if(a == b)
      return a;
    if(a > b) {
      return b;
    } else {
      return a;
    }
  }

  /** sqrt(a^2 + b^2) without under/overflow. **/
  public static double hypot(double a, double b) {
    double r;
    if (fabs(a) > fabs(b)) {
      r = b/a;
      r = fabs(a)*sqrt(1+r*r);
    } else if (b != 0) {
      r = a/b;
      r = fabs(b)*sqrt(1+r*r);
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
