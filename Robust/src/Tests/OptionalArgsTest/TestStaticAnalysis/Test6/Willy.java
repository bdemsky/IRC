

task Startup(StartupObject s {initialstate}){
    
    Test o = new Test() {A};
    
    taskexit(s {!initialstate});
    
}

task ONE(Test o{A}){

    taskexit(o {!A, B});
}

task TWO(Test o{B}){
    
  taskexit(o {!B, C});
  
}

task THREE(Test o{B}){

    taskexit(o {!B, D});
}

task FOUR(Test o{C}){

    taskexit(o {!C, E});
    
    
}

task FIVE(Test o{D}){
  
    taskexit(o {!D, F});

}

task SIX(optional Test o{E}){
  
    taskexit(o {!E, G});

}

task SEVEN(Test o{F}){
  
    taskexit(o {!F, G});

}

task EIGHT(optional Test o{G}){
  
    taskexit(o {!G, H});

}

