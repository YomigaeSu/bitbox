package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
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

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		switch (fileSystemEvent.event) {
		case FILE_MODIFY:

			// transform fileSystemEvent into JSONObject
			JSONObject message = new JSONObject();
			message.put("command", "FILE_MODIFY_REQUEST");

			Document doc = (Document) fileSystemEvent.fileDescriptor.toDoc();
			// transform fileDesciptor Doc into JSONObject
			JSONObject fileDescriptor = new JSONObject();
			fileDescriptor.put("md5", doc.get("md5"));
			fileDescriptor.put("lastModified", doc.get("lastModified"));
			fileDescriptor.put("fileSize", doc.get("fileSize"));
			message.put("fileDescriptor", fileDescriptor);

			message.put("pathName", fileSystemEvent.name);

			String protocol = message.toJSONString();
			log.info(protocol);

			break;
		}
	}

}
