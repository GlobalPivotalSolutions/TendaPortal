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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.AreaFilter;
import au.csiro.cass.arch.index.AreaMatcher;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.URLSplit;
import au.csiro.cass.arch.utils.Utils;

/**
 * Implements web site log processing model and functions
 * 
 * @author Arkadi Kosmynin
 *
 */
public class LogSite
{
   public static final Logger LOG = LoggerFactory.getLogger( LogSite.class ) ;
   Date                    logsStart ; // time of the first record in available logs
   Date                      logsEnd ; // time of the end record in available logs
   Date                 weightsStart ; // start of processed interval in the db
   Date                   weightsEnd ; // end of old processed interval in the db
   Date                  scoresStart ; // start of newly processed interval
   Date                    scoresEnd ; // end of newly processed interval
   LogProcessor         logProcessor ; // the root object 
   LogFile[]                   logs0 ; // log files found in log repository
   LogFile[]                    logs ; // log files found
   String                        url ; // base url of this site
   String                       name ; // alias
   String                        dir ; // location of this site's data
   String                     logDir ; // location of this site's logs
   String                newURLsFile ; // file to output new found URLs to
   ConfigList                    cfg ; // configuration parameters
   boolean                 filterIPs ; // if false, ip filters will not be used
   IndexSiteDB                    db ; // db interface
   LogLineParser              parser ; // format specific line parser
   long                 cleaningTime ; // last cache cleaning time
   String                 sitemapURL ; // optional URL for pre-processed log stats in sitemap format
   boolean                deleteLogs ; // delete logs after processing
   int                  cacheCounter ; // number of cached file
   public AreaMatcher      URLFilter ; // used to block irrelevant URLs from counting
   
   // Log repository and preprocessing related parameters
   String                    repoDir ; // Location of log repository, if any
   int                     retention ; // Retention of postprocessed logs, days
   boolean                preprocess ; // === (repoDir != null)

   // VFS based logs support
   ArrayList<String[]>      pathMask ;
   ArrayList<String[]>     pathMask0 ;
   FileSystemOptions            opts ;
   FileSystemManager       fsManager ;
   boolean                 cacheLogs ;
   
   // Site cache
   static Map<String, LogSite>  sites = new HashMap<String, LogSite>() ;


  static LogSite getSite( String dirName, LogProcessor logProcessor ) throws Exception
  {
	LogSite site = sites.get( dirName ) ;
	if ( site == null )
	   { 
		site =  LogSite.newLogSite( dirName, logProcessor ) ;
		sites.put( dirName, site ) ;
	   }
	return site ;
  }
/**
 * A factory. Creates and initialises LogSite object.
 * Sets dates of the first and last records.
 *
 * @param dirName   site name and name of directory where site data is kept
 * @param boss      a reference to parent LogProcessor object
 * @param action    processing action to prepare for 
 * @return a new LogSite object
 * @throws Exception
 */
 static LogSite newLogSite( String dirName, LogProcessor logProcessor )
 throws Exception
 {
  LogSite site = new LogSite() ;
  site.logProcessor = logProcessor ;
  site.logsEnd = null ;
  site.logsStart = null ;
  site.name = dirName ;
  site.dir = logProcessor.dataDir + "/sites/" + dirName ;
  site.cfg = ConfigList.newConfigList( site.dir + "/config.txt", logProcessor.cfg ) ;
  if ( !site.cfg.getInherited( "enabled.loglinks", true ) ) return null ;
  site.cacheCounter = 0 ;

  // save configuration to the database
  logProcessor.db.writeConfig( site.name, site.cfg ) ;
  site.logDir = null ;
  if ( new File( site.dir + "/logs" ).exists() ) 
	              site.logDir = "file:///" + site.dir + "/logs" ;
  if ( logProcessor.logDirectory != null ) 
                                      site.logDir = logProcessor.logDirectory ;
  String[] logs = site.cfg.getAll( "logs" ) ;
  site.pathMask = new ArrayList<String[]>() ; 
  site.pathMask0 = new ArrayList<String[]>() ; 
  if ( ( logs == null || logProcessor.logDirectory != null ) && site.logDir != null )
	  site.pathMask.add( site.logDir.trim().split( "\\s*\\|\\s*" ) ) ;
    else if ( logs != null )
           for ( String path : logs )
    	     site.pathMask.add( path.trim().split( "\\s*\\|\\s*" ) ) ;
  // If a log repository is provided, logs will be taken from there, preprocessed and 
  // placed into log directory
  site.repoDir = site.cfg.get(  "log.repository", null ) ;
  if ( site.repoDir != null )
  {
    if ( site.pathMask.size() == 0 )
      throw new Exception( "Log repository is provided, but no log directory to place merged logs to." ) ;
    site.preprocess = true ;
    site.logDir = site.pathMask.get( 0 )[ 0 ] ;
    site.pathMask0.addAll( site.pathMask ) ;
    site.pathMask.clear() ;
    site.pathMask.add( site.repoDir.split( "\\s*\\|\\s*" ) ) ;
    site.retention = site.cfg.get(  "merged.retention", -1 ) ; // unlimited by default
  }
      
  
  site.cacheLogs = site.cfg.getInherited( "cachelogs", true ) ;
  
  site.opts = new FileSystemOptions() ;
  SftpFileSystemConfigBuilder.getInstance( ).setUserDirIsRoot( site.opts, false ) ;
  FtpFileSystemConfigBuilder.getInstance( ).setUserDirIsRoot( site.opts, false ) ;
  FtpsFileSystemConfigBuilder.getInstance( ).setUserDirIsRoot( site.opts, false ) ;
  site.fsManager = VFS.getManager();
  
  site.name = dirName ;
  site.filterIPs = site.cfg.getInherited( "ip.filter", true ) ; 
  LOG.info( "Log site: " + site.name ) ;
  site.url = site.cfg.get( "url", "", "SITE_URL" ) ; 
  if ( site.url.length() == 0 ) Utils.missingParam( "url" ) ;
  if ( !site.url.endsWith( "/" ) ) site.url += "/" ;
  logProcessor.db.regSite( dirName, site.url ) ;
  site.newURLsFile = site.dir + "/newURLs.txt" ;
  site.deleteLogs = site.cfg.getInherited( "delete.logs", false ) ;
  

  LogLineParserFactory factory = LogLineParserFactory.get( logProcessor.nutchConf, logProcessor ) ;
  String logFormat = site.cfg.getInherited( "log.format", "combined" ) ;
  site.parser = factory.get( logFormat ) ;
  site.parser.setLogSite( site ) ;

  DBInterfaceFactory dfactory = DBInterfaceFactory.get( logProcessor.nutchConf ) ;
  String database = site.cfg.getInherited( "database", "MySQL" ) ;
  DBInterface intrf = dfactory.get( database ) ;
  site.URLFilter = AreaMatcher.newAreaMatcher( site.cfg ) ;
    
  site.db = intrf.newIndexSiteDB( site.cfg, site.name, false ) ;
  site.weightsStart = site.getProcessed( "weightsStart" ) ;
  site.weightsEnd = site.getProcessed( "weightsEnd" ) ;
  if ( site.weightsStart != null && site.weightsEnd != null )
  {
	LOG.info("Logs processed from " + site.weightsStart + " to " + site.weightsEnd ) ;  
  } else LOG.info( "No processed interval registered." ) ;
  site.sitemapURL = site.cfg.get( "sitemap.url", null ) ;
  // If logProcessor has it, it means that a single site is being processed via command line, override 
  if ( logProcessor.sitemapURL != null ) site.sitemapURL = logProcessor.sitemapURL ;
    
  site.prepare() ;
  
  return site ;
 }
 
 
 
 void prepare() throws Exception
 {
   if ( preprocess )
   {
     // Read logs from logRepo
     readLogs( true ) ;
     // Merge them to logDir
     mergeLogs() ;
     // Switch site to reading logs from logDir
     logs0 = logs ;
     pathMask = pathMask0 ; 
   }
   
   readLogs( false ) ;
 }
 
 
  /**
  * @throws Exception 
  * 
  */
 private void mergeLogs() throws Exception
 {
  if ( logs == null || logs.length == 0 )
                                    return ;
  int num = logs.length ;
  if ( num == 0 ) return ;
  int bestIndex ;
  LogFile[] a = new LogFile[ num ] ;
  System.arraycopy( logs, 0, a, 0, num ) ;
  SimpleDateFormat format = new SimpleDateFormat( "yyyyMMdd-HHmmss" ) ;
  String mergedName = logDir + "/merged_log_" + format.format( logs[ 0 ].timeStart ) + ".gz" ;
  Writer writer = getGzWriter( mergedName ) ;
  
   
  loop: while( a.length > 0 ) 
  {
    bestIndex = 0 ;      
    String bestLine = currentLine( a[ bestIndex ] ) ;
    if ( bestLine == null ) // one of the logs has been finished - purge it.
       { a = purge( a, 0 ) ; continue loop ; }
    
    for ( int i = 1 ; i < a.length ; i++ )
    {
      if ( a[ i ].timeStart.after( a[ bestIndex ].currentDate ) ) 
        break ; // don't have to look for more, next files are more recent
      String line = currentLine( a[ i ] ) ;
      if ( line == null ) // Start from the beginning - for simplicity, we don't do it often
         { a = purge( a, i ) ; continue loop ; }
      if ( a[ i ].currentDate.before( a[ bestIndex ].currentDate ) )
         { bestIndex = i ; bestLine = a[ bestIndex ].currentLine ; }
    }
    // Now we've found the best line to output 
    // Output it and advance the source   
    writer.write( bestLine + "\n" );
    a[ bestIndex ].timeEnd = a[ bestIndex ].currentDate ;
//    System.out.println( bestLine ) ;
    if ( !a[ bestIndex ].getNextLine() )
               a = purge( a, bestIndex ) ;
    
  }
  // Close the output and save state to the database 
  try
  {
    logProcessor.db.begin() ;
    for ( LogFile file : logs )
                    file.save() ;
    if ( writer != null ) writer.close() ;
    writer = null ;
    logProcessor.db.commit();
  }
  catch( Exception e )
  {
    logProcessor.db.rollback() ;
    e.printStackTrace() ;
    try { if ( writer != null ) writer.close() ; } catch( Exception e2 ) {} ;
    // Delete output file
    FileObject dest = fsManager.resolveFile( mergedName ) ;
    dest.delete() ;
  }
 }


 /**
  * @param a - array to remove an element from
  * @param i - index of the element
  * @return array containing remaining elements
  * @throws Exception 
  */
 private LogFile[] purge( LogFile[] a, int i ) throws Exception
 {
   if ( a[ i ].br != null ) try { a[ i ].br.close() ; } catch( Exception e ) {}
   a[ i ].br = null ;
   a = (LogFile[])ArrayUtils.remove( a, i ) ;
   return a ;
 }


 /**
  * Returns current file of the file, opening file reader and reading first line
  * if necessary. Skips lines with invalid dates.
  * 
  * @param f LogFiule object 
  * @return current line with proper date
  * @throws Exception
  */
 private String currentLine( LogFile f ) throws Exception
 {
   if ( f.ignore ||  
        f.currentLine == null && !f.getNextLine() )
     return null ;
   else return f.currentLine ;   
 }


 Writer getGzWriter( String fileName ) throws Exception
 {
   FileObject dest = fsManager.resolveFile( fileName ) ;
   dest.createFile() ;
   OutputStream fos = dest.getContent().getOutputStream() ;
   Writer writer = new OutputStreamWriter ( new GZIPOutputStream( fos ), "UTF-8" ) ;
   return writer ;
 }

/**
  * Makes a list of available log file objects. Downloads and caches files if they are remote.
  * 
  * @param preprocessing - true if called at preprocessing stage.
  * @throws Exception
  */
public void readLogs( boolean preprocessing ) throws Exception 
 {
   ArrayList<LogFile> logsS = new ArrayList<LogFile>() ;
   Date tooOld = new Date() ;
   long days = preprocessing && retention > 0 ? retention : 100 * 365 ; // 100 years 
   tooOld.setTime( tooOld.getTime() - days * 1000 * 24 * 3600L );

   if ( sitemapURL == null )
     {
        // open log directory and read a list of files from there 
        Map<String, FileObject> logFiles = listFiles() ;
   
       if ( logFiles != null && logFiles.values().size() != 0 )
       { 
         int i, j ;                              // for each file
         // there must not be anything but log files in the logs directory
         for ( FileObject ff : logFiles.values() )
         {
           if ( ff.getType() != FileType.FILE ) continue ;
           LogFile lf = LogFile.newLogFile( ff, this, preprocessing ) ;
           
           // 
           if ( ff.getName().getBaseName().startsWith( "merged_log_" ) && 
                ( lf.ignore || lf.getTimeEnd().before( tooOld ) ) )
           {
             lf.delete() ; continue ;
           }
           
           
           if ( lf.ignore ) lf.timeEnd = lf.timeStart = new Date( 0 ) ;
           logsS.add( lf ) ;
           if ( !preprocessing && !lf.ignore ) // update time coverage
              if ( logsStart == null || logsStart.after( lf.timeStart ) ) logsStart = lf.timeStart ;
         }
         if ( logsS.size() > 0 && !preprocessing )
            { LOG.info( "Logs cover time from " + logsStart ) ; }
         
         if ( logsS.size() > 0 )
         {
           logs = new LogFile[ logsS.size() ] ;
           for ( i = 0 ; i < logsS.size() ; i++ )
                 logs[ i ] = (LogFile)logsS.get( i ) ;

           // arrange logs by dates 
           for ( i = 0 ; i < logs.length - 1 ; i++ )
           {
             LogFile Li = logs[ i ] ;
             for ( j = i + 1 ; j < logs.length ; j++ )
              {
                LogFile Lj = logs[ j ] ;
                if ( Li.ignore || ( !Lj.ignore && Lj.timeStart.before( Li.timeStart ) ) )
                  {
                    Li = Lj ;
                    logs[ j ] = logs[ i ] ;
                    logs[ i ] = Lj ;
                  }
              }
           }
         }
       }
       if ( logs == null && !preprocessing )
           LOG.warn( "No log files found." ) ;
     }
   if ( sitemapURL != null && !preprocessing )
       LOG.info( "Ignoring log files, downloading sitemap file from " + sitemapURL ) ;
 }
 
 
 
 
 /**
  * Make a collection of files referenced by log directories paths and masks
  * @throws Exception
  */
 public Map<String, FileObject> listFiles() throws Exception
 {
   Map<String, FileObject> files = new HashMap<String, FileObject>() ;
   for ( String[] path : pathMask )
   {
 	if ( path.length > 1 )
      for ( int i = 1 ; i < path.length ; i++ )
       {
         ArrayList<FileObject> ff = listFiles( path[ 0 ], path[ i ] ) ;
         if ( ff.size() > 0 )
           for( FileObject file : ff ) 
         	 if ( !files.containsKey( file.getURL().toString() ) ) files.put( file.getURL().toString(), file ) ;
       }
 	else
 	{
 	  ArrayList<FileObject> ff = listFiles( path[ 0 ], null ) ;
       if ( ff.size() > 0 )
           for( FileObject file : ff ) 
         	 if ( !files.containsKey( file.getURL().toString() ) ) files.put( file.getURL().toString(), file ) ;
 	}
   }
  return files ;
 }

 
 /**
  * Make a collection of files referenced by a path and mask pair
  * @throws Exception
  */
 public ArrayList<FileObject> listFiles( String path, String mask ) throws Exception
 {
   ArrayList<FileObject> files = new ArrayList<FileObject>() ;
   FileObject file = fsManager.resolveFile( path, opts );

	 // List the children of URI
	 FileObject[] children = file.getChildren();
	 for ( int i = 0; i < children.length; i++ )
	 {
	   if ( mask == null || children[ i ].getName().getBaseName().matches( mask ) )
	       {
		//     System.out.println( children[ i ].getName().getBaseName() + " accepted" ) ;
		     files.add( children[ i ] ) ;
	       }
	   // else System.out.println( children[ i ].getName().getBaseName() + " rejected" ) ;
	 }    
  return files ;
 }

 
 /**
  * Close db connection, release resources
  * @throws Exception
  */
  public void close()
  throws Exception
  {
    logProcessor.db.deleteLogFiles( name ) ;
    // save info about log files, if there are any
    if ( logs != null )
    {
      for ( int i = 0 ; i < logs.length ; i++ )
      { 
       logs[ i ].removeFromCache() ;
       if ( !deleteLogs ) logs[ i ].save() ;
                     else logs[ i ].delete() ;
      }
    }  
    if ( logs0 != null && logs0 != logs )
    {
      for ( int i = 0 ; i < logs0.length ; i++ )
      { 
       logs0[ i ].removeFromCache() ;
       if ( !deleteLogs ) logs0[ i ].save() ;
                     else logs0[ i ].delete() ;
      }
    }  
    db.close() ;
  }
  
  /**
   * Close all cached log sites
   * @throws Exception
   */
   public static void closeAll()
   throws Exception
   {
	for ( LogSite site : sites.values() )
	   if ( site != null ) site.close() ;
	sites.clear() ;
   }
   
/**
 * Drop all tables in the db
 * @throws Exception
 */
 public void resetAll() throws Exception
 {
   db.resetAll() ;
 }
 
/**
 * Drop document weights in the db
 * @throws Exception
 */
 public void resetWeights() throws Exception
 {
   db.resetWeights() ; db.resetScores() ;
 }
 
/**
 * Clear list of ignored IPs
 * @throws Exception
 */
 public void resetIgnoredIPs() throws Exception
 {
   logProcessor.db.resetIPs( name ) ;
   if ( logs != null )
     for ( int i = 0 ; i < logs.length ; i++ )
                              logs[ i ].setUsedForIPs( false ) ;
 }
   
/**
 * Get date and time of a named processing point
 * @param point name of the processing point
 * @return date and time of the processing point
 * @throws Exception
 */
 public Date getProcessed( String point ) throws Exception
 { return logProcessor.db.getProcessed( point, name ) ; }

/**
 * Save date and time of a named processing point
 * @param point name of the processing point
 * @param value the date and time to save
 * @throws Exception
 */
 public void putProcessed( String point, Date value ) throws Exception
 { logProcessor.db.putProcessed( point, name, value ) ; }


/**
 * Save start and end points of time interval covered by scores in the db
 * @param scoresStart start of the interval
 * @param scoresEnd   end of the interval
 * @throws Exception
 */
 public void saveScoresInterval( Date scoresStart, Date scoresEnd ) throws Exception
 {
  this.scoresStart = scoresStart ;
  this.scoresEnd = scoresEnd ; 
  logProcessor.db.putProcessed( "scoresStart", name, scoresStart ) ;  
  logProcessor.db.putProcessed( "scoresEnd", name, scoresEnd ) ;  
 }
 
/**
 * Read start and end points of time interval covered by scores in the db
 * @throws Exception
 */
 public void readScoresInterval() throws Exception
 { 
   scoresStart = getProcessed( "scoresStart" ) ;
   scoresEnd = getProcessed( "scoresEnd" ) ;
 }

 /**
  * Export document weights in SiteMap format
  * @param filename  name of output file
  * @throws Exception
  */
  public void exportSiteMap( String filename ) throws Exception 
  {
	OutputStream os = new FileOutputStream( filename + ".tmp" ) ;
    if ( logProcessor.keyFile != null ) // if encryption is wanted
    {
      KeyGenerator keyGen = KeyGenerator.getInstance( "AES" ) ;
      SecretKey symKey = keyGen.generateKey() ;
      byte[] keyBytes = symKey.getEncoded() ;
      // read the provided public key
      File filePublicKey = new File( logProcessor.keyFile ) ;
      FileInputStream fis = new FileInputStream( logProcessor.keyFile ) ;
      byte[] encodedPublicKey = new byte[(int) filePublicKey.length() ] ;
      fis.read( encodedPublicKey ) ;
      fis.close();
      X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec( encodedPublicKey ) ;
      KeyFactory keyFactory = KeyFactory.getInstance( "RSA" ) ;
      PublicKey pubKey = keyFactory.generatePublic( pubKeySpec ) ;
      // encrypt the symmetric key with the provided public key
      Cipher cipher = Cipher.getInstance( "RSA" ) ;
      cipher.init( Cipher.ENCRYPT_MODE, pubKey );
      byte[] encrypted = cipher.doFinal( keyBytes ) ;
      // save the encrypted key to the output file name with added ".key"
      FileOutputStream fos = new FileOutputStream( filename + ".key.tmp" ) ;
      fos.write( encrypted ) ;
      fos.close() ;
      // ready to encrypt with the symmetric key
      cipher = Cipher.getInstance( "AES" ) ;
      cipher.init( Cipher.ENCRYPT_MODE, symKey );
      os = new CipherOutputStream( os, cipher ) ;
    } else // delete the key file if any, to avoid confusion
    {
      new File( filename + ".key" ).delete() ;
    }
	if ( logProcessor.compress ) os = new GZIPOutputStream( os ) ;
	os.write( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset>\n".getBytes() ) ;
    db.exportSiteMap( os, this.url, logProcessor.maxScore ) ;
	os.write( "</urlset>\n".getBytes() ) ;
    os.close() ;
    File out = new File( filename ) ;
    if ( out.exists() ) out.delete() ;
    new File( filename + ".tmp" ).renameTo( out ) ;
    if ( logProcessor.keyFile != null )
    {
       out = new File( filename + ".key") ;
       if ( out.exists() ) out.delete() ;
       new File( filename + ".key.tmp" ).renameTo( out ) ;
    }
  }
   
  /**
   * Export document weights in SiteMap format
   * @param filename  name of input file, possibly encrypted and compressed
   * @throws Exception
   */
  public void importSiteMap( String filename ) throws Exception 
  {
	 String smurl = sitemapURL != null ? sitemapURL : url + "arch/sitemap.dat" ; 
	 if ( filename == null )
		 { 
		   String tempDir = logProcessor.cfg.get( "temp.dir", null ) ;
		   if ( tempDir == null )
		   { LOG.warn( "Temp directory is not defined, using current directory." ) ; tempDir = "." ; }
		   filename = tempDir + "/" + name + "-sitemap.dat" ;
		   if ( !Utils.URL2File( filename, smurl ) ) 
			           LOG.warn( "Could not download sitemap file from " + smurl ) ;
		   Utils.URL2File( filename + ".key", smurl + ".key" ) ;
      	 }
	 Cipher cipher = null ;
	 CipherInputStream cis = null ;
	 GZIPInputStream zis = null ;
	 DataInputStream dis = null ;
	 BufferedReader rd = null ;
	 
	 File keyFile = new File( filename + ".key" ) ;
	 File dataFile = new File( filename ) ;
	 if ( !dataFile.exists() )
		 throw new Exception( "SiteMap file " + dataFile.getCanonicalPath() + " does not exist." ) ;
	 InputStream is = null ;
	 try
	 {
	   // Prepare everything for decryption
	   if ( logProcessor.keyFile != null && keyFile.exists() )
	   {
	     FileInputStream fis = new FileInputStream( logProcessor.keyFile );
	     byte[] encodedPrivateKey = new byte[ (int)( new File( logProcessor.keyFile ) ).length() ] ;
	     fis.read( encodedPrivateKey ) ;
	     fis.close() ;
	     KeyFactory keyFactory = KeyFactory.getInstance( "RSA" );
	     PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec( encodedPrivateKey ) ;
	     PrivateKey privateKey = keyFactory.generatePrivate( privateKeySpec ) ;
	     fis = new FileInputStream( keyFile );
	     byte[] encodedSymmKey = new byte[ (int)( keyFile.length() ) ] ;
	     fis.read( encodedSymmKey ) ;
	     fis.close() ;
	     Cipher cc = Cipher.getInstance( "RSA" ) ;
	     cc.init( Cipher.DECRYPT_MODE, privateKey ) ;
	     byte[] symmKeyBytes = cc.doFinal( encodedSymmKey ) ;
	     // now get the symmetric key
	     SecretKeySpec skeySpec = new SecretKeySpec(symmKeyBytes, "AES");
         cipher = Cipher.getInstance( "AES" ) ;
	     cipher.init( Cipher.DECRYPT_MODE, skeySpec ) ;
	   }
	 } catch( Exception e )
	 {
	   LOG.error( "Can't initiate decription of " + dataFile.getCanonicalPath() ) ;
	 }
	   
	 try // try compressed and encrypted   
	 {
	   if ( cipher == null ) throw new Exception( "Must be unencrypted." ) ;
	   is = new FileInputStream( dataFile ) ;
	   cis = new CipherInputStream( is, cipher ) ;
	   zis = new GZIPInputStream( cis ) ;
	   dis = new DataInputStream( zis ) ;
	   rd = new BufferedReader( new InputStreamReader( dis ) ) ;
	   String line = rd.readLine() ;
	   if ( !line.startsWith( "<?xml" ) )
		         throw new Exception( "Does not look right" ) ;
	 } catch( Exception e1 )
	 { try // try compressed but not encrypted 
	   {
		 if ( rd != null ) try { rd.close() ; } catch( Exception ee ) {} 
		 else if ( zis != null ) try { zis.close() ; } catch( Exception ee ) {} 
		 else if ( cis != null ) try { cis.close() ; } catch( Exception ee ) {} 
		 else if ( is != null ) try { is.close() ; } catch( Exception ee ) {} 
	     rd = null ; dis = null ; zis = null ; cis = null ; is = null ;
		 is = new FileInputStream( dataFile ) ;
		 zis = new GZIPInputStream( is ) ;
		 dis = new DataInputStream( zis ) ;
		 rd = new BufferedReader( new InputStreamReader( dis ) ) ;
		 String line = rd.readLine() ;
		 if ( !line.startsWith( "<?xml" ) )
			         throw new Exception( "Does not look right" ) ;
	   } catch( Exception e2 )
	   { try // try encrypted but not compressed 
		 {
		   if ( rd != null ) try { rd.close() ; } catch( Exception ee ) {} 
		   else if ( zis != null ) try { zis.close() ; } catch( Exception ee ) {}
		   else if ( is != null ) try { cis.close() ; } catch( Exception ee ) {} 
           rd = null ; dis = null ; zis = null ; is = null ;
		   is = new FileInputStream( dataFile ) ;
           cis = new CipherInputStream( is, cipher ) ;
		   dis = new DataInputStream( cis ) ;
		   rd = new BufferedReader( new InputStreamReader( dis ) ) ;
		   String line = rd.readLine() ;
		   if ( !line.startsWith( "<?xml" ) )
				         throw new Exception( "Does not look right" ) ; 
		 } catch( Exception e3 )
		 { // must be plain text, if not - we have a problem
		   if ( rd != null ) try { rd.close() ; } catch( Exception ee ) {} 
		   else if ( cis != null ) try { cis.close() ; } catch( Exception ee ) {} 
		   else if ( is != null ) try { is.close() ; } catch( Exception ee ) {} 
	       rd = null ; dis = null ; cis = null ; is = null ;
		   is = new FileInputStream( dataFile ) ;
		   dis = new DataInputStream( is ) ;
		   rd = new BufferedReader( new InputStreamReader( dis ) ) ;
		   String line = rd.readLine() ;
		   if ( !line.startsWith( "<?xml" ) )
					         throw new Exception( "Can't recognize format of the input file " + dataFile.getCanonicalPath() ) ;    
		 }   
	   }
	 }
    db.importSiteMap( rd, this.url, logProcessor.maxScore ) ;
    rd.close() ;
  }
    

  /**
   * Export document scores to a file
   * @param filename  name of output file
   * @throws Exception
   */
   public void scores2file( String filename ) throws Exception 
   {
    readScoresInterval() ;
    ScoreFile scoreFile = ScoreFileImpl.newScoreFile( filename, scoresStart, scoresEnd ) ;
    db.scores2file( scoreFile ) ;
    scoreFile.close() ;
   }
    
/**
 * Import document scores from a file
 * @param filename  name of input file
 * @throws Exception
 */
 public void file2scores( String filename ) throws Exception
 {
  // open source file for reading.
  ScoreFile scoreFile = ScoreFileImpl.newScoreFile( filename ) ;
  db.resetScores() ;
  saveScoresInterval( scoreFile.getStart(), scoreFile.getEnd() ) ;
  db.file2scores( scoreFile ) ;
  scoreFile.close() ;
  scores2weights() ;
 }
  
/**
 * Update document weights based on scores
 * @throws Exception
 */
 public void scores2weights() throws Exception 
 {

  if ( logsStart == null || logs == null ) return ; // nothing to do, no logs 
  // open file to export new URLs to 
  FileOutputStream fo = new FileOutputStream( newURLsFile, true ) ; 
  PrintWriter ps = new PrintWriter( fo ) ;
  int newURLs = 0 ;
  boolean firstBuild = false ; // if true, this is first build and all URLs are new
  final long MSDAY = 24 * 3600L * 1000 ; // milliseconds in a day

  weightsStart = getProcessed( "weightsStart" ) ;
  weightsEnd = getProcessed( "weightsEnd" ) ;
  scoresStart = getProcessed( "scoresStart" ) ;
  scoresEnd = getProcessed( "scoresEnd" ) ;
  if ( scoresStart == null || scoresEnd == null )
	 { LOG.warn( "Site " + name +  ": no scores extracted, are there any logs?" ) ; return ; }
  LOG.info( "Applying scores after processing logs from " + scoresStart + " to " + scoresEnd ) ;
  System.out.println( "Site " + name +  ": applying scores after processing logs from " + scoresStart + " to " + scoresEnd ) ;

  long logLengthMS = logProcessor.logLength * MSDAY ; // in milliseconds 
  long intervalInDb = 0 ; // log interval currently covered by scores in the DB     
  if ( weightsEnd != null && weightsStart != null )
       intervalInDb = weightsEnd.getTime() - weightsStart.getTime() ;
  long scoresInterval = scoresEnd.getTime() - scoresStart.getTime() ;
  long leavingInDb = 0 ; // part of log interval currently covered that will be left
  if ( intervalInDb == 0 || scoresInterval >= logLengthMS ) // if nothing is there or too old
                                                    leavingInDb = 0 ;
                              // if everything will be left
  else if ( intervalInDb + scoresInterval <= logLengthMS ) leavingInDb = intervalInDb ; 
     else leavingInDb = logLengthMS - scoresInterval ; // leaving a part
  LOG.info( "Logs covered in db: " + intervalInDb / MSDAY + " days, leaving " +
		                                          leavingInDb / MSDAY + " days." ) ;
  // if nothing to process
  if ( weightsEnd != null && !scoresEnd.after( weightsEnd ) )
	                                           { LOG.info( "Nothing to apply." ) ; return ; }
  Date startProcessing = new Date( scoresEnd.getTime() - scoresInterval ) ;
  float scoreValueRetain = 0 ; 
  long timeEnd = scoresEnd.getTime(), start = startProcessing.getTime() ;
  LOG.info("Adding new interval from " + startProcessing + " to " + scoresEnd + 
		     ", " + (timeEnd - start)/ MSDAY + " days." ) ;
  if ( intervalInDb != 0 )
        scoreValueRetain = (float)leavingInDb / ( leavingInDb + scoresInterval ) ;

  db.begin() ;
  float maxWeight = db.getMaxWeight() ;
  if ( maxWeight < 0f ) { maxWeight = 10f ; firstBuild = true ; }
  float maxScore = db.getMaxScore() ;
  if ( maxScore < 0f ) maxScore = 10f ;
  float max = 0 ;
  
  if ( maxScore > 0.000001 ) // else have nothing to do
   {
    float koeff = maxWeight / (float)Math.log( maxScore + 1 ) ;
//	float koeff = maxWeight / (float)Math.sqrt( maxScore + 1 ) ;
    ResultSet  rs = db.listScores() ;
    float[] paramContainer = new float[1] ;
    while( rs.next() )
     {
      int id = rs.getInt( 1 ) ;
      String path = rs.getString( 2 ) ;
      String name = rs.getString( 3 ) ; if ( name.equals( "/" ) ) name = "" ;
      path = path + "/" + name ;
      int score = rs.getInt( 5 ) ;
      float weight = rs.getFloat( 4 ) ;
      int aliasOf = rs.getInt( 6 ) ;
      if ( aliasOf != 0 ) // this URL has aliases, sum their scores
      {
        if ( aliasOf == id ) // first in the alias chain 
        {
          score = db.getSumScores( rs.getInt( 6 ), paramContainer ) ;
        }
        else // weight of the first alias has been calculated already 
        {
          weight =  db.getWeight( aliasOf ) ;
        }
      }
      // if not an alias, or first alias, have to calculate a new weight
      if ( aliasOf == 0 || aliasOf == id ) 
      {
        float delta = ( 1f-scoreValueRetain ) * koeff * (float)Math.log( score + 1 ) ;
//      float delta = ( 1f-scoreValueRetain ) * koeff * (float)Math.sqrt( score + 1 ) ;
        if ( weight < -0.5 ) // this is a new url, output it to the list
         {
           if ( !firstBuild ) { ps.println( url + path ) ; newURLs++ ; }
           weight = maxWeight / 5 ; // starter weight for new urls
         }
        weight = weight * scoreValueRetain + delta ;
      }
      if ( weight > max ) max = weight ;
      if ( weight < 0.1f ) weight = 0.1f ;
      if ( LOG.isTraceEnabled() )
      {
        LOG.trace( "Set weight: " + weight + " " + path ) ;
      }
      db.updateWeight( id, weight ) ;
     }
    rs.close() ;
    // now normalise what we got in the database
    if ( max == 0 ) max = 10f ; // should not happen
    koeff = logProcessor.maxScore / max ;
    if ( koeff != 1.0 ) db.normaliseWeights(  koeff ) ;
   }
  // update times in the sites table
  if ( leavingInDb == 0 ) putProcessed( "weightsStart", startProcessing ) ;
     else putProcessed( "weightsStart", new Date( weightsEnd.getTime() - leavingInDb ) ) ; 
  putProcessed( "weightsEnd", scoresEnd ) ;
  ps.close() ;
  fo.close() ; 
  if ( newURLs == 0 ) // no new urls found
   {
     File f = new File( newURLsFile ) ;
     f.delete() ;
   }
  db.commit() ;
 }
 
 /**
 * Process log files, calculate document scores and save them to the db
 * @throws Exception
 */
 public void logs2scores() throws Exception 
 {
  HashMap urls = new HashMap() ;
  long time = 0 ;
  
  Date start = new Date() ;
  
  if ( logsStart == null || logs == null ) return ; // nothing to do, no logs
  LOG.info( "Processing site " + name ) ;
  System.out.println( "Site " + name +  ": calculating document scores." ) ;
  cleaningTime = 0 ; 
  db.resetScores() ;  
  Date startProcessing = logsEnd = logsStart ;
  if ( weightsEnd != null && startProcessing.before( weightsEnd ) )
                                                               startProcessing = weightsEnd ;
  for ( int j = 0 ; j < logs.length ; j++ ) // for each log file in the site
  {
    LogFile log = logs[ j ] ;
    if ( ( log.timeEnd != null && !log.timeEnd.after( startProcessing ) )
        || log.ignore ) continue ;

    System.out.println( "         log file: " + log.fileName ) ;
    // for every log line in the log
    Date lineDate = null ;
    if ( log.getNextLine() ) // getNextGoodLine may never find a good line in this file
      lineDate = logsEnd = parser.getDate() ; 
    while( log.getNextGoodLine() )
     {
       lineDate = logsEnd = parser.getDate() ;
       if ( !lineDate.after( startProcessing ) ) continue ;
       long latestTime = lineDate.getTime() ;
       if ( time == 0 ) time = latestTime ;
       if ( latestTime - time >= 3600 * 24 * 1000 ) // one day is gone
                                { nextDay( urls ) ; time = latestTime ; }
       String url0 = parser.getCanonicalUrl() ;
       if ( urls.size() >= logProcessor.maxURLCacheSize )
        { LOG.info( " Compacting URLs cache..." ) ;
                 compactURLCache( urls, false ) ;
        }
       URLCacheObject uco = (URLCacheObject)urls.get( url0 ) ;
       if ( uco == null ) 
          { 
            uco = new URLCacheObject( logProcessor.hitHistorySize, logProcessor ) ;
            uco.addLogLine( parser ) ;
            urls.put( url0, uco ) ;
          } else uco.addLogLine( parser ) ;
     } // end lines
    log.close();
    if ( lineDate != null ) log.timeEnd = lineDate ; // date of last line
       else log.timeEnd = log.timeStart ; // does not have anything of interest 
  } // end logs
  LOG.debug( " Writing scores..." ) ;
  compactURLCache( urls, true ) ;
  Date end = new Date() ;
  LOG.info( " Finished in " + ( end.getTime() - start.getTime() )/1000 + " seconds." ) ;
      
  saveScoresInterval( startProcessing, logsEnd ) ;
 }
 
/**
 * Write info on least accessed urls to the db. Release resources.
 * 
 * @param urls  a cache of urls
 * @param flushAll  if true, all urls will be recorded and removed from cache
 * @throws Exception
 */
 public void compactURLCache( HashMap urls, boolean flushAll ) throws Exception
 {
  // have to achieve at least 10% of size reduction
  // increase hits number until have achieved this
  int maxSize = urls.size() ;
  int delta = urls.size() / 10 ;
  if ( delta == 0 ) delta = 1 ;
  if ( flushAll ) delta = maxSize ;
  URLSplit split = null ;

  db.begin() ; 
  for ( int hits = 1, removed = 0 ; removed < delta ; hits++ )
   {
     Object[] set = urls.keySet().toArray() ; 
     for ( int i = 0 ; i < set.length ; i++ )
      {  
        String url = (String)set[ i ] ;
        URLCacheObject o = (URLCacheObject)urls.get( url ) ;
        o.nextDay() ;
        if ( o.getTotalScore() <= hits || flushAll )
          { 
            urls.remove( url ) ; removed++ ;
            if ( o.ignored ) continue ;
            int score = o.getTotalScore() ;
            if ( LOG.isTraceEnabled() )
                LOG.trace( "Score : " + score + " " + url ) ;   
            split = URLSplit.newURLSplit( url, split, "f" ) ;
            try
            {  
              // Ignore abnormally long paths and names
              if ( split.path.length() <= DBInterface.MAX_PATH_LENGTH &&
                     split.name.length() <= DBInterface.MAX_NAME_LENGTH )
                   db.writeScore( split.path, split.name, score, true ) ;
            } catch( Throwable th )
            {
              LOG.warn( "Could not save score for " + url + " " + th.getMessage() );
            }
          }
      }
     if ( flushAll ) break ; 
   }
   db.commit() ; 
 }

/**
 * Clear hit history in all cached urls when a new day comes
 * 
 * @param urls  a cache of urls
 * @throws Exception
 */
 public void nextDay( HashMap urls ) throws Exception //========================
 {
   cleaningTime = 0 ;
   Collection values = urls.values() ; 
   Iterator iter = values.iterator() ;
   while ( iter.hasNext() )
    {  
     URLCacheObject o = (URLCacheObject)iter.next() ;
     o.nextDay() ;
    }  
 }
 
/**
 * Builda a global list of ignored (blacklisted) IP addresses
 * 
 * @param ignoredIPs  a set of blacklisted IP addresses
 * @throws Exception
 */
 void makeIgnoredIPs( Map<String,String> ignoredIPs )
 throws Exception
 {
   if ( !filterIPs ) return ; // can't rely on ip addresses
   if ( logsStart == null || logs == null ) return ; // no logs?
   Date startProcessing = logsStart ;
   if ( weightsEnd != null && startProcessing.before( weightsEnd ) )
                                                    startProcessing = weightsEnd ;
   long cleaningTime = 0 ;
   HashMap  ips = new HashMap( logProcessor.maxIPCacheSize ) ;
   System.out.println( "Site " + name +  ": identifying IP addresses to ignore." ) ;

   for ( int j = 0 ; j < logs.length ; j++ ) // for each log file
   {
     LogFile log = logs[ j ] ;
     // If the log is too old or have been used in making the list of ignored IPs, ignore
     if ( log.isUsedForIPs() || !log.timeEnd.after( startProcessing ) ) continue ;
     log.open() ; // unpack if required
     if ( log.getSize() != log.getOldSize() && 
    		    log.getOldTimeEnd() != null &&  !log.getOldTimeEnd().after( startProcessing ) )
     { log.skip( log.getOldSize() ) ; } // only interested in what's new

       // for every log line in the log
     while( log.getNextGoodLine() )
     {
       if ( parser.getDate().before( startProcessing ) ) continue ;
       long lineTime = parser.getDate().getTime() ;
       if ( cleaningTime == 0 ) cleaningTime = lineTime ;
       if ( ips.size() >= logProcessor.maxIPCacheSize 
            /* || lineTime - cleaningTime > captureInterval * 200 */)
             { compactIPCache( ips, lineTime, ips.size() >= logProcessor.maxIPCacheSize ) ;
               cleaningTime = lineTime ;
             }
       // for every ip address ocurring in the line
       for ( int k = 0 ; k < parser.getAddr().size() ; k++ )
        {
          String ip = (String)parser.getAddr().get( k ) ;
          // if ( ignoredIPs.contains( ip ) ) continue ; no need, checked in LogLine
          IPCacheObject o = (IPCacheObject)ips.get( ip ) ;
          if ( o == null )
           { o = new IPCacheObject( parser.getDate() ) ;
             ips.put( ip, o ) ;
           } else // contains this object
           {
             long time = parser.getDate().getTime() ;
             if ( time - o.getTime() > logProcessor.captureInterval )
              { o.setTime( parser.getDate() ) ; o.setCount( 0 ) ; }
             if ( o.getCount() + 1 > logProcessor.hitsThreshold )
              { 
                if ( LOG.isDebugEnabled() )
                   LOG.debug("Adding IP to ignored: " + ip ) ;   
                ignoredIPs.put( ip, "0" ) ; 
                ips.remove( ip ) ;
              } else o.countPP() ;
            }  
         }
     } // end log lines
     log.setUsedForIPs( true ) ;
     log.close();
   } // end logs
 }
 

 /**
 * Remove least suspicious IP addresses from the cache to release resources
 * 
 * @param ips  a cache of IP addresses
 * @param time release all that were accessed before this time minus capture interval
 * @param noSpace   true if need to release at least 10% of space
 * @throws Exception
 */
public void compactIPCache( HashMap ips, long time, boolean noSpace ) throws Exception
 {
   // have to achieve at leqast 10% of size reduction
   // increase hits number until have achieved this
   int maxSize = ips.size() ;
   int delta = ips.size() / 10 ;
   for ( int hits = 0, removed = 0 ; hits < 1 || removed < delta && noSpace ; hits++ )
   {
     Object[] set = ips.keySet().toArray() ; 
     for ( int i = 0 ; i < set.length ; i++ )
      {  
        String ip = (String)set[ i ] ;
        IPCacheObject o = (IPCacheObject)ips.get( ip ) ;
        if ( o.getCount() <= hits ||
             o.getTime() + logProcessor.captureInterval < time )
         { ips.remove( ip ) ; removed++ ; }
      }
   }  

 }

 // Getters and setters
 
 public String getUrl()
  {
   return url ;
  }


 public void setUrl( String baseUrl )
  {
   this.url = url ;
  }


 public LogProcessor getBoss()
  {
   return logProcessor ;
  }


 public void setBoss( LogProcessor boss )
  {
   this.logProcessor = boss;
  }


 public LogFile[] getLogs()
  {
   return logs;
  }


 public void setLogs( LogFile[] logs )
  {
   this.logs = logs;
  }


 public Date getLogsEnd()
  {
   return logsEnd;
  }


 public void setLogsEnd( Date logsEnd )
  {
   this.logsEnd = logsEnd;
  }


 public Date getLogsStart()
  {
   return logsStart;
  }


 public void setLogsStart( Date logsStart )
  {
   this.logsStart = logsStart;
  }

 public String getName()
  {
   return name;
  }

 public void setName( String name )
  {
   this.name = name;
  }

public Date getWeightsEnd()
  {
   return weightsEnd;
  }

 public void setWeightsEnd( Date weightsEnd )
  {
   this.weightsEnd = weightsEnd;
  }

 public Date getWeightsStart()
  {
   return weightsStart;
  }

 public void setWeightsStart( Date weightsStart )
  {
   this.weightsStart = weightsStart;
  }
 
 /**
 * A container for IP cache datum
 */ 
 public class IPCacheObject
 {
  long time ; // timestamp of last hit
  int count ; // counts of hits
  
 /**
  * A constructor
  * @param date date of the first hit
  */ 
  public IPCacheObject( Date date )
   {
     count = 1 ;
     setTime( date ) ;
   }

 /**
  * Increment hit count
  */ 
  public void countPP()
   {
    this.count++ ;
   }
  
  public int getCount()
   {
    return count;
   }

  public void setCount( int count )
   {
    this.count = count;
   }

  public long getTime()
   {
    return time;
   }

  public void setTime( Date date )
   {
    this.time = date.getTime() ;
   }
  
 }
 
/**
 * A container for url cache datum
 */ 
 public class URLCacheObject
 {
   String[] ips = null ; // a list of last IP addresses that accessed this URL
   long[] times = null ; // timestamps of accesses
   int[]   hits = null ; // numbers of hits
   int           score ; // score within a day, limited by a max value
   int      totalScore ; // summ on all log days
   LogProcessor   boss ; // a reference to the
   boolean     ignored ; // true if this URL is ignored

  
/**
 * Constructor
 * 
 * @param hitHistorySize a number of last IP addresses to remember
 * @param boss  a reference to LogProcessor object
 */ 
  public URLCacheObject( int hitHistorySize, LogProcessor boss )
  {
    this.score = 0 ;
    this.boss = boss ;
    if ( !boss.ignoredIPs.isEmpty() ) // only need to do it if ip based filtering is on
     {
       this.ips = new String[ hitHistorySize ] ; 
       this.times = new long[ hitHistorySize ] ; 
       this.hits = new int[ hitHistorySize ] ; 
       for ( int i = 0 ; i < hitHistorySize ; i++ )
         { this.ips[ i ] = null ; times[i] = hits[i] = 0 ; }
     }
  }
  
/**
 * Register (count in) hits in the line
 * 
 * @param line a parsed log line
 */ 
  void addLogLine( LogLineParser parser )
  {
   if ( ignored ) return ; 
   int time = (int)( parser.getDate().getTime()/1000L ) ;
   if( !boss.ignoredIPs.isEmpty() && parser.getScore() > 0 )
     for ( int i = 0 ; i < parser.getAddr().size() ; i++ )
            addIPHit( (String)parser.getAddr().get(  i  ), time ) ;
     else score += parser.getAddr().size() * parser.getScore() ;
  }
  
/**
 * Register (count in) a hit
 * 
 * @param ip    source IP address
 * @param time  timestamp of the hit
 */ 
  void addIPHit( String ip, long time )
  {
   if ( ignored ) return ; 
   int earlyI = 0, freeI, i, found ;
   long earlyTime = times[ earlyI ] ;
   
   for ( i = 0, freeI = -1, found = -1 ; i < boss.hitHistorySize ; i++ )
    {
      if ( this.ips[ i ] == null && freeI == -1 ) { freeI = i ; continue ; }
      if ( this.ips[ i ] != null )
       { if ( this.ips[ i ].equals( ip ) ) { found = i ; break ; }
         if ( times[ i ] < earlyTime ) { earlyTime = times[ i ] ; earlyI = i ; }
       }
    }
   // if ip is not on the list, add it
   if ( found == -1 )
    { // if no space, forget the earliest one
      if ( freeI == -1 ) freeI = earlyI ;
      this.ips[ freeI ] = ip ; hits[ freeI ] = 0 ;
      found = freeI ;
    }
   // if it is on the list, hits++
   hits[ found ]++ ; times[ found ] = time ;
   // if hits < maxHitsPerDay, score++
   if ( hits[ found ] <= boss.maxHitsIpPerDay ) score++ ;   
   // if hits == abuseThreshold, ignore ip, deduct the score
      else if ( boss.abuseThreshold  > boss.maxHitsIpPerDay &&
                hits[ found ] >= boss.abuseThreshold )
       {
        score -= boss.maxHitsIpPerDay ;
        if ( !boss.ignoredIPs.containsKey( ip ) )
                   boss.ignoredIPs.put( ip, "0" ) ; // CAREFUL!!! Some pages can be requested often
        this.ips[ found ] = null ;
       }
  }
  
/**
 * Clear hit history
 */ 
  public void clearHistory()
  {
    if ( ignored ) return ;
    if ( score > 0 ) // ignore repeat requests
     if ( !boss.ignoredIPs.isEmpty() )
       for ( int i = 0 ; i < boss.hitHistorySize ; i++ )
         { this.ips[ i ] = null ; this.times[ i ] = 0 ; }
  }

/**
 * Prepare for the next day
 */ 
  public void nextDay()
  {
    if ( ignored ) return ;
    clearHistory() ;
    if ( score > boss.maxHitsPerDay ) score = boss.maxHitsPerDay ;
    totalScore += score ;
    score = 0 ;
  }

  public int[] getHits()
  {
    return hits;
  }

  public void setHits( int[] hits )
   {
    this.hits = hits;
   }

  public String[] getIps()
   {
    return ips;
   }

  public void setIps( String[] ips )
   {
    this.ips = ips;
   }

  public int getScore()
   {
    return score;
   }

  public void setScore( int score )
   {
    this.score = score;
   }

  public int getTotalScore()
   {
    return totalScore ;
   }

  public void setTotalScore( int score )
   {
    this.totalScore = score;
   }

  public long[] getTimes()
   {
    return times;
   }

  public void setTimes( long[] times )
   {
    this.times = times;
   }
  
 }
  
}
