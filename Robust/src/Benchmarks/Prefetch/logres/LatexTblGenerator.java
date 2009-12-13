/**
  * compile: javac LatexTblGenerator.java
  * run: java LatexTblGenerator res.txt bench.txt latexresults1.txt
  *
**/

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.Vector;

public class LatexTblGenerator {
  String inputfile;
  String outorderfile;
  String outputfile;
  FileInputStream inStream;
  FileInputStream outorderStream;
  File outFile;
  
  Vector<BenchInfo> m_benchinfos;
  
  class BenchInfo {
    public String benchname;
    public int thdnum;
    public float exenpnc;
    public float exen;

    public BenchInfo(String benchname) {
      this.benchname = benchname;
      this.thdnum = -1;
      this.exenpnc = -1;
      this.exen = -1;
    }
  }

  public LatexTblGenerator(String input,
                           String outputorder,
                           String output) {
    try{
      this.inputfile = input;
      this.outputfile = output;
      this.outorderfile = outputorder;
      this.inStream = new FileInputStream(this.inputfile);
      this.outorderStream = new FileInputStream(this.outorderfile);
      this.outFile = new File(this.outputfile);
      this.m_benchinfos = new Vector<BenchInfo>();
    } catch(Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public void process() {
    try{
      FileWriter resultFile = new FileWriter(this.outFile);
      PrintWriter outputFile = new PrintWriter(resultFile);

      byte[] b = new byte[1024 * 100];
      int length = this.inStream.read(b);
      if(length < 0) {
        System.out.print("No content in input file: " + this.inputfile + "\n");
        System.exit(-1);
      }
      String inputdata = new String(b, 0, length);
      Vector<Integer> thds = new Vector<Integer>();

      // process average.txt
      // format: benchname  # thread  Exe-NPNC  Exe-N
      int inindex = inputdata.indexOf('\n');
      String inline = null;
      int tmpinindex = 0;
      while((inindex != -1) ) {
        inline = inputdata.substring(0, inindex);
        inputdata = inputdata.substring(inindex + 1);
        //System.out.println("inline= " + inline + "   inputdata= " + inputdata);
        tmpinindex = inline.indexOf(' ');
        String benchname = inline.substring(0, tmpinindex);
        inline = inline.substring(tmpinindex + 1);
        while(inline.startsWith(" ")) {
          inline = inline.substring(1);
        }
        tmpinindex = inline.indexOf(' ');
        int thdnum = Integer.parseInt(inline.substring(0, tmpinindex));
        inline = inline.substring(tmpinindex + 1);
        while(inline.startsWith(" ")) {
          inline = inline.substring(1);
        }
        tmpinindex = inline.indexOf(' ');
        float exenpnc = Float.parseFloat(inline.substring(0, tmpinindex));
        inline = inline.substring(tmpinindex + 1);
        while(inline.startsWith(" ")) {
          inline = inline.substring(1);
        }
        tmpinindex = inline.indexOf(' ');
        float exen = Float.parseFloat(inline.substring(0, tmpinindex));
        BenchInfo newbench = new BenchInfo(benchname);
        newbench.thdnum = thdnum;
        newbench.exenpnc = exenpnc;
        newbench.exen = exen;
        this.m_benchinfos.addElement(newbench);
        int i = 0;
        for(; i < thds.size(); i++) {
          if(thds.elementAt(i) > thdnum) {
            thds.insertElementAt(thdnum, i);
            break;
          } else if(thds.elementAt(i) == thdnum) {
            break;
          }
        }
        if(i == thds.size()) {
          thds.addElement(thdnum);
        }
        inindex = inputdata.indexOf('\n');
      }
      
      // parse the output order
      byte[] bb = new byte[1024 * 100];
      int length2 = this.outorderStream.read(bb);
      if(length2 < 0) {
        System.out.print("No content in input file: " + this.outorderfile + "\n");
        System.exit(-1);
      }
      String outorder = new String(bb, 0, length2);
      Vector<String> out_benchs = new Vector<String>();
      inindex = outorder.indexOf('\n');
      while((inindex != -1) ) {
        out_benchs.addElement(outorder.substring(0, inindex));
        outorder = outorder.substring(inindex + 1);
        //System.printString(inline + "\n");
        inindex = outorder.indexOf('\n');
      }
      
      // output average time tbl
      // format:
      /*
       * {
  \footnotesize
  \begin{center}
  \begin{tabular}{c|cc|cc|cc|cc|cc}
  & \multicolumn{2}{|c|}{2D Conv} & \multicolumn{2}{|c|}{Moldyn} & \multicolumn{2}{|c|}{Matrix Multiply} & \multicolumn{2}{|c|}{SOR} & \multicolumn{2}{|c}{2DFFT}\\
  \hline
  & Base & Prefetch & Base & Prefetch & Base & Prefetch & Base & Prefetch & Base & Prefetch\\
  1J & 69.59 & --- & 122.97s & --- & 104.67s & --- & 351.36s & --- & 9.99s & ---\\
  1 & 73.39s & --- & 123.17s & --- & 105.79s & --- & 844.69s & --- & 14.40s & ---\\
  2 & 39.56s & 35.58s & 62.69s & 62.46s & 62.06s & 60.08s & 445.1s & 405.93s & 9.85s & 8.70s\\
  4 & 21.31s & 19.37s & 36.55s & 32.66s & 36.92s & 33.31s & 232.06s & 215.98s & 6.37s & 5.83s\\
  8 & 12.29s & 11.31s & 21.15s & 20.40s & 23.63s & 20.01s & 128.17s & 111.87s & 5.08s & 4.74s\\
  \end{tabular}
  \end{center}
  }
       */
      DecimalFormat df = new DecimalFormat("#.00");
      
      outputFile.println("Numerical Benchmark Results Tbl:");
      outputFile.println("{");
      outputFile.println("\\footnotesize");
      outputFile.println("\\begin{center}");
      outputFile.print("\\begin{tabular}{c");
      for(int i = 0; i < out_benchs.size(); i++) {
        outputFile.print("|cc");
      }
      for(int i = 0; i < out_benchs.size(); i++) {
        outputFile.print("& \\multicolumn{2}{|c|}{");
        outputFile.print(out_benchs.elementAt(i));
        outputFile.print("} ");
      }
      outputFile.println("\\\\");
      outputFile.println("\\hline");
      for(int i = 0; i < out_benchs.size(); i++) {
        outputFile.print("& Base & Prefetch ");
      }
      outputFile.println("\\\\");
      for(int i = 0; i < thds.size(); i++) {
        int thd = thds.elementAt(i);
        if(thd == 0) {
          outputFile.print("1J");
        } else {
          outputFile.print(thd);
        }
        for(int j = 0; j < out_benchs.size(); j++) {
          String bench = out_benchs.elementAt(j);
          outputFile.print(" & ");
          int k = 0;
          for(; k < this.m_benchinfos.size(); k++) {
            BenchInfo info = this.m_benchinfos.elementAt(k);
            if(info.benchname.equals(bench) && (info.thdnum == thd)) {
              if(info.exenpnc != -1) {
                outputFile.print(df.format(info.exenpnc) + "s & ");
              } else {
                outputFile.print("--- & ");
              }
              if(info.exen != -1) {
                outputFile.print(df.format(info.exen) + "s");
              } else {
                outputFile.print("---");
              }
              break;
            }
          }
          if(k == this.m_benchinfos.size()) {
            // did not find the bench
            outputFile.print("--- & ---");
          }
        }
        outputFile.println("\\\\");
      }
      
      outputFile.println("\\end{tabular}");
      outputFile.println("\\end{center}");
      outputFile.println("}");
      outputFile.flush();

    } catch(Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public static void main(String[] args) throws Exception {
    LatexTblGenerator rpp = new LatexTblGenerator(args[0], args[1], args[2]);
    rpp.process();
  }

}
