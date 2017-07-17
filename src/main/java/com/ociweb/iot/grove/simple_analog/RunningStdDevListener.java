package com.ociweb.iot.grove.simple_analog;

public abstract class RunningStdDevListener implements AnalogListenerable{

	private final int[] data;
	private int index = 0;
	private final int windowSize;
	private int lastMeanX_Squared;
	private int lastMeanX;
	public RunningStdDevListener(int windowSize){
		this.windowSize = windowSize;
		this.data = new int[windowSize];
	}
	public int addSample(double sample){
		if (index > windowSize){
			
		}
		else {
			
		}
		return 0;
	}
	
	abstract void runningStandardDeviation(double stdDev);
}
