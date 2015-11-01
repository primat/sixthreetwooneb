package ca.primat.comp6231a2.client;

import dlms.BankServer;
import dlms.ServerResponse;

/**
 * This is the manager client application for the DLMS
 * 
 * @author mat
 * 
 */
public class ManagerClient extends Client {

	protected static int instances = 1;
	protected final int id;
	
	/**
	 * Constructor
	 */
	public ManagerClient() {
		
		super();
		this.id = instances++;
		this.setUpLogger(this.id);
	}
	
	/**
	 * Delays a payment on a loan
	 * 
	 * @param bank
	 * @param loanId
	 * @param currentDueDate
	 * @param NewDueDate
	 * @return
	 */
	public Boolean delayPayment(String bank, int loanId, String currentDueDate, String newDueDate) {
		
		BankServer server = this.getBankServer(bank);
		ServerResponse response = server.delayPayment(loanId, currentDueDate, newDueDate);
		logger.info(this.getTextId() + ": " + response.message);
		return response.result;
	}
	
	/**
	 * Prints the customer info of the provided bank
	 * 
	 * @param bank
	 * @return
	 */
	public void printCustomerInfo(String bank) {
		
		BankServer server = this.getBankServer(bank);
		String result = server.printCustomerInfo();
		logger.info(this.getTextId() + ": Bank-" + bank + "\n" + result);
	}

	/**
	 * Get the text ID of this client e.g. ManagerClient-1
	 * 
	 * @return
	 */
	protected String getTextId() {
		return this.getClass().getSimpleName() + "-" + this.id;
	}
	
}
