package ca.primat.comp6231a2.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

import dlms.BankServer;
import dlms.BankServerHelper;

/**
 * Base class for customer and manager clients 
 * 
 * @author mat
 *
 */
public class Client {

	public final static String orbArgs[] = { "-ORBInitialPort 1050", "-ORBInitialHost localhost" };
	
	protected HashMap<String, BankServer> servers;
	protected Logger logger = null;
	protected ORB orb;
	
	
	/**
	 * 
	 * Constructor
	 */
	public Client() {
		
		//super();
		
		// Prepare the bankServer cache
		this.servers = new HashMap<String, BankServer>();
		
		// Create and initialize the ORB
		orb = ORB.init(orbArgs, null);
	}

	
	public void setUpLogger(int id) {
	
		// Set up the logger
		String className = this.getClass().getSimpleName();
		String textId = className + "-" + id;
		this.logger = Logger.getLogger(textId);
	    FileHandler fh;  
	
	    try {
	        // This block configure the logger with handler and formatter  
	        fh = new FileHandler(textId + "-log.txt");  
	        logger.addHandler(fh);
	        SimpleFormatter formatter = new SimpleFormatter();  
	        fh.setFormatter(formatter);  
	        logger.info(textId + " logger started");
	    } catch (SecurityException e) {  
	        e.printStackTrace();
	        System.exit(1);
	    } catch (IOException e) {  
	        e.printStackTrace(); 
	        System.exit(1); 
	    }
	}
		
	/**
	 * Gets the BankServerImplementation object
	 * 
	 * @param serverId
	 * @return
	 */
	protected BankServer getBankServer(String serverId) {
		
		if (this.servers.containsKey(serverId)) {
			return this.servers.get(serverId);
		}
		
		BankServer bankServer = null;
		
		try {
			
			// Get the root naming context
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
			
			// Resolve the Object Reference in Naming
			bankServer = BankServerHelper.narrow(ncRef.resolve_str(serverId));
			this.servers.put(serverId, bankServer);

		} catch (Exception e) {
			System.out.println("Error : " + e);
			e.printStackTrace(System.out);
		}

		return bankServer;
	}
}
