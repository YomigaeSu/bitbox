package unimelb.bitbox;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.swing.CellRendererPane;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	public ArrayList<String> eventList;
	public ConnectedPeers connectedPeers;

	
	

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
		eventList = new ArrayList<String>();
		connectedPeers= new ConnectedPeers();
	}
	
	

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		Document newCommand = new Document();
		String message = new String();
		switch (fileSystemEvent.event) {
		case FILE_MODIFY:

			// transform fileSystemEvent into Document(JSONObject)
			newCommand.append("command", "FILE_MODIFY_REQUEST");
			// Read fileDescriptor from fileSystemEvent
			Document fileDescriptor = (Document) fileSystemEvent.fileDescriptor.toDoc();
			// transform fileDesciptor Doc into JSONObject
			newCommand.append("fileDescriptor", fileDescriptor);
			newCommand.append("pathName", fileSystemEvent.name);

			message = newCommand.toJson();
			log.info(message);
			break;

		case FILE_CREATE:

			// transform fileSystemEvent into JSONObject
			newCommand.append("command", "FILE_CREATE_REQUEST");
			Document document = (Document) fileSystemEvent.fileDescriptor.toDoc();
			newCommand.append("fileDescriptor", document);
			newCommand.append("pathName", fileSystemEvent.name);

			message = newCommand.toJson();
			log.info(message);
			break;
			
		case FILE_DELETE:

			// transform fileSystemEvent to JSONObject
			JSONObject message2 = new JSONObject();
			message2.put("command", "FILE_DELETE_REQUEST");

			Document document2 = (Document) fileSystemEvent.fileDescriptor.toDoc();

			// transform fileDesciptor Doc into JSONObject
			JSONObject fileDescriptor2 = new JSONObject();
			fileDescriptor2.put("md5", document2.get("md5"));
			fileDescriptor2.put("lastModified", document2.get("lastModified"));
			fileDescriptor2.put("filesize", document2.get("fileSize"));

			message2.put("fileDescriptor", fileDescriptor2);
			message2.put("pathname", fileSystemEvent.name);

			String protocol2 = message2.toJSONString();
			log.info(protocol2);
			eventList.add(message2.toString());

			break;

		case DIRECTORY_CREATE:

			// transform fileSystemEvent to JSONObject
			JSONObject message3 = new JSONObject();
			message3.put("command", "DIRECTORY_CREATE_REQUEST");
			message3.put("pathname", fileSystemEvent.pathName);

			String protocol3 = message3.toJSONString();
			log.info(protocol3);
			eventList.add(message3.toString());

			break;

		case DIRECTORY_DELETE:

			// transform fileSystemEvent to JSONObject
			JSONObject message4 = new JSONObject();
			message4.put("pathname", fileSystemEvent.pathName);
			message4.put("command", "DIRECTORY_DELETE_REQUEST");

			String protocol4 = message4.toJSONString();
			log.info(protocol4);
			eventList.add(message4.toString());

			break;
		}
	}
	
	public String byte_request(JSONObject message) throws NoSuchAlgorithmException, IOException {
		JSONObject response = new JSONObject();		
		long length = 6;
		long lastModified = 1556363147448L;
		String md5 = "299614d7f27cc981f3ad1f7be45c7087";
		if (fileSystemManager.createFileLoader("test.txt", md5, length, lastModified) == true) {
			if (fileSystemManager.checkShortcut("test.txt") == false) {
				response.put("command", "FILE_BYTES_REQUEST");
				JSONObject des = new JSONObject();
				des.put("md5", md5);
				des.put("lastModified", lastModified);
				des.put("fileSize", length);
				response.put("fileDescriptor", des);
				response.put("pathName", "test.txt");
				response.put("position", 0);
				response.put("length", 6);
			}
		}	
		
		return response.toJSONString();
	}
	
	public String byte_response(JSONObject message) throws NoSuchAlgorithmException, IOException, ParseException {

		JSONObject response = new JSONObject();
		
		JSONParser parser1 = new JSONParser();
		String descriptor = message.get("fileDescriptor").toString();
		JSONObject des = (JSONObject) parser1.parse(descriptor);
		String fileName = (String) message.get("pathName");
		File f = new File("share/"+fileName);	
		
		response.put("pathName", (String)message.get("pathName"));
		response.put("command", "FILE_BYTES_RESPONSE");
		response.put("fileDescriptor",des);
		response.put("length",(long)message.get("length"));
		response.put("position",(long)message.get("position"));
		response.put("message", "successful read");
		response.put("status", true);
		ByteBuffer sendingBuffer = fileSystemManager.readFile((String)des.get("md5"), (long)message.get("position"), (long)message.get("length"));
		sendingBuffer.flip();
		String s = StandardCharsets.UTF_8.decode(sendingBuffer).toString();
		response.put("content", s);
		
		return response.toJSONString();
	}
	

	public String write_byte(JSONObject message) throws IOException, NoSuchAlgorithmException, ParseException {
		String result = "";
		JSONParser parser1 = new JSONParser();
		String descriptor = message.get("fileDescriptor").toString();
		JSONObject des = (JSONObject) parser1.parse(descriptor);
		String buffer_str = (String)message.get("content");
		ByteBuffer babb = Charset.forName("UTF-8").encode(buffer_str);
		long position = (long)message.get("position") + (long)message.get("length");
		
		if (fileSystemManager.writeFile((String)message.get("pathName"), babb, (long)message.get("position")) == true) {
		
			if ((long)message.get("position") <= ((long)des.get("fileSize")-(long)message.get("length")*2)) {
				JSONObject response = new JSONObject();	

				response.put("command", "FILE_BYTES_REQUEST");
				response.put("fileDescriptor", des);
				response.put("pathName", (String)message.get("pathName"));
				response.put("length", (long)message.get("length"));
				response.put("position", position);
				
				result = response.toJSONString();
				
			} else if ((long)message.get("position") < ((long)des.get("fileSize")-(long)message.get("length"))) {
				JSONObject response = new JSONObject();	

				response.put("command", "FILE_BYTES_REQUEST");
				response.put("fileDescriptor", des);
				response.put("pathName", (String)message.get("pathName"));
				response.put("length", (long)des.get("fileSize")-position);
				response.put("position", position);
				
				result = response.toJSONString();
			}	else if (((long)message.get("position") == ((long)des.get("fileSize")-(long)message.get("length")))) {
				if (fileSystemManager.checkWriteComplete((String)message.get("pathName")) == true ) {
					if ( fileSystemManager.cancelFileLoader((String)message.get("pathName")) == true ) {
					System.out.println("done");
					result = "done";
					} else {
						System.out.println("wrong");
					}
				} 
			}
			} 
		return result;
	}
	
	
	public class ConnectedPeers{
		private ArrayList<HostPort> peerList = new ArrayList<>();
		private ArrayList<PeerRunning> peerRunnings = new ArrayList<>();
		// Add a new peerThread and store its peerList
		public boolean add(PeerRunning peerRunning) {
			return peerRunnings.add(peerRunning) && peerList.add(peerRunning.getHostPort());
		}
		
		public boolean remove(HostPort hostPort) {
			for (PeerRunning peerThread : peerRunnings) {
				if (peerThread.getHostPort().equals(hostPort)) {
					peerRunnings.remove(peerThread);
					peerList.remove(hostPort);
					return true;
				}
			}
			return false;
		}
		
		public boolean contains(HostPort hostPort) {
			return peerList.contains(hostPort);
		}
		/** Get the size of the HostPorts ArrayList
		 * @return
		 */
		public int getSize() {
			return peerList.size();
			}

	}
	
	// Get the hostPort
	public ArrayList<HostPort> getPeerList(){
		return connectedPeers.peerList;
	}
	
	public void sendToAllPeers(String message) {
		ArrayList<PeerRunning> peerRunnings =connectedPeers.peerRunnings;
		int count=0;
		for (PeerRunning peerRunning : peerRunnings) {
			peerRunning.send(message);
			count ++;
		}
		System.out.println("Message: "+message+" sent to"+count+"peers.");
	}





}
