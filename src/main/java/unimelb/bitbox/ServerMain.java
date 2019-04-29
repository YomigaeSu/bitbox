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
			eventList.add(message);
			break;
			
		case FILE_CREATE:

			// transform fileSystemEvent into JSONObject
			newCommand.append("command", "FILE_CREATE_REQUEST");
			Document document  = (Document) fileSystemEvent.fileDescriptor.toDoc();
			newCommand.append("fileDescriptor", document);
			newCommand.append("pathName", fileSystemEvent.name);
			
			message = newCommand.toJson();
			log.info(message);
			break;
		}
	}

}