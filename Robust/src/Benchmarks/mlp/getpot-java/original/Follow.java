// -*- java -*-
//    FILE: Follow.java
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

public class Follow {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Follow");
	
	// to function 'cl.search(..)' 
	if( cl.search("--help", "-h", "--hilfe") )
	    print_help();

	// read arguments one by one on the command line
	//  (lazy command line parsing)
	double Alpha = cl.follow(0.,  "--alpha");   // [rad]
	int    Beta  = cl.follow(256, "--beta"); // [1/s]
	cl.init_multiple_occurrence();
	String User  = cl.follow("You", "--user");      
	int    No    = cl.next(0x42); 
	String User2 = cl.follow("You Two", "--user"); // second user specified ?
	int    No2   = cl.next(0x43); 
	cl.enable_loop(); 
	double XSz   = cl.follow(0., "--size", "--sz", "-s"); // [cm]
	double YSz   = cl.next(0.);                           // [cm]
	
	System.out.println();
	System.out.println("Alpha = " + Alpha);
	System.out.println("Beta  = " + Beta );
	System.out.println("Names           = " + User + " and " + User2);
	System.out.println("Special numbers = " + No + " and " + No2);
	System.out.println("x-size, y-size  = " + XSz + ", " + YSz);
	System.out.println();
    }

    static void print_help() {
	System.out.println();
	System.out.println("Example to use follow()-functions:");
	System.out.println("USAGE:");
	System.out.println("--help, -h");
	System.out.println("       get some help about this program.");
	System.out.println("--alpha number");
	System.out.println("       specify the value of alpha given as number.");
	System.out.println("--beta number");
	System.out.println("       specify the value of beta given as number.");
	System.out.println("--user string number");
	System.out.println("       specify user name as string and id as number");
	System.out.println("       (default = 'You' and '0x42').");
	System.out.println("       multiple users possible. ");
	System.out.println("--size, --sz, -s number1 number2");
	System.out.println("       specify x and y sizes");
	System.out.println();
	System.exit(0);
    }
}










    
