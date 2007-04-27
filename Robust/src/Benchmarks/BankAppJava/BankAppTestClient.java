// Bank App in Java

// Author: Danish Lakhani

import java.io.*;
import java.net.*;

class BankAppTestClient
{
	public static void main(String [] args)
		throws IOException
	{
		BufferedReader local_in = new BufferedReader(new InputStreamReader(System.in));
		String sendline;

		System.out.println("Client");

		sendline = local_in.readLine();
if (sendline == null)
	sendline = "localhost";
		System.out.println("Connecting to server...");
		Socket mySocket = new Socket(sendline, 8000);

		System.out.println("Connected!!");
		
		PrintWriter out = new PrintWriter(mySocket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
		

		
		while (true)
		{
			System.out.print("Send: ");
			sendline = local_in.readLine();

			if (!sendline.equals("no"))
			{
				out.println(sendline);
			}
			else
			{
				System.out.print("Reading: ");
				String inString = in.readLine();
				System.out.println(inString);
			}
		}
	}
}
