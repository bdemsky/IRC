// -*- java -*-
//    FILE: Ufo.java Version 0.9
//    (C) 2001-2002  Frank R. Schaefer
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


public class Ufo {
public static void main(String args []) {
    GetPot   cl = new GetPot(args, "Ufo");
    String[]   ufos = {};

    if( cl.search("-h", "--help") ) {
	print_help();
	System.exit(0);
    }
    else if( cl.search("--arguments") ) {
	// (*) unidentified flying arguments ---------------------------------------------
	ufos = cl.unidentified_arguments(new String[]{"--arguments", "-h", "--help", "yes", "no"});
	print("Unidentified Arguments (other than '--arguments', '-h', '--help', 'yes', 'no'):\n");
    }
    else if( cl.search("--options") ) {
	// (*) unidentified flying options (starting with '-' or '--') -------------------
	ufos = cl.unidentified_options(new String[]{"--options",  "-h", "--help",
						    "yes(ignored anyway)", "no(ignored anyway)"});
	print("Unidentified Options (different from '--options',  '-h', '--help'):\n");
    }
    else if( cl.search("--flags") ) {
	// (*) unidentified flying flags -------------------------------------------------

	// -- flags in the first argument
	String ufo_s = cl.unidentified_flags("xzfjct", 1);
	print("-- Unaccepted flags in argument 1:\n");
	for(int i=0; i< ufo_s.length() ; i++)
	    print("      '" + ufo_s.charAt(i) + "'\n");

	print("\n   Accepted flags: 'x' 'z' 'f' 'j' 'c' 't'\n\n");

	// -- flags in arguments starting with a single '-'
	ufo_s = cl.unidentified_flags("ltrm");
	print("-- Unaccepted flags in options (argument with one '-'):\n");
	for(int k=0; k< ufo_s.length() ; k++)
	    print("      '" + ufo_s.charAt(k) + "'\n");

	print("\n   Accepted flags in options: 'l' 't' 'r' 'm'\n");
    }			
    else if( cl.search("--variables") ) {
	// (*) unidentified flying variables ---------------------------------------------
	ufos = cl.unidentified_variables(new String[]{"x", "y", "z", "length", "height"});
	
	print("Unidentified Variables (other than 'x', 'y', 'z', 'length', 'height'):\n");
    }
    else if( cl.search("--sections") ) {
	// (*) unidentified flying sections ----------------------------------------------
	GetPot  ifile = new GetPot("example.pot");
	ufos = ifile.unidentified_sections(new String[]{
	    "vehicle/", 
	    "vehicle/tires/", 
	    "vehicle/chassis/",
	    "vehicle/chassis/doors/",
	    "group/",
	    "other/",
	    "user-variables/",
	    "pseudo-function-calls/"});
	
	print("Unidentified sections in file 'example.pot':\n");
	if( ufos.length == 0 )
	    print("    (none) add [..] section labels in file 'example.pot'.\n");
    }
    else if( cl.search("--nominuses") ) {
	// (*) unidentified flying options (starting with '-' or '--') -------------------
	String  tmp[] = new String[2];
	// -- read two filenames for demonstration purposes
	tmp[0] = cl.follow("default-in.znf", "-i");
	tmp[1] = cl.follow("default-out.znf", "-o");
	
	// -- get any other nominuses not used until now
	ufos = cl.unidentified_nominuses(tmp);

	print("Unused Nominus Arguments (other than arguments after -i and -o):\n");
    }
    else  {
	print_help();
	System.exit(0);
    }

    // (*) print out unidentified flying objects
    for(int i=0; i < ufos.length ; i++)
	print("     " + ufos[i] + "\n");
}
    

    static void print_help() {
	String msg = 
	    "USAGE:  java Ufo [--help] [--arguments] [--options] [--flags] [--sections] \n" +
	    "                 [--variables] [--nominuses [-i file] [-o filename]]\n" +
	    "                 ... any arguments\n" +
	    "\n" +
	    "PURPOSE: \n" +
	    "        Testing  the GetPot's  ability to detect  unknown flying objects\n" +
	    "         (i.e. un-recognized command line arguments). The type of flying\n" +
	    "        object you  want to check  for is specified  through  one of the\n" +
	    "        following options:\n" +
	    "\n" +
	    "        --arguments\n" +
	    "             test all arguments against an internal list of possible arguments.\n" +
	    "\n" +
	    "        --options\n" +
	    "             test all options (arguments starting with at least one '-') against\n" +
	    "             an internal list of options.\n" +
	    "\n" +
	    "        --flags\n" +
	    "             test first argument and all options against internal list of flags\n" +
	    "             (i.e. letters in arguments that act as switches)\n" +
	    "\n" +
	    "        --sections\n" +
	    "             reads the input file 'example.pot' for sections that are\n" +
	    "              unidentified. add some sections yourself to test this feature.\n" +
	    "\n" +
	    "        --variables\n" +
	    "             tests all variables specified on the command line against a list\n" +
	    "             of specified variables.\n" +
	    "\n" +
	    "        --nominuses\n" +
	    "             check for unrecognized nominuses. filenames after -i and -o are\n" +
	    "             recognized.\n" +
	    "\n" +
	    "        Once you specified your mode add any number of command line\n" +
	    "        arguments and watch the output.\n" +
	    "\n" +     
	    "EXAMPLES:\n" +
	    "        java Ufo yes no -i source.cpp --arguments\n" +
	    "\n" +
	    "        java Ufo -i source.cpp --force n=13 -xzt --options\n" +
	    "\n" +
	    "        java Ufo zfd if=source.cpp force n=13 -l -zrt -olp --flags\n" +
	    "\n" +
	    "        java Ufo zfd x=12 y=234 loength=21 height=21 n=2 --variables\n" +
	    "\n" +
	    "        java Ufo --sections # (edit file section labels in example.pot)\n" +
	    "\n" +
	    "        java Ufo n=21 -i in.dat -o out.dat /etc/fstab force --nominuses \n" +
	    "\n" +
	    "AUTHOR: (C) 2002 Frank R. Schaefer\n";
	System.out.print(msg);
	    
    }

    static void print(String Str) {
	System.out.print(Str);
    }
	
}
