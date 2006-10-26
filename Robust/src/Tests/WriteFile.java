public class WriteFile {
    public static void main(String []str) {
	String filename="testfile000";
	FileOutputStream fos=new FileOutputStream(filename);
	String st=new String("adsasdasd");
	fos.write(st.getBytes());
	fos.close();
    }

}
