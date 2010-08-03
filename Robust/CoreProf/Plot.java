import java.io.*;
import java.util.*;

public class Plot {
  PrintWriter out;
  PrintWriter command;
  String filename;
  int count=0;
  String cmdstr="plot ";
  Hashtable series;
  boolean first=true;
  boolean percent;
  public Plot(String filename, boolean percent) {
    this(filename);
    this.percent=percent;
  }

  public Plot(String filename) {
    try {
      command=new PrintWriter(new FileOutputStream(filename+".cmd"), true);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    this.filename=filename;
    series=new Hashtable();
  }

  public Series getSeries(String name) {
    if (series.containsKey(name))
      return (Series)series.get(name);
    Series s=createSeries(name);
    series.put(name, s);
    return s;
  }

  private Series createSeries(String name) {
    Series s=null;
    try {
      s=new Series(new PrintWriter(new FileOutputStream(filename+"."+count),true));
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    if (!first) cmdstr+=",";
    first=false;
    cmdstr+="\""+filename+"."+count+"\" title \""+name+"\"";
    count++;
    return s;
  }
  
  public void close() {
    for(Iterator it=series.values().iterator();it.hasNext();) {
      Series s=(Series)it.next();
      s.close();
    }
    command.println("set style data linespoints");
    command.println("set terminal postscript enhanced eps \"Times-Roman\" 18");
    command.println("set key left");
    command.println("set output \""+filename+"linear.eps\"");
    command.println(cmdstr);
    if (!percent) {
      command.println("set log y");
      command.println("set output \""+filename+"log.eps\"");
      command.println(cmdstr);
    }
    command.close();
  }
}