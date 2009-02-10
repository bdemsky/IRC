// -*- java -*-
//    FILE: DirectFollow.java
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

public class DirectFollow {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "DirectFollow");
	
	if( cl.size() == 1 || cl.search("--help", "-h") ) print_help("DirectFollow");

	// Specify, that in case the cursor reaches the end of argument list,
	// it is not automatically reset to the start. This way the search
	// functions do not wrap around. Instead, they notify an 'not fount'
	// in case the option was not in between 'cursor' and the argv.end().
	cl.reset_cursor();
	cl.disable_loop();
	
	// check out 'String' versions
	String  User  = cl.direct_follow("You",   "-U");  
	String  User2 = cl.direct_follow("Karl",  "-U"); 
	String  User3 = cl.direct_follow("Heinz", "-U");
	
	// check out 'double' versions
	cl.reset_cursor(); 
	double  Value  = cl.direct_follow(3.14, "-V"); 
	double  Value2 = cl.direct_follow(9.81, "-V"); 
	double  Value3 = cl.direct_follow(1.62, "-V"); 
	
	// check out 'integer' versions
	cl.reset_cursor(); 
	int  Number  = cl.direct_follow(12, "-NUM");
	int  Number2 = cl.direct_follow(43, "-NUM");
	int  Number3 = cl.direct_follow(64, "-NUM");
	
	something(User, User2, User3, Value, Value2, Value3, Number, Number2, Number3);
    }
    
    static void something(String User, String User2, String User3,
			  double Value, double Value2, double Value3,
			  int Number, int Number2, int Number3)	{
	    System.out.println("Users   = " + User  + ", " + User2 + ", " + User3);
	    System.out.println("Values  = " + Value + ", " + Value2 + ", " + Value3);
	    System.out.println("Numbers = " + Number + ", " + Number2 + ", " + Number3);
    }

    static void print_help(String Application) {
	System.out.println();
	System.out.println("Example to use direct_follow()-functions:");
	System.out.println("USAGE:");
	System.out.println("--help, -h  get some help about this program.");
	System.out.println();
	System.out.println("-Ustring    specify user name as string");
	System.out.println("-Vx         specify a value given as x");
	System.out.println("-NUMx       specify number given as x");
	System.out.println();
	System.out.println("a total amount of three of each is expected.");
	System.out.println();
	System.exit(0);
    }
}
