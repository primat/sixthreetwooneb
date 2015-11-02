package ca.primat.comp6231a2.model;

public class LoanRequestStatus {

	public final static int STATUS_SUCCESS = 0;
	public final static int STATUS_FATAL = 1;
	public final static int STATUS_RETRY = 2;
	public final static int STATUS_REFUSED= 3;
	public final static int STATUS_UNKNOWN = 4;
	
	public int status = LoanRequestStatus.STATUS_UNKNOWN;
	public String message = "";
	public int loanSum = 0;
}
