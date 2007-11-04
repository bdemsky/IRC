public class System {
    public static void printInt(int x) {
	String s=String.valueOf(x);
	printString(s);
    }

    public static native void printString(String s);

    public static void error() {
	System.printString("Error (Use Breakpoint on ___System______error method for more information!)\n");
    }

    public static native void exit(int status);
}
