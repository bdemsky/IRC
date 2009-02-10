// -*- java -*-
//    FILE: Filter.java
//    (C) 2002  Frank R. Schaefer
//
//    This library is free software; you can redistribute it and/or
//    modify it under the terms of the GNU Lesser General Public
//    License as published by the Free Software Foundation; either
//    version 2.1 of the License, or (at your option) any later version.
//
//    This library is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//    Lesser General Public License for more details.
//
//    You should have received a copy of the GNU Lesser General Public
//    License along with this library; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
///////////////////////////////////////////////////////////////////////////////////
import GetPot.*;
import java.lang.String;

public class Filter {

    static double Sqr(double x) { return x*x; } 


    public static void main(String args[]) {
	GetPot   cl = new GetPot(args, "Filter");
	GetPot   ifpot = new GetPot("example.pot");
               
	// (1) search for multiple options with the same meaning
	if( cl.search("--help", "-h", "--hilfe", "--sos") ) {
	    String Msg = "Example program treating the prefix filtering.\n\n" +
		"   Using the function .set_prefix(section) only arguments, options \n" +
		"   variables are considered in the given 'section'\n\n" +
		"--help, -h, --hilfe, --sos \n" +
		"       this page.\n" +
		"--nice \n" +
		"       demonstrates how pseudo function calls can be accomplished.\n\n" +
		"please refer to the file 'example.pot' as input file.\n";
	    System.out.println(Msg);
	    System.exit(0);
	}

	//  -- note that the prefix is not considered as a flag
	//  -- the index in 'argument contains' indicates the position
	//     of the argument inside the namespace given by prefix
	ifpot.set_prefix("group/");

	System.out.println(" -- flags in options / arguments");
	boolean first_f  = ifpot.argument_contains(1, "xX");
	boolean second_f = ifpot.argument_contains(1, "cCkK");
	boolean abc_f    = ifpot.options_contain("abc");
	System.out.println("    Flags in first argument in [flags]\n");
	System.out.println("    x or X in arg 1       = " + first_f);
	System.out.println("    c, C, k or K in arg 1 = " + second_f);
	System.out.println("    a,b, or c in options  = " + abc_f);
	System.out.println("");
	System.out.println(" -- search(), next() and follow()");
	System.out.println("");
	System.out.println("    found \"--rudimental\" = " + ifpot.search("--rudimental"));

	int Tmp1 = ifpot.next(-1);
	int Tmp2 = ifpot.next(-1);
	System.out.println("    followed by " + Tmp1 + " " + Tmp2);

	String Tmp3 = ifpot.follow("nothing", "--rudimental");
	int    Tmp4 = ifpot.next(-1);
	System.out.println("    rudimental = " + Tmp3 + ", " + Tmp4 + "\n");

	//  -- variables
	System.out.println("");
	System.out.println(" -- variables in section [user-variables]");
	System.out.println("");
	ifpot.set_prefix("user-variables/");

	String variable_names[] = ifpot.get_variable_names();
	for(int i=0; i < variable_names.length ; i++) {
	    String name = variable_names[i];
	    System.out.print("    " + name + "   \t= ");
	    System.out.print(ifpot.call(name, 1e37, 0));
	    System.out.print("[" + ifpot.call(name, "[1]", 1) + "]\n");
	}
	System.out.println("");	

	//  -- pseudo function calls
	if( cl.search("--nice") ) {
	    System.out.println(" -- pseudo function call feature");
	    System.out.println("");
	    ifpot.set_prefix("pseudo-function-calls/");
	    ifpot.init_multiple_occurrence();
	    
	    ifpot.search("LE-DEBUT");
	    while( 1 + 1 == 2 ) {
		String Next = ifpot.next("(no-func)");
	    
		if( Next.compareTo("(no-func)") == 0 ) break;
		else if( Next.compareTo("rectangle") == 0) {
		    int size_x = ifpot.next(10);
		    int size_y = ifpot.next(10);
		    System.out.print("\n");
		    for(int y=0; y < size_y; y++) {
			for(int x=0; x < size_x; x++) {
			    System.out.print("*");
			}
			System.out.print("\n");
		    }
		}
		else if( Next.compareTo("circle") == 0) {
		    int radius = ifpot.next(4);
		    System.out.println("\n");
		    for(int y=0; y < radius*2 + 1; y++) {
			for(int x=0; x < radius*2 + 1; x++)			    
			    if(    Sqr(x-radius) + Sqr(y-radius) <= Sqr(radius)
				   && Sqr(x-radius) + Sqr(y-radius) >= Sqr(radius)/4. )
				System.out.print(".");
			    else 
				System.out.print(" ");
			System.out.print("\n");
		    }
		}
		else if( Next.compareTo("smiley") == 0 ) {
		    String Mood = ifpot.next("happy");
		    if( Mood.compareTo("sad") == 0 ) System.out.print(":( ");
		    else                             System.out.print(":) ");
		}
		else if( Next.compareTo("new-line") == 0 ) {
		    int No = ifpot.next(1);
		    for(int i=0; i<No ;i++)
			System.out.println("\n");
		}
	    }
	}   
	else {
	    System.out.println("(use the --nice command line flag for graphical output)");
	}
	System.out.println("");
    }
}
	   


