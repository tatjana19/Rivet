package org.e2k;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

public class PSK2400 extends PSK {
	
	private int state=0;
	private Rivet theApp;
	private long sampleCount=0;
	private long symbolCounter=0;
	
	private final int NZEROS_LOW=10;
	private final double GAIN_LOW=4.194017117e+05;
	private double xvLow[]=new double[NZEROS_LOW+1];
	private double yvLow[]=new double[NZEROS_LOW+1];
	
	private final int NZEROS=20;
	private final double GAIN=1.102413938e+14;
	private double xv[]=new double[NZEROS+1];
	private double yv[]=new double[NZEROS+1];
	
	double rectifiedCarrierBuffer[]=new double[2500];
	private int rectifiedCarrierBufferCounter=0;
	
	private int carrierFrequency=0;
	private boolean recoverCarrier=false;
	
	private double sineWaveLookup[]=new double[500];
	
	public PSK2400 (Rivet tapp)	{
		// Generate a 1800 Hz sine wave
		sineWaveLookup=sineGenerate (1800,24000,0.25,100);
		theApp=tapp;
	}
	
	public int getState() {
		return state;
	}
	
	public void setState(int state) {
		this.state=state;
		if (state==0) theApp.setStatusLabel("Setup");
		else if (state==1) theApp.setStatusLabel("Signal Hunt");
		else if (state==2) theApp.setStatusLabel("Msg Hunt");
	}
	
	// The main decode routine
	public void decode (CircularDataBuffer circBuf,WaveData waveData)	{
		// Initial startup
		if (state==0)	{
			// Check the sample rate
			if (waveData.getSampleRate()!=8000.0)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"WAV files containing\nPSK2400 recordings must have\nbeen recorded at a sample rate\nof 8 KHz.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			// Check this is a mono recording
			if (waveData.getChannels()!=1)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"Rivet can only process\nmono WAV files.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			// Check this is a 16 bit WAV file
			if (waveData.getSampleSizeInBits()!=16)	{
				state=-1;
				JOptionPane.showMessageDialog(null,"Rivet can only process\n16 bit WAV files.","Rivet", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			// sampleCount must start negative to account for the buffer gradually filling
			sampleCount=0-circBuf.retMax();
			symbolCounter=0;
			// Add a user warning that this mode is experimental doesn't yet decode
			//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			theApp.writeLine("Please note that this mode is experimental and doesn't work yet !",Color.RED,theApp.italicFont);
			//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			setState(1);
			return;
		}
		else {
			sampleCount++;
			if (sampleCount<0) return;
			// For every sample that arrives pad it with two zeros to raise the effective sample rate to 24000 Hz
			processSample(lowPassFilter((int)circBuf.getLast()));
			processSample(lowPassFilter(0.0));
			processSample(lowPassFilter(0.0));
		}
		
	}	
	
	// A 2500 Hz low pass filter
	private double lowPassFilter (double in)	{
		int a;
		for (a=0;a<NZEROS_LOW;a++)	{
			xvLow[a]=xvLow[a+1];
			yvLow[a]=yvLow[a+1];
		}
		xvLow[NZEROS_LOW]=in/GAIN_LOW;
		yvLow[10] =   (xvLow[0] + xvLow[10]) + 10 * (xvLow[1] + xvLow[9]) + 45 * (xvLow[2] + xvLow[8])
                + 120 * (xvLow[3] + xvLow[7]) + 210 * (xvLow[4] + xvLow[6]) + 252 * xvLow[5]
                + ( -0.0137757219 * yvLow[0]) + (  0.1953296201 * yvLow[1])
                + ( -1.2624940545 * yvLow[2]) + (  4.9039991908 * yvLow[3])
                + (-12.6953486280 * yvLow[4]) + ( 22.9248466450 * yvLow[5])
                + (-29.3030999830 * yvLow[6]) + ( 26.2473202970 * yvLow[7])
                + (-15.8199532880 * yvLow[8]) + (  5.8207343496 * yvLow[9]);	
		return yvLow[NZEROS_LOW];
	}
	
	// Process a 24000 Hz sample
	private void processSample (double in)	{
		
		// Recover the carrier every 10000 samples
		if ((sampleCount%10000==0)||(sampleCount==rectifiedCarrierBuffer.length)) recoverCarrier=true;
			
			
		if (recoverCarrier==true) carrierRecovery(in);
	}
	
	// Carrier Recovery
	private void carrierRecovery (double cin)	{
		
		// Put a rectified carrier through a 3500 Hz to 3700 Hz band pass filter
		rectifiedCarrierBuffer[rectifiedCarrierBufferCounter]=bandPassFilter(Math.abs(cin));
		
		//theApp.debugDump(Double.toString(rectifiedCarrierBuffer[rectifiedCarrierBufferCounter]));
		
		rectifiedCarrierBufferCounter++;
		if (rectifiedCarrierBufferCounter==rectifiedCarrierBuffer.length)	{
			double pav=returnPeaks();
			pav=pav*2;
			double freq=24000.0/pav;
			carrierFrequency=(int)freq;
			rectifiedCarrierBufferCounter=0;
			recoverCarrier=false;
			
			int a;
			for (a=0;a<=NZEROS;a++)	{
				xv[a]=0.0;
				yv[a]=0.0;
			}
			
			theApp.writeLine("Carrier Frequency "+Integer.toString(carrierFrequency)+" Hz",Color.BLUE,theApp.italicFont);
			
		}
	}
	
	private double bandPassFilter (double in)	{
		int a;
		for (a=0;a<NZEROS;a++)	{
			xv[a]=xv[a+1];
			yv[a]=yv[a+1];
		}
		xv[NZEROS]=in/GAIN;
		yv[20] =   (xv[0] + xv[20]) - 10 * (xv[2] + xv[18]) + 45 * (xv[4] + xv[16])
                - 120 * (xv[6] + xv[14]) + 210 * (xv[8] + xv[12]) - 252 * xv[10]
                + ( -0.7155110779 * yv[0]) + (  8.5550348230 * yv[1])
                + (-53.4258874590 * yv[2]) + (226.3529683200 * yv[3])
                + (-722.1572229300 * yv[4]) + (1831.7728680000 * yv[5])
                + (-3816.1157775000 * yv[6]) + (6665.6194565000 * yv[7])
                + (-9893.5741471000 * yv[8]) + (12584.0278360000 * yv[9])
                + (-13780.7192090000 * yv[10]) + (13012.4141250000 * yv[11])
                + (-10578.6357520000 * yv[12]) + (7369.7929107000 * yv[13])
                + (-4362.8946836000 * yv[14]) + (2165.5257120000 * yv[15])
                + (-882.7995994700 * yv[16]) + (286.1246537800 * yv[17])
                + (-69.8328092170 * yv[18]) + ( 11.5629324230 * yv[19]);

		return yv[NZEROS];
	}
	
	// Return an average peak position difference
	private double returnPeaks ()	{
		int nextPeak=0,lastPeak=0;
		double total=0.0,dCount=0.0;
		while (nextPeak!=-1)	{
			lastPeak=nextPeak;
			nextPeak=returnPeak(nextPeak);
			if (nextPeak!=-1)	{
				total=total+(nextPeak-lastPeak);
				dCount++;
			}
		}
		return (total/dCount);
	}
	
	
	// Return the next peak found
	private int returnPeak (int start)	{
		int a;
		for (a=start+1;a<(rectifiedCarrierBuffer.length-1);a++)	{
			if ((rectifiedCarrierBuffer[a]>rectifiedCarrierBuffer[a-1])&&(rectifiedCarrierBuffer[a]>rectifiedCarrierBuffer[a+1]))	{
				return a;
			}
		}
		return -1;
	}
	
}