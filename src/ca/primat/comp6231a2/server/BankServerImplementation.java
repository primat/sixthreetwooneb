package ca.primat.comp6231a2.server;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.omg.CORBA.ORB;

import ca.primat.comp6231a2.LoanRequestStatus;
import ca.primat.comp6231a2.ThreadSafeHashMap;
import ca.primat.comp6231a2.UdpRequesterCallable;
import ca.primat.comp6231a2.data.Account;
import ca.primat.comp6231a2.data.Loan;
import dlms.BankServerPOA;
import dlms.GetLoanResponse;
import dlms.OpenAccountResponse;
import dlms.ServerResponse;

/**
 * The Java implementation of the BankServer IDL interface
 * 
 * @author mat
 *
 */
public class BankServerImplementation extends BankServerPOA {

	protected volatile Bank bank;
	protected volatile HashMap<String, Bank> bankCollection;
	protected volatile Object lockObject;
	protected int sequenceNbr = 1;
	protected Logger logger = null;
	private ORB orb;
	
	/**
	 * Constructor - Use inversion of control so that we manage creation of dependencies outside this class
	 * 
	 * @param reg The local registry where BankServers register to make themselves available to clients
	 * @param bankCollection The collection of all banks available in the system
	 * @param bankId The bank ID of the bank that this server is managing
	 */
	public BankServerImplementation(HashMap<String, Bank> bankCollection, String bankId, final Object lockObject) {
		
		super();
		this.bankCollection = bankCollection;
		this.bank = bankCollection.get(bankId);
		this.lockObject = lockObject;

		// Set up the logger
		this.logger = Logger.getLogger(this.bank.getTextId());  
	    FileHandler fh;  
	    try {
	        fh = new FileHandler(this.bank.getTextId() + "-log.txt");  
	        logger.addHandler(fh);
	        SimpleFormatter formatter = new SimpleFormatter();  
	        fh.setFormatter(formatter);  
	        logger.info(this.bank.getTextId() + " logger started");  
	    } catch (SecurityException e) {  
	        e.printStackTrace();
	        System.exit(1);
	    } catch (IOException e) {  
	        e.printStackTrace(); 
	        System.exit(1); 
	    }

	    // Start the bank's UDP listener
		BankUdpListener udpPeer = new BankUdpListener(this.bank, this.logger);
		Thread udpPeerThread = new Thread(udpPeer);
		udpPeerThread.start();
	}

	public void setORB(ORB orb_val) {
		orb = orb_val;
	}

	@Override
	public void shutdown() {
		try {
			orb.shutdown(false);
			orb.destroy();
		} catch (Exception e) {
			System.out.println("Orb did not shutdown cleanly");
		}
	}

	@Override
	public String printCustomerInfo() {
		
		logger.info("-------------------------------");
		logger.info(this.bank.getTextId() + ": Client invoked printCustomerInfo()");

		String result = new String();
		result = "------ ACCOUNTS ------\n";
		for (String key : this.bank.accounts.keySet()) {
			ThreadSafeHashMap<Integer, Account> accountsByLetter = this.bank.accounts.get(key);
			for (Integer accountId : accountsByLetter.keySet()) {
				Account account = accountsByLetter.get(accountId);
				result += account.toString() + "\n";
			}
		}
		
		result += "------ LOANS ------\n";
		for (String key : this.bank.loans.keySet()) {
			ThreadSafeHashMap<Integer, Loan> loansByLetter = this.bank.loans.get(key);
			for (Integer loanId : loansByLetter.keySet()) {
				Loan loan = loansByLetter.get(loanId);
				result += loan.toString() + "\n";
			}
		}

		return result;
	}

	@Override
	public OpenAccountResponse openAccount(String firstName, String lastName, String emailAddress, String phoneNumber,
			String password) {

		logger.info("-------------------------------");
		logger.info(this.bank.getTextId() + ": Client invoked openAccount(emailAddress:" + emailAddress + ")");

		OpenAccountResponse resp = this.bank.createAccount(firstName, lastName, emailAddress, phoneNumber, password);
	
		if (resp.result) {
			logger.info(this.bank.getTextId() + " successfully opened an account for user " + emailAddress + " with account number " + resp.accountNbr);
		}
		else {
			logger.info(this.bank.getTextId() + " failed to open an account for user " + emailAddress);
		}
		return resp;
	}

	@Override
	public GetLoanResponse getLoan(int accountNbr, String password, int loanAmount) {
		
		int newLoanId = 0;
		String returnMessage;

		logger.info("-------------------------------");
		logger.info(this.bank.getTextId() + ": Client invoked getLoan(accountNbr:" + accountNbr + ", password:" + password + ", loanAmount:" + loanAmount + ")");
			
		synchronized (lockObject) {
			
			// Test the existence of the account
			Account account = this.bank.getAccount(accountNbr);
			if (account == null) {
				returnMessage = "Account " + accountNbr + " does not exist at bank " + this.bank.getId();
				logger.info(this.bank.getTextId() + ": " + returnMessage);
				return new GetLoanResponse(false, returnMessage, "", 0);
			}

			// Validate that passwords match
			if (!account.getPassword().equals(password)) {
				returnMessage = "Invalid credentials. Loan refused at bank " + this.bank.getId() +  " " + account.getPassword() + "/" + password;
				logger.info(this.bank.getTextId() + ": " + returnMessage);
				return new GetLoanResponse(false, returnMessage, "", 0);
			}
	
			// Avoid making UDP requests if the loan amount is already bigger than the credit limit of the local account
			int currentLoanAmount = this.bank.getLoanSum(accountNbr);
			if (currentLoanAmount + loanAmount > account.getCreditLimit()) {
				returnMessage = "Loan refused at bank " + this.bank.getId() + ". Local credit limit exceeded";
				logger.info(this.bank.getTextId() + ": " + returnMessage);
				return new GetLoanResponse(false, returnMessage, "", 0);
			}
			
			// Get the loan sum for all banks and approve or not the new loan
			ExecutorService pool = Executors.newFixedThreadPool(this.bankCollection.size()-1);
		    Set<Future<LoanRequestStatus>> set = new HashSet<Future<LoanRequestStatus>>();
		    for (Bank destinationBank : this.bankCollection.values()) {
		    	if (this.bank != destinationBank) {
					Callable<LoanRequestStatus> callable = new UdpRequesterCallable(this.bank, destinationBank, account.getEmailAddress(), this.sequenceNbr, this.logger);
					Future<LoanRequestStatus> future = pool.submit(callable);
					set.add(future);
				}
			}
	
			int extLoanSum = 0; // Storage for the total sum of loans at other banks for this user
			
			for (Future<LoanRequestStatus> future : set) {
	
				try {
					LoanRequestStatus status = future.get();
					if (status == null) {
						returnMessage = "Loan refused at bank " + this.bank.getId() + ". Unable to obtain a status for the original loan request.";
						logger.info(this.bank.getTextId() + ": " + returnMessage);
						return new GetLoanResponse(false, returnMessage, "", 0);
					}
					else if (status.status == LoanRequestStatus.STATUS_SUCCESS) {
						extLoanSum += status.loanSum;
					}
					else {
						returnMessage = "Loan refused at bank " + this.bank.getId() + ". " + status.message;
						logger.info(this.bank.getTextId() + ": " + returnMessage);
						return new GetLoanResponse(false, status.message, "", 0);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
					returnMessage = "Loan request failed for user " + account.getEmailAddress() + ". InterruptedException";
					logger.info(this.bank.getTextId() + ": " + returnMessage);
					return new GetLoanResponse(false, returnMessage, "", 0);
					
				} catch (ExecutionException e) {
					e.printStackTrace();
					returnMessage = "Loan request failed for user " + account.getEmailAddress() + ". ExecutionException";
					logger.info(this.bank.getTextId() + ": " + returnMessage);
					return new GetLoanResponse(false, returnMessage, "", 0);
				}
			}
			
			this.sequenceNbr++;
			
			// Check if all (UDP) operations were successful
			if ((loanAmount + extLoanSum) > account.getCreditLimit()) {
				logger.info(this.bank.getTextId() + ": Loan refused at bank " + this.bank.getId() + ". Total credit limit exceeded");
				return new GetLoanResponse(false, "Loan refused at bank " + this.bank.getId() + ". Total credit limit exceeded", "", 0);
			}
				
			newLoanId = this.bank.createLoan(account.getEmailAddress(), accountNbr, loanAmount);
			
			logger.info(this.bank.getTextId() + ": Loan approved for user " + account.getEmailAddress() + ", amount " + loanAmount + " at bank " + this.bank.getId());
			
			return new GetLoanResponse(true, "Loan approved at bank " + this.bank.getId() + ".", "", newLoanId);
		}
	}

	@Override
	public ServerResponse delayPayment(int loanId, String currentDueDate, String newDueDate) {
		
		logger.info("-------------------------------");
		logger.info(this.bank.getTextId() + ": Client invoked delayPayment(loanId:" + loanId + ")");
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-M-yyyy");
		//Date dateCurrent = null;
		Date dateNew = null;
		
		try {
			//dateCurrent = dateFormat.parse(currentDueDate);
			dateNew = dateFormat.parse(newDueDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		synchronized (lockObject) {
			
			Loan loan = this.bank.getLoanById(loanId);
			if (loan == null) {
				logger.info(this.bank.getTextId() + ": Loan id " + loanId + " does not exist");
				return new ServerResponse(false, "", "Loan id " + loanId + " does not exist");
			}
			// TODO: Fix Validation of currentDate in delayPayment()
//			if (!loan.getDueDate().equals(dateCurrent)) {
//				logger.info(this.bank.getTextId() + ": Loan id " + loanId + " - currentDate argument mismatch");
//				return new ServerResponse(false, "", "Loan id " + loanId + " - currentDate argument mismatch");
//			}
			if (!loan.getDueDate().before(dateNew)) {
				logger.info(this.bank.getTextId() + ": Loan id " + loanId + " - currentDueDate argument must be later than the actual current due date of the loan");
				return new ServerResponse(false, "", " Loan id " + loanId + " - currentDueDate argument must be later than the actual current due date of the loan");
			}
			
			loan.setDueDate(dateNew);
		}

		logger.info(this.bank.getTextId() + " loan " + loanId + " successfully delayed");
		return new ServerResponse(true, this.bank.getTextId() + " loan " + loanId + " successfully delayed", "");
	}

	@Override
	public ServerResponse transferLoan(int loanId, String currentBankId, String otherBankId) {
		
		synchronized (lockObject) {
			
			Loan loan = this.bank.getLoanById(loanId);
			if (loan == null) {
				logger.info(this.bank.getTextId() + ": Loan transfer " + loanId + " failed. LoanId does not exist");
				return new ServerResponse(false, "", "Loan transfer " + loanId + " failed. LoanId does not exist");
			}
			
			
			
		}

		logger.info(this.bank.getTextId() + ": Loan transfer " + loanId + " from " + currentBankId + " to " + otherBankId + " successful");
		return new ServerResponse(true, "", "Loan transfer " + loanId + " from " + currentBankId + " to " + otherBankId + " successful");
	}
	
	
	//
	// Getters and setters
	//
	
	/**
	 * 
	 * @return
	 */
	public Bank getBank() {
		return this.bank;
	}
}

