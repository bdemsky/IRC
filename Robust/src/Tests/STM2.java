/* This test case tests the thread joining for a threadDSM library */ 
public class STM2 extends Thread {
  public People[] team;
  public STM2() {
  }
  public static void main(String[] st) {
    int b = 0,c = 0;
    int i;
    
    Integer age;
    STM2 tmp;
    STM2[] at5;
    
    at5 =  new STM2[4];
    atomic {
      for(i = 0; i < 4; i++) {
	at5[i] = new STM2();
	at5[i].team = new People[2];
	at5[i].team[0] = new People();
	at5[i].team[1] = new People();
	age = new Integer(35);
	at5[i].team[0].age = age;
	at5[i].team[1].age = age;
      }
      b = at5[1].team[0].getAge();
    }
    System.printInt(b);
    System.printString("\n");
    atomic {
      age = new Integer(70);
      at5[1].team[1].age = age;
      c = at5[1].team[1].getAge();
    }
    System.printInt(c);
    System.printString("\n");
    System.printString("Starting\n");
    for(i = 0 ; i< 4; i++) {
      tmp = at5[i];
      tmp.start();
    }
    for(i = 0; i< 4; i++) {
      tmp = at5[i];
      tmp.join();
    }
    System.printString("Finished\n");
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
