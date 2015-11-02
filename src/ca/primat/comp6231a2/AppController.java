package ca.primat.comp6231a2;

import java.net.InetSocketAddress;
import java.util.HashMap;
import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import org.omg.PortableServer.*;
import org.omg.PortableServer.POA;

import ca.primat.comp6231a2.client.CustomerClient;
import ca.primat.comp6231a2.client.ManagerClient;
import dlms.BankServer;
import dlms.BankServerHelper;
import dlms.GetLoanResponse;
import ca.primat.comp6231a2.server.Bank;
import ca.primat.comp6231a2.server.BankServerImplementation;

/**
 * Main launcher class for the comp6231 assignment #2
 * 
 * @author mat
 *
 */
public class AppController {
	
	// ORB configuration
	public static final String orbArgs[] = {"-ORBInitialPort 1050", "-ORBInitialHost localhost"};
	
	protected BankServerImplementation bankServant1;
	protected BankServerImplementation bankServant2;
	protected BankServerImplementation bankServant3;
	protected BankServer bankServer1;
	protected BankServer bankServer2;
	protected BankServer bankServer3;
	private java.lang.Object commonLock = new java.lang.Object();

	/**
	 * The application launcher
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		try {
			new AppController();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Use the AppController constructor to choose a scenario to run
	 * @throws InterruptedException 
	 */
	public AppController() throws InterruptedException {
		
		super();
		this.prepareServers();
		
		// Run some simple tests
		//this.testServers();
		//this.testOpenAccount();
		//this.testGetLoan();
		//this.testDelayLoan();
		this.testTransferLoan();

	}

	/**
	 * Test method #3 - Tests concurrency for the DelayLoan operation
	 * 
	 * @throws InterruptedException
	 */
	public Boolean testTransferLoan() throws InterruptedException {

		//System.out.println("Starting manager client #1");
		ManagerClient mc = new ManagerClient();
		CustomerClient cc = new CustomerClient();
		
		int accNbr = cc.openAccount("rbc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
		// Add a loan for user jondoe@gmail.com at bank "rbc"
		GetLoanResponse resp = cc.getLoan("rbc", accNbr, "jondoe", 500);
		if (!resp.result) {
			System.out.println(resp.message);
			return false;
		}

		// Before loan transfer
		mc.printCustomerInfo("rbc");
		mc.printCustomerInfo("bmo");
		
		// Transfer a loan
		cc.transferLoan(resp.loanId, "rbc", "bmo");
		
		// After loan transfer
		mc.printCustomerInfo("rbc");
		mc.printCustomerInfo("bmo");
		
		return true;
	}

	/**
	 * Test method #3 - Tests concurrency for the DelayLoan operation
	 * 
	 * @throws InterruptedException
	 */
	public Boolean testDelayLoan() throws InterruptedException {
		
		CustomerClient cc = new CustomerClient();
		int accNbr = cc.openAccount("rbc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
		// Add a loan for user jondoe@gmail.com at bank "rbc"
		GetLoanResponse resp = cc.getLoan("rbc", accNbr, "jondoe", 500);
		if (resp.result) {
			ManagerClient mc = new ManagerClient();
			mc.delayPayment("rbc", resp.loanId, "31-12-2015", "31-06-2016");	
			mc.printCustomerInfo("rbc");
			return true;
		}
		else {
			System.out.println(resp.message);
		}
		return false;
	}

	/**
	 * Test method #2 - Tests concurrency for the GetLoan operation
	 * 
	 * @throws InterruptedException
	 */
	public void testGetLoan() throws InterruptedException {
	
		// Create a few customer clients in their own threads and make them do some operations
		final Thread tc1 = new Thread() {
			@Override
			public void run() {
				CustomerClient cc = new CustomerClient();
				int accNbr = cc.openAccount("rbc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
				// Add a loan for user jondoe@gmail.com at bank "rbc"
				cc.getLoan("rbc", accNbr, "jondoe", 500);
			}
		};
	
		final Thread tc2 = new Thread() {
			@Override
			public void run() {
				CustomerClient cc = new CustomerClient();
				int accNbr = cc.openAccount("bmo", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
				// Add a loan for user jondoe@gmail.com at bank "bmo"
				cc.getLoan("bmo", accNbr, "jondoe", 500);
			}
		};
	
		final Thread tc3 = new Thread() {
			@Override
			public void run() {
				CustomerClient cc = new CustomerClient();
				int accNbr = cc.openAccount("cibc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
				// Add a loan for user jondoe@gmail.com at bank "cibc"
				cc.getLoan("cibc", accNbr, "jondoe", 600);
			}
		};
	
		tc1.start();
		tc2.start();
		tc3.start();
		tc1.join();
		tc1.join();
		tc3.join();
	
		final Thread tm1 = new Thread() {
			@Override
			public void run() {
				//System.out.println("Starting manager client #1");
				ManagerClient mc = new ManagerClient();
				mc.printCustomerInfo("rbc");
				mc.printCustomerInfo("bmo");
				mc.printCustomerInfo("cibc");
			}
		};
	
		tm1.start();
		tm1.join();
	}

	/** 
	 * Test method #1 - Tests concurrency for OpenAccount operation
	 * 
	 * @throws InterruptedException
	 */
	public void testOpenAccount() throws InterruptedException {

		// Create a few customer clients in their own threads and make them do some operations
		//CustomerClient cust1 = null;
		final Thread tc1 = new Thread() {
			@Override
			public void run() {
				CustomerClient cc = new CustomerClient();
				cc.openAccount("rbc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
				cc.openAccount("bmo", "Charles", "Xavier", "charlesxavier@gmail.com", "5145145145", "charlesxavier");
				cc.openAccount("cibc", "Vincent", "Vega", "vincentvega@gmail.com", "5145145155", "vincentvega");
			}
		};
		final Thread tc2 = new Thread() {
			@Override
			public void run() {
				CustomerClient cc = new CustomerClient();
				cc.openAccount("cibc", "Vincent", "Vega", "vincentvega@gmail.com", "5145145155", "vincentvega");
				cc.openAccount("bmo", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
				cc.openAccount("rbc", "Sarah", "Conor", "sarahconor@gmail.com", "5145145144", "sarahconor");
				cc.openAccount("bmo", "Lois", "Lane", "loislane@gmail.com", "5145145244", "loislane");
				cc.openAccount("cibc", "Jules", "Winnfield", "juleswinnfield@gmail.com", "5145145144", "juleswinnfield");
			}
		};
		final Thread tc3 = new Thread() {
			@Override
			public void run() {
				CustomerClient cc = new CustomerClient();
				cc.openAccount("cibc", "Vincent", "Vega", "vincentvega@gmail.com", "5145145155", "vincentvega");
				cc.openAccount("cibc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
				cc.openAccount("rbc", "Kyle", "Reese", "kylereese@gmail.com", "5145145163", "kylereese");
				cc.openAccount("bmo", "Elanor", "Gamgee", "elanorgamgee@gmail.com", "5145145343", "elanorgamgee");
				cc.openAccount("cibc", "John", "Doe", "jondoe@gmail.com", "5145145145", "jondoe");
			}
		};

		tc1.start();
		tc2.start();
		tc3.start();
		tc1.join();
		tc2.join();
		tc3.join();

		final Thread tm1 = new Thread() {
			@Override
			public void run() {
				ManagerClient mc = new ManagerClient();
				mc.printCustomerInfo("rbc");
				mc.printCustomerInfo("cibc");
				mc.printCustomerInfo("bmo");
			}
		};
		
		tm1.start();
		tm1.join();
	}
	
	/**
	 * A simple method for testing if servers are online
	 * 
	 * @throws InterruptedException
	 */
	public void testServers() throws InterruptedException {

		final Thread tc1 = new Thread() {
			@Override
			public void run() {
				ManagerClient mc = new ManagerClient();
				mc.printCustomerInfo("rbc");
				mc.printCustomerInfo("cibc");
				mc.printCustomerInfo("bmo");
			}
		};
		
		tc1.start();
		tc1.join();
	}
	
	/**
	 * Create three bank servants
	 */
	private void createBanks() {

		Bank bank1 = new Bank("rbc", new InetSocketAddress("localhost", 10101));
		Bank bank2 = new Bank("cibc", new InetSocketAddress("localhost", 10102));
		Bank bank3 = new Bank("bmo", new InetSocketAddress("localhost", 10103));

		HashMap<String, Bank> bankCollection = new HashMap<String, Bank>();
		bankCollection.put(bank1.getId(), bank1);
		bankCollection.put(bank2.getId(), bank2);
		bankCollection.put(bank3.getId(), bank3);
		
		this.bankServant1 = new BankServerImplementation(bankCollection, bank1.getId(), commonLock);
		this.bankServant2 = new BankServerImplementation(bankCollection, bank2.getId(), commonLock);
		this.bankServant3 = new BankServerImplementation(bankCollection, bank3.getId(), commonLock);
		
		System.out.println("AppController: Three bank servants created");
	}


	/**
	 * Create the ORBs, servants and servers and make the DLMS accessible by clients
	 */
	private void prepareServers() {

		try {
			
			// Create and initialize the ORB
			ORB orb = ORB.init(orbArgs, null);

			// Get reference to RootPOA and activate the POAManager
			POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
			rootpoa.the_POAManager().activate();

			// Create servant and register it with the ORB
			this.createBanks();

			// Get object reference from the servant
			org.omg.CORBA.Object ref1 = rootpoa.servant_to_reference(bankServant1);
			bankServer1 = BankServerHelper.narrow(ref1);
			org.omg.CORBA.Object ref2 = rootpoa.servant_to_reference(bankServant2);
			bankServer2 = BankServerHelper.narrow(ref2);
			org.omg.CORBA.Object ref3 = rootpoa.servant_to_reference(bankServant3);
			bankServer3 = BankServerHelper.narrow(ref3);

			// Get the root naming context
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

			// Bind the object reference in naming
			NameComponent path1[] = ncRef.to_name(bankServant1.getBank().getId());
			ncRef.rebind(path1, bankServer1);
			NameComponent path2[] = ncRef.to_name(bankServant2.getBank().getId());
			ncRef.rebind(path2, bankServer2);
			NameComponent path3[] = ncRef.to_name(bankServant3.getBank().getId());
			ncRef.rebind(path3, bankServer3);

			System.out.println("AppController: Bank servers ready and waiting ...");
			
			final Thread orbThread = new Thread() {
				@Override
				public void run() {
					// Wait for invocations from clients
					orb.run();
				}
			};

			// Start the servers (in a new thread)
			orbThread.start();
		}

		catch (Exception e) {
			System.err.println("Error: " + e);
			e.printStackTrace(System.out);
		}
		
	}
	
//	/**
//	 * Resets all bank data to the empty state
//	 */
//	private void clearBankData() {
//		this.bankServant1.bank.resetBankData();
//		this.bankServant2.bank.resetBankData();
//		this.bankServant3.bank.resetBankData();
//	}
	
}