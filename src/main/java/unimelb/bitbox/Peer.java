package unimelb.bitbox;

import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.sql.ClientInfoStatus;
import java.util.ArrayList;
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

	private static ArrayList<HostPort> connectedPeers = new ArrayList<>();

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ServerMain ser = new ServerMain();
		client(firstPeerIp, firstPeerPort);
		server();
		

		// ser.sentOutMessages();

	}

	public static void client(String peerIp, int peerPort) {
		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {

				Socket socket = null;
				try {
					// Create a stream socket bounded to any port and connect it to the
					// socket bound to localhost on port 8111
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

					// Waiting for the server peer's response
					while (true) {

						// Receive the reply from the server peer by reading from the socket input
						// stream
						if (in.available() > 0) {

							String received = in.readUTF();
							System.out.println("COMMAND RECEIVED: " + received);

							// Marshal the result into JSONObject
							Document command = Document.parse(received);

							// Handle the reply got from the server
							switch (command.get("command").toString()) {
							case "HANDSHAKE_RESPONSE":
								// Connection Established
								// TODO: Connect next peer in the config file
								HostPort connectedHostPort = new HostPort((Document)command.get("hostPort"));
								if(!connectedPeers.contains(connectedHostPort)) {
									connectedPeers.add(connectedHostPort);
								}
								break;

							case "HANDSHAKE_REFUSED":
								// The serverPeer reached max number
								// Read the the returned peer list, use BFS to connect one of them
								socket.close();
								handleConnectionRefused(command);
								break;

							default:
								break;
							}

						}

					}

				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					// e.printStackTrace();
				} finally {
					// Close the socket
					if (socket != null) {
						try {
							socket.close();

						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}

			private void handleConnectionRefused(Document command) {
				// {
				// "command": "CONNECTION_REFUSED",
				// "message": "connection limit reached"
				// "peers": [
				// {
				// "host" : "sunrise.cis.unimelb.edu.au",
				// "port" : 8111
				// },
				// {
				// "host" : "bigdata.cis.unimelb.edu.au",
				// "port" : 8500
				// }
				// ]
				// }
				ArrayList<Document> peers = (ArrayList<Document>) command.get("peers");
				for (Document peer : peers) {
					HostPort hostPort = new HostPort(peer);
					// While the peer is not on the list
					// try to connect it.
					if (!connectedPeers.contains(hostPort)) {
						System.out.println("Try connecting to alternative peer: " + hostPort.host + ":" + hostPort.port);
						client(hostPort.host, hostPort.port);
						System.out.println("Current connected peers: " + connectedPeers);
					}
					// If the client successfully connect to one peer,
					// then the connectedPeers list should contain that peer now
					// Stop the BFS
					if (connectedPeers.contains(hostPort)) {
						System.out.println("Recursion complete!");
						return;
					}
				}
			}

		});
		t1.start();

	}

	public static void server() {
		ServerSocketFactory factory = ServerSocketFactory.getDefault();
		try (ServerSocket serverSocket = factory.createServerSocket(port)) {
			int connectionCount = 0;
			System.out.println("Server listening on" + ip + ":" + port + " for a connection");
			while (true) {
				if (connectionCount <= 10) {
				Socket clientSocket = serverSocket.accept();
				
				// Check if the connection has reached maximum number
				// TODO Change 10 to maxConnection Number in the configuration
				
					Thread t2 = new Thread(() -> serverClient(clientSocket));
					connectionCount++;
					System.out.println(
							"Working as serverPeer: Client conection number " + connectionCount + " accepted:");
					t2.start();
				}

			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ServerPeer's actions after a clientPeer has connected to it 1) Receive
	 * client's request 2) Generate message
	 * 
	 * @param client
	 */
	private static void serverClient(Socket client) {
		try (Socket clientSocket = client) {

			// Get the input/output streams for reading/writing data from/to the socket
			DataInputStream in = new DataInputStream(clientSocket.getInputStream());
			DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

			// Receiving more data
			while (true) {
				if (in.available() > 0) {
					// Attempt to convert read data to JSON
					String received = in.readUTF();
					System.out.println("COMMAND RECEIVED: " + received);
					Document command = Document.parse(received);

					String message = "";
					switch (command.getString("command")) {
					case "HANDSHAKE_REQUEST":
						message = handleHandshake(command);

						break;

					default:
						message = generateInvalidProtocol();
						break;
					}

					out.writeUTF(message);
					out.flush();
					System.out.println("COMMAND SENT: " + message);

				}

			}

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
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
		HostPort hostPort = new HostPort((Document)command.get("hostPort"));

		Document newCommand = new Document();
		// TODO Check if the maximum connections are reached
		// If reached, reply with the current connected peer list
		if (checkConnectionNumber() >= Integer
				.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"))) {
			newCommand.append("command", "HANDSHAKE_REFUSED");
			newCommand.append("message", "connection limit reached");
			ArrayList<HostPort> peers = (ArrayList<HostPort>) connectedPeers;
			ArrayList<Document> docs = new ArrayList<>();
			for (HostPort peer : peers) {
				docs.add(peer.toDoc());
			}
			newCommand.append("peers", docs);
		} else if(connectedPeers.contains(hostPort)) {
			newCommand.append("command", "HANDSHAKE_REFUSED");
			newCommand.append("message", "peer already connected");
		}
		else {
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
		JSONObject newCommand = new JSONObject();
		newCommand.put("command", "INVALID_PROTOCOL");
		newCommand.put("message", "message must contain a command field as string");
		return newCommand.toJSONString();
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

	public void connect() {

	}
}
