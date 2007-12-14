public class Atomic4 extends Thread {
	public Atomic4() {
		People[] team = new People[2];
	}
	People[] team;
	public static void main(String[] st) {
		int mid = (128<<24)|(195<<16)|(175<<8)|70;
		int b,c;
		
		Integer age;
		String name;
		Atomic4 at4 = null;
		atomic {
			at4 = global new Atomic4();
			age = global new Integer(35);
			name = global new String("Harry Potter");
			at4.team[0] = global new People(name, age);
			b = at4.team[0].getAge();
		}
		atomic {
			age = global new Integer(70);
			name = global new String("Harry Potter");
			at4.team[1] = global new People(name,age);
			c = at4.team[1].getAge();
		}
		System.printInt(b);
		System.printString("\n");
		System.printString("Starting\n");
		at4.start(mid);
		System.printString("Finished\n");
		while(true) {
			;
		}
	}

	public int run() {
		String name;
		int a;
		boolean old = false;
		atomic {
			//FIXME a bug value of trans commit is not saved
			//a = root.value.intValue();
			a = team[1].getAge();
			name = team[1].getName();
			if(a > 65)
				old = true;
		}
		if(old){
			System.printString(name + " gets Pension"); 
			System.printString("\n");
		}
	}
}

public class People {
	String name;
	Integer age;

	public People(String name, Integer age) {
		this.name = name;
		this.age = age;
	}

	public String getName() {
		return name;
	}

	public int getAge() {
		return age.intValue();
	}

	public boolean isSenior() {
		if(this.getAge() > 65)
			return true;
		return false;;
	}
}
