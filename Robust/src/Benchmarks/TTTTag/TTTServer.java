/* Startup object is generated with the initialstate flag set by the
 *  system to start the computation up */

// Create ServerSocket
task Startup(StartupObject s {initialstate}) {
	System.printString("TTT Server Starting...\n");
	ServerSocket ss = new ServerSocket(8000);
	System.printString("Creating ServerSocket\n");
	Board tttBoard = new Board() {init};
	taskexit(s {!initialstate}); // Turn off initial state flag
}

//Listen for a request and accept request 
task AcceptConnection(ServerSocket ss{SocketPending}) {
	System.printString("Waiting for connection...\n");
	tag t=new tag(connect);
	TTTServerSocket ttts = new TTTServerSocket() {TTTSInitialize}{t};
	System.printString("Calling accept...\n");
	ss.accept(t);
	System.printString("Connected...\n");
}

// Process incoming requests
task ProcessRequest(TTTServerSocket ttts{TTTSInitialize}{connect l}, Socket s{IOPending}{connect l}) {
	System.printString("Request received...");
	int action = ttts.receive(s);
	if (action == 1) { // Make move
		taskexit(ttts {MakeMove});
	}
	else { // Send Error
		taskexit(ttts {SendError});
	}
}

task ProcessMove(TTTServerSocket ttts{MakeMove}, Board tttBoard{init}) {
	System.printString("Processing player's move...");
	int result = tttBoard.makeMove(ttts.getRow(), ttts.getCol());
	if (result == 1) { //Move made, send board display
		taskexit(ttts {!MakeMove, SendBoard});
	}
	else if (result == 2) { //Move made, game over
		taskexit(ttts {!MakeMove, SendDone});
	}
	else {// Error
		taskexit(ttts {!MakeMove, SendError});
	}
}

task SendBoardDisplay(TTTServerSocket ttts{SendBoard}{connect l}, Board tttBoard{init}, Socket s{}{connect l}) {
	ttts.sendBoardDisplay(tttBoard, s);
}

task GameOver(TTTServerSocket ttts{SendDone}{connect l}, Board tttBoard{init}, Socket s{}{connect l}) {
	ttts.sendDone(tttBoard.winner(), s);
}

task SendErrorMessage(TTTServerSocket ttts{SendError}{connect l}, Board tttBoard{init}, Socket s{}{connect l}) {
	ttts.sendError(s);
}
