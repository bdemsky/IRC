class StringBufferTest {
    public static void main(String str[]) {
	String a="hello world";
	StringBuffer b=new StringBuffer(a);
	b.append(a);
	System.printString(b.toString());
    }
}
