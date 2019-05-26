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

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ser = new ServerMain();
		int synchornizeTimeInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		String mode = Configuration.getConfigurationValue("mode");
		
		System.out.println(mode);
		if (mode.contentEquals("tcp")) {
			// wait for connections--------- this is an independent thread running all the time
			new Thread(() -> TCP.waiting(ser)).start();
			
			// try to connect all the peers in the list
			TCP.connectAllPeers();
			
			// running sync
			TCP.sync(synchornizeTimeInterval, ser);
		}
		
		if (mode.contentEquals("udp")) {
			
			String[] peerList = Configuration.getConfigurationValue("peers").split(",");
			for (String hostPort : peerList) {
				String peerIP = hostPort.split(":")[0];
				int peerPort = Integer.parseInt(hostPort.split(":")[1]);
				HostPort hostport = new HostPort(peerIP, peerPort);
				if (!connectedPeers.contains(hostport)) {
					connectedPeers.add(hostport);
				}
			}
			
			DatagramSocket socket = new DatagramSocket(port);
			
			// peer receiving
			new Thread(() -> peerRunning(socket, ser)).start();
			// peer sending and sync
			sync(socket, synchornizeTimeInterval, ser);
		}
	}
	
	
	// ================================= hear other peers and remember the peers ===================
	public static void peerRunning(DatagramSocket socket, ServerMain ser) {
		Thread receive = new Thread(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				try {
					byte[] buf = new byte[8192];
					while (true) {
						DatagramPacket packet = new DatagramPacket(buf, buf.length);
						socket.receive(packet);
						
						InetAddress address = packet.getAddress();
			            int port = packet.getPort();
			            HostPort hostport = new HostPort(address.toString().substring(1), port);
			            
			            // check maximum number
			            if (!connectedPeers.contains(hostport)) {
			            	if (connectedPeers.size() < 10) {
			            		connectedPeers.add(hostport);
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
								packet = new DatagramPacket(buf, buf.length, address, port);
								socket.send(packet);
			            	}
						}
			            
						String received = new String(packet.getData(), 0, packet.getLength());
						Document command = Document.parse(received);
						System.out.println(command.get("command").toString());
						String reply;
						String des;
						switch (command.get("command").toString()) {
						    // still need 3 cases 1 modify and 2 byte transfer
							case "FILE_CREATE_REQUEST":
								reply = ser.file_create_response(command);
								buf = reply.getBytes();
								packet = new DatagramPacket(buf, buf.length, address, port);
								socket.send(packet);
								System.out.println("file created");
								
								String reply1 = ser.file_bytes_request(command);
								if (!reply1.equals("complete")) {
									buf = reply1.getBytes();
									packet = new DatagramPacket(buf, buf.length, address, port);
									socket.send(packet);
									System.out.println("send file_bytes_request");
								}
								break;
								
							case "FILE_DELETE_REQUEST":
								reply = ser.delete_file(command);
								buf = reply.getBytes();
								packet = new DatagramPacket(buf, buf.length, address, port);
								socket.send(packet);
								System.out.println("file deleted");		
								break;
								
							case "DIRECTORY_DELETE_REQUEST":
								reply = ser.delete_directory(command);
								buf = reply.getBytes();
								packet = new DatagramPacket(buf, buf.length, address, port);
								socket.send(packet);
								System.out.println("ditectory deleted");
								break;
								
							case "DIRECTORY_CREATE_REQUEST":
								reply = ser.create_directory(command);
								buf = reply.getBytes();
								packet = new DatagramPacket(buf, buf.length, address, port);
								socket.send(packet);
								System.out.println("directory created");
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
								des = ((Document) command.get("fileDescriptor")).toJson();
								if (responseList.containsKey(des)) {
									responseList.put(des, true);
								}
								break;
							case "DIRECTORY_DELETE_RESPONSE":
								des = ((Document) command.get("fileDescriptor")).toJson();
								if (responseList.containsKey(des)) {
									responseList.put(des, true);
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
	
	// check connection and retry
	public static void waitResponse (String key, HostPort hostport) {
		Thread waiting = new Thread(new Runnable() {
			@Override
			public void run() {
				int count = 0;
				int tryTimes = 3;  // try to re-send at most 3 times
				try {
					while (count < tryTimes) {
						TimeUnit.SECONDS.sleep(5);
						// check value of key
						if ((boolean) responseList.get(key)) {
							break;
						}
						count++;
					}
					if (count == tryTimes) {
						// disconnect
						responseList.remove(key);
						connectedPeers.remove(hostport);
					} else {
						// recevied the response
						responseList.remove(key);
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		waiting.start();
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
	
	public static void checkMaxConnection() {
		
	}
	
	// send all event in ser eventList to all the remembered peers
	@SuppressWarnings("unchecked")
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
					
					Document doc = Document.parse(s);
					String key = ((Document) doc.get("fileDescriptor")).toJson();
					responseList.put(key, false);
					new Thread(() -> waitResponse (key, hostPort)).start();
					break;
				}
			}
		
		} catch (UnknownHostException e1) {
			// if that host is not there, remove it
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}




