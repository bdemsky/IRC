// Bank App in Java

// Author: Danish Lakhani

// BankAppServer - Bank Application Server that services one client at a time

import java.io.*;
import java.net.*;
import java.util.*;

class BankAppServer
{
	static int SUCCESS = 0;
	static int ERROR = 1;

	static int LOGIN_ACCOUNT = 1;
	static int LOGIN_PIN = 2;

	static int ACCOUNT_SAVINGS = 1;
	static int ACCOUNT_CHECKING = 2;	
	static int ACCOUNT_TELLER = 3;
	
	private ServerSocket serverSocket = null;
	private Socket clientSocket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	private static int serverPort = 44444;
	private AccountDatabase accounts = null;
	
	private boolean isLoggedIn = false;
	private Integer activeAccount = 0;
	private Integer tellerCode = 0;

	public BankAppServer()
	{
//		initializeServer();
	}

	private void initializeServer()
	{
		//Initialize Database
		accounts = new AccountDatabase();

		//Initialize Server Socket
		System.out.print("Creating Server Socket...");
		try {
			serverSocket = new ServerSocket(serverPort);
		} catch (IOException e) {
			System.out.println("Cannot listen on port " + serverPort);
			System.exit(-1);
		}
		System.out.println("Done");	
	}

	private void establishConnection()
		throws IOException
	{
			System.out.print("Waiting for connection...");
			try {
				clientSocket = serverSocket.accept();
			} catch (IOException e) {
				System.out.println("Accept failed");
				System.exit(-1);
			}
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			System.out.println("Connection Established!");
	}

	private int authenticateUser(Integer accountNumber, Integer pin)
	{
		if (!accounts.accountExists(accountNumber))
			return LOGIN_ACCOUNT;
		if (accounts.getPin(accountNumber) != pin)
			return LOGIN_PIN;
		return SUCCESS;
	}

	private int login()
		throws IOException
	{
		out.println("OK");
		Integer accountNumber = new Integer(in.readLine());
		System.out.println("Account number: " + accountNumber);
		Integer pin = new Integer(in.readLine());
		System.out.println("PIN: " + pin);
		System.out.println("Authenticating...");
		int status = authenticateUser(accountNumber, pin);
		if (status == SUCCESS)
		{
			out.println(accountNumber);
			isLoggedIn = true;
			tellerCode = 0;
			activeAccount = 0;
			if (accounts.isTeller(accountNumber))
				tellerCode = accountNumber;
			else
				activeAccount = accountNumber; 
			System.out.println("Logged Success");
			return SUCCESS;
		}
		else {
			if (status == LOGIN_ACCOUNT)
				out.println("ERROR: login failed: Account " + accountNumber + " does not exist.");
			else
				out.println("ERROR: login failed: Incorrect pin.");
		}			
		System.out.println("Login Failed");
		return ERROR;
	}

	private void closeConnection()
		throws IOException
	{
		out.close();
		in.close();
		clientSocket.close();
//		serverSocket.close();
	}

	private void processDeposit(Integer accountNumber) throws IOException
	{
		String inVal;
		if (!accounts.accountExists(accountNumber))
		{
			out.println("ERROR: Account " + accountNumber + " not found.");
			return;
		}

		out.println("OK");
				
		//Get Deposit Amount
		inVal = in.readLine();
		Double depAmount = new Double(inVal);
		if (depAmount <= 0)
		{
			out.println("ERROR: Negative or zero deposit amount");
			return;
		}					

		accounts.deposit(accountNumber, depAmount);
		out.println("OK");
		out.println("$" + depAmount + " deposited successfully! Account: " + accountNumber + " New Balance: " + accounts.getBalance(accountNumber));
		return;
	}
	
	private void processWithdrawal(Integer accountNumber) throws IOException
	{
		String inVal;
		if (!accounts.accountExists(accountNumber))
		{
			out.println("ERROR: Account " + accountNumber + " not found.");
			return;
		}

		out.println("OK");

		//Get withdrawal amount
		inVal = in.readLine();
		Double wdAmount = new Double(inVal);
		if (wdAmount <= 0)
		{
			out.println("ERROR: Negative or zero withdrawal amount");
			return;
		}
//		else if (wdAmount > accounts.getBalance(accountNumber))
//		{
//			out.println("ERROR: Insufficient funds. Balance = " + accounts.getBalance(accountNumber));
//			return;
//		}

		accounts.withdraw(accountNumber, wdAmount);
		out.println("OK");
		out.println("$" + wdAmount + " withdrawn successfully! Account: " + accountNumber + " New Balance: " + accounts.getBalance(accountNumber));
		return;
	}
	
	private void processBalanceCheck(Integer accountNumber)
	{
		out.println("OK");
		out.println("Account: " + accountNumber + " Balance: " + accounts.getBalance(accountNumber));
		return;
	}

	private void startServer()
		throws IOException
	{
		int serverQuit = 0;
		int status = SUCCESS;
		int isConnected = 0;
		initializeServer();
		while (serverQuit == 0)
		{		
			establishConnection();
			isConnected = 1;
			accounts.loadDatabase();
			while (isConnected == 1)
			{
				System.out.println("Waiting for request...");
				// Wait for requests
				String request = in.readLine();					
				if (request == null)
					continue;

				System.out.println("Request: " + request);
				
				// Service requests				
				if (request.equals("EXIT"))
				{
					accounts.storeDatabase();
					isLoggedIn = false;
					activeAccount = 0;
					tellerCode = 0; 
					closeConnection();
					isConnected = 0;
					continue;
				}

				if (request.equals("LOGIN"))
				{
					if (isLoggedIn)
					{
						out.println("ERROR: Already logged in. Please logout.");
						continue;
					}
					status = login();
					if (status == ERROR)
					{
//						isConnected = 0;
						continue;
					}
				}

				if (!isLoggedIn)
				{
					out.println("ERROR: Not logged in");
					continue;
				}
				
				if (request.equals("LOGOUT"))
				{
					out.println("OK");
					if (tellerCode == 0)
						out.println(activeAccount);
					else
						out.println(tellerCode);
					accounts.storeDatabase();
					isLoggedIn = false;
					activeAccount = 0;
					tellerCode = 0; 
//					closeConnection();
//					isConnected = 0;
				}
				
				if (request.equals("DEPOSIT"))
				{
					processDeposit(activeAccount);
				}
				
				if (request.equals("WITHDRAW"))
				{
					processWithdrawal(activeAccount);
				}
				
				if (request.equals("CHECK"))
				{
					processBalanceCheck(activeAccount);
				}
				
				if (request.equals("TELLERDEPOSIT"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer acc = new Integer(in.readLine());
					processDeposit(acc);
				}
				
				if (request.equals("TELLERWITHDRAW"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer acc = new Integer(in.readLine());
					processWithdrawal(acc);
				}
				
				if (request.equals("TELLERCHECK"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer acc = new Integer(in.readLine());
					processBalanceCheck(acc);
				}
				
				if (request.equals("TELLEROPEN"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer accNum = new Integer(in.readLine());
					String fName = in.readLine();
					String mName = in.readLine();
					String lName = in.readLine();
					Integer accType = new Integer(in.readLine());
					Double bal = new Double(in.readLine());
					Integer pNum = new Integer(in.readLine());
					status = accounts.openAccount(accNum, fName, mName, lName, accType, bal, pNum);
					if (status == ERROR)
					{
						out.println("ERROR: Account " + accNum + " already exists.");
						continue;
					}
					out.println("OK");
					out.println("Account Number: " + accNum + " " +
									"Customer Name: " + fName + " " + mName + " " + lName + " " +
									"Account Type: " + ((accType == ACCOUNT_SAVINGS)?"SAVINGS":(accType == ACCOUNT_CHECKING)?"CHECKING":"TELLER") + " " +
									"Balance: $" + bal + " " +
									"PIN: " + pNum + " ");
				}
				
				if (request.equals("TELLERCLOSE"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer accNum = new Integer(in.readLine());
					status = accounts.closeAccount(accNum);
					if (status == ERROR)
					{
						out.println("ERROR: Account " + accNum + " does not exist.");
						continue;
					}
					out.println("OK");
					out.println("Account " + accNum + " closed successfully");					
				}
				
				if (request.equals("TELLERMODIFY"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer accNum = new Integer(in.readLine());
					if (!accounts.accountExists(accNum))
					{
						out.println("ERROR: Account " + accNum + " does not exist.");
						continue;
					}
					out.println("OK");
					String inVal;
					while (!(inVal = in.readLine()).equals("DONE"))
					{
						if (inVal.equals("CHANGENAME"))
						{
							String fName = in.readLine();
							String mName = in.readLine();
							String lName = in.readLine();
							accounts.modifyName(accNum, fName, mName, lName);
							out.println("OK");
						}
						else if (inVal.equals("CHANGETYPE"))
						{
							Integer newType = new Integer(in.readLine());
							if (newType.intValue() < 1 || newType.intValue() > 3)
							{
								out.println("ERROR: Invalid account type: " + newType + ". Must be 1-3.");
								continue;
							}
							accounts.modifyType(accNum, newType);
							out.println("OK");
						}
						else if (inVal.equals("CHANGEPIN"))
						{
							Integer newPin = new Integer(in.readLine());
							if ((newPin < 0) || (newPin > 9999))
							{
								out.println("ERROR: Invalid pin " + newPin + ". Must be 0000-9999.");
								continue;
							}
							accounts.modifyPin(accNum, newPin);
							out.println("OK");
						}
						else if (inVal.equals("CHANGEBALANCE"))
						{
							Double newBal = new Double(in.readLine());
							accounts.modifyBalance(accNum, newBal);
							out.println("OK");
						}
					}
					out.println("Account Number: " + accNum + " " +
									"Customer Name: " + accounts.nameString(accNum) + " " +
									"Account Type: " + accounts.typeString(accNum) + " " +
									"Balance: $" + accounts.getBalance(accNum) + " " +
									"PIN: " + accounts.getPin(accNum) + " ");				
				}
				
				if (request.equals("TELLERVIEW"))
				{
					if (tellerCode == 0)
					{
						out.println("ERROR: Teller not logged in");
						continue;
					}
					out.println("OK");
					Integer accNum = new Integer(in.readLine());
					if (!accounts.accountExists(accNum))
					{
						out.println("ERROR: Account " + accNum + " does not exist.");
						continue;
					}
					out.println("OK");
					out.println("Account Number: " + accNum + " " +
									"Customer Name: " + accounts.nameString(accNum) + " " +
									"Account Type: " + accounts.typeString(accNum) + " " +
									"Balance: $" + accounts.getBalance(accNum) + " " +
									"PIN: " + accounts.getPin(accNum) + " ");				
				}
			}
		}
	}

	public static void main(String [] args)
		throws IOException
	{
		System.out.println("BankAppServer in Java");
		BankAppServer server = new BankAppServer();
		server.startServer();
	}
}

class AccountEntry
{
	Integer accountNumber;
	String firstName;
	String middleName;
	String lastName;
	Integer accountType;
	Double balance;
	Integer pin;

	public AccountEntry(Integer accNum, String fName, String mName, String lName, Integer accType, Double bal, Integer pNum)
	{
		accountNumber = accNum;
		firstName = fName;
		middleName = mName;
		lastName = lName;
		accountType = accType;
		balance = bal;
		pin = pNum;		
	}
}


class AccountDatabase
{
	static int ACCOUNT_SAVINGS = 1;
	static int ACCOUNT_CHECKING = 2;	
	static int ACCOUNT_TELLER = 3;
	static int SUCCESS = 0;
	static int ERROR = 1;

	static String dbfilename = "accts.txt";

	Vector<AccountEntry> entries = null;

	public AccountDatabase()
	{
		entries = new Vector<AccountEntry>();
	}

	public void loadDatabase()
	{
		entries.removeAllElements();
		try {
			BufferedReader fin = new BufferedReader(new FileReader(dbfilename));
			String str;
			while ((str = fin.readLine()) != null)
			{
				Integer accNum = new Integer(str);
				String fName = fin.readLine();
				String mName = fin.readLine();
				String lName = fin.readLine();
				Integer accType = new Integer(fin.readLine());
				Double bal = new Double(fin.readLine());
				Integer pNum = new Integer(fin.readLine());
				AccountEntry newEntry = new AccountEntry(accNum, fName, mName, lName, accType, bal, pNum);
				entries.add(newEntry);				
			}
			fin.close();
		} catch (IOException e) {
			System.out.println("Cannot open database file");
			System.exit(-1);
		}
		printAccounts();
	}

	public void storeDatabase()
	{
		try {
			BufferedWriter fout = new BufferedWriter(new FileWriter(dbfilename));
			for (int i = 0; i < entries.size(); i++)
			{
				AccountEntry acc = (AccountEntry)entries.elementAt(i);
				fout.write(acc.accountNumber.toString());
				fout.newLine();
				fout.write(acc.firstName);
				fout.newLine();
				fout.write(acc.middleName);
				fout.newLine();
				fout.write(acc.lastName);
				fout.newLine();
				fout.write(acc.accountType.toString());
				fout.newLine();
				fout.write(acc.balance.toString());
				fout.newLine();
				fout.write(acc.pin.toString());
				fout.newLine();
			}
			fout.close();
		} catch (IOException e) {
			System.out.println("Cannot write to database file");
			System.exit(-1);
		}	
	}

	public AccountEntry getAccount(Integer accNum)
	{
		for (int i = 0; i < entries.size(); i++)
		{
			AccountEntry acc = (AccountEntry)entries.elementAt(i);
			if (acc.accountNumber.equals(accNum))
				return acc;
		}
		return null;
	}
	
	public void deposit(Integer accNum, Double amount)
	{
		AccountEntry acc = getAccount(accNum);
		acc.balance += amount;
	}
	
	public void withdraw(Integer accNum, Double amount)
	{
		AccountEntry acc = getAccount(accNum);
		acc.balance -= amount;
	}
	
	public Double getBalance(Integer accNum)
	{
		AccountEntry acc = getAccount(accNum);
		return acc.balance;
	}

	public int getPin(Integer accNum)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
			return acc.pin.intValue();
		return -1;
	}

	public boolean accountExists(Integer accNum)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
			return true;
		return false;
	}
	
	public boolean isTeller(Integer accNum)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc.accountType.equals(ACCOUNT_TELLER))
			return true;
		return false;
	}

	public Integer openAccount(Integer accNum, String fName, String mName, String lName, Integer accType, Double bal, Integer pNum)
	{
		if (accountExists(accNum))
			return ERROR;
		AccountEntry acc = new AccountEntry(accNum, fName, mName, lName, accType, bal, pNum);
		entries.add(acc);
		return SUCCESS;
	}
	
	public Integer closeAccount(Integer accNum)
	{
		if (accountExists(accNum))
		{
			AccountEntry acc = getAccount(accNum);
			entries.remove(acc);
			return SUCCESS;
		}
		else
			return ERROR;
	}
	
	public String nameString(Integer accNum)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
		{
			return (acc.firstName + " " + acc.middleName + " " + acc.lastName);
		}
		return "";		
	}
	
	public String typeString(Integer accNum)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
		{
			return ((acc.accountType == ACCOUNT_SAVINGS)?"SAVINGS":(acc.accountType == ACCOUNT_CHECKING)?"CHECKING":"TELLER");
		}
		return "";		
	}
	
	public void modifyName(Integer accNum, String fName, String mName, String lName)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
		{
			acc.firstName = fName;
			acc.middleName = mName;
			acc.lastName = lName;
		}
		return;	
	}

	public void modifyType(Integer accNum, Integer newType)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
		{
			acc.accountType = newType;
		}
		return;	
	}

	public void modifyPin(Integer accNum, Integer newPin)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
		{
			acc.pin = newPin;
		}
		return;	
	}
	
	public void modifyBalance(Integer accNum, Double newBal)
	{
		AccountEntry acc = getAccount(accNum);
		if (acc != null)
		{
			acc.balance = newBal;
		}
		return;		
	}
	
	public void printAccounts()
	{
		System.out.println("entries.size = " + entries.size());
		for (int i = 0; i < entries.size(); i++)
		{
			System.out.println("Entry " + i);
			AccountEntry acc = entries.elementAt(i);
			System.out.println("1 " + acc.accountNumber.toString());
			System.out.println("2 " + acc.firstName);
			System.out.println("3 " + acc.middleName);
			System.out.println("4 " + acc.lastName);
			System.out.println("5 " + acc.accountType.toString());
			System.out.println("6 " + acc.balance.toString());
			System.out.println("7 " + acc.pin.toString());
		}
	}
}
