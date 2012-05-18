package Util;

import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
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
		boolean isCompressed = true;

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
		out.println("int nextInt(int fd, int * pos, int * isHighbits);");
		out.println("double nextDouble(int fd, int * pos, int * isHighbits);");
		out.println("#endif");
		out.close();
		
		//Create the .c file that contains the big two-dimension array and 
		//read functions
		outFilename = PREFIX+"InputFileArrays.c";
		out = new PrintWriter(new FileWriter(new File(outFilename)));
		out.println("#include \"InputFileArrays.h\"");
		out.println("#include \"math.h\"");
		out.println();
		Vector<String> sourcefiles=new Vector<String>();
		Vector<Boolean> compressVec = new Vector<Boolean>();
		Hashtable<Byte, Integer> encodemapping = new Hashtable<Byte, Integer>();
		Vector<Byte> encodeVec = new Vector<Byte>();
		int encodenum = 1;
		int returncount = 10;
		for(int i=0; i<args.length-offset; i++)  {
		    if(isCompressed) {
			encodemapping.clear();
			encodeVec.clear();
			encodenum = 0;
			encodemapping.put((byte)0, 0);
			encodeVec.add((byte)0);
		    }
		    String inFilename = args[i+offset];
		    String arrayname = inFilename.replaceAll("\\.", "_");
		    sourcefiles.add(inFilename);
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
		    int writenum = isCompressed?2:1;
		    compressVec.add(isCompressed?true:false);
		    int bitmask = 0x00FF;
		    while ((len = gzipInputStream.read(buf)) > 0) {
			//out.write(buf, 0, len);
			for(int j = 0; j < len; ) {
			    // read out 2 bytes at a time
			    byte[] towrite = new byte[writenum];
			    for(int k = 0; k<writenum&&j<len; k++) {
				if('#' == buf[j]) {
				    // skip the comments
				    while(j<len && buf[j++] != '\n') {
				    }
				} else {
				    towrite[k] = buf[j];
				}
				j++;
			    }
			    int writevalue = towrite[0];
			    if(isCompressed) {
				// compress two value into one for compressed version
				if(!encodemapping.containsKey(towrite[0])) {
				    encodemapping.put(towrite[0], encodenum++);
				    encodeVec.add(towrite[0]);
				}
				writevalue = encodemapping.get(towrite[0]);
				if(!encodemapping.containsKey(towrite[1])) {
				    encodemapping.put(towrite[1], encodenum++);
				    encodeVec.add(towrite[1]);
				}
				if(encodeVec.size()>=16) {
				    System.err.println("Error! The compressed file has more than 16 types of characters! It can not be compressed!");
				    System.exit(-1);
				}
				writevalue = ((writevalue<<4)|encodemapping.get(towrite[1]))&bitmask;
			    }
			    if(!printComma) {
				printComma = true;
			    } else {
				out.print(',');
			    }
			    if(printReturn) {
				out.println();
				printReturn = false;
			    }
			    if(!isCompressed && ('\n' == buf[j])) {
				out.print("\'\\n\'");
				printReturn = true;
			    } else {
				out.print(writevalue);
				if(isCompressed) {
				    returncount--;
				    if(returncount==0) {
					printReturn=true;
					returncount = 10;
				    }
				}
			    }
			}
		    }
		    out.println("};");
		    out.println();
		    gzipInputStream.close();
		    if(isCompressed) {
			// print out the decoding array
			String decodename = arrayname.concat("_decodeTbl");
			out.println("unsigned char " + decodename + "[] = {");
			printComma = false;
			for(int j = 0; j < encodeVec.size(); j++) {
			    byte b = encodeVec.elementAt(j).byteValue();
			    if(!printComma) {
				printComma = true;
			    } else {
				out.print(',');
			    }
			    if('\n' == b) {
				out.print("\'\\n\'");
				printReturn = true;
			    } else {
				out.print(b);
			    }
			}
			out.println("};");
			out.println();
		    }
		}
		
		out.println();
		out.println("unsigned char * inputFileArrays[]= {");
		for(int i = 0; i < sourcefiles.size(); i++) {
		    String arrayname = sourcefiles.elementAt(i).replaceAll("\\.", "_");
		    
		    if(i > 0) {
			out.println(',');
		    }
		    out.print("&"+arrayname+"[0]");
		}
		out.println();
		out.println("};");
		out.println();
		
		out.println("unsigned char * inputFileArrays_decodeTbl[]= {");
		for(int i = 0; i < sourcefiles.size(); i++) {
		    String arrayname = sourcefiles.elementAt(i).replaceAll("\\.", "_");
		    
		    if(i > 0) {
			out.println(',');
		    }
		    if(compressVec.elementAt(i).booleanValue()) {
			out.print("&"+arrayname.concat("_decodeTbl")+"[0]");
		    } else {
			out.print("0");
		    }
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
		out.println("  return -1;");
		out.println("}");
		out.println();
		
		out.println("unsigned char nextValue='\\0';");
		out.println("bool holdVal=false;");
		out.println();

		out.println("void initVal() {");
		out.println("  nextValue='\\0';");
		out.println("  holdVal=false;");
		out.println("}");
		out.println();

		out.println("unsigned char peek(unsigned char * filearray, int * pos, int * isHighbits, bool isCompressed) {");
		out.println("  if(!isCompressed) {");
		out.println("    return filearray[*pos];");
		out.println("  } else {");
		out.println("    unsigned char value=0;");
		out.println("    if(!holdVal) {");
		out.println("      nextValue=filearray[*pos];");
		out.println("    }");
		out.println("    if(1==*isHighbits) {");
		out.println("      value=((nextValue&0x00F0)>>4)&0x000F;");
		out.println("      holdVal=true;");
		out.println("      *isHighbits=0;");
		out.println("    } else {");
		out.println("      value=nextValue&0x000F;");
		out.println("      holdVal=false;");
		out.println("      *isHighbits=1;");
		out.println("    }");
		out.println("    return value;");
		out.println("  }");
		out.println("}");
		out.println();

		out.println("void next(int * pos, bool isCompressed) {");
		out.println("  if(!isCompressed||!holdVal) {");
		out.println("    *pos = *pos+1;");
		out.println(" }");
		out.println("}");
		out.println();
	        
		out.println("int nextInt(int fd, int * pos, int * isHighbits) {");
		out.println("  int i = 0;");
		out.println("  unsigned char * filearray = inputFileArrays[fd];");
		out.println("  bool isCompressed = false;");
		out.println("  if(inputFileArrays_decodeTbl[fd]!=0) {");
		out.println("  	isCompressed = true;");
		out.println("  }");
		out.println("  initVal();");
		out.println();
		out.println("  unsigned char b='\\0';");
		out.println("  while(true) {");
		out.println("    b=peek(filearray, pos, isHighbits, isCompressed); ");
		out.println("    if((b==' ')||(b=='\\n')){");
		out.println("      next(pos, isCompressed);");
		out.println("    } else {");
		out.println("      break;");
		out.println("    }");
		out.println("  }");
		out.println();
		out.println("  unsigned int value = 0;");
		out.println("  bool isNeg=false;");
	        out.println("  unsigned int radix = 10;");
	        out.println();
	        out.println("  if (b=='-') {");
	        out.println("    isNeg=true;");
	        out.println("    next(pos, isCompressed);");
	        out.println("    b=peek(filearray, pos, isHighbits, isCompressed);");
	        out.println("  }");
	        out.println("  bool cont=true;");
	        out.println("  do {");
	        out.println("    unsigned int val;");
	        out.println("    if (b>='0'&&b<='9')");
	        out.println("      val=b-'0';");
	        out.println("    else if (b>='a'&&b<='z')");
	        out.println("      val=10+b-'a';");
	        out.println("    else if (b>='A'&&b<='Z')");
	        out.println("      val=10+b-'A';");
	        out.println("    else {");
	        out.println("      cont=false;");
	        out.println("    }");
	        out.println("    if (cont) {");
	        out.println("      if (val>=radix)");
	        out.println("        printf(\"Error in nextInt(): val >= radix\");");
	        out.println("      value=value*radix+val;");
	        out.println("      next(pos, isCompressed);");
	        out.println("      b=peek(filearray, pos, isHighbits, isCompressed);");
	        out.println("    }");
	        out.println("  }while(cont);");
	        out.println("  if (isNeg)");
	        out.println("  	value=-value;");
	        out.println();
	        out.println("  return value;");
	        out.println("}");
	        out.println();
		
	        out.println("double nextDouble(int fd, int * pos, int * isHighbits) {");
	        out.println("  int i = 0;");
	        out.println("  unsigned char * filearray = inputFileArrays[fd];");
	        out.println("  bool isCompressed = false;");
		out.println("  if(inputFileArrays_decodeTbl[fd]!=0) {");
		out.println("  	isCompressed = true;");
		out.println("  }");
		out.println("  initVal();");
		out.println();
		out.println("  unsigned char b='\\0';");
		out.println("  while(true) {");
		out.println("    b=peek(filearray, pos, isHighbits, isCompressed); ");
		out.println("    if((b==' ')||(b=='\\n')){");
		out.println("      next(pos, isCompressed);");
		out.println("    } else {");
		out.println("      break;");
		out.println("    }");
		out.println("  }");
		out.println();
	        out.println("  double result=0.0;");
	        out.println("  unsigned int value=0;");
	        out.println("  unsigned int maxvalue=((unsigned int)(1<<32)-1)/10;");
	        out.println("  bool isNeg=false;");
	        out.println("  bool isDiv=false;");
	        out.println("  unsigned int radix = 10;");
	        out.println();
	        out.println("  if (b=='-') {");
	        out.println("    isNeg=true;");
	        out.println("    next(pos, isCompressed);");
	        out.println("    b=peek(filearray, pos, isHighbits, isCompressed); ");
	        out.println("  }");
	        out.println("  bool cont=true;");
	        out.println("  bool exp=false;");
	        out.println("  bool decimal=false;");
	        out.println("  bool compute=true;");
	        out.println("  bool omit=false;");
	        out.println("  unsigned int dcount=0;");
	        out.println("  // compute the base");
	        out.println("  do {");
	        out.println("    unsigned int val;");
	        out.println("    if (b>='0'&&b<='9') {");
	        out.println("      if (!omit) {");
	        out.println("        val=b-'0';");
	        out.println("        if(decimal) {");
	        out.println("          dcount++;");
	        out.println("        }");
	        out.println("      }");
	        out.println("    } else if (b=='.') {");
	        out.println("      decimal=true;");
	        out.println("      compute=false;");
	        out.println("    } else if (b=='E'||b=='e') {");
	        out.println("      exp=true;");
	        out.println("      cont=false;");
	        out.println("      next(pos, isCompressed);");
	        out.println("    } else {");
	        out.println("      cont=false;");
	        out.println("    }");
	        out.println("    if (cont) {");
	        out.println("      if (val>=radix)");
	        out.println("        printf(\"Error in nextDouble(): val >= radix\");");
	        out.println("      if(compute) {");
	        out.println("        if(value<maxvalue) {");
	        out.println("          value=value*radix+val;");
	        out.println("        } else {");
	        out.println("          omit=true;");
	        out.println("          compute=false;");
	        out.println("          dcount--;");
	        out.println("        }");
	        out.println("      } else if(!omit) {");
	        out.println("        compute=true;");
	        out.println("      }");
	        out.println("      next(pos, isCompressed);");
	        out.println("      b=peek(filearray, pos, isHighbits, isCompressed); ");
	        out.println("    }");
	        out.println("  }while(cont);");
	        out.println("  if(exp) {");
	        out.println("    // compute the power index");
	        out.println("    cont=true;");
	        out.println("    unsigned int n=0;");
	        out.println("    b=peek(filearray, pos, isHighbits, isCompressed); ");
	        out.println("    if (b=='-') {");
	        out.println("      isDiv=true;");
	        out.println("      next(pos, isCompressed);");
	        out.println("    } else if (b=='+') {");
	        out.println("      next(pos, isCompressed);");
	        out.println("      b=peek(filearray, pos, isHighbits, isCompressed); ");
	        out.println("    }");
	        out.println("    do {");
	        out.println("      unsigned int val;");
	        out.println("      if (b>='0'&&b<='9') {");
	        out.println("        val=b-'0';");
	        out.println("      } else {");
	        out.println("        cont=false;");
	        out.println("      }");
	        out.println("      if (cont) {");
	        out.println("        if (val>=radix)");
	        out.println("          printf(\"Error in nextDouble(): val >= radix\");");
	        out.println("        n=n*10+val;");
	        out.println("        next(pos, isCompressed);");
	        out.println("        b=peek(filearray, pos, isHighbits, isCompressed); ");
	        out.println("      }");
	        out.println("    }while(cont);");
	        out.println("    if(isDiv) {");
	        out.println("      result = (double)value/pow(radix, n+dcount);");
	        out.println("    } else {");
	        out.println("      if(n>dcount) {");
	        out.println("        result = (double)value*pow(radix, n-dcount);");
	        out.println("      } else if(n<dcount) {");
	        out.println("        result = (double)value/pow(radix, dcount-n);");
	        out.println("      } else {");
	        out.println("        result = (double)value;");
	        out.println("      }");
	        out.println("    }");
	        out.println("  } else {");
	        out.println("    result = (double)value/pow(radix, dcount);");
	        out.println("  }");
	        out.println("  if (isNeg)");
	        out.println("    result=-result;");
	        out.println();
	        out.println("return result;");
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
