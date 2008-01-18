public class Atomic4 extends Thread {
	public People[] team;
	public Atomic4() {
	}
	public static void main(String[] st) {
		int mid = (128<<24)|(195<<16)|(175<<8)|70;
		int b = 0,c = 0;
		
		Integer age;
		String name;
		Atomic4 at4 = null;
		atomic {
			at4 = global new Atomic4();
			at4.team = global new People[2];
			at4.team[0] = global new People();
			at4.team[1] = global new People();
			age = global new Integer(35);
			name = global new String("Harry Potter");
			at4.team[0].name = name;
			at4.team[0].age = age;
			b = at4.team[0].getAge();
		}
		atomic {
			age = global new Integer(70);
			name = global new String("John Smith");
			at4.team[1].name = name;
			at4.team[1].age = age;
			c = at4.team[1].getAge();
		}
		System.printInt(b);
		System.printInt(c);
		System.printString("\n");
		System.printString("Starting\n");
		at4.start(mid);
		System.printString("Finished\n");
		while(true) {
			;
		}
	}

	public int run() {
		String newname = "";
		int ag;
		boolean old = false;
		atomic {
			ag = team[1].getAge();
			newname = team[1].getName();
			if(ag > 65)
				old = true;
		}
		if(old){
			System.printString(" gets Pension"); 
			System.printString("\n");
		}
	}
}

public class People {
	String name;
	Integer age;

	public People() {
	}

	public People(String name, Integer age) {
		this.name = name;
		this.age = age;
	}

	public void setName(String n) {
		name = n;
	}
	
	public void setAge(Integer a) {
		age = a;
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
		return false;
	}
}
