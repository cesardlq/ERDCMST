package edu.icesi.app;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

import org.apache.giraph.master.MasterCompute;
import org.apache.giraph.utils.WritableUtils;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Writable;

import aggregators.AddDeleteCostReduce;
import aggregators.BestLocationAggregator;
import aggregators.ArrayPrimitiveOverwriteAggregator;
import aggregators.SelectedNodeAggregator;
import aggregators.SumSuccessorDeleteCostsAggregator;

public class RDCMSTMasterCompute extends MasterCompute {
	
	private ArrayList<Integer> list;
	private int SUPER_STEPS_PER_ITERATION = 5;
	private int iteration = 0;
	private int MAX_ITERARIONS = 5;
	
	//JUST FOR DEBUGGING
	private int[] selectedNodes = new int[]{2, 1, 3, 2, 3};
	
	private RDCMSTValue selectedNode;

	@Override
	public void readFields(DataInput arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void write(DataOutput arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void compute() {
		
		
		
		//DANGEROUS CAST!
		int superStepPhase =  (int) getSuperstep() % SUPER_STEPS_PER_ITERATION;
		System.out.println("\n\n" + iteration);
		System.out.println("Iteration/Movement: " + iteration);
		System.out.println("***** Computation " +  superStepPhase + " *****");
		System.out.println("***MASTER ***");
		if (iteration < MAX_ITERARIONS) {		
			switch (superStepPhase) {
				//**DELETE OPERATION
				case 0:					
					//Node selection
					setComputation(EdgeRemovalComputation.class);
					Random rand = new Random();
					System.out.println(this.getClass().getName() + " - Total number of vertices: " + (int) getTotalNumVertices());
//					int  selectedNodeId = rand.nextInt(3) + 1;	
					//JUST FOE DEBUGGING
					int  selectedNodeId = selectedNodes[iteration];
					//System.out.println("Aggregator:: " + getAggregatedValue("selectedNode") );
					System.out.println("Broadcasting:: " + selectedNodeId);
					broadcast("selectedNodeId", new IntWritable(selectedNodeId));
					broadcast("selectedNode", selectedNode);
					//
					registerReducer("addDeleteCostForSuccessors", new AddDeleteCostReduce());
					break;
				case 1:
					
					selectedNode = getAggregatedValue("selectedNodeA");
					broadcast("selectedNode", selectedNode);
					setComputation(EdgeInsertionComputation.class);
					MapWritable deleteCosts = getReduced("addDeleteCostForSuccessors");
					System.out.println("Length of KeySet of reduce operation: " + deleteCosts.keySet().size());
					MapWritable possibleNewBsDirPred = new MapWritable();
					for (Writable dw: deleteCosts.keySet()) {
						System.out.println("Key: " + dw + " - Delete Costs:: " + deleteCosts.get(dw));
						possibleNewBsDirPred.put(dw, new IntWritable(0));
					}
					setAggregatedValue("sumDeleteCostForSuccessors", deleteCosts);
					break;
				//**BEST LOCATION OPERATION
			    //For each node there are two possible ways of inserting a node:
				//1) directly as a leaf successor of the node, we called this FROM NODE WAY; and 
				//2) as a predecessor of the node, breaking the existing edge between the old predecessor and it, we called this BREAKING EDGE WAY.
				case 2:
//					selectedNode = getAggregatedValue("selectedNode");
//					Location bestLocation = getAggregatedValue("bestLocation");
//					System.out.println("Selected node at master Compute 2: " + selectedNode.getId());
//					System.out.println("Best Location at master Compute 2: " + bestLocation.getNodeId());
					
					broadcast("selectedNode", selectedNode);
					setComputation(BFsUpdateAndBestLocationBeginningComputation.class);
					DoubleWritable longestBranchLength = new DoubleWritable(getLongestBranchLength());
					broadcast("bestPossibleNewBDirPred", longestBranchLength);
					/**
					 * TODO
					 */
					break;
				case 3:
					broadcast("selectedNode", selectedNode);
					computeBValues();
					setComputation(BestLocationEndingComputation.class);
					break;
				case 4:
					broadcast("selectedNode", selectedNode);
					setComputation(insertOperationAndBFsUpdate.class);
					iteration++;
					break;
				default:
					
					
			}
		} else {
			System.out.println("Halting:: ");
			haltComputation();
		}
		
	}


	@Override
	public void initialize() throws InstantiationException, IllegalAccessException {

		System.out.println("Master compute's initialize()");
		
		selectedNode = new RDCMSTValue();
		selectedNode.setId(-1);
		
		registerPersistentAggregator("selectedNodeA", SelectedNodeAggregator.class);
		//The cost which is necessary to update the values of f the successors branches of the selected node.
		//<K,V> K: Id of the one of selected node's child; V: Cost necessary to update the values of f in K branch of the selected node.
		registerPersistentAggregator("sumDeleteCostForSuccessors", SumSuccessorDeleteCostsAggregator.class);
		//New variable to decide which of the new branches, created after the removing node removal, 
		//drive now to the farthest leaf.
		registerPersistentAggregator("possibleNewBsDirPred", SumSuccessorDeleteCostsAggregator.class);
		
		registerAggregator("bestLocation", BestLocationAggregator.class);
		
		//We are doing the positions' update of selected node just with messages
//		registerPersistentAggregator("bestLocationPositions", ArrayPrimitiveOverwriteAggregator.class);
	}
	
	/**
	 *  This value is computed as the maximum value in the possibleNewBsDirPred map, and will become
	 *  the new b value of the predecessor of the removing node,
	 *  which means that every predecessor of the removing node will have the chance of update
	 *  its own value from this value.
	 * @return
	 */
	public double getLongestBranchLength() {
		MapWritable branchLengths = getAggregatedValue("possibleNewBsDirPred");
		double largestBranchLength = 0;
		for (Writable branch: branchLengths.keySet()) {
			DoubleWritable currentLength = (DoubleWritable) branchLengths.get(branch);
			if (currentLength.get() > largestBranchLength) {
				largestBranchLength = currentLength.get();
			}
		}
		return largestBranchLength;
	}
	
	/**
	 * Compute B values for all predecessors of the selected node
	 */
	private void computeBValues() {
		// TODO Auto-generated method stub
		
	}

}
