package unimelb.bitbox;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import antlr.CharBuffer;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import java.util.ArrayList;
import java.util.Base64;


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
		switch (fileSystemEvent.event) {
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
			message.put("pathName", fileSystemEvent.pathName);

			String protocol = message.toJSONString();
			eventList.add(message.toString());
			

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
			 message1.put("pathName", fileSystemEvent.pathName);
			      
			 String protocol1 = message1.toJSONString();
		     eventList.add(message1.toString());
					
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
			message2.put("pathName", fileSystemEvent.pathName);
			
			String protocol2 = message2.toJSONString();
			eventList.add(message2.toString());
			
			break;
			
		case DIRECTORY_CREATE:
			
			//transform fileSystemEvent to JSONObject 
			JSONObject message3 = new JSONObject();
			message3.put("command", "DIRECTORY_CREATE_REQUEST");
			message3.put("pathName", fileSystemEvent.pathName);
			
			String protocol3 = message3.toJSONString();
			eventList.add(message3.toString());
			
			break;
			
		case DIRECTORY_DELETE:
			
			//transform fileSystemEvent to JSONObject 
			JSONObject message4 = new JSONObject();
			message4.put("pathName", fileSystemEvent.pathName);
			message4.put("command", "DIRECTORY_DELETE_REQUEST");
			
			String protocol4 = message4.toJSONString();
			eventList.add(message4.toString());
			
			break;
		}
	};
	
	public String file_create_response(Document message) throws NoSuchAlgorithmException, IOException {
		Document response = new Document();		
		Document des =(Document) message.get("fileDescriptor");
		String pathName = (String)message.get("pathName");
		long length = (long)des.get("fileSize");
		long lastModified = (long)des.get("lastModified");
		String md5 = (String)des.get("md5");
		long blockSize =  Long.parseLong(Configuration.getConfigurationValue("blockSize"));
		if (fileSystemManager.fileNameExists(pathName) == false) {
		if (fileSystemManager.createFileLoader(pathName, md5, length, lastModified) == true) {
				response.append("command", "FILE_CREATE_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", pathName);
				response.append("message", "file loader ready");
				response.append("status", true);			
				System.out.println("1");
		} else {
			response.append("command", "FILE_CREATE_RESPONSE");
			response.append("fileDescriptor", des);
			response.append("pathName", pathName);
			response.append("message", "there was a problem creating the file");
			response.append("status", false); 
			System.out.println("2");
		}
		} else {
			if (fileSystemManager.modifyFileLoader(pathName, md5, length, lastModified) == true) {
					response.append("command", "FILE_CREATE_RESPONSE");
					response.append("fileDescriptor", des);
					response.append("pathName", pathName);
					response.append("message", "file loader ready");
					response.append("status", true);	
					System.out.println("3");
			} else {
				response.append("command", "FILE_CREATE_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", pathName);
				response.append("message", "there was a problem creating the file");
				response.append("status", false);
				System.out.println("4");
			}
			
		}
		return response.toJson();
		
	}
	
	public String file_bytes_request(Document message)  {
		String result = "";
		Document response = new Document();		
		Document des =(Document) message.get("fileDescriptor");
		String pathName = (String)message.get("pathName");
		long length = (long)des.get("fileSize");
		long lastModified = (long)des.get("lastModified");
		String md5 = (String)des.get("md5");
		long blockSize =  Long.parseLong(Configuration.getConfigurationValue("blockSize"));
		try {
		if (fileSystemManager.checkWriteComplete(pathName) == true ) {
				result = "complete";
			} else {
				if(blockSize > length) {
					response.append("command", "FILE_BYTES_REQUEST");			
					response.append("fileDescriptor", des);
					response.append("pathName", pathName);
					response.append("position", 0);
					response.append("length", length);
					result = response.toJson();
					
				} else {
					response.append("command", "FILE_BYTES_REQUEST");			
					response.append("fileDescriptor", des);
					response.append("pathName", pathName);
					response.append("position", 0);
					response.append("length", blockSize);
					result = response.toJson();
				}
			}
		 } catch(Exception e) {
			 result = "complete";
		 }

		return result;
		
	}
	
	public String file_modify_response(Document message) throws NoSuchAlgorithmException, IOException {
		Document response = new Document();		
		Document des =(Document) message.get("fileDescriptor");
		String pathName = (String)message.get("pathName");
		long length = (long)des.get("fileSize");
		long lastModified = (long)des.get("lastModified");
		String md5 = (String)des.get("md5");
		if (fileSystemManager.modifyFileLoader(pathName, md5, length, lastModified) == true) {
				response.append("command", "FILE_MODIFY_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", pathName);
				response.append("message", "file loader ready");
				response.append("status", true);
		} else {
			response.append("command", "FILE_MODIFY_RESPONSE");
			response.append("fileDescriptor", des);
			response.append("pathName", pathName);
			response.append("message", "there was a problem modifying the file");
			response.append("status", false);
		}
		return response.toJson();
		
	}
	
	public String byte_request(Document message) throws NoSuchAlgorithmException, IOException {
		Document response = new Document();		
		Document des =(Document) message.get("fileDescriptor");
		String pathName = (String)message.get("pathName");
		long lastModified = (long)des.get("lastModified");
		long length = (long)des.get("fileSize");
		String md5 = (String)des.get("md5");	
		long blockSize =  Long.parseLong(Configuration.getConfigurationValue("blockSize"));
		if ((long)des.get("fileSize")!=0) {
			if (fileSystemManager.checkShortcut("test.txt") == false) {
				if(blockSize > length) {
					response.append("command", "FILE_BYTES_REQUEST");			
					response.append("fileDescriptor", des);
					response.append("pathName", pathName);
					response.append("position", 0);
					response.append("length", length);
				} else {
					response.append("command", "FILE_BYTES_REQUEST");			
					response.append("fileDescriptor", des);
					response.append("pathName", pathName);
					response.append("position", 0);
					response.append("length", blockSize);
				}
			}
		} else {
			
		}
					
		return response.toJson();
	}
	
	public String byte_response(Document command) throws NoSuchAlgorithmException, IOException, ParseException {
		Document des = (Document)command.get("fileDescriptor");
		String fileName = (String) command.get("pathName");
		File f = new File("share/"+fileName);
		
		Document response = new Document();
		response.append("command", "FILE_BYTES_RESPONSE");
		response.append("fileDescriptor",des);
		response.append("pathName", fileName);
		response.append("position",(long)command.get("position"));
		response.append("length",(long)command.get("length"));
		response.append("message", "successful read");
		response.append("status", true);
		
		ByteBuffer sendingBuffer = fileSystemManager.readFile((String)des.get("md5"), (long)command.get("position"), (long)command.get("length"));
		String encoded = Base64.getEncoder().encodeToString(sendingBuffer.array());
		response.append("content", encoded);
		
		System.out.println(response.toJson());
		return response.toJson();
	}
	

	public String write_byte(Document message) throws IOException, NoSuchAlgorithmException, ParseException {
		String result = "";
		Document des = (Document)message.get("fileDescriptor");
		String buffer_str = (String)message.get("content");
		
		byte [] barr = Base64.getDecoder().decode(buffer_str);
		
		ByteBuffer content = ByteBuffer.wrap(barr);
		long position = (long)message.get("position") + (long)message.get("length");
		
		if (fileSystemManager.writeFile((String)message.get("pathName"), content, (long)message.get("position")) == true) {
		
			if ((long)message.get("position") <= ((long)des.get("fileSize")-(long)message.get("length")*2)) {
				Document response = new Document();		

				response.append("command", "FILE_BYTES_REQUEST");
				response.append("fileDescriptor", des);
				response.append("pathName", (String)message.get("pathName"));
				response.append("length", (long)message.get("length"));
				response.append("position", position);
				
				result = response.toJson();
				
			} else if ((long)message.get("position") < ((long)des.get("fileSize")-(long)message.get("length"))) {
				Document response = new Document();	

				response.append("command", "FILE_BYTES_REQUEST");
				response.append("fileDescriptor", des);
				response.append("pathName", (String)message.get("pathName"));
				response.append("length", (long)des.get("fileSize")-position);
				response.append("position", position);
				
				result = response.toJson();
			}	else if (((long)message.get("position") == ((long)des.get("fileSize")-(long)message.get("length")))) {
				if (fileSystemManager.checkWriteComplete((String)message.get("pathName")) == true ) {
					result  = "complete";
				} else {
					Document response = new Document();		

					response.append("command", "FILE_CREATE_RESPONSE");
					response.append("fileDescriptor", des);
					response.append("pathName", (String)message.get("pathName"));
					response.append("message", "there was a problem creating the file");
					response.append("status", false);
					
					result = response.toJson();
				}
			} else {
				Document response = new Document();		

				response.append("command", "FILE_CREATE_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", (String)message.get("pathName"));
				response.append("message", "there was a problem creating the file");
				response.append("status", false);
				
				result = response.toJson();
			}
			} else {
				Document response = new Document();		

				response.append("command", "FILE_CREATE_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", (String)message.get("pathName"));
				response.append("message", "there was a problem creating the file");
				response.append("status", false);
				
				result = response.toJson();
			}
		return result;
	}
	
	public String delete_file(Document message) {
		Document response = new Document();	
		Document des = (Document)message.get("fileDescriptor");
		String pathName = (String)message.get("pathName");
		long lastModified = (long)des.get("lastModified");
		String md5 = (String)des.get("md5");
		if (fileSystemManager.deleteFile(pathName, lastModified, md5) == true) {
			response.append("command", "FILE_DELETE_RESPONSE");
			response.append("fileDescriptor", des);
			response.append("pathName", pathName);
			response.append("message", "file deleted" );
			response.append("status", true);
		} else {
			if (fileSystemManager.fileNameExists(pathName, md5) == true) {
				response.append("command", "FILE_DELETE_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", pathName);
				response.append("message", "there was a problem deleting the file" );
				response.append("status", false);
				System.out.println("1");
			} else {
				response.append("command", "FILE_DELETE_RESPONSE");
				response.append("fileDescriptor", des);
				response.append("pathName", pathName);
				response.append("message", "pathname does not exist" );
				response.append("status", false);
				System.out.println("2");
			}
		}
		return response.toJson();
	}

	public String delete_directory(Document message) {
		Document response = new Document();
		String pathName = (String)message.get("pathName");
		if(fileSystemManager.deleteDirectory((String)message.get("pathName")) == true) {
			response.append("command", "DIRECTORY_DELETE_RESPONSE");
			response.append("pathName", pathName);
			response.append("message", "directory deleted" );
			response.append("status", true);
		} else if(fileSystemManager.dirNameExists(pathName) == true){			
			response.append("command", "DIRECTORY_DELETE_RESPONSE");
			response.append("pathName", pathName);
			response.append("message", "there was a problem deleting the directory" );
			response.append("status", false);
		} else {
			response.append("command", "DIRECTORY_DELETE_RESPONSE");
			response.append("pathName", pathName);
			response.append("message", "pathname does not exist" );
			response.append("status", false);
		}
		return response.toJson();
	}

	public String create_directory(Document message) {
		Document response = new Document();	;
		String pathName = (String)message.get("pathName");
		if(fileSystemManager.makeDirectory((String)message.get("pathName")) == true) {
			response.append("command", "DIRECTORY_CREATE_RESPONSE");
			response.append("pathName", pathName);
			response.append("message", "directory created" );
			response.append("status", true);
		} else if(fileSystemManager.dirNameExists(pathName) == true) {
			response.append("command", "DIRECTORY_CREATE_RESPONSE");
			response.append("pathName", pathName);
			response.append("message", "pathname already exists" );
			response.append("status", false);
		} else {
			response.append("command", "DIRECTORY_CREATE_RESPONSE");
			response.append("pathName", pathName);
			response.append("message", "there was a problem creating the directory" );
			response.append("status", false);
		}
		return response.toJson();
	}


}
