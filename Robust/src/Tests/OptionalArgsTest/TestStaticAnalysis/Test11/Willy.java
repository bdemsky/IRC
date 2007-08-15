

task Startup(StartupObject s {initialstate}){
    
    Test o = new Test() {A};
    
    taskexit(s {!initialstate});
    
}

task ONE(optional Test o{A}){

    taskexit(o {!A, B});
}

task TWO(optional Test o{B}){
    
 if(o.is()) taskexit(o {!B, C});
 else taskexit(o {!B, D});
  
}

/*task THREE(Test o{B}){

    taskexit(o {!B, D});
    }*/

task FOUR(optional Test o{C}){

    taskexit(o {!C, E});
    
    
}

task FIVE(optional Test o{D}){
  
    taskexit(o {!D, F});

}

task SIX(optional Test o{E}){
  
    taskexit(o {!E, G, J});

}

task SEVEN(optional Test o{F}){
  
    taskexit(o {!F, G, K});

}

task EIGHT(optional Test o{G}){
  
    
    if(true) taskexit(o {!G, H}); 
    else taskexit(o {!G, I});

}

