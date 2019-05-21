package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.ClientInfoStatus;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.net.ServerSocketFactory;
//import javax.swing.text.Document;

import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1OctetStringParser;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.tls.TlsAuthentication;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.DSASigner.noneDSA;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.hibernate.boot.internal.DefaultCustomEntityDirtinessStrategy;
import org.hibernate.sql.Delete;
import org.bouncycastle.asn1.ASN1Encodable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.CertificateUtils;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class Peer {
	private static Logger log = Logger.getLogger(Peer.class.getName());
	private static String ip = Configuration.getConfigurationValue("advertisedName");
	private static int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
	
	private static ArrayList<HostPort> connectedPeers = new ArrayList<HostPort>();
	private static ArrayList<Socket> socketList = new ArrayList<Socket>();
	private static Queue<HostPort> hostPortsQueue = new LinkedList<>();

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ServerMain ser = new ServerMain();
		int synchornizeTimeInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		
		new Thread(() -> waiting(ser)).start();
		new Thread(()-> waitingClient(ser)).start();
		
		ArrayList<FileSystemEvent> pathevents = ser.fileSystemManager.generateSyncEvents();
		for(FileSystemEvent pathevent : pathevents) {
			ser.processFileSystemEvent(pathevent);
		}
		
		String[] peerList = Configuration.getConfigurationValue("peers").split(",");
		for (String hostPort : peerList) {
			String peerIP = hostPort.split(":")[0];
			int peerPort = Integer.parseInt(hostPort.split(":")[1]);
			
			connectPeer(ser, peerIP, peerPort);
			
		}
		ser.eventList.removeAll(ser.eventList);
		sync(synchornizeTimeInterval, ser);
	}
	

	/** Connect to a given Peer, complete the handshake and add it to the socket+peer list
	 * @param ser
	 * @param peerIP
	 * @param peerPort
	 *  @return true if successfully connected, false if failed.
	 */
	public static boolean connectPeer(ServerMain ser, String peerIP, int peerPort) {
		Socket socket = sentConnectionRequest(peerIP, peerPort, ser);
		if ((socket != null) && (!socket.isClosed())) {
			HostPort hostport = new HostPort(peerIP, peerPort);
			new Thread(() -> peerRunning(socket, hostport, ser)).start();
			if(!socketList.contains(socket)&&!connectedPeers.contains(hostport)) {
				socketList.add(socket);
				connectedPeers.add(hostport);
				peerSending(socket, ser);
			}
			return true;
		}
		return false;
	}

		
			

	//  ================================ sync ==================================================
	public static void sync(int sleepTime, ServerMain ser) {
		int count = 0;
		while (true) {
			//System.out.println(socketList);
//			System.out.println(connectedPeers);
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			count++;
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
				System.out.println("All events sent to: "+connectedPeers);
				count = 0;
			} else {
				Iterator<Socket> iter = socketList.iterator();
				while (iter.hasNext()) {
					Socket socket = iter.next();
					peerSending(socket, ser);
				}
				ser.eventList.removeAll(ser.eventList);
			}
		}
	}

	// ========================== sent out a connection request ===========================================
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
			
//			System.out.println(command.get("command").toString());
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
//			System.out.println(peer.toJson());
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
//						System.out.println(command.get("command").toString());
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
//										System.out.println("COMMAND SENT: " + reply4);
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
//										System.out.println("COMMAND SENT: " + reply1);
										
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
//									System.out.println("COMMAND SENT: " + reply);		
									break;
									
								case "DIRECTORY_DELETE_REQUEST":
									String reply1 = ser.delete_directory(command);
									out.write(reply1 + "\n");
									out.flush();
//									System.out.println("COMMAND SENT: " + reply1);
									break;
									
								case "DIRECTORY_CREATE_REQUEST":
									String reply2 = ser.create_directory(command);
									out.write(reply2 + "\n");
									out.flush();
//									System.out.println("COMMAND SENT: " + reply2);
									break;
									
								case "FILE_BYTES_RESPONSE":
									try {
										String reply3 = ser.write_byte(command);
//										System.out.println(reply3);
										if(reply3.equals("complete")) {
											break;
										}else {
										out.write(reply3+"\n");
										out.flush();
//										System.out.println("COMMAND SENT: " + reply3);
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
//										System.out.println(command.toJson());
										byte_response = ser.byte_response(command);
										out.write(byte_response+"\n");
										out.flush();
//										System.out.println("COMMAND SENT: " + byte_response);
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
	public static void waitSync(Socket socket, ServerMain ser) {
		Thread tw = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					TimeUnit.SECONDS.sleep(3);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				ArrayList<FileSystemEvent> pathevents = ser.fileSystemManager.generateSyncEvents();
				for(FileSystemEvent pathevent : pathevents) {
					ser.processFileSystemEvent(pathevent);
				}
				peerSending(socket, ser);
				ser.eventList.removeAll(ser.eventList);
				
			}});
		tw.start();
	}
	
	
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
			
			new Thread(() -> waitSync(socket, ser)).start();

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
	
	/** A helper function for constructing current connected peers field
	 * @param peers The list of HostPort of all the peers that is connected to the current one
	 * @return a List in Document format ready to append to JSON message 
	 */
	public static ArrayList<Document> getConnectedPeers(ArrayList<HostPort> peers){
		ArrayList<Document> peerList = new ArrayList<>();
		for (HostPort peer : peers) {
			peerList.add(peer.toDoc());
		}
		return peerList;
	}
	
	// New thread for client/peer communication
		public static void waitingClient(ServerMain ser) {
			int clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {
					ServerSocketFactory factory = ServerSocketFactory.getDefault();
					try (ServerSocket serverSocket = factory.createServerSocket(clientPort)) {
						System.out.println("Server waiting for a client on "+clientPort);
						while(true) {
							Socket socket = serverSocket.accept();
							// Prepare a key for this session
							SecretKey secretKey= null;
							BufferedWriter out= new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
							BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

							String received = in.readLine();
							System.out.println(received);

							Document command = Document.parse(received);
							String response;
							if (command.getString("command").equals("AUTH_REQUEST")) {
								// check the list in Configuration
								String pubKeyString = findPubKey(command.getString("identity"));
								
								if(pubKeyString==null) {
									// ============= No key founded, refuse communication=============
									Document newCommand = new Document();
									newCommand.append("command", "AUTH_RESPONSE");
									newCommand.append("status", false);
									newCommand.append("message", "public key not found");

									response=newCommand.toJson();
									
									out.write(response+"\n");
									out.flush();
								}else {
									// ============= Generating an AES secrete key =============
									secretKey = generateAESKey();

									try {
										// ============= Convert OpenSSH public key to Java RSAPublicKey Object =============
										RSAPublicKey publicKey =  (RSAPublicKey) CertificateUtils.parseSSHPublicKey(pubKeyString);
//										System.out.println(publicKey);

										// ============= Encrypting with client's public key =============
										Cipher cipher = Cipher.getInstance("RSA");
										cipher.init(Cipher.ENCRYPT_MODE, publicKey);

										String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
										String encrypted = cipher.doFinal(encodedKey.getBytes()).toString();
										//System.out.println(encrypted);
										Document newCommand = new Document();
										newCommand.append("command", "AUTH_RESPONSE");
										newCommand.append("AES128", encodedKey);
										newCommand.append("status", true);
										newCommand.append("message", "public key found");

										response=newCommand.toJson();

										// ============= Sending the encrypted SecretKey to client =============
										out.write(response+"\n");
										out.flush();

									} catch (Exception e) {
										e.printStackTrace();
									}
									
									// ============= Receiving Client's command reply =============
									received = in.readLine();
									System.out.println("Message received: "+received);
									
									// ============= Decrypt the message with AES key =============
									command =Document.parse(received);
									String encrypted = Document.parse(received).getString("payload");
									String decrypted = decryptMsg(encrypted, secretKey);
									
									command = Document.parse(decrypted);
									response = executeClientCmd(ser, command);

									// =============Sending back the response =============
									encrypted = encryptMsg(response, secretKey);
									Document doc = new Document();
									doc.append("payload", encrypted);
									response=doc.toJson();
									out.write(response+"\n");
									out.flush();
									System.out.println("Message sent: "+response);
								}

							}
							
							// ============= End communication =============
							socket.close();			
						}


					} catch (Exception e) {
						e.printStackTrace();
					}

				}


				/**
				 * @return
				 * @throws NoSuchAlgorithmException
				 */
				private SecretKey generateAESKey() throws NoSuchAlgorithmException {
					SecretKey secretKey;
					KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
					keyGenerator.init(128);
					secretKey = keyGenerator.generateKey();
					return secretKey;
				}

				private String findPubKey(String id) {
					String[] keys= Configuration.getConfigurationValue("authorized_keys").split(",");
					//		System.out.println(keys);
					for (String key : keys) {
						//			split(" ")[0]:ssh-rsa	[1]:key	[2]:identity(aaron@krusty)
						if(key.split(" ")[2].equals(id)) {
							return key;
						}
					}
					return null;
				}
				
				private String decryptMsg(String encrypted, SecretKey secretKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
					try {
						Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
						cipher.init(Cipher.DECRYPT_MODE, secretKey);
						byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
						return new String(decrypted, "UTF-8");
					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;
					
				}
				
				private String encryptMsg(String message, SecretKey secretKey) {
					try {
						Cipher cipher;
						cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
						cipher.init(Cipher.ENCRYPT_MODE, secretKey);
//						System.out.println(message.getBytes("UTF-8"));
						byte[] encrypted = cipher.doFinal(message.getBytes("UTF-8"));
						return Base64.getEncoder().encodeToString(encrypted);
					} catch (Exception e) {
						e.printStackTrace();
					} 
					return null;
				}
				
				/**
				 * @param ser
				 * @param command
				 * @return
				 * @throws IOException
				 */
				private String executeClientCmd(ServerMain ser, Document command) throws IOException {
					String response= null;
					Document newCommand;

					switch (command.getString("command")) {
					case "LIST_PEERS_REQUEST":
						// Generate LIST_PEERS_RESPONSE
						newCommand=new Document();
						newCommand.append("command", "LIST_PEERS_RESPONSE");
						newCommand.append("peers", getConnectedPeers(connectedPeers));
						response=newCommand.toJson();
						break;
						
					case "CONNECT_PEER_REQUEST":
						// Get the peer's address
						String peerIP = command.getString("host");
						int peerPort =(int) command.getLong("port");
						// Build socket and send Handshake_Requset to the given peer
						boolean status = connectPeer(ser, peerIP, peerPort);
						// Construct reply message
						newCommand=new Document();
						newCommand.append("command", "CONNECT_PEER_RESPONSE");
						newCommand.append("host", peerIP);
						newCommand.append("port", peerPort);
						newCommand.append("status", status);
						if(status==true) {
							newCommand.append("message", "connected to peer");
						}else {
							newCommand.append("message","connection failed");
						}
						response=newCommand.toJson();

						break;
					case "DISCONNECT_PEER_REQUEST":
						// Get the HostPort from the command
						peerIP = command.getString("host");
						peerPort = (int)command.getLong("port");
						boolean status1 = disconnectPeer(peerIP, peerPort);
						newCommand = new Document();
						newCommand.append("command", "DISCONNECT_PEER_RESPONSE");
						newCommand.append("host", peerIP);
						newCommand.append("port", peerPort);
						newCommand.append("status", status1);
						if(status1) {
							newCommand.append("message", "disconnected from peer");
						}else {
							newCommand.append("message", "connection not active");
						}
						response = newCommand.toJson();
						break;


					default:
						break;
					}
					return response;
				}


			});
			thread.start();
		}
		
		/**
		 * @param peerIP
		 * @param peerPort
		 */
		private static HostPort findPeer(String peerIP, int peerPort) {
			Iterator<HostPort> it  = connectedPeers.iterator();
			while(it.hasNext()) {
				HostPort peer = it.next();
//				System.out.println(peer);
//				System.out.println((peer.host.equals(peerIP)));
//				System.out.println((peer.port==peerPort));
				if (peer.host.equals(peerIP)&&peer.port==peerPort) {	
					return peer;
				}
				
			}
			// Peer not found
			return null;
		}



		/** Find a socket in the list connected to the given address and port
		 * @param peerIP
		 * @param peerPort
		 * @return The socket
		 */
		private static Socket getSocketByHostPort(String peerIP, int peerPort) {
			Iterator<Socket> it = socketList.iterator();
			while(it.hasNext()) {
				Socket soc = it.next(); 
				System.out.println(soc.getInetAddress().getCanonicalHostName()+soc.getPort());
				System.out.println("soc.getInetAddress().getCanonicalHostName().equals(peerIP)"+soc.getInetAddress().getCanonicalHostName().equals(peerIP));
				System.out.println("soc.getPort()==peerPort"+(soc.getPort()==peerPort));
				if(soc.getInetAddress().getCanonicalHostName().equals(peerIP)&&soc.getPort()==peerPort) {
					return soc;
				}
			}
			// Socket not found
			return null;
		}
		
		/** Check if the peer is in the list
		 * IF yes 	-> find the socket, close it.
		 * 			-> delete it From both the lists
		 * @param peerIP
		 * @param peerPort
		 * @return
		 * @throws IOException
		 */
		private static boolean disconnectPeer(String peerIP, int peerPort) throws IOException {
			
			// Check if the peer is in the list
			HostPort hostPort = findPeer(peerIP, peerPort);
			System.out.println(findPeer(peerIP, peerPort));
			
			// IF yes 	-> find the socket, close it.
			//			-> delete it From both the lists
			if(hostPort!=null) {
				Socket soc = getSocketByHostPort(peerIP, peerPort);
				try {
					soc.shutdownOutput();
					soc.shutdownInput();
					soc.close();
				}catch (Exception e) {
					e.printStackTrace();
					return false;
				}finally {
					socketList.remove(soc);
					connectedPeers.remove(hostPort);	
				}
				return true;
			}
			// IF no	-> return  false
			return false;
			
			
		}
}