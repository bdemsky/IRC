// -*- java -*-
//    FILE: Get.java Version 0.9.8
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

public class Get {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Get");

	// GetPot Java can treat upto 6 strings, then it needs a String[]
	if( cl.search("--help", "-h", "--hilfe", "--sos", "--about", "--what-is") ) {
	    System.out.println("call " +  cl.getitem(0) + " with four arguments.");
	    System.exit(0);
	}
	if( cl.search(new String[]{"--stop", "--quit", "--do-nothing", 
				   "--let-it-be", "-q", "-s", "--never-mind",
				   "--achtung-anhalten", "--arrete", "--fait-rien",
				   "--glonde-rien"}) )
	    System.exit(0);

	int     A = cl.get(4, 256);    // integer version of get()
	double  B = cl.get(1, 3.14);   // double version of get()
	String  C = cl.get(2, "You");  // const char* version of get()
	
	System.out.println("A (argument no 4) = " + A);
	System.out.println("B (argument no 1) = " + B);
	System.out.println("C (argument no 2) = " + C);
    }
}


