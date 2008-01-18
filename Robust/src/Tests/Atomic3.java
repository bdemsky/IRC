public class Atomic3 extends Thread {
	public Atomic3() {
	}
	Tree root;
	Integer count;
	public static void main(String[] st) {
		int mid = (128<<24)|(195<<16)|(175<<8)|70;
		int b;
		Atomic3 at3 = null;
		Integer y,z;
		atomic {
			at3 = global new Atomic3();
			z = global new Integer(300);
			at3.root = global new Tree();
			at3.root.insert(z);
			b = at3.root.value.intValue();
		}
		System.printString("b is ");
		System.printInt(b);
		atomic{
			at3.root.item = 2445;
			y = global new Integer(400);
			at3.root.value = y;
			b = at3.root.value.intValue();
		}
		System.printString("b is ");
		System.printInt(b);
		System.printString("\n");
		System.printString("Starting\n");
		at3.start(mid);
		System.printString("Finished\n");
		while(true) {
			;
		}
	}

	public int run() {
		int a;
		atomic {
			a = root.value.intValue();
		}
		System.printString("a is ");
		System.printInt(a);
		System.printString("\n");
	}
}

public class Tree {
	public Integer value;
	public int item;
	public Tree left;
	public Tree right;

	public Tree() {
	}

	public Tree(Integer item) {
		value = item;
		left = null;
		right = null;
	}

	public Tree(Integer item , Tree l, Tree r) {
		value = item;
		left =l;
		right = r;
	}

	public Tree insert(Integer a) {
		value = a;
		left = null;
		right = null;
		return this;
	}
}
