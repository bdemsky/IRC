import java.io.*;
import java.util.*;

public class filter 
{
  public static void main(String[] args)
  {
    
    ArrayList wordpool = new ArrayList<String>();
    String line;
    int cnt=0;

    try {
      BufferedReader br = new BufferedReader(new FileReader(args[0]));
      BufferedWriter bw = new BufferedWriter(new FileWriter(args[1]));

      while((line = br.readLine()) != null) {


      StringTokenizer stk = new StringTokenizer(line);

      while(stk.hasMoreTokens())
      {
        String word = stk.nextToken();
        
        // if word is not in wordpool
        if(!wordpool.contains(word))
        {
          wordpool.add(word);
          bw.write(word);
          cnt++;
          bw.newLine();
        }
      }
      }
      br.close();
      bw.close();
    }catch(IOException e) {
      System.out.println(e);
    }
    System.out.println("Number of new Words = " + cnt);
  }
}




