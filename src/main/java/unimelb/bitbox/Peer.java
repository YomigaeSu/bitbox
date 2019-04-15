package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;

public class Peer
{
	private static Logger log = Logger.getLogger(Peer.class.getName());
    public static void main( String[] args ) throws IOException, NumberFormatException, NoSuchAlgorithmException
    {
    	System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        Configuration.getConfiguration();
        
        ServerMain ser = new ServerMain();
        
        Thread t1 = new Thread(new Runnable() {
			public void run()
            {
				
				Socket socket = null;
				try {
					// Create a stream socket bounded to any port and connect it to the
					// socket bound to localhost on port 8111
					socket = new Socket("localhost", 8111);
					System.out.println("Connection established");
					

					// Get the input/output streams for reading/writing data from/to the socket
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

					Scanner scanner = new Scanner(System.in);
					String inputStr = null;

					//While the user input differs from "exit"
					while (!(inputStr = scanner.nextLine()).equals("exit")) {
						
						// Send the input string to the server by writing to the socket output stream
						out.write(inputStr + "\n");
						out.flush();
						System.out.println("Message sent");
						
						// Receive the reply from the server by reading from the socket input stream
						String received = in.readLine(); // This method blocks until there
															// is something to read from the
															// input stream
						System.out.println("Message received: " + received);
					}
					
					scanner.close();
					
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					//e.printStackTrace();
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
            public void run()
            {	
            	
            	ServerSocket listeningSocket = null;
        		Socket clientSocket = null;
        		
        		try {
        			//Create a server socket listening on port 8112
        			listeningSocket = new ServerSocket(8112);
        			int i = 0; //counter to keep track of the number of clients
        			
        			SocketAddress add = listeningSocket.getLocalSocketAddress();
        			
        			//Listen for incoming connections for ever
        			while (true) {
        				System.out.println("Server listening on port" + add + " for a connection");
        				//Accept an incoming client connection request 
        				clientSocket = listeningSocket.accept(); //This method will block until a connection request is received
        				i++;
        				System.out.println("Client conection number " + i + " accepted:");
        				System.out.println("Remote Port: " + clientSocket.getPort());
        				System.out.println("Remote Hostname: " + clientSocket.getInetAddress().getHostName());
        				System.out.println("Local Port: " + clientSocket.getLocalPort());
        				
        				//Get the input/output streams for reading/writing data from/to the socket
        				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
        				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));

        				
        				//Read the message from the client and reply
        				//Notice that no other connection can be accepted and processed until the last line of 
        				//code of this loop is executed, incoming connections have to wait until the current
        				//one is processed unless...we use threads!
        				String clientMsg = null;
        				try {
        				while((clientMsg = in.readLine()) != null) {
        					System.out.println("Message from client " + i + ": " + clientMsg);
        					out.write("Server Ack " + clientMsg + "\n");
        					out.flush();
        					System.out.println("Response sent");
        				}}
        				catch(SocketException e) {
        					System.out.println("closed...");
        				}
        				clientSocket.close();
        			}
        		} catch (SocketException ex) {
        			ex.printStackTrace();
        		}catch (IOException e) {
        			e.printStackTrace();
        		} 
        		finally {
        			if(listeningSocket != null) {
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
        
        

        //ser.sentOutMessages();
            
        
    }
    
    
    public void connect() {
    	
    }
}
