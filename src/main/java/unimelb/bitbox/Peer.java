package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.sql.ClientInfoStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;
//import javax.swing.text.Document;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class Peer {
	protected static Logger log = Logger.getLogger(Peer.class.getName());
	protected static String ip = Configuration.getConfigurationValue("advertisedName");
	protected static int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
	
	protected static ArrayList<HostPort> connectedPeers = new ArrayList<HostPort>();
	protected static ArrayList<Socket> socketList = new ArrayList<Socket>();
	protected static Queue<HostPort> hostPortsQueue = new LinkedList<>();
	protected static ServerMain ser = null;

	public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tc] %2$s %4$s: %5$s%n");
		log.info("BitBox Peer starting...");
		Configuration.getConfiguration();
		ser = new ServerMain();
		int synchornizeTimeInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		
		// this is for TCP mode
		// wait for connections--------- this is an independent thread running all the time
		new Thread(() -> TCP.waiting(ser)).start();
		
		// try to connect all the peers in the list
		TCP.connectAllPeers();
		
		// running sync
		TCP.sync(synchornizeTimeInterval, ser);
	}
}