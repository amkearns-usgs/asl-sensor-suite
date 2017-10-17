package asl.sensor.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexFormat;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockContainer;
import org.jfree.chart.block.FlowArrangement;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.CompositeTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.title.Title;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.VerticalAlignment;

import asl.sensor.experiment.ExperimentEnum;
import asl.sensor.experiment.RandomizedExperiment;
import asl.sensor.experiment.ResponseExperiment;
import asl.sensor.input.DataStore;
import asl.sensor.utils.NumericUtils;

/**
 * Panel to display results from a randomized calibration experiment.
 * This includes plots of response magnitude and argument, selectable from a
 * drop-down combo box on the panel.
 * The inclusion of two selectable plots means that overrides are necessary
 * to produce output of both plots when creating a report of the results,
 * and that the typical means of assigning the visible chart cannot be used.
 * @author akearns
 *
 */
public class RandomizedPanel extends ExperimentPanel {

  public static final String MAGNITUDE = ResponseExperiment.MAGNITUDE;
  public static final String ARGUMENT = ResponseExperiment.ARGUMENT;
  private static final Color[] COLOR_LIST = 
      new Color[]{Color.RED, Color.BLUE, Color.GREEN};
  private static final int TITLE_IDX = 0; // TODO: replace w/ title pointers
  
  /**
   * 
   */
  private static final long serialVersionUID = -1791709117080520178L;
  /**
   * Utility function for formatting additional report pages from the
   * underlying experiment backend; can be called without constructing a
   * panel. Called by a non-static function in order to implement overrides, as
   * static functions do not get overridden by inheritance.
   * @param rnd RandomizedExperiment to pull data from (i.e., from a panel 
   * instance)
   * @return List of strings, each one representing a new page's worth of data
   */
  public static String[] getAdditionalReportPages(RandomizedExperiment rnd) {
    
    // TODO: refactor this now that period values are included in
    // the inset portion of the report text instead of merely in the extra data
    StringBuilder sb = new StringBuilder();
    
    StringBuilder csvPoles = new StringBuilder();
    StringBuilder csvZeros = new StringBuilder();
    StringBuilder csvTitle = new StringBuilder();
    DecimalFormat csvFormat = new DecimalFormat("+#.####;-#.####");
    NumericUtils.setInfinityPrintable(csvFormat);
    
    final int COL_WIDTH = 9;
    String[] columns = new String[]{"Init", "Fit", "Diff", "Mean", "PctDiff"};
    for (String column : columns) {
      StringBuilder paddedColumn = new StringBuilder(column);
      while ( paddedColumn.length() < COL_WIDTH ) {
        paddedColumn.append(" "); // add a space
      }
      csvTitle.append( paddedColumn );
    }
    
    List<Complex> fitP = rnd.getFitPoles();
    List<Complex> initP = rnd.getInitialPoles();
    List<Complex> fitZ = rnd.getFitZeros();
    List<Complex> initZ = rnd.getInitialZeros();
    
    boolean solverNotRun = rnd.getSolverState();
    
    if (solverNotRun) {
      return new String[]{};
    }
    
    // get statistics for differences between initial and solved parameters
    csvPoles = new StringBuilder("POLE VARIABLES, AS CSV:\n");
    csvPoles.append(csvTitle);
    csvPoles.append("\n");

    for (int i = 0; i < fitP.size(); ++i) {
      double realPartFit = fitP.get(i).getReal();
      double imagPartFit = fitP.get(i).getImaginary();

      double realPartInit = initP.get(i).getReal();
      double imagPartInit = initP.get(i).getImaginary();

      // make sure sign of the imaginary parts are the same
      if ( Math.signum(imagPartFit) != Math.signum(imagPartInit) ) {
        imagPartFit *= -1;
      }

      double realDiff = realPartInit - realPartFit;
      double imagDiff = imagPartInit - imagPartFit;

      double realAvg = (realPartInit + realPartFit) / 2.;
      double imagAvg = (imagPartInit + imagPartFit) / 2.;

      double realPct = realDiff * 100 / realPartFit;
      if ( realPartFit == 0. ) {
        realPct = 0.;
      }
      double imagPct = imagDiff * 100 / imagPartFit;
      if ( imagPartFit == 0. ) {
        imagPct = 0.;
      }

      // INIT, FIT, DIFF, AVG, PCT

      double[] realRow = new double[]
          {realPartInit, realPartFit, realDiff, realAvg, realPct};

      double[] imagRow = new double[]
          {imagPartInit, imagPartFit, imagDiff, imagAvg, imagPct};

      for (double colNumber : realRow) {
        String column = csvFormat.format(colNumber);
        StringBuilder paddedColumn = new StringBuilder(column);
        while ( paddedColumn.length() < COL_WIDTH ) {
          paddedColumn.append(" "); // add a space
        }
        csvPoles.append( paddedColumn );
      }
      csvPoles.append("\n");

      for (double colNumber : imagRow) {
        String column = csvFormat.format(colNumber);
        StringBuilder paddedColumn = new StringBuilder(column);
        while ( paddedColumn.length() < COL_WIDTH ) {
          paddedColumn.append(" "); // add a space
        }
        csvPoles.append( paddedColumn );
      }
      csvPoles.append("\n");

      if (imagPartFit != 0.) {
        ++i; // skip complex conjugate
      }
    }

    // get statistics for differences between initial and solved parameters
    if ( fitZ.size() > 0 ) {
      csvZeros = new StringBuilder("ZERO VARIABLES, AS CSV:\n");
      csvZeros.append(csvTitle);
      csvZeros.append("\n");
    }


    for (int i = 0; i < fitZ.size(); ++i) {
      double realPartFit = fitZ.get(i).getReal();
      double imagPartFit = fitZ.get(i).getImaginary();

      double realPartInit = initZ.get(i).getReal();
      double imagPartInit = initZ.get(i).getImaginary();

      // make sure sign of the imaginary parts are the same
      if ( Math.signum(imagPartFit) != Math.signum(imagPartInit) ) {
        imagPartFit *= -1;
      }

      double realDiff = realPartInit - realPartFit;
      double imagDiff = imagPartInit - imagPartFit;

      double realAvg = (realPartInit + realPartFit) / 2.;
      double imagAvg = (imagPartInit + imagPartFit) / 2.;

      double realPct = realDiff * 100 / realPartFit;
      if ( realPartFit == 0. ) {
        realPct = 0.;
      }
      double imagPct = imagDiff * 100 / imagPartFit;
      if ( imagPartFit == 0. ) {
        imagPct = 0.;
      }

      double[] realRow = new double[]
          {realPartInit, realPartFit, realDiff, realAvg, realPct};

      double[] imagRow = new double[]
          {imagPartInit, imagPartFit, imagDiff, imagAvg, imagPct};

      for (double colNumber : realRow) {
        String column = csvFormat.format(colNumber);
        StringBuilder paddedColumn = new StringBuilder(column);
        while ( paddedColumn.length() < COL_WIDTH ) {
          paddedColumn.append(" "); // add a space
        }
        csvZeros.append( paddedColumn );
      }
      csvZeros.append("\n");

      for (double colNumber : imagRow) {
        String column = csvFormat.format(colNumber);
        StringBuilder paddedColumn = new StringBuilder(column);
        while ( paddedColumn.length() < COL_WIDTH ) {
          paddedColumn.append(" "); // add a space
        }
        csvZeros.append( paddedColumn );
      }
      csvZeros.append("\n");

      if (imagPartFit != 0.) {
        ++i; // skip complex conjugate
      }
    }

    sb.append(csvPoles);
    sb.append(csvZeros);

    String[] out = new String[]{sb.toString()}; // just a single new page
    return out;
  }
  
  public static String complexListToString(List<Complex> stringMe) {
    final int MAX_LINE = 2; // maximum number of entries per line
    
    DecimalFormat df = new DecimalFormat("#.#####");
    NumericUtils.setInfinityPrintable(df);
    ComplexFormat cf = new ComplexFormat(df);
    StringBuilder sb = new StringBuilder();
    int numInLine = 0;
    
    for (int i = 0; i < stringMe.size(); ++i) {
      
      Complex c = stringMe.get(i);
      double initPrd = NumericUtils.TAU / c.abs();
      
      sb.append( cf.format(c) );
      sb.append(" (");
      sb.append( df.format(initPrd) );
      sb.append(")");
      ++numInLine;
      // want to fit two to a line for paired values
      if (numInLine >= MAX_LINE) {
        sb.append("\n");
        numInLine = 0;
      } else {
        sb.append(", ");
      }
    }
    
    return sb.toString();
  }
  
  
  /**
   * Static helper method for getting the formatted inset string directly
   * from a RandomizedExperiment
   * @param rnd RandomizedExperiment with data to be extracted
   * @return String format representation of data from the experiment
   */
  public static String[] getInsetString(RandomizedExperiment rnd) {
    
    List<Complex> fitP = rnd.getFitPoles();
    List<Complex> initP = rnd.getInitialPoles();
    List<Complex> fitZ = rnd.getFitZeros();
    List<Complex> initZ = rnd.getInitialZeros();
    
    if (fitP == null) {
      return new String[]{""};
    }
    
    boolean solverNotRun = rnd.getSolverState();
    
    double initResid = rnd.getInitResidual();
    double fitResid = rnd.getFitResidual();
    
    StringBuilder sbInit = new StringBuilder();
    StringBuilder sbFit = new StringBuilder();
    // add poles, initial then fit (single loop, append the two builders)
    sbInit.append("Initial poles: \n");
    sbFit.append("Fit poles: \n");
    
    sbInit.append( complexListToString(initP) );
    sbFit.append( complexListToString(fitP) );
     
    sbInit.append("\n");
    sbFit.append("\n");
    
    StringBuilder sbInitZ = new StringBuilder();
    StringBuilder sbFitZ = new StringBuilder();
    
    if ( fitZ.size() > 0 ) {
      sbInitZ.append("Initial zeros: \n");
      sbFitZ.append("Fit zeros: \n");
    }
    
    sbInitZ.append( complexListToString(initZ) );
    sbFitZ.append( complexListToString(fitZ) );
    
    sbFit.append("\n");
    sbInit.append("\n");
    sbInitZ.append("\n");
    sbFitZ.append("\n");
    
    if (!solverNotRun) {
      sbInit.append(sbFit);
    }
    
    if (!solverNotRun) {
      sbInitZ.append(sbFitZ);
    }
    
    StringBuilder sbR = new StringBuilder();
    sbR.append("Residuals:");
    sbR.append('\n');
    sbR.append("Initial (nom. resp curve): ");
    sbR.append(initResid);
    sbR.append('\n');
    if (!solverNotRun) {
      sbR.append("Best fit: ");
      sbR.append(fitResid);
    }
    
    return new String[]{sbInit.toString(), sbInitZ.toString(), sbR.toString()};
  }
  
  private ValueAxis degreeAxis, residPhaseAxis, residAmpAxis, prdAxis,
                    residXAxis, residPrdAxis;
  private JComboBox<String> plotSelection;
  private JCheckBox lowFreqBox, showParams, freqSpace;
  private JFreeChart magChart, argChart, residAmpChart, residPhaseChart;
  
  public RandomizedPanel(ExperimentEnum exp) {
    super(exp);
    
    channelType[0] = "Calibration input";
    channelType[1] = "Calibration output from sensor (RESP required)";
    
    initAxes();
    
    applyAxesToChart(); // now that we've got axes defined
    
    magChart = buildChart(null, xAxis, yAxis);
    argChart = buildChart(null, xAxis, degreeAxis);
    residPhaseChart = buildChart(null, xAxis, residPhaseAxis);
    residAmpChart = buildChart(null, xAxis, residAmpAxis);
    
    // set the GUI components
    this.setLayout( new GridBagLayout() );
    GridBagConstraints gbc = new GridBagConstraints();
    
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0; gbc.gridy = 0;
    gbc.weightx = 1.0; gbc.weighty = 1.0;
    gbc.gridwidth = 3;
    gbc.anchor = GridBagConstraints.CENTER;
    this.add(chartPanel, gbc);
    
    // place the other UI elements in a single row below the chart
    gbc.gridwidth = 1;
    gbc.weighty = 0.0; gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridy += 1; gbc.gridx = 0;
    JPanel checkBoxPanel = new JPanel();
    checkBoxPanel.setLayout( new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS) );
    checkBoxPanel.add(lowFreqBox);
    checkBoxPanel.add(showParams);
    checkBoxPanel.add(freqSpace);
    this.add(checkBoxPanel, gbc);
    
    gbc.gridx += 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    // gbc.gridwidth = GridBagConstraints.REMAINDER;
    this.add(save, gbc);
    
    // plot selection combo box
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx += 1;
    gbc.weightx = 0;
    gbc.anchor = GridBagConstraints.WEST;
    plotSelection = new JComboBox<String>();
    plotSelection.addItem(MAGNITUDE);
    plotSelection.addItem(ARGUMENT);
    plotSelection.addItem("Residual amplitude plot");
    plotSelection.addItem("Residual phase plot");
    plotSelection.addActionListener(this);
    this.add(plotSelection, gbc);
  }
  
  private void initAxes() {
    String yAxisTitle = "10 * log10( RESP(f) )";
    String xAxisTitle = "Frequency (Hz)";
    String prdAxisTitle = "Period (s)";
    String degreeAxisTitle = "phi(RESP(f))";
    
    xAxis = new LogarithmicAxis(xAxisTitle);
    prdAxis = new LogarithmicAxis(prdAxisTitle);
    residXAxis = new LogarithmicAxis(xAxisTitle);
    residPrdAxis = new LogarithmicAxis(prdAxisTitle);
    
    yAxis = new NumberAxis(yAxisTitle);
    yAxis.setAutoRange(true);
    
    degreeAxis = new NumberAxis(degreeAxisTitle);
    degreeAxis.setAutoRange(true);    
    
    residPhaseAxis = new NumberAxis("Phase error (degrees)");
    residAmpAxis = new NumberAxis("Amplitude error (percentage)");
    
    ( (NumberAxis) yAxis).setAutoRangeIncludesZero(false);
    Font bold = xAxis.getLabelFont().deriveFont(Font.BOLD);
    xAxis.setLabelFont(bold);
    yAxis.setLabelFont(bold);
    degreeAxis.setLabelFont(bold);
    residPhaseAxis.setLabelFont(bold);
    residAmpAxis.setLabelFont(bold);
    
    lowFreqBox = new JCheckBox("Low frequency calibration");
    lowFreqBox.setSelected(true);
    
    showParams = new JCheckBox("Show params");
    showParams.setEnabled(false);
    showParams.addActionListener(this);
    
    freqSpace = new JCheckBox("Use Hz units (req. regen)");
    freqSpace.setSelected(true);
  }
  
  @Override
  public void actionPerformed(ActionEvent e) {
    
    super.actionPerformed(e);
    
    if ( e.getSource() == plotSelection ) {
      
      if (!set) {
        XYPlot xyp = chart.getXYPlot();
        String label = getXAxis().getLabel();
        xyp.getDomainAxis().setLabel(label);
        label = getYAxis().getLabel();
        xyp.getRangeAxis().setLabel(label);
        return;
      }
      
      int idx = plotSelection.getSelectedIndex();
      JFreeChart[] charts = 
          new JFreeChart[]{magChart, argChart, residAmpChart, residPhaseChart};
      chart = charts[idx];
      chartPanel.setChart(chart);
      
      return;
      
    }
    
    if ( e.getSource() == showParams ) {
      
      if( !showParams.isSelected() ) {
        for ( JFreeChart chart : getCharts() ) {
          Title extra = chart.getSubtitle(TITLE_IDX);
          chart.removeSubtitle(extra);
          extra = chart.getSubtitle(TITLE_IDX);
          chart.removeSubtitle(extra);
        }
      }

      /*
      XYPlot xyp = magChart.getXYPlot();
      xyp.clearAnnotations();
      */
      if ( showParams.isSelected() ) {
        setSubtitles();
        
        // xyp.addAnnotation(xyt);
      }
      
      return;
    }
    
  }
  
  private void setSubtitles() {
    BlockContainer bc = new BlockContainer( new FlowArrangement() );
    CompositeTitle ct = new CompositeTitle(bc);
    String[] insets = getInsetStringsAsList();
    for (String inset : insets) {
      TextTitle result = new TextTitle();
      result.setText(inset);
      // result.setFont( new Font("Dialog", Font.BOLD, 12) );
      result.setBackgroundPaint(Color.white);
      bc.add(result);
    }

    TextTitle result = new TextTitle();
    RandomizedExperiment re = (RandomizedExperiment) expResult;
    int numIters = re.getIterations();
    StringBuilder sb = new StringBuilder("NUMBER OF ITERATIONS: ");
    sb.append(numIters);
    result.setText( sb.toString() );
    result.setBackgroundPaint(Color.white);

    ct.setVerticalAlignment(VerticalAlignment.BOTTOM);
    ct.setPosition(RectangleEdge.BOTTOM);
    result.setVerticalAlignment(VerticalAlignment.BOTTOM);
    result.setPosition(RectangleEdge.BOTTOM);
    for ( JFreeChart chart : getCharts() ) {
      chart.addSubtitle(TITLE_IDX, ct);
      chart.addSubtitle(TITLE_IDX, result);
    }
  }
  
  @Override
  protected void drawCharts() {
    // just force the active plot at the start to be the amplitude plot
    showParams.setSelected(true);
    showParams.setEnabled(true);
    chart = magChart;
    chartPanel.setChart(chart);
    plotSelection.setSelectedIndex(0);
    chartPanel.setMouseZoomable(true);
  }
  
  @Override
  public String[] getAdditionalReportPages() {
    // produce output of poles and zeros as period values in new report page
    
    RandomizedExperiment rnd = (RandomizedExperiment) expResult;
    
    return getAdditionalReportPages(rnd);
  }
  
  @Override
  public JFreeChart[] getCharts() {
    return new JFreeChart[]{magChart, argChart};
  }
  
  @Override
  /**
   * Get the index of the data holding the sensor output.
   * Note that the input data list is listed as CAL, OUT, RESP, so the
   * relevant index is the second one
   */
  protected int getIndexOfMainData() {
    return 1;
  }
  
  /**
   * Used to get the text that will represent the title text in the PDF result
   */
  @Override
  public String getInsetStrings() {
    StringBuilder sb = new StringBuilder();
    for (String str : getInsetStringsAsList()) {
      sb.append(str);
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Produce arrays of pole, zero, and residual data for text titles
   * @return Array of strings 
   */
  public String[] getInsetStringsAsList() {
    RandomizedExperiment rnd = (RandomizedExperiment) expResult;
    return getInsetString(rnd);
  }
  
  @Override
  public String getMetadataString() {
    RandomizedExperiment rnd = (RandomizedExperiment) expResult;
    StringBuilder sb = new StringBuilder();
    
    int iters = rnd.getIterations();
    sb.append("Iteration count from solver: ");
    sb.append(iters);
    sb.append("\n");
    
    sb.append( super.getMetadataString() );
    
    double[] weights = rnd.getWeights();
    sb.append("Residuals weighting:\n");
    sb.append("    Amplitude: ");
    sb.append(weights[0]);
    sb.append("\n");
    sb.append("    Phase: ");
    sb.append(weights[1]);
    return sb.toString();
  }
  
  @Override
  /**
   * Produce the filename of the report generated from this experiment.
   * Since response data is not directly associated with data at a given
   * time, rather than a sensor as a whole, we merely use the current date
   * and the first response used in the experiment.
   * @return String that will be default filename of PDF generated from data
   */
  public String getPDFFilename() {
    
    StringBuilder sb = new StringBuilder();
    if ( lowFreqBox.isSelected() ) {
      sb.append("Low_Frq_");
    } else {
      sb.append("High_Frq_");
    }
    
    sb.append( super.getPDFFilename() );
    
    return sb.toString();
  }
  
  @Override
  public JFreeChart[] getSecondPageCharts() {
    return new JFreeChart[]{residAmpChart, residPhaseChart};
  }
  
  @Override
  public ValueAxis getXAxis() {
    if ( null == plotSelection || freqSpace.isSelected() ) {
      return xAxis;
    } else {
      return prdAxis;
    }
  }
  
  public ValueAxis getResidAxis() {
    if ( null == plotSelection || freqSpace.isSelected() ) {
      return residXAxis;
    } else {
      return residPrdAxis;
    }
  }
  
  @Override
  public ValueAxis getYAxis() {
    
    if (null == plotSelection) {
      return yAxis;
    }
    
    int idx = plotSelection.getSelectedIndex();
    ValueAxis[] out = 
        new ValueAxis[]{yAxis, degreeAxis, residAmpAxis, residPhaseAxis};
    return out[idx];
  }

  @Override
  public int panelsNeeded() {
    return 2;
  }

  @Override
  protected void updateData(DataStore ds) {
    
    //initAxes();
    
    set = true;
    showParams.setSelected(false);
    
    final boolean isLowFreq = lowFreqBox.isSelected();
    seriesColorMap = new HashMap<String, Color>();
    
    RandomizedExperiment rndExp = (RandomizedExperiment) expResult;
    rndExp.setLowFreq(isLowFreq);
    rndExp.useFreqUnits( freqSpace.isSelected() );
    expResult.runExperimentOnData(ds);
    
    String appendFreqTitle;
    
    if (isLowFreq) {
      appendFreqTitle = " (LOW FREQ.)";
    } else {
      appendFreqTitle = " (HIGH FREQ.)";
    }
    
    List<XYSeriesCollection> xysc = expResult.getData();
    
    XYSeriesCollection magSeries = xysc.get(0);
    XYSeriesCollection argSeries = xysc.get(1);
    
    for (int i = 0; i < magSeries.getSeriesCount(); ++i) {
      
      Color toColor = COLOR_LIST[i];
      String magName = (String) magSeries.getSeriesKey(i);
      seriesColorMap.put(magName, toColor);
      
      String argName = (String) argSeries.getSeriesKey(i);
      seriesColorMap.put(argName, toColor);
      
    }
    
    getXAxis().setAutoRange(true);
    
    argChart = buildChart(argSeries, getXAxis(), degreeAxis);
    argChart.getXYPlot().getRangeAxis().setAutoRange(true);
    invertSeriesRenderingOrder(argChart);
    
    magChart = buildChart(magSeries, getXAxis(), yAxis);
    magChart.getXYPlot().getRangeAxis().setAutoRange(true);
    invertSeriesRenderingOrder(magChart);
    
    if (!isLowFreq) {
      Marker maxFitMarker = new ValueMarker( rndExp.getMaxFitFrequency() );
      maxFitMarker.setStroke( new BasicStroke( (float) 1.5 ) );
      magChart.getXYPlot().addDomainMarker(maxFitMarker);
      argChart.getXYPlot().addDomainMarker(maxFitMarker);
    }
    
    String inset = getInsetStrings();
    TextTitle result = new TextTitle();
    result.setText(inset);
    result.setBackgroundPaint(Color.white);
    
    appendChartTitle(argChart, appendFreqTitle);
    appendChartTitle(magChart, appendFreqTitle);
    
    // get residuals plot
    residAmpChart = buildChart(xysc.get(2), getResidAxis(), residAmpAxis);
    /*
    double[] weights = rndExp.getWeights();
    StringBuilder sb = new StringBuilder();
    sb.append("Amplitude weighting: ");
    sb.append(weights[0]);
    sb.append("\nPhase weighting: ");
    sb.append(weights[1]);
    TextTitle weightInset = new TextTitle();
    weightInset.setText( sb.toString() );
    weightInset.setBackgroundPaint(Color.white);
    XYTitleAnnotation weightAnnot = 
        new XYTitleAnnotation(0, 1, weightInset, RectangleAnchor.TOP_LEFT);
    XYPlot residPlot = residChart.getXYPlot();
    residPlot.clearAnnotations();
    residPlot.addAnnotation(weightAnnot);
    */
    residPhaseChart = buildChart(xysc.get(3), getResidAxis(), residPhaseAxis);
    /*
    double[] weights = rndExp.getWeights();
    StringBuilder sb = new StringBuilder();
    sb.append("Amplitude weighting: ");
    sb.append(weights[0]);
    sb.append("\nPhase weighting: ");
    sb.append(weights[1]);
    TextTitle weightInset = new TextTitle();
    weightInset.setText( sb.toString() );
    weightInset.setBackgroundPaint(Color.white);
    XYTitleAnnotation weightAnnot = 
        new XYTitleAnnotation(0, 1, weightInset, RectangleAnchor.TOP_LEFT);
    XYPlot residPlot = residChart.getXYPlot();
    residPlot.clearAnnotations();
    residPlot.addAnnotation(weightAnnot);
    */
    /*
    for ( JFreeChart chart : getCharts() ) {
      chart.addSubtitle( TITLE_IDX, result );
    }
    */
    
    setSubtitles();
  }

}
