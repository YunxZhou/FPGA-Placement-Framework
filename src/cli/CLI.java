package cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import packers.BlePacker;
import packers.ClbPacker;
import placers.Placer;
import placers.MDP.MDPBasedPlacer;
import placers.SAPlacer.EfficientBoundingBoxNetCC;
import timinganalysis.TimingGraph;

import architecture.Architecture;
import architecture.FourLutSanitized;
import architecture.HeterogeneousArchitecture;

import circuit.BlePackedCircuit;
import circuit.PackedCircuit;
import circuit.PrePackedCircuit;
import circuit.parser.blif.BlifReader;
import cli.Options;


public class CLI {
	
	public static void main(String[] args) {
		
		Options options = new Options();
		options.parseArguments(args);
		
		
		// Read the blif file
		BlifReader blifReader = new BlifReader();
		PrePackedCircuit prePackedCircuit = null;
		
		try {
			prePackedCircuit = blifReader.readBlif(options.blifFile.toString(), 6);
		} catch(IOException e) {
			error("Failed to read blif file");
		}
		
		
		// Pack the circuit
		BlePacker blePacker = new BlePacker(prePackedCircuit);
		BlePackedCircuit blePackedCircuit = blePacker.pack();
		
		ClbPacker clbPacker = new ClbPacker(blePackedCircuit);
		PackedCircuit packedCircuit = clbPacker.pack();
		
		
		// Set the architecture
		// Currently only the heterogeneous architecture is supported
		Architecture architecture = null; // Needed to suppress "variable may not be initialized" errors
		switch(options.architecture) {
		
		case "4LUT":
		case "4lut":
			int archSize = FourLutSanitized.calculateSquareArchDimensions(packedCircuit);
			int trackwidth = 4;
			architecture = new FourLutSanitized(archSize, archSize, trackwidth);
			break;
		
		case "heterogeneous":
			architecture = new HeterogeneousArchitecture(packedCircuit);
			break;
		
		default:
			error("Architecture type not recognized: " + options.architecture);
		}
		
		
		// Place the circuit
		Placer placer = null; // Needed to suppress "variable may not be initialized" errors
		switch(options.placer) {
			
		case "MDP":
			if(!architecture.getClass().equals(FourLutSanitized.class)) {
				error("MDP doesn't support the architecture \"heterogeneous\" (yet)");
			}
			
			placer = new MDPBasedPlacer((FourLutSanitized) architecture, packedCircuit);
			break;
		
		case "analytical":
			
		case "random":
			
		case "SA":
			
		case "TDSA":
			error("Placer not yet implemented: " + options.placer);
			System.exit(1);
			
		default:
			error("Placer type not recognized: " + options.placer);
			System.exit(1);
		}
		
		long timeStartPlace = System.nanoTime();
		placer.place();
		long timeStopPlace = System.nanoTime();
		
		
		
		// Analyze the circuit and print statistics
		System.out.println();
		double placeTime = (timeStopPlace - timeStartPlace) * 1e-12;
		System.out.format("%15s: %fs\n", "Place time", placeTime);
		
		EfficientBoundingBoxNetCC effcc = new EfficientBoundingBoxNetCC(packedCircuit);
		double totalCost = effcc.calculateTotalCost();
		System.out.format("%15s: %f\n", "Total cost", totalCost);
		
		// TODO: why does this only work with a pre-packed circuit?
		TimingGraph timingGraph = new TimingGraph(prePackedCircuit);
		timingGraph.buildTimingGraph();
		double maxDelay = timingGraph.calculateMaximalDelay();
		System.out.format("%15s: %f\n", "Max delay", maxDelay);
		
		
		
		// Print out the place file
		try {
			packedCircuit.dumpPlacement(options.placeFile.toString());
		} catch (FileNotFoundException e) {
			error("Place file not found: " + options.placeFile);
		}
	}
	
	
	private static void error(String error) {
		System.err.println(error);
		System.exit(1);
	}

}