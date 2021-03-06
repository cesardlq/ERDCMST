package edu.icesi.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.BasicComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.ArrayPrimitiveWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.join.TupleWritable;

/**
 * Update b and f after the delete.
   Compute the cost of inserting from node.
   Send messages to complete information

 * @author cdlq1
 *
 */
public class BFsUpdateAndBestLocationBeginningComputation extends AbstractComputation
		<IntWritable, RDCMSTValue, DoubleWritable, EntryWritable, MapWritable> {

	@Override
	public void compute(Vertex<IntWritable, RDCMSTValue, DoubleWritable> vertex, Iterable<EntryWritable> messages) throws IOException {
		
		//vertex.getValue().print();
		
		RDCMSTValue selectedNode = getBroadcast("selectedNode");
		System.out.println("Selected node at superstep 2: " + selectedNode.getId());
		System.out.println("Selected node parent: " + selectedNode.getPredecessorId());
		
		
		updateBnFs(vertex, selectedNode, messages);
		computecCostInsertingFromNode(vertex, selectedNode);
		sendBreakingEdgeInfo(vertex, selectedNode);
	}
	
	/**
	 * 
	   In this point, we have enough information to update the b and f values of every node in the graph.
	   Depending on the node, it updates its state in one way or another. 
	   If the node was a predecessor of the removing node, it has to update its b value with predecessorsDeleteCost,
	   only in the case where it does not have a different branch with a farther leaf.
	   If the node was a successor of  the removing node, it has to update its f value according to
	   the value in aggrtrAddDeleteCosts  which matches with the branch where the node is.
	   If the node is neither a predecessor nor a successor of the removing node, nothing happens. 
	 * @param vertex
	 * @param selectedNode
	 * @param message
	 */
	public void updateBnFs(Vertex<IntWritable, RDCMSTValue, DoubleWritable> vertex, RDCMSTValue selectedNode,
			Iterable<EntryWritable> messages){
		
		
	
		
	
//		IntWritable succesorId = (IntWritable) message.keySet().iterator().next();
//		DoubleWritable succesorB = (DoubleWritable) message.get(succesorId);
		DoubleWritable bestPossibleNewBDirPred = getBroadcast("bestPossibleNewBDirPred");
		
		
		if (vertex.getId().get() == selectedNode.getId()) {
			//THIS SHOULD BE IMPROVED
			int[] childrenIds = new int[vertex.getNumEdges()];
			int i = 0;
			System.out.println("selected vertex's children: ");
			for (Edge<IntWritable, DoubleWritable> edge : vertex.getEdges()) { 
    			childrenIds[i] = edge.getTargetVertexId().get();
    			System.out.println("child " + i + ": " + childrenIds[i]);
    			i++;
    			//System.out.println("Removing edge from " + vertex.getId().get() + " to " +  edge.getTargetVertexId());
    			//vertex.removeEdges(edge.getTargetVertexId());
    		}
			vertex.setEdges(new ArrayList<Edge<IntWritable,DoubleWritable>>());
			reduce("selectedVertexChildren", new ArrayPrimitiveWritable(childrenIds));
		} else if (vertex.getValue().getPositions()[selectedNode.getId()] == Position.PREDECESSOR) {
			System.out.println("It is a selected node's predecessor");
			int childToSelectedNode = -1;
			double maxPossibbleB = 0;
			for (EntryWritable message : messages) {
				
				Text messageKey = (Text) message.getKey();
				System.out.println("Incoming messages. Key: " + messageKey + " Value: " + message.get(messageKey));
				if (messageKey.toString().equals("ID")) {
					IntWritable childToSelectedNodeWritable = (IntWritable) message.get(messageKey);
					childToSelectedNode = childToSelectedNodeWritable.get();
					System.out.println("ID message: " + childToSelectedNode);
				} else {
					DoubleWritable possibleB = (DoubleWritable) message.get(message.getKey());
					maxPossibbleB = Math.max(maxPossibbleB,  possibleB.get() );
				}
			}
			if (vertex.getId().get() == selectedNode.getPredecessorId()) {
				System.out.println("Removing edge from " + vertex.getValue() + " to " +  selectedNode.getId());
	    		vertex.removeEdges(new IntWritable(selectedNode.getId()));
	    		MapWritable deleteCostForSuccessors = getAggregatedValue("sumDeleteCostForSuccessors");
				for (Writable branchId : deleteCostForSuccessors.keySet()) {
					System.out.println("id in deleteCostForSuccessors: " + branchId);
	    			System.out.println("Inserting edge from " + vertex.getId().get() + " to " + branchId );
	    			IntWritable IntBranchId = (IntWritable) branchId;
	    			double edgeWeight = vertex.getValue().getDistances()[IntBranchId.get()];
					vertex.addEdge(EdgeFactory.create((IntWritable) branchId, new DoubleWritable(edgeWeight)));
				}
				if (maxPossibbleB > bestPossibleNewBDirPred.get()) {
					vertex.getValue().setB(maxPossibbleB);
				} else {
					vertex.getValue().setB(bestPossibleNewBDirPred.get());
				}
				System.out.println("best posible b coming from unaffected branch: " + maxPossibbleB);
				System.out.println("best posible b coming from affected branch: " + bestPossibleNewBDirPred);
				System.out.println("b value of parent vertex " + vertex.getId() + ": " + vertex.getValue().getB());
				reduce("parentB", new EntryWritable(new IntWritable(vertex.getValue().getPredecessorId()), new DoubleWritable(vertex.getValue().getB())));
			} else {
				System.out.println("storing in allPredecessorsPossibleNewBs");
				ElementsToComputeB elementsToComputeB = new ElementsToComputeB(vertex.getValue().getPredecessorId(), maxPossibbleB, 
						vertex.getValue().getDistances()[childToSelectedNode]);			
				MapWritable elementsToComputeBMap = new MapWritable();
				elementsToComputeBMap.put(vertex.getId(), elementsToComputeB);
				reduce("allPredecessorsPossibleNewBs", elementsToComputeBMap);
			}
		} else if (vertex.getValue().getPositions()[selectedNode.getId()] == Position.SUCCESSOR) {
			MapWritable deleteCostForSuccessors = getAggregatedValue("sumDeleteCostForSuccessors");
			System.out.println("deleteCostForSuccessors:");
			for (Writable branchId : deleteCostForSuccessors.keySet()) {
				IntWritable branchIdInt = (IntWritable) branchId;
				System.out.println("Key: " + branchIdInt + " Value: " + deleteCostForSuccessors.get(branchId));
				//This vertex is in branchId
				if (vertex.getValue().getPositions()[branchIdInt.get()] == Position.SUCCESSOR || vertex.getId().get() == branchIdInt.get()) {
					DoubleWritable branchCost = (DoubleWritable) deleteCostForSuccessors.get(branchId);
					double newF = vertex.getValue().getF() + branchCost.get();
					System.out.println("newF: " + newF);
					vertex.getValue().setF(newF);
					if (vertex.getId().get() == branchIdInt.get()) {
						System.out.println("Updating parent after delete...");
						vertex.getValue().setPredecessorId(selectedNode.getPredecessorId());
					}
				}
			}
		}
	}
	
	/**
	 * compute the cost of inserting the removing node FROM NODE WAY in each node.
	 * @param vertex
	 * @param selectedNode
	 */
	public void computecCostInsertingFromNode(Vertex<IntWritable, RDCMSTValue, DoubleWritable> vertex, RDCMSTValue selectedNode) {
		//feasible insert
		boolean feasibleInsert = (vertex.getValue().getF() + vertex.getValue().getDistances()[selectedNode.getId()] + 0) <= 1000000;
		System.out.println("partial best location?: " + vertex.getValue().getPartialBestLocationCost());
		//selected vertex is not considered for best location operation
		if(feasibleInsert && vertex.getId().get() != selectedNode.getId()){
			double cost =  vertex.getValue().getDistances()[selectedNode.getId()];
			System.out.println("Cost FROM NODE: " + cost);
			vertex.getValue().setPartialBestLocationCost(cost);
		}
		
	}
	
	/**
	 * send to its successor both the distance between it and them and the distance between it and the removing node.
	 * @param vertex
	 * @param selectedNode
	 */
	public void sendBreakingEdgeInfo(Vertex<IntWritable, RDCMSTValue, DoubleWritable> vertex, RDCMSTValue selectedNode){
		for(Edge<IntWritable,DoubleWritable> edge : vertex.getEdges()){
			MapWritable messages = new MapWritable();
			IntWritable targetId = edge.getTargetVertexId();
			messages.put(new Text("TO_SUCC"), new DoubleWritable(vertex.getValue().getDistances()[targetId.get()]));
			messages.put(new Text("TO_SELEC"), new DoubleWritable(vertex.getValue().getDistances()[selectedNode.getId()]));
			sendMessage(targetId, messages);
		}
	}
	
	

}