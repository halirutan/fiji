package fiji.plugin.trackmate.gui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Duplicator;
import ij.process.ColorProcessor;
import ij.process.StackConverter;
import ij3d.Content;
import ij3d.ContentCreator;
import ij3d.Image3DUniverse;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.integer.UnsignedByteType;

import fiji.plugin.trackmate.Feature;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate_;
import fiji.plugin.trackmate.io.TmXmlWriter;
import fiji.plugin.trackmate.visualization.SpotDisplayer;
import fiji.plugin.trackmate.visualization.SpotDisplayer2D;
import fiji.plugin.trackmate.visualization.SpotDisplayer3D;
import fiji.plugin.trackmate.visualization.SpotDisplayer.TrackDisplayMode;


/**
* This code was edited or generated using CloudGarden's Jigloo
* SWT/Swing GUI Builder, which is free for non-commercial
* use. If Jigloo is being used commercially (ie, by a corporation,
* company or business for any purpose whatever) then you
* should purchase a license for each developer using Jigloo.
* Please visit www.cloudgarden.com for details.
* Use of Jigloo implies acceptance of these licensing terms.
* A COMMERCIAL LICENSE HAS NOT BEEN PURCHASED FOR
* THIS MACHINE, SO JIGLOO OR THIS CODE CANNOT BE USED
* LEGALLY FOR ANY CORPORATE OR COMMERCIAL PURPOSE.
*/
public class TrackMateFrame <T extends RealType<T>> extends javax.swing.JFrame {

	
	/**
	 * This is a helper class modified after a class by Albert Cardona
	 */
	private class DisplayUpdater extends Thread {
		long request = 0;

		// Constructor autostarts thread
		DisplayUpdater() {
			super("TrackMate displayer thread");
			setPriority(Thread.NORM_PRIORITY);
			start();
		}

		void doUpdate() {
			if (isInterrupted())
				return;
			synchronized (this) {
				request++;
				notify();
			}
		}

		void quit() {
			interrupt();
			synchronized (this) {
				notify();
			}
		}

		public void run() {
			while (!isInterrupted()) {
				try {
					final long r;
					synchronized (this) {
						r = request;
					}
					// Call displayer update from this thread
					if (r > 0)
						displayer.refresh(); // Is likely to generate NPE
					synchronized (this) {
						if (r == request) {
							request = 0; // reset
							wait();
						}
						// else loop through to update again
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	
	
	static final Font FONT = new Font("Arial", Font.PLAIN, 10);
	static final Font SMALL_FONT = FONT.deriveFont(8);
	static final Dimension TEXTFIELD_DIMENSION = new Dimension(40,18);
	

	
	private static enum GuiState {
		START,
		SEGMENTING,
		THRESHOLD_BLOBS,
		TRACKING;
	};
	
	private static final long serialVersionUID = 1L;

	private static final String START_DIALOG_KEY = "Start";
	private static final String THRESHOLD_GUI_KEY = "Threshold";
	private static final String LOG_PANEL_KEY = "Log";
	private static final String DISPLAYER_PANEL_KEY = "Displayer";
	
	private static final int DEFAULT_RESAMPLING_FACTOR = 3; // for the 3d viewer
	private static final int DEFAULT_THRESHOLD = 50; // for the 3d viewer
	private static final String DEFAULT_FILENAME = "TrackMateData.xml";

	private TrackMate_<? extends RealType<?>> trackmate;
	private GuiState state;
	private Logger logger;
	private SpotDisplayer displayer;
	private File file;
	private DisplayUpdater updater = new DisplayUpdater();
	
	private StartDialogPanel startDialogPanel;
	private ThresholdGuiPanel thresholdGuiPanel;
	private LogPanel logPanel;
	private DisplayerPanel displayerPanel;
	private CardLayout cardLayout;
	private JButton jButtonSave;
	private JButton jButtonLoad;
	private JButton jButtonPrevious;
	private JButton jButtonNext;
	private JPanel jPanelButtons;
	private JPanel jPanelMain;
	
	
	{
		//Set Look & Feel
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * CONSTRUCTORS
	 */
	
	public TrackMateFrame(TrackMate_<T> plugin) {
		if (null == plugin)
			plugin = new TrackMate_<T>();
		this.trackmate = plugin;
		initGUI();
		logger = logPanel.getLogger();
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				updater.quit();
			}
		});
	}
	
	public TrackMateFrame() {
		this(new TrackMate_<T>());
	}
	
	/**
	 * Called when the "Next >>" button is pressed.
	 */
	private void next() {
		switch(state) {
			case START:
				execSegmentationStep();
				state = GuiState.SEGMENTING;
				break;
				
			case SEGMENTING:
				execThresholdingStep();
				state = GuiState.THRESHOLD_BLOBS;
				break;
				
			case THRESHOLD_BLOBS:
				execTrackingStep();
				state = GuiState.TRACKING;
				break;
				
			case TRACKING:
				cardLayout.show(jPanelMain, DISPLAYER_PANEL_KEY);
				break;
				
		}
	}
	
	/**
	 * Called when the "<<" is pressed.
	 */
	private void previous() {		
	}
	
	/**
	 * Called when the "Load" button is pressed.
	 */
	private void load() {
	}
	
	/**
	 * Called when the "Save" button is pressed.
	 */
	private void save() {
		jButtonSave.setEnabled(false);
		if (null == file ) {
			File folder = new File(System.getProperty("user.dir")).getParentFile().getParentFile();
			file = new File(folder.getPath() + File.separator + DEFAULT_FILENAME);
		}
		JFileChooser fileChooser = new JFileChooser(file.getParent());
		fileChooser.setSelectedFile(file);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("XML files", "xml");
		fileChooser.setFileFilter(filter);

		int returnVal = fileChooser.showSaveDialog(this);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			file = fileChooser.getSelectedFile();
		} else {
			logger.log("Save data aborted.");
			return;  	    		
		}
		TmXmlWriter writer = new TmXmlWriter(trackmate);
		try {
			writer.writeToFile(file);
			logger.log("Data saved to: "+file.toString());
		} catch (FileNotFoundException e) {
			logger.error("File not found:\n"+e.getMessage());
		} catch (IOException e) {
			logger.error("Input/Output error:\n"+e.getMessage());
		} finally {
			jButtonSave.setEnabled(true);
		}
	}


	
	/**
	 * Switch to the log panel, and execute the segmentation step, which will be delegated to 
	 * the {@link TrackMate_} glue class in a new Thread.
	 */
	private void execSegmentationStep() {
		cardLayout.show(jPanelMain, LOG_PANEL_KEY);
		Settings settings = trackmate.getSettings();
		startDialogPanel.updateSettings(settings);
		logger.log("Starting segmentation...\n", Logger.BLUE_COLOR);
		new Thread("TrackMate segmentation thread") {					
			public void run() {
				long start = System.currentTimeMillis();
				try {
					trackmate.setLogger(logger);
					jButtonNext.setEnabled(false);
					trackmate.execSegmentation();
				} catch (Exception e) {
					logger.error("An error occured:\n"+e+'\n');
					e.printStackTrace(logger);
				} finally {
					jButtonNext.setEnabled(true);
					long end = System.currentTimeMillis();
					logger.log(String.format("Segmentation done in %.1f s.\n", (end-start)/1e3f), Logger.BLUE_COLOR);
				}
			}
		}.start();
	}
	
	/**
	 * Collect the segmentation result, render it in another thread, the switch to the thresholding panel. 
	 */
	private void execThresholdingStep() {
		// Launch renderer
		logger.log("Rendering results...\n",Logger.BLUE_COLOR);
		jButtonNext.setEnabled(false);
		// Thread for rendering
		new Thread("TrackMate rendering thread") {
			public void run() {
				Settings settings = trackmate.getSettings();
				// Render image data
				boolean is3D = settings.imp.getNSlices() > 1;
				if (is3D) { 
					final Image3DUniverse universe = new Image3DUniverse();
					universe.show();
					ImagePlus[] images = makeImageForViewer(settings);
					Content imageContent = ContentCreator.createContent(
							settings.imp.getTitle(), 
							images, 
							Content.VOLUME, 
							DEFAULT_RESAMPLING_FACTOR, 
							0,
							null, 
							DEFAULT_THRESHOLD, 
							new boolean[] {true, true, true});
					// Render spots
					displayer = new SpotDisplayer3D(universe, settings.segmenterSettings.expectedRadius); 							
					universe.addContentLater(imageContent);

				} else {
					final float[] calibration = new float[] {
							(float) settings.imp.getCalibration().pixelWidth, 
							(float) settings.imp.getCalibration().pixelHeight};
					displayer = new SpotDisplayer2D(settings.imp, settings.segmenterSettings.expectedRadius, calibration);
				}
				displayer.setSpots(trackmate.getSpots());
				displayer.render();
				cardLayout.show(jPanelMain, THRESHOLD_GUI_KEY);
				
				thresholdGuiPanel.setSpots(trackmate.getSpots().values());
				thresholdGuiPanel.addThresholdPanel(Feature.QUALITY);
				thresholdGuiPanel.addChangeListener(new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						// Threshold spots
						trackmate.setFeatureThresholds(thresholdGuiPanel.getFeatureThresholds());
						trackmate.execThresholding();
						// Send to displayer
						displayer.setSpotsToShow(trackmate.getSelectedSpots());
						updater.doUpdate();
					}
				});
				thresholdGuiPanel.stateChanged(null);
				logger.log("Rendering done.\n", Logger.BLUE_COLOR);
				jButtonNext.setEnabled(true);
			}
		}.start();
	}
	
	/**
	 * Switch to the log panel, and execute the tracking part in another thread.
	 */
	private void execTrackingStep() {
		cardLayout.show(jPanelMain, LOG_PANEL_KEY);
		jButtonNext.setEnabled(false);
		new Thread("TrackMate tracking thread") {					
			public void run() {
				// Threshold
				trackmate.setFeatureThresholds(thresholdGuiPanel.getFeatureThresholds());
				trackmate.execThresholding();
				displayer.setSpotsToShow(trackmate.getSelectedSpots());
				// Track
				trackmate.execTracking();
				displayer.setTrackGraph(trackmate.getTrackGraph());
				displayer.setDisplayTrackMode(TrackDisplayMode.ALL_WHOLE_TRACKS, 20);
				updater.doUpdate();
				// Re-enable the GUI
				jButtonNext.setEnabled(true);
			}
		}.start();
	}
	
	
	/**
	 * Ensure an 8-bit gray image is sent to the 3D viewer.
	 */
	private static final ImagePlus[] makeImageForViewer(final Settings settings) {
		final ImagePlus origImp = settings.imp;
		final ImagePlus imp;
		
		if (origImp.getType() == ImagePlus.GRAY8)
			imp = origImp;
		else {
			imp = new Duplicator().run(origImp);
			new StackConverter(imp).convertToGray8();
		}
		
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = settings.tend - settings.tstart + 1;
		ImagePlus[] ret = new ImagePlus[nFrames];
		int w = imp.getWidth(), h = imp.getHeight();

		ImageStack oldStack = imp.getStack();
		String oldTitle = imp.getTitle();
		
		for(int i = 0; i < nFrames; i++) {
			
			ImageStack newStack = new ImageStack(w, h);
			for(int j = 0; j < nSlices; j++) {
				int index = imp.getStackIndex(1, j+1, i+settings.tstart+1);
				Object pixels;
				if (nChannels > 1) {
					imp.setPositionWithoutUpdate(1, j+1, i+1);
					pixels = new ColorProcessor(imp.getImage()).getPixels();
				}
				else
					pixels = oldStack.getPixels(index);
				newStack.addSlice(oldStack.getSliceLabel(index), pixels);
			}
			ret[i] = new ImagePlus(oldTitle	+ " (frame " + i + ")", newStack);
			ret[i].setCalibration(imp.getCalibration().copy());
			
		}
		return ret;
	}

	/**
	 * Is called when the user change the color by feature combo box in the 
	 * {@link ThresholdGuiPanel}.
	 */
	private void recolorSpots() {
		displayer.setColorByFeature(thresholdGuiPanel.getColorByFeature());
		updater.doUpdate();
	}
	
	private void displayModeChanged() {
		displayer.setDisplayTrackMode(displayerPanel.getTrackDisplayMode(), displayerPanel.getTrackDisplayDepth());
		updater.doUpdate();
	}
	
	
	private void initGUI() {
		try {
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			this.setTitle(TrackMate_.PLUGIN_NAME_STR + " v"+TrackMate_.PLUGIN_NAME_VERSION);
			this.setResizable(false);
			{
				jPanelMain = new JPanel();
				cardLayout = new CardLayout();
				getContentPane().add(jPanelMain, BorderLayout.CENTER);
				jPanelMain.setLayout(cardLayout);
				jPanelMain.setPreferredSize(new java.awt.Dimension(300, 461));
			}
			{
				jPanelButtons = new JPanel();
				getContentPane().add(jPanelButtons, BorderLayout.SOUTH);
				jPanelButtons.setLayout(null);
				jPanelButtons.setSize(300, 30);
				jPanelButtons.setPreferredSize(new java.awt.Dimension(300, 30));
				{
					jButtonNext = new JButton();
					jPanelButtons.add(jButtonNext);
					jButtonNext.setText("Next >>");
					jButtonNext.setFont(FONT);
					jButtonNext.setBounds(221, 0, 70, 25);
					jButtonNext.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							next();
						}
					});
				}
				{
					jButtonPrevious = new JButton();
					jPanelButtons.add(jButtonPrevious);
					jButtonPrevious.setText("<<");
					jButtonPrevious.setFont(FONT);
					jButtonPrevious.setBounds(181, 0, 40, 25);
					jButtonPrevious.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							previous();
						}
					});
				}
				{
					jButtonLoad = new JButton();
					jPanelButtons.add(jButtonLoad);
					jButtonLoad.setText("Load");
					jButtonLoad.setFont(FONT);
					jButtonLoad.setBounds(7, 0, 50, 25);
					jButtonLoad.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							load();
						}
					});
				}
				{
					jButtonSave = new JButton();
					jPanelButtons.add(jButtonSave);
					jButtonSave.setText("Save");
					jButtonSave.setFont(FONT);
					jButtonSave.setBounds(61, 0, 50, 25);
					jButtonSave.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							save();
						}
					});
				}
			}
			pack();
			this.setSize(300, 520);
			{
				startDialogPanel = new StartDialogPanel();
				jPanelMain.add(startDialogPanel, START_DIALOG_KEY);
			}
			{
				logPanel = new LogPanel();
				jPanelMain.add(logPanel, LOG_PANEL_KEY);
			}
			{
				thresholdGuiPanel = new ThresholdGuiPanel();
				thresholdGuiPanel.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
							recolorSpots();
						} 
					});				
				jPanelMain.add(thresholdGuiPanel, THRESHOLD_GUI_KEY);
			}
			{
				displayerPanel = new DisplayerPanel();
				displayerPanel.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						displayModeChanged();
					}
				});
				jPanelMain.add(displayerPanel, DISPLAYER_PANEL_KEY);
			}
			cardLayout.show(jPanelMain, START_DIALOG_KEY);
			state = GuiState.START;
		} catch (Exception e) {
			e.printStackTrace();
		}
		repaint();
		validate();
	}


	/**
	 * Auto-generated main method to display this JFrame
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				TrackMateFrame<UnsignedByteType> inst = new TrackMateFrame<UnsignedByteType>();
				inst.setLocationRelativeTo(null);
				inst.setVisible(true);
			}
		});
	}
	
}
