public class FieldPointer {
	Account x;
	public FieldPointer() {
	}

	public static void main(String[] args) {
		int bal;
		FieldPointer fp = new FieldPointer();
		fp.x = new Account();
		fp.x.name = new String("TestAccount");
		fp.x.accountid = new Integer(12345);
		fp.x.balance = new Item();
		fp.x.balance.i = new Integer(11000);
		bal = fp.getBalance(fp.x.accountid);
		if(bal < 7500) 
			bal = bal + fp.x.getBalance().intValue();
		else 
			bal = fp.x.getBalance().intValue();
		System.printInt(bal);
	}

	public int getBalance(Integer accountid) {
		if(accountid.intValue() > 12000) {
			return 10000;
		} else {
			return 5000;
		}
	}
}

public class Account {
	String name;
	Integer accountid;
	Item balance;
	public Account() {
	}
	public Integer getBalance() {
		return balance.i;
	}
}

public class Item {
	Integer i;
	public Item() {
	}
}
