public class BankAccount
{
	//can't init here, won't compile, do it in the constructor
	
	//nine digits
	String AccountNumber; //field #1
	
	//account owner's name
	//always 10 chars
	//pad with @
	String FirstName; //field #2 
	String MiddleName; //field #3
	String LastName; //field #4
	
	//1  == Savings
	//2 == Checking
	//3 == Teller
	String AccountType; //field #5
	
	//ints only, should use floats in the future
	//1234567890
	//assumes balance does is never negative
	//always 10 chars
	//pad with @
	String Balance; //field #6
	
	//four digits
	String PIN; //field #7
	
	public BankAccount()
	{
	
	}
	
	public BankAccount(String account, String first, String middle, String last, String type, String balance, String pin)
	{
		if(account != null)
			AccountNumber = account;
		if(first != null)	
			FirstName = first;
		if(middle != null)
			MiddleName = middle;
		if(last != null)
			LastName = last;
		if(type != null)
			AccountType = type;
		if(balance != null)
			Balance = balance;
		if(pin != null)
			PIN = pin;
	}
	
	public void modifyAccount(String account, String first, String middle, String last, String type, String balance, String pin)
	{
		if(account != null)
			AccountNumber = account;
		if(first != null)	
			FirstName = first;
		if(middle != null)
			MiddleName = middle;
		if(last != null)
			LastName = last;
		if(type != null)
			AccountType = type;
		if(balance != null)
			Balance = balance;
		if(pin != null)
			PIN = pin;
	}
}
