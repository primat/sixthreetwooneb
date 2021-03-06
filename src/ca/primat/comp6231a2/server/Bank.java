package ca.primat.comp6231a2.server;

import java.net.InetSocketAddress;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import ca.primat.comp6231a2.exception.AppException;
import ca.primat.comp6231a2.exception.ValidationException;
import ca.primat.comp6231a2.model.Account;
import ca.primat.comp6231a2.model.Loan;
import ca.primat.comp6231a2.model.ThreadSafeHashMap;

/**
 * The Bank class stores bank data and operations on it
 * 
 * @author mat
 *
 */
public class Bank {

	protected final String id;
	protected final InetSocketAddress udpAddress;

	public String contextEmailAddress;
	
//	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
//	private final Lock readLock = readWriteLock.readLock();
//	private final Lock writeLock = readWriteLock.writeLock();
	
	public HashMap<String, ThreadSafeHashMap<Integer, Account>> accounts;
	public HashMap<String, ThreadSafeHashMap<Integer, Loan>> loans;
	public HashMap<String, Object> locks;
	
	protected static int nextAccountNbr = 100;
	protected static int nextLoanId = 1000;
	
	/**
	 * Constructor
	 * 
	 * @param id
	 * @param udpAddress
	 */
	public Bank(String id, InetSocketAddress udpAddress) {
		
		super();
		
		this.id = id;
		this.contextEmailAddress = "";
		this.udpAddress = udpAddress;
		this.accounts = new HashMap<String, ThreadSafeHashMap<Integer, Account>>();
		this.loans = new HashMap<String, ThreadSafeHashMap<Integer, Loan>>();
		this.locks = new HashMap<String, Object>();
		
		// Initialize the locks
		
		// Pre-fill the first dimension of the accounts and loans arrays otherwise we need to 
		// synchronize access when testing the existence of an entry
		this.resetData();
		
		// Initialize the object locks
		char ch;
		for (ch = 'A'; ch <= 'Z'; ++ch) {
			final Object obj = new Object();
			this.locks.put(String.valueOf(ch), obj); 
		}
	}

	/**
	 * Create an account
	 * 
	 * @param firstName
	 * @param lastName
	 * @param emailAddress
	 * @param phoneNumber
	 * @param password
	 * @return
	 * @throws AppException 
	 */
	public int createAccount(String firstName, String lastName, String emailAddress, String phoneNumber, String password) throws ValidationException {
		
		String firstLetter = emailAddress.substring(0, 1).toUpperCase();
		ThreadSafeHashMap<Integer, Account> accounts = this.accounts.get(firstLetter);
		int newAccountNbr = 0;
		Object lock;
		
		// TODO: Perform some additional field validation
		
		// Get the lock object and run the critical section
		lock = this.getLockObject(emailAddress);
		
		synchronized (lock) {

			// Check if there is already an account with that email address
			for (Integer accNbr : accounts.keySet()) {
				Account account = accounts.get(accNbr);
				if (account.getEmailAddress().equals(emailAddress)) {
					throw new ValidationException("The account " + emailAddress + " already exists at bank" + this.getId());
					//return new OpenAccountResponse(false, "", "The account " + emailAddress + " already exists at bank" + this.getId(), account.getAccountNbr());
				}
			}

			// Create the account and add it to memory
			newAccountNbr = nextAccountNbr++;
			Account newAccount = new Account(newAccountNbr, firstName, lastName, emailAddress, phoneNumber, password);
			accounts.put(newAccountNbr, newAccount);
		}
		
		return newAccountNbr;
	}

	/**
	 * Create a new loan using today's date as the payment date
	 * 
	 * @param emailAddress
	 * @param accountNbr
	 * @param loanAmount
	 * @return
	 */
	protected int createLoan(String emailAddress, int accountNbr, int loanAmount) {

		Date now = new Date();
		Calendar cal = Calendar.getInstance();
	    cal.setTime(now);
	    cal.add(Calendar.MONTH, 2);
		return createLoan(accountNbr, emailAddress, loanAmount, cal.getTime());
	}

	/**
	 * Create a new loan
	 * 
	 * @param emailAddress
	 * @param accountNbr
	 * @param loanAmount
	 * @return
	 */
	protected int createLoan(int accountNbr, String emailAddress, int loanAmount, Date dueDate) {
		
		String firstLetter = emailAddress.substring(0, 1).toUpperCase();
		ThreadSafeHashMap<Integer, Loan> loans = this.loans.get(firstLetter);

		synchronized (loans) {
		    int loanId = nextLoanId++;
			Loan loan = new Loan(accountNbr, emailAddress, loanAmount, dueDate, loanId);
			loans.put(loanId, loan);
		}
		
		return nextLoanId-1;
	}

	/**
	 * Removes a loan from the list of loans
	 * 
	 * @param loanId
	 * @return
	 */
	public Boolean deleteLoan(int loanId) {

		for (String firstLetter : this.loans.keySet()) {
			ThreadSafeHashMap<Integer, Loan> loansByLetter = this.loans.get(firstLetter);
			for (Integer id : loansByLetter.keySet()) {
				if (id == loanId) {
					loansByLetter.remove(loanId);
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Gets the account corresponding to the provided account number or null if no such account exists
	 * 
	 * @param emailAddress
	 * @return
	 */
	public Account getAccount(int accountNbr) {
		
//		readLock.lock();
//		
//		try {
			for (String firstLetter : this.accounts.keySet()) {
				ThreadSafeHashMap<Integer, Account> accountsByLetter = this.accounts.get(firstLetter);
				Account account = accountsByLetter.get(accountNbr);
				if (account != null) {
					return account;
				}
			}
//		} finally {
//			readLock.unlock();
//		}
		
		return null;
	}

	/**
	 * Gets the account corresponding to the provided email address or null if no such account exists
	 * 
	 * @param emailAddress
	 * @return
	 */
	public Account getAccount(String emailAddress) {
		
		String firstLetter = emailAddress.substring(0, 1).toUpperCase();
		ThreadSafeHashMap<Integer, Account> accountsByLetter = this.accounts.get(firstLetter);

		// Check if there is an account with that email address and return it if it exists
		for (Integer accNbr : accountsByLetter.keySet()) {
			Account account = accountsByLetter.get(accNbr);
			if (account.getEmailAddress().equals(emailAddress)) {
				return account;
			}
		}
		
		return null;
	}
	
	/**
	 * Get a loan from its ID
	 * 
	 * @param id
	 * @return
	 */
	public Loan getLoan(int id) {
		
		for (String firstLetter : this.loans.keySet()) {
			ThreadSafeHashMap<Integer, Loan> loansByLetter = this.loans.get(firstLetter);
			for (Integer loanId : loansByLetter.keySet()) {
				Loan loan = loansByLetter.get(loanId);
				if (loan.getId() == id) {
					return loan;
				}
			}
		}

		return null;
	}

	/**
	 * Gets the "letter" lock, used to lock the 2nd level HashMaps of accounts and loans
	 * 
	 * @param firstLetter
	 * @return
	 */
	public Object getLockObject(String firstLetter) {
		firstLetter = firstLetter.substring(0, 1).toUpperCase();
		return locks.get(firstLetter);
	}
	
	/**
	 * Gets the sum of all loans for the given email address (aka username)
	 * 
	 * @param emailAddress
	 * @return
	 */
	public int getLoanSum(String emailAddress) {

		String firstLetter = emailAddress.substring(0, 1).toUpperCase();
		ThreadSafeHashMap<Integer, Loan> loansByLetter = this.loans.get(firstLetter);
		int result = 0;
		//Date now = Calendar.getInstance().getTime();
		
		for (Integer loanId : loansByLetter.keySet()) {
			Loan loan = loansByLetter.get(loanId);
			if (loan.getEmailAddress().equals(emailAddress)) {
				result += loan.getAmount();
			}
		}

		return result;
	}
	
	/**
	 * Method for clearing test data
	 */
	public void resetData() {

		char ch;
		for (ch = 'A'; ch <= 'Z'; ++ch) {
			this.accounts.put(String.valueOf(ch), new ThreadSafeHashMap<Integer, Account>()); 
		}
		for (ch = 'A'; ch <= 'Z'; ++ch) {
			this.loans.put(String.valueOf(ch), new ThreadSafeHashMap<Integer, Loan>()); 
		}
		for (ch = 'A'; ch <= 'Z'; ++ch) {
			//final Object obj = ;
			this.locks.put(String.valueOf(ch), new Object()); 
		}
	}
	
	//
	// Getters and setters
	//

	/**
	 * Get a lower case version of the bank name
	 * 
	 * @return
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * 
	 * @return
	 */
	public String getTextId() {
		return "Bank-" + this.getId();
	}

	/**
	 * 
	 * @return
	 */
	public InetSocketAddress getUdpAddress() {
		return udpAddress;
	}
	
	// Returns the first letter of the username, uppercased
	/**
	 * 
	 * @param str
	 * @return
	 */
	public static String getLetterKey(String str) {
		return str.substring(0, 1).toUpperCase();
	}
	
}
