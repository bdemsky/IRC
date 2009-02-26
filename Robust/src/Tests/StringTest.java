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

	String b = "Danger iN cAVErn_coVE";
	System.out.println( "normal: "+b );
	System.out.println( "upper:  "+b.toUpperCase() );
	System.out.println( "lower:  "+b.toLowerCase() );
    }
}
