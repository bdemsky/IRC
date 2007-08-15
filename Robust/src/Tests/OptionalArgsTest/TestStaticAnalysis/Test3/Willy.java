

task Startup(StartupObject s {initialstate}){
    
    Test o = new Test() {A};
    
    taskexit(s {!initialstate});
    
}

task ONE(optional Test o{A}){

    taskexit(o {!A, B});
}

task TWO(optional Test o{B}){
    
    taskexit(o {!B, C});
}

task THREE(optional Test o{B}){

    taskexit(o {!B, D});
}

task FOUR(optional Test o{D||C}){

    taskexit(o {!D,!C, F});
    
    
}
/*
task FIVE(Test o{D}){
  
    taskexit(o {!D, F});

}

task SIX(Test o{E}){
  
    taskexit(o {!E, F});

}

task SEVEN(Test o{F}){
  
    taskexit(o {!F, G});

}

task EIGHT(Test o{G}){
  
    taskexit(o {!G, H});

}
*/
