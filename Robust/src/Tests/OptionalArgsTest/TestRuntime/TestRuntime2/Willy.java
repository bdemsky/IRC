

task Startup(StartupObject s {initialstate}){
    
    Test o = new Test() {A};
    
    taskexit(s {!initialstate});
    
}

task ONE(Test o{A}){
    o.decrease();
    System.printString("Inside ONE\n");
    int i = 100/o.getNumber();
    taskexit(o {!A, B});
    taskexit(o {!A, B});
}

task TWO(optional Test o{B}){
 System.printString("Inside TWO\n");   
 if(false) taskexit(o {!B, C});
 else taskexit(o {!B, D});
  
}

/*task THREE(Test o{B}){

    taskexit(o {!B, D});
    }*/

task FOUR(Test o{C}){
System.printString("Inside FOUR\n");
    taskexit(o {!C, E});
    
    
}

task FIVE(optional Test o{D}){
  System.printString("Inside FIVE\n");
    taskexit(o {!D, F});

}

task SIX(Test o{E}){
  System.printString("Inside SIX\n");
    taskexit(o {!E, G, J});

}

task SEVEN(optional Test o{F}){
  System.printString("Inside SEVEN\n");
    taskexit(o {!F, G, K});

}

task EIGHT(optional Test o{G}){
  System.printString("Inside EIGHT\n");
    
    if(false) taskexit(o {!G, H}); 
    else taskexit(o {!G, I});

}

task NINE(optional Test o{H}){
System.printString("Inside NINE\n");
taskexit(o {!H}); 
}
task TEN(optional Test o{I}){
System.printString("Inside TEN\n");
 if(true) taskexit(o {!I,L }); 
 else taskexit(o {!I,M });
}
