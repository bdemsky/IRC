public class Math {
    //public static final double PI=3.14159265358979323846;
    
    public static double fabs(double x) {
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

    public static native double sin(double a);
    public static native double cos(double a);
    public static native double asin(double a);
    public static native double acos(double a);
    public static native double tan(double a);
    public static native double atan(double a);
    public static native double exp(double a);
    public static native double sqrt(double a);
    public static native double log(double a);
    public static native double pow(double a, double b);
}
