package unimelb.bitbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.Configuration;

public class Peer {
	private static Logger log = Logger.getLogger(Peer.class.getName());

	// Hard coded the ip addresses and ports for test purpose
	// Peer1 settings: port 8111; peerPort 8112
<<<<<<< HEAD
	// Peer2 settings: port 8112; peerPort 8113
=======
	// Peer2 settings: port 8112; peerPort 8111
>>>>>>> origin/master
	private static String ip = "localhost";
	private static int port = 8111;
	private static String peerIp = "localhost";
	private static int peerPort = 8112;

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();

		ServerMain ser = new ServerMain();

		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {

				Socket socket = null;
				try {
					// Create a stream socket bounded to any port and connect it to the
					// socket bound to localhost on port 8111
					socket = new Socket(peerIp, peerPort);
					System.out.println("Working as clientPeer: Connection established");

					// Get the input/output streams for reading/writing data from/to the socket
					DataInputStream in = new DataInputStream(socket.getInputStream());
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());

//					out.writeUTF("Peer hosting on" + ip + ":" + port);
//					out.flush();

					// Send a HANDSHAKE_REQUEST to another peer by writing to the socket output
					// stream
					JSONObject request = new JSONObject();
					request.put("command", "HANDSHAKE_REQUEST");
					JSONObject hostPort = new JSONObject();
					hostPort.put("host", ip);
					hostPort.put("port", Integer.toString(port));
					// Write {"hostPort":{"host","localhost","port","8118"}} into request
					request.put("hostPort", hostPort);

					out.writeUTF(request.toJSONString());
					out.flush();
					System.out.println("COMMAND SENT: " + request.toJSONString());

					// Waiting for the server peer's response
					while (true) {

						// Receive the reply from the server peer by reading from the socket input
						// stream
						if (in.available() > 0) {

							String received = in.readUTF();
							System.out.println("COMMAND RECEIVED: " + received);

							// Marshal the result into JSONObject
							JSONParser parser = new JSONParser();
							JSONObject message = (JSONObject) parser.parse(received);

						}

					}

				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					// e.printStackTrace();
				} catch (ParseException e) {
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
		});

		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {

				ServerSocket listeningSocket = null;
				Socket clientSocket = null;

				try {
					// Create a server socket listening on port 8112
					listeningSocket = new ServerSocket(port);
					int i = 0; // counter to keep track of the number of clients

//					SocketAddress add = listeningSocket.getLocalSocketAddress();

					// Listen for incoming connections for ever
					while (true) {
						System.out.println("Server listening on" + ip + ":" + port + " for a connection");

						// Accept an incoming client connection request
						clientSocket = listeningSocket.accept(); // This method will block until a connection request is
																	// received
						i++;
						System.out.println("Working as serverPeer: Client conection number " + i + " accepted:");
//						System.out.println("Remote Port: " + clientSocket.getPort());
//						System.out.println("Remote Hostname: " + clientSocket.getInetAddress().getHostName());
//						System.out.println("Local Port: " + clientSocket.getLocalPort());

						// Get the input/output streams for reading/writing data from/to the socket
						DataInputStream in = new DataInputStream(clientSocket.getInputStream());
						DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

						// Read the HANDSHAKE_REQUEST and reply
						// Notice that no other connection can be accepted and processed until the last
						// line of
						// code of this loop is executed, incoming connections have to wait until the
						// current
						// one is processed unless...we use threads!
						// String clientMsg = null;
						try {
							// The JSON Parser
							JSONParser parser = new JSONParser();

							// Receiving more data
							while (true) {
								if (in.available() > 0) {
									// Attempt to convert read data to JSON
									String received = in.readUTF();
									System.out.println("COMMAND RECEIVED: " + received);
									JSONObject command = (JSONObject) parser.parse(received);

									// Generate a HANDSHAKE_RESPONSE
									String message = handleHandshake(command);
									out.writeUTF(message);
									out.flush();
									System.out.println("COMMAND SENT: " + message);

								}

							}
						} catch (SocketException e) {
							System.out.println("closed...");
						} catch (ParseException e) {
							e.printStackTrace();
						}
						clientSocket.close();
					}
				} catch (SocketException ex) {
					ex.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (listeningSocket != null) {
						try {
							listeningSocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});

		t1.start();
		t2.start();

		// ser.sentOutMessages();

	}

	/**
	 * If the received message is a HANDSHAKE_REQUEST, generate a HANDSHAKE_RESPONSE
	 * and marshal it to JSONString
	 * 
	 * @param message The massage received
	 * @param out     Data Output Stream bound to the socket
	 */
	private static String handleHandshake(JSONObject message) {
		// TODO The method should also check if the Handshake has been completed
		// If yes, send a INVALID_PROTOCOL

		// A placeholder for the outgoing command message.
		JSONObject response = new JSONObject();

		if (message.get("command").equals("HANDSHAKE_REQUEST")) {

			// The message received is a Handshake request
			// Generate a Handshake response JSONO
			response.put("command", "HANDSHAKE_RESPONSE");
			JSONObject hostPort = new JSONObject();
			hostPort.put("host", ip);
			hostPort.put("port", port);
			// Added {"hosrPort":{"host":"localhost","port":8112}} to the handshake response
			response.put("hostPort", hostPort);
		} else {
			// If the message doesn't have command field, send INVALID_PROTOCOL
			response.put("command", "INVALID_PROTOCOL");
			response.put("message", "message must contain a command field as string");
		}
		return response.toJSONString();
	}

	public void connect() {

	}
}
