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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
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
	
	//private static int maximumIncommingConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	private static ArrayList<HostPort> connectedPeers = new ArrayList<>();

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ServerMain ser = new ServerMain();
		
		new Thread(() -> waiting(ser)).start();
		
		Socket socket = sentConnectionRequest(firstPeerIp, firstPeerPort, ser);
		if ((socket != null) && (!socket.isClosed())) {
			HostPort hostport = new HostPort(firstPeerIp, firstPeerPort);
			new Thread(() -> peerRunning(socket, hostport, ser)).start();
		}
		
	}
	
	// ========================== sent out a connection request ==================================================
	public static Socket sentConnectionRequest(String peerIp, int peerPort, ServerMain ser) {
		Socket socket = null;
		try {
			socket = new Socket(peerIp, peerPort);
			System.out.println("Sent connection request: Try connecting to " + peerIp + ":" + peerPort);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			Document newCommand = new Document();
			newCommand.append("command", "HANDSHAKE_REQUEST");
			// my own host port info
			HostPort hostPort = new HostPort(ip, port);
			newCommand.append("hostPort", (Document) hostPort.toDoc());
			out.writeUTF(newCommand.toJson());
			out.flush();
			System.out.println("COMMAND SENT: " + newCommand.toJson());
		} catch (UnknownHostException e) {
			//e.printStackTrace();
		} catch (IOException e) {
			//e.printStackTrace();
		}
		return socket;
	}
	
	public static void waiting(ServerMain ser) {
		Thread serverListening = new Thread(new Runnable() {
			@Override
			public void run() {
				ServerSocketFactory factory = ServerSocketFactory.getDefault();
				try (ServerSocket serverSocket = factory.createServerSocket(port)) {
					System.out.println("Server listening on " + ip + ":" + port + " for a connection");
					while (true) {
						// this step will block, if there is no more connection coming in
						Socket socket = serverSocket.accept();
						System.out.println("here");
						DataInputStream in = new DataInputStream(socket.getInputStream());
						DataOutputStream out = new DataOutputStream(socket.getOutputStream());
						
						String received = in.readUTF();
						Document command = Document.parse(received);
						// Handle the reply got from the server
						String message;
						switch (command.get("command").toString()) {
						case "HANDSHAKE_REQUEST":
							// here need to build a socket and start a thread: in handleHandshake
							message = handleHandshake(socket, command, ser);
							out.writeUTF(message);
							out.flush();
							System.out.println("COMMAND SENT: " + message);
							break;
					}
				}
				}catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		serverListening.start();
	}
	
	
	public static void peerRunning(Socket socket, HostPort hostport, ServerMain ser) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					DataInputStream in = new DataInputStream(socket.getInputStream());
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					while (true) {
						
						// to check whether the other peer is shutdown or not
						// Iterator<Socket> iter = socketList.iterator();
						try {
							out = new DataOutputStream(socket.getOutputStream());
							Document newCommand = new Document();
							newCommand.append("command", "CHECK");
							out.writeUTF(newCommand.toJson());
							out.flush();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							// e.printStackTrace();
							connectedPeers.remove(hostport);
							break;
						}
						
						// System.out.println(ser.eventList);
						// if we have message coming in
						if (in.available() > 0) {
							String received = in.readUTF();
							Document command = Document.parse(received);
							// Handle the reply got from the server
							String message;
							switch (command.get("command").toString()) {
							
							case "HANDSHAKE_REQUEST":
								// here need to build a socket and start a thread: in handleHandshake
								message = handleHandshake(socket, command, ser);
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
								
							case "CHECK":
								// just ignore for now
								break;
								
							default:
								System.out.println("COMMAND RECEIVED: " + received);
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
		thread.start();
	}
	
	/**
	 * A method that generates HANDSHAKE_RESPONSE in response to HANDSHAKE_REQUEST
	 * The result is a marshaled JSONString
	 * 
	 * @param command The massage received
	 *
	 */
	private static String handleHandshake(Socket socket, Document command, ServerMain ser) {
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
			new Thread(() -> peerRunning(socket, hostPort, ser)).start();
			
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
		System.out.println("connectedPeers: " + connectedPeers.size());
		return connectedPeers.size();
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