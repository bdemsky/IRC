public class TTTServerSocket {
	// TTTServerSocket flags
	flag ReceiveRequest;

	flag MakeMove;
	flag SendError;
	flag SendBoard;
	flag SendDone;
	
	String request;
	int row, col;
		
	//Constructor
	public TTTServerSocket(){
		System.printString("Constructing TTTServerSocket....\n");
	}

	public int receive(Socket s)
	{
		byte b1[] = new byte[1024];
		s.read(b1);
		request = new String(b1);
		System.printString("request: ");
		System.printString(request);
		if (parseTransaction() == 1) {
			System.printString(request);
			return 1;
		}
		System.printString("Error receiving...\n");
		return 0;
	}

	// Parse request
	public int parseTransaction(){
		int start = request.indexOf('_');
		//System.printString("start parse");
		String s = request.subString(start+1);
		//System.printString("before checking the string");
//_move:3:3
		if (s.startsWith("move")==true){
			//Get row
			int i1 = s.indexOf(':');
			String rowStr = new String(s.subString(i1+1, i1+2));
			row = Integer.parseInt(rowStr);
			
			//System.printString("row");

			//Get col
			String s2 = new String(s.subString(i1+2));
			int i2 = s2.indexOf(':');
			String colStr = new String(s2.subString(i2+1, i2+2));
			col = Integer.parseInt(colStr);
			return 1;
			
			
			
		}
		// Error transaction
		return -1;
	}
	
	public int getRow(){
		return row;
	}
	public int getCol(){
		return col;
	}
	
	public void sendBoardDisplay(Board theBoard, Socket s) {
		StringBuffer line1 = new StringBuffer("\n\n");
		
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				if (theBoard.board[i][j] == 1)
					line1.append("X");
				else if (theBoard.board[i][j] == 2)
					line1.append("O");
				else
					line1.append("-");
			}
			line1.append("\n");
		}
		String towrite = new String(line1);
		s.write(towrite.getBytes());
		return;
	}
	
	public void sendDone(int winner, Socket s) {
		StringBuffer line1 = new StringBuffer ("done_");
		if (winner == 0)
			line1.append("tie");
		else if (winner == 1)
			line1.append("player");
		else
			line1.append("computer");
			
		String towrite = new String(line1);
		s.write(towrite.getBytes());
		return;
	}
	
	public void sendError(Socket s) {
		StringBuffer line1 = new StringBuffer ("error_wrongmove");
			
		String towrite = new String(line1);
		s.write(towrite.getBytes());
		return;
	}
}
