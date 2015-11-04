package ca.primat.comp6231a2;

import java.net.InetSocketAddress;
import java.util.HashMap;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import ca.primat.comp6231a2.server.Bank;
import ca.primat.comp6231a2.server.BankServerImplementation;
import dlms.BankServer;
import dlms.BankServerHelper;

public class Bank1Launcher {

	protected static BankServerImplementation bankServant1;
	protected static BankServer bankServer1;
	
	public static void main(String[] args) {
		
		try {
			
			// Create and initialize the ORB
			ORB orb = ORB.init(args, null);

			// Get reference to RootPOA and activate the POAManager
			POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
			rootpoa.the_POAManager().activate();

			// Create servant and register it with the ORB
			//this.createBanks();
			
			
			Bank bank1 = new Bank("rbc", new InetSocketAddress("localhost", 10101));

			HashMap<String, Bank> bankCollection = new HashMap<String, Bank>();
			bankCollection.put(bank1.getId(), bank1);
			
			bankServant1 = new BankServerImplementation(bankCollection, bank1.getId(), new Object());
			
			

			// Get object reference from the servant
			org.omg.CORBA.Object ref1 = rootpoa.servant_to_reference(bankServant1);
			bankServer1 = BankServerHelper.narrow(ref1);

			// Get the root naming context
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

			// Bind the object reference in naming
			NameComponent path1[] = ncRef.to_name(bankServant1.getBank().getId());
			ncRef.rebind(path1, bankServer1);


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

}
