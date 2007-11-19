public class ArrayTest {
    Item x;	
    public ArrayTest() {
    }
    
    public static void main(String[] args) {
	ArrayTest[] n=new ArrayTest[10];
	for(int i=0;i<10;i++) {
	    n[i]=new ArrayTest();	
	    n[i].x=new Item();
	    n[i].x.i=new Integer(10);
	}
	addItems(n);
    }
    
    public static int addItems(ArrayTest [] array) {
	int sum=0;
	for(int i=0;i<array.length;i++) {
	    ArrayTest a=array[i];
	    sum=sum+a.x.i.intValue();
	}
	return sum;
    }
}

public class Item {
    Integer i;
    public Item() {
	
    }

}
