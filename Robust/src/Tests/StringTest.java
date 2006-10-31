class StringTest {
    public static void main(String str[]) {
	String a="hello world\n";
	System.printString(a);
	System.printInt(a.indexOf('e'));
	System.printString("\n");
	System.printInt(a.indexOf("world"));
	System.printString("\n");
	System.printString(a.subString(3));
	System.printString(a.subString(3,6));
	System.printString("\n");
    }
}
