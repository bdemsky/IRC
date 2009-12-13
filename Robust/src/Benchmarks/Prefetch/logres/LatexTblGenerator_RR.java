// compile: javac LatexTblGenerator_RR.java
// run: java LatexTblGenerator_RR bench1.txt <outputfile>
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.Vector;

public class LatexTblGenerator_RR {
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
    public long rrnpnc;
    public long rrn;

    public BenchInfo(String benchname) {
      this.benchname = benchname;
      this.thdnum = -1;
      this.rrnpnc = -1;
      this.rrnpnc = -1;
    }
  }

  public LatexTblGenerator_RR(String outputorder,
                              String output) {
    try{
      this.inputfile = null;
      this.outputfile = output;
      this.outorderfile = outputorder;
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
      
      // parse the output order
      byte[] bb = new byte[1024 * 100];
      int length2 = this.outorderStream.read(bb);
      if(length2 < 0) {
        System.out.print("No content in input file: " + this.outorderfile + "\n");
        System.exit(-1);
      }
      String outorder = new String(bb, 0, length2);
      Vector<String> out_benchs = new Vector<String>();
      int inindex = outorder.indexOf('\n');
      while((inindex != -1) ) {
        out_benchs.addElement(outorder.substring(0, inindex));
        outorder = outorder.substring(inindex + 1);
        //System.printString(inline + "\n");
        inindex = outorder.indexOf('\n');
      }
      bb = null;

      Vector<Integer> thds = new Vector<Integer>();
      for(int ii = 0; ii < out_benchs.size(); ii++) {
        String benchname = out_benchs.elementAt(ii);
        inputfile = "/tmp/adash/prefetch_rst/totalrst_" + benchname + ".txt";
        this.inStream = new FileInputStream(this.inputfile);
        
        byte[] b = new byte[1024 * 100];
        int length = this.inStream.read(b);
        if(length < 0) {
          System.out.print("No content in input file: " + this.inputfile + "\n");
          System.exit(-1);
        }
        String inputdata = new String(b, 0, length);

        // process totalrst_${bench}.txt
        // format: THREAD   NPNC-RemoteRead NPNC-EXETime    NPNC-Abort  
        //         NPNC-Commit % NPNC-Abort    N-RemoteRead    N-PrefetchHit   
        //         N-EXETime   N-Abort N-Commit    % N-Abort   % Improvement   
        //         % PrefetchHit
        // omit the first line
        inindex = inputdata.indexOf('\n');
        inputdata = inputdata.substring(inindex + 1);
        inindex = inputdata.indexOf('\n');
        String inline = null;
        int tmpinindex = 0;
        while((inindex != -1) ) {
          inline = inputdata.substring(0, inindex);
          inputdata = inputdata.substring(inindex + 1);
          //System.printString(inline + "\n");
          tmpinindex = inline.indexOf('\t');
          int thdnum = Integer.parseInt(inline.substring(0, tmpinindex)); // 1st colum
          inline = inline.substring(tmpinindex + 1);
          while(inline.startsWith(" ")) {
            inline = inline.substring(1);
          }
          tmpinindex = inline.indexOf('\t');
          long rrnpnc = Long.parseLong(inline.substring(0, tmpinindex));  // 2nd colum
          inline = inline.substring(tmpinindex + 1);
          while(inline.startsWith(" ")) {
            inline = inline.substring(1);
          }
          tmpinindex = inline.indexOf('\t');  // 3rd colum
          inline = inline.substring(tmpinindex + 1);
          while(inline.startsWith(" ")) {
            inline = inline.substring(1);
          }
          tmpinindex = inline.indexOf('\t');  // 4th colum
          inline = inline.substring(tmpinindex + 1);
          while(inline.startsWith(" ")) {
            inline = inline.substring(1);
          }
          tmpinindex = inline.indexOf('\t');  // 5th colum
          inline = inline.substring(tmpinindex + 1);
          while(inline.startsWith(" ")) {
            inline = inline.substring(1);
          }
          tmpinindex = inline.indexOf('\t');  // 6th colum
          inline = inline.substring(tmpinindex + 1);
          while(inline.startsWith(" ")) {
            inline = inline.substring(1);
          }
          tmpinindex = inline.indexOf('\t');  
          long rrn = Long.parseLong(inline.substring(0, tmpinindex));  // 7th colum
          
          BenchInfo newbench = new BenchInfo(benchname);
          newbench.thdnum = thdnum;
          newbench.rrnpnc = rrnpnc;
          newbench.rrn = rrn;
          this.m_benchinfos.addElement(newbench);
          int i = 0;
          if(!thds.contains(thdnum)) {
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
          }
          inindex = inputdata.indexOf('\n');
        }
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
  2 & 39.56s & 35.58s & 62.69s & 62.46s & 62.06s & 60.08s & 445.1s & 405.93s & 9.85s & 8.70s\\
  4 & 21.31s & 19.37s & 36.55s & 32.66s & 36.92s & 33.31s & 232.06s & 215.98s & 6.37s & 5.83s\\
  8 & 12.29s & 11.31s & 21.15s & 20.40s & 23.63s & 20.01s & 128.17s & 111.87s & 5.08s & 4.74s\\
  \end{tabular}
  \end{center}
  }
       */
      DecimalFormat df = new DecimalFormat("#.00");
      
      outputFile.println("Remote Read Results Tbl:");
      outputFile.println("{");
      outputFile.println("\\footnotesize");
      outputFile.println("\\begin{center}");
      outputFile.print("\\begin{tabular}{c");
      for(int i = 0; i < out_benchs.size(); i++) {
        outputFile.print("|cc");
      }
      outputFile.println("}");
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
              if(info.rrnpnc != -1) {
                outputFile.print(info.rrnpnc + " & ");
              } else {
                outputFile.print("--- & ");
              }
              if(info.rrn != -1) {
                outputFile.print(info.rrn);
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
    LatexTblGenerator_RR rpp = new LatexTblGenerator_RR(args[0], args[1]);
    rpp.process();
  }

}
