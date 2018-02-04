package ida.ipl;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

import ibis.ipl.MessageUpcall;
import ibis.ipl.Registry;
import ibis.ipl.RegistryEventHandler;

import ibis.ipl.IbisCreationFailedException;
import java.lang.Runnable;

import java.io.IOException;
import java.util.Date;

import java.util.ArrayList;

// Class to maintain the solution for the puzzle
class puzzle{
	static int solutions = 0;

	synchronized public void solutionFound(int res, boolean flag){
		System.out.println("Solution Found : Solutions : res ::"+solutions+res);
		solutions += res;
	}

	/**
	 * expands this board into all possible positions, and returns the number of
	 * solutions. Will cut off at the bound set in the board.
	 */
	public static int solutions(Board board, BoardCache cache) {
		if (board.distance() == 0) {
			return 1;
		}

		if (board.distance() > board.bound()) {
			return 0;
		}

		Board[] children = board.makeMoves(cache);
		int result = 0;

		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				result += solutions(children[i], cache);
			}
		}
		cache.put(children);
		return result;
	}

	/**
	 * expands this board into all possible positions, and returns the number of
	 * solutions. Will cut off at the bound set in the board.
	 */
	public static int solutions(Board board) {
		if (board.distance() == 0) {
			return 1;
		}

		if (board.distance() > board.bound()) {
			return 0;
		}

		Board[] children = board.makeMoves();
		int result = 0;

		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				result += solutions(children[i]);
			}
		}
		return result;
	}

}

final class Ida implements MessageUpcall {

	/**
     * Port type used for receiving/sending board
     */
    PortType SendRequestBoardPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_EXPLICIT,
            PortType.CONNECTION_ONE_TO_ONE);

    /**
     * Port type used for receiving/sending ID of clients
     */
    PortType SendRequestIDPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_EXPLICIT,
            PortType.CONNECTION_MANY_TO_ONE, PortType.CONNECTION_DOWNCALLS);

    /**
     * Port type used for receiving/sending a reply from client
     */
    PortType SendRecvReplyPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_OBJECT,  PortType.RECEIVE_AUTO_UPCALLS, PortType.CONNECTION_MANY_TO_ONE, PortType.CONNECTION_DOWNCALLS
          );

    IbisCapabilities ibisCapabilities = new IbisCapabilities(
            IbisCapabilities.ELECTIONS_STRICT, IbisCapabilities.CLOSED_WORLD);

    private Ibis myIbis;
    IbisIdentifier ServerID;
    ReceivePort ReceiveID;
    ReceivePort ReceiveResult;

    static int PoolSize =0; // keep track of number of nodes joined IBIS
    static int CounterWork = 0;

    //Object for solving puzzle board
    puzzle solve_puzzle = new puzzle();  

    void multiple_board(Board board, BoardCache cache) throws Exception{

		if(board.distance() == 1){
			solve_puzzle.solutionFound(1,false);
			return;
		}

		if(board.distance() > board.bound()){
			return;
		}

		int PoolSize = myIbis.registry().getPoolSize();
		System.out.println("PoolSize :"+PoolSize);
		// only one node - Master
		if(PoolSize == 1){
			if (cache != null) {
				solve_puzzle.solutionFound(solve_puzzle.solutions(board,cache), false);
			}else{
				solve_puzzle.solutionFound(solve_puzzle.solutions(board), false);
			}
			return;
		}

		Board[] b = board.makeMoves();
		ArrayList<Board> moves = new ArrayList<Board>();
		for(int i=0;i<b.length;++i){
			if(b[i]!=null){
			moves.add(b[i]);
			}	
		}
		Board extraBoard;
		while( ( moves.size() <= (PoolSize*4) ) &&  moves.isEmpty() == false){

			extraBoard = moves.remove(0);

			if (extraBoard == null){
				continue;
			}
			// System.out.println(extraBoard);
			if(extraBoard.distance() == 1){
				solve_puzzle.solutionFound(1, false);
				continue;
			}else if (extraBoard.distance() > extraBoard.bound()){
				continue;
			}
			Board[] bb = extraBoard.makeMoves();
			ArrayList<Board> someextraBoard = new ArrayList<Board>();
			for(int i=0;i<bb.length;++i){
				someextraBoard.add(bb[i]);	
			}
			if (someextraBoard.isEmpty()) {
				break;
			}
			moves.addAll(someextraBoard);
		}

		// send boad work to client
		for( Board newBoard : moves){
			if(newBoard == null){
				continue;
			}
			if(newBoard.distance() == 1){
				solve_puzzle.solutionFound(1,false);
				continue;
			}else if(newBoard.distance() > newBoard.bound()){
				continue;
			}
			synchronized(this){
				CounterWork++;
			}

			// For sending board, get client ID 
			System.out.println("Server : Receive ID client");
			ReadMessage message = ReceiveID.receive();
			ReceivePortIdentifier requestor = (ReceivePortIdentifier) message.readObject();
			message.finish();

			// send board - connect to requestor's receive port
 			SendPort replyPort = myIbis.createSendPort(SendRequestBoardPortType);
			replyPort.connect(requestor);

			// Create a board reply message
			WriteMessage reply = replyPort.newMessage();
			reply.writeObject(newBoard);
			reply.finish();

			replyPort.close();
		}

		//wait till reply of every client 
		synchronized (this){
			while(CounterWork!=0){
				try{
					wait();
				}catch(Exception e){}
			}
		}
	}


	 void solve(Board board, boolean useCache) throws Exception {
		puzzle solve_puzzle = new puzzle();

		BoardCache cache = null;
		if (useCache) {
			cache = new BoardCache();
		}
		int bound = board.distance();
		//int solutions;

		System.out.print("Try bound ");
		System.out.flush();

		do {
			board.setBound(bound);

			System.out.print(bound + " ");
			System.out.flush();
			multiple_board(board,cache);
			bound+=2;
		} while (solve_puzzle.solutions == 0);
		offConnection();
		System.out.println("\nresult is " + solve_puzzle.solutions + " solutions of "
				+ board.bound() + " steps");

	}

	//Main server node -> client request handler
	public void upcall(ReadMessage m) throws IOException, ClassNotFoundException{
		int solutions = m.readInt();
		m.finish();
		if(solutions!=0){
			solve_puzzle.solutionFound(solutions,false);
		}
		synchronized(this){
			CounterWork--;
			notifyAll();
		}
	} 

	void offConnection() throws Exception{
		PoolSize--;
		//client wait for next result
		while(PoolSize!=0){
			ReadMessage rMessage = ReceiveID.receive();
			ReceivePortIdentifier requestor = (ReceivePortIdentifier)rMessage.readObject();
			rMessage.finish();

			SendPort sendBoard = myIbis.createSendPort(SendRequestBoardPortType);
			sendBoard.connect(requestor);

			WriteMessage sendMessage = sendBoard.newMessage();
			sendMessage.writeObject(null);
			sendMessage.finish();
			sendBoard.close();
			PoolSize--;
		}
	}

// Server
    private void server(String[] args) throws Exception {

    	String fileName = null;
		boolean cache = true;

		/* Use suitable default value. */
		int length = 103;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--file")) {
				fileName = args[++i];
			} else if (args[i].equals("--nocache")) {
				cache = false;
			} else if (args[i].equals("--length")) {
				i++;
				length = Integer.parseInt(args[i]);
			} else {
				System.err.println("No such option: " + args[i]);
				System.exit(1);
			}
		}

		Board initialBoard = null;

		if (fileName == null) {
			initialBoard = new Board(length);
		} else {
			try {
				initialBoard = new Board(fileName);
			} catch (Exception e) {
				System.err
						.println("could not initialize board from file: " + e);
				System.exit(1);
			}
		}
		System.out.println("Running IDA*, initial board:");
		System.out.println(initialBoard);

		//Receive Request from client
		ReceiveID = myIbis.createReceivePort(SendRequestIDPortType, "server");
		ReceiveID.enableConnections();

		ReceiveResult = myIbis.createReceivePort(SendRecvReplyPortType, "upcall", this);
		//enable connections
		ReceiveResult.enableConnections();
		//enable upcalls
		ReceiveResult.enableMessageUpcalls();

		myIbis.registry().waitUntilPoolClosed();
		PoolSize = myIbis.registry().getPoolSize(); // Maintain clients connected to server
		System.out.println("Solve call Server");
		long start = System.currentTimeMillis();
		solve(initialBoard, cache);
		long end = System.currentTimeMillis();

		// NOTE: this is printed to standard error! The rest of the output
		// is
		// constant for each set of parameters. Printing this to standard
		// error
		// makes the output of standard out comparable with "diff"
		System.err.println("ida took " + (end - start) + " milliseconds");
    }


	   //Client
	private void client(boolean ifcache) throws Exception{
		
		BoardCache cache = null;
		if(ifcache){
			cache = new BoardCache();
		}

		// Send client ID to server
		SendPort SendID = myIbis.createSendPort(SendRequestIDPortType);
		SendID.connect(ServerID, "server");

		//After connecting to Server, receive board
		ReceivePort ReceiveBoard = myIbis.createReceivePort(SendRequestBoardPortType, null);
		ReceiveBoard.enableConnections();

		while(true){
			//send Request message - Client ID to server
			WriteMessage request = SendID.newMessage();
			request.writeObject(ReceiveBoard.identifier());
        	request.finish();
        	
        	System.out.println("Client ID send");
        	
        	// Receive Board Work
        	ReadMessage work = ReceiveBoard.receive();
        	Board board = (Board)work.readObject();
        	work.finish();
        	
        	System.out.println(board);

        	if(board == null){
        		break;	
        	}

        	int sol = 0;
        	if(ifcache){
        		sol = solve_puzzle.solutions(board, cache);
        	}else{
        		sol = solve_puzzle.solutions(board);
        	}

        	// Send result back to the master node
        	SendPort sendResult = myIbis.createSendPort(SendRecvReplyPortType);
        	sendResult.connect(ServerID, "upcall");
        	WriteMessage resultMessage = sendResult.newMessage();
        	resultMessage.writeInt(sol);

        	resultMessage.finish();
        	sendResult.close();
		}
	}

		  /**
     * Constructor. Actually does all the work too :)
     */
    private void ClientServer(String[] args) throws Exception {
        // Create an ibis instance.
        // Notice createIbis uses varargs for its parameters.
       try{
        myIbis = IbisFactory.createIbis(ibisCapabilities, null,
                 SendRequestBoardPortType, SendRecvReplyPortType, SendRequestIDPortType);

        // Elect a server
        ServerID = myIbis.registry().elect("Server");

        // If I am the server, run server, else run client.
        if (ServerID.equals(myIbis.identifier())) {
            server(args);
        } else {
        	boolean cache = true; //bydefault keep it true

        	for(int i=0;i<args.length;i++){
        		if(args[i].equals("--nocache")){
        			cache = false;
        		}
        	}
            client(cache);
        }
        // End ibis.
        myIbis.end();
    	}
    	catch(IOException exc){
    		exc.printStackTrace();
    	}
    }

	public static void main(String[] args) {
		try {
            Ida ida_one = new Ida();
            ida_one.ClientServer(args);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
	}

}
