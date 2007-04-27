//Banking Application Server

/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

task Startup(StartupObject s{initialstate})
{
    System.printString("Starting\n");
    ServerSocket ss = new ServerSocket(8080);
    System.printString("Creating ServerSocket\n");
	BankDatabase Bank = new BankDatabase(){DatabaseInit};
    taskexit(s{!initialstate}); /* Turns initial state flag off, so this task won't refire */
}

task AcceptConnection(ServerSocket ss{SocketPending})
{
    BankAppSocket bas = new BankAppSocket(){BASocketInit};
	ss.accept(bas);
    System.printString("Connected\n");
}

//i think this task could probably be broken up into smaller tasks
task ProcessRequest(BankAppSocket bas{IOPending && BASocketInit}, BankDatabase Bank{DatabaseInit})
{
	String message = new String(bas.receive());
	//System.printString(message);
	
	//login
	if(message.startsWith("1"))
	{
		String account = message.subString(1, 10);
		String pin = message.subString(10, 14);
		
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account) && Bank.database[i].PIN.equals(pin))
			{
				bas.send("Login OK");
				//System.printString("Login OK");
			}
			else
			{
				bas.send("Login Error");
				//System.printString("Login Error");
			}
		}
	}
	//logout
	else if(message.startsWith("2"))
	{
		String account = message.subString(1, 10);
		
		//find the account
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
			{
				bas.send("Logout OK");
				//System.printString("Logout OK");
			}
			else
			{
				bas.send("Logout Error");
				//System.printString("Logout Error");
			}
		}
	}
	//create
	else if(message.startsWith("3"))
	{
		String account = message.subString(1, 10);
		String first = message.subString(10, 20);
		String middle = message.subString(20, 30);
		String last = message.subString(30, 40);
		String type = message.subString(40, 41);
		String balance = message.subString(41, 51);
		String pin = message.subString(51, 55);
		
		//find first empty space
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals("@@@@@@@@@"))
				id = i;
		}
		
		if(id != -1)
		{
			//should check for input errors first but...
			Bank.database[id].AccountNumber = first;
			Bank.database[id].FirstName = middle;
			Bank.database[id].MiddleName = last;
			Bank.database[id].LastName = last;
			Bank.database[id].AccountType = type;
			Bank.database[id].Balance = balance;
			Bank.database[id].PIN = pin;
		
			Bank.numOfAccounts++;
		
			bas.send(Bank.database[id].AccountNumber);
			//System.printString(Bank.database[id].AccountNumber);
		}
		else
		{
			bas.send("Create Error");
			//System.printString("Create Error");
		}
	}
	//delete
	else if(message.startsWith("4"))
	{
		String account = message.subString(1, 10);
		
		//find the account
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
				id = i;
		}
		
		if(id != -1)
		{
			Bank.database[id].AccountNumber = "@@@@@@@@@@";
			Bank.database[id].FirstName = "@@@@@@@@@@";
			Bank.database[id].MiddleName = "@@@@@@@@@@";
			Bank.database[id].LastName = "@@@@@@@@@@";
			Bank.database[id].AccountType = "@";
			Bank.database[id].Balance = "@@@@@@@@@@";
			Bank.database[id].PIN = "@@@@";
			Bank.numOfAccounts--;
			
			bas.send("Close Account OK");
			//System.printString("Close Account OK");
		}
		else
		{
			bas.send("Close Account Error");
			//System.printString("Close Account Error");
		}
	}
	//modify
	else if(message.startsWith("5"))
	{
		String account = message.subString(1, 10);
		String field = message.subString(10, 11);
		//two digits 00-99
		String numBytes = message.subString(11, 13);
		String data = message.subString(13, 13 + Integer.parseInt(numBytes));
		
		//find the account
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
				id = i;
		}
		
		if(id != -1)
		{
			//maybe shouldn't allow changes to some of these fields
			if(field.equals("1"))
			{
				Bank.database[id].AccountNumber = data;
			}
			else if(field.equals("2"))
			{
				Bank.database[id].FirstName = data;
			}
			else if(field.equals("3"))
			{
				Bank.database[id].MiddleName = data;
			}
			else if(field.equals("4"))
			{
				Bank.database[id].LastName = data;
			}
			else if(field.equals("5"))
			{
				Bank.database[id].AccountType = data;
			}
			else if(field.equals("6"))
			{
				Bank.database[id].Balance = data;
			}
			else if(field.equals("7"))
			{
				Bank.database[id].PIN = data;
			}
			
			bas.send("Modify OK");
			//System.printString("Modify OK");
		}
		else
		{
			bas.send("Modify Error");
			//System.printString("Modify Error");
		}
	}
	//check account info
	else if(message.startsWith("6"))
	{
		String account = message.subString(1, 10);
		
		//find the account
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
				id = i;
		}
		
		if(id != -1)
		{
			StringBuffer strBuffer = new StringBuffer(Bank.database[id].AccountNumber);
			strBuffer.append(Bank.database[id].FirstName);
			strBuffer.append(Bank.database[id].MiddleName);
			strBuffer.append(Bank.database[id].LastName);
			strBuffer.append(Bank.database[id].AccountType);
			strBuffer.append(Bank.database[id].Balance);
			strBuffer.append(Bank.database[id].PIN);
		
			bas.send(strBuffer.toString());
			//System.printString(strBuffer.toString());
		}
		else
		{
			bas.send("Check Account Info Error");
			//System.printString("Check Account Info Error");
		}
	
	}
	//deposit
	//more string operations or a Float Object could be useful here 
	else if(message.startsWith("7"))
	{
		String account = message.subString(1, 10);
		//two digits 00-99
		//dollar part only
		String numBytes = message.subString(10, 12);
		//get dollars
		String data = message.subString(12, 12 + Integer.parseInt(numBytes));
			
		
		//find the account
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
				id = i;
		}
		
		if(id != -1)
		{	
			Integer sum = new Integer(Integer.parseInt(Bank.database[id].Balance) + Integer.parseInt(data));
			
			StringBuffer sumBuffer = new StringBuffer(sum.toString());
			
			int padding = 10 - sumBuffer.length();
			
			for(int i = 0; i < padding; i++)
			{
				sumBuffer.append("@");
			}
			
			//assumes no overflow
			Bank.database[id].Balance = sumBuffer.toString();
			
			bas.send("Deposit OK");
			//System.printString("Deposit OK");
		}
		else
		{
			bas.send("Deposit Error");
			//System.printString("Deposit Error");
		}
	}
	//withdraw
	else if(message.startsWith("8"))
	{
		String account = message.subString(1, 10);
		//two digits 00-99
		//dollar part only
		String numBytes = message.subString(10, 12);
		//get dollars
		String data = message.subString(12, 12 + Integer.parseInt(numBytes));
		
		//find the account
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
				id = i;
		}
		
		if(id != -1)
		{
			Integer difference = new Integer(Integer.parseInt(Bank.database[id].Balance) - Integer.parseInt(data));
			
			if(difference.intValue() >= 0)
			{
				StringBuffer difBuffer = new StringBuffer(difference.toString());
			
				int padding = 10 - difBuffer.length();
			
				for(int i = 0; i < padding; i++)
				{
					difBuffer.append("@");
				}
			
				//assumes no overflow
				Bank.database[id].Balance = difBuffer.toString();
				
				bas.send("Withdraw OK");
				//System.printString("Withdraw OK");
			}
			else
			{
				bas.send("Overdraw Error");
				//System.printString("Overdraw Error");
			}
		}
		else
		{
			bas.send("Withdraw Error");
			//System.printString("Withdraw Error");
		}
	}
	//check balance
	else if(message.startsWith("9"))
	{
		String account = message.subString(1, 10);
					
		int id = -1;
		for(int i = 0; i < Bank.numOfAccounts; i++)
		{
			if(Bank.database[i].AccountNumber.equals(account))
				id = i;
		}
		
		if(id != -1)
		{
			bas.send(Bank.database[id].Balance);
			//System.printString(Bank.database[id].Balance);
		}
		else
		{
			bas.send("Check Balance Error");
			//System.printString("Check Balance Error");
		}
	}
	else
	{
		bas.send("Message Error");
		//System.printString("Message Error");
	}
}
