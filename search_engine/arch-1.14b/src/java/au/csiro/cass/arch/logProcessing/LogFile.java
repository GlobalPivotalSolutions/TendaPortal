/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.logProcessing;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import org.apache.commons.vfs2.FileObject;
import org.apache.hadoop.fs.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Models a log file
 * 
 * @author Arkadi Kosmynin
 *
 */
public class LogFile
{
 public static final Logger LOG = LoggerFactory.getLogger( LogFile.class ) ;

 // Stored in the database
 String          fileName ; // file name of the log file
 Date           timeStart ; // time of the first record
 Date             timeEnd ; // time of the end record
 Date          oldTimeEnd ; // timeEnd at the time of previous processing 
 Date        timeModified ; // file last modification date
 boolean       usedForIPs ; // true if used to make list of ignored IPs
 // Not stored in the database
 String          tempName ; // name of the cached unpacked file
 FileObject          file ;
 LogSite             site ; // the site the log belongs to
 BufferedReader        br ; // input stream
 String       currentLine ; // last read line
 Date         currentDate ; // date of the last read line
 long                size ; // file size
 long             oldSize ; // size at the time of previous processing
 boolean           ignore ; // this log is to be ignored (a duplicate?)
 boolean           cached ;
  
  
 /**
 * LogFile factory. Creates a new LogFile instance, sets dates of the first
 * and last records.
 * 
 * @param fileName log file name
 * @param site  a reference to parent LogSite object
 * @return a new LogFile object
 * @throws Exception 
 *
 */
 public static LogFile newLogFile( FileObject fo, LogSite site, boolean preprocessing ) throws Exception
 {
  LogFile logFile = new LogFile() ;
  logFile.site = site ;
  logFile.file = fo ;
  logFile.fileName = fo.getName().getURI() ;
  LOG.info( "Log file: " + logFile.fileName ) ;

  // check if this is not a known file
  if ( !site.logProcessor.db.readLogFile( logFile, site.name ) ||
       logFile.timeModified.getTime() != fo.getContent().getLastModifiedTime() )
  {
    LOG.info( "      New log file: " + logFile.fileName ) ;
    logFile.oldSize = logFile.size ;
    logFile.oldTimeEnd = logFile.timeEnd ;
    logFile.timeModified = new Date( fo.getContent().getLastModifiedTime() ) ;
    logFile.open() ;
    if ( !logFile.getNextLine() ) logFile.ignore = true ;
         else logFile.timeStart = site.parser.getDate() ;
                    
    if ( !logFile.ignore )
        if ( !logFile.getLastLine() ) logFile.timeEnd = logFile.timeModified ;
          else logFile.timeEnd = site.parser.getDate() ;
      else logFile.timeEnd = logFile.timeModified ;
    logFile.setUsedForIPs( false ) ;
    logFile.close();
    if ( logFile.ignore == true )
       {
    	 LOG.info( "Ignoring log file " + logFile.fileName ) ;
         System.out.println( "      New log file " + logFile.fileName + " ignored.") ;
       } else System.out.println( "      New log file " + logFile.fileName + " from " 
    		   + logFile.timeStart + " to " + logFile.timeEnd ) ;

  }
    else
  { 
     logFile.oldSize = logFile.size ;
     logFile.oldTimeEnd = logFile.timeEnd ;
     if ( site.logProcessor.isIgnoredIPsEmpty() ) logFile.setUsedForIPs( false ) ;
     System.out.println( "      Known log file " + logFile.fileName ) ;
     if ( preprocessing ) logFile.ignore = true ; // It has been merged into a merged file already
  }
  return logFile ;
 }
 
 /**
 * Saves db stored info of this LogFile to the database
 * 
 * @throws Exception 
 *
 */
 public void save() throws Exception
 {
   timeModified = new Date( file.getContent().getLastModifiedTime() ) ;
   site.logProcessor.db.saveLogFile( this, site.name ) ;
 }
  
 /**
  * Deletes the log file
  * 
  * @throws Exception 
  *
  */
  public void delete() throws Exception
  {
 //   File f = new File( fileName ) ;
 //   FileUtil.fullyDelete( f ) ;
    file.delete() ; // FileObject delete
  }
   
 /**
 * Opens the log file, unpacks if needed and prepares for reading
 * 
 * @throws Exception 
 *
 */
 void open() throws Exception
 {
  File f = null ;
  if ( !cached )
  {
    // If need to get a local unpacked copy first
    if ( !fileName.startsWith( "file://" ) )
    {
  	 cached = true ;
	 tempName = "file://" + site.logProcessor.workDir + "/" + site.name + "_" + new Date().getTime() 
	                      + "_log" + (++site.cacheCounter) + ".txt" ;
 	 if ( fileName.endsWith( ".gz" ) ) tempName += ".gz" ;
	
	 FileObject dest = site.fsManager.resolveFile( tempName ) ;
	 dest.createFile() ;
	 OutputStream fos = dest.getContent().getOutputStream() ;
	 InputStream is = file.getContent().getInputStream() ;
	 tempName = dest.getName().getPath() ;

	 byte[] buf = new byte[ 10240 ] ;
	 for ( int readNum ; (readNum = is.read( buf ) ) != -1 ; )
	     fos.write( buf, 0, readNum ) ;
	 fos.close() ;
	 is.close() ;
    } else { tempName = file.getName().getPath() ; cached = false ; }
    for ( int i = 0 ; i < 1000 ; i++ )
     { f = new File( tempName ) ;
       if ( f.exists() ) break ; 
       Thread.sleep( 100 ) ; // give the OS time to refresh file tables
     }
    size = f.length() ;
    if ( !f.exists() )
     if ( cached ) throw new Exception( " Can't download " + fileName ) ;
      else throw new Exception( " Log file " + fileName + " does not exist." ) ;
  } else f = new File( tempName ) ;     
  br = getBufferedReader( f ) ;
 }
 
 
 
 /**
  * Opens a file for reading, creating a proper reader for gzipped or text files.
  * Expects the file to be in UTF-8 encoding.
  * 
  * @param f
  * @return
  */
 static BufferedReader getBufferedReader( File f )
 {
   BufferedReader buffered = null ;
   try 
   {
     InputStream fileStream = new FileInputStream( f.getAbsolutePath() ) ;
     DataInputStream inputStream = null ;
     if ( f.getName().endsWith( ".gz" ) )
      {
       InputStream gzipStream = new GZIPInputStream( fileStream ) ;
       inputStream = new DataInputStream( gzipStream ) ;
      }
      else inputStream = new DataInputStream( fileStream ) ;
      Reader reader = new InputStreamReader( inputStream, "UTF-8" ) ;
      buffered = new BufferedReader( reader ) ;
   }
   catch( Exception e )
   {
     LOG.error( "Can't open file " + f.getAbsolutePath() + " for reading: " + e.getMessage() );
   }
   return buffered ;
 }

 
 /**
 * Closes the log file, removes it if needed 
 * 
 * @throws Exception 
 *
 */
 void close() throws Exception
 {
    try { br.close() ; } catch( Exception e ) {}
    br = null ;
    if ( !site.cacheLogs && cached ) 
    { 
      File ff = new File( tempName ) ;
      FileUtil.fullyDelete( ff ) ;
      cached = false ;
    }
 }

 
 /**
  * Skips a number of characters  
  * 
  * @param long num - number of characters to skip
  * @throws Exception 
  * @return long number of characters skipped
  *
  */
  long skip( long num ) throws Exception
  {
     return br.skip( num ) ;
  }

 
 
 
 void removeFromCache() throws Exception 
 {
  if ( !cached ) return ;
  File ff = new File( tempName ) ;
  if ( ff.exists()  ) FileUtil.fullyDelete( ff ) ;
 }

 
  
 /**
  * Returns next 'good' line that relates to a text file and not 
  * produced by a source with blacklisted IP address
  * 
  * @return LogLine object or null 
  * @throws Exception 
  *
  */
 boolean getNextGoodLine() throws Exception 
 {
   if ( br == null )
                 open() ;
   
   if ( br == null ) return false ;
   boolean result ;
   do { 
        String currentLine = br.readLine() ; 
        if ( currentLine == null ) return false ;
        result = site.parser.parse( currentLine ) ;
        currentDate = site.parser.getDate() ;
      } while ( !result ) ;
   return result ;
 }
   
 /**
  * Returns next line with a valid date
  * 
  * @return LogLine object or null 
  * @throws Exception 
  *
  */
 boolean getNextLine() throws Exception 
 {
   if ( br == null )
                open() ;
   
   if ( br == null ) return false ;
   boolean result ;
   do { 
        currentLine = br.readLine() ; 
        if ( currentLine == null ) return false ;
        result = site.parser.parse( currentLine, true ) ;
        currentDate = site.parser.getDate() ;
      } while ( !result ) ;
   return result ;
 }
   
 /**
 * Returns last line with a valid date
 * 
 * @return LogLine object or null 
 * @throws Exception 
 *
 */
 boolean getLastLine() throws Exception 
 {
  if ( tempName.endsWith( ".gz" ) ) // compressed file, can't do
                           return false ;
  // read the last 10K of the file
  RandomAccessFile f = new RandomAccessFile( tempName, "r" ) ;
  long length = f.length() ;
  if ( length > 10000 ) length = 10000 ;
  f.seek( f.length() - length ) ;
  byte[] arr = new byte[ (int)length ] ;
  f.readFully(  arr  ) ;
  f.close() ;
  String str = new String( arr ) ;
  String[] lines = str.split( "\n" ) ;
  
  for ( int i = lines.length - 1  ; i > 0 ; i-- )
   {
     String ln = lines[ i ].trim() ;
     site.parser.parse( ln ) ;
     if ( site.parser.parseDate( ln ) ) return true ; 
   }
  // Only care for a date because there may be no 'good' line in the last few
  return false ;
 }

 // Getters and setters below
 
 public String getFileName()
  {
   return fileName;
  }

 public void setFileName( String fileName )
  {
   this.fileName = fileName;
  }

 public Date getTimeEnd()
  {
   return timeEnd;
  }

 public void setTimeEnd( Date timeEnd )
  {
   this.timeEnd = timeEnd;
  }

 public Date getTimeModified()
  {
   return timeModified;
  }

 public void setTimeModified( Date timeModified )
  {
   this.timeModified = timeModified;
  }

 public Date getTimeStart()
  {
   return timeStart;
  }

 public void setTimeStart( Date timeStart )
  {
   this.timeStart = timeStart;
  }

 public boolean isUsedForIPs()
  {
   return usedForIPs;
  }

 public void setUsedForIPs( boolean usedForIPs )
  {
   this.usedForIPs = usedForIPs;
  }

/**
 * @return the size
 */
public long getSize() {
	return size;
}

/**
 * @param size the size to set
 */
public void setSize(long size) {
	this.size = size;
}

/**
 * @return the oldTimeEnd
 */
public Date getOldTimeEnd() {
	return oldTimeEnd;
}

/**
 * @param oldTimeEnd the oldTimeEnd to set
 */
public void setOldTimeEnd(Date oldTimeEnd) {
	this.oldTimeEnd = oldTimeEnd;
}

/**
 * @return the oldSize
 */
public long getOldSize() {
	return oldSize;
}

/**
 * @param oldSize the oldSize to set
 */
public void setOldSize(long oldSize) {
	this.oldSize = oldSize;
}
   
}


