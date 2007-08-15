

task Startup(StartupObject s {initialstate}){
    tag t =new tag(link);
    Test o = new Test() {A}{t};
    taskexit(s {!initialstate});
    
}

task ONE(Test o{A}{link l}){

    taskexit(o {!A, B});
}

task TWO(Test o{B}{link l}){
    
  taskexit(o {!B, C});
  
}

task THREE(Test o{B}{link l}){

    taskexit(o {!B, D});
}

task FOUR(Test o{C}{link l}){
    tag f =new tag(link);
    taskexit(o {!C, E}{!l, f});
    
    
}

task FIVE(Test o{D}{link l}){
    tag h =new tag(link);
    taskexit(o {!D, F}{!l, h});

}

task SIX(optional Test o{E}{link l}){
  
    taskexit(o {!E, G});

}

task SEVEN(Test o{F}{link l}){
  
    taskexit(o {!F, G});

}

task EIGHT(optional Test o{G}{link l}){
  
    taskexit(o {!G, H});

}

