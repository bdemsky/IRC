public class System {
    public static void printInt(int x) {
	String s=String.valueOf(x);
	printString(s);
    }

    public static native void printString(String s);
}
