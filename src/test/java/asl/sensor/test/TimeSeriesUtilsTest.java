package asl.sensor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.apache.commons.math3.util.Pair;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

import asl.sensor.gui.InputPanel;
import asl.sensor.input.DataBlock;
import asl.sensor.utils.ReportingUtils;
import asl.sensor.utils.TimeSeriesUtils;
import edu.iris.dmc.seedcodec.B1000Types;
import edu.iris.dmc.seedcodec.CodecException;
import edu.iris.dmc.seedcodec.DecompressedData;
import edu.iris.dmc.seedcodec.UnsupportedCompressionType;
import edu.sc.seis.seisFile.mseed.DataHeader;
import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedFormatException;
import edu.sc.seis.seisFile.mseed.SeedRecord;

public class TimeSeriesUtilsTest {

  public String station = "TST5";
  public String location = "00";
  public String channel = "BH0";
  
  public String fileID = station+"_"+location+"_"+channel;
  
  public String filename1 = "./test-data/blocktrim/"+fileID+".512.seed";
  
  @Test
  public void canGetFile() {
    try{
      FileInputStream fis = new FileInputStream(filename1);
      fis.close();
    } catch (Exception e) {
      assertNull(e);
    }
  }
  
  @Test
  public void dumbDivisionTest() {
    int div = 12;
    double num = 1.44;
    double res = num / div;
    assertEquals(0.12, res, 1E-10);
  }
  
  //@Test
  public void testDataLocally() {
    String fname = "./data/gitignoreme/HF_MAJO_10_EHZ.512.cut.seed";
    try {
      DataBlock db = TimeSeriesUtils.getFirstTimeSeries(fname);
      Map<Long, double[]> map = db.getDataMap();
      System.out.println(db.getName());
      System.out.println("\tSample interval: "+db.getInterval());
      for (long time : map.keySet()) {
        System.out.println("START: " + time); 
        System.out.println("\tLENGTH: " + map.get(time).length);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      fail();
    }
  }
  
  @Test
  public void canGetMultiplexDataNames() {
    String filename2 = "./test-data/multiplex/cat.seed";
    Set<String> names;
    
    try {
      names = TimeSeriesUtils.getMplexNameSet(filename2);
      assertTrue(names.contains("IU_ANMO_00_LH1"));
      assertTrue(names.contains("IU_ANMO_00_LH2"));
      assertTrue(names.contains("IU_ANMO_00_LHZ"));
      assertEquals( names.size(), 3 );
    } catch (FileNotFoundException e) {
      fail();
    }
  }
  
  @Test
  public void decimationTest() {
    
    long interval40Hz = (TimeSeriesUtils.ONE_HZ_INTERVAL / 40);
    long interval = TimeSeriesUtils.ONE_HZ_INTERVAL;
    
    double[] timeSeries = new double[160];
    
    for (int i = 0; i < 160; ++i) {
      timeSeries[i] = i;
    }
    
    // System.out.println(timeSeries);
    
    timeSeries = TimeSeriesUtils.decimate(timeSeries, interval40Hz, interval);
    
    // System.out.println(timeSeries);
    
    assertEquals(timeSeries.length, 4);
    for (int i = 0; i < timeSeries.length; ++i) {
      assertEquals(timeSeries[i], 40. * i, 0.5);
    }
  }
  
  @Test
  public void demeaningTest() {
    
    // tests that demean does what it says it does and that
    // the results are applied in-place
    
    double[] numbers = {1,2,3,4,5};
    
    double[] numList = numbers.clone();
    double[] demeaned = numList.clone();
    
    TimeSeriesUtils.demeanInPlace(demeaned);
    
    for (int i = 0; i < numList.length; ++i) {
      assertEquals(demeaned[i], numList[i]-3, 1E-15);
    }
    
  }
  
  @Test
  public final void testDemean1to9() throws Exception {
    double[] x = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    double[] expected = { -4d, -3d, -2d, -1d, 0d, 1d, 2d, 3d, 4d };
    TimeSeriesUtils.demeanInPlace(x);
    for (int i = 0; i < x.length; i++) {
      assertEquals(x[i], expected[i], 1E-15);
    }
  }
  
  @Test
  public void detrendingCycleTest() {
    
    double[] x = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 
        18, 19, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 
        3, 2, 1 };
    
    // List<Number> toDetrend = Arrays.asList(x);
    
    double[] answer = { -9d, -8d, -7d, -6d, -5d, -4d, -3d, -2d, -1d, 0d, 1d, 2d,
        3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 9d, 8d, 7d, 6d, 5d, 4d, 3d, 2d, 1d, 0d,
        -1d, -2d, -3d, -4d, -5d, -6d, -7d, -8d, -9d };

    
    x = TimeSeriesUtils.detrend(x);
    
    for (int i = 0; i < x.length; i++) {
      assertEquals( x[i],  answer[i], 0.5);
    }
    
  }
  
  @Test
  public void detrendingLinearTest() {
    
    Number[] x = { 1, 2, 3, 4, 5, 6, 7, 8, 9};
    
    List<Number> toDetrend = Arrays.asList(x);
    TimeSeriesUtils.detrend(toDetrend);
    
    for (Number num : toDetrend) {
      assertEquals(num.doubleValue(), 0.0, 0.001);
    }
    
  }
  
  @Test
  public final void testDetrendLinear2() throws Exception {
    double[] x = { -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 
        7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };

    x = TimeSeriesUtils.detrend(x);
    for (int i = 0; i < x.length; i++) {
      assertEquals(new Double(Math.round(x[i])), new Double(0));
    }
  }
  
  public void 
  doInputParseTest(String dataFolderName, String extension, String testID) {

    String fileName =  dataFolderName + extension;
    String metaName;
    try {
      metaName = TimeSeriesUtils.getMplexNameList(fileName).get(0);
      Pair<Long, Map<Long, double[]>> data = 
          TimeSeriesUtils.getTimeSeriesMap(fileName, metaName);
      Map<Long, double[]> map = data.getSecond();
      
      List<Long> times = new ArrayList<Long>( map.keySet() );
      Collections.sort(times);
      long lastTime = times.get( times.size() - 1 );
      
      StringBuilder sb = new StringBuilder();
      for (Long time : times) {
        sb.append(time);
        sb.append(": ");
        sb.append( map.get(time) );
        if (time < lastTime) {
          sb.append("\n");
        }
      }
      
      String folderName = "testResultImages";
      File folder = new File(folderName);
      if ( !folder.exists() ) {
        System.out.println("Writing directory " + folderName);
        folder.mkdirs();
      }
      
      String outputFilename = 
          folderName + "/outputData"+testID+"TimeDataMap.txt";
      PrintWriter write;
      write = new PrintWriter(outputFilename);
      write.println( sb.toString() );
      write.close();
      
    } catch (FileNotFoundException e) {
      fail();
      e.printStackTrace();
    }
  }
  
  @Test
  public void firstSampleCorrect() {
    String fname = "./test-data/random_cal_lowfrq/BHZ.512.seed";
    try {
      String data = TimeSeriesUtils.getMplexNameList(fname).get(0);
      DataBlock db = TimeSeriesUtils.getTimeSeries(fname, data);
      long start = db.getStartTime();
      Map<Long, double[]> timeseries = db.getDataMap();
      double[] firstContiguous = timeseries.get(start);
      long sum = 0;
      for (Number n : firstContiguous) {
        sum += n.longValue();
      }
      assertEquals(2902991374L, sum);
      assertEquals(1652432, firstContiguous.length);
      // System.out.println(timeseries.get(start)[0]);
      
    } catch (FileNotFoundException e) {
      fail();
      e.printStackTrace();
    }
  }
  
  @Test
  public void firstSampleCorrect2() {
    String fname = "./test-data/random_cal_lowfrq/BC0.512.seed";
    try {
      String data = TimeSeriesUtils.getMplexNameList(fname).get(0);
      DataBlock db = TimeSeriesUtils.getTimeSeries(fname, data);
      long start = db.getStartTime();
      Map<Long, double[]> timeseries = db.getDataMap();
      double[] firstContiguous = timeseries.get(start);
      assertEquals(561682, firstContiguous.length);
      int halfLen = firstContiguous.length / 2;
      firstContiguous = Arrays.copyOfRange(firstContiguous, 0, halfLen);
      long sum = 0;
      for (Number n : firstContiguous) {
        sum += n.longValue();
      }
      assertEquals(707752187L, sum);
      Calendar cCal = db.getStartCalendar();
      
      String correctDate = "2017.08.02 | 00:00:00.019";
      SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd | HH:mm:ss.SSS");
      sdf.setTimeZone( TimeZone.getTimeZone("UTC") );
      String inputDate = sdf.format( cCal.getTime() );
      assertEquals(inputDate, correctDate);
      assertEquals(20.0, db.getSampleRate(), 1E-20);
      // System.out.println(timeseries.get(start)[0]);
      
    } catch (FileNotFoundException e) {
      fail();
      e.printStackTrace();
    }
  }
  
  public Calendar getStartCalendar(long time) {
    SimpleDateFormat sdf = InputPanel.SDF;
    sdf.setTimeZone( TimeZone.getTimeZone("UTC") );
    Calendar cCal = Calendar.getInstance( sdf.getTimeZone() );

    cCal.setTimeInMillis(time);
    return cCal;
  }
  
  @Test
  public void inputFileReaderCreatesXYSeries() {
    DataInput dis;
    List<Number> data = new ArrayList<Number>();
    
    try {
      dis = new DataInputStream( 
            new BufferedInputStream( 
            new FileInputStream(filename1) ) );
      while ( true ) {

        try {
          long interval = 0L;
          SeedRecord sr = SeedRecord.read(dis,4096);
          if(sr instanceof DataRecord) {
            DataRecord dr = (DataRecord)sr;
            DataHeader dh = dr.getHeader();
           
            DecompressedData decomp = dr.decompress();

            // get the original datatype of the series (loads data faster)
            // otherwise the decompressed data gets converted (cloned) as
            // the other type instead
            int dataType = decomp.getType();

            // This is probably the best way to do this since
            // we have to add each point individually and type convert anyway

            switch (dataType) {
            case B1000Types.INTEGER:
              int[] decomArrayInt = decomp.getAsInt();
              for (int dataPoint : decomArrayInt ) {
                data.add(dataPoint);
              }
              break;
            case B1000Types.FLOAT:
              float[] decomArrayFlt = decomp.getAsFloat();
              for (float dataPoint : decomArrayFlt ) {
                data.add(dataPoint);
              }
              break;
            case B1000Types.SHORT:
              short[] decomArrayShr = decomp.getAsShort();
              for (short dataPoint : decomArrayShr ) {
                data.add(dataPoint);
              }
              break;
            default:
              double[] decomArrayDbl = decomp.getAsDouble();
              for (double dataPoint : decomArrayDbl ) {
                data.add(dataPoint);
              }
              break;
            }
          }
        } catch(EOFException e) {
          break;
        }
        
      }
      
      // quickly get the one name in the list
      Set<String> names = TimeSeriesUtils.getMplexNameSet(filename1);
      List<String> nameList = new ArrayList<String>(names);
      System.out.println("DATA BLOCK SIZE: " + data.size());
      
      DataBlock testAgainst = 
          TimeSeriesUtils.getTimeSeries(filename1, nameList.get(0) );
      assertEquals( data.size(), testAgainst.getData().length );
      
    } catch (FileNotFoundException e) {
      assertNull(e);
    } catch (SeedFormatException e) {
      assertNull(e);
    } catch (IOException e) {
      assertNull(e);
    } catch (UnsupportedCompressionType e) {
      assertNull(e);
    } catch (CodecException e) {
      assertNull(e);
    }
  }
  
  @Test
  public void producePlotReadIn() {
    try {
      String calname = "./test-data/random_cal_lowfrq/BC0.512.seed";
      String outname = "./test-data/random_cal_lowfrq/BHZ.512.seed";
      String calMplex = TimeSeriesUtils.getMplexNameList(calname).get(0);
      String outMplex = TimeSeriesUtils.getMplexNameList(outname).get(0);
      DataBlock cal = TimeSeriesUtils.getTimeSeries(calname, calMplex);
      DataBlock out = TimeSeriesUtils.getTimeSeries(outname, outMplex);
      long start = Math.max(cal.getStartTime(), out.getStartTime());
      long end = Math.min(cal.getEndTime(), out.getEndTime());
      cal.trim(start, end);
      out.trim(start, end);
      
      XYSeriesCollection xysc = new XYSeriesCollection();
      xysc.addSeries( cal.toXYSeries() );
      xysc.addSeries( out.toXYSeries() );
      
      JFreeChart chart = ChartFactory.createXYLineChart(
          "MAJO calibration and output data",
          "Time",
          "Signal data (counts)",
          xysc,
          PlotOrientation.VERTICAL,
          true,
          false,
          false);
      
      int width = 1280; int height = 960;
      PDDocument pdf = new PDDocument();
      ReportingUtils.chartsToPDFPage(width, height, pdf, chart);
      
      String currentDir = System.getProperty("user.dir");
      String testResultFolder = currentDir + "/testResultImages/";
      File dir = new File(testResultFolder);
      if ( !dir.exists() ) {
        dir.mkdir();
      }
      String testResult = 
          testResultFolder + "Time-Series_MAJO.pdf";
      pdf.save( new File(testResult) );
      pdf.close();
      
    } catch (IOException e) {
      fail();
      e.printStackTrace();
    }

    
    
  }
  
  @Test
  public void seisFileCanParseFile() {
    
    try {
      DataInput dis = new DataInputStream( new BufferedInputStream( 
          new FileInputStream(filename1) ) ); 
      try{
        while(true) {
          SeedRecord sr = SeedRecord.read(dis,4096);
          if (sr instanceof DataRecord) {
            DataRecord dr = (DataRecord)sr;

            String loc = dr.getHeader().getLocationIdentifier();
            assertTrue( loc.equals(location) );
            String stat = dr.getHeader().getStationIdentifier().trim();
            assertTrue( stat.equals(station) );

            String chan = dr.getHeader().getChannelIdentifier();
            assertTrue( chan.equals(channel) );
          }
        }
      } catch (EOFException e) {
        assertNotNull(e); // I haaates it! I haaaaaaaaaates it!
      } catch (SeedFormatException e) {
        assertNull(e);
      } catch (IOException e) {
        assertNull(e);
      }
      
    } catch (FileNotFoundException e) {
      assertNull(e);
    }
    
  }
  
  @Test
  public void seisFileGivesCorrectSampleRateAndInterval() {
    DataInput dis;
    try {
      while (true) {
        
        dis = new DataInputStream( new BufferedInputStream( 
            new FileInputStream(filename1) ) );
        SeedRecord sr = SeedRecord.read(dis,4096);
        if(sr instanceof DataRecord) {
          DataRecord dr = (DataRecord)sr;
          
          int fact = dr.getHeader().getSampleRateFactor();
          int mult = dr.getHeader().getSampleRateMultiplier();
          
          //System.out.println(fact+","+mult);
          
          double rate = dr.getHeader().getSampleRate();
          assertTrue((double)fact/mult == rate);
          
          // checking the correct values for the intervals
          
          double multOf1Hz = rate/TimeSeriesUtils.ONE_HZ;
          long inverse = TimeSeriesUtils.ONE_HZ_INTERVAL/(long)multOf1Hz;
          
          long interval = TimeSeriesUtils.ONE_HZ_INTERVAL*mult/fact;
          
          assertEquals( inverse, interval);
          // System.out.println(interval);
          
          break;
          
        }
      }
    } catch (FileNotFoundException e) {
      assertNull(e); // only reading one record;
    } catch (SeedFormatException e) {
      assertNull(e);
    } catch (IOException e) {
      assertNull(e);
    }
  }
  
  public void showsTimeSeries(String fname, String annot) {

    try {
      String data = TimeSeriesUtils.getMplexNameList(fname).get(0);
      DataBlock db = TimeSeriesUtils.getTimeSeries(fname, data);
      Map<Long, double[]> timeseries = db.getDataMap();
      List<Long> timeList = new ArrayList<Long>( timeseries.keySet() );
      Collections.sort(timeList);
      StringBuilder sb = new StringBuilder();
      for ( Long time : timeList ) {
        
        Calendar cCal = getStartCalendar(time);
        SimpleDateFormat sdf = 
            new SimpleDateFormat("YYYY.MM.dd | HH:mm:ss.SSS");
        sdf.setTimeZone( TimeZone.getTimeZone("UTC") );
        String inputDate = sdf.format( cCal.getTime() );
        
        double[] contiguous = timeseries.get(time);
        // long tstamp = time / 1000;
        sb.append("Contiguous block of data at " + inputDate + ":\n");
        for (Number n : contiguous) {
          sb.append("\t"+n+"\n");
        }
        sb.append("\n");
      }
      
      String currentDir = System.getProperty("user.dir");
      String testResultFolder = currentDir + "/testResultImages/";
      File dir = new File(testResultFolder);
      if ( !dir.exists() ) {
        dir.mkdir();
      }
      String testResult = 
          testResultFolder + "Time-Series_MAJO_" + annot + ".txt";
      
      PrintWriter out = new PrintWriter(testResult);
      out.println( sb.toString() );
      out.close();
      
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      fail();
    }
  }
  
  @Test
  public void showsTimeSeriesCal() {
    String fname = "./test-data/random_cal_lowfrq/BC0.512.seed";
    showsTimeSeries(fname, "BC0");
  }
  
  @Test
  public void showsTimeSeriesOut() {
    String fname = "./test-data/random_cal_lowfrq/BHZ.512.seed";
    showsTimeSeries(fname, "BHZ");
  }
  
  @Test
  public void testGapPadding() {
   
   // TODO: re-write test to deal with data structure change 
    
  }
  
  @Test
  public void testInputParsing1() {
    String dataFolderName = "test-data/random_cal/"; 
    String extension = "_EC0.512.seed";    
    String testID = "1_Cal";
    doInputParseTest(dataFolderName, extension, testID);
    extension = "00_EHZ.512.seed";
    testID = "1_Out";
    doInputParseTest(dataFolderName, extension, testID);
  }

  @Test
  public void testInputParsing4() {
    String dataFolderName = "test-data/random_cal_4/"; 
    String extension = "CB_BC0.512.seed";
    String testID = "4_Cal";
    doInputParseTest(dataFolderName, extension, testID);
    extension = "00_EHZ.512.seed";
    testID = "4_Out";
    doInputParseTest(dataFolderName, extension, testID);
  }
  
  

  @Test
  public void timeDataCorrect() {
    String fname = "./test-data/random_cal_lowfrq/BHZ.512.seed";
    try {
      String data = TimeSeriesUtils.getMplexNameList(fname).get(0);
      DataBlock db = TimeSeriesUtils.getTimeSeries(fname, data);
      
      Map<Long, double[]> dataMap = db.getDataMap();
      List<Long> regions = new ArrayList<Long>( dataMap.keySet() );
      Collections.sort(regions);
      for (int i = 0; i < regions.size(); ++i) {
        long time = regions.get(i);
        System.out.println(dataMap.get(time).length);
      }
      
      long start = db.getStartTime() / TimeSeriesUtils.TIME_FACTOR;
      Calendar cCal = getStartCalendar(start);
      SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd | HH:mm:ss.SSS");
      sdf.setTimeZone( TimeZone.getTimeZone("UTC") );
      String inputDate = sdf.format( cCal.getTime() );
      // System.out.println(inputDate);
      String correctDate = "2017.08.02 | 00:00:00.019";
      assertEquals(inputDate, correctDate);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

}
