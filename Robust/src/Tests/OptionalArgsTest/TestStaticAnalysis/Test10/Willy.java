

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

   taskexit(o {!C, D});
  
}

task FOUR(optional Test o{D}){

  taskexit(o {!D, B});
  
    
}

task FIVE(optional Test o{D}){
  
    taskexit(o {!D, E});

}

task SIX(optional Test o{E}){
  
   if (true) taskexit(o {!E, F});

}
/*
task SEVEN(Test o{F}){
  
    taskexit(o {!F, G});

}

task EIGHT(Test o{G}){
  
    taskexit(o {!G, H});

}
*/
