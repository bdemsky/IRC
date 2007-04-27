// Bank App in Java

// Author: Danish Lakhani

// BankAppServer - Bank Application Server that services one client at a time

import java.io.*;
import java.net.*;
import java.util.*;

class BankAppClientTeller
{
	static int SUCCESS = 0;
	static int ERROR = 1;

	static int LOGIN_ACCOUNT = 1;
	static int LOGIN_PIN = 2;

	static int ACCOUNT_SAVINGS = 1;
	static int ACCOUNT_CHECKING = 2;	
	static int ACCOUNT_TELLER = 3;
	
	private Socket mySocket = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	private static int serverPort = 44444;

	public BankAppClientTeller()
	{
	}
	
	private void establishConnection()
		throws IOException
	{
		System.out.println("Connecting to server...");
		mySocket = new Socket("localhost", serverPort);
		out = new PrintWriter(mySocket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
		System.out.println("Connection Established!");
	}
	
	private void closeConnection()
		throws IOException
	{
		if (out != null)
			out.close();
		if (in != null)
			in.close();
		if (mySocket != null)
			mySocket.close();
	}

	private void displayMenu()
	{
		System.out.println("\nBankAppClientTeller");
		System.out.println("----------------");
		System.out.println("1. Login");
		System.out.println("2. Logout");
		System.out.println("3. Deposit");
		System.out.println("4. Withdraw");
		System.out.println("5. Check Balance");
		System.out.println("6. Open Account");
		System.out.println("7. Close Account");
		System.out.println("8. Modify Account");
		System.out.println("9. Check Account Info");
		System.out.println("0. Exit\n");
		System.out.print("Enter Choice: ");
		return;
	}
	
	private void startClient()
		throws IOException
	{
		int clientQuit = 0;
		int status = SUCCESS;
		int isConnected = 0;
		boolean loggedIn = false;
		BufferedReader local_in = new BufferedReader(new InputStreamReader(System.in));
		while (clientQuit == 0)
		{
			if (!loggedIn)
				establishConnection();
			isConnected = 1;
			while (isConnected == 1)
			{
				displayMenu();
				String input = local_in.readLine();
				int selection = Integer.parseInt(input);
				String response;
				switch (selection)
				{
					case 0:
						System.out.println("Exitting...");
						out.println("EXIT");
						System.exit(0);
						break;
					case 1:
						System.out.println("Login");
						out.println("LOGIN");
						response = in.readLine();
						if (response.equals("OK"))
						{
							System.out.print("Enter account number: ");
							String accountNumber = local_in.readLine();
							System.out.print("Enter PIN: ");
							String pinNumber = local_in.readLine();
							out.println(accountNumber);
							out.println(pinNumber);
							response = in.readLine();
							if (response.equals(accountNumber))
							{
								System.out.println("Login Successful! Account: " + response);
								loggedIn = true;
							}
							else
							{
								System.out.println(response);
							}					
						}
						else
						{
							System.out.println(response);
						}
						break;
					case 2:
						System.out.println("Logout");
						out.println("LOGOUT");
						response = in.readLine();
						if (response.equals("OK"))
						{
							response = in.readLine();
							System.out.println("Logout Successful! Account: " + response);
						}
						else
						{
							System.out.println(response);
						}
						break;
					case 3:
						System.out.println("Deposit");
						out.println("TELLERDEPOSIT");
						response = in.readLine();
						if (response.equals("OK"))
						{
							System.out.print("Enter Account Number: ");
							String accNum = local_in.readLine();
							out.println(accNum);
							response = in.readLine();
							if (!response.equals("OK"))
							{
								System.out.println(response);
								break;
							}
							System.out.print("Enter Deposit Amount: ");
							String depAmount = local_in.readLine();
							out.println(depAmount);
							response = in.readLine();
							if (response.equals("OK"))
							{
								response = in.readLine();
							}
						}
						System.out.println(response);
						break;
					case 4:
						System.out.println("Withdraw");
						out.println("TELLERWITHDRAW");
						response = in.readLine();
						if (response.equals("OK"))
						{
							System.out.print("Enter Account Number: ");
							String accNum = local_in.readLine();
							out.println(accNum);
							response = in.readLine();
							if (!response.equals("OK"))
							{
								System.out.println(response);
								break;
							}
							System.out.print("Enter Withdrawal Amount: ");
							String wdAmount = local_in.readLine();
							out.println(wdAmount);
							response = in.readLine();
							if (response.equals("OK"))
							{
								response = in.readLine();
							}
						}
						System.out.println(response);
						break;
					case 5:
						System.out.println("Check Balance");
						out.println("TELLERCHECK");
						response = in.readLine();
						if (response.equals("OK"))
						{
							System.out.print("Enter Account Number: ");
							String accNum = local_in.readLine();
							out.println(accNum);
							response = in.readLine();
							if (!response.equals("OK"))
							{
								System.out.println(response);
								break;
							}
							response = in.readLine();
						}
						System.out.println(response);
						break;
					case 6:
						System.out.println("Account Open");
						out.println("TELLEROPEN");
						response = in.readLine();
						if (!response.equals("OK"))
						{
							System.out.println(response);
							break;
						}
						System.out.print("Enter Account Number: ");
						out.println(local_in.readLine());
						System.out.print("Enter First Name: ");
						out.println(local_in.readLine());
						System.out.print("Enter Middle Name: ");
						out.println(local_in.readLine());
						System.out.print("Enter Last Name: ");
						out.println(local_in.readLine());
						System.out.print("Enter Account Type: ");
						out.println(local_in.readLine());
						System.out.print("Enter Initial Balance: ");
						out.println(local_in.readLine());
						System.out.print("Enter PIN: ");
						out.println(local_in.readLine());
						response = in.readLine();
						if (response.equals("OK"))
							response = in.readLine();
						System.out.println(response);
						break;
					case 7:
						System.out.println("Account Close");
						out.println("TELLERCLOSE");
						response = in.readLine();
						if (!response.equals("OK"))
						{
							System.out.println(response);
							break;
						}
						System.out.print("Enter Account Number: ");
						out.println(local_in.readLine());
						response = in.readLine();
						if (response.equals("OK"))
							response = in.readLine();
						System.out.println(response);
						break;
					case 8:
						System.out.println("Modify Account");
						out.println("TELLERMODIFY");
						response = in.readLine();
						if (!response.equals("OK"))
						{
							System.out.println(response);
							break;
						}
						System.out.print("Enter Account Number: ");
						String accNum = local_in.readLine();
						out.println(accNum);
						response = in.readLine();
						if (!response.equals("OK"))
						{
							System.out.println(response);
							break;
						}
						int done = 0;
						while (done == 0)
						{
							System.out.println("1. Change Name");
							System.out.println("2. Change Type");
							System.out.println("3. Change PIN");
							System.out.println("4. Change Balance");
							System.out.println("5. Done\n");
							System.out.print("Enter Choice: ");
							int choice = Integer.parseInt(local_in.readLine());
							switch (choice)
							{
								case 1:
									out.println("CHANGENAME");
									System.out.print("Enter New First Name: ");
									out.println(local_in.readLine());
									System.out.print("Enter New Middle Name: ");
									out.println(local_in.readLine());
									System.out.print("Enter New Last Name: ");
									out.println(local_in.readLine());
									response = in.readLine();
									if (!response.equals("OK"))
									{
										System.out.println(response);
									}
									break;
								case 2:
									out.println("CHANGETYPE");
									System.out.print("Enter New Account Type: ");
									out.println(local_in.readLine());
									response = in.readLine();
									if (!response.equals("OK"))
									{
										System.out.println(response);
									}								
									break;
								case 3:
									out.println("CHANGEPIN");
									System.out.print("Enter New PIN: ");
									out.println(local_in.readLine());
									response = in.readLine();
									if (!response.equals("OK"))
									{
										System.out.println(response);
									}
									break;
								case 4:
									out.println("CHANGEBALANCE");
									System.out.print("Enter New Balance: ");
									out.println(local_in.readLine());
									response = in.readLine();
									if (!response.equals("OK"))
									{
										System.out.println(response);
									}
									break;
								case 5:
									done = 1;
									out.println("DONE");
									break;
								default:
									System.out.println("Invalid selection");
									break;
							}
						}
						response = in.readLine();
						System.out.println(response);
						break;
					case 9:
						System.out.println("View Account");
						out.println("TELLERVIEW");
						response = in.readLine();
						if (!response.equals("OK"))
						{
							System.out.println(response);
							break;
						}
						System.out.print("Enter Account Number: ");
						String accNumber = local_in.readLine();
						out.println(accNumber);
						response = in.readLine();
						if (response.equals("OK"))
							response = in.readLine();
						System.out.println(response);
						break;
					default:
						System.out.println("Invalid Selection");
						break;
				}
				System.out.println("Press Enter to Continue...");
				local_in.readLine();
			}
		}
	}

	public static void main(String [] args)
		throws IOException
	{
		System.out.println("BankAppClientTeller in Java");
		BankAppClientTeller client = new BankAppClientTeller();
		client.startClient();
	}
}
