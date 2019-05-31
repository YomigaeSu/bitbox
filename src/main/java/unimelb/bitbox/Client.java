package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import unimelb.bitbox.util.CmdLineArgs;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
public class Client {
	private static Logger log = Logger.getLogger(Configuration.class.getName());
	private static final String MY_ID= "yilu@unimelb";
	//	private static final String MY_ID= "aaron@krusty";
	private static final String PRIVATEKEY_FILE = "bitboxclient_rsa";
	
	private static SecretKey secretKey;

	public static void main(String[] args) {
		// Parse the args and store them into argsBean
		CmdLineArgs argsBean = new CmdLineArgs();
		CmdLineParser parser = new CmdLineParser(argsBean);
		try {

			parser.parseArgument(args);
			HostPort server = argsBean.getServer();
			String CmdMsg = generateCmd(argsBean);
			if(CmdMsg==null) {
				// command incorrect, ends program
				return;
			}

			Socket socket;
			try {
				socket = new Socket(server.host, server.port);
				BufferedReader in= new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				
				// ============= Sending Authentication Request  ============= 
				String message = sendAuthReq();
				out.write(message + "\n");
				out.flush();
				log.info("sent "+message);

				// ============= Receiving Auth response ============= 
				String received = in.readLine();
				log.info("received "+received);
				Document authRes = Document.parse(received);
				if (!authRes.getString("command").equals("AUTH_RESPONSE")) {
					log.warning(received);
					System.out.println("Invalid command field in response: "+ authRes.getString("command"));
					return;
				}

				// ============= Getting the secrete key from the response =============
				if(!authRes.getBoolean("status")) {
					log.warning(received);
					System.out.println("Public key not found, check your identity: "+MY_ID);
					return;
				}
				getSecretKey(authRes);
				
				// If successfully read key into secretKey, starts communication
				
				// ============= Sending command request to the peer =============
				String encrypted = encryptMsg(CmdMsg,secretKey);
				
				Document newCommand = new Document();
				newCommand.append("payload", encrypted);
				message=newCommand.toJson();

				out.write(message + "\n");
				out.flush();
				log.info("sent "+message);
				
				// ============= Receiving response from the server============= 
				received = in.readLine();
				log.info("received "+received);
				encrypted = Document.parse(received).getString("payload");
				String decrypted = decryptMsg(encrypted, secretKey);
				
				// ============= Printing out the output=============
				String output = printResult(decrypted);
				
				System.out.println(output);

			} catch (IOException e) {
				e.printStackTrace();
			}


		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		}

	}


	private static String sendAuthReq() {
		Document command = new Document();
		command.append("command", "AUTH_REQUEST");
		command.append("identity" , MY_ID);

		return command.toJson();

	}



	private static String generateCmd(CmdLineArgs argsBean) {
		String command =argsBean.getCommand();
		HostPort peer;

		String message = null;
		Document newCommand=new Document();
		switch (command) {
		case "list_peers":
			newCommand.append("command", "LIST_PEERS_REQUEST");
			message=newCommand.toJson();
			break;

		case "connect_peer":
			peer=argsBean.getPeer();
			newCommand.append("command", "CONNECT_PEER_REQUEST");
			newCommand.append("host", peer.host);
			newCommand.append("port", peer.port);
			message=newCommand.toJson();
			break;

		case "disconnect_peer":
			peer=argsBean.getPeer();
			newCommand.append("command", "DISCONNECT_PEER_REQUEST");
			newCommand.append("host", peer.host);
			newCommand.append("port", peer.port);
			message=newCommand.toJson();
			break;

		default:
			System.out.println("No command matched with: " + command+"\nTry: \tlist_peers\tconnect_peer\tdisconnect_peer");
			break;

		}

		if(message!=null){
			return message;	
		}
		return null;

	}

	private static void getSecretKey(Document authRes) {
		
		String encrypted=authRes.getString("AES128");
		// ============= RSA Decryption=============
		try {
			RSAPrivateKey privateKey = readPrivKey();
			
			Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsa.init(Cipher.DECRYPT_MODE, privateKey);
			byte[] decodedKey = rsa.doFinal(Base64.getDecoder().decode(encrypted));
			secretKey = (SecretKey)new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
		} catch (NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | FileNotFoundException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
//		// Getting the Secret key without using RSA
//		byte[] encodedKey=authRes.getString("AES128").getBytes();
//		byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
//		secretKey = (SecretKey)new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

	}

	private static RSAPrivateKey readPrivKey() throws FileNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException {
			Security.addProvider(new BouncyCastleProvider());	
			try {
		
	//			BufferedReader br = new BufferedReader(new FileReader(PRIVATEKEY_FILE));	
				PEMParser pemParser = new PEMParser(new FileReader(PRIVATEKEY_FILE));
				JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
				PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();
				KeyPair kp = converter.getKeyPair(pemKeyPair);	
				
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				RSAPrivateCrtKeySpec keySpec = keyFactory.getKeySpec(kp.getPrivate(), RSAPrivateCrtKeySpec.class);
				RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
//				System.out.println(privateKey);
				return privateKey;
		
		
			} catch (IOException e) {
				e.printStackTrace();
			}
			//	     
			return null;
		
		}


	private static String encryptMsg(String message, SecretKey secretKey) {
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
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				e.printStackTrace();
			} catch (BadPaddingException e) {
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			
			
			return null;
		}









	private static String decryptMsg(String encrypted, SecretKey secretKey) {
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
	
	/** Converts the received message into a single line output
	 * @param received
	 * @return
	 */
	private static String printResult(String received) {
		Document command = Document.parse(received);
		String output = "";
		switch (command.getString("command")) {
		case "LIST_PEERS_RESPONSE":
			ArrayList<Document> peers = (ArrayList<Document>) command.get("peers");
			if(peers.size()>1) {
				output = "Connected to "+peers.size()+" peers\n";
			}else {
				output ="Connected to "+peers.size()+" peer\n";
			}
			for (Document peer : peers) {
				HostPort hostPort = new HostPort(peer);					
				output+=hostPort.toString()+"\n";
			}
			//				output = output.substring(0,output.length()-2);

			break;

		case "CONNECT_PEER_RESPONSE":
			output = command.getString("message");

			break;
			
		case "DISCONNECT_PEER_RESPONSE":
			output = command.getString("message");
			break;

		default:
			break;
		}
		return output;
	}

}


