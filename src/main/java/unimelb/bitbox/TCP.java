package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;

import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class TCP {
	protected static Logger log = Peer.log;
	protected static String ip = Peer.ip;
	protected static int port = Peer.port;
	
	protected static ArrayList<HostPort> connectedPeers = Peer.connectedPeers;
	protected static ArrayList<Socket> socketList = Peer.socketList;
	protected static Queue<HostPort> hostPortsQueue = Peer.hostPortsQueue;
	protected static ServerMain ser = Peer.ser;
	
	// ============================== establish connections ===================================
		public static void connectAllPeers() {
			String[] peerList = Configuration.getConfigurationValue("peers").split(",");
			for (String hostPort : peerList) {
				String peerIP = hostPort.split(":")[0];
				int peerPort = Integer.parseInt(hostPort.split(":")[1]);
				
				Socket socket = sentConnectionRequest(peerIP, peerPort, ser);
				
				// if socket exist: means the other peer is running
				if ((socket != null) && (!socket.isClosed())) {
					HostPort hostport = new HostPort(peerIP, peerPort);
					new Thread(() -> peerRunning(socket, hostport, ser)).start();
					socketList.add(socket);
					connectedPeers.add(hostport);
				}
			}
		}
		

		// ================================ sync ==================================================
		// sync after a period of time and also update the new events for every second
		public static void sync(int sleepTime, ServerMain ser) {
			int count = sleepTime;
			while (true) {
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					//e.printStackTrace();
				}
				
				// avoid synchronizing and updating concurrently
				// sync with all peers
				if (count == sleepTime) {
					ArrayList<FileSystemEvent> pathevents = ser.fileSystemManager.generateSyncEvents();
					for(FileSystemEvent pathevent : pathevents) {
						ser.processFileSystemEvent(pathevent);
					}
					Iterator<Socket> iter = socketList.iterator();
					while (iter.hasNext()) {
						Socket socket = iter.next();
						peerSending(socket, ser);
					}
					ser.eventList.removeAll(ser.eventList);
					count = 0;
				} else {
				// if not synchronizing, we keep checking update for every 1 second
					Iterator<Socket> iter = socketList.iterator();
					while (iter.hasNext()) {
						Socket socket = iter.next();
						peerSending(socket, ser);
					}
					ser.eventList.removeAll(ser.eventList);
				}
				count++;
			}
		}

		// ========================== sent out a connection request ===========================================
		// this function will either get a valid socket or get a null which means we can't connect because the other peer is not running
		public static Socket sentConnectionRequest(String peerIp, int peerPort, ServerMain ser) {
			Socket socket = null;
			System.out.println("Sent connection request: Try connecting to " + peerIp + ":" + peerPort);
			try {
				socket = new Socket(peerIp, peerPort);
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
				Document newCommand = new Document();
				newCommand.append("command", "HANDSHAKE_REQUEST");
				// my own host port info
				HostPort hostPort = new HostPort(ip, port);
				newCommand.append("hostPort", (Document) hostPort.toDoc());
				out.write(newCommand.toJson() + "\n");
				out.flush();
				
				String received = in.readLine();
				Document command = Document.parse(received);
				
				System.out.println(command.get("command").toString());
				switch (command.get("command").toString()) {
					case "HANDSHAKE_RESPONSE":
						break;
					case "CONNECTION_REFUSED":
						socket.close();
						socket = null;
						socket = solveConnectionRefused(command, ser);
						break;
				}
				
				System.out.println("COMMAND SENT: " + newCommand.toJson());
			} catch (UnknownHostException e) {
				socket = null;
				System.out.println("connection failed");
			} catch (IOException e) {
				socket = null;
				System.out.println("connection failed");
			}
			return socket;
		}
		
		
		private static Socket solveConnectionRefused(Document command, ServerMain ser) {
			@SuppressWarnings("unchecked")
			ArrayList<Document> peers = (ArrayList<Document>) command.get("peers");
			for (Document peer : peers) {
				System.out.println(peer.toJson());
				String h = peer.getString("host");
				String p = peer.get("port").toString();
				hostPortsQueue.offer(new HostPort(h, Integer.parseInt(p)));
			}
			while (!hostPortsQueue.isEmpty()) {
				HostPort hostport = hostPortsQueue.poll();
				Socket socket = sentConnectionRequest(hostport.host, hostport.port, ser);
				if ((socket != null) && (!socket.isClosed())) {
					hostPortsQueue.removeAll(hostPortsQueue);
					return socket;
				}
			}
			return null;
		}
		
		// ================================= waiting for new connection request ==================================
		// running all the time waiting for incoming connections
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
							BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
							BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
							
							String received = in.readLine();
							Document command = Document.parse(received);
							// Handle the reply got from the server
							String message;
							switch (command.get("command").toString()) {
							case "HANDSHAKE_REQUEST":
								// here need to build a socket and start a thread: in handleHandshake
								message = handleHandshake(socket, command, ser);
								out.write(message + "\n");
								out.flush();
								System.out.println("COMMAND SENT: " + message);
								break;
						    }
					    }
					}catch (IOException e) {
						//e.printStackTrace();
						System.out.println("e0");
					}
				}
			});
			serverListening.start();
		}
		
		
		// =============================== send the new events to the other peer ==========================================
		public static Boolean peerSending(Socket socket, ServerMain ser) {
			try {
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
				// if we have message need to send out
				Iterator<String> iter = ser.eventList.iterator();
				while (iter.hasNext()) {
					String s = iter.next();
					out.write(s + "\n");
					out.flush();
				}
			} catch (IOException e) {
				System.out.println("can't send");
				return false;
			}
			return true;
		}
		
		// ==================== running the thread to receive, channel established between two sockets ====================
		public static void peerRunning(Socket socket, HostPort hostport, ServerMain ser) {
			
			Thread receive = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
						
						while (true) {
							String received = in.readLine();
							Document command = Document.parse(received);
							String message;
							System.out.println(command.get("command").toString());
							switch (command.get("command").toString()) {
									
									case "HANDSHAKE_REQUEST":
										message = handleHandshake(socket, command, ser);
										out.write(message + "\n");
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
										socket.close();
										break;
										
									case "FILE_MODIFY_REQUEST":					
										try {
											String reply4 = ser.file_modify_response(command);
											out.write(reply4 + "\n");
											out.flush();
											System.out.println("COMMAND SENT: " + reply4);
											String reply5 = ser.byte_request(command);
											out.write(reply5 + "\n");
											out.flush();
											System.out.println("COMMAND SENT: " + reply5);
										} catch (NoSuchAlgorithmException e2) {
											e2.printStackTrace();
										}
										break;
										
									case "FILE_CREATE_REQUEST":
										try {
											String reply1 = ser.file_create_response(command);
											out.write(reply1 + "\n");
											out.flush();
											System.out.println("COMMAND SENT: " + reply1);
											
										} catch (NoSuchAlgorithmException e1) {
											e1.printStackTrace();
										}
										break;
									case "FILE_CREATE_RESPONSE":
										break;
									case "FILE_DELETE_RESPONSE":
										break;
									case "FILE_MODIFY_RESPONSE":
										break;
									case "DIRECTORY_CREATE_RESPONSE":
										break;
									case "DIRECTORY_DELETE_RESPONSE":
										break;
										
									case "FILE_DELETE_REQUEST":
										String reply = ser.delete_file(command);
										out.write(reply + "\n");
										out.flush();
										System.out.println("COMMAND SENT: " + reply);		
										break;
										
									case "DIRECTORY_DELETE_REQUEST":
										String reply1 = ser.delete_directory(command);
										out.write(reply1 + "\n");
										out.flush();
										System.out.println("COMMAND SENT: " + reply1);
										break;
										
									case "DIRECTORY_CREATE_REQUEST":
										String reply2 = ser.create_directory(command);
										out.write(reply2 + "\n");
										out.flush();
										System.out.println("COMMAND SENT: " + reply2);
										break;
										
									case "FILE_BYTES_RESPONSE":
										try {
											String reply3 = ser.write_byte(command);
											System.out.println(reply3);
											if(reply3.equals("complete")) {
												break;
											}else {
											out.write(reply3+"\n");
											out.flush();
											System.out.println("COMMAND SENT: " + reply3);
											}
										} catch (NoSuchAlgorithmException e) {
											e.printStackTrace();
										} catch (ParseException e) {
											e.printStackTrace();
										}
										break;
										
									case "FILE_BYTES_REQUEST":
										
										String byte_response;
										try {
											System.out.println(command.toJson());
											byte_response = ser.byte_response(command);
											out.write(byte_response+"\n");
											out.flush();
											System.out.println("COMMAND SENT: " + byte_response);
										} catch (NoSuchAlgorithmException e) {
											e.printStackTrace();
										} catch (ParseException e) {
											e.printStackTrace();
										}
										break;
										
									case "INVALID_PROTOCOL":
										break;
										
									default:
										System.out.println("COMMAND RECEIVED: " + received);
										System.out.println("Running: No matched protocol");
										break;
									}
								
						} 
						
					} catch (IOException e) {
						connectedPeers.remove(hostport);
						socketList.remove(socket);
					}
				}
			});
			receive.start();
		}
		
		// ============================== helper methods =======================================================
		/**
		 * A method that generates HANDSHAKE_RESPONSE in response to HANDSHAKE_REQUEST
		 * The result is a marshaled JSONString
		 * 
		 * @param command The massage received
		 *
		 */	
		
		// return a message for either CONNECTION_REFUSED or HANDSHAKE_RESPONSE
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
				newCommand.append("command", "INVALID_PROTOCOL");
				newCommand.append("message", "peer already connected");
			} else {
				// Accept connection, generate a Handshake response
				new Thread(() -> peerRunning(socket, hostPort, ser)).start();
				
				newCommand.append("command", "HANDSHAKE_RESPONSE");
				newCommand.append("hostPort", new HostPort(ip, port).toDoc());

				// Add the connecting peer to the connected peer list
				connectedPeers.add(hostPort);
				socketList.add(socket);
				
				checkConnectionNumber();
				System.out.println("Current connected peers: " + connectedPeers);
			}
			return newCommand.toJson();
		}
		
		private static int checkConnectionNumber() {
			System.out.println("connectedPeers: " + connectedPeers.size());
			return connectedPeers.size();
		}
		


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
