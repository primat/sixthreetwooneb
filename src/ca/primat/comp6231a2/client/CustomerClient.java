package ca.primat.comp6231a2.client;

import dlms.BankServer;
import dlms.GetLoanResponse;
import dlms.OpenAccountResponse;
import dlms.ServerResponse;

/**
 * This is the customer client application for the DLMS
 * 
 * @author mat
 *
 */
public class CustomerClient extends Client {

	protected static int instances = 1;
	protected final int id;
	
	/**
	 * Constructor
	 */
	public CustomerClient() {
		
		super();
		this.id = instances++;
		this.setUpLogger(this.id);
	}
	
	/**
	 * Opens an account at the provided bank, assuming the account doesn't already exist
	 * 
	 * @param bank
	 * @param firstName
	 * @param lastName
	 * @param emailAddress
	 * @param phoneNumber
	 * @param password
	 * @return
	 */
	public int openAccount(String bank, String firstName, String lastName, String emailAddress, String phoneNumber, String password) {
		
		BankServer server = this.getBankServer(bank);
		
	    logger.info(this.getTextId() + ": Opening an account at " + bank + " for user " + emailAddress);
	    
		OpenAccountResponse response = server.openAccount(firstName, lastName, emailAddress, phoneNumber, password);
		if (response.accountNbr > 0) {
		    logger.info(this.getTextId() + ": Account " + emailAddress + " created successfully at bank " + bank + " with account number " + response.accountNbr);
		}
		else {
		    logger.info(this.getTextId() + ": Could not open account " + emailAddress + " at bank " + bank + ". " + response.message);
		}
		
		return response.accountNbr;
	}
	
	/**
	 * Request a loan at the given Bank
	 * 
	 * @param bank
	 * @param accountNumber
	 * @param password
	 * @param loanAmount
	 * @return
	 */
	public GetLoanResponse getLoan(String bank, int accountNumber, String password, int loanAmount) {
		
		BankServer server = this.getBankServer(bank);
		GetLoanResponse response = server.getLoan(accountNumber, password, loanAmount);
		
		if (response.loanId > 0) {
		    logger.info(this.getTextId() + ": Account " + accountNumber + " successfully got a loan of " + loanAmount + " at bank " + bank + " with loanId " + response.loanId);
			return response;
		}
		else {
		    logger.info(this.getTextId() + ": Account " + accountNumber + " was refused a loan of " + loanAmount + " at bank " + bank);
		}
		
		return null;
	}

	/**
	 * Transfer a loan from one bank to another
	 * 
	 * @param bank
	 * @param currentBankId
	 * @param newBankId
	 * @return
	 */
	public ServerResponse transferLoan(int loanId, String currentBankId, String newBankId) {
		
		BankServer server = this.getBankServer(currentBankId);

		ServerResponse response = server.transferLoan(loanId, currentBankId, newBankId);
		
		System.out.println(response);

	    logger.info(this.getTextId() + ": " + response.message);
		
		if (response.result) {
			return response;
		}
		else {
		    logger.info(this.getTextId() + ": " + response.message);
		}
		
		return null;
	}
	
	/**
	 * Get the text ID of this client e.g. CustomerClient-1
	 * 
	 * @return
	 */
	protected String getTextId() {
		return this.getClass().getSimpleName() + "-" + this.id;
	}
	
}

