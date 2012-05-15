package Util;

import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

public class InputFileTranslator{
    public static String PREFIX="";
    
    public static void main(String args[]){
	try{
	    //To Uncompress GZip File Contents we need to open the gzip file.....
	    if(args.length<=0){
		System.out.println("Please enter the valid file name");
	    }else{
		int offset = 0;
		if (args[offset].equals("-dir")) {
		    PREFIX=args[++offset]+"/";
		    offset++;
		}

		System.out.println("Opening the output file............. : opened");
		
		//Create the .h file 
		String outFilename = PREFIX+"InputFileArrays.h";
		PrintWriter out = new PrintWriter(new FileWriter(new File(outFilename)));
		out.println("#ifndef INPUT_FILE_ARRAYS_H");
		out.println("#define INPUT_FILE_ARRAYS_H");
		out.println();
		out.println("#ifndef bool");
		out.println("typedef int bool;");
		out.println("#define true 1");
		out.println("#define false 0");
		out.println("#endif");
		out.println();
		out.println("int filename2fd(char * filename, int length);");
		out.println("int nextInt(int fd, int * pos);");
		out.println("double nextDouble(int fd, int * pos);");
		out.println("#endif");
		out.close();
		
		//Create the .c file that contains the big two-dimension array and 
		//read functions
		outFilename = PREFIX+"InputFileArrays.c";
		out = new PrintWriter(new FileWriter(new File(outFilename)));
		out.println("#include \"InputFileArrays.h\"");
		out.println();
		Vector<String> sourcefiles=new Vector<String>();
		for(int i=0; i<args.length-offset; i++)  {
		    String inFilename = args[i+offset];
		    String arrayname = inFilename.replaceAll("\\.", "_");
		    sourcefiles.add(arrayname);
		    out.println("#define " + arrayname.toUpperCase() + " " + i);
		    System.out.println("Opening the gzip file.......................... :  opened");
		    GZIPInputStream gzipInputStream = null;
		    FileInputStream fileInputStream = null;
		    gzipInputStream = new GZIPInputStream(new FileInputStream(inFilename));
		    //OutputStream out = new FileOutputStream(outFilename);
		    System.out.println("Trsansferring bytes from the compressed file to the output file........:  Transfer successful");
		    
		    out.println("unsigned char " + arrayname + "[] = {");

		    boolean printComma = false;
		    boolean printReturn = false;
		    byte[] buf = new byte[1024];  //size can be changed according to programmer's need.
		    int len;
		    while ((len = gzipInputStream.read(buf)) > 0) {
			//out.write(buf, 0, len);
			for(int j = 0; j < len; j++) {
			    if('#' == buf[j]) {
				// skip the comments
				while(j<len && buf[j++] != '\n') {
				}
			    } else {
				if(!printComma) {
				    printComma = true;
				} else {
				    out.print(',');
				}
				if(printReturn) {
				    out.println();
				    printReturn = false;
				}
				if('\n' == buf[j]) {
				    out.print("\'\\n\'");
				    printReturn = true;
				} else {
				    out.print(buf[j]);
				}
			    }
			}
		    }
		    out.println("};");
		    out.println();
		    gzipInputStream.close();
		}
		
		out.println();
		out.println("unsigned char * inputFileArrays[]= {");
		for(int i = 0; i < sourcefiles.size(); i++) {
		    String arrayname = sourcefiles.elementAt(i);
		    
		    if(i > 0) {
			out.println(',');
		    }
		    out.print("&"+arrayname+"[0]");
		}
		out.println();
		out.println("};");
		out.println();
		
		//Create the read functions
		out.println("int filename2fd(char * filename, int length) {");
		for(int i=0; i<sourcefiles.size(); i++)  {
		    String inFilename = sourcefiles.elementAt(i);
		    String arrayname = inFilename.replaceAll("\\.", "_");
		    out.print("  char " + arrayname + "[] = {");
		    for(int j = 0; j < inFilename.length(); j++) {
			if(j>0) {
			    out.print(",");
			}
			out.print("'"+inFilename.charAt(j)+"'");
		    }
		    out.println("};");
		    out.println("  if(length==" + inFilename.length() + ") {");
		    out.println("    int i;");
		    out.println("    for(i = 0; i < length; i++) {");
		    out.println("      if(filename[i]!="+arrayname+"[i]) {");
		    out.println("        break;");
		    out.println("      }");
		    out.println("    }");
		    out.println("    if(i==length) {");
		    out.println("      return "+arrayname.toUpperCase() + ";");
		    out.println("    }");
		    out.println("  }");
		    out.println();
		}
		out.println("}");
		
		out.println();
		out.println("int nextInt(int fd, int * pos) {");
		out.println("    int i = 0;");
		out.println("    unsigned char * filearray = inputFileArrays[fd];");
		out.println("    while((filearray[*pos]==\' \')||(filearray[*pos]==\'\\n\')){");
		out.println("  	  *pos++;");
		out.println("    }");
		out.println("    int value = 0;");
		out.println("    bool isNeg=false;");
	        out.println("    int radix = 10;");
	        out.println();
	        out.println("    if (filearray[*pos]==\'-\') {");
	        out.println("      isNeg=true;");
	        out.println("      *pos++;");
	        out.println("    }");
	        out.println("    bool cont=true;");
	        out.println("    do {");
	        out.println("  	  unsigned char b=filearray[*pos];");
	        out.println("        int val;");
	        out.println("        if (b>=\'0\'&&b<=\'9\')");
	        out.println("          val=b-\'0\';");
	        out.println("        else if (b>=\'a\'&&b<=\'z\')");
	        out.println("          val=10+b-\'a\';");
	        out.println("       else if (b>=\'A\'&&b<=\'Z\')");
	        out.println("          val=10+b-\'A\';");
	        out.println("        else {");
	        out.println("          cont=false;");
	        out.println("        }");
	        out.println("        if (cont) {");
	        out.println("          if (val>=radix)");
	        out.println("            printf(\"Error in nextInt(): val >= radix\");");
	        out.println("          value=value*radix+val;");
	        out.println("          *pos++;");
	        out.println("        }");
	        out.println("    }while(cont);");
	        out.println("    if (isNeg)");
	        out.println("  	  value=-value;");
	        out.println();
	        out.println("    return value;");
	        out.println("  }");
	        out.println();
		
	        out.println("double nextDouble(int fd, int * pos) {");
	        out.println("    int i = 0;");
	        out.println("    unsigned char * filearray = inputFileArrays[fd];");
	        out.println("    while((filearray[*pos]==\' \')||(filearray[*pos]==\'\\n\')){");
	        out.println("  	  *pos++;");
	        out.println("    }");
	        out.println("    double value = 0.0;");
	        out.println("//TODO");
	        out.println("    return value;");
	        out.println("  }");
	        out.println();

		System.out.println("The file and stream is ......closing.......... : closed");
		out.close();
	    }
	}
	catch(IOException e){
	    System.out.println("Exception has been thrown" + e);
	}
    }
}
