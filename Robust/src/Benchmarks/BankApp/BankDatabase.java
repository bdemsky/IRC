public class BankDatabase
{
	flag DatabaseInit;

	BankAccount[] database;
	int numOfAccounts;
	
	public BankDatabase()
	{
		//6 pre-created accounts
		numOfAccounts = 6;
		
		//10 account limit
		database = new BankAccount[10];
		
		for(int i = 0; i < 10; i++)
		{
			database[i] = new BankAccount();
		}
		
		//some hardcoded values
		database[0].modifyAccount("123456789", "John@@@@@@", "Q@@@@@@@@@", "Public@@@@", "1", "256000001@", "2007");
		database[1].modifyAccount("987654321", "Nancy@@@@@", "H@@@@@@@@@", "Private@@@", "2", "166@@@@@@@", "1234");
		database[2].modifyAccount("000111000", "Paul@@@@@@", "Wellington", "Franks@@@@", "1", "454225@@@@", "0000");
		database[3].modifyAccount("211411911", "Felix@@@@@", "the@@@@@@@", "Cat@@@@@@@", "3", "0@@@@@@@@@", "9999");
		database[4].modifyAccount("111000111", "Paul@@@@@@", "Wellington", "Franks@@@@", "2", "1128989@@@", "0000");
		//empty
		database[5].modifyAccount("@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@", "@@@@@@@@@@", "@@@@");
		database[6].modifyAccount("@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@", "@@@@@@@@@@", "@@@@");
		database[7].modifyAccount("@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@", "@@@@@@@@@@", "@@@@");
		database[8].modifyAccount("@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@", "@@@@@@@@@@", "@@@@");
		database[9].modifyAccount("@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@@@@@@@@@@", "@", "@@@@@@@@@@", "@@@@");
		
		//test read into database[5]
		ReadFile(5);
		
		//test write from database[5]
		WriteFile(database[5].AccountNumber, database[5].FirstName, database[5].MiddleName, database[5].LastName, database[5].AccountType, database[5].Balance, database[5].PIN);
	}
	
	/* what, no destructor?
	public ~BankDatabase()
	{
		//test write from database[5]		
	}*/
	
	public void ReadFile(int index) 
	{
		//need to check if read/write works the way I think it does
		String filename="BankAppRead.dat";
		FileInputStream fis = new FileInputStream(filename);
		
		byte account[] = new byte[9];
		byte first[] = new byte[10];
		byte middle[] = new byte[10];
		byte last[] = new byte[10];
		byte type[] = new byte[1];
		byte balance[] = new byte[10];
		byte pin[] = new byte[4];
		
		//read one account for now
		fis.read(account);
		fis.read(first);
		fis.read(middle);
		fis.read(last);
		fis.read(type);
		fis.read(balance);
		fis.read(pin);
		
		fis.close();
		
		String S1 = new String(account);
		//System.printString(S1);
		String S2 = new String(first);
		//System.printString(S2);
		String S3 = new String(middle);
		//System.printString(S3);
		String S4 = new String(last);
		//System.printString(S4);
		String S5 = new String(type);
		//System.printString(S5);
		String S6 = new String(balance);
		//System.printString(S6);
		String S7 = new String(pin);
		//System.printString(S7);
		
		//read into one account for now
		database[index].modifyAccount(S1, S2, S3, S4, S5, S6, S7);
    }
	
	public void WriteFile(String account, String first, String middle, String last, String type, String balance, String pin) 
	{
		String filename="BankAppWrite.dat";
		FileOutputStream fos = new FileOutputStream(filename);
		
		//write one account for now
		fos.write(account.getBytes());
		fos.write(first.getBytes());
		fos.write(middle.getBytes());
		fos.write(last.getBytes());
		fos.write(type.getBytes());
		fos.write(balance.getBytes());
		fos.write(pin.getBytes());
			
		fos.close();
    }
}
