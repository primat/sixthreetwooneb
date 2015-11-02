package ca.primat.comp6231a2.udpmessage;

import java.io.Serializable;

/**
 * A message object representing a loan request
 * 
 * @author mat
 *
 */
public class MessageRequestGetLoan implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public int sequenceNbr;
	public String emailAddress;
	
}
