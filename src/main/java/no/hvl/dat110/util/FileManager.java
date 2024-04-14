package no.hvl.dat110.util;


/**
 * @author tdoy
 * dat110 - project 3
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class FileManager {
	
	private static final Logger logger = LogManager.getLogger(FileManager.class);
	
	private BigInteger[] replicafiles;							// array stores replicated files for distribution to matching nodes
	private int numReplicas;									// let's assume each node manages nfiles (5 for now) - can be changed from the constructor
	private NodeInterface chordnode;
	private String filepath; 									// absolute filepath
	private String filename;									// only filename without path and extension
	private BigInteger hash;
	private byte[] bytesOfFile;
	private String sizeOfByte;
	
	private Set<Message> activeNodesforFile = null;
	
	public FileManager(NodeInterface chordnode) throws RemoteException {
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, int N) throws RemoteException {
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, String filepath, int N) throws RemoteException {
		this.filepath = filepath;
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
public void createReplicaFiles() {
	    
	    // Ensure filename is set before creating replicas
	    if (filename == null || filename.isEmpty()) {
	        logger.error("Filename is not set. Cannot create replica files.");
	        return;
	    }
	    
	    for(int i = 0; i < numReplicas; i++) {
	        // Append the index to the filename to create a replica
	        String replicaName = filename + i;
	        
	        // Hash the replica name
	        BigInteger replicaHash = Hash.hashOf(replicaName);
	        
	        // Store the hash in the replicafiles array
	        replicafiles[i] = replicaHash;
	    }
	}
	
    /**
     * 
     * @param bytesOfFile
     * @throws RemoteException 
     */
public int distributeReplicastoPeers() throws RemoteException {
    int counter = 0;

    // create replicas of the filename
    createReplicaFiles();
    
    // randomly appoint one of the replicas as the primary
    Random rnd = new Random();                         
    int primaryIndex = rnd.nextInt(numReplicas);
    
    // iterate over the replicas
    for(int i = 0; i < replicafiles.length; i++) {
        BigInteger replica = replicafiles[i];
        
        // find the successor node of the replica
        NodeInterface successor = chordnode.findSuccessor(replica);
        
        // add the replica to the successor's keys
        if(successor != null) {
            successor.addKey(replica);
            boolean isPrimary = (i == primaryIndex);
            successor.saveFileContent(filename, replica, this.getBytesOfFile(), isPrimary);
            counter++;
        }
    }

    return counter;
}
	
	/**
	 * 
	 * @param filename
	 * @return list of active nodes having the replicas of this file
	 * @throws RemoteException 
	 */
    public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException {

    this.filename = filename;
    activeNodesforFile = new HashSet<Message>();

    // generate the N replicas from the filename
    createReplicaFiles();

    // iterate over the replicas of the file
    for(BigInteger replicaID : replicafiles) {

        // for each replica, find its successor (peer)
        NodeInterface successor = chordnode.findSuccessor(replicaID);

        if (successor != null) {
            // get the metadata of the replica from the successor
            Message metadata = successor.getFilesMetadata(replicaID);

            // check if the metadata is not null and add to the set of active nodes
            if (metadata != null) {
                activeNodesforFile.add(metadata);
            }
        }
    }

    return activeNodesforFile;
}

	/**
	 * Find the primary server - Remote-Write Protocol
	 * @return 
	 */
    public NodeInterface findPrimaryOfItem() throws RemoteException {

        requestActiveNodesForFile(this.filename); // populate activeNodesforFile

        for (Message metadata : activeNodesforFile) {
            if (metadata.isPrimaryServer()) { // check if this is the primary server
                NodeInterface node = Util.getProcessStub(metadata.getNodeName(), metadata.getPort());
                return node; // return the stub to the primary server
            }
        }
        return null;
    }

	
    /**
     * Read the content of a file and return the bytes
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     */
    public void readFile() throws IOException, NoSuchAlgorithmException {
    	
    	File f = new File(filepath);
    	
    	byte[] bytesOfFile = new byte[(int) f.length()];
    	
		FileInputStream fis = new FileInputStream(f);
        
        fis.read(bytesOfFile);
		fis.close();
		
		//set the values
		filename = f.getName().replace(".txt", "");		
		hash = Hash.hashOf(filename);
		this.bytesOfFile = bytesOfFile;
		double size = (double) bytesOfFile.length/1000;
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		sizeOfByte = nf.format(size);
		
		logger.info("filename="+filename+" size="+sizeOfByte);
    	
    }
    
    public void printActivePeers() {
    	
    	activeNodesforFile.forEach(m -> {
    		String peer = m.getNodeName();
    		String id = m.getNodeID().toString();
    		String name = m.getNameOfFile();
    		String hash = m.getHashOfFile().toString();
    		int size = m.getBytesOfFile().length;
    		
    		logger.info(peer+": ID = "+id+" | filename = "+name+" | HashOfFile = "+hash+" | size ="+size);
    		
    	});
    }

	/**
	 * @return the numReplicas
	 */
	public int getNumReplicas() {
		return numReplicas;
	}
	
	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}
	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}
	/**
	 * @return the hash
	 */
	public BigInteger getHash() {
		return hash;
	}
	/**
	 * @param hash the hash to set
	 */
	public void setHash(BigInteger hash) {
		this.hash = hash;
	}
	/**
	 * @return the bytesOfFile
	 */ 
	public byte[] getBytesOfFile() {
		return bytesOfFile;
	}
	/**
	 * @param bytesOfFile the bytesOfFile to set
	 */
	public void setBytesOfFile(byte[] bytesOfFile) {
		this.bytesOfFile = bytesOfFile;
	}
	/**
	 * @return the size
	 */
	public String getSizeOfByte() {
		return sizeOfByte;
	}
	/**
	 * @param size the size to set
	 */
	public void setSizeOfByte(String sizeOfByte) {
		this.sizeOfByte = sizeOfByte;
	}

	/**
	 * @return the chordnode
	 */
	public NodeInterface getChordnode() {
		return chordnode;
	}

	/**
	 * @return the activeNodesforFile
	 */
	public Set<Message> getActiveNodesforFile() {
		return activeNodesforFile;
	}

	/**
	 * @return the replicafiles
	 */
	public BigInteger[] getReplicafiles() {
		return replicafiles;
	}

	/**
	 * @param filepath the filepath to set
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
}
