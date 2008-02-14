/* This test case tests the thread joining for a threadDSM library */ 
public class Atomic5 extends Thread {
	public People[] team;
	public Atomic5() {
	}
	public static void main(String[] st) {
		int mid = (128<<24)|(195<<16)|(175<<8)|70;
		int b = 0,c = 0;
		int i;
		
		Integer age;
		Atomic5 tmp;
		Atomic5[] at5;
		
		atomic {
			at5 =  global new Atomic5[4];
		}
		atomic {
			for(i = 0; i < 4; i++) {
				at5[i] = global new Atomic5();
				at5[i].team = global new People[2];
				at5[i].team[0] = global new People();
				at5[i].team[1] = global new People();
				age = global new Integer(35);
				at5[i].team[0].age = age;
				at5[i].team[1].age = age;
			}
			b = at5[1].team[0].getAge();
		}
		System.printInt(b);
		System.printString("\n");
		atomic {
			age = global new Integer(70);
			at5[1].team[1].age = age;
			c = at5[1].team[1].getAge();
			//at5[20].team[1].age = age;
		}
		System.printInt(c);
		System.printString("\n");
		System.printString("Starting\n");
		for(i = 0 ; i< 4; i++) {
			atomic {
				tmp = at5[i];
			}
			tmp.start(mid);
		}
		for(i = 0; i< 4; i++) {
			atomic {
				tmp = at5[i];
			}
			tmp.join();
		}
		System.printString("Finished\n");
		/*
		while(true) {
			;
		}
		*/
	}

	public void run() {
		int ag;
		boolean old = false;
		atomic {
			ag = team[1].getAge();
			if(ag > 65)
				old = true;
		}
		if(old){
			System.printString("Gets Pension"); 
			System.printString("\n");
		} else {
			System.printString("Gets No Pension"); 
			System.printString("\n");
		}
	}
}

public class People {
	Integer age;

	public People() {
	}

	public People(Integer age) {
		this.age = age;
	}

	public void setAge(Integer a) {
		age = a;
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
