package asl.sensor.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.complex.Complex;

/**
 * Class containing methods to serve as math functions, mainly for angle calcs.
 * @author akearns
 *
 */
public class NumericUtils {

  /**
   * Complex comparator that takes ordering by magnitude of the values
   * Used to sort response pole values mainly for use in calibrations
   * @author akearns
   *
   */
  public static class CpxMagComparator implements Comparator<Complex> {
    
    public static final CpxMagComparator instance = new CpxMagComparator();
    
    private CpxMagComparator() {
      
    }
    
    @Override
    public int compare(Complex c1, Complex c2) {
      if( c1.abs() == c2.abs() ) {
        Double r1 = c1.getReal();
        Double r2 = c2.getReal();
        if (r1 != r2) {
          return r1.compareTo(r2);
        } else {
          return (int) Math.signum( c1.getImaginary() - c2.getImaginary() );          
        }
      }
      return (int) Math.signum( c1.abs() - c2.abs() );
    }
    
  }
  
  /**
   * Complex comparator ordering by magnitude but listing all real-values first
   * And then sorting complex numbers according to real value first, and then
   * by imaginary value if the real values match.
   * @author akearns
   *
   */
  public static class CpxRealComparator implements Comparator<Complex> {
    public static final CpxRealComparator instance = new CpxRealComparator();
    
    private CpxRealComparator() {
      
    }
    
    @Override
    public int compare(Complex c1, Complex c2) {
      
      if( c1.getImaginary() == 0. && c2.getImaginary() == 0. ) {
        return (int) Math.signum( c1.getReal() - c2.getReal() );
      } else if ( c1.getImaginary() == 0. ) {
        return -1;
      } else if ( c2.getImaginary() == 0. ) {
        return 1;
      } else {
        if( c1.getReal() == c2.getReal() ) {
          return (int) Math.signum( c1.getImaginary() - c2.getImaginary() );
        } else {
          return (int) Math.signum( c1.getReal() - c2.getReal() );
        }
      }
    }
  }
  
  public static CpxMagComparator cmc;
  
  /**
   * 2 * Pi, sometimes also referred to as Tau. 
   * The number of radians in a full circle.
   */
  public final static double TAU = Math.PI * 2; // radians in full circle
  
  /**
   * Get the two-component arctan of a complex number. A simpler way of calling
   * arctan2 using the real and imaginary components of a complex number
   * (see Math.atan2 for more details). This is the angle of the complex number
   * along the positive real axis.
   * @param c Complex number to get atan value of
   * @return atan, between -pi and pi
   */
  public static double atanc(Complex c) {
    
    final double CUTOFF = 1./1000.;
    
    if ( c.abs() < CUTOFF) {
      return 0.;
    }
    
    return Math.atan2( c.getImaginary(), c.getReal() );
  }
  
  /**
   * Sort a list of complex values according to their magnitude. Used to 
   * sort poles and zeros according to their period, which is a function of
   * the magnitude.
   * @param complexes List of complex numbers to sort
   */
  public static void complexMagnitudeSorter(List<Complex> complexes) {
    Collections.sort(complexes, CpxMagComparator.instance);
  }
  
  /**
   * Sort a list of complex values so that pure real numbers appear first.
   * The real and complex numbers are then each sorted according to their real
   * value, and then by their imaginary value.
   * @param complexes
   */
  public static void complexRealsFirstSorter(List<Complex> complexes) {
    Collections.sort(complexes, CpxRealComparator.instance);
  }
  
  /**
   * Sets decimalformat object so that infinity can be printed in a PDF document
   * @param df DecimalFormat object to change the infinity symbol value of
   */
  public static void setInfinityPrintable(DecimalFormat df) {
    DecimalFormatSymbols symbols = df.getDecimalFormatSymbols();
    symbols.setInfinity("Inf.");
    df.setDecimalFormatSymbols(symbols);
  }
  
  /**
   * Given a plot of data within a 2 * Pi range, check that a point is
   * continuous with a previous value.
   * @param phi Angle to fit within range of previous value (radians)
   * @param prevPhi Angle to check discontinuity against (radians)
   * @return New angle, with distance < Pi rad from the previous value
   */
  public static double unwrap(double phi, double prevPhi) {
    // sets range to [0,TAU], this syntax used because Java mod is weird
    double newPhi = ( (phi % TAU) + TAU) % TAU;
    
    while ( Math.abs(prevPhi - newPhi) > Math.PI ) {
      if (prevPhi < newPhi) {
        newPhi -= TAU;
      } else {
        newPhi += TAU;
      }
    }
    
    return newPhi;
  }
  
  /**
   * Given a list of doubles representing the curve of a function with output
   * in radians over a range of 2 * pi, create a new curve that removes any
   * discontinuities in the plot. The starting point will be set to be as close
   * to zero as possible.
   * @param angles Array of input angles to make continuous (radians)
   * @return New array of angles where each pair of continuous points is
   * within distance < Pi rad from the previous value
   */
  public static double[] unwrapList(double[] angles) {
    double[] out = new double[angles.length];
    double prevPhi = 0.;
    
    for (int i = 0; i < out.length; ++i) {
      out[i] = unwrap(angles[i], prevPhi);
      prevPhi = out[i];
    }
    
    return out;
  }
  
}


