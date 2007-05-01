class Example {
    flag needoperation;
    flag needprinting;
    public Example() {}

   
    int operation;
    int x;
    int y;
    int z;
}

/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

task Startup(StartupObject s {initialstate}) {
    for(int i=0;i<10;i++) {
	Example e=new Example() {needoperation};
	e.x=i;
	e.y=2;
	e.operation=i%2;
    }
    
    taskexit(s {!initialstate}); /* Turns initial state flag off, so this task won't refire */

}

/* Fails for x=1 */

task DoOperation(Example e{needoperation}) {
    e.z=10*e.y/(e.x-1);

    if (e.operation==0)
	/* Print the result */
	taskexit(e {!needoperation, needprinting});
    else
	/* Don't print the result */
	taskexit(e {!needoperation});
}

/* Note that we can write arbitrary boolean expressions for flag
 * expressions.  For example, needprinting && ! needoperation would
 * also be a legal flag expression */

task DoPrint(Example e{needprinting && ! needoperation}) {
    System.printInt(e.z);
    System.printString("\n");
    taskexit(e {!needprinting});
}
