

task Startup(StartupObject s {initialstate}){
    
    Test o = new Test() {A};
    Test2 o2 = new Test2() {G};
    taskexit(s {!initialstate});
    
}

task ONE(Test o{A}){

    taskexit(o {!A, B});
}

task TWO(optional Test o{B}){
    
    taskexit(o {!B, C});
}

task THREE(optional Test o{C}, Test2 o2{G}){

    taskexit(o {!C, D});
}

task FOUR(optional Test o{D}, Test2 o2{G}){
    
   taskexit(o {!D, E});
}
