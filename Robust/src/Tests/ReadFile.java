public class ReadFile {
    public static void main(String []str) {
	String filename="testfile000";
	FileInputStream fis=new FileInputStream(filename);
	byte x[]=new byte[9];
	fis.read(x);
	fis.close();
	String st=new String(x);
	System.printString(st);
    }

}
