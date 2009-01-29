

task Startup( StartupObject s{ initialstate } ) {

  int a = 0;

  sese {
    int x = 3;
  }
  
  a = x;
  
  
  sese {
    int y = 4;
    y+=4;

    sese {
      int z = x + y;
      z+=6;
      Integer n=new Integer(23);
    }
  }

  x = y + z;


  taskexit( s{ !initialstate } );
}
