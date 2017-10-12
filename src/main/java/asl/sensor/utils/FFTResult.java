package asl.sensor.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.jfree.data.xy.XYSeries;

import asl.sensor.input.DataBlock;
import asl.sensor.input.InstrumentResponse;

/**
 * Holds the data returned from a power spectral density calculation
 * (The PSD data (without response correction) and frequencies of the FFT)
 * Most methods that either calculate or involve FFT calculations exist here,
 * such as raw PSD calculation, inverse and forward trimmed FFTs,
 * and band-pass filtering.
 * @author akearns
 *
 */
public class FFTResult {
  
  /**
   * Specifies the width of the cosine taper function used in windowing
   */
  private static final double TAPER_WIDTH = 0.10;
  
  /**
   * Filter out data outside of the range between the low and high frequencies;
   * can be used for a low-pass filter if low frequency is set to 0
   * and high-pass if higher frequency is set to sample rate
   * @param toFilt series of data to do a band-pass filter on
   * @param sps sample rate of the current data (samples / sec)
   * @param low low corner frequency of band-pass filter
   * @param high high corner frequency of band-pass filter
   * @return timeseries with band-pass filter applied
   */
  public static double[] 
  bandFilter(double[] toFilt, double sps, double low, double high) {
    
    return bandFilterWithCuts(toFilt, sps, low, high, 0., sps);
    /*
    Complex[] fft = simpleFFT(toFilt);
    
    int trim = fft.length / 2 + 1;
    
    Complex[] toInvert = new Complex[trim];
    
    double freqDelta = sps / trim;
    
    for (int i = 0; i < trim; ++i) {
      double x = i * freqDelta;
      double scale = 1;
      if (x < low) {
        scale = x / low;
      } else if (x > high) {
        scale = 1 - ( x / (sps - high) );
      }
      
      toInvert[i] = fft[i].multiply(scale);
    }
    
    return singleSidedInverseFFT(toInvert, toFilt.length);
    */
    
  }
  
  /**
   * Wrapper to do band filter on a list of data rather than an array.
   * For more details see other definition of bandFilter
   * @param toFilt timeseries data to be filtered
   * @param sps samples per second of input data
   * @param low low corner frequency for trim
   * @param high higher corner frequency for trim
   * @return timeseries data (list) that has gone through band-pass filter
   */
  public static List<Number> 
  bandFilter(List<Number> toFilt, double sps, double low, double high) {
    
    
    double[] toFFT = new double[toFilt.size()];
    
    for (int i = 0; i < toFFT.length; ++i) {
      toFFT[i] = toFilt.get(i).doubleValue();
    }
    
    toFFT = bandFilter(toFFT, sps, low, high);
    
    List<Number> out = new ArrayList<Number>();
    for (double value : toFFT) {
      out.add(value);
    }
    
    return out;
    
  }
  
  /**
   * Band-pass filter that creates hard-stop for values outside of range but
   * produces linear dropoff for points between the corner frequencies and
   * hard stop limits.
   * @param toFilt series of data to do a band-pass filter on
   * @param sps sample rate of the current data (samples / sec)
   * @param low low corner frequency of band-pass filter
   * @param high high corner frequency of band-pass filter
   * @param lowStop low frequency value beyond which to attenuate all signal
   * @param highStop high frequency value beyond which to attenuate all signal
   * @return timeseries with band-pass filter applied
   */
  public static double[] 
  bandFilterWithCuts(double[] toFilt, double sps, double low, double high, 
                     double lowStop, double highStop) {
    
    // System.out.println("FILTERING OPERATION OCCURRING");
    
    Complex[] fft = simpleFFT(toFilt);
    
    int trim = fft.length/2 + 1;
    
    Complex[] toInvert = new Complex[trim];
    
    double freqDelta = sps / trim;
    
    for (int i = 0; i < trim; ++i) {
      double x = i * freqDelta;
      double scale = 1;
      if (x < lowStop || x > highStop) {
        scale = 0;
      } else if (x < low) {
        scale = (x - lowStop) / (low - lowStop);
      } else if (x > high) {
        scale = (x - highStop) / (high - highStop);
      }
      
      toInvert[i] = fft[i].multiply(scale);
    }
    
    return singleSidedInverseFFT(toInvert, toFilt.length);
  }
  
  /**
   * Calculates and performs an in-place cosine taper on an incoming data set.
   * Used for windowing for performing FFT.
   * @param dataSet The dataset to have the taper applied to.
   * @param taperW Width of taper to be used
   * @return Value corresponding to power loss from application of taper.
   */
  public static double cosineTaper(double[] dataSet, double taperW) {
    
    double ramp = taperW * dataSet.length;
    double taper;
    double wss = 0.0; // represents power loss
    
    for (int i = 0; i < ramp; i++) {
      taper = 0.5 * (1.0 - Math.cos( (double) i * Math.PI / ramp) );
      dataSet[i] *= taper;
      int idx = dataSet.length-i-1;
      dataSet[idx] *= taper;
      wss += 2.0 * taper * taper;
    }
    
    wss += ( dataSet.length - (2 * ramp) );
    
    return wss;
  }
  
  /**
   * Root funtion for calculating crosspower. Gets spectral calculation of data
   * from inputted data series by calling the spectralCalc function, and then
   * applies the provided responses to that result. This is the Power Spectral
   * Density of the inputted data if both sets are the same.
   * @param data1 First data series
   * @param data2 Second data series
   * @param ir1 Response of instrument producing first series
   * @param ir2 Response of instrument producing second series
   * @return Data structure containing the crosspower result of the two data
   * sets as a complex array and the frequencies matched to them in a double
   * array. 
   */
  public static FFTResult crossPower(DataBlock data1, DataBlock data2,
      InstrumentResponse ir1, InstrumentResponse ir2) {
    
    FFTResult selfPSD = spectralCalc(data1, data2);
    Complex[] results = selfPSD.getFFT();
    double[] freqs = selfPSD.getFreqs();
    Complex[] freqRespd1 = ir1.applyResponseToInput(freqs);
    Complex[] freqRespd2 = ir2.applyResponseToInput(freqs);
    
    return crossPower(results, freqs, freqRespd1, freqRespd2);
  }
  
  private static FFTResult crossPower(Complex[] results, double[] freqs, 
      Complex[] freqRespd1, Complex[] freqRespd2) {
    
    Complex[] out = new Complex[freqs.length];
    
    for (int j = 0; j < freqs.length; ++j) {
      Complex scaleFactor = 
          new Complex( 0.0, -1.0 / (NumericUtils.TAU * freqs[j]) );
      Complex resp1 = freqRespd1[j].multiply(scaleFactor);
      Complex resp2 = freqRespd2[j].multiply(scaleFactor);
      
      Complex respMagnitude = 
          resp1.multiply( resp2.conjugate() );
      
      
      if (respMagnitude.abs() == 0) {
        respMagnitude = new Complex(Double.MIN_VALUE, 0);
      }
      
      out[j] = results[j].divide(respMagnitude);
    }
    
    return new FFTResult(out, freqs);
    
  }
  
  public static FFTResult crossPower(double[] data1, double[] data2,
      InstrumentResponse ir1, InstrumentResponse ir2, long interval) {
    FFTResult selfPSD = spectralCalc(data1, data2, interval);
    Complex[] results = selfPSD.getFFT();
    double[] freqs = selfPSD.getFreqs();
    Complex[] freqRespd1 = ir1.applyResponseToInput(freqs);
    Complex[] freqRespd2 = ir2.applyResponseToInput(freqs);
    
    return crossPower(results, freqs, freqRespd1, freqRespd2);
  }
  
  /**
   * Collects the data points in the Peterson new high noise model 
   * into a plottable format.
   * Assumes that there is a text file in the .resources folder that contains 
   * the NHNM data points for given input frequencies.
   * @param freqSpace True if the data's x-axis should be units of Hz
   * (otherwise it is units of seconds, the interval between samples)
   * @return Plottable data series representing the NHNM
   */
  public static XYSeries getHighNoiseModel(boolean freqSpace) {
    XYSeries xys = new XYSeries("NHNM");
    try {
      ClassLoader cl = FFTResult.class.getClassLoader();
      InputStream is = cl.getResourceAsStream("NHNM.txt");
      
      BufferedReader fr = new BufferedReader( new InputStreamReader(is) );
      String str = fr.readLine();
      while (str != null) {
        String[] values = str.split("\\s+");
        double x = Double.parseDouble(values[0]); // period, in seconds
        if (x > 1.0E3) {
          break;
        }
        double y = Double.parseDouble(values[1]);
        if (freqSpace) {
          xys.add(1/x, y);
        } else {
          xys.add(x, y);
        }
        
        str = fr.readLine();
      }
      fr.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return xys;
  }
  
  /**
   * Collects the data points in the Peterson new low noise model 
   * into a plottable format.
   * Assumes that there is a text file in the .resources folder that contains 
   * the NLNM data points for given input frequencies. ("NLNM.txt")
   * @param freqSpace True if the data's x-axis should be units of Hz
   * (otherwise it is units of seconds, the interval between samples)
   * @return Plottable data series representing the NLNM
   */
  public static XYSeries getLowNoiseModel(boolean freqSpace) {
    XYSeries xys = new XYSeries("NLNM");
    try {
      
      ClassLoader cl = FFTResult.class.getClassLoader();
      
      InputStream is = cl.getResourceAsStream("NLNM.txt");
      
      BufferedReader fr = new BufferedReader( new InputStreamReader(is) );
      String str = fr.readLine();
      while (str != null) {
        String[] values = str.split("\\s+");
        double x = Double.parseDouble(values[0]); // period, in seconds
        if (x > 1.0E3) {
          break;
        }
        double y = Double.parseDouble(values[3]);
        if (freqSpace) {
          xys.add(1/x, y);
        } else {
          xys.add(x, y);
        }
        
        str = fr.readLine();
      }
      fr.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return xys;
  }
  
  
  /**
   * Produce a multitaper series using a sine function for use in spectral
   * calculations (i.e., specified when calculating PSD values)
   * @param winLen Length of the window (how long the data is)
   * @param numTapers Number of tapers to apply to the data
   * @return 2D array with first dimension being the timeseries length and
   * the second dimension being the taper count
   */
  public static double[][] getMultitaperSeries(int winLen, int numTapers) {
    double[][] taperMat = new double[numTapers][winLen];
    
    double denom = winLen - 1;
    double scale = Math.sqrt( 2 / denom );
    
    // TODO: may need to check correct loop index order for efficiency
    for (int j = 0; j < numTapers; ++j) {
      for (int i = 0; i < winLen; ++i) {
        // is the rightmost value of the series nonzero because of precision? 
        taperMat[j][i] = scale * Math.sin(Math.PI * i * (j + 1) / denom);
      }
    }
    
    return taperMat;
  }
  
  /**
   * Function for padding and returning the result of a forward FFT.
   * This does not trim the negative frequencies of the result; it returns
   * the full FFT result as an array of Complex numbers
   * @param dataIn Array of doubles representing timeseries data
   * @return Complex array representing forward FFT values, including
   * symmetric component (second half of the function)
   */
  public static Complex[] simpleFFT(double[] dataIn) {
    
    int padding = 2;
    while ( padding < dataIn.length ) {
      padding *= 2;
    }
    
    double[] toFFT = new double[padding];
    
    for (int i = 0; i < dataIn.length; ++i) {
      toFFT[i] = dataIn[i];
    }
    
    FastFourierTransformer fft = 
        new FastFourierTransformer(DftNormalization.STANDARD);
    
    Complex[] frqDomn = fft.transform(toFFT, TransformType.FORWARD);
    
    return frqDomn;
  }
  
  /**
   * Calculates the FFT of the timeseries data in a DataBlock
   * and returns the positive frequencies resulting from the FFT calculation
   * @param db DataBlock to get the timeseries data from
   * @param mustFlip True if signal from sensor is inverted (for step cal)
   * @return Complex array of FFT values and double array of corresponding 
   * frequencies 
   */
  public static FFTResult singleSidedFFT(DataBlock db, boolean mustFlip) {
    
    double[] data = db.getData().clone();
    
    for (int i = 0; i < db.size(); ++i) {
      if (mustFlip) {
        data[i] *= -1;
      }
    }
    
    data = TimeSeriesUtils.demean(data);
    
    // data = TimeSeriesUtils.normalize(data);
    
    Complex[] frqDomn = simpleFFT(data);
    
    int padding = frqDomn.length;
    int singleSide = padding/2 + 1;
    
    double nyquist = db.getSampleRate() / 2;
    double deltaFrq = nyquist / (singleSide - 1);
    
    Complex[] fftOut = new Complex[singleSide];
    double[] frequencies = new double[singleSide];
    
    for (int i = 0; i < singleSide; ++i) {
      fftOut[i] = frqDomn[i];
      frequencies[i] = i * deltaFrq;
    }
    
    // System.out.println(frequencies[singleSide - 1]);
    
    return new FFTResult(fftOut, frequencies);
    
  }
  
  /**
   * Calculates the FFT of the timeseries data in a DataBlock
   * and returns the positive frequencies resulting from the FFT calculation
   * @param db DataBlock to get the timeseries data from
   * @param mustFlip True if signal from sensor is inverted (for step cal)
   * @return Complex array of FFT values and double array of corresponding 
   * frequencies 
   */
  public static FFTResult 
  singleSidedFilteredFFT(DataBlock db, boolean mustFlip) {
    
    double[] data = db.getData().clone();
    
    for (int i = 0; i < db.size(); ++i) {
      if (mustFlip) {
        data[i] *= -1;
      }
    }
    
    long interval = db.getInterval();

    double sps = TimeSeriesUtils.ONE_HZ_INTERVAL / interval;
    
    data = bandFilter(data, sps, 0.0, 0.1);
    
    data = TimeSeriesUtils.demean(data);
    
    // data = TimeSeriesUtils.normalize(data);
    
    Complex[] frqDomn = simpleFFT(data);
    
    int padding = frqDomn.length;
    int singleSide = padding/2 + 1;
    
    double nyquist = db.getSampleRate() / 2;
    double deltaFrq = nyquist / (singleSide - 1);
    
    Complex[] fftOut = new Complex[singleSide];
    double[] frequencies = new double[singleSide];
    
    for (int i = 0; i < singleSide; ++i) {
      fftOut[i] = frqDomn[i];
      frequencies[i] = i * deltaFrq;
    }
    
    return new FFTResult(fftOut, frequencies);
    
  }
  
  /**
   * Do the inverse FFT on the result of a single-sided FFT operation.
   * The negative frequencies are reconstructed as the complex conjugates of
   * the positive corresponding frequencies
   * @param freqDomn Complex array (i.e., the result of a previous FFT calc)
   * @param trim How long the original input data was
   * @return A list of doubles representing the original timeseries of the FFT
   */
  public static double[] singleSidedInverseFFT(Complex[] freqDomn, int trim) {
    FastFourierTransformer fft = 
        new FastFourierTransformer(DftNormalization.STANDARD);
     
    int padding = (freqDomn.length - 1) * 2;
    
    Complex[] padded = new Complex[padding];
    for (int i = 0; i < freqDomn.length; ++i) {
      padded[i] = freqDomn[i];
    }
    for (int i = 1; i < padding/2; ++i) {
      // System.out.println(freqDomn.length+","+i);
      padded[padded.length - i] = padded[i].conjugate();
    }
    
    Complex[] timeSeriesCpx = 
        fft.transform(padded, TransformType.INVERSE);
    
    double[] timeSeries = new double[trim];
    for (int i = 0; i < trim; ++i) {
      timeSeries[i] = timeSeriesCpx[i].getReal();
    }
    
    return timeSeries;
  }
  
  /**
   * Helper function to calculate power spectral density / crosspower.
   * Takes in two time series data and produces the windowed FFT over each.
   * The first is multiplied by the complex conjugate of the second.
   * If the two series are the same, this is the PSD of that series. If they
   * are different, this result is the crosspower.
   * The result is smoothed but does not have the frequency response applied,
   * and so does not give a full result -- this is merely a helper function
   * for the crossPower function.
   * @param data1 DataBlock with relevant time series data
   * @param data2 DataBlock with relevant time series data
   * @return A structure with two arrays: an array of Complex numbers 
   * representing the PSD result, and an array of doubles representing the
   * frequencies of the PSD.
   */
  public static FFTResult spectralCalc(DataBlock data1, DataBlock data2) {

    // this is ugly logic here, but this saves us issues with looping
    // and calculating the same data twice
    boolean sameData = data1.getName().equals( data2.getName() );
    
    double[] list1 = data1.getData();
    double[] list2 = list1;
    if (!sameData) {
      list2 = data2.getData();
    }
    
    long interval = data1.getInterval();
    
    return spectralCalc(list1, list2, interval);
    
  }
    
  /**
   * Helper function to calculate power spectral density / crosspower.
   * Takes in two time series data and produces the windowed FFT over each.
   * The first is multiplied by the complex conjugate of the second.
   * If the two series are the same, this is the PSD of that series. If they
   * are different, this result is the crosspower.
   * The result is smoothed but does not have the frequency response applied,
   * and so does not give a full result -- this is merely a helper function
   * for the crossPower function.
   * @param list1 First list of data to be given as input
   * @param list2 Second list of data to be given as input, which can be
   * the same as the first (and if so, is ignored)
   * @param interval Interval of the data (same for both lists)
   * @return FFTResult (FFT values and frequencies as a pair of arrays)
   * representing the power-spectral density / crosspower of the input data.
   */
  public static FFTResult 
  spectralCalc(double[] list1, double[] list2, long interval) {
    
    boolean sameData = list1.equals(list2);
    
    // divide into windows of 1/4, moving up 1/16 of the data at a time
    
    int range = list1.length/4;
    int slider = range/4;
    
    // period is 1/sample rate in seconds
    // since the interval data is just that multiplied by a large number
    // let's divide it by that large number to get our period
    
    // shouldn't need to worry about a cast here
    double period = 1.0 / TimeSeriesUtils.ONE_HZ_INTERVAL;
    period *= interval;
    
    int padding = 2;
    while (padding < range) {
      padding *= 2;
    }
    
    int singleSide = padding / 2 + 1;
    double deltaFreq = 1. / (padding * period);
    
    Complex[] powSpectDens = new Complex[singleSide];
    double wss = 0;
    
    int segsProcessed = 0;
    int rangeStart = 0;
    int rangeEnd = range;
    
    for (int i = 0; i < powSpectDens.length; ++i) {
      powSpectDens[i] = Complex.ZERO;
    }
    
    while ( rangeEnd <= list1.length ) {
      
      Complex[] fftResult1 = new Complex[singleSide]; // first half of FFT reslt
      Complex[] fftResult2 = null;
      
      if (!sameData) {
        fftResult2 = new Complex[singleSide];
      }
      
      // give us a new list we can modify to get the data of
      double[] data1Range = 
          Arrays.copyOfRange(list1, rangeStart, rangeEnd);
      double[] data2Range = null;
      
      if (!sameData) {
        data2Range = Arrays.copyOfRange(list2, rangeStart, rangeEnd);
      }
       
      // double arrays initialized with zeros, set as a power of two for FFT
      // (i.e., effectively pre-padded on initialization)
      double[] toFFT1 = new double[padding];
      double[] toFFT2 = null;
      
      // demean and detrend work in-place on the list
      TimeSeriesUtils.detrend(data1Range);
      TimeSeriesUtils.demeanInPlace(data1Range);
      wss = cosineTaper(data1Range, TAPER_WIDTH);
      // presumably we only need the last value of wss
      
      if (!sameData) {
        TimeSeriesUtils.demeanInPlace(data2Range);
        TimeSeriesUtils.detrend(data2Range);
        wss = cosineTaper(data2Range, TAPER_WIDTH);
        toFFT2 = new double[padding];
      }
      
      // TODO: this can clearly be refactored
      for (int i = 0; i < data1Range.length; ++i) {
        // no point in using arraycopy -- must make sure each Number's a double
        toFFT1[i] = data1Range[i];
        if (!sameData) {
          toFFT2[i] = data2Range[i];
        }
      }
      
      FastFourierTransformer fft = 
          new FastFourierTransformer(DftNormalization.STANDARD);

      Complex[] frqDomn1 = fft.transform(toFFT1, TransformType.FORWARD);
      // use arraycopy now (as it's fast) to get the first half of the fft
      System.arraycopy(frqDomn1, 0, fftResult1, 0, fftResult1.length);
      
      Complex[] frqDomn2 = null;
      if (toFFT2 != null) {
        frqDomn2 = fft.transform(toFFT2, TransformType.FORWARD);
        System.arraycopy(frqDomn2, 0, fftResult2, 0, fftResult2.length);
      }
      
      for (int i = 0; i < singleSide; ++i) {
        
        Complex val1 = fftResult1[i];
        Complex val2 = val1;
        if (fftResult2 != null) {
          val2 = fftResult2[i];
        }
        
        val1 = val1.multiply(2);
        val2 = val2.multiply(2);
        
        powSpectDens[i] = 
            powSpectDens[i].add( 
                val1.multiply( 
                    val2.conjugate() ) );
      }
      
      ++segsProcessed;
      rangeStart  += slider;
      rangeEnd    += slider;
      
    }
    
    // normalization time!
    // System.out.println("PERIOD: " + period);
    // period = 1.0; // quick testing
    double psdNormalization = period / padding; // was mult. by 2.0 previously
    // removal of 2.0 here result of adding mult by 2 ~line 654
    double windowCorrection = wss / (double) range;
    // value of wss associated with taper parameters, not related to data
    
    psdNormalization /= windowCorrection;
    psdNormalization /= segsProcessed; // NOTE: divisor here should be 13
    
    double[] frequencies = new double[singleSide];
    
    for (int i = 0; i < singleSide; ++i) {
      powSpectDens[i] = powSpectDens[i].multiply(psdNormalization);
      frequencies[i] = i * deltaFreq;
    }
    
    // do smoothing over neighboring frequencies; values taken from 
    // asl.timeseries' PSD function
    int nSmooth = 11, nHalf = 5;
    Complex[] psdCFSmooth = new Complex[singleSide];
    
    int iw = 0;
    
    for (iw = 0; iw < nHalf; ++iw) {
      psdCFSmooth[iw] = powSpectDens[iw];
    }
    
    // iw should be icenter of nsmooth point window
    for (; iw < singleSide - nHalf; ++iw){
      int k1 = iw - nHalf;
      int k2 = iw + nHalf;
      
      Complex sumC = Complex.ZERO;
      for (int k = k1; k < k2; ++k) {
        sumC = sumC.add(powSpectDens[k]);
      }
      psdCFSmooth[iw] = sumC.divide((double) nSmooth);
    }
    
    // copy remaining into smoothed array
    for (; iw < singleSide; ++iw) {
      psdCFSmooth[iw] = powSpectDens[iw];
    }
    
    return new FFTResult(psdCFSmooth, frequencies);
    
  }
  
  /**
   * Calculate the PSD using a multitaper on the data. This obviates the need
   * for windowing the input, so low-frequency data is retained better.
   * The given result is FFT(list1) * Conjugate(FFT(list2)).
   * @param data1 First datablock to be given as input
   * @param data2 Second datablock to be given as input, which can be
   * the same as the first (based on data's name; if equal, is ignored)
   * @return FFTResult (FFT values and frequencies as a pair of arrays)
   * representing the power-spectral density / crosspower of the input data.
   */
  public static FFTResult 
  spectralCalcMultitaper(DataBlock data1, DataBlock data2) {
    // this is ugly logic here, but this saves us issues with looping
    // and calculating the same data twice
    boolean sameData = data1.getName().equals( data2.getName() );
    
    double[] list1 = data1.getData();
    double[] list2 = list1;
    if (!sameData) {
      list2 = data2.getData();
    }
    
    return spectralCalcMultitaper( list1, list2, data1.getInterval() );
  }
  
  /**
   * Calculate the PSD using a multitaper on the data. This obviates the need
   * for windowing the input, so low-frequency data is retained better.
   * The given result is FFT(list1) * Conjugate(FFT(list2)).
   * @param list1 First list of data to be given as input
   * @param list2 Second list of data to be given as input, which can be
   * the same as the first (and if so, is ignored)
   * @param ivl Interval of the data (same for both lists)
   * @return FFTResult (FFT values and frequencies as a pair of arrays)
   * representing the power-spectral density / crosspower of the input data.
   */
  public static FFTResult 
  spectralCalcMultitaper(double[] list1, double[] list2, long ivl) {
    
    boolean sameData = list1.equals(list2);
    
    int padding = 2;
    while ( padding < list1.length ) {
      padding *= 2;
    }
    
    final int TAPER_COUNT = 12;
    double period = 1.0 / TimeSeriesUtils.ONE_HZ_INTERVAL;
    period *= ivl;
    
    int singleSide = padding / 2 + 1;
    double deltaFreq = 1. / (padding * period);
    
    Complex[] powSpectDens = new Complex[singleSide];
    
    for (int i = 0; i < powSpectDens.length; ++i) {
      powSpectDens[i] = Complex.ZERO;
    }
   
    Complex[] fftResult1 = new Complex[singleSide]; // first half of FFT result
    for (int i = 0; i < fftResult1.length; ++i) {
      fftResult1[i] = Complex.ZERO;
    }
    Complex[] fftResult2 = fftResult1;
    // instantiate FFT-calculating object
    FastFourierTransformer fft = 
        new FastFourierTransformer(DftNormalization.STANDARD);
    
    if (!sameData) {
      fftResult2 = new Complex[singleSide];    
      for (int i = 0; i < fftResult2.length; ++i) {
        fftResult2[i] = Complex.ZERO;
      }
    }
    
    // give us a new list we can modify to get the data of
    double[] data1Range = list1.clone();
    double[] data2Range = data1Range;
    if (!sameData) {
      data2Range = list2.clone();
    }
    
    // double arrays initialized with zeros, set as a power of two for FFT
    // (i.e., effectively pre-padded on initialization)

    double[][] taperMat = 
        getMultitaperSeries(data1Range.length, TAPER_COUNT);
    // System.out.println("SIZES: " + padding + ", " + data1Range.size());
    
    // demean and detrend work in-place on the list
    TimeSeriesUtils.detrend(data1Range);
    TimeSeriesUtils.demeanInPlace(data1Range);
    // apply each taper, take FFT, and average the overall results
    double[] data = data1Range;
    for (int j = 0; j < taperMat.length; ++j) {
      double[] toFFT = new double[padding];
      double[] taperCurve = taperMat[j];
      double taperSum = 0.;
      for (int i = 0; i < data.length; ++i) {
        taperSum += Math.abs(taperCurve[i]);
        double point = data[i];
        toFFT[i] = point * taperCurve[i];
      }
      Complex[] frqDomn = fft.transform(toFFT, TransformType.FORWARD);
      for (int i = 0; i < fftResult1.length; ++i) {
        fftResult1[i] = fftResult1[i].add( frqDomn[i].divide(taperSum) );
      }
    }
    for (int i = 0; i < fftResult1.length; ++i) {
      fftResult1[i] = fftResult1[i].divide(TAPER_COUNT);
    }
    
    if (!sameData) {
      TimeSeriesUtils.detrend(data2Range);
      TimeSeriesUtils.demeanInPlace(data2Range);
      data = data2Range;
      for (int j = 0; j < taperMat.length; ++j) {
        double[] toFFT = new double[padding];
        double[] taperCurve = taperMat[j];
        double taperSum = 0.;
        for (int i = 0; i < data.length; ++i) {
          taperSum += Math.abs(taperCurve[i]);
          double point = data[i];
          toFFT[i] = point * taperCurve[i];
        }
        Complex[] frqDomn = fft.transform(toFFT, TransformType.FORWARD);
        for (int i = 0; i < fftResult2.length; ++i) {
          fftResult2[i] = fftResult2[i].add( frqDomn[i].divide(taperSum) );
        }
      }
      for (int i = 0; i < fftResult2.length; ++i) {
        fftResult2[i] = fftResult2[i].divide(TAPER_COUNT);
      }
    }
    
    double[] frequencies = new double[singleSide];
    for (int i = 0; i < singleSide; ++i) {
      frequencies[i] = i * deltaFreq;
      Complex val1 = fftResult1[i];
      Complex val2 = val1;
      if (!sameData) {
        val2 = fftResult2[i];
      }
      
      powSpectDens[i] = 
          powSpectDens[i].add( val1.multiply( val2.conjugate() ) );
    }
    
    return new FFTResult(powSpectDens, frequencies);
    
  }
  
  final private Complex[] transform; // the FFT data
  
  final private double[] freqs; // array of frequencies matching the fft data
  
  /**
   * Instantiate the structure holding an FFT and its frequency range
   * (Used to return data from the spectral density calculations)
   * Holds results of an FFT calculation already performed, usable in return
   * statements
   * @param inPSD Precalculated FFT result for some timeseries
   * @param inFreq Frequencies matched up to each FFT value
   */
  public FFTResult(Complex[] inPSD, double[] inFreq) {
    transform = inPSD;
    freqs = inFreq;
  }

  /**
   * Get the FFT for some sort of previously calculated data
   * @return Array of FFT results, as complex numbers
   */
  public Complex[] getFFT() {
    return transform;
  }
  
  /**
   * Return the value of the FFT at the given index
   * @param idx Index to get the FFT value at
   * @return FFT value at index
   */
  public Complex getFFT(int idx) {
    return transform[idx];
  }
  
  /**
   * Get the frequency value at the given index
   * @param idx Index to get the frequency value at
   * @return Frequency value at index
   */
  public double getFreq(int idx) {
    return freqs[idx];
  }
  
  /**
   * Get the frequency range for the (previously calculated) FFT
   * @return Array of frequencies (doubles), matching index to each FFT point
   */
  public double[] getFreqs() {
    return freqs;
  }
  
  /**
   * Get the size of the complex array of FFT values, also the size of the
   * double array of frequencies for the FFT at each index
   * @return int representing size of thi's object's arrays
   */
  public int size() {
    return transform.length;
  }
  
}
