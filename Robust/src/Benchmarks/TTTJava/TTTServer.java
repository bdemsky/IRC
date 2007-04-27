import java.net.*;
import java.io.*;

public class TTTServer
{
	//the tictactoe game board
    //2d 3x3 char array
    private static char[][] board;
    //keeps track of how many turns have past
    private static int numOfTurns;
    //ints used to store the location of the cell the user will input
    private static int row;
    private static int col;
    private static boolean notDone;
    
    private static void resetGame()
    {
        numOfTurns = 0;
        row = 0;
        col = 0;
        
        for(int i = 0; i < 3; i++)
        {
            for(int j = 0; j < 3; j++)
            {
                board[i][j] = ' ';
            }
        }
    }
    
    private static void displayBoard()
    {
        System.out.println("--------------------");
        System.out.println("[R,C][ 1 ][ 2 ][ 3 ]");
        System.out.println("--------------------");
        System.out.println("[ 1 ]| " + board[0][0] + " |  " + board[0][1] +
                           " |  " + board[0][2] + " | ");
        System.out.println("--------------------");
        System.out.println("[ 2 ]| " + board[1][0] + " |  " + board[1][1] +
                           " |  " + board[1][2] + " | ");
        System.out.println("--------------------");
        System.out.println("[ 3 ]| " + board[2][0] + " |  " + board[2][1] +
                           " |  " + board[2][2] + " | ");
        System.out.println("--------------------");    
    }
    
    //put the move on the board and update numOfTurns
    private static void markMove(char xo)
    {
        board[row - 1][col - 1] = xo;
        numOfTurns++;
    }
    
    //check for a winner or a tie
    //true == winner or tie
    private static boolean checkWinner(char xo)
    {
        //horizontal win
        if(board[0][0] == xo && board[0][0] == board[0][1] &&
           board[0][1] == board[0][2])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //horizontal win
        else if(board[1][0] == xo && board[1][0] ==  board[1][1] &&
                board[1][1] == board[1][2])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //horizontal win
        else if(board[2][0] == xo && board[2][0] ==  board[2][1] &&
                board[2][1] == board[2][2])
        {
            System.out.println(xo + " is the winner!");
            return true;
        } 
        //vertial win
        else if(board[0][0] == xo && board[0][0] ==  board[1][0] &&
                board[1][0] == board[2][0])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //vertial win
        else if(board[0][1] == xo && board[0][1] ==  board[1][1] &&
                board[1][1] == board[2][1])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //vertial win
        else if(board[0][2] == xo && board[0][2] ==  board[1][2] &&
                board[1][2] == board[2][2])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //diagonal win
        else if(board[0][0] == xo && board[0][0] ==  board[1][1] &&
                board[1][1] == board[2][2])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //diagonal win
        else if(board[0][2] == xo && board[0][2] ==  board[1][1] &&
                board[1][1] == board[2][0])
        {
            System.out.println(xo + " is the winner!");
            return true;
        }
        //tie game
        //board is full
        else if(numOfTurns == 9)
        {
            System.out.println("Tie Game!");
            return true;            
        }
        //no winner yet
        else
            return false;
    }
    
    //the logic that happens for each turn for X or O
    public static void turnLogic(char xo, int r, int c)
    {
    	if(xo == 'X')
    	{
    		System.out.println("\n" + xo + "'s turn.");
    		System.out.println("Please enter your move as two separate integers: ");
    		//-1 -1 to quit
    		row = readInt();
    		col = readInt();
    		System.out.println("\nYou entered (" + row + "," + col + ")\n");
    	}
    	else if(xo == 'O')
    	{
    		System.out.println("\n" + xo + "'s turn.");
    		row = r;
    		col = c;
    	}
    	
        markMove(xo);
        displayBoard();

        //check for a winner and quit cond.
        if(checkWinner(xo) || (row == -1 && col == -1))
        	notDone = false;
    }
    
    public static void main(String[] args)
    {  
        //the sockets to be used
        ServerSocket mySocket = null;
        Socket myConnection= null;
        //string that will hold the message to be received and sent
        String myString = null;
        //input buffer
        BufferedReader myInput = null;
        //output buffer
        PrintStream myOutput = null;
        //loop variable
        notDone = true;
        
        board = new char[3][3];
        resetGame();
        
        //start server
        try
        {
            //create new socket
            mySocket = new ServerSocket(8080);
            System.out.println("Server Port: " + mySocket.getLocalPort());
            
            displayBoard();
            
            //accept incoming tcp connection
            myConnection = mySocket.accept();
            
            //create and read the message from the client to the input buffer
            myInput = new BufferedReader(new InputStreamReader(myConnection.getInputStream()));
            
            //create and print the message to the output buffer
            myOutput = new PrintStream(myConnection.getOutputStream());
            
            //loop for nine times at most
            while(numOfTurns != 9 && notDone == true)
            {
                switch(numOfTurns % 2)
                {
                    //even case
                    case 0:              
                        turnLogic('X', 0, 0);
                        myOutput.println(row + " " + col); 
                        break;
                        
                    //odd case
                    case 1:
                    	myString = myInput.readLine(); 
                        
                    	//was "quit" received?
                        if(myString.equals("quit"))
                        {
                            notDone = false;
                        }
                        else
                        {
                        	int r = Integer.parseInt(myString.substring(0, 1));
                        	int c = Integer.parseInt(myString.substring(2, 3));
                        	turnLogic('O', r, c);
                        }
                        break;
                        
                    //should not happen
                    default:
                        System.out.println("Program Error!");
                        break;
                }
            }
            
            //close buffers
            myInput.close();
            myOutput.close();
            
            //close socket
            myConnection.close();
        }
        catch(IOException e)
        {
            System.err.println(e);
            System.exit(1);
        }
    }
    
    //some good stuff to read user ints, maybe too much?
	public static int readInt() throws NumberFormatException
   	{
		String inputString = null;
	    inputString = readWord();
	    
      	return Integer.parseInt(inputString);
	}

	//read a lot 
 	public static String readWord()
    {
 		String result = "";
        char next;

        next = readChar();
       	while (Character.isWhitespace(next))
	    next = readChar();

	    while(!(Character.isWhitespace(next)))
	    {
	    	result = result + next;
        	next = readChar();
	    }

        if (next == '\r')
        {
        	next = readChar();
        	
            if (next != '\n')
            {
                System.err.println("Error.");
                System.exit(1);
            }
        }

        return result;
    }

 	//read one
 	public static char readChar()
    {
        int charAsInt = -1; 
        
        try
        {
            charAsInt = System.in.read();
        }
        catch(IOException e)
        {
            System.err.println(e);
            System.exit(1);
        }

        return (char)charAsInt;
    }
}
