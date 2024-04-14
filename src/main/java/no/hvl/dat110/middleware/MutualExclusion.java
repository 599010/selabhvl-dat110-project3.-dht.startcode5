/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class MutualExclusion {
		
	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);
	/** lock variables */
	private boolean CS_BUSY = false;						// indicate to be in critical section (accessing a shared resource) 
	private boolean WANTS_TO_ENTER_CS = false;				// indicate to want to enter CS
	private List<Message> queueack; 						// queue for acknowledged messages
	private List<Message> mutexqueue;						// queue for storing process that are denied permission. We really don't need this for quorum-protocol
	
	private LamportClock clock;								// lamport clock
	private Node node;
	
	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}
	
	public synchronized void acquireLock() {
		CS_BUSY = true;
	}
	
	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	//synchronzied sia stuck i loop, yesss woohooooo"!!!!!! elske loops
	public synchronized boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
	    
	    logger.info(node.getNodeName() + " wants to access CS");
	    queueack.clear();
	    mutexqueue.clear();
	    clock.increment();
	    message.setClock(clock.getClock());
	    WANTS_TO_ENTER_CS = true;
	    
	    List<Message> uniquepeers = removeDuplicatePeersBeforeVoting();
	    multicastMessage(message, uniquepeers);
	    
	   // while (!areAllMessagesReturned(uniquepeers.size())) {
	    //    try {
	    //        wait();  // Wait for notifications that an acknowledgment has been received
	    //    } catch (InterruptedException e) {
	         //  e.printStackTrace();
	      //      // Handle interruption
	      //  }
	   // }
	    
	    final long timeout = 10000; // Timeout of 10 seconds, for example
	    final long start = System.currentTimeMillis();
	    while (!areAllMessagesReturned(uniquepeers.size()) && System.currentTimeMillis() - start < timeout) {
	        try {
	            wait(1000);  // Wait for up to 1 second before checking again
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	            Thread.currentThread().interrupt(); // Re-interrupt the thread
	            return false; // Exit due to interruption
	        }
	    }

	    // Check if timeout occurred
	    if (System.currentTimeMillis() - start >= timeout) {
	        logger.error("Timeout occurred waiting for mutex acknowledgments");
	        return false;
	    }
	    
	    acquireLock();
	    node.broadcastUpdatetoPeers(updates);
	    releaseLocks();
	    mutexqueue.clear();
	    WANTS_TO_ENTER_CS = false;
	    
	    return true;
	}
	
	// multicast message to other processes including self
	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
	    
	    for (Message peer : activenodes) {
	        NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
	        stub.onMutexRequestReceived(message);
	    }
	}
	
	//synchronzied sia stuck i loop :p
	public synchronized void onMutexRequestReceived(Message message) throws RemoteException {
	    
	    clock.increment();
	    
	    if (message.getNodeName().equals(node.getNodeName())) {
	        onMutexAcknowledgementReceived(message);
	    } else {
	        int caseid = WANTS_TO_ENTER_CS ? (CS_BUSY ? 1 : 2) : 0;
	        doDecisionAlgorithm(message, mutexqueue, caseid);
	    }
	}
	
	//synchronized sia stuck i loop lololol
	public synchronized void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
	    
	    
	    switch (condition) {
	    case 0: {
	        NodeInterface stub = Util.getProcessStub(message.getNodeName(), message.getPort());
	        Message ack = new Message(node.getNodeID(), node.getNodeName(), node.getPort());
	        ack.setClock(clock.getClock()); // set clock
	        stub.onMutexAcknowledgementReceived(ack); // send ack
	        break;
	    }
	        case 1: // Receiver already has access to the resource (don't reply but queue the request)
	            queue.add(message);
	            break;
	        case 2: {
	            // Assuming getNodeID returns a BigInteger, and getClock returns an int
	            int compare = Integer.compare(clock.getClock(), message.getClock());
	            BigInteger localNodeID = node.getNodeID();
	            BigInteger messageNodeID = message.getNodeID();

	            boolean isLesser = compare < 0 || (compare == 0 && localNodeID.compareTo(messageNodeID) < 0);

	            if (isLesser) {
	                NodeInterface stub = Util.getProcessStub(message.getNodeName(), message.getPort());
	                Message ack = new Message(localNodeID, node.getNodeName(), node.getPort());
	                ack.setClock(clock.getClock());
	                ack.setAcknowledged(true); // Set the flag to indicate that this is an acknowledgment
	                stub.onMutexAcknowledgementReceived(ack);
	            } else {
	                queue.add(message); // Add the incoming request to the queue if the local node's timestamp or ID is higher
	            }

	            break;
	        }
	        default:
	            break;
	    }
	}
	
	//synchronized sia stuck i loop
	public synchronized void onMutexAcknowledgementReceived(Message message) throws RemoteException {
	    
	    queueack.add(message);
	    notifyAll();  // Notify any waiting threads that an acknowledgment has been received
	}
	
	// multicast release locks message to other processes including self
	public void multicastReleaseLocks(Set<Message> activenodes) throws RemoteException {
	    
	    for (Message nodeinfo : activenodes) {
	        NodeInterface stub = Util.getProcessStub(nodeinfo.getNodeName(), nodeinfo.getPort());
	        stub.releaseLocks();
	    }
	}
	
	//synchronized sia stuck i loop
	private synchronized boolean areAllMessagesReturned(int numvoters) {
	    
	    boolean allreturned = (queueack.size() == numvoters);
	    if (allreturned) {
	        queueack.clear();  // Clear the list for future use
	    }
	    return allreturned;
	}
	
	private List<Message> removeDuplicatePeersBeforeVoting() {
		
		List<Message> uniquepeer = new ArrayList<Message>();
		for(Message p : node.activenodesforfile) {
			boolean found = false;
			for(Message p1 : uniquepeer) {
				if(p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if(!found)
				uniquepeer.add(p);
		}		
		return uniquepeer;
	}
}
