/*
 * Copyright 2006 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.visualization.oldtwod;

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.io.RawDataFile;
import net.sf.mzmine.io.util.RawDataAcceptor;
import net.sf.mzmine.io.util.RawDataRetrievalTask;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.taskcontrol.Task.TaskPriority;
import net.sf.mzmine.util.ScanUtils;
import net.sf.mzmine.util.ScanUtils.BinningType;

import org.jfree.chart.event.PlotChangeEvent;
import org.jfree.chart.event.PlotChangeListener;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.AbstractXYZDataset;

/**
 * 
 */
public class OldTwoDDataSet implements RawDataAcceptor {
	
	public static final int NO_DATA = 0;
	public static final int LOADING_DATA = 1;
	public static final int DATA_READY = 2;
	

    private Logger logger = Logger.getLogger(this.getClass().getName());
    
    private RawDataFile rawDataFile;
    
    private OldTwoDVisualizerWindow visualizer;

    private float intensityMatrix[][];
    private int mzResolution, rtResolution;

    private boolean interpolate;
       
    // bounds for rendered data range
    private double rtMin, rtMax, rtStep;
    private double mzMin, mzMax, mzStep;
    private int msLevel;

    // max intensity in current image
    private float maxIntensity;
    
    private Task currentTask;
    
    

    public OldTwoDDataSet(RawDataFile rawDataFile, OldTwoDVisualizerWindow visualizer) {

        this.rawDataFile = rawDataFile;
        this.visualizer = visualizer;

    }
    
    public void resampleIntensityMatrix(boolean interpolate) {
    	resampleIntensityMatrix(this.msLevel, this.rtMin, this.rtMax, this.mzMin,
				this.mzMax, this.rtResolution, this.mzResolution, interpolate);
    }
    
    public void resampleIntensityMatrix(int msLevel, double desiredRTMin, double desiredRTMax, double mzMin,
            							double mzMax, int desiredRTResolution, int mzResolution, boolean interpolate) {
    	
    	this.msLevel = msLevel;
        this.mzMin = mzMin;
        this.mzMax = mzMax;
        this.mzResolution = mzResolution;
        this.interpolate = interpolate;

        // Pickup scan number within given rt range
        int[] scanNumbersForRetrieval = rawDataFile.getScanNumbers(msLevel, (float)desiredRTMin, (float)desiredRTMax);
        
        // Adjust rt resolution if there are less scans that desired resolution
        rtResolution = desiredRTResolution;
        if (scanNumbersForRetrieval.length<rtResolution) rtResolution = scanNumbersForRetrieval.length; 
        
        // Find minimum and maximum RT of scans within range 
        rtMin = Float.MAX_VALUE;
        rtMax = Float.MIN_VALUE;
        for (int scanNumber : scanNumbersForRetrieval) {
        	float rt = rawDataFile.getScan(scanNumber).getRetentionTime();
        	if (rtMin>rt) rtMin=rt;
        	if (rtMax<rt) rtMax=rt;
        }
        
        // Initialize intensity matrix
        intensityMatrix = new float[rtResolution][mzResolution];

        // Bin sizes
        this.rtStep = (rtMax - rtMin) / (rtResolution-1);
        this.mzStep = (mzMax - mzMin) / mzResolution;

        
        currentTask = new RawDataRetrievalTask(rawDataFile, scanNumbersForRetrieval,
                "Updating 2D visualizer of " + rawDataFile, this);

        MZmineCore.getTaskController().addTask(currentTask, TaskPriority.HIGH, visualizer);
    	
    }

    /**
     * @see net.sf.mzmine.io.RawDataAcceptor#addScan(net.sf.mzmine.data.Scan)
     */
    public synchronized void addScan(Scan scan, int index, int total) {

        int bitmapSizeX, bitmapSizeY;

        logger.finest("Adding scan " + scan);
        
        bitmapSizeX = intensityMatrix.length;
        bitmapSizeY = intensityMatrix[0].length;

        System.out.print("Adding scan with scan.getRetentionTime()=" + scan.getRetentionTime());
        System.out.println(", rtMin=" + rtMin + ", rtMax=" + rtMax);
        
        if ((scan.getRetentionTime() < rtMin)
                || (scan.getRetentionTime() > rtMax))
            return;
        
        System.out.println("scan.getRetentionTime() - rtMin = " + ((scan.getRetentionTime() - rtMin)/rtStep));

        int xIndex = (int) Math.floor((scan.getRetentionTime() - rtMin)
                / rtStep);

        System.out.println("xIndex=" + xIndex);
              
        float mzValues[] = scan.getMZValues();
        float intensityValues[] = scan.getIntensityValues();

        float binnedIntensities[] = ScanUtils.binValues(mzValues, intensityValues, (float)mzMin, (float)mzMax, bitmapSizeY, interpolate, BinningType.SUM);
        
        for (int i = 0; i < bitmapSizeY; i++) {

            intensityMatrix[xIndex][bitmapSizeY-i-1] += binnedIntensities[i];

            if (intensityMatrix[xIndex][bitmapSizeY-i-1] > maxIntensity)
                maxIntensity = intensityMatrix[xIndex][bitmapSizeY-i-1];
            
        }

        if (index>=(total-1))
        	visualizer.datasetUpdateReady();
        else
        	visualizer.datasetUpdating();

        	

    }


    public float[][] getIntensityMatrix() {
        return intensityMatrix;
    }
    
    public boolean isInterpolated() {
    	return interpolate;
    }

    public int getMSLevel() {
    	return msLevel;
    }
    
    public float getMinRT() {
    	return (float)rtMin;
    }
    
    public float getMaxRT() {
    	return (float)rtMax;
    }
    
    public float getMinMZ() {
    	return (float)mzMin;
    }
    
    public float getMaxMZ() {
    	return (float)mzMax;
    }
    
    public float getMaxIntensity() {
    	return maxIntensity;
    }
       
    public int getStatus() {
    	if (currentTask == null) return NO_DATA;
    	if ((currentTask.getStatus() == Task.TaskStatus.FINISHED) ||
    		(currentTask.getStatus() == Task.TaskStatus.CANCELED) ||
    		(currentTask.getStatus() == Task.TaskStatus.ERROR)) return DATA_READY;
    	
    	if ((currentTask.getStatus() == Task.TaskStatus.PROCESSING) ||
    		(currentTask.getStatus() == Task.TaskStatus.WAITING)) return LOADING_DATA;
    	
    	return LOADING_DATA;
    }
    
}