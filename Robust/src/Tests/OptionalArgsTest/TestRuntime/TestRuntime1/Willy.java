

task Startup(StartupObject s {initialstate}){
    
    Test o = new Test() {A};
    
    taskexit(s {!initialstate});
    
}

task ONE(Test o{A}){
    o.decrease();
    System.printString("Inside ONE\n");
    int i = 100/o.getNumber();
    taskexit(o {!A, B});
}

task TWO(Test o{B}){
    System.printString("Inside TWO\n");
  taskexit(o {!B, C});
  
}

task THREE(optional Test o{B}){
System.printString("Inside THREE\n");
    taskexit(o {!B, D});
}

task FOUR(Test o{C}){
System.printString("Inside FOUR\n");
    taskexit(o {!C, E});
    
    
}

task FIVE(Test o{D}){
  System.printString("Inside FIVE\n");
    taskexit(o {!D, F});

}

task SIX(optional Test o{E}){
  System.printString("Inside SIX\n");
    taskexit(o {!E, G});

}

task SEVEN(Test o{F}){
  System.printString("Inside SEVEN\n");
    taskexit(o {!F, G});

}

task EIGHT(optional Test o{G}){
  System.printString("Inside EIGHT\n");
    taskexit(o {!G, H});

}

