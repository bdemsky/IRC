// -*- java -*-
//    FILE: Flags.java Version 0.9.8
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

public class Flags {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Flags");
	if( cl.size() == 1 || cl.search("--help", "-h") ) print_help();

	// does first argument contain 'x', 'X', 'c', 'C', 'k', or  'K' ?
	boolean  first_f = cl.argument_contains(1, "xX");
	boolean  second_f  = cl.argument_contains(1, "cCkK");
	
	// is there any option starting with '-' containing 'a', 'b', or 'c' ?
	boolean  abc_f = cl.options_contain("abc");
	
	System.out.println("first flag  = " + first_f);
	System.out.println("second flag = " + second_f);
	System.out.println("a, b, or c found = " + abc_f );
    }


    static void print_help() {
	    System.out.println();
	    System.out.println("Example using flags:");
	    System.out.println("USAGE:");
	    System.out.println("--help, -h  get some help about this program.");
	    System.out.println();
	    System.out.println("The first argument will be checked if it contains 'x' or 'X'.");
	    System.out.println("If so the first flag will be set. The second flag will be set if");
	    System.out.println("the first argument contains a 'c', 'C', 'k', or a 'K'.");
	    System.out.println();
	    System.out.println("The 'abc' flag is set when any argument starting with '-' contains an");
	    System.out.println("'a', 'b' or a 'c'.");
	    System.out.println();
	    System.exit(0);
	}
}



