package ca.primat.comp6231a2.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.logging.Logger;

import ca.primat.comp6231a2.model.Account;
import ca.primat.comp6231a2.udpmessage.MessageRequestGetLoan;
import ca.primat.comp6231a2.udpmessage.MessageRequestTransferLoan;
import ca.primat.comp6231a2.udpmessage.MessageResponseLoan;
import ca.primat.comp6231a2.udpmessage.MessageResponseTransferLoan;
import dlms.OpenAccountResponse;

/**
 * The UdpListener class is a runnable associated with a specific bank servant 
 * that runs in an infinite loop to accept incoming UDP/IP packets or more 
 * appropriately, requests from other bank servants
 * 
 * @author mat
 *
 */
public class UdpListener implements Runnable {

	protected volatile Bank bank;
	protected Logger logger;

	/**
	 * Constructor
	 * 
	 * @param bank
	 * @param logger
	 */
	UdpListener(Bank bank, Logger logger) {
		
		this.bank = bank;
		this.logger = logger;
	}

	@Override
	public void run() {
		
		DatagramSocket serverSocket = null;

		try {

			serverSocket = new DatagramSocket(this.bank.udpAddress);
			byte[] receiveData = new byte[1024];
			byte[] sendData = new byte[1024];

			while (true) {

				//
				// LISTENER
				//
				
				receiveData = new byte[1024];
				final DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				// Wait for the packet
				logger.info(this.bank.getTextId() + ": Waiting for bank UDP request on " + this.bank.udpAddress.toString());
				serverSocket.receive(receivePacket);
				
				// Received a request. Parse it.
				byte[] data = new byte[receivePacket.getLength()];
		        System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());
				final InetAddress remoteIpAddress = receivePacket.getAddress();
				final int remotePort = receivePacket.getPort();

				// Extract the receiver's message into the appropriate object
				ByteArrayInputStream bais = new ByteArrayInputStream(data);
	            ObjectInputStream ois = new ObjectInputStream(bais);
	            Object obj;
				try {
					obj = ois.readObject();
				} catch (ClassNotFoundException e) {
					logger.info(this.bank.getTextId() + ": Recieved an invalid packet from: " + remoteIpAddress + ":" + remotePort + ". Discarding it.");
					continue;
				}
	            bais.close();
	            ois.close();

	            // Take appropriate action based on the request
				if (obj instanceof MessageRequestGetLoan) {
					this.RespondGetLoan((MessageRequestGetLoan) obj, sendData, remoteIpAddress, remotePort, serverSocket);
				} 
				else if (obj instanceof MessageRequestTransferLoan) {
					this.RespondTransferLoan((MessageRequestTransferLoan) obj, sendData, remoteIpAddress, remotePort, serverSocket);
				} else {
					logger.info(this.bank.getTextId() + ": Received an unknown request packet from " + remoteIpAddress + ":" + remotePort + ". Discarding it.");
					continue;
				}
			}

		} catch (final SocketException e) {
			logger.info(this.bank.getTextId() + ": Unable to bind " + this.bank.getId() + " to UDP Port " + this.bank.udpAddress.getPort() + ". Port already in use.");
			System.exit(1);
		} catch (final IOException e) {
			e.printStackTrace();
			//System.exit(1);
		} finally {if(serverSocket != null) serverSocket.close();}
	}
	

	/**
	 * 
	 * @param req
	 * @param sendData
	 * @param remoteIpAddress
	 * @param remotePort
	 * @param serverSocket
	 */
	protected void RespondTransferLoan(MessageRequestTransferLoan req, byte[] sendData, InetAddress remoteIpAddress, 
			int remotePort, DatagramSocket serverSocket) {

		// Request parsed successfully
		logger.info(this.bank.getTextId() + ": Received loan transfer request from " + this.bank.udpAddress.toString() + " for user " + req.account.getEmailAddress());
		
		//
		// RESPONDER
		//

		// Check if the account exists already and get the account number so we can add the loan
		int accountNbr = 0;
		Account account = this.bank.getAccount(req.loan.getEmailAddress());
		if (account == null) {

			
			 OpenAccountResponse oaResp = this.bank.createAccount(req.account.getFirstName(), req.account.getLastName(), 
					 req.account.getEmailAddress(), req.account.getPhoneNbr(), req.account.getPassword());
			 if (!oaResp.result) {
				 // TODO: Handle failed account creation
			 }
			 accountNbr = oaResp.accountNbr;
			 logger.info(this.bank.getTextId() + ": Loan transfer created a new account for user " + req.account.getEmailAddress() + " + with number " + accountNbr);
		}
		else {
			accountNbr = account.getAccountNbr();
		}
		
		// Add the loan
		int newLoandId = this.bank.createLoan(accountNbr, req.loan.getEmailAddress(), req.loan.getAmount(), req.loan.getDueDate());
		logger.info(this.bank.getTextId() + ": Loan transfer created a new loan for user " + req.account.getEmailAddress() + " with ID " + newLoandId);
		
		MessageResponseTransferLoan resp = new MessageResponseTransferLoan();
		resp.message = "Loan added successfully";
		resp.status = true;
		resp.loanId = newLoandId;
		resp.accountNbr = accountNbr;
		resp.sequenceNbr = req.sequenceNbr;
		
        // Prep the response
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(resp);
			sendData = baos.toByteArray();
			baos.close();
	        oos.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		logger.info(this.bank.getTextId() + ": Responding to successful loan transfer request for user " + req.account.getEmailAddress());

		final DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, remoteIpAddress, remotePort);
		
		try {
			serverSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param req
	 * @param sendData
	 * @param remoteIpAddress
	 * @param remotePort
	 * @param serverSocket
	 */
	protected void RespondGetLoan(MessageRequestGetLoan req, byte[] sendData, InetAddress remoteIpAddress, int remotePort, DatagramSocket serverSocket) {

		// Request parsed successfully
		logger.info(this.bank.getTextId() + " received loan request from " + this.bank.udpAddress.toString() + " for user " + req.emailAddress);
		
		//
		// RESPONDER
		//

        // Get the sum of all loans for this user and create the response
		int loanSum = this.bank.getLoanSum(req.emailAddress);
        MessageResponseLoan resp = new MessageResponseLoan();
        resp.sequenceNbr = req.sequenceNbr;
        resp.amountAvailable = loanSum;
		
        // Prep the response
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(resp);
			sendData = baos.toByteArray();
			baos.close();
	        oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		logger.info(this.bank.getTextId() + " responding to loan request for user " + req.emailAddress + " with loan sum: " + resp.amountAvailable);

		final DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, remoteIpAddress, remotePort);
		
		try {
			serverSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
