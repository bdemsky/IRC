import java.io.*;
import java.util.*;

public class DataParse {

  public static void main(String args[]) {

    try {

      FileWriter fout = new FileWriter("out.csv");
      BufferedWriter out = new BufferedWriter(fout);

      FileInputStream fin = new FileInputStream("errinj-history-5001.txt");
      DataInputStream in = new DataInputStream(fin);
      BufferedReader br = new BufferedReader(new InputStreamReader(in));
      String inLine;
      String value;
      while ((inLine = br.readLine()) != null) {
        // System.out.println(inLine);
        if (inLine.startsWith("idx")) {
          value = inLine.substring(4, inLine.length());
          out.write("\n" + value);
        } else if (inLine.startsWith("inj")) {
          if (inLine.length() > 3) {
            value = inLine.substring(4, inLine.length());
            if (value.length() > 0) {
              StringTokenizer st = new StringTokenizer(value, " ");
              if (st.hasMoreTokens()) {
                out.write("," + st.nextToken());
              }
            }
          }
        } else if (inLine.startsWith("NO DIFF")) {
          // do nothing
        } else {
          out.write("," + inLine);
        }
      }
      in.close();
      out.close();

    } catch (Exception e) {
      System.out.println(e);
    }

  }

}