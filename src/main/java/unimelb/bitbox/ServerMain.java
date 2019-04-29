package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.json.simple.JSONObject;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	public ArrayList<String> eventList;

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
		eventList = new ArrayList<String>();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		switch (fileSystemEvent.event) {
		/*case FILE_MODIFY:

			// transform fileSystemEvent into Document(JSONObject)
			newCommand.append("command", "FILE_MODIFY_REQUEST");
			// Read fileDescriptor from fileSystemEvent
			Document fileDescriptor = (Document) fileSystemEvent.fileDescriptor.toDoc();
			// transform fileDesciptor Doc into JSONObject
			newCommand.append("fileDescriptor", fileDescriptor);
			newCommand.append("pathName", fileSystemEvent.name);

			message = newCommand.toJson();
			log.info(message);
			eventList.add(message);
			break;*/
			
		case FILE_MODIFY:

			// transform fileSystemEvent into JSONObject
			JSONObject message = new JSONObject();
			message.put("command", "FILE_MODIFY_REQUEST");

			Document document = (Document) fileSystemEvent.fileDescriptor.toDoc();
			
			// transform fileDesciptor Doc into JSONObject
			JSONObject fileDescriptor = new JSONObject();
			fileDescriptor.put("md5", document.get("md5"));
			fileDescriptor.put("lastModified", document.get("lastModified"));
			fileDescriptor.put("fileSize", document.get("fileSize"));
			
			message.put("fileDescriptor", fileDescriptor);
			message.put("pathName", fileSystemEvent.name);

			String protocol = message.toJSONString();
			log.info(protocol);
			eventList.add(protocol);
			break;
			
	case FILE_CREATE:

			 // transform fileSystemEvent into JSONObject
			 JSONObject message1 = new JSONObject();
			 message1.put("command", "FILE_CREATE_REQUEST");

			 Document document1  = (Document) fileSystemEvent.fileDescriptor.toDoc();
			 
			 // transform fileDesciptor Doc into JSONObject
			 JSONObject fileDescriptor1 = new JSONObject();
			 fileDescriptor1.put("md5", document1.get("md5"));
			 fileDescriptor1.put("lastModified", document1.get("lastModified"));
			 fileDescriptor1.put("fileSize", document1.get("fileSize"));
			 
			 message1.put("fileDescriptor", fileDescriptor1);
			 message1.put("pathName", fileSystemEvent.name);
			      
			 String protocol1 = message1.toJSONString();
		     log.info(protocol1);
		     eventList.add(protocol1);
			 break; 
					
		case FILE_DELETE:
			
			//transform fileSystemEvent to JSONObject 
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
			eventList.add(protocol2);
			break;
			
		case DIRECTORY_CREATE:
			
			//transform fileSystemEvent to JSONObject 
			JSONObject message3 = new JSONObject();
			message3.put("command", "DIRECTORY_CREATE_REQUEST");
			message3.put("pathname", fileSystemEvent.name);
			
			String protocol3 = message3.toJSONString();
			log.info(protocol3);
			eventList.add(protocol3);
			break;
			
		case DIRECTORY_DELETE:
			
			//transform fileSystemEvent to JSONObject 
			JSONObject message4 = new JSONObject();
			message4.put("pathname", fileSystemEvent.name);
			message4.put("command", "DIRECTORY_DELETE_REQUEST");
			
			String protocol4 = message4.toJSONString();
			log.info(protocol4);
			eventList.add(protocol4);
			break;	
			
		}
	}

}