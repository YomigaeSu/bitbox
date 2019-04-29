package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.sql.ClientInfoStatus;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;
//import javax.swing.text.Document;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Document;

public class Peer {
	private static Logger log = Logger.getLogger(Peer.class.getName());

	// Read local hostPort from configuration.properties
	// Hard coded peer address for test purpose
	// Peer1 settings: port 8111; peerPort 8112
	// Peer2 settings: port 8112; peerPort 8111
	private static String ip = Configuration.getConfigurationValue("advertisedName");
	private static int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
	private static String firstPeerIp = "localhost";
	private static int firstPeerPort = Integer
			.parseInt(Configuration.getConfigurationValue("peers").split(",")[0].split(":")[1]);
	
	private static int maximumIncommingConnections = Integer
			.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	private static ArrayList<HostPort> connectedPeers = new ArrayList<>();

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ServerMain ser = new ServerMain();
		
		client(firstPeerIp, firstPeerPort, ser);
		new Thread(() -> listening(ser)).start();
	}
	
	// ========================== to build connection ==================================================
	public static void client(String peerIp, int peerPort, ServerMain ser) {
		Socket socket = builtSocket(peerIp, peerPort);
		if ((socket != null) && (!socket.isClosed())) {
			clientRunning(socket, ser);
		}
	}
	
	public static Socket builtSocket(String peerIp, int peerPort) {
		Socket socket = null;
		boolean no_connected = true;
		try {
			socket = new Socket(peerIp, peerPort);
			System.out.println("Working as clientPeer: Try connecting to " + peerIp + ":" + peerPort);

			// Get the input/output streams for reading/writing data from/to the socket
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());

			// out.writeUTF("Peer hosting on" + ip + ":" + port);
			// out.flush();
			// Send a HANDSHAKE_REQUEST to another peer by writing to the socket output
			// stream
			Document newCommand = new Document();
			newCommand.append("command", "HANDSHAKE_REQUEST");
			HostPort hostPort = new HostPort(ip, port);
			newCommand.append("hostPort", (Document) hostPort.toDoc());

			out.writeUTF(newCommand.toJson());
			out.flush();
			System.out.println("COMMAND SENT: " + newCommand.toJson());
			while (no_connected) {
				if (in.available() > 0) {
					String received = in.readUTF();
					System.out.println("COMMAND RECEIVED: " + received);
					Document command = Document.parse(received);
					switch (command.get("command").toString()) {
					case "HANDSHAKE_RESPONSE":
						HostPort connectedHostPort = new HostPort((Document) command.get("hostPort"));
						if (!connectedPeers.contains(connectedHostPort)) {
							connectedPeers.add(connectedHostPort);
						}
						no_connected = false;
						break;

					case "CONNECTION_REFUSED":
						// The serverPeer reached max number
						// Read the the returned peer list, use BFS to connect one of them
						socket.close();
//						handleConnectionRefused(command);
						break;

					default:
						System.out.println("connecting: No matched protocol");
						break;
					}	
				}
			}
			
		} catch (UnknownHostException e) {
			//e.printStackTrace();
		} catch (IOException e) {
			//e.printStackTrace();
		}
		return socket;
	}
	
	
	public static void clientRunning(Socket socket, ServerMain ser) {
		Thread client = new Thread(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				DataInputStream in;
				DataOutputStream out;
				try {
					in = new DataInputStream(socket.getInputStream());
					out = new DataOutputStream(socket.getOutputStream());
					while (true) {
						
						//System.out.println(ser.eventList);
						// if we have message coming in
						if (in.available() > 0) {
							String received = in.readUTF();
							System.out.println("COMMAND RECEIVED: " + received);
							Document command = Document.parse(received);
							// Handle the reply got from the server
							String message;
							switch (command.get("command").toString()) {
							case "HANDSHAKE_REQUEST":
								message = handleHandshake(command);
								out.writeUTF(message);
								out.flush();
								System.out.println("COMMAND SENT: " + message);
								break;
							case "HANDSHAKE_RESPONSE":
								HostPort connectedHostPort = new HostPort((Document) command.get("hostPort"));
								if (!connectedPeers.contains(connectedHostPort)) {
									connectedPeers.add(connectedHostPort);
								}
								break;

							case "CONNECTION_REFUSED":
								// The serverPeer reached max number
								// Read the the returned peer list, use BFS to connect one of them
								socket.close();
//								handleConnectionRefused(command);
								break;
								
							case "FILE_MODIFY_REQUEST":
								System.out.println(command.toJson());
								//message = command.get("command").toString();
								System.out.println("we need send this message to serverMain and let it modify our local file");
								break;

							default:
								System.out.println("Running: No matched protocol");
								break;
							}
						}
						
						// if we have message need to send out
						if (ser.eventList.size()>0) {
							String s = ser.eventList.get(0);
							System.out.println(s);
							out.writeUTF(s);
							out.flush();
							ser.eventList.remove(0);
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		client.start();
	}
	
	public static void listening(ServerMain ser) {
		ServerSocketFactory factory = ServerSocketFactory.getDefault();
		try (ServerSocket serverSocket = factory.createServerSocket(port)) {
			int connectionCount = 0;
			System.out.println("Server listening on " + ip + ":" + port + " for a connection");
			while (connectionCount <= maximumIncommingConnections) {
				// this step will block, if there is no more connection coming in
				Socket clientSocket = serverSocket.accept();
				// Check if the connection has reached maximum number
				// TODO Change 10 to maxConnection Number in the configuration
				connectionCount++;
				//new Thread(() -> listening(ser)).start();
				clientRunning(clientSocket, ser);
				System.out.println(
						"Working as serverPeer: Client conection number " + connectionCount + " accepted:");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	


	/**
	 * A method that generates HANDSHAKE_RESPONSE in response to HANDSHAKE_REQUEST
	 * The result is a marshaled JSONString
	 * 
	 * @param command The massage received
	 *
	 */
	private static String handleHandshake(Document command) {
		HostPort hostPort = new HostPort((Document) command.get("hostPort"));

		Document newCommand = new Document();
		// TODO Check if the maximum connections are reached
		// If reached, reply with the current connected peer list
		if (checkConnectionNumber() >= Integer
				.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"))) {
			newCommand.append("command", "CONNECTION_REFUSED");
			newCommand.append("message", "connection limit reached");
			ArrayList<HostPort> peers = (ArrayList<HostPort>) connectedPeers;
			ArrayList<Document> docs = new ArrayList<>();
			for (HostPort peer : peers) {
				docs.add(peer.toDoc());
			}
			newCommand.append("peers", docs);
		} else if (connectedPeers.contains(hostPort)) {
			newCommand.append("command", "CONNECTION_REFUSED");
			newCommand.append("message", "peer already connected");
		} else {
			// Accept connection, generate a Handshake response
			newCommand.append("command", "HANDSHAKE_RESPONSE");
			newCommand.append("hostPort", new HostPort(ip, port).toDoc());

			// Add the connecting peer to the connected peer list
			connectedPeers.add(hostPort);
			// TODO test print
			checkConnectionNumber();
			System.out.println("Current connected peers: " + connectedPeers);
		}
		return newCommand.toJson();
	}

	private static int checkConnectionNumber() {
		// TODO Auto-generated method stub
		System.out.println("connectedPeers: " + connectedPeers.size());
		return connectedPeers.size();
	}

	private static String generateInvalidProtocol() {
		Document newCommand = new Document();
		newCommand.append("command", "INVALID_PROTOCOL");
		newCommand.append("message", "message must contain a command field as string");
		return newCommand.toJson();
	}

	/**
	 * @return The list of HostPort stored in configuration
	 */
	public static ArrayList<HostPort> getPeerList() {
		// Read configuration.properties
		String[] peers = Configuration.getConfigurationValue("peers").split(",");
		ArrayList<HostPort> hostPorts = new ArrayList<>();
		// Convert each string into a HostPort object
		for (String peer : peers) {
			HostPort hostPort = new HostPort(peer);
			hostPorts.add(hostPort);
		}
		return hostPorts;
	}

}