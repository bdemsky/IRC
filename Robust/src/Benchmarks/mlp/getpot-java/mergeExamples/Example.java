import GetPot.*;
import java.lang.String;

public class Example {

    public static void main(String args []) {

      

	GetPot   cl = disjoint gp1 new GetPot(args, "Flags");
	if( cl.size() == 1 || cl.search("--help", "-h") ) print_help();

	// does first argument contain 'x', 'X', 'c', 'C', 'k', or  'K' ?
	boolean  first_f = cl.argument_contains(1, "xX");
	boolean  second_f  = cl.argument_contains(1, "cCkK");
	
	// is there any option starting with '-' containing 'a', 'b', or 'c' ?
	boolean  abc_f = cl.options_contain("abc");
	
	System.printString("first flag  = " + first_f);
	System.printString("second flag = " + second_f);
	System.printString("a, b, or c found = " + abc_f );



	cl = disjoint gp2 new GetPot(args, "DirectFollow");
	
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







	cl = disjoint gp3 new GetPot(args, "Filter");
	GetPot   ifpot = disjoint gp4 new GetPot("example.pot");
               
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

	System.printString(" -- flags in options / arguments");
	first_f  = ifpot.argument_contains(1, "xX");
	second_f = ifpot.argument_contains(1, "cCkK");
	abc_f    = ifpot.options_contain("abc");
	System.printString("    Flags in first argument in [flags]\n");
	System.printString("    x or X in arg 1       = " + first_f);
	System.printString("    c, C, k or K in arg 1 = " + second_f);
	System.printString("    a,b, or c in options  = " + abc_f);
	System.printString("");
	System.printString(" -- search(), next() and follow()");
	System.printString("");
	System.printString("    found \"--rudimental\" = " + ifpot.search("--rudimental"));

	int Tmp1 = ifpot.next(-1);
	int Tmp2 = ifpot.next(-1);
	System.printString("    followed by " + Tmp1 + " " + Tmp2);

	String Tmp3 = ifpot.follow("nothing", "--rudimental");
	int    Tmp4 = ifpot.next(-1);
	System.printString("    rudimental = " + Tmp3 + ", " + Tmp4 + "\n");

	//  -- variables
	System.printString("");
	System.printString(" -- variables in section [user-variables]");
	System.printString("");
	ifpot.set_prefix("user-variables/");

	String variable_names[] = ifpot.get_variable_names();
	for(int i=0; i < variable_names.length ; i++) {
	    String name = variable_names[i];
	    System.printString("    " + name + "   \t= ");
	    System.printString(""+ifpot.call(name, 1e37, 0));
	    System.printString("[" + ifpot.call(name, "[1]", 1) + "]\n");
	}
	System.printString("");	

	//  -- pseudo function calls
	if( cl.search("--nice") ) {
	    System.printString(" -- pseudo function call feature");
	    System.printString("");
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
	    System.printString("(use the --nice command line flag for graphical output)");
	}
	System.printString("");
    }




    
    static void something(String User, String User2, String User3,
			  double Value, double Value2, double Value3,
			  int Number, int Number2, int Number3)	{
	    System.printString("Users   = " + User  + ", " + User2 + ", " + User3);
	    System.printString("Values  = " + Value + ", " + Value2 + ", " + Value3);
	    System.printString("Numbers = " + Number + ", " + Number2 + ", " + Number3);
    }


    static double Sqr(double x) { return x*x; } 


    static void print_help() {
	    System.printString("help!");
	    System.exit(0);
	}

    static void print_help(String s) {
	    System.printString("help!");
	    System.exit(0);
	}
}



