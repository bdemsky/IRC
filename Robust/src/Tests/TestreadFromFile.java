/**
 * Tests read from a  file that uses buffering directly, thereby eliminating the read method calls
 * to read number of rows from an input txt file
 **/
public class TestreadFromFile {
  public static void main(String[] args) {
    int numRows = 0;
    int MAX_LINE_LENGTH = 1000000; /* max input is 400000 one digit input + spaces */
    String filename;
    filename = "input/random-n2048-d16-c16.txt";
    FileInputStream inputFile = new FileInputStream(filename);
    byte buf[] = new byte[MAX_LINE_LENGTH];
    int n;
    while ((n = inputFile.read(buf)) != 0) {
      for (int i = 0; i < n; i++) {
        if (buf[i] == '\n')
          numRows++;
      }
    }
    inputFile.close();
    System.out.println("numRows= " +numRows);
  }
}

/**
 * compile:
 * ../buildscript TestreadFromFile.java -mainclass TestreadFromFile -o TestreadFromFile
 **/
