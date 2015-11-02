package ca.primat.comp6231a2.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import ca.primat.comp6231a2.model.LoanRequestStatus;
import ca.primat.comp6231a2.udpmessage.MessageRequestGetLoan;
import ca.primat.comp6231a2.udpmessage.MessageResponseLoan;

/**
 * Callable to run a UDP request to get loan info in a thread so that it doesn't block client operations
 * and be able to return a value to the parent thread
 * 
 * @author mat
 *
 */
public class UdpGetLoanCallable implements Callable<LoanRequestStatus> {

	private volatile Bank sourceBank;
	private volatile Bank destinationBank;
	private volatile String emailAddress;
	private volatile int sequenceNbr;
	private Logger logger;
	
	/**
	 * Constructor
	 * 
	 * @param sourceBank
	 * @param destinationBank
	 * @param emailAddress
	 * @param sequenceNbr
	 * @param logger
	 */
	public UdpGetLoanCallable(Bank sourceBank, Bank destinationBank, String emailAddress, int sequenceNbr, Logger logger) {
		
		this.sourceBank = sourceBank;
		this.destinationBank = destinationBank;
		this.emailAddress = emailAddress;
		this.sequenceNbr = sequenceNbr;
		this.logger = logger;
	}

	/**
	 * Makes a UDP request to another bank get loan information on a particular user
	 */
	public LoanRequestStatus call() {
		
		DatagramSocket clientSocket = null;
		
		try {
			
			//
			// REQUESTING
			//
			
			// Init data structures
			clientSocket = new DatagramSocket(); // sourceBank.udpAddress.getPort()
			final byte[] receiveData = new byte[1024];
			final DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        ObjectOutputStream oos;
	        
	        // Prepare the loan request message
			MessageRequestGetLoan message = new MessageRequestGetLoan();
			message.emailAddress = this.emailAddress;
			message.sequenceNbr = this.sequenceNbr;
			
			// Serialize the message
			oos = new ObjectOutputStream(baos);
			oos.writeObject(message);
			byte[] sendData = baos.toByteArray();
			
			logger.info(this.sourceBank.getTextId() + " requesting loan info for user " + this.emailAddress + " at bank " + this.destinationBank.getTextId());
			
			final DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, destinationBank.getUdpAddress());
			clientSocket.send(sendPacket);
	
			//
			// GETTING RESPONSE
			//
			clientSocket.setSoTimeout(10000);
			
			try {

				clientSocket.receive(receivePacket);
				
				// Parse the response data
				byte[] recvData = new byte[receivePacket.getLength()];
		        System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), recvData, 0, receivePacket.getLength());

				ByteArrayInputStream bais = new ByteArrayInputStream(recvData);
	            ObjectInputStream ois;
	            Object obj = null;
	            MessageResponseLoan resp = null;
	            
				try {
					ois = new ObjectInputStream(bais);
					obj = ois.readObject();
		            bais.close();
		            ois.close();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

	            if(obj instanceof MessageResponseLoan) {
	                resp = (MessageResponseLoan) obj;
	            } else {
	                System.out.println("UDP request was not a MessageResponseLoan object");
	                //System.exit(1);
	            }
				
				logger.info(this.sourceBank.getTextId() + " received loan info response from  " + this.destinationBank.getTextId() + " for user " + this.emailAddress + ": " + resp.amountAvailable);
				
				LoanRequestStatus status = new LoanRequestStatus();
				status.status = LoanRequestStatus.STATUS_SUCCESS;
				status.loanSum = resp.amountAvailable;
				
				return status;

			} catch (final SocketTimeoutException ste) {
				System.out.println("Timeout Occurred: Packet assumed lost");
			} finally {
				if (clientSocket != null)
					clientSocket.close();
			}
			
			clientSocket.close();

		} catch (final SocketException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		} finally {
			if (clientSocket != null)
				clientSocket.close();
		}
		
		return null;
	}
}
