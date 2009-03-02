import GetPot.*;
import java.lang.String;

public class Example {

    public static void main(String args []) {

      String  User;
      String  User2;
      String  User3;



      
        String[] args1 = new String[7];
	args1[0] = "program";
	args1[1] = "-yox";
	args1[2] = "-nxj";
	args1[3] = "-dude";
	args1[4] = "-fernb";	
	args1[5] = "--ok";
	args1[6] = "gibberish";
      
	GetPot   cl = 
	  //disjoint gp1 
	  new GetPot(args1, "Flags");

	if( cl.size() == 1 || cl.search("--help", "-h") ) print_help();

	// does first argument contain 'x', 'X', 'c', 'C', 'k', or  'K' ?
	boolean  first_f = cl.argument_contains(1, "xX");
	boolean  second_f  = cl.argument_contains(1, "cCkK");
	
	// is there any option starting with '-' containing 'a', 'b', or 'c' ?
	boolean  abc_f = cl.options_contain("abc");
	
	System.printString("first flag  = " + first_f+"\n");
	System.printString("second flag = " + second_f+"\n");
	System.printString("a, b, or c found = " + abc_f+"\n");



	/*
        String[] args2 = new String[5];
	args2[0] = "program";
	args2[1] = "-V";
	args2[2] = "-9.81";
	args2[3] = "-U";
	args2[4] = "-Heinz";	

	cl = 
	  //disjoint gp2 
	  new GetPot(args2, "DirectFollow");
	
	if( cl.size() == 1 || cl.search("--help", "-h") ) print_help("DirectFollow");

	// Specify, that in case the cursor reaches the end of argument list,
	// it is not automatically reset to the start. This way the search
	// functions do not wrap around. Instead, they notify an 'not fount'
	// in case the option was not in between 'cursor' and the argv.end().
	cl.reset_cursor();
	cl.disable_loop();
	
	// check out 'String' versions
	User  = cl.direct_follow("You",   "-U");  
	User2 = cl.direct_follow("Karl",  "-U"); 
	User3 = cl.direct_follow("Heinz", "-U");
	
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
	*/




        String[] args3 = new String[5];
	args3[0] = "program";
	args3[1] = "-gjx";
	args3[2] = "--rudimental";
	args3[3] = "guy";
	args3[4] = "dude";	

	cl = 
	  //disjoint gp3 
	  new GetPot(args3, "Filter");

	GetPot   ifpot = 
	  //disjoint gp4 
	  new GetPot("example.pot");
               
	// (1) search for multiple options with the same meaning
	if( cl.search("--help", "-h", "--hilfe", "--sos") ) {
	    String Msg = "Example program treating the prefix filtering.\n\n" +
		"   Using the function .set_prefix(section) only arguments, options \n" +
		"   variables are considered in the given 'section'\n\n" +
		"--help, -h, --hilfe, --sos \n" +
		"       this page.\n" +
		"--nice \n" +
		"       demonstrates how pseudo function calls can be accomplished.\n\n" +
		"please refer to the file 'example.pot' as input file.\n";
	    System.printString(Msg);
	    System.exit(0);
	}

	//  -- note that the prefix is not considered as a flag
	//  -- the index in 'argument contains' indicates the position
	//     of the argument inside the namespace given by prefix
	ifpot.set_prefix("group/");

	System.printString(" -- flags in options / arguments"+"\n");
	first_f  = ifpot.argument_contains(1, "xX");
	second_f = ifpot.argument_contains(1, "cCkK");
	abc_f    = ifpot.options_contain("abc");
	System.printString("    Flags in first argument in [flags]\n"+"\n");
	System.printString("    x or X in arg 1       = " + first_f+"\n");
	System.printString("    c, C, k or K in arg 1 = " + second_f+"\n");
	System.printString("    a,b, or c in options  = " + abc_f+"\n");
	System.printString(""+"\n");
	System.printString(" -- search(), next() and follow()"+"\n");
	System.printString(""+"\n");
	System.printString("    found \"--rudimental\" = " + ifpot.search("--rudimental")+"\n");

	int Tmp1 = ifpot.next(-1);
	int Tmp2 = ifpot.next(-1);
	System.printString("    followed by " + Tmp1 + " " + Tmp2+"\n");

	String Tmp3 = ifpot.follow("nothing", "--rudimental");
	int    Tmp4 = ifpot.next(-1);
	System.printString("    rudimental = " + Tmp3 + ", " + Tmp4 + "\n"+"\n");

	//  -- variables
	System.printString(""+"\n");
	System.printString(" -- variables in section [user-variables]"+"\n");
	System.printString(""+"\n");
	ifpot.set_prefix("user-variables/");

	String variable_names[] = ifpot.get_variable_names();
	for(int i=0; i < variable_names.length ; i++) {
	    String name = variable_names[i];
	    System.printString("    " + name + "   \t= "+"\n");
	    System.printString(""+ifpot.call(name, 1e37, 0)+"\n");
	    System.printString("[" + ifpot.call(name, "[1]", 1) + "]\n"+"\n");
	}
	System.printString(""+"\n");	

	//  -- pseudo function calls
	if( cl.search("--nice") ) {
	    System.printString(" -- pseudo function call feature"+"\n");
	    System.printString(""+"\n");
	    ifpot.set_prefix("pseudo-function-calls/");
	    ifpot.init_multiple_occurrence();
	    
	    ifpot.search("LE-DEBUT");
	    while( 1 + 1 == 2 ) {
		String Next = ifpot.next("(no-func)");
	    
		if( Next.compareTo("(no-func)") == 0 ) break;
		else if( Next.compareTo("rectangle") == 0) {
		    int size_x = ifpot.next(10);
		    int size_y = ifpot.next(10);
		    System.printString("\n");
		    for(int y=0; y < size_y; y++) {
			for(int x=0; x < size_x; x++) {
			    System.printString("*");
			}
			System.printString("\n");
		    }
		}
		else if( Next.compareTo("circle") == 0) {
		    int radius = ifpot.next(4);
		    System.printString("\n");
		    for(int y=0; y < radius*2 + 1; y++) {
			for(int x=0; x < radius*2 + 1; x++)			    
			    if(    Sqr(x-radius) + Sqr(y-radius) <= Sqr(radius)
				   && Sqr(x-radius) + Sqr(y-radius) >= Sqr(radius)/4. )
				System.printString(".");
			    else 
				System.printString(" ");
			System.printString("\n");
		    }
		}
		else if( Next.compareTo("smiley") == 0 ) {
		    String Mood = ifpot.next("happy");
		    if( Mood.compareTo("sad") == 0 ) System.printString(":( ");
		    else                             System.printString(":) ");
		}
		else if( Next.compareTo("new-line") == 0 ) {
		    int No = ifpot.next(1);
		    for(int i=0; i<No ;i++)
			System.printString("\n");
		}
	    }
	}   
	else {
	    System.printString("(use the --nice command line flag for graphical output)"+"\n");
	}
	System.printString(""+"\n");




	/*
	cl    = 
	  //disjoint gp5 
	  new GetPot(args1, "Get");

	ifpot = 
	  //disjoint gp6
	  new GetPot("expand.pot");

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
	*/






	
	cl = 
	  //disjoint gp7
	  new GetPot(args1, "Follow");
	
	// to function 'cl.search(..)' 
	if( cl.search("--help", "-h", "--hilfe") )
	    print_help();

	// read arguments one by one on the command line
	//  (lazy command line parsing)
	double Alpha = cl.follow(0.,  "--alpha");   // [rad]
	int    Beta  = cl.follow(256, "--beta"); // [1/s]
	cl.init_multiple_occurrence();
	User  = cl.follow("You", "--user");      
	int    No    = cl.next(0x42); 
	User2 = cl.follow("You Two", "--user"); // second user specified ?
	int    No2   = cl.next(0x43); 
	cl.enable_loop(); 
	double XSz   = cl.follow(0., "--size", "--sz", "-s"); // [cm]
	double YSz   = cl.next(0.);                           // [cm]
	
	System.printString("\n");
	System.printString("Alpha = " + Alpha);
	System.printString("Beta  = " + Beta );
	System.printString("Names           = " + User + " and " + User2);
	System.printString("Special numbers = " + No + " and " + No2);
	System.printString("x-size, y-size  = " + XSz + ", " + YSz);
	System.printString("\n");




	/*
	cl = 
	  //disjoint gp8
	  new GetPot(args1, "Get");

	// GetPot Java can treat upto 6 strings, then it needs a String[]
	if( cl.search("--help", "-h", "--hilfe", "--sos", "--about", "--what-is") ) {
	    System.printString("call " +  cl.getitem(0) + " with four arguments.");
	    System.exit(0);
	}
	String[] someStrings = new String[11];
	someStrings[0] = "--stop";
	someStrings[1] = "--quit";
	someStrings[2] = "--do-nothing";
	someStrings[3] = "--let-it-be";
	someStrings[4] = "-q";
	someStrings[5] = "-s";
	someStrings[6] = "--never-mind";
	someStrings[7] = "--achtung-anhalten";
	someStrings[8] = "--arrete";
	someStrings[9] = "--fait-rien";
	someStrings[10] = "--glonde-rien";

	if( cl.search(someStrings) )
	    System.exit(0);

	int     A = cl.get(4, 256);    // integer version of get()
	double  B = cl.get(1, 3.14);   // double version of get()
	String  C = cl.get(2, "You");  // const char* version of get()
	
	System.printString("A (argument no 4) = " + A);
	System.printString("B (argument no 1) = " + B);
	System.printString("C (argument no 2) = " + C);
	*/




	/*
 	cl = 
	  //disjoint gp9
	  new GetPot(args1, "InputFile");

 	GetPot   ifile = 
	  //disjoint gp10
	  new GetPot("example.pot");
	
   	if( cl.size() == 1 || cl.search("--help", "-h") ) 
  	{ print_help();	System.exit(0); }

  	if( cl.search("--internal-information", "-i") ) 
  	{ ifile.print(); System.exit(0); }

	// (2) playing with sections
	System.printString("webpage       = " + ifile.call("webpage", "nothing.somewhere.idn"));
	System.printString("user          = " + ifile.call("user", "nobody"));
	System.printString("dos-file      = " + ifile.call("dos-file", "nobody"));
	System.printString("latex-formula = " + ifile.call("latex-formula", "nobody"));
	System.printString("no. clicks    = " + ifile.call("clicks", 0));
	System.printString("acceleration  = " + ifile.call("acceleration", 3.14));
    
	System.printString("vehicle/wheel-base = " + ifile.call("vehicle/wheel-base",2.66));
	System.printString("vehicle/initial-xyz = ");
        // first element of vector
	System.printString(ifile.call("vehicle/initial-xyz", 0., 0) + "\t");  
        // second element of vector 
	System.printString(ifile.call("vehicle/initial-xyz", 0., 1) + "\t");  
        // third element of vector
	System.printString(ifile.call("vehicle/initial-xyz", 0., 2) + "\n");  

	System.printString("vehicle/tires/B = " + ifile.call("vehicle/tires/B",777.7));
	System.printString("              C = " + ifile.call("vehicle/tires/C", 777.7));
	System.printString("              E = " + ifile.call("vehicle/tires/E", 777.7));
	System.printString("              D = " + ifile.call("vehicle/tires/D", 777.7));

	System.printString("vehicle/chassis/Roh = " + ifile.call("vehicle/chassis/Roh",777.7));
	System.printString("                S   = " + ifile.call("vehicle/chassis/S",  777.7));
	System.printString("                Cd  = " + ifile.call("vehicle/chassis/Cd", 777.7));

	System.printString("vehicle/chassis/doors/number = " + ifile.call("vehicle/chassis/doors/number",2));
	System.printString("                      locks  = " + ifile.call("vehicle/chassis/doors/locks","yes"));

        // (3) playing with things we do normally only with command line arguments
  	boolean n_f = ifile.search("--nonsense", "-n", "--unsinn", "--sans-sense");
	double XR = ifile.follow(3.14, "vehicle/-x");
	System.printString("x-ratio    = " + XR);
	System.printString("sound-mode = " + ifile.next("none"));    
	String nfl;
	if( n_f ) { nfl = "activated"; } else { nfl = "disabled"; }
	System.printString("nonsense-flag = " + nfl);
	*/
	






	/*
	cl = 
	  //disjoint gp11
	  new GetPot(args1, "Next");

	// all the following pain, only to pass a string array
	// to function 'cl.search(..)' 
	if( cl.search("--help", "-h", "--hilfe") ) print_help();
        // read arguments one by one on the command line
	//  (lazy command line parsing)
	cl.reset_cursor();
	double   dA = cl.next(0.);    // [rad]
	int      iB = cl.next(256);   // [1/s]
	User = cl.next("You");
	int      iNo   = cl.next(0x42); 

	System.printString("\n");
	System.printString("A = " + dA);
	System.printString("B = " + iB);
	System.printString("Name           = " + User);
	System.printString("Special number = " + iNo);
	System.printString("\n");
	*/


	/*
	System.exit( -1 );
	

	cl = disjoint gp12 new GetPot(args1, "Nominus");

	// if( cl.size() == 1 || cl.search("--help", "-h") ) print_help();

	// print out all argument that do not start with a minus
	String  nm = cl.next_nominus();     
	while( nm != "" ) {
	    System.printString("[" + nm + "]");
	    nm = cl.next_nominus();     
	} 
    
	System.printString("\n");

	// now get the whole vector at once
	String[]   files = cl.nominus_vector();
	for(int i=0; i<files.length ; i++)
	    System.printString("<" + files[i] + ">");
	*/







	/*
	cl = 
	  //disjoint gp13
	  new GetPot(args1, "Options");

	// (1) search for a single option
	// -------------------------------
	//     check if the '--do-nothing' flag is set and exit if yes
	if( cl.search("--do-nothing") ) System.exit(0);

	// (2) search for multiple options with the same meaning
	// GetPot Java can treat upto 6 strings, then it needs a String[]
	if( cl.search("--help", "-h", "--hilfe", "--sos", "--about", "--what-is") )
	    print_help();

	//     does the user want us to be nice ... ?
	boolean  be_nice_f = cl.search("--nice");
	

	if( cl.search("--beep", "--piepse", "--bip", "--senal-acustica", "-b") )
	    System.printString("(imagine a beep - the '\\a' is a invalid escape character in Java)");
	
	System.printString( "Program terminated.");
	if( be_nice_f == true )
	    System.printString( "Have a nice day.");
	*/





	



	cl = 
	  //disjoint gp14
	  new GetPot(args1, "Ufo");

	String[]   ufos = new String[1];

	if( cl.search("-h", "--help") ) {
	  print_help();
	  System.exit(0);
	}
	else if( cl.search("--arguments") ) {
	  // (*) unidentified flying arguments ---------------------------------------------
	  String[] ufas = new String[5];
	  ufas[0] = "--arguments";
	  ufas[1] = "-h";
	  ufas[2] = "--help";
	  ufas[3] = "yes";
	  ufas[4] = "no";
	  ufos = cl.unidentified_arguments(ufas);
	  print("Unidentified Arguments (other than '--arguments', '-h', '--help', 'yes', 'no'):\n");
	}
	else if( cl.search("--options") ) {
	  // (*) unidentified flying options (starting with '-' or '--') -------------------
	  String[] ufoos = new String[5];
	  ufoos[0] = "--options";
	  ufoos[1] = "-h";
	  ufoos[2] = "--help";
	  ufoos[3] = "yes(ignored anyway)";
	  ufoos[4] = "no(ignored anyway)";
	  ufos = cl.unidentified_options(ufoos);
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
	  String[] ufvs = new String[5];
	  ufvs[0] = "x";
	  ufvs[1] = "y";
	  ufvs[2] = "z";
	  ufvs[3] = "length";
	  ufvs[4] = "height";
	  ufos = cl.unidentified_variables(ufvs);
	  
	  print("Unidentified Variables (other than 'x', 'y', 'z', 'length', 'height'):\n");
	}
	else if( cl.search("--sections") ) {
	  // (*) unidentified flying sections ----------------------------------------------
	  GetPot ifile = disjoint gp15 new GetPot("example.pot");
	  String[] ufss = new String[8];
	  ufss[0] = "vehicle/";
	  ufss[1] = "vehicle/tires/"; 
	  ufss[2] = "vehicle/chassis/";
	  ufss[3] = "vehicle/chassis/doors/";
	  ufss[4] = "group/";
	  ufss[5] = "other/";
	  ufss[6] = "user-variables/";
	  ufss[7] = "pseudo-function-calls/";
	  ufos = ifile.unidentified_sections(ufss);
	  
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







	
	cl = 
	  //disjoint gp16
	  new GetPot(args1, "Variables");

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

	System.printString("Specified Parameters:");
	System.printString("float   = " + A_Number);
	System.printString("integer = " + An_Integer);
	System.printString("string  = " + A_String);
	System.printString("\n");
	System.printString("Vector elements:");
	System.printString("Element 0 (double) = " + Element1);
	System.printString("Element 1 (string) = " + Element2);
	System.printString("Element 2 (int)    = " + Element3);	
    }


    static void print(String Str) {
	System.printString(Str);
    }

    static void my_print(String Str) {
	System.printString(Str);
    }

    static void my_print(String Str1, String Str2) {
	System.out.println(Str1 + Str2);
    }    
    
    static void something(String User, String User2, String User3,
			  double Value, double Value2, double Value3,
			  int Number, int Number2, int Number3)	{
	    System.printString("Users   = " + User  + ", " + User2 + ", " + User3+"\n");
	    System.printString("Values  = " + Value + ", " + Value2 + ", " + Value3+"\n");
	    System.printString("Numbers = " + Number + ", " + Number2 + ", " + Number3+"\n");
    }


    static double Sqr(double x) { return x*x; } 


    static void print_help() {
	    System.printString("help!\n");
	    System.exit(0);
	}

    static void print_help(String s) {
	    System.printString("help!\n");
	    System.exit(0);
	}
}



