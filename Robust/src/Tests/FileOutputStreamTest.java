//compile: ../buildscript -mainclass FileOutputStreamTest FileOutputStreamTest.java

public class FileOutputStreamTest {
  public static void main(String[] args) {
    FileOutputStream fos = new FileOutputStream("/tmp/fostest.txt");
    System.out.println("###############");
    fos.FileOutputStream("/tmp/fostest.txt");
    byte[] b = new byte[1];
    b[0] = (byte) 'A';
    fos.write(b);//write one byte using FileOutputStream
    fos.flush();
    fos.close();
  }
}
