package ca.primat.comp6231a2.udpmessage;

import java.io.Serializable;

/**
 * A message object representing a loan request response
 * 
 * @author mat
 *
 */
public class MessageResponseLoan implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public int sequenceNbr;
	public int amountAvailable;
	public String emailAddress;
}
