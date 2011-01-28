class AssignmentConversionTest {
    public static final int sfi = 100;
  
    public static void main(String[] args) {
        short s = 12;           // narrow 12 to short
        float f = s;            // widen short to float
        System.out.println("f=12 : " + (int)f);

        char c = '\u0009';
        int l = c;         // widen char to int
        System.out.println("l=0x9 : 0x" + Integer.toString(l));

        f = 1.23f;
        double d = f;           // widen float to double
        System.out.println("d=123 : " + (int)(d*100));
        
        s = AssignmentConversionTest.sfi;
        System.out.println("s=100 : " + s);
        
        s = 12+2;
        System.out.println("s=12+2=" + (12+2) + ": "+ s);
        
        s = 12-2;
        System.out.println("s=12-2=" + (12-2) + ": "+ s);
        
        s = 12*2;
        System.out.println("s=12*2=" + (12*2) + ": "+ s);
        
        s = 12/2;
        System.out.println("s=12/2=" + (12/2) + ": "+ s);
        
        s = 12%2;
        System.out.println("s=12%2=" + (12%2) + ": "+ s);
        
        s = 12|2;
        System.out.println("s=12|2=" + (12|2) + ": "+ s);
        
        s = 12^2;
        System.out.println("s=12^2=" + (12^2) + ": "+ s);
        
        s = 12&2;
        System.out.println("s=12&2=" + (12&2) + ": "+ s);
        
        s = 12>2?1:2;
        System.out.println("s=12>2?1:2=" + (12>2?1:2) + ": "+ s);
        
        s = 12<2?1:2;
        System.out.println("s=12<2?1:2=" + (12<2?1:2) + ": "+ s);
        
        /*       
        byte a = 12<2;

        s = 12%2?1:2;
        System.out.println("s=12&2=" + (12%2?1:2) + ": "+ s);
        
        short se = 123;
        char ce = se;         // error: would require cast
        se = ce;              // error: would require cast
        */
    }
}
