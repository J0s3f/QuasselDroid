package com.iskrembilen.quasseldroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class NetworkCollection extends Observable implements Observer {
	private static final String TAG = NetworkCollection.class.getSimpleName();
	List<Network> networkList = new ArrayList<Network>();
	HashMap<Integer, Network> networkMap = new HashMap<Integer, Network>();
	
	public void addNetwork(Network network) {
		networkMap.put(network.getId(), network);
		networkList.add(network);
		network.addObserver(this);
		Collections.sort(networkList);
		setChanged();
		notifyObservers();
	}
	
	public Network getNetwork(int location) {
		return networkList.get(location);
	}
	
	public Buffer getBufferById(int bufferId) {
		for (Network network : networkList) {
			if(network.getStatusBuffer().getInfo().id == bufferId)
				return network.getStatusBuffer();
			if(network.getBuffers().hasBuffer(bufferId)) {
				return network.getBuffers().getBuffer(bufferId);
			}
		}
		return null;
	}
	
	public Buffer getPreviousBufferFromId(int bufferId, boolean incStatus) {
		Buffer last = null;
		boolean foundCurrent = false;
		for (int i=networkList.size()-1; i>=0; i--) {
			List<Buffer> bufList = networkList.get(i).getBuffers().getRawFilteredBufferList();
			for (int ii=bufList.size()-1; ii>=0; ii--) {
				Buffer buf = bufList.get(ii);
				
				if (last == null)
					last = buf;
				
				if (foundCurrent)
					return buf;
				
				if (buf.getInfo().id == bufferId)
					foundCurrent = true;
			}
			
			if (incStatus) {
				Buffer status = networkList.get(i).getStatusBuffer();
				if (last == null)
					last = status;
				
				if (foundCurrent)
					return status;
			}
		}

		// Still not found it but we're at the start of the list, so return the last buffer
		return last;
	}
	
	public Buffer getNextBufferFromId(int bufferId, boolean incStatus) {
		Buffer first = null;
		boolean foundCurrent = false;
		for (Network network : networkList) {
			if (first == null && incStatus)
				first = network.getStatusBuffer();
			
			if (network.getStatusBuffer().getInfo().id == bufferId) {
				if (incStatus && foundCurrent)
					return network.getStatusBuffer();
				foundCurrent = true;
			}
			
			for (Buffer buf : network.getBuffers().getRawFilteredBufferList()) {
				if (first == null)
					first = buf;
				
				if (foundCurrent)
					return buf;
				
				if (buf.getInfo().id == bufferId)
					foundCurrent = true;
			}
		}
		
		return first;
	}
	
	public Network getNetworkById(int networkId) {
		return networkMap.get(networkId);
	}
	
	public void addBuffer(Buffer buffer) {
		int id = buffer.getInfo().networkId;
		for(Network network : networkList) {
			if(network.getId() == id) {
				network.addBuffer(buffer);
				return;
			}
		}
		throw new RuntimeException("Buffer + " + buffer.getInfo().name + " has no valide network id " + id);
	}

	public List<Network> getNetworkList() {
		return networkList;
	}

	public int size() {
		return networkList.size();
	}

	public void update(Observable observable, Object data) {
		setChanged();
		notifyObservers();
		
	}
}
