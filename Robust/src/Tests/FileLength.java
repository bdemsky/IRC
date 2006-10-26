public class FileLength {
    public static void main(String []str) {
	String filename="testfile000";
	File fi=new File(filename);
	long length=fi.length();
	String st=String.valueOf((int)length);
	System.printString(st);
    }

}
