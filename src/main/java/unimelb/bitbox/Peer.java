package unimelb.bitbox;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.net.ServerSocketFactory;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.CertificateUtils;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class Peer {
	protected static Logger log = Logger.getLogger(Peer.class.getName());
	protected static String ip = Configuration.getConfigurationValue("advertisedName");
	protected static int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
	
	protected static ArrayList<HostPort> connectedPeers = new ArrayList<HostPort>();
	protected static ArrayList<Socket> socketList = new ArrayList<Socket>();
	protected static Queue<HostPort> hostPortsQueue = new LinkedList<>();
	protected static ServerMain ser = null;
	protected static String mode =null;
	
	protected static JSONObject responseList = new JSONObject();
	protected static DatagramSocket socket = null;

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ser = new ServerMain();
		int synchornizeTimeInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		mode = Configuration.getConfigurationValue("mode");
		
		System.out.println(mode);
		if (mode.contentEquals("tcp")) {
			port = Integer.parseInt(Configuration.getConfigurationValue("port"));
			
			// wait for connections--------- this is an independent thread running all the time
			new Thread(() -> TCP.waiting(ser)).start();
			new Thread(()-> waitingClient(ser)).start();
			
			// try to connect all the peers in the list
			TCP.connectAllPeers();
			
			// running sync
			TCP.sync(synchornizeTimeInterval, ser);
		}
		
		if (mode.contentEquals("udp")) {		
			port = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
			// all packets sending out and coming in from udpPort
			socket = new DatagramSocket(port);
			
			// peer receiving
			new Thread(() -> peerRunning(socket, ser)).start();
			new Thread(()-> waitingClient(ser)).start();
			
			// notice all the peers
			connectNotify(socket);
			
			// peer sending and sync
			sync(socket, synchornizeTimeInterval, ser);
		}
	}
	
	public static void connectNotify(DatagramSocket socket) throws IOException {
		String[] peerList = Configuration.getConfigurationValue("peers").split(",");
		for (String hp : peerList) {
			String peerIP = hp.split(":")[0];
			int peerPort = Integer.parseInt(hp.split(":")[1]);
			sendConnectionRequest(socket, peerIP, peerPort);
		}
	}

	/** For UDP connection: send out a HANDSHAKE_REQUEST packet with the local peer address
	 * @param socket the socket for sending datagram
	 * @param peerIP the peer that you are trying to connect
	 * @param peerPort
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public static void sendConnectionRequest(DatagramSocket socket, String peerIP, int peerPort){
		try {
			HostPort hostport = new HostPort(peerIP, peerPort);
			HostPort self_hostport = new HostPort(ip, port);
			
			Document newCommand = new Document();
			newCommand.append("command", "HANDSHAKE_REQUEST");
			newCommand.append("hostPort", (Document) self_hostport.toDoc());
			byte[] buf = new byte[8192];
			buf = newCommand.toJson().getBytes();
			DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(peerIP), peerPort);
			socket.send(packet);
			
			// set hostPort as key waiting for reply
			// change: for waiting peers port
			newCommand.append("hostPort", (Document) hostport.toDoc());
			waitResponse(hostport, newCommand.toJson(), "hostPort", packet);
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	// ===================== keep sending out packets ==============================================
	public static void sync(DatagramSocket socket, int sleepTime, ServerMain ser) {
		int count = sleepTime;
		while (true) {
			//System.out.println(connectedPeers);
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				//e.printStackTrace();
			}
			// avoid synchronizing and updating concurrently
			// sync with all peers
			if (count == sleepTime) {
				ser.eventList.removeAll(ser.eventList);
				ArrayList<FileSystemEvent> pathevents = ser.fileSystemManager.generateSyncEvents();
				for(FileSystemEvent pathevent : pathevents) {
					ser.processFileSystemEvent(pathevent);
				}
				peerSending(socket, ser);
				ser.eventList.removeAll(ser.eventList);
				count = 0;
				System.out.println("ConnectedPeers when sync: "+connectedPeers);
			} else {
			// if not synchronizing, we keep checking update for every 1 second
				peerSending(socket, ser);
				ser.eventList.removeAll(ser.eventList);
			}
			count++;
		}
	}
	
	// send all events in ser eventList to all the remembered peers
	public static void peerSending(DatagramSocket socket, ServerMain ser) {
		try {
			Iterator<String> iter = ser.eventList.iterator();
			while (iter.hasNext()) {
				String s = iter.next();
				byte[] buf = s.getBytes();
				Iterator<HostPort> peer_iter = connectedPeers.iterator();
				while (peer_iter.hasNext()) {
					HostPort hostPort = peer_iter.next();
					DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(hostPort.host), hostPort.port);
					socket.send(packet);
					// different cases for file and directory
					try {
						// if it is a file
						waitResponse(hostPort, s, "fileDescriptor", packet);
					} catch (NullPointerException ed) {
						// if it is a directory
						waitResponse(hostPort, s, "pathName", packet);
					}
				}
			}
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// check connection and retry
	@SuppressWarnings("unchecked")
	public static void waitResponse(HostPort hostport, String jsonString, String fieldName, DatagramPacket packet) {
		Document doc = Document.parse(jsonString);
		if (fieldName.equals("fileDescriptor")) {
			String key = ((Document) doc.get(fieldName)).toJson() + hostport.toString();
			responseList.put(key, false);
			new Thread(() -> wait (key, hostport, packet)).start();
		}
		if (fieldName.equals("pathName")) {
			String key = (String) doc.get(fieldName) + hostport.toString();
			responseList.put(key, false);
			new Thread(() -> wait (key, hostport, packet)).start();
		}
	}
	
	public static void wait (String key, HostPort hostport, DatagramPacket packet) {
		Thread waiting = new Thread(new Runnable() {
			@Override
			public void run() {
				int count = 0;
				int tryTimes = Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));
				try {
					while (count < tryTimes) {
						int timeout = Integer.parseInt(Configuration.getConfigurationValue("udpTimeout"));
						TimeUnit.MILLISECONDS.sleep(timeout);
						// check value of key and re-try
						if (responseList.isEmpty()) {
							System.out.println("empty");
							break;
						}
						if ((boolean) responseList.get(key)) {
							break;
						} else {
							socket.send(packet);
						}
						count++;
					}
					if (count == tryTimes) {
						// disconnect
						System.out.println("time out: "+new String(packet.getData(), 0, packet.getLength()));
						responseList.remove(key);
						connectedPeers.remove(hostport);
					} else {
						// recevied the response
						responseList.remove(key);
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (NullPointerException e) {
					System.out.println("empty");
				}
			}
		});
		waiting.start();
	}
	
	
	// ================================= hear other peers and remember the peers ===================
		public static void peerRunning(DatagramSocket socket, ServerMain ser) {
			Thread receive = new Thread(new Runnable() {
				@SuppressWarnings("unchecked")
				@Override
				public void run() {
					try {
						while (true) {
							byte[] buf = new byte[8192];
							DatagramPacket packet = new DatagramPacket(buf, buf.length);
							socket.receive(packet);
							
							InetAddress peerAddress = packet.getAddress();
				            int peerPort = packet.getPort();
				            HostPort peerHostport = new HostPort(peerAddress.toString().substring(1), peerPort);
				            
				            String received = new String(packet.getData(), 0, packet.getLength());
							Document command = Document.parse(received);
							
							System.out.println(received);
				         
							// if a new peer come in, check maximum number
							if (command.get("command").toString().equals("HANDSHAKE_REQUEST")) {
								if (connectedPeers.size() < Integer
										.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"))) {
									connectedPeers.add(peerHostport);
								} else {
									Document newCommand = new Document();
									newCommand.append("command", "CONNECTION_REFUSED");
									newCommand.append("message", "connection limit reached");
									ArrayList<HostPort> peers = (ArrayList<HostPort>) connectedPeers;
									ArrayList<Document> docs = new ArrayList<>();
									for (HostPort peer : peers) {
										docs.add(peer.toDoc());
									}
									newCommand.append("peers", docs);
									String reply = newCommand.toJson();
									buf = reply.getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
									continue;
								}
							}
			                
			                if (command.get("command").toString().equals("HANDSHAKE_RESPONSE")) {
			                	connectedPeers.add(peerHostport);
			                }
			                
			                if (command.get("command").toString().equals("CONNECTION_REFUSED")) {
			                	ArrayList<Document> peers = (ArrayList<Document>) command.get("peers");
								for (Document peer : peers) {
//									System.out.println(peer.toJson());
									String h = peer.getString("host");
									String p = peer.get("port").toString();
									hostPortsQueue.offer(new HostPort(h, Integer.parseInt(p)));
								}
								
								while (!hostPortsQueue.isEmpty()) {
									HostPort hostport = hostPortsQueue.poll();
									sendConnectionRequest(socket, hostport.host, hostport.port);
								}
				            }
			                
			                if (!connectedPeers.contains(peerHostport)) {
			                 continue;
			                }
				            
							
							String reply;
							String des;
							switch (command.get("command").toString()) {
							
							    // =============================== requests ==============================================
							    case "HANDSHAKE_REQUEST":
							    	// send response
							    	Document replyCommand = new Document();
									replyCommand.append("command", "HANDSHAKE_RESPONSE");
									replyCommand.append("hostPort", new HostPort(ip, port).toDoc());
									buf = replyCommand.toJson().getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
									
									// send all the local data first
									ser.eventList.removeAll(ser.eventList);
									ArrayList<FileSystemEvent> pathevents = ser.fileSystemManager.generateSyncEvents();
									for(FileSystemEvent pathevent : pathevents) {
										ser.processFileSystemEvent(pathevent);
									}
									peerSending(socket, ser);
									ser.eventList.removeAll(ser.eventList);
									break;
								
								case "FILE_CREATE_REQUEST":
									reply = ser.file_create_response(command);
									buf = reply.getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
									System.out.println("file created");
									
									Document create_reply = Document.parse(reply);
									if (create_reply.get("status").toString().equals("true")) {
									String reply1 = ser.file_bytes_request(command);
									buf = reply1.getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
									System.out.println("send file_bytes_request");
									}
									break;
									
								case "FILE_DELETE_REQUEST":
									reply = ser.delete_file(command);
									buf = reply.getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
									System.out.println("file deleted");		
									break;
									
								case "DIRECTORY_DELETE_REQUEST":
									reply = ser.delete_directory(command);
									buf = reply.getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
									System.out.println("ditectory deleted");
									break;
									
								case "DIRECTORY_CREATE_REQUEST":
									reply = ser.create_directory(command);
									buf = reply.getBytes();
									packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
									socket.send(packet);
//									System.out.println("directory created");
									break;
									
								case "FILE_MODIFY_REQUEST":
									try {
										String reply4 = ser.file_modify_response(command);
										buf = reply4.getBytes();
										packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
										socket.send(packet);
										String reply5 = ser.byte_request(command);
										buf = reply5.getBytes();
										packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
										socket.send(packet);
									} catch (NoSuchAlgorithmException e2) {
										e2.printStackTrace();
									}
									break;
									
								case "FILE_BYTES_REQUEST":
									try {
										reply = ser.byte_response(command);
										buf = reply.getBytes();
										packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
										socket.send(packet);
									} catch (ParseException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}								
									break;
									
									
							    // ======================= response =====================================
								case "FILE_BYTES_RESPONSE":
									try {
										String reply3 = ser.write_byte(command);
										if(reply3.equals("complete")) {
											break;
										}else {
										buf = reply3.getBytes();
										packet = new DatagramPacket(buf, buf.length, peerAddress, peerPort);
										socket.send(packet);
										}
									} catch (NoSuchAlgorithmException e) {
										e.printStackTrace();
									} catch (ParseException e) {
										e.printStackTrace();
									}
									break;
									
								case "HANDSHAKE_RESPONSE":
									//connectedPeers.add(hostport);
									des = ((Document) command.get("hostPort")).toJson();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
							    	break;
									
								case "FILE_CREATE_RESPONSE":
									// check responseList
									des = ((Document) command.get("fileDescriptor")).toJson() + peerHostport.toString();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "FILE_DELETE_RESPONSE":
									des = ((Document) command.get("fileDescriptor")).toJson() + peerHostport.toString();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "FILE_MODIFY_RESPONSE":
									des = ((Document) command.get("fileDescriptor")).toJson() + peerHostport.toString();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "DIRECTORY_CREATE_RESPONSE":
									des = (String) command.get("pathName") + peerHostport.toString();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "DIRECTORY_DELETE_RESPONSE":
									des = (String) command.get("pathName") + peerHostport.toString();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
									
								// =========================== other protocols ===============================
								case "INVALID_PROTOCOL":
									connectedPeers.remove(peerHostport);
									break;
								}
						} 
					} catch (IOException e) {
						System.out.println("error");
						e.printStackTrace();
					} catch (NoSuchAlgorithmException e1) {
						e1.printStackTrace();
					}
				}
			});
			receive.start();
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
//							System.out.println(received);

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
										//												System.out.println(publicKey);

										// ============= Encrypting with client's public key =============
										String encrypted = rsaEncrypt(secretKey, publicKey);

										Document newCommand = new Document();
										newCommand.append("command", "AUTH_RESPONSE");
										newCommand.append("AES128", encrypted);
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
									if(received==null) {
										continue;
									}
//									System.out.println("Message received: "+received);

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
//									System.out.println("Message sent: "+response);
								}

							}

							// ============= End communication =============
							socket.close();			
						}


					} catch (Exception e) {
						e.printStackTrace();
					}

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

				/**
				 * @param secretKey
				 * @param publicKey
				 * @return
				 * @throws NoSuchAlgorithmException
				 * @throws NoSuchPaddingException
				 * @throws InvalidKeyException
				 * @throws IllegalBlockSizeException
				 * @throws BadPaddingException
				 */
				private String rsaEncrypt(SecretKey secretKey, RSAPublicKey publicKey) throws NoSuchAlgorithmException,
				NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
					Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
					cipher.init(Cipher.ENCRYPT_MODE, publicKey);

					byte[] encodedKey = secretKey.getEncoded();
					String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(encodedKey));
					//							System.out.println(encrypted);
					return encrypted;
				}


				private String decryptMsg(String encrypted, SecretKey secretKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
					try {
						Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
						cipher.init(Cipher.DECRYPT_MODE, secretKey);
						byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
						String result = new String(decrypted, "UTF-8");
						result = result.split("\n")[0];
						return result;
					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;

				}

				private String encryptMsg(String message, SecretKey secretKey) {
					try {
						Cipher cipher;
						cipher = Cipher.getInstance("AES/ECB/NoPadding");
						cipher.init(Cipher.ENCRYPT_MODE, secretKey);
						// Padding the message with random data, so that each block is exactly 128bit
						byte bytes[];
						byte appended[];
						message=message+"\n";
						int messagelength = message.getBytes("UTF-8").length;
						if (messagelength%16!=0) {

							SecureRandom random = new SecureRandom();
						      bytes = new byte[16-(messagelength%16)];
						      random.nextBytes(bytes);

						      appended = new byte[messagelength+bytes.length];
						      System.arraycopy(message.getBytes("UTF-8"), 0, appended, 0, messagelength);
						      System.arraycopy(bytes, 0, appended, messagelength, bytes.length);
						} else {
							appended=message.getBytes("UTF-8");
						}

						byte[] encrypted = cipher.doFinal(appended);
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
		 * @throws UnknownHostException 
		 */
		private static HostPort findPeer(String peerIP, int peerPort) throws UnknownHostException {
			Iterator<HostPort> it  = connectedPeers.iterator();
			while(it.hasNext()) {
				HostPort peer = it.next();
				//						System.out.println(peer);
				//						System.out.println((peer.host.equals(peerIP)));
				//						System.out.println((peer.port==peerPort));
				if (peer.host.equals(peerIP)&&peer.port==peerPort) {	
					return peer;
				}
				if (peer.host.equals(InetAddress.getByName(peerIP).getHostAddress())&&peer.port==peerPort) {	
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
//				System.out.println(soc.getInetAddress().getCanonicalHostName()+soc.getPort());
//				System.out.println("soc.getInetAddress().getCanonicalHostName().equals(peerIP)"+soc.getInetAddress().getCanonicalHostName().equals(peerIP));
//				System.out.println("soc.getPort()==peerPort"+(soc.getPort()==peerPort));
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
			if(mode.contentEquals("tcp")) {
				// Check if the peer is in the list
				HostPort hostPort = findPeer(peerIP, peerPort);
//				System.out.println(findPeer(peerIP, peerPort));

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
			}
			
			if(mode.contentEquals("udp")) {
				// Check if the peer is in the list
				HostPort hostPort = findPeer(peerIP, peerPort);
				System.out.println("found"+hostPort);
				if(hostPort!=null) {
					connectedPeers.remove(hostPort);
					System.out.println("after remove"+connectedPeers);
					return true;
				}
			}
			
			return false;


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
		
		/** Connect to a given Peer, complete the handshake and add it to the socket+peer list
		 * @param ser
		 * @param peerIP
		 * @param peerPort
		 *  @return true if successfully connected, false if failed.
		 */
		public static boolean connectPeer(ServerMain ser, String peerIP, int peerPort) {
			if(mode.contentEquals("tcp")) {
				Socket socket = TCP.sentConnectionRequest(peerIP, peerPort, ser);
				if ((socket != null) && (!socket.isClosed())) {
					HostPort hostport = new HostPort(peerIP, peerPort);
					new Thread(() -> TCP.peerRunning(socket, hostport, ser)).start();
					if(!socketList.contains(socket)&&!connectedPeers.contains(hostport)) {
						socketList.add(socket);
						connectedPeers.add(hostport);
						TCP.peerSending(socket, ser);
					}
					return true;
				}
			}

			if(mode.contentEquals("udp")) {
				sendConnectionRequest(socket,peerIP, peerPort);
				int count = 0;
				int tryTimes = 3;  // try to re-send at most 3 times
				try {
					while (count < tryTimes) {
						if (findPeer(peerIP, peerPort)!=null) {
							return true;
						}
						TimeUnit.SECONDS.sleep(5);	
						System.out.println("current peer: "+connectedPeers);
						count++;
					}
					if (count==tryTimes) {
						System.out.println("Connecting:"+peerIP+":"+peerPort+ " time out");
						return false;
					}
				}catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			return false;
		}
}




