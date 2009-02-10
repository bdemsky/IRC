// -*- java -*-
//    FILE: Expand.java
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
//////////////////////////////////////////////////////////////////////////////////

import GetPot.*;
import java.lang.String;

public class Expand {
    public static void main(String args []) {
	GetPot   cl    = new GetPot(args, "Get");
	GetPot   ifpot = new GetPot("expand.pot");

	if( cl.search("--internal-information", "-i") ) {
	    ifpot.print();
	    System.exit(0);
	}

	// (*) help display
	if( cl.search("--help", "-h", "--hilfe", "--sos") || cl.search("--ok") == false ) {
	    String msg = "Example how to use GetPot to parse input files.\n\n" +
		"USAGE:\n" +
		"--ok\n" +
		"        run the input file parsing.\n" +
		"--help, -h, --hilfe, --sos\n" +
		"        get some help about this program.\n\n" +
		"--internal-information, -i\n" +
		"        show contents of database that was created by file parser.\n" +
		"--infile string\n" +
		"        input file name (default: example.pot)";
	    my_print(msg);
	    System.exit(0);
	}

	// (*) example usage
	my_print("[1.1]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "nobody"));

	ifpot.set_prefix("1.2/");
	my_print("[1.2]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
    
	ifpot.set_prefix("1.3/");
	my_print("[1.3]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
    
	ifpot.set_prefix("1.4/");
	my_print("[1.4]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
 
	ifpot.set_prefix("2.1/");
	my_print("[2.1]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
	my_print("Information 2: ", ifpot.call("info2", "(nothing)"));

	ifpot.set_prefix("2.2/");
	my_print("[2.2]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));

	ifpot.set_prefix("2.3/");
	my_print("[2.3]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
	my_print("Information 2: ", ifpot.call("info2", "(nothing)"));

	ifpot.set_prefix("3.1/");
	my_print("[3]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
	my_print("Information 2: ", ifpot.call("info2", "(nothing)"));
	my_print("Information 3: ", ifpot.call("info3", "(nothing)"));
	my_print("Information 4: ", ifpot.call("info4", "(nothing)"));
	my_print("Information 5: ", ifpot.call("info5", "(nothing)"));
	my_print("Information 6: ", ifpot.call("info6", "(nothing)"));
	my_print("Information 7: ", ifpot.call("info7", "(nothing)"));

	ifpot.set_prefix("3.2/");
	my_print("[3.2]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));

	ifpot.set_prefix("3.3/");
	my_print("[3.3]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
	my_print("Information 2: ", ifpot.call("info2", "(nothing)"));
	my_print("Information 3: ", ifpot.call("info3", "(nothing)"));
	my_print("Information 4: ", ifpot.call("info4", "(nothing)"));
	my_print("Information 5: ", ifpot.call("info5", "(nothing)"));
	my_print("Information 6: ", ifpot.call("info6", "(nothing)"));
	my_print("Information 7: ", ifpot.call("info7", "(nothing)"));

	ifpot.set_prefix("3.4/");
	my_print("[3.4]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
	my_print("Information 2: ", ifpot.call("info2", "(nothing)"));

	ifpot.set_prefix("3.5/");
	my_print("[3.5]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
	my_print("Information 2: ", ifpot.call("info2", "(nothing)"));
	my_print("Information 3: ", ifpot.call("info3", "(nothing)"));
	my_print("Information 4: ", ifpot.call("info4", "(nothing)"));
	my_print("Information 5: ", ifpot.call("info5", "(nothing)"));
	my_print("Information 6: ", ifpot.call("info6", "(nothing)"));
	my_print("Information 7: ", ifpot.call("info7", "(nothing)"));

	ifpot.set_prefix("3.6/");
	my_print("[3.6]--------------------------------------------------------------------------");
	my_print("Information 0: ", ifpot.call("info0", "(nothing)"));
	my_print("Information 1: ", ifpot.call("info1", "(nothing)"));
    }

    static void my_print(String Str) {
	System.out.println(Str);
    }

    static void my_print(String Str1, String Str2) {
	System.out.println(Str1 + Str2);
    }    
}
