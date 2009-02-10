// -*- java -*-
//    FILE: Variables.java Version 0.9.8
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

public class Variables {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Variables");
	if( cl.size() == 1 || cl.search("--help", "-h") ) print_help("java Variables");

	if( cl.search("--internal-information", "-i") ) {
	    cl.print(); System.exit(0);
	}
	// (2) some variables of each type
	double A_Number   = cl.call("float", 0.);
	double An_Integer = cl.call("integer", 0);
	String A_String   = cl.call("string", "default");

	double Element1 = cl.call("vector", 0., 0);
	String Element2 = cl.call("vector", "default", 1);
	int    Element3 = cl.call("vector", 0, 2);

	System.out.println("Specified Parameters:");
	System.out.println("float   = " + A_Number);
	System.out.println("integer = " + An_Integer);
	System.out.println("string  = " + A_String);
	System.out.println();
	System.out.println("Vector elements:");
	System.out.println("Element 0 (double) = " + Element1);
	System.out.println("Element 1 (string) = " + Element2);
	System.out.println("Element 2 (int)    = " + Element3);
    }
    
    static void print_help(String Application) {
	System.out.println();
	System.out.println("Example how to use GetPot to parse variables on the command line.");
	System.out.println(); 
	System.out.println("USAGE:");
	System.out.println("--help, -h, --hilfe, --sos");
	System.out.println("        get some help about this program.");
	System.out.println("--internal-information, -i");
	System.out.println("        show contents of database that was created by parser.");
	System.out.println();       
	System.out.println("type a command line like the following:");
	System.out.println(Application + " string=otto.von.bismarck float=3.14 \\");
	System.out.println("        integer=41 vector='1.26 something 0x40'");
	System.out.println();
	System.out.println("the variables that can be specified are:");
	System.out.println("       float    a double variable");
	System.out.println("       integer  an integer variable");
	System.out.println("       string   a string variable");
	System.out.println("       vector   a mixed type vector:");
	System.out.println("                element 0 => double ");
	System.out.println("                element 1 => string");
	System.out.println("                element 2 => int");
	System.out.println();
    }
}    
