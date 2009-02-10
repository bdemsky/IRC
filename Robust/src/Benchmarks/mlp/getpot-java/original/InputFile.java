// -*- java -*-
//    FILE: InputFile.java Version 0.9.8
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

public class InputFile {
    public static void main(String args []) {
 	GetPot   cl = new GetPot(args, "InputFile");
 	GetPot   ifile = new GetPot("example.pot");
	
   	if( cl.size() == 1 || cl.search("--help", "-h") ) 
  	{ print_help();	System.exit(0); }

  	if( cl.search("--internal-information", "-i") ) 
  	{ ifile.print(); System.exit(0); }

	// (2) playing with sections
	System.out.println("webpage       = " + ifile.call("webpage", "nothing.somewhere.idn"));
	System.out.println("user          = " + ifile.call("user", "nobody"));
	System.out.println("dos-file      = " + ifile.call("dos-file", "nobody"));
	System.out.println("latex-formula = " + ifile.call("latex-formula", "nobody"));
	System.out.println("no. clicks    = " + ifile.call("clicks", 0));
	System.out.println("acceleration  = " + ifile.call("acceleration", 3.14));
    
	System.out.println("vehicle/wheel-base = " + ifile.call("vehicle/wheel-base",2.66));
	System.out.print("vehicle/initial-xyz = ");
        // first element of vector
	System.out.print(ifile.call("vehicle/initial-xyz", 0., 0) + "\t");  
        // second element of vector 
	System.out.print(ifile.call("vehicle/initial-xyz", 0., 1) + "\t");  
        // third element of vector
	System.out.print(ifile.call("vehicle/initial-xyz", 0., 2) + "\n");  

	System.out.println("vehicle/tires/B = " + ifile.call("vehicle/tires/B",777.7));
	System.out.println("              C = " + ifile.call("vehicle/tires/C", 777.7));
	System.out.println("              E = " + ifile.call("vehicle/tires/E", 777.7));
	System.out.println("              D = " + ifile.call("vehicle/tires/D", 777.7));

	System.out.println("vehicle/chassis/Roh = " + ifile.call("vehicle/chassis/Roh",777.7));
	System.out.println("                S   = " + ifile.call("vehicle/chassis/S",  777.7));
	System.out.println("                Cd  = " + ifile.call("vehicle/chassis/Cd", 777.7));

	System.out.println("vehicle/chassis/doors/number = " + ifile.call("vehicle/chassis/doors/number",2));
	System.out.println("                      locks  = " + ifile.call("vehicle/chassis/doors/locks","yes"));

        // (3) playing with things we do normally only with command line arguments
  	boolean n_f = ifile.search("--nonsense", "-n", "--unsinn", "--sans-sense");
	double XR = ifile.follow(3.14, "vehicle/-x");
	System.out.println("x-ratio    = " + XR);
	System.out.println("sound-mode = " + ifile.next("none"));    
	System.out.println("nonsense-flag = " + (n_f ? "activated" : "disabled"));
    }


    static void
    print_help() {
	System.out.println();
	System.out.println("Example how to use GetPot to parse input files.");
	System.out.println(); 
	System.out.println("USAGE:");
	System.out.println("--ok");
	System.out.println("        run the input file parsing." );
	System.out.println("--help, -h, --hilfe, --sos");
	System.out.println("        get some help about this program.");
	System.out.println();       
	System.out.println("--internal-information, -i");
	System.out.println("        show contents of database that was created by file parser.");
	System.out.println();
	System.out.println("--infile string");
	System.out.println("        input file name (default: example.pot)");        
	System.out.println();
    }
}    
