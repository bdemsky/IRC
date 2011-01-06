public class Test{
    static public void main(String[] args){
	int mainInt = 10;
	Foo f1=new Foo();
	Foo f2=new Foo(3);
	f1.print();
	f2.print();
    }
}

class Foo{
    private int a=1;
    private double b=5;
    private byte c=(byte)10;
    private short d=(short)7;
    private long e=15;
    private float f=6;
    private boolean g=true;
    private char h='h';
    
     public Foo(int alpha){
	 a=alpha;
     }
    
    public Foo(){
	
    }
    
    public void print() {
	System.out.println("a="+a);
	System.out.println("b="+b);
	System.out.println("c="+c);
	System.out.println("d="+d);
	System.out.println("e="+e);
	System.out.println("f="+f);
	System.out.println("g="+g);
	System.out.println("h="+h);
    }

}
