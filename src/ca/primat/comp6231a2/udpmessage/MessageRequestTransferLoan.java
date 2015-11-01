package ca.primat.comp6231a2.udpmessage;

import java.io.Serializable;

import ca.primat.comp6231a2.data.Account;
import ca.primat.comp6231a2.data.Loan;

public class MessageRequestTransferLoan implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Loan loan = null;
	Account account = null;

}
