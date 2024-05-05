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

public class MutualExclusion {
    
    private static final Logger logger = LogManager.getLogger(MutualExclusion.class);
    
    private boolean CS_BUSY = false;
    private boolean WANTS_TO_ENTER_CS = false;
    private List<Message> queueack;
    private List<Message> mutexqueue;
    private LamportClock clock;
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

    public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
        
        logger.info(node.getNodeName() + " wants to access CS");
        queueack.clear();
        mutexqueue.clear();
        clock.increment();
        message.setClock(clock.getClock());
        WANTS_TO_ENTER_CS = true;
        
        List<Message> uniquepeers = removeDuplicatePeersBeforeVoting();
        multicastMessage(message, uniquepeers);
        
        if (!areAllMessagesReturned(uniquepeers.size())) {
            return false;
        }
        
        acquireLock();
        node.broadcastUpdatetoPeers(updates);
        releaseLocks();
        mutexqueue.clear();
        WANTS_TO_ENTER_CS = false;
        
        return true;
    }
    
    private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
        
        for (Message peer : activenodes) {
            NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
            stub.onMutexRequestReceived(message);
        }
    }
    
    public synchronized void onMutexRequestReceived(Message message) throws RemoteException {
        
        clock.increment();
        
        if (message.getNodeName().equals(node.getNodeName())) {
            onMutexAcknowledgementReceived(message);
        } else {
            int caseid = WANTS_TO_ENTER_CS ? (CS_BUSY ? 1 : 2) : 0;
            doDecisionAlgorithm(message, mutexqueue, caseid);
        }
    }
    
    public synchronized void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
        
        switch (condition) {
            case 0: {
                NodeInterface stub = Util.getProcessStub(message.getNodeName(), message.getPort());
                message.setAcknowledged(true);
                stub.onMutexAcknowledgementReceived(message);
                break;
            }
            case 1:
                queue.add(message);
                break;
            case 2: {
                int compare = Integer.compare(node.getMessage().getClock(), message.getClock());
                BigInteger localNodeID = node.getNodeID();
                BigInteger messageNodeID = message.getNodeID();

                boolean isLesser = compare < 0 || (compare == 0 && localNodeID.compareTo(messageNodeID) < 0);

                if (isLesser) {
                    NodeInterface stub = Util.getProcessStub(message.getNodeName(), message.getPort());
                    message.setAcknowledged(true);
                    stub.onMutexAcknowledgementReceived(message);
                } else {
                    queue.add(message);
                }

                break;
            }
            default:
                break;
        }
    }
    
    public synchronized void onMutexAcknowledgementReceived(Message message) throws RemoteException {
        
        queueack.add(message);
        notifyAll();
    }
    
    public void multicastReleaseLocks(Set<Message> activenodes) throws RemoteException {
        
        for (Message nodeinfo : activenodes) {
            NodeInterface stub = Util.getProcessStub(nodeinfo.getNodeName(), nodeinfo.getPort());
            stub.releaseLocks();
        }
    }
    
    private synchronized boolean areAllMessagesReturned(int numvoters) {
        
        boolean allreturned = (queueack.size() == numvoters);
        if (allreturned) {
            queueack.clear();
        }
        return allreturned;
    }
    
    private List<Message> removeDuplicatePeersBeforeVoting() {
        
        List<Message> uniquepeer = new ArrayList<Message>();
        for (Message p : node.activenodesforfile) {
            boolean found = false;
            for (Message p1 : uniquepeer) {
                if (p.getNodeName().equals(p1.getNodeName())) {
                    found = true;
                    break;
                }
            }
            if (!found)
                uniquepeer.add(p);
        }
        return uniquepeer;
    }
}
