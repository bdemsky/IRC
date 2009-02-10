// -*- java -*-
//    FILE: Nominus.java
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

public class Nominus {
    public static void main(String args []) {
	GetPot   cl = new GetPot(args, "Nominus");

	// if( cl.size() == 1 || cl.search("--help", "-h") ) print_help();

	// print out all argument that do not start with a minus
	String  nm = cl.next_nominus();     
	while( nm != "" ) {
	    System.out.println("[" + nm + "]");
	    nm = cl.next_nominus();     
	} 
    
	System.out.println();

	// now get the whole vector at once
	String[]   files = cl.nominus_vector();
	for(int i=0; i<files.length ; i++)
	    System.out.println("<" + files[i] + ">");
				
    }

    static void print_help() {
	System.out.println();
	System.out.println( "Example to use nominus arguments:");
	System.out.println( "USAGE:");
	System.out.println( "--help, -h  get some help about this program.");
	System.out.println();
	System.out.println( "any option that does not start with '-'");
	System.out.println( "will be printed on screen twice.");
	System.out.println();
	System.exit(0);
    }
}


