// -*- java -*-
//    FILE: Next.java Version 0.9.8
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

public class Next {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Next");

	// all the following pain, only to pass a string array
	// to function 'cl.search(..)' 
	if( cl.search("--help", "-h", "--hilfe") ) print_help();
        // read arguments one by one on the command line
	//  (lazy command line parsing)
	cl.reset_cursor();
	double   A = cl.next(0.);    // [rad]
	int      B = cl.next(256);   // [1/s]
	String   User = cl.next("You");
	int      No   = cl.next(0x42); 

	System.out.println();
	System.out.println("A = " + A);
	System.out.println("B = " + B);
	System.out.println("Name           = " + User);
	System.out.println("Special number = " + No);
	System.out.println();
    }

    static void print_help() {
	System.out.println();
	System.out.println("Example to use next()-functions:\n");
	System.out.println("USAGE:");
	System.out.println("    $1  (double) Value of A");
	System.out.println("    $2  (integer) Value of B");
	System.out.println("    $3  (string) Name to be printed");
	System.out.println("    $4  (integer) Some number to be printed");
	System.out.println();
	System.exit(0);
    }
}

