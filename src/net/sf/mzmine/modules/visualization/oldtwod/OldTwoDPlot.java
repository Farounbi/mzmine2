package net.sf.mzmine.modules.visualization.oldtwod;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Vector;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import net.sf.mzmine.data.Peak;
import net.sf.mzmine.data.PeakListRow;

public class OldTwoDPlot extends JPanel implements ActionListener, MouseListener, MouseMotionListener {

	private OldTwoDDataSet dataset;
	
	private JPopupMenu popupMenu;

	private JMenuItem zoomToSelectionMenuItem;
	private JMenuItem zoomOutMenuItem;
	private JMenuItem zoomOutLittleMenuItem;

	private JMenuItem selectNearestPeakMenuItem;

	private OldTwoDVisualizerWindow visualizerWindow;
	
	private int mouseCursorPositionX;
	private int mouseCursorPositionY;
	
	private int selectionFirstClickX = -1;
	private int selectionFirstClickY = -1;
	private int selectionLastClickX = -1;
	private int selectionLastClickY = -1;
	
	private int mouseAreaStartX;
	private int mouseAreaStartY;
	private int mouseAreaStopX;
	private int mouseAreaStopY;
	
	private BufferedImage bitmapImage;

	
	public OldTwoDPlot(OldTwoDVisualizerWindow visualizerWindow, OldTwoDDataSet dataset) {

		this.visualizerWindow = visualizerWindow;
		this.dataset = dataset;

	    // Create popup-menu
	    popupMenu = new JPopupMenu();
	    zoomToSelectionMenuItem = new JMenuItem("Zoom to selection");
	    zoomToSelectionMenuItem.addActionListener(this);
	    zoomToSelectionMenuItem.setEnabled(false);
	    popupMenu.add(zoomToSelectionMenuItem);

	    zoomOutMenuItem = new JMenuItem("Zoom out full");
	    zoomOutMenuItem.addActionListener(this);
	    popupMenu.add(zoomOutMenuItem);

		zoomOutLittleMenuItem = new JMenuItem("Zoom out little");
		zoomOutLittleMenuItem.addActionListener(this);
	    popupMenu.add(zoomOutLittleMenuItem);

	    popupMenu.addSeparator();

	    selectNearestPeakMenuItem = new JMenuItem("Select nearest peak");
	    selectNearestPeakMenuItem.addActionListener(this);
	    popupMenu.add(selectNearestPeakMenuItem);

	    popupMenu.addSeparator();

	    addMouseListener(this);
	    addMouseMotionListener(this);

	}

	public void datasetUpdating() {
		// TODO
	}
	
	
	public void datasetUpdateReady() {
		
		// Update bitmap			
		bitmapImage = constructBitmap();
		
	}
	

	
	private BufferedImage constructBitmap() {

		
		float[][] bitmapMatrix = dataset.getIntensityMatrix();
		int bitmapXSize = bitmapMatrix.length;
		int bitmapYSize = bitmapMatrix[0].length;;
		
		
		
		
		// Get suitable color space and maximum intensity value
		ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
		float dataImgMax = dataset.getMaxIntensity();
	
		// How many 8-bit components are used for representing shade of color in this color space?
		int nComp = cs.getNumComponents();
		int[] nBits = new int[nComp];
		for (int nb=0; nb<nComp; nb++) { nBits[nb]=8; }

		// Create sample model for storing the image
		ColorModel colorModel = new ComponentColorModel(cs, nBits, false, true, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		SampleModel sampleModel = colorModel.createCompatibleSampleModel(bitmapXSize, bitmapYSize);
		DataBuffer dataBuffer = sampleModel.createDataBuffer();

		// Loop through the bitmap and translate each raw intensity to suitable representation in current color space
		byte count=0;
		byte[] b = new byte[nComp];
		double bb;
		double fac;
		for (int xpos=0; xpos<bitmapXSize; xpos++) {
			for (int ypos=0; ypos<bitmapYSize; ypos++) {

				
				
				bb = 0;
				bb = (double)((0.20*dataImgMax-bitmapMatrix[xpos][ypos])/(0.20*dataImgMax));
				if (bb<0) { bb = 0; }
				b[0] = (byte)(255*bb);

				sampleModel.setDataElements(xpos,ypos,b,dataBuffer);
			}

		}

		WritableRaster wr = Raster.createWritableRaster(sampleModel,dataBuffer, new Point(0,0));
		BufferedImage bi = new BufferedImage(colorModel, wr,true,null);

		return bi;

	}

	public void paint(Graphics g) {

		
		if (dataset==null) {
			return; 
		}
		

		
		double w = getWidth();
		double h = getHeight();
		
		if (bitmapImage != null) {

			Graphics2D g2d = (Graphics2D)g;
			g2d.drawRenderedImage(bitmapImage, AffineTransform.getScaleInstance(w/bitmapImage.getWidth(),h/bitmapImage.getHeight()));
	
			// Draw peaks
			/*
			g.setColor(Color.green);
			if (peakListRows!=null) {

				
				float[] mzs;
				float[] rts;
				for (PeakListRow row : peakListRows) {

					Peak p = row.getPeaks()[0];

					if (p==null) continue;
					
					int[] scanNumbers = p.getScanNumbers();
					mzs = new float[scanNumbers.length];
					rts = new float[scanNumbers.length];
					
					int ind=0;
					for (int scanNumber : scanNumbers) {					
						mzs[ind] = p.getRawDatapoints(scanNumber)[0][0];
						rts[ind] = dataset.getRetentionTime(scanNumber);
						ind++;
					}

					int prevx = (int)java.lang.Math.round((double)w * ((double)(rts[0]-imageScaleMinRT) / (double)(imageScaleMaxRT-imageScaleMinRT+1)));
					int prevy = (int)java.lang.Math.round((double)h * ((double)(mzs[0]-imageScaleMinMZ) / (double)(imageScaleMaxMZ-imageScaleMinMZ)));
					
					for (ind=0; ind<=mzs.length; ind++) {
						int currx = (int)java.lang.Math.round((double)w * ((double)(rts[ind]-imageScaleMinRT) / (double)(imageScaleMaxRT-imageScaleMinRT+1)));
						int curry = (int)java.lang.Math.round((double)h * ((double)(mzs[ind]-imageScaleMinMZ) / (double)(imageScaleMaxMZ-imageScaleMinMZ)));
						
						g.drawLine(prevx,(int)(h-prevy),currx,(int)(h-prevy));
						g.drawLine(currx,(int)(h-prevy),currx,(int)(h-curry));
						
						prevx = currx;
						prevy = curry;

					}
					int nextx = (int)java.lang.Math.round((double)w * ((double)(rts[rts.length-1]-imageScaleMinRT) / (double)(imageScaleMaxRT-imageScaleMinRT+1)));
					g.drawLine(prevx,(int)(h-prevy),nextx,(int)(h-prevy));
				}

			}
			*/
			
			// Draw cursor position x-coordinate => scan
			g.setColor(Color.red);
			g.drawLine(mouseCursorPositionX, 0, mouseCursorPositionX, (int)h);
			
			// Draw cursor position y-coordinate => m/z
			g.setColor(Color.red);
			g.drawLine(0, mouseCursorPositionY, (int)w, mouseCursorPositionY);
			
			// Draw selection
			g.setColor(Color.blue);
			g.drawRect(mouseAreaStartX, mouseAreaStartY, mouseAreaStopX-mouseAreaStartX, mouseAreaStopY-mouseAreaStartY);
			
		}
	}


	private int convertPlotXCoordinateToIntensityMatrixXIndex(int xCoordinate) {
		return (int)java.lang.Math.floor( ((float)xCoordinate / (float)getWidth()) * (float)dataset.getIntensityMatrix().length);
	}
	
	private float convertPlotYCoordinateToMZ(int yCoordinate) {
		float dataMZRangeWidth = dataset.getMaxMZ()-dataset.getMinMZ();
		return ( ( (float)getHeight()-(float)yCoordinate ) / (float)getHeight() ) * dataMZRangeWidth + dataset.getMinMZ(); 
	}
	
	private float convertPlotXCoordinateToRT(int xCoordinate) {
		float dataRTRangeWidth = dataset.getMaxRT()-dataset.getMinRT();
		return ( (float)xCoordinate / (float)getWidth() ) * dataRTRangeWidth + dataset.getMinRT(); 
	}	
	
	private int quantizePlotXCoordinateToIntensityMatrixXIndex(int xCoordinate) {
		int xSteps = dataset.getIntensityMatrix().length;
		double xStepInPixels = (double)getWidth() / (double)xSteps;
		
		int nearestStep = 0;
		for (int xStep=0; xStep<xSteps; xStep++)
			if (
					java.lang.Math.abs(xStep*xStepInPixels-xCoordinate) <
					java.lang.Math.abs(nearestStep*xStepInPixels-xCoordinate)
					) nearestStep = xStep;
					
		return (int)java.lang.Math.round(nearestStep * xStepInPixels);
		
	}
	
	public void actionPerformed(java.awt.event.ActionEvent e) {

		Object src = e.getSource();

		if (src == zoomToSelectionMenuItem) {
			
			
			
			float rangeMaxMZ = convertPlotYCoordinateToMZ(mouseAreaStartY);
			float rangeMinMZ = convertPlotYCoordinateToMZ(mouseAreaStopY);
			
			float rangeMinRT = convertPlotXCoordinateToRT(mouseAreaStartX);
			float rangeMaxRT = convertPlotXCoordinateToRT(mouseAreaStopX);

			System.out.println("zoomToSelectionMenuItem");
			System.out.println("mouseAreaStartY=" + mouseAreaStartY);
			System.out.println("mouseAreaStopY=" + mouseAreaStopY);
			System.out.println("mouseAreaStartX=" + mouseAreaStartX);
			System.out.println("mouseAreaStopX=" + mouseAreaStopX);			

			System.out.println("rangeMinMZ=" + rangeMinMZ);
			System.out.println("rangeMaxMZ=" + rangeMaxMZ);
			System.out.println("rangeMinRT=" + rangeMinRT);
			System.out.println("rangeMaxRT=" + rangeMaxRT);			
			
			visualizerWindow.setZoomRange(1, rangeMinRT, rangeMaxRT, rangeMinMZ, rangeMaxMZ);

		}

		if (src == zoomOutMenuItem) {
			visualizerWindow.setFullZoom(1);
		}

		if (src == zoomOutLittleMenuItem) {
			// TODO
		}

		if (src == selectNearestPeakMenuItem) {
			// TODO 
		}

	}

	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}

	public void mouseReleased(MouseEvent e) {

	    if (e.getButton()!=MouseEvent.BUTTON1) {
			popupMenu.show(e.getComponent(), e.getX(), e.getY());
	    } else {
			selectionFirstClickX = -1;
			selectionFirstClickY = -1;
			selectionLastClickX = -1;
			selectionLastClickY = -1;
	    }

	}

	private int lastPressedButtonWas;
	public void mousePressed(MouseEvent e) {

		lastPressedButtonWas = e.getButton();

	    if (e.getButton()==MouseEvent.BUTTON1) {
	    	
	    	int w = getWidth();
	    	int h = getHeight();
	    	
	    	mouseCursorPositionX = quantizePlotXCoordinateToIntensityMatrixXIndex(e.getX());
	    	mouseCursorPositionY = e.getY();
	    	
	    	zoomToSelectionMenuItem.setEnabled(false);
	    	
	    	mouseAreaStartX = -1;
	    	mouseAreaStartY = -1;
	    	mouseAreaStopX = -1;
	    	mouseAreaStopY = -1;
	    	
			repaint();
			
	    }

	}

	public void mouseDragged(MouseEvent e) {

		if (lastPressedButtonWas!=MouseEvent.BUTTON1) { return; }

		if (selectionFirstClickX == -1) {
			
			selectionFirstClickX = quantizePlotXCoordinateToIntensityMatrixXIndex(e.getX());
			selectionFirstClickY = e.getY();
			mouseCursorPositionX = selectionFirstClickX;
			mouseCursorPositionY = selectionFirstClickY;
			
		} else {
			
			selectionLastClickX = quantizePlotXCoordinateToIntensityMatrixXIndex(e.getX());
			selectionLastClickY = e.getY();

			if (selectionLastClickX>selectionFirstClickX) {
				mouseAreaStartX = selectionFirstClickX;
				mouseAreaStopX = selectionLastClickX;
			} else {
				mouseAreaStartX = selectionLastClickX;
				mouseAreaStopX = selectionFirstClickX;
			}

			if (selectionLastClickY>selectionFirstClickY) {
				mouseAreaStartY = selectionFirstClickY;
				mouseAreaStopY = selectionLastClickY;
			} else {
				mouseAreaStartY = selectionLastClickY;
				mouseAreaStopY = selectionFirstClickY;
			}

			zoomToSelectionMenuItem.setEnabled(true);

			repaint();
			
		}
				
	}

	public void mouseMoved(MouseEvent e) {}

}

