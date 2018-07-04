/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A utility class for reading of forked process output
 * 
 * @author Arkadi Kosmynin
 *
 */
public class OutputReader extends Thread
{
  InputStream is ;
    
  OutputReader( InputStream is )
  {
    this.is = is;
  }
    
  public void run()
  {
   try
   {                
    InputStreamReader isr = new InputStreamReader( is ) ;
    BufferedReader br = new BufferedReader( isr ) ;
    String line = null ;
    while ( (line = br.readLine()) != null)
     {
      System.out.println( line ) ;
      this.sleep( 10 ) ;
     }
   } catch ( IOException ioe )
     {
       ioe.printStackTrace() ;  
     }
     catch ( InterruptedException e ) {} 
   }
}
