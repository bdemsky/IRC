

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

task THREE(optional Test o{C}){

   if (true)  taskexit(o {!C, D});
   else taskexit(o {C});
}

task FOUR(optional Test o{D}){

    taskexit(o {!D, E});
    
    
}
/*
task FIVE(Test o{C}){
  
    taskexit(o {C});

}

task SIX(Test o{E}){
  
    taskexit(o {!E, G});

}

task SEVEN(Test o{F}){
  
    taskexit(o {!F, G});

}

task EIGHT(Test o{G}){
  
    taskexit(o {!G, H});

}
*/
