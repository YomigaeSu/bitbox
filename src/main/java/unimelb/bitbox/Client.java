package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

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
	private static final String MY_ID= "yilumac@YilusdeMBP-208.gateway";
	//	private static final String MY_ID= "aaron@krusty";
	private static final String PRIVATEKEY_FILE = "bitboxclient_rsa";

	public static void main(String[] args) {
		// Parse the args and store them into argsBean
		CmdLineArgs argsBean = new CmdLineArgs();
		CmdLineParser parser = new CmdLineParser(argsBean);
		try {

			parser.parseArgument(args);
			HostPort server = argsBean.getServer();

			Socket socket;
			try {
				socket = new Socket(server.host, server.port);
				BufferedReader in= new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				// Send Authentication Request
				String message = sendAuthReq();

				out.write(message + "\n");
				out.flush();

				// ============= Receiving Auth response ============= 
				String received = in.readLine();
				System.out.println(received);


				// ============= Getting secrete key from the response =============
				Document authRes = Document.parse(received);
//				getSecretKey(authRes);

				// If succeeds, start communication
				
				// ============= Sending command request to the peer =============
				message =generateCmd(argsBean);
//				// TODO: message need to be encrypted
//				SecretKey secretKey = null;
//				String encrypted = encryptMsg(message,secretKey);

				//				out.write(encrypted + "\n");
				out.write(message + "\n");
				out.flush();
				System.out.println("MESSAGE SENT: "+message);
				
				// ============= Receiving response from the server============= 
				received = in.readLine();
				System.out.println(received);
				

			} catch (IOException e) {
				e.printStackTrace();
			}


		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		}

	}
	



	private static String encryptMsg(String message, SecretKey secretKey) {
		return null;
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

		// >>Theses commands need to be encrypted
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
			//			"command" : "DISCONNECT_PEER_REQUEST",
			//			"host" : "bigdata.cis.unimelb.edu.au",
			//			"port" : 8500
			peer=argsBean.getPeer();
			newCommand.append("command", "DISCONNECT_PEER_REQUEST");
			newCommand.append("host", peer.host);
			newCommand.append("port", peer.port);
			message=newCommand.toJson();
			break;

		default:
			System.out.println("No matched command: " + command);
			break;

		}

		if(message!=null){
			return message;	
		}
		log.warning("The message is empty");
		return null;

	}

	private static void getSecretKey(Document authRes) {
		if (!authRes.getString("command").equals("AUTH_RESPONSE")) {
			log.warning("Response is not AUTH_REPSONSE!");
		}
		String encrypted=authRes.getString("AES128");

		try {
			PrivateKey privateKey = readPrivKey();
			System.out.println(privateKey.getEncoded());

			Cipher rsa = Cipher.getInstance("RSA");
			rsa.init(Cipher.DECRYPT_MODE, privateKey);
			byte[] utf8 = rsa.doFinal(encrypted.getBytes());
			System.out.println(utf8);

		} catch (NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | FileNotFoundException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}


		//			String encodedKey=authRes.getString("AES128");
		//			byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
		////			System.out.println(Base64.getDecoder().decode(encodedKey));
		//			SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

	}

	private static PrivateKey readPrivKey() throws FileNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException {
		Security.addProvider(new BouncyCastleProvider());	
		try {

			BufferedReader br = new BufferedReader(new FileReader(PRIVATEKEY_FILE));	
			PEMParser pp = new PEMParser(br);
			PEMKeyPair pemKeyPair = (PEMKeyPair) pp.readObject();
			KeyPair kp = new JcaPEMKeyConverter().getKeyPair(pemKeyPair);		
			return kp.getPrivate();

			//			File privateKeyFile = new File(PRIVATEKEY_FILE); // private key file in PEM format
			//			PEMParser pemParser = new PEMParser(new FileReader(privateKeyFile));
			//			PEMKeyPair pemKeyPair =(PEMKeyPair) pemParser.readObject();
			//			// === Code for private key with password ===
			//			// PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password);
			//			JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
			//			KeyPair kp = converter.getKeyPair(pemKeyPair);
			//			PrivateKeyInfo pki =pemKeyPair.getPrivateKeyInfo();
			//			System.out.println(kp.getPublic());;
			//            return converter.getPrivateKey(pki);

		} catch (IOException e) {
			e.printStackTrace();
		}
		//	     
		return null;



	}

}


