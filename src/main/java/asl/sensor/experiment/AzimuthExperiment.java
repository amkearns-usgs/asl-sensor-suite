package asl.sensor.experiment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import 
org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.util.Pair;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import asl.sensor.input.DataBlock;
import asl.sensor.input.DataStore;
import asl.sensor.utils.FFTResult;
import asl.sensor.utils.NumericUtils;
import asl.sensor.utils.TimeSeriesUtils;

/**
 * More specific javadoc will be incoming, but for now a brief explanation
 * of the angle conventions used
 * The program attempts to fit known-orthogonal sensors of unknown azimuth to a
 * reference sensor assumed to be north. The rotation angle between the
 * reference sensor and the unknown components is solved for via least-squares
 * using the coherence calculation of the rotated and reference signal.
 * The resulting angle, then, is the clockwise rotation from the reference.
 * If the angle of the reference is zero (i.e., pointing directly north),
 * the result of this calculation SHOULD be the value of the azimuth, using
 * a clockwise rotation convention.
 * If the reference sensor is itself offset X degrees clockwise from
 * north, the azimuth is the sum of the estimated angle difference between
 * the sensors plus the offset from north.
 * This calculation is mostly based on Ringler, Edwards, et al.,
 * 'Relative azimuth inversion by way of damped maximum correlation estimates',
 * Elsevier Computers and Geosciences 43 (2012)
 * but using coherence maximization rather than correlation to find optimized
 * angles.
 * @author akearns
 *
 */
public class AzimuthExperiment extends Experiment {
  
  /*
   * Check if data is aligned antipolar or not (signs of data are inverted)
   * by determining if the Pearson's correlation metric is positive or not.
   * @param rot Data that has been rotated and may be 180 degrees off from
   * correct orientation (i.e., should but may not be aligned with reference)
   * @param ref Data that is to be used as reference with known orientation
   * @return True if more data analysed has opposite signs than matching signs
   * (i.e., one signal is positive and one is negative)
   */
  /*
  public static boolean
  alignedAntipolar(double[] rot, double[] ref) {
    // TODO: add bandpass filter? what are the relevant frequencies?
    int len = ref.length;
    return alignedAntipolar(rot, ref, len);
  }
  */
    
  /**
   * Check if data is aligned antipolar or not (signs of data are inverted)
   * by determining if the Pearson's correlation metric is positive or not.
   * @param rot Data that has been rotated and may be 180 degrees off from
   * correct orientation (i.e., should but may not be aligned with reference)
   * @param ref Data that is to be used as reference with known orientation
   * @param len Amount of data to be analysed for sign matching
   * @return True if more data analysed has opposite signs than matching signs
   * (i.e., one signal is positive and one is negative)
   */
  public static boolean
  alignedAntipolar(double[] rot, double[] ref, int len) {

    double[] refTrim = Arrays.copyOfRange(ref, 0, len);
    double[] rotTrim = Arrays.copyOfRange(rot, 0, len);
    
    PearsonsCorrelation pc = new PearsonsCorrelation();
    double cor = pc.correlation(refTrim, rotTrim);
    if (cor < 0) {
      return true;
    }

    return false;
    
    /*
    int numSameSign = 0; int numDiffSign = 0;
    for (int i = 0; i < len; ++i) {
      int sigRot = (int) Math.signum(rot[i]);
      int sigRef = (int) Math.signum(ref[i]);
      
      if (sigRot - sigRef == 0) {
        ++numSameSign;
      } else {
        ++numDiffSign;
      }
    }
    
    return numSameSign < numDiffSign;
    */
    
  }
  private double offset = 0.;
  
  private double angle, uncert;
  // private double[] freqs;
  
  // private double[] coherence;
  
  private boolean simpleCalc; // used for nine-noise calculation
  private boolean enoughPts; // enough points in range for estimation?
  
  public AzimuthExperiment() {
    super();
    simpleCalc = false;
  }
  
  /**
   * Entry point for this experiment to guarantee the use of the simple solver
   * and require less overhead, callable from another experiment
   * @param testNorth timeseries data from presumed north-facing test sensor
   * @param testEast timeseries data from presumed east-facing test sensor
   * @param refNorth timeseries data from known north-facing sensor
   * @param interval sampling interval of data
   * @param start start time of data
   * @param end end time of data
   */
  protected void alternateEntryPoint(
      double[] testNorth, double[] testEast, 
      double[] refNorth, long interval, long start, long end) {
    dataNames = new ArrayList<String>();
    dataNames.add("N");
    dataNames.add("E");
    dataNames.add("R");
    simpleCalc = true;
    
    backend(testNorth.clone(), testEast.clone(), refNorth.clone(), 
        interval, start, end);
  }
  
  @Override
  protected void backend(final DataStore ds) {
    
    // assume the first two are the reference and the second two are the test
    // we just need the timeseries, don't actually care about response
    DataBlock testNorthBlock = ds.getXthLoadedBlock(1);
    DataBlock testEastBlock = ds.getXthLoadedBlock(2);
    DataBlock refNorthBlock = ds.getXthLoadedBlock(3);
    
    dataNames = new ArrayList<String>();
    dataNames.add( testNorthBlock.getName() );
    dataNames.add( testEastBlock.getName() );
    dataNames.add( refNorthBlock.getName() );
    
    double[] testNorth = testNorthBlock.getData();
    double[] testEast = testEastBlock.getData();
    double[] refNorth = refNorthBlock.getData();
    
    // resampling should already have been done when loading in data
    long interval = testNorthBlock.getInterval();
    long startTime = testNorthBlock.getStartTime();
    long endTime = testNorthBlock.getEndTime();
    
    backend(testNorth, testEast, refNorth, interval, startTime, endTime);

  }
    
  /**
   * Backend library call for both datasets
   * @param testNorth North-facing data to find azimuth of
   * @param testEast East-facing data to find azimuth of
   * @param refNorth North-facing data to use as reference
   * @param interval Time in nanoseconds between data samples
   * @param startTime Start time of data
   * @param endTime End time of data
   */
  private void backend(
      double[] testNorth, double[] testEast, 
      double[] refNorth, long interval, long startTime, long endTime) {

    enoughPts = false;
    
    double[] initTestNorth = TimeSeriesUtils.demean(testNorth);
    double[] initTestEast = TimeSeriesUtils.demean(testEast);
    double[] initRefNorth =TimeSeriesUtils.demean(refNorth);

    initTestNorth = TimeSeriesUtils.detrend(initTestNorth);
    initTestEast = TimeSeriesUtils.detrend(initTestEast);
    initRefNorth = TimeSeriesUtils.detrend(initRefNorth);
    
    // originally had normalization step here, but that harmed the estimates
    
    double sps = TimeSeriesUtils.ONE_HZ_INTERVAL / interval;
    double low = 1./8;
    double high = 1./4;
    
    initTestNorth = FFTResult.bandFilter(initTestNorth, sps, low, high);
    initTestEast = FFTResult.bandFilter(initTestEast, sps, low, high);
    initRefNorth = FFTResult.bandFilter(initRefNorth, sps, low, high);
    
    MultivariateJacobianFunction jacobian = 
        getJacobianFunction(initTestNorth, initTestEast, initRefNorth, interval);
    
    // want mean coherence to be as close to 1 as possible
    RealVector target = MatrixUtils.createRealVector(new double[]{1.});
    
    
    LeastSquaresProblem findAngleY = new LeastSquaresBuilder().
        start(new double[] {0}).
        model(jacobian).
        target(target).
        maxEvaluations(Integer.MAX_VALUE).
        maxIterations(Integer.MAX_VALUE).
        lazyEvaluation(false).
        build();
    
    LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer().
        withCostRelativeTolerance(1E-7).
        withParameterRelativeTolerance(1E-7);
    
    LeastSquaresOptimizer.Optimum optimumY = optimizer.optimize(findAngleY);
    RealVector angleVector = optimumY.getPoint();
    double tempAngle = angleVector.getEntry(0);
    
    String newStatus = "Found initial guess for angle";
    fireStateChange(newStatus);
    
    // how much data we need (i.e., iteration length) to check 10 seconds
    // used when checking if alignment is off by 180 degrees
    // int tenSecondsLength = (int)  ( sps * 10 ) + 1;
    // int antipolarTrimLen = tenSecondsLength * 100; // thousand secs?
    
    if (simpleCalc) {
      
      // used for orthogonality & multi-component self-noise and gain
      // where a 'pretty good' estimate of the angle is all we need
      // just stop here, don't do windowing
      angle = tempAngle;
      angle = angle % NumericUtils.TAU;
      
      /*
      // check if we need to rotate by 180 degrees
      // (unlikely, assume in simple case sensors near-aligned)
      double[] rot = 
          TimeSeriesUtils.rotate(testNorth, testEast, angle);
      
      if ( alignedAntipolar(rot, refNorth, 2 * tenSecondsLength) ) {
        angle += Math.PI; // still in radians
      }
      */
      
      angle = ( (angle % NumericUtils.TAU) + NumericUtils.TAU) 
          % NumericUtils.TAU;
      
      return;
    }
    
    // angleVector is our new best guess for the azimuth
    // now let's cut the data into 1000-sec windows with 500-sec overlap
    // store the angle and resulting correlation of each window
    // and then take the best-correlation angles and average them
    long timeRange = endTime - startTime;
    
    // first double -- angle estimate over window
    // second double -- coherence from that estimate over the window
    Map<Long, Pair<Double,Double>> angleCoherenceMap = 
        new HashMap<Long, Pair<Double, Double>> ();
    List<Double> sortedCoherence = new ArrayList<Double>();
    
    final long twoThouSecs = 2000L * TimeSeriesUtils.ONE_HZ_INTERVAL; 
    // 1000 ms per second, range length
    final long fiveHundSecs = twoThouSecs / 4L; // distance between windows
    int numWindows = (int) ( (timeRange - twoThouSecs) / fiveHundSecs);
    // look at 2000s windows, sliding over 500s of data at a time
    for (int i = 0; i < numWindows; ++i) {
      StringBuilder sb = new StringBuilder();
      sb.append("Fitting angle over data in window ");
      sb.append(i + 1);
      sb.append(" of ");
      sb.append(numWindows);
      newStatus = sb.toString();
      
      fireStateChange(newStatus);
      
      /*
      if (timeRange < 2 * twoThouSecs) {
        break;
      }
      */
      
      // get start and end indices from given times
      long wdStart = fiveHundSecs * i; // start of 500s-sliding window
      long wdEnd = wdStart + twoThouSecs; // end of window (2000s long)
      
      int startIdx = (int) (wdStart / interval);
      int endIdx = (int) (wdEnd / interval);
      
      double[] testNorthWin = Arrays.copyOfRange(testNorth, startIdx, endIdx);
      double[] testEastWin = Arrays.copyOfRange(testEast, startIdx, endIdx);
      double[] refNorthWin = Arrays.copyOfRange(refNorth, startIdx, endIdx);
      
      testNorthWin = TimeSeriesUtils.detrend(testNorthWin);
      testEastWin = TimeSeriesUtils.detrend(testEastWin);
      refNorthWin = TimeSeriesUtils.detrend(refNorthWin);
      
      testNorthWin = FFTResult.bandFilter(testNorthWin, sps, low, high);
      testEastWin = FFTResult.bandFilter(testEastWin, sps, low, high);
      refNorthWin = FFTResult.bandFilter(refNorthWin, sps, low, high);
      
      jacobian = 
          getJacobianFunction(testNorthWin, testEastWin, refNorthWin, interval);
      
      LeastSquaresProblem findAngleWindow = new LeastSquaresBuilder().
          start(new double[]{tempAngle}).
          model(jacobian).
          target(target).
          maxEvaluations(Integer.MAX_VALUE).
          maxIterations(Integer.MAX_VALUE).
          lazyEvaluation(false).
          // checker(cv).
          build();
            
      optimumY = optimizer.optimize(findAngleWindow);
      
      RealVector angleVectorWindow = optimumY.getPoint();
      double angleTemp = angleVectorWindow.getEntry(0);
      double coherence = jacobian.value(angleVectorWindow).getFirst().getEntry(0);
      /*
      double coherenceAvg = 0;
      for (double cVal : coherence) {
        coherenceAvg += cVal;
      }
      coherenceAvg /= coherence.length;
      */
      angleCoherenceMap.put(
          wdStart, new Pair<Double, Double>(angleTemp, coherence) );
      sortedCoherence.add(coherence);
    }
    
    int minCoherences = 5;
    if (angleCoherenceMap.size() < minCoherences) {
      fireStateChange("Window size too small for good angle estimation...");
      double tau = NumericUtils.TAU;
      angle = ( ( angleVector.getEntry(0) % tau) + tau ) % tau;
    } else {
      // get the best-coherence estimations of angle and average them
      enoughPts = true;
      Collections.sort(sortedCoherence); // now it's actually sorted
      int maxBoundary = Math.max(minCoherences, sortedCoherence.size() * 3 / 20);
      sortedCoherence = sortedCoherence.subList(0, maxBoundary);
      Set<Double> acceptableCoherences = new HashSet<Double>(sortedCoherence);
      
      // store values for use in 
      List<Double> acceptedVals = new ArrayList<Double>();
      
      double averageAngle = 0.;
      int coherenceCount = 0;
      
      for (Pair<Double, Double> angCoherePair : angleCoherenceMap.values()) {
        double angleTemp = angCoherePair.getFirst();
        double coherence = angCoherePair.getSecond();
        if ( acceptableCoherences.contains(coherence) ) {
          averageAngle += angleTemp;
          acceptedVals.add(angleTemp);
          ++coherenceCount;
        }
      }
      
      averageAngle /= coherenceCount;
      
      uncert = 0.;
      
      // now get standard deviation
      for (double angle : acceptedVals) {
        uncert += Math.pow(angle - averageAngle, 2);
      }
      
      uncert = Math.sqrt( uncert / (coherenceCount) );
      uncert *= 2; // two-sigma gets us 95% confidence interval
      
      // do this calculation to get plot of freq/coherence, a side effect
      // of running evaluation at the given point; this will be plotted
      RealVector angleVec = 
          MatrixUtils.createRealVector(new double[]{averageAngle});
      findAngleY.evaluate(angleVec);
      
      double tau = NumericUtils.TAU;
      angle = ( (averageAngle % tau) + tau ) % tau;
      
    }

    fireStateChange("Solver completed! Producing plots...");

    /*
    // solver produces angle of x, 180+x that is closer to reference
    // if angle is ~180 degrees away from reference in reality, then the signal
    // would be inverted from the original. so get 10 seconds of data and check
    // to see if the data is all on the same side of 0.

    double[] rotTimeSeries = 
        TimeSeriesUtils.rotate(testNorth, testEast, angle);
    double[] refTimeSeries = refNorth;

    if ( alignedAntipolar(rotTimeSeries, refTimeSeries, antipolarTrimLen) ) {
      angle += Math.PI; // still in radians
      angle = angle % NumericUtils.TAU; // keep between 0 and 360
    }
    */
    
    double angleDeg = Math.toDegrees(angle);
    
    String northName = dataNames.get(0);
    String eastName = dataNames.get(1);
    String refName = dataNames.get(2);
    
    XYSeries ref = new XYSeries(northName + " rel. to reference");
    ref.add(offset + angleDeg, 0);
    ref.add(offset + angleDeg, 1);
    XYSeries set = new XYSeries(eastName + " rel. to reference");
    set.add(offset + angleDeg + 90, 1);
    set.add(offset + angleDeg + 90, 0);
    XYSeries fromNorth = new XYSeries (refName + " location");
    fromNorth.add(offset, 1);
    fromNorth.add(offset, 0);

    // xySeriesData = new XYSeriesCollection();
    XYSeriesCollection xysc = new XYSeriesCollection();
    xysc.addSeries(ref);
    xysc.addSeries(set);
    xysc.addSeries(fromNorth);
    xySeriesData.add(xysc);
    
    /*
    XYSeries coherenceSeries = new XYSeries("Per-freq. coherence of best-fit");
    for (int i = 0; i < freqs.length; ++i) {
      coherenceSeries.add(freqs[i], coherence[i]);
    }
    */
    
    xysc = new XYSeriesCollection();
    XYSeries timeMapAngle = new XYSeries("Best-fit angle per window");
    XYSeries timeMapCoherence = new XYSeries("Coherence estimate per window");
    xysc.addSeries(timeMapAngle);
    xysc.addSeries(timeMapCoherence);
    
    for ( long time : angleCoherenceMap.keySet() ) {
        long xVal = time / 1000;
        double angle = angleCoherenceMap.get(time).getFirst();
        double coherence = angleCoherenceMap.get(time).getSecond();
        timeMapCoherence.add(xVal, coherence);
        timeMapAngle.add( xVal, Math.toDegrees(angle) );
    }

    
    xySeriesData.add( new XYSeriesCollection(timeMapAngle) );
    xySeriesData.add( new XYSeriesCollection(timeMapCoherence) );
    // xySeriesData.add( new XYSeriesCollection(coherenceSeries) );
  }
  
  @Override
  public int blocksNeeded() {
    return 3;
  }
  
  /**
   * Return the fit angle calculated by the backend in degrees
   * @return angle result in degrees
   */
  public double getFitAngle() {
    return Math.toDegrees(angle);
  }

  /**
   * Return the fit angle calculated by the backend in radians
   * @return angle result in radians
   */
  public double getFitAngleRad() {
    return angle;
  }
  
  /**
   * Returns the jacobian function for this object given input timeseries data.
   * The timeseries are used as input to the rotation function.
   * We take the inputs as fixed and rotate copies of the data to find the
   * Jacobian of the data.
   * @param l1 Data from the test sensor's north-facing component
   * @param l2 Data from the test sensor's east-facing component
   * @param l3 Data from the known north-facing sensor
   * @return jacobian function to fit an angle of max coherence of this data
   */
  private MultivariateJacobianFunction 
  getJacobianFunction(double[] l1, double[] l2, double[] l3, 
      long interval) {    
    
    // make my func the j-func, I want that func-y stuff
    MultivariateJacobianFunction jFunc = new MultivariateJacobianFunction() {

      final double[] finalTestNorth = l1;
      final double[] finalTestEast = l2;
      final double[] finalRefNorth = l3;
      final long finalInterval = interval;

      public Pair<RealVector, RealMatrix> value(final RealVector point) {
        return jacobian(point, 
            finalRefNorth, 
            finalTestNorth, 
            finalTestEast,
            finalInterval);
      }
    };
    
    return jFunc; 
  }
  
  public double getOffset() {
    return ( (offset % 360) + 360 ) % 360;
  }
  
  /**
   * Get the uncertainty of the angle 
   * @return Uncertainty estimation of the current angle (from variance)
   */
  public double getUncertainty() {
    return Math.toDegrees(uncert);
  }
  
  @Override
  public boolean hasEnoughData(DataStore ds) {
    for (int i = 0; i < blocksNeeded(); ++i) {
      if ( !ds.blockIsSet(i) ) {
        return false;
      }
    }
    return true;
  }

  /**
   * Jacobian function for the azimuth solver. Takes in the directional
   * signal components (DataBlocks) and the angle to evaluate at and produces
   * the correlation at that point and the forward difference
   * @param point Current angle
   * @param refNorth Reference sensor, facing north
   * @param testNorth Test sensor, facing approximately north
   * @param testEast Test sensor, facing approximately east and orthogonal to
   * testNorth
   * @return Correlation (RealVector) and forward difference 
   * approximation of the Jacobian (RealMatrix) at the current angle
   */
  private Pair<RealVector, RealMatrix> jacobian(
      final RealVector point, 
      final double[] refNorth,
      final double[] testNorth, 
      final double[] testEast,
      final long interval) {
    
    double diff = 1E-7;
    
    double theta = ( point.getEntry(0) );
    double thetaDelta = theta + diff;
    
    // was the frequency range under examination (in Hz) when doing coherence
    // double lowFreq = 1./18.;
    // double highFreq = 1./3.;
    
    // angles of rotation are x, x+dx respectively
    double[] testRotated = 
        TimeSeriesUtils.rotate(testNorth, testEast, theta);
    double[] rotatedDiff =
        TimeSeriesUtils.rotate(testNorth, testEast, thetaDelta);
    
    PearsonsCorrelation pc = new PearsonsCorrelation();
    double value = pc.correlation(refNorth, testRotated);
    RealVector valueVec = MatrixUtils.createRealVector(new double[]{value});
    double deltaY = pc.correlation(refNorth, rotatedDiff);
    double change = (deltaY - value) / diff;
    double[][] jacobianArray = new double[][]{{change}};
    RealMatrix jacobian = MatrixUtils.createRealMatrix(jacobianArray);
    return new Pair<RealVector, RealMatrix>(valueVec, jacobian);
    
    /*
    // was the frequency range under examination (in Hz) when doing coherence
    double lowFreq = 1./18.;
    double highFreq = 1./3.;     
    // this is the old code that used a correlation calculation
    // similar to how the cal solvers deconvolve responses
    // commented out because it can't distinguish 180-out rotation
    FFTResult crossPower = 
        FFTResult.spectralCalc(refNorth, testRotated, interval);
    FFTResult rotatedPower = 
        FFTResult.spectralCalc(testRotated, testRotated, interval);
    FFTResult refPower = 
        FFTResult.spectralCalc(refNorth, refNorth, interval);
    
    freqs = crossPower.getFreqs();
    
    Complex[] crossPowerSeries = crossPower.getFFT();
    Complex[] rotatedSeries = rotatedPower.getFFT();
    Complex[] refSeries = refPower.getFFT();
    
    coherence = new double[crossPowerSeries.length];
    
    for (int i = 0; i < crossPowerSeries.length; ++i) {
      Complex conj = crossPowerSeries[i].conjugate();
      Complex numerator = crossPowerSeries[i].multiply(conj);
      Complex denom = rotatedSeries[i].multiply(refSeries[i]);
      coherence[i] = numerator.divide(denom).getReal();
    }
    
    double peakVal = Double.NEGATIVE_INFINITY;
    double peakFreq = 0;
    
    for (int i = 0; i < freqs.length; ++i) {
      if (freqs[i] < lowFreq) {
        continue;
      } else if (freqs[i] > highFreq) {
        break;
      }
      if (peakVal < coherence[i]) {
        peakVal = coherence[i];
        peakFreq = freqs[i];
      }
    }
    
    if (peakFreq / 2 > lowFreq) {
      lowFreq = peakFreq / 2.;
    }
    
    if (peakFreq * 2 < highFreq) {
      highFreq = peakFreq * 2.;
    }
    
    double meanCoherence = 0.;
    int samples = 0;
    
    for (int i = 0; i < freqs.length; ++i) {
      if (freqs[i] < highFreq && freqs[i] > lowFreq) {
        meanCoherence += coherence[i];
        ++samples;
      }
    }
    
    meanCoherence /= samples;
    
    RealVector curValue = 
        MatrixUtils.createRealVector(new double[]{meanCoherence});
    
    double thetaDelta = theta + diff;
    double[] rotateDelta = 
        TimeSeriesUtils.rotate(testNorth, testEast, thetaDelta);
    
    crossPower = FFTResult.spectralCalc(refNorth, rotateDelta, interval);
    rotatedPower = FFTResult.spectralCalc(rotateDelta, rotateDelta, interval);
    crossPowerSeries = crossPower.getFFT();
    rotatedSeries = rotatedPower.getFFT();
    
    double fwdMeanCoherence = 0.;
    samples = 0;
    double[] fwdCoherence = new double[crossPowerSeries.length];
    
    for (int i = 0; i < crossPowerSeries.length; ++i) {
      Complex conj = crossPowerSeries[i].conjugate();
      Complex numerator = crossPowerSeries[i].multiply(conj);
      Complex denom = rotatedSeries[i].multiply(refSeries[i]);
      fwdCoherence[i] = numerator.divide(denom).getReal();
      
      if (freqs[i] < highFreq && freqs[i] > lowFreq) {
        fwdMeanCoherence += fwdCoherence[i];
        ++ samples;
      }
      
    }
    
    fwdMeanCoherence /= (double) samples;
    double deltaMean = (fwdMeanCoherence - meanCoherence) / diff;
    
    // System.out.println(deltaMean);
    
    double[][] jacobianArray = new double[1][1];
    jacobianArray[0][0] = deltaMean;
    
    // we have only 1 variable, so jacobian is a matrix w/ single column
    RealMatrix jbn = MatrixUtils.createRealMatrix(jacobianArray);
    
    return new Pair<RealVector, RealMatrix>(curValue, jbn);
    */
  }

  /**
   * Set the angle offset for the reference sensor (degrees from north)
   * @param newOffset Degrees from north that the reference sensor points
   */
  public void setOffset(double newOffset) {
    offset = newOffset;
  }
  
  /**
   * Used to set a simple calculation of rotation angle, such as for
   * nine-input self-noise. This is the case where the additional windowing
   * is NOT done, and the initial least-squares guess gives us an answer.
   * When creating an instance of this object, this is set to false and only
   * needs to be explicitly set when a simple calculation is desired.
   * @param isSimple True if a simple calculation should be done
   */
  public void setSimple(boolean isSimple) {
    simpleCalc = isSimple;
  }
  
  /**
   * Returns true if there were enough points to do correlation windowing step
   * @return Boolean that is true if correlation windows were taken
   */
  public boolean hadEnoughPoints() {
    return enoughPts;
  }
}