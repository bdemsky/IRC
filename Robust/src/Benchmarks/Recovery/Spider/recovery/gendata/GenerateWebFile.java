import java.util.*;
import java.net.*;
import java.io.*;
public class GenerateWebFile {
  Vector wordList;
  File newfile;
  Vector main;

  public GenerateWebFile() {

  }

  public static void main(String[] args) {
    int numFiles = 3000;
    int numLinks = 10;
    GenerateWebFile gwf = new GenerateWebFile();
    gwf.wordList = gwf.fileToVector("wordList");
    for(int i = 0; i < 3000; i++) {
      Random rword = new Random(i);
      String title = gwf.genTitle(gwf.wordList, rword);
      String body = gwf.createBody(numLinks, rword);
      //System.out.println("\n\nPassed create Body\n\n");
      gwf.createFile(title, i, body);
      //System.out.println("\n\nPassed create File\n\n");
    }
  }

  public String genTitle(Vector v, Random rword) {
    String title = "";
    title = "";
    //Randomly pick  5 words to  generate Title
    title += v.elementAt(rword.nextInt(8000));
    for(int i=0; i<5; i++) {
      title += " ";
      title += v.elementAt(rword.nextInt(8000));
    }
    return title;
  }

  public String createBody(int numlinkinBody, Random rword) {
    String body = "";
    String hostname = null;
    int nextRandomWord;
    for(int i = 0; i< numlinkinBody; i++) {
      nextRandomWord = rword.nextInt(3000);
      hostname = "dc-11.calit2.uci.edu";
      body += "<a href=\"http://" + hostname + "/" + nextRandomWord + ".html\">XXXXX</a> <br>" + "\n";
    }
    return body;
  }


  public void createFile(String title, int index, String body) {
    try {
      String Filename = title+"/"+index+".html";
      BufferedWriter out = new BufferedWriter(new FileWriter(index+".html", true));
      out.write("<html>");
      out.write("<head>");
      out.write("<title>"+title+"</title>");
      //out.write(title);
      //out.write("</title>");
      out.write("</head>");
      out.write("<body>");
      out.write("Filling in body<br>");
      out.write(body);
      out.write("</body>");
      out.write("</html>");
      out.newLine();
      out.flush();
      out.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      System.exit(0);
    } catch(Exception e) {
      System.out.println("Exception!");
      System.exit(0);
    }
  }

  public String readFromfile(String filename) {
    String DataLine = "";
    try {
      File inFile = new File(filename);
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));

      DataLine = br.readLine();
      br.close();
    } catch (FileNotFoundException ex) {
      return null;
    } catch (IOException ex) {
      return null;
    }
    return DataLine;
  }

  public Vector fileToVector(String fileName) {
    Vector v = new Vector();
    String inputLine;
    try {
      File inFile = new File(fileName);
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
      while ((inputLine = br.readLine()) != null) {
        v.addElement(inputLine.trim());
      }
      br.close();
    }//Try
    catch (FileNotFoundException ex) {
      System.out.println("File Not Found");
    } catch (IOException ex) {
      System.out.println("I/O exception");
    }
    return v;
  }
}
