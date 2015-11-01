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

import ca.primat.comp6231a2.udpmessage.MessageRequestGetLoan;
import ca.primat.comp6231a2.udpmessage.MessageResponseLoan;

/**
 * UDP listener to accept incoming packets from bank peers (and respond to the packets)
 * 
 * @author mat
 *
 */
public class BankUdpListener implements Runnable {

	protected volatile Bank bank;
	protected Logger logger;

	/**
	 * Constructor - Run the UDP Listener
	 * 
	 * @param bank
	 * @param logger
	 */
	BankUdpListener(Bank bank, Logger logger) {
		
		this.bank = bank;
		this.logger = logger;
	}
	
	/**
	 * Plain text version
	 */
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
				logger.info(this.bank.getTextId() + " waiting for loan info request packet on " + this.bank.udpAddress.toString());
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
					logger.info(this.bank.getTextId() + " an invalid packet from: " + remoteIpAddress + ":" + remotePort + ". Discarding it.");
					continue;
				}
	            bais.close();
	            ois.close();
	            MessageRequestGetLoan req = null;
	            
				if (obj instanceof MessageRequestGetLoan) {
					req = (MessageRequestGetLoan) obj;
				} else {
					logger.info(this.bank.getTextId() + " received an unknown request packet from " + remoteIpAddress + ":" + remotePort + ". Discarding it.");
					continue;
				}
				
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
		        ObjectOutputStream oos = new ObjectOutputStream(baos);
		        oos.writeObject(resp);
				sendData = baos.toByteArray();
				baos.close();
	            oos.close();

				logger.info(this.bank.getTextId() + " responding to loan request for user " + req.emailAddress + " with loan sum: " + resp.amountAvailable);

				final DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, remoteIpAddress, remotePort);
				serverSocket.send(sendPacket);
			}

		} catch (final SocketException e) {
			logger.info("Unable to bind " + this.bank.getId() + " to UDP Port " + this.bank.udpAddress.getPort() + ". Port already in use.");
			System.exit(1);
		} catch (final IOException e) {
			e.printStackTrace();
			//System.exit(1);
		} finally {if(serverSocket != null) serverSocket.close();}
	}
}
