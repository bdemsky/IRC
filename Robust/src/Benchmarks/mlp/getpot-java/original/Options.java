// -*- java -*-
//    FILE: Options.java Version 0.9.8
//    (C) 2001  Frank R. Schaefer
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
//////////////////////////////////////////////////////////////////////////////////////
import GetPot.*;
import java.lang.String;

public class Options {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Options");

	// (1) search for a single option
	// -------------------------------
	//     check if the '--do-nothing' flag is set and exit if yes
	if( cl.search("--do-nothing") ) System.exit(0);

	// (2) search for multiple options with the same meaning
	// GetPot Java can treat upto 6 strings, then it needs a String[]
	if( cl.search("--help", "-h", "--hilfe", "--sos", "--about", "--what-is") )
	    print_help();

	//     does the user want us to be nice ... ?
	boolean  be_nice_f = cl.search("--nice");
	

	if( cl.search("--beep", "--piepse", "--bip", "--senal-acustica", "-b") )
	    System.out.println("(imagine a beep - the '\\a' is a invalid escape character in Java)");
	
	System.out.println( "Program terminated.");
	if( be_nice_f == true )
	    System.out.println( "Have a nice day.");
    }


    static void print_help() {
	System.out.println();
	System.out.println("Example to use search()-functions:\n\n");
	System.out.println(); 
	System.out.println("USAGE:");
	System.out.println("--do-nothing   quit without doing anything.");
	System.out.println("--nice         write a nice phrase on the terminal.");
	System.out.println("--help, -h, --hilfe, --sos");
	System.out.println("               get some help about this program.");
	System.out.println("--beep, -b, --piepse, --bip, --senal-acustica");
	System.out.println("               get your system to make a beep.");
	System.out.println( );
	System.exit(0);
    }
}    



