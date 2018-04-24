package asl.sensor;

import asl.sensor.experiment.ExperimentEnum;
import asl.sensor.gui.ExperimentPanel;
import asl.sensor.gui.ExperimentPanelFactory;
import asl.sensor.gui.InputPanel;
import asl.sensor.gui.SwingWorkerSingleton;
import asl.sensor.input.DataStore;
import asl.sensor.utils.ReportingUtils;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jfree.chart.JFreeChart;

/**
 * Main window of the sensor test program and the program's launcher
 * Mainly used for handling the input (InputPanel)
 * and output (ExperimentPanel)
 * GUI frames and making sure they fit together and cooperate.
 *
 * @author akearns
 */
public class SensorSuite extends JPanel
    implements ActionListener, ChangeListener, PropertyChangeListener {

  /**
   *
   */
  private static final long serialVersionUID = 2866426897343097822L;

  /**
   * Loads the main window for the program on launch
   */
  private static void createAndShowGUI() {
    String version = SensorSuite.class.getPackage().getImplementationVersion();
    String title = "Sensor Tester [VERSION: " + version + "]";
    JFrame frame = new JFrame(title);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    frame.add(new SensorSuite());

    frame.pack();
    frame.setVisible(true);
  }

  /**
   * Starts the program -- instantiate the top-level GUI
   *
   * @param args (Any parameters fed in on command line are currently ignored)
   */
  public static void main(String[] args) {
    //Schedule a job for the event dispatch thread:
    //creating and showing this application's GUI.

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        Logger.getRootLogger().setLevel(Level.WARN);
        createAndShowGUI();
      }
    });

  }

  /**
   * Plots the data from an output panel and its associated input
   *
   * @param file Filename to write to
   * @param ep Experiment panel with data to be plotted
   * @param ip Input panel holding data associated with the experiment
   */
  private static void plotsToPDF(File file, ExperimentPanel ep, InputPanel ip) {

    // note that PDFBox is not thread safe, so don't try to thread these
    // calls to either the experiment or input panels

    int inPlotCount = ep.plotsToShow();
    String[] responses = ip.getResponseStrings(ep.getResponseIndices());

    // START OF UNIQUE CODE FOR PDF CREATION HERE
    PDDocument pdf = new PDDocument();
    ep.savePDFResults(pdf);

    if (inPlotCount > 0) {
      int inHeight = ip.getImageHeight(inPlotCount) * 2;
      int width = 1280; // TODO: set as global static variable somewhere?

      BufferedImage[] toFile =
          ip.getAsMultipleImages(width, inHeight, inPlotCount);

      ReportingUtils.imageListToPDFPages(pdf, toFile);
    }

    if (responses.length > 0) {
      ReportingUtils.textListToPDFPages(pdf, responses);
    }

    try {
      pdf.save(file);
    } catch (IOException e) {
      // if there's an error with formatting to PDF, try saving
      // the raw data instead
      e.printStackTrace();

      String text = ep.getAllTextData();
      JFreeChart[] charts = ep.getCharts();
      String saveDirectory = file.getParent();
      String folderName = saveDirectory + "/test_results/"
          + file.getName().replace(".pdf", "");

      saveExperimentData(folderName, text, charts);

    } finally {
      try {
        pdf.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }

  /**
   * Saves data collected from an experiment to. Files will be written into
   * a specified folder, with the format "Chart#.png" and all metadata in a
   * single file referred to as "outputData.txt".
   *
   * @param folderName Folder to write data into, presumably something like a
   * user's home directory, but inside a subdirectory of format
   * "test_results/[Experiment-specified filename]".
   * @param text Text output from an experiment
   * @param charts Array of charts produced from the experiment
   */
  private static void
  saveExperimentData(String folderName, String text, JFreeChart[] charts) {
    // start in the folder the pdf is saved, add data into a new
    // subfolder for calibration data

    File folder = new File(folderName);
    if (!folder.exists()) {
      System.out.println("Writing directory " + folderName);
      //noinspection ResultOfMethodCallIgnored
      folder.mkdirs();
    }

    String textName = folderName + "/outputData.txt";

    try {
      PrintWriter out = new PrintWriter(textName);
      out.println(text);
      out.close();
    } catch (FileNotFoundException e2) {
      System.out.println("Can't write the text");
      e2.printStackTrace();
    }

    for (int i = 0; i < charts.length; ++i) {
      JFreeChart chart = charts[i];
      String plotName = folderName + "/chart" + (i + 1) + ".png";
      BufferedImage chartImage =
          ReportingUtils.chartsToImage(1280, 960, chart);
      File plotPNG = new File(plotName);
      try {
        ImageIO.write(chartImage, "png", plotPNG);
      } catch (IOException e1) {
        e1.printStackTrace();
      }
    }
  }

  private final JFileChooser fc; // loads in files based on parameter
  private final InputPanel inputPlots;
  private final JTabbedPane tabbedPane; // holds set of experiment panels
  private final JButton generate;
  private final JButton savePDF; // run all calculations

  // used to store current directory locations
  private String saveDirectory = System.getProperty("user.home");

  /**
   * Creates the main window of the program when called
   * (Three main panels: the top panel for displaying the results
   * of sensor tests; the lower panel for displaying plots of raw data from
   * miniSEED files; the side panel for most file-IO operations
   */
  private SensorSuite() {

    super();

    // set up experiment panes in a tabbed pane
    tabbedPane = new JTabbedPane();

    for (ExperimentEnum exp : ExperimentEnum.values()) {
      JPanel tab = ExperimentPanelFactory.createPanel(exp);
      tabbedPane.addTab(exp.getName(), tab);
    }

    Dimension d = tabbedPane.getPreferredSize();
    d.setSize(d.getWidth() * 1.5, d.getHeight());
    tabbedPane.setPreferredSize(d);
    tabbedPane.addChangeListener(this);

    inputPlots = new InputPanel();
    inputPlots.addChangeListener(this);

    // experiments on left, input on the right; split to allow resizing
    JSplitPane mainSplit =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
    // boolean allows redrawing immediately on resize
    mainSplit.setLeftComponent(tabbedPane);
    mainSplit.setRightComponent(inputPlots);
    // set the left-pane to resize more when window is horizontally stretched
    mainSplit.setResizeWeight(.5);
    mainSplit.setOneTouchExpandable(true);

    // we want to make sure the split pane fills the window
    this.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.weighty = 1;
    c.gridwidth = 2;
    c.fill = GridBagConstraints.BOTH;

    this.add(mainSplit, c);

    c.fill = GridBagConstraints.VERTICAL;
    c.gridx = 0;
    c.gridy = 1;
    c.weightx = 1.0;
    c.weighty = 0.0;
    c.gridwidth = 1;

    // now add the buttons
    savePDF = new JButton("Generate PDF report from current test");
    savePDF.setEnabled(false);
    savePDF.addActionListener(this);
    c.anchor = GridBagConstraints.EAST;
    this.add(savePDF, c);
    c.gridx += 1;

    generate = new JButton("Generate test result");
    generate.setEnabled(false);
    generate.addActionListener(this);
    d = generate.getPreferredSize();
    d.setSize(d.getWidth() * 1.5, d.getHeight() * 1.5);
    generate.setMinimumSize(d);
    c.anchor = GridBagConstraints.WEST;
    this.add(generate, c);

    fc = new JFileChooser();

    ExperimentPanel ep = (ExperimentPanel) tabbedPane.getSelectedComponent();
    inputPlots.showDataNeeded(ep.panelsNeeded());
    inputPlots.setChannelTypes(ep.getChannelTypes());

  }

  /**
   * Handles actions when the buttons are clicked -- either the 'save PDF'
   * button, which compiles the input and output plots into a single PDF, or
   * the 'generate result' button.
   * Because generating results of an experiment can be slow, the operation
   * is set to run in a separate thread.
   */
  @Override
  public void actionPerformed(ActionEvent e) {

    if (e.getSource() == generate) {

      ExperimentPanel ep = (ExperimentPanel) tabbedPane.getSelectedComponent();
      ep.addPropertyChangeListener("Backend completed", this);
      savePDF.setEnabled(false);

      // update the input plots to show the active region being calculated
      inputPlots.showRegionForGeneration();
      // pass the inputted data to the panels that handle them
      DataStore ds = inputPlots.getData();
      SwingWorkerSingleton.setInstance(ep, ds);
      SwingWorker<Boolean, Void> wkr = SwingWorkerSingleton.getInstance();
      wkr.execute();

    } else if (e.getSource() == savePDF) {

      String ext = ".pdf";
      fc.setCurrentDirectory(new File(saveDirectory));
      fc.addChoosableFileFilter(
          new FileNameExtensionFilter("PDF file (.pdf)", ext));
      fc.setFileFilter(fc.getChoosableFileFilters()[1]);
      fc.setDialogTitle("Save PDF report...");
      ExperimentPanel ep = (ExperimentPanel) tabbedPane.getSelectedComponent();
      String defaultName = ep.getPDFFilename();

      fc.setSelectedFile(new File(defaultName));
      int returnVal = fc.showSaveDialog(savePDF);

      if (returnVal == JFileChooser.APPROVE_OPTION) {

        File selFile = fc.getSelectedFile();
        saveDirectory = selFile.getParent();
        if (!selFile.getName().toLowerCase().endsWith(ext)) {
          selFile = new File(selFile.getName() + ext);
        }

        plotsToPDF(selFile, ep, inputPlots);
      }
    }

  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    // handle the completion of the SwingWorker thread of the backend
    if (evt.getPropertyName().equals("Backend completed")) {
      ExperimentPanel source = (ExperimentPanel) evt.getSource();
      source.removePropertyChangeListener(this);

      if (evt.getNewValue().equals(false)) {
        return;
      }

      if (tabbedPane.getSelectedComponent() == source) {
        savePDF.setEnabled(source.hasRun());
      }
    }
  }

  /**
   * Checks when input panel gets new data or active experiment changes
   * to determine whether or not the experiment can be run yet
   */
  @Override
  public void stateChanged(ChangeEvent e) {

    if (e.getSource() == inputPlots) {
      ExperimentPanel ep = (ExperimentPanel) tabbedPane.getSelectedComponent();
      DataStore ds = inputPlots.getData();
      boolean canGenerate = ep.hasEnoughData(ds);
      generate.setEnabled(canGenerate);
    } else if (e.getSource() == tabbedPane) {
      ExperimentPanel ep = (ExperimentPanel) tabbedPane.getSelectedComponent();

      inputPlots.setChannelTypes(ep.getChannelTypes());

      inputPlots.showDataNeeded(ep.panelsNeeded());
      DataStore ds = inputPlots.getData();
      boolean canGenerate = ep.hasEnoughData(ds);
      boolean isSet = ep.hasRun();
      generate.setEnabled(canGenerate);
      savePDF.setEnabled(canGenerate && isSet);
    }

  }

}
