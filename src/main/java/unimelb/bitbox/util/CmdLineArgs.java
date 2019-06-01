package unimelb.bitbox.util;
import org.kohsuke.args4j.Option;
public class CmdLineArgs {

	@Option(required = true, name = "-c", usage = "Command")
	private String command;
	
	@Option(required = true, name = "-s", usage = "Server address")
	private String s;
	
	@Option(required = false, name = "-p", usage = "Peer address")
	private String p;
	
	@Option(required = false, name = "-i", usage = "Client identity")
	private String identity = "yilu@unimelb";
	
	public String getCommand() {
		return command;
	}

	public HostPort getServer() {
		HostPort server = new HostPort(s);
		return server;
	}
	
	public HostPort getPeer() {
		HostPort peer = new HostPort(p);
		return peer;
	}
	
	public String getIdentity() {
		return identity;
	}
	
}