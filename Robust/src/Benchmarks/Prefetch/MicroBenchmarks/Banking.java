public class Banking extends Thread {
  Account myacc;
  public Banking(Account myacc) {
    this.myacc = myacc;
  }

  public void run() {
    for(int i = 0;  i < 10000; i++) {
      myacc




  }

  public static void main(String[] args) {
    Banking[] b;
    int[] mid = new int[2];
	mid[0] = (128<<24)|(195<<16)|(175<<8)|79; //dw-8
	mid[1] = (128<<24)|(195<<16)|(175<<8)|73; //dw-5
    atomic {
      b = global new Banking[2];
      for(int i = 0; i < 2; i++) {
        b[i] = global new Banking();
        b[i].myacc = global new Account(i);
      }
    }

    Banking tmp;
    for(int i = 0; i <  2; i++) {
      atomic {
        tmp = b[i];
      }
      tmp.start(mid[i]);
    }

    for(int i = 0; i < 2; i++) {
      atomic {
        tmp = b[i];
      }
      tmp.join();
    }
  }
}

class Account {
  int custid;
  int balance;

  public Account(int custid) {
    this.custid = custid;
    this.balance = 0;
  }

  public void deposit(int amt) {
    balance += amt;
  }

  public void withdraw(int amt) {
    if(amt > balance) {
      System.printString("Amount is greater than balance\n");
    } else {
      balance -= amt;
    }
  }
}
