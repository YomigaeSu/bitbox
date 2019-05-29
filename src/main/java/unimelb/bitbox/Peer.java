package unimelb.bitbox;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

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
	
	protected static JSONObject responseList = new JSONObject();
	protected static DatagramSocket socket = null;

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ser = new ServerMain();
		int synchornizeTimeInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		String mode = Configuration.getConfigurationValue("mode");
		
		System.out.println(mode);
		if (mode.contentEquals("tcp")) {
			port = Integer.parseInt(Configuration.getConfigurationValue("port"));
			
			// wait for connections--------- this is an independent thread running all the time
			new Thread(() -> TCP.waiting(ser)).start();
			
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
			String key = ((Document) doc.get(fieldName)).toJson();
			responseList.put(key, false);
			new Thread(() -> wait (key, hostport, packet)).start();
		}
		if (fieldName.equals("pathName")) {
			String key = (String) doc.get(fieldName);
			responseList.put(key, false);
			new Thread(() -> wait (key, hostport, packet)).start();
		}
	}
	
	public static void wait (String key, HostPort hostport, DatagramPacket packet) {
		Thread waiting = new Thread(new Runnable() {
			@Override
			public void run() {
				int count = 0;
				int tryTimes = 3;  // try to re-send at most 3 times
				try {
					while (count < tryTimes) {
						TimeUnit.SECONDS.sleep(5);
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
						System.out.println("time out");
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
				            
				            // if a new peer come in, check maximum number
				            if (!connectedPeers.contains(peerHostport)) {
				            	if (connectedPeers.size() < 10) {
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
				            
							String received = new String(packet.getData(), 0, packet.getLength());
							Document command = Document.parse(received);
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
									
									String reply1 = ser.file_bytes_request(command);
									if (!reply1.equals("complete")) {
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
									System.out.println("directory created");
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
										packet = new DatagramPacket(buf, buf.length, peerAddress, port);
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
									des = ((Document) command.get("fileDescriptor")).toJson();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "FILE_DELETE_RESPONSE":
									des = ((Document) command.get("fileDescriptor")).toJson();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "FILE_MODIFY_RESPONSE":
									des = ((Document) command.get("fileDescriptor")).toJson();
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "DIRECTORY_CREATE_RESPONSE":
									des = (String) command.get("pathName");
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
								case "DIRECTORY_DELETE_RESPONSE":
									des = (String) command.get("pathName");
									if (responseList.containsKey(des)) {
										responseList.put(des, true);
									}
									break;
									
								// =========================== other protocols ===============================
								case "CONNECTION_REFUSED":
									des = peerHostport.toString();
									if (responseList.containsKey(des)) {
										responseList.put(des, false);
									}
							    	break;
									
								case "INVALID_PROTOCOL":
									break;
								}
						} 
					} catch (IOException e) {
						System.out.println("error");
					} catch (NoSuchAlgorithmException e1) {
						e1.printStackTrace();
					}
				}
			});
			receive.start();
		}
	
}




