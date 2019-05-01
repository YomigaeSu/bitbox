package unimelb.bitbox;

import java.awt.Event;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.experimental.theories.Theories;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class PeerRunning extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());

	// Internal
	private Socket socket;
	private HostPort hostPort;
	private ServerMain ser;
	private DataInputStream in;
	private DataOutputStream out;
	private ArrayList<String> messageList;

	public PeerRunning(Socket socket, HostPort hostPort, ServerMain ser) {
		this.socket = socket;
		this.hostPort = hostPort;
		this.ser = ser;
		messageList = new ArrayList<>();
		try {
			this.in = new DataInputStream(socket.getInputStream());
			this.out = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// ===Initial Synchronization: Send out CREATE_REQUEST for all local files===
		ArrayList<FileSystemEvent> pathevents = ser.fileSystemManager.generateSyncEvents();

		for (FileSystemEvent pathevent : pathevents) {
			Document newCommand = new Document();
			String message = new String();
			switch (pathevent.event) {
			case DIRECTORY_CREATE:

				// transform fileSystemEvent to JSONObject
				JSONObject message3 = new JSONObject();
				message3.put("command", "DIRECTORY_CREATE_REQUEST");
				message3.put("pathname", pathevent.pathName);

				// messageList.add(message3.toString());
				// try {
				// out.writeUTF(message3.toString());
				// out.flush();
				// } catch (IOException e) {
				// e.printStackTrace();
				// }
				send(message3.toString());

				break;

			case FILE_CREATE:

				// transform fileSystemEvent into JSONObject
				newCommand.append("command", "FILE_CREATE_REQUEST");
				Document document = (Document) pathevent.fileDescriptor.toDoc();
				newCommand.append("fileDescriptor", document);
				newCommand.append("pathName", pathevent.name);
				message = newCommand.toJson();

				// messageList.add(message);
				// out.writeUTF(message);
				// out.flush();
				send(message);
				break;
			}
			// === Start sending and receiving messages
			start();

		}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

		try {

			while (true) {
				// to check whether the other peer is shutdown or not
				// Iterator<Socket> iter = socketList.iterator();
				try {
					Document newCommand = new Document();
					newCommand.append("command", "CHECK");
					out.writeUTF(newCommand.toJson());
					out.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					ser.connectedPeers.remove(hostPort);
					break;
				}

				// if we have message need to send out
				while (messageList.size() > 0) {
					String s = messageList.get(0);
					System.out.println(s);
					out.writeUTF(s);
					out.flush();
					ser.eventList.remove(0);
				}

				// System.out.println(ser.eventList);
				// if we have message coming in
				if (in.available() > 0) {
					String received = in.readUTF();
					Document command = Document.parse(received);
					JSONParser parser = new JSONParser();
					// Prepare the reply string for the peer
					String reply;
					// switch based on command type
					switch (command.get("command").toString()) {

					case "CHECK":
						// just ignore for now
						break;

					// case "FILE_MODIFY_REQUEST":
					// System.out.println(command.toJson());
					// //message = command.get("command").toString();
					// System.out.println("we need send this message to serverMain and let it modify
					// our local file");
					// break;
					//

					case "DIRECTORY_CREATE_REQUEST":
						System.out.println(command.toJson());
						System.out.println("Requesting Servermain to create a local directory.");
						break;

					// case "FILE_CREATE_REQUEST":
					// System.out.println(command.toJson());
					// System.out.println("Requesting servermain to create a local file");
					// try {
					// String reply1 = ser.file_create_response(command);
					// out.writeUTF(reply1);
					// out.flush();
					// System.out.println("COMMAND SENT: " + reply1);
					//
					// } catch (NoSuchAlgorithmException e1) {
					// e1.printStackTrace();
					// }
					// break;

					case "DIRECTORY_DELETE_REQUEST":
						System.out.println(command.toJson());
						System.out.println("Requesting Servermain to delete our local directory.");
						break;

					case "FILE_DELETE_REQUEST":
						System.out.println(command.toJson());
						System.out.println("Requesting Servermain to delete our local file.");
						break;

					case "FILE_BYTE_RESPONSE":
						System.out.println(command.toJson());
						String reply1;
						try {
							reply1 = ser.write_byte((JSONObject) parser.parse(received));
							if (reply1.equals("complete")) {
								break;
							} else {
								out.writeUTF(reply1);
								out.flush();
								System.out.println("COMMAND SENT: " + reply1);
							}
						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						break;

					case "FILE_BYTE_REQUEST":
						String byte_response;
						try {
							byte_response = ser.byte_response((JSONObject) parser.parse(received));
							out.writeUTF(byte_response);
							out.flush();
							System.out.println("COMMAND SENT: " + byte_response);
						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						break;

					default:
						System.out.println("COMMAND RECEIVED: " + received);
						System.out.println("Running: No matched protocol");
						break;
					}
				}

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

	public boolean send(String message) {
		try {
			out.writeUTF(message);
			out.flush();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

	}

	public HostPort getHostPort() {
		return hostPort;
	}

}
