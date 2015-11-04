package ca.primat.comp6231a2;

import java.util.HashMap;

public class BankConfig {

	public String id;
	public String host;
	public int udpPort;
	
	public static HashMap<String, BankConfig> bankConfigs;
	
	/**
	 * Constructor
	 * 
	 * @param id
	 * @param host
	 * @param udpPort
	 */
	public BankConfig(String id, String host, int udpPort) {
		super();
		this.id = id;
		this.host = host;
		this.udpPort = udpPort;
		
		bankConfigs = new HashMap<String, BankConfig>();
		bankConfigs.put("rbc", new BankConfig("rbc", "localhost", 10101));
		bankConfigs.put("cibc", new BankConfig("rbc", "localhost", 10102));
		bankConfigs.put("bmo", new BankConfig("rbc", "localhost", 10103));
	}

}
