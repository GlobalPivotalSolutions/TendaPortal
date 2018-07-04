/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexRootDB;

/**
 * A set of various utility functions
 * 
 * @author Arkadi Kosmynin
 *
 */
public class Utils
{
  public static final Logger LOG = LoggerFactory.getLogger( Utils.class );
  private final static boolean inCygwin = checkCygwin();

  static public DBConnected indexFilter;
  static public DBConnected scoringFilter;
  static public DBConnected prefixFilter;
  static public DBConnected scanUtil;
  static public DBConnected dedupReducer;
  static public DBConnected dbFilter;

  /**
   * Check if this is run under Cygwin
   * 
   * @return true if the program is run under Cygwin, else false
   */
  static public boolean checkCygwin()
  {
    String res = systemRead( "uname" );
    if ( res != null && res.indexOf( "CYGWIN" ) >= 0 )
      return true;
    return false;
  }

  /**
   * Return value of inCygwin
   * 
   * @return true if the program is run under Cygwin, else false
   */
  static public boolean isCygwin()
  {
    return inCygwin;
  }

  /**
   * Converts Windows path to Cygwin path if running under Cygwin. Returns the original path if not under Cygwin.
   * 
   * @param Windows
   *          path
   * @return Cygwin path
   */
  static public String getPath( String originalPath )
  {
    if ( !inCygwin )
      return originalPath;
    String res = systemRead( "cygpath " + originalPath );
    return res;
  }

  /**
   * Check if this name is good for a site or area
   * 
   * @param name
   *          the name to check
   * @return true if the name is good, else false
   */
  static public boolean isProperName( String name )
  {
    if ( name == null || name.length() == 0 )
      return false;
    if ( !Character.isLetter( name.charAt( 0 ) ) )
      return false;
    for ( int i = 0; i < name.length(); i++ )
      if ( !Character.isLetter( name.charAt( i ) ) && !Character.isDigit( name.charAt( i ) ) )
        return false;
    return true;
  }

  /**
   * Create a symbolic link (on Linux) or junction (on Windows)
   * 
   * @param source
   *          sym link source
   * @param destination
   *          sym link destination
   * @param replace
   *          if true replace existing sym link else error if exists
   * @return true if the name is good, else false
   */
  static public boolean symLink( String source, String destination, boolean replace ) throws Exception
  {
    String result;
    if ( exists( source ) )
    {
      if ( !replace )
        return false;
      result = systemRead( "rm -f " + source );
      if ( exists( source ) )
        throw new Exception( "Can't delete " + source + ": " + result );
    }
    if ( inCygwin )
    {
      source = source.replaceAll( "[\\/]", "\\\\" );
      destination = destination.replaceAll( "[\\/]", "\\\\" );
      result = systemRead( "CMD /C mklink /D " + source + " " + destination );
    } else
      result = systemRead( "ln -s " + destination + " " + source );
    if ( !exists( source ) )
      throw new Exception( "Can't create symbolic link " + source + " to " + destination + ": " + result );
    return true;
  }

  static public void checkLinkd() throws Exception
  {
    if ( inCygwin )
    {
      String result = systemRead( "linkd /?" );
      if ( result == null || result.indexOf( "LINKD" ) < 0 )
        throw new Exception( "Linkd is not installed or not found in the path." );
    }
  }

  /**
   * Form and throw and exception when a required configuration parameter is missing
   * 
   * @param param
   *          name of the missing parameter
   */
  static public void missingParam( String param ) throws Exception
  {
    throw new Exception( "Parameter " + param + " is undefined." );
  }

  /**
   * Bring first char of the string to upper case
   * 
   * @param s
   *          string to modify
   * @return modified string
   */
  static public String up( String s )
  {
    return ( s.length() > 0 ) ? Character.toUpperCase( s.charAt( 0 ) ) + s.substring( 1 ) : s;
  }

  /**
   * Check of two arrays of strings are same
   * 
   * @param a
   *          array 1
   * @param b
   *          array 2
   * @return true if these arrays contain same strings, else fasle
   */
  static public boolean isSame( String[] a, String[] b )
  {
    if ( a == null && b == null )
      return true;
    if ( a == null || b == null )
      return false;
    if ( a.length != b.length )
      return false;
    Arrays.sort( a );
    Arrays.sort( b );
    for ( int i = 0; i < a.length; i++ )
      if ( a[ i ].compareTo( b[ i ] ) != 0 )
        return false;
    return true;
  }

  /**
   * Check of arrays has this string
   * 
   * @param a
   *          array of strings to search
   * @param b
   *          string to search for
   * @return number of the element in the array if found
   */
  static public int in( String[] a, String b )
  {
    if ( a == null || b == null )
      return -1;
    for ( int i = 0; i < a.length; i++ )
      if ( a[ i ].equalsIgnoreCase( b ) )
        return i;
    return -1;
  }

  /**
   * Find first element that differs two arrays
   * 
   * @param a
   *          array 1
   * @param b
   *          array 2
   * @return number of the element that differs
   */
  static public String diff( String[] a, String[] b )
  {
    if ( a == null && b == null )
      return null;
    if ( a == null )
      return b[ 0 ];
    else if ( b == null )
      return a[ 0 ];
    Arrays.sort( a );
    Arrays.sort( b );

    for ( int i = 0; i < a.length && i < b.length; i++ )
    {
      if ( a[ i ].compareTo( b[ i ] ) != 0 )
      {
        if ( a.length == i + 1 && b.length == i + 1 )
          return a[ i ]; // does not matter, could be b[i]
        if ( a.length > i + 1 && a[ i + 1 ].compareTo( b[ i ] ) == 0 )
          return a[ i ];
        if ( b.length > i + 1 && b[ i + 1 ].compareTo( a[ i ] ) == 0 )
          return b[ i ];
        return a[ i ]; // does not matter, could be b[i]
      }
    }

    if ( a.length == b.length )
      return null;
    else if ( a.length < b.length )
      return b[ a.length ];
    else
      return a[ b.length ];
  }

  /**
   * Return name of first file in the directory
   * 
   * @param dir
   *          directory to search
   * @param withDir
   *          if true inlude path in the file name
   * @return file name
   */
  static public String getFirstFile( String dir, boolean withDir )
  {
    String[] aa = new File( dir ).list();
    int i = 0;
    if ( aa != null )
    {
      while ( aa[ i ].equals( "." ) || aa[ i ].equals( ".." ) )
        i++;
      if ( withDir )
        return dir + File.separatorChar + aa[ i ];
      else
        return aa[ i ];
    } else
      return null;
  }

  /**
   * Return a list of files in the directory
   * 
   * @param dir
   *          directory to search
   * @param withDir
   *          if true return full name, else short name
   * @param fs
   *          if null - local directory, else rely use hadoop fs
   * 
   * @return array of String file names
   */
  static public String[] getFiles( String dir, boolean withDir, FileSystem fs ) throws Exception
  {
    String[] names = null;
    if ( fs == null )
      names = new File( dir ).list();
    else
    {
      Path path = new Path( dir );
      FileStatus[] files = fs.listStatus( path );
      if ( files == null )
        return new String[ 0 ];
      names = new String[ files.length ];
      for ( int i = 0; i < files.length; i++ )
      {
        names[ i ] = files[ i ].getPath().getName();
      }
    }
    String pref = "";
    if ( withDir )
    {
      pref = dir + File.separatorChar;
      for ( int i = 0; i < names.length; i++ )
        names[ i ] = pref + names[ i ];
    }
    return names;
  }

  /**
   * Check if this file exists
   * 
   * @param file
   *          file name
   * @return true if the file exists
   */
  static public boolean exists( String file )
  {
    return new File( file ).exists();
  }

  /**
   * Recursively remove the directory
   * 
   * @param dir
   *          directory to remove
   * @throws Exception
   */
  static public void rmdir( String dir, FileSystem fs ) throws Exception
  {
    if ( fs == null )
    {
      File toDelete = new File( dir );
      if ( toDelete.exists() && !FileUtil.fullyDelete( toDelete ) )
        LOG.warn( "Could not delete directory " + dir );
    } else
    {
      Path toDelete = new Path( dir );
      if ( fs.exists( toDelete ) && !fs.delete( toDelete, true ) )
        LOG.warn( "Could not delete directory " + dir );
    }
  }

  /**
   * Recursively create the directory
   * 
   * @param dir
   *          directory to create\
   * @throws Exception
   */
  static public void mkdir( String dir ) throws Exception
  {
    File dd = new File( dir );
    if ( !dd.exists() )
    {
      boolean res = false;
      int i = 0;
      do
      { // it looks like Cygwin and Windows need a bit of time to sort out things
        res = dd.mkdirs();
        if ( dd.exists() )
          break;
        Thread.sleep( 100 );
      } while ( !res && i < 10 );
      if ( !dd.exists() )
        throw new Exception( "Could not create directory " + dir );
    }
  }

  /**
   * Copy file from source to destination
   * 
   * @param src
   *          source file to copy
   * @param dst
   *          destination file
   * @throws Exception
   */
  static public void copyFile( String dst, String src ) throws IOException
  {
    InputStream in = new FileInputStream( new File( src ) );
    OutputStream out = new FileOutputStream( new File( dst ) );
    byte[] buf = new byte[ 10240 ];
    int len;
    while ( ( len = in.read( buf ) ) > 0 )
    {
      out.write( buf, 0, len );
    }
    in.close();
    out.close();
  }

  /**
   * Run a short system command and read its output
   * 
   * @param command
   * @return command output
   */
  public static String systemRead( String command )
  {
    String res = "";
    try
    {
      Process p = Runtime.getRuntime().exec( command );
      p.waitFor();
      BufferedReader reader = new BufferedReader( new InputStreamReader( p.getInputStream() ) );
      String line = reader.readLine();
      while ( line != null )
      {
        res += line + "\n";
        line = reader.readLine();
      }
      return res;
    } catch ( IOException e1 )
    {} catch ( InterruptedException e2 )
    {}
    return null;
  }

  /**
   * Run a (long) system command
   * 
   * @param command
   * @return 0 if success, -1 if failed
   */
  public static int systemLong( String command )
  {
    int res = -1;
    try
    {
      // Process p = Runtime.getRuntime().exec( command + " 1>/dev/null 2>/dev/null" ) ;
      Process p = Runtime.getRuntime().exec( command );
      /*
       * The commented part is needed to read forked process output, if it is not directed to null
       */
      OutputReader errorReader = new OutputReader( p.getErrorStream() );
      OutputReader outputReader = new OutputReader( p.getInputStream() );
      // kick them off
      errorReader.start();
      outputReader.start();

      res = p.waitFor();
    } catch ( IOException e1 )
    {} catch ( InterruptedException e2 )
    {}
    return res;
  }

  /**
   * Run a system command, ignore its output
   * 
   * @param command
   * @return 0 if success, -1 if failed
   */
  public static int system( String command )
  {
    int res = -1;
    try
    {
      Process p = Runtime.getRuntime().exec( command );
      p.waitFor();
      res = p.exitValue();
      return res;
    } catch ( IOException e1 )
    {} catch ( InterruptedException e2 )
    {}
    return -1;
  }

/**
 * Encode '&', '>', '<', '\"', '\'' 
 * 
 * @param source string
 * @return string with replaced chars
 */
  public static String htmlEncode( String str )
  {
    if ( str == null )
      return "";
    StringBuilder buf = new StringBuilder( str.length() );
    int len = str.length();
    for ( int i = 0; i < len; i++ )
    {
      char c = str.charAt( i );
      if ( c != '&' && c != '>' && c != '<' && c != '\"' && c != '\'' )
        buf.append( c );
      else
        buf.append( "&#" ).append( (int)c ).append( ";" );
    }
    return buf.toString();
  }

  /**
   * Decode a string encoded by the method above.
   * 
   * @param source
   *          string
   * @return decoded string
   */
  public static String htmlDecode( String str )
  {
    StringBuilder buf = new StringBuilder( str.length() );
    int len = str.length();
    for ( int i = 0; i < len; i++ )
    {
      char c = str.charAt( i );
      if ( c != '&' )
        buf.append( c );
      else
      {
        int end = str.indexOf( ';', i );
        c = (char)Integer.parseInt( str.substring( i + 1, end ) );
        buf.append( c );
        i = end;
      }
    }
    return buf.toString();
  }

  public static String makeQuery( HttpServletRequest request )
  {
    return makeQuery( request.getParameter( "ar_fall" ), request.getParameter( "ar_fnot" ),
        request.getParameter( "ar_furl" ), request.getParameter( "ar_fhost" ), request.getParameter( "ar_ftitle" ),
        request.getParameter( "ar_fcontent" ) );
  }

  public static String makeQuery( SolrParams request )
  {
    return makeQuery( request.get( "ar_fall" ), request.get( "ar_fnot" ), request.get( "ar_furl" ),
        request.get( "ar_fhost" ), request.get( "ar_ftitle" ), request.get( "ar_fcontent" ) );
  }

  public static String makeQuery( String all, String not, String url, String host, String title, String content )
  {
    String query = "";

    query = parseField( all, null, "+", query, true );
    query = parseField( url, "url", "", query, true );
    query = parseField( host, "host", "", query, true );
    query = parseField( title, "title", "", query, true );
    query = parseField( content, "content", "", query, true );
    query = parseField( not, null, "-", query, true );

    return query;
  }

  public static String parseField( String field, String name, String sign, String query, boolean enclose )
  {
    if ( field == null || field.length() == 0 )
      return query;
    boolean containsSpecialChars = StringUtils.containsAny( field, "*[]~?^" ) ;
    StringTokenizer tk = new StringTokenizer( field, " \"", true );
    String group = "", result = "", groupSign = "";
    boolean inQ = false;
    while ( tk.hasMoreTokens() )
    {
      String t = tk.nextToken();
      if ( t.charAt( 0 ) == ' ' )
        continue;
      if ( t.charAt( 0 ) == '\"' )
        if ( inQ ) // inside quotes, finish group
        {
          inQ = false;
          result += finishGroup( group, name, groupSign, sign, result.length() == 0 );
          group = "";
          groupSign = "";
          continue;
        } else
        // outside quotes
        {
          inQ = true;
          group = "\"";
          continue;
        }

      // else this is not a delimeter
      if ( inQ ) // inside quotes, add term to group
      {
        if ( group.length() > 1 )
          group += " " + t;
        else
          group += t;
      } else if ( ( t.charAt( 0 ) == '-' || t.charAt( 0 ) == '+' ) && t.length() == 1 ) // group sign
      {
        groupSign = t;
      } else
      // this is a separate term, possibly, with sign
      {
        String s = sign;
        if ( t.charAt( 0 ) == '-' || t.charAt( 0 ) == '+' )
        {
          s = t.substring( 0, 1 );
          t = t.substring( 1 );
        } else if ( groupSign.length() > 0 )
          s = groupSign;
        if ( sign.equals( "-" ) )
        {
          if ( result.length() > 0 )
            s = "OR ";
          else
            s = "";
        }
        // Put the term in quotes, this will help if it gets split later, like 7mm -> 7 mm.
        String qq = isTokenizable( t ) && t.indexOf( ':' ) < 0 && !containsSpecialChars ? "\"" : ""; // do not enquote field:term type
        if ( containsSpecialChars ) t = t.toLowerCase(); // It seems that special queries bypass normal processing in Solr
        if ( name != null )
          result += " " + s + name + ":" + qq + t + qq;
        else
          result += " " + s + qq + t + qq;
        groupSign = "";
      }
    }
    // forgive unclosed quotes and finish a started group, if any
    if ( inQ )
      result = finishGroup( group, name, groupSign, sign, result.length() == 0 );
    if ( result.length() > 0 )
    {
      String q1 = enclose ? "(" : "";
      String q2 = enclose ? ")" : "";
      if ( sign.equals( "-" ) )
        query = q1 + query + q2 + " NOT " + q1 + result + q2;
      else if ( query.length() > 0 )
        query += " AND " + q1 + result + q2;
      else
        query = q1 + result + q2;
    }
    return query;
  }

  static boolean isTokenizable( String string )
  {
    int len = string.length();
    boolean lowerCaseFound = false, upperCaseFound = false;

    for ( int i = 0; i < len; i++ )
    {
      char c = string.charAt( i );
      if ( Character.isDigit( c ) )
        return true;
      if ( Character.isUpperCase( c ) )
      {
        if ( lowerCaseFound )
          return true;
        upperCaseFound = true;
      }
      if ( Character.isLowerCase( c ) )
        lowerCaseFound = true;
      if ( !Character.isDigit( c ) && !Character.isLetter( c ) )
        return true;
    }
    return false;
  }

  static String finishGroup( String group, String name, String groupSign, String sign, boolean first )
  {
    group += "\"";
    if ( groupSign.length() == 0 )
      groupSign = sign;
    if ( sign.equals( "-" ) )
    {
      if ( !first )
        groupSign = "OR ";
      else
        groupSign = "";
    }
    if ( name != null )
      group = " " + groupSign + name + ":" + group;
    else
      group = " " + groupSign + group;
    if ( group.length() > 1 )
      return group;
    else
      return "";
  }

  static public String makeClause( String field, String value )
  {
    if ( value == null || value.length() == 0 )
      return "";
    String[] aa = value.split( " " );
    StringBuffer result = new StringBuffer();
    for ( int ii = 0; ii < aa.length; ii++ )
      if ( aa[ ii ].length() > 0 )
      {
        result.append( " " );
        result.append( field );
        result.append( ":" );
        result.append( aa[ ii ] );
      }
    if ( result.length() == 0 )
      return "";
    else
      return result.toString();
  }

  /*
   * static public void cleanQuery( Query query ) {
   * 
   * }
   */

  // Makes a filter query, given a set of <name>:<value> pairs
  static public String makeFQ( String[] ar_fs )
  {
    MultiHashMap mhm = new MultiHashMap();
    if ( ar_fs == null )
      return "";
    for ( String pair : ar_fs )
    {
      String[] aa = pair.split( ":", 2 );
      mhm.put( aa[ 0 ], aa[ 1 ] );
    }
    Collection<String> fields = mhm.keySet();
    String fq = "";
    // Fields are concatenated by AND, values by OR
    for ( String field : fields )
    {
      if ( fq.length() > 0 )
        fq += " AND ";
      fq += field + ":(";
      Collection<String> values = mhm.getCollection( field );
      String ors = "";
      for ( String value : values )
      {
        if ( ors.length() > 0 )
          ors += " OR ";
        ors += value;
      }
      fq += ors + ")";
    }
    return "fq=" + fq;
  }

  static public boolean sameText( String a, String b )
  {
    boolean emptyA = a == null || a.length() == 0;
    boolean emptyB = b == null || b.length() == 0;
    if ( emptyA && emptyB )
      return true;
    if ( emptyA || emptyB )
      return false; // the other one is not empty
    return a.equals( b );
  }

  static public String getTheme( HttpServletRequest request )
  {
    String theme = "blue";
    Cookie[] cookies = request.getCookies();

    if ( cookies != null )
      for ( int i = 0; i < cookies.length; i++ )
      {
        Cookie cookie = cookies[ i ];
        if ( "options".equals( cookie.getName() ) )
        {
          String encoded = cookie.getValue();
          String[] options = encoded.split( "\t" );
          for ( int j = 0; j < options.length; j++ )
          {
            if ( options[ j ].startsWith( "theme=" ) )
              return options[ j ].substring( 6 );
          }

        }
      }

    return theme;
  }

  static public String printPages( long total, long start, long hits, String url, String next, boolean usePages )
  {
    // remove &start= from the url
    if ( !usePages )
      url = removeParam( url, "start" );
    else
    {
      url = removeParam( url, "page" );
      start = ( start - 1 ) * hits - 1;
    }
    StringBuffer o = new StringBuffer();
    String n = "";
    if ( total <= 0 )
      return "";
    int pages = (int)( ( total - 1 ) / hits );
    if ( hits * pages < total )
      pages++;
    int current = (int)( ( start + 1 ) / hits ) + 1;

    int i = current - 5;
    if ( i < 1 )
      i = 1;
    int last = i + 9;

    if ( i > 1 )
    {
      String u = usePages ? url + "&page=1" : url + "&start=0";
      u = htmlEncode( u );
      o.append( "<a href=\'" + u + "\' class=\'page\'>1</a>\n" );
      o.append( "<span class='ellipsis'> ... </span>\n" );
    }
    for ( ; i <= last && i <= pages; i++ )
    {
      if ( i == current )
        o.append( "<span class=\'thispage\'>" + i + "</span>" );
      else
      {
        String u = usePages ? url + "&page=" + i : url + "&start=" + ( ( i - 1 ) * hits );
        u = htmlEncode( u );
        o.append( "<a href=\'" + u + "\' class=\'page\' onclick=\'return linkClick( event );\' >" + i + "</a>\n" );
        if ( current == i - 1 )
          n = "<a href=\'" + u + "\' class=\'next\' onclick=\'return linkClick( event );\'>" + next + "</a>\n";
      }
    }
    if ( i < pages )
    {
      o.append( "<span class='ellipsis'> ... </span>\n" );
      String u = usePages ? url + "&page=" + pages : url + "&start=" + ( ( pages - 1 ) * hits );
      u = htmlEncode( u );
      o.append( "<a href=\'" + u + "\' class=\'page\'>" + pages + "</a>\n" );
    }
    o.append( n );
    String out = o.toString();
    return out;
  }

  static public String nextPage( long total, long start, long hits, String url )
  {
    url = removeParam( url, "start" );
    if ( start + hits <= total )
      return url + "&start=" + ( start + hits );
    else
      return null;
  }

  // Remove parameter from the URL
  static public String removeParam( String url, String param )
  {
    while ( url.indexOf( param + "=" ) >= 0 )
    {
      int e = 0;
      String a;
      if ( url.indexOf( "&" + param + "=" ) >= 0 )
        a = "&" + param + "=";
      else
      {
        a = param + "=";
        e = 1;
      }
      int pos = url.indexOf( a );
      int end = url.indexOf( "&", pos + param.length() );
      String u;
      if ( end > 0 )
        u = url.substring( 0, pos ) + url.substring( end + e );
      else
        u = url.substring( 0, pos );
      url = u;
    }
    return url;
  }

  // True if this query has this clause. Assumes that the query has other parameters.
  static public boolean hasClause( String query, String clause )
  {
    String midClause = clause + "&";
    return query.indexOf( midClause ) >= 0 || query.endsWith( clause );
  }

  // Remove a clause from a query. Assumes that the query has other parameters.
  static public String removeClause( String query, String clause )
  {
    String midClause = clause + "&";
    String lastClause = "&" + clause;
    if ( query.indexOf( midClause ) >= 0 )
      return query.replaceAll( midClause, "" );
    if ( query.indexOf( lastClause ) >= 0 )
      return query.replaceAll( lastClause, "" );
    return query;
  }

  static public String getCanonicalURL( String[] indexPages, String url )
  {
    // if ( true ) return url ; // !!! Disable
    String canonicalUrl = url;
    if ( url == null || indexPages == null || indexPages.length == 0 )
      return url;

    for ( int j = 0; j < indexPages.length; j++ )
    {
      if ( url.endsWith( indexPages[ j ] ) )
      {
        canonicalUrl = url.substring( 0, url.length() - indexPages[ j ].length() + 1 );
        break;
      }
    }

    // if ( canonicalUrl.endsWith( "/" ) )
    // canonicalUrl = canonicalUrl.substring( 0, canonicalUrl.length() - 1 ) ;

    return canonicalUrl;
  }

  static public IndexRootDB connect( Configuration config, boolean servletMode ) throws Exception
  {
    ConfigList cfg = new ConfigList();
    String database = config.get( "arch.database" );
    String target = config.get( "arch.target.db" );
    String driver = config.get( "arch.db.driver" );

    if ( servletMode && target != null && target.contains( ":embedded" ) )
      return null;
    cfg.set( "database", database );
    cfg.set( "target.db", target );
    cfg.set( "db.driver", driver );
    cfg.finalise();

    // if ( LOG.isDebugEnabled() )
    LOG.debug( "Utils.connect, database: " + database + " target: " + target + " driver: " + driver );

    DBInterfaceFactory factory = DBInterfaceFactory.get( config );
    DBInterface intrf = factory.get( database );

    IndexRootDB db = intrf.newIndexRootDB( cfg, servletMode );
    return db;
  }

  /**
   * Split a space separated string into a set.
   * 
   * @param line
   *          - configuration parameter name
   * @return a set containing separated tokens
   */
  public static Set<String> splitToSet( String line )
  {
    if ( line == null )
      return null;
    StringTokenizer tokens = new StringTokenizer( line, " \t\n\r" );
    Set<String> set = new HashSet<String>();
    while ( tokens.hasMoreTokens() )
      set.add( tokens.nextToken() );
    if ( set.size() > 0 )
      return set;
    else
      return null;
  }

  /**
   * Split a space separated string into an ArrayList.
   * 
   * @param line
   *          - configuration parameter name
   * @return a set containing separated tokens
   */
  public static ArrayList<String> splitToArray( String line )
  {
    if ( line == null )
      return null;
    StringTokenizer tokens = new StringTokenizer( line, " \t\n\r" );
    ArrayList<String> array = new ArrayList<String>();
    while ( tokens.hasMoreTokens() )
      array.add( tokens.nextToken() );
    if ( array.size() > 0 )
      return array;
    else
      return null;
  }

  /**
   * Returns a subset of tokens that are in the set and the line.
   * 
   * @param param
   *          - configuration parameter name
   * @return and array of compiled IP address patterns.
   */
  public static ArrayList<String> subSet( Set<String> set, String line )
  {
    if ( set == null && ( line == null || line.equalsIgnoreCase( "all" ) ) )
      return null;
    ArrayList<String> array = new ArrayList<String>();
    if ( line == null || line.equalsIgnoreCase( "all" ) ) // add whole set
      for ( String token : set )
        array.add( token );
    else
    {
      StringTokenizer tokens = new StringTokenizer( line, " \t\n\r" );
      while ( tokens.hasMoreTokens() )
      {
        String token = tokens.nextToken();
        if ( set == null || set.contains( token ) )
          array.add( token );
      }
    }
    return array;
  }

  /**
   * Creates a query filter for given access limitations.
   * 
   * @param request
   *          - HttpServletRequest object of the request
   * @param sites
   *          - sites this request is allowed to search.
   * @param areas
   *          - sites this request is allowed to search.
   * @param users
   *          - search under these user names.
   * @param groups
   *          - search under these group names.
   */
  public static String makeFilter( HttpServletRequest request, Set<String> allowedSites, Set<String> allowedAreas,
      Set<String> allowedUsers, Set<String> allowedGroups )
  {
    StringBuilder fq = new StringBuilder();
    String users = request.getParameter( "ar_user" );
    String groups = request.getParameter( "ar_groups" );
    String sites = request.getParameter( "ar_site" );
    String areas = request.getParameter( "ar_area" );
    ArrayList<String> userS = subSet( allowedUsers, users );
    ArrayList<String> groupS = subSet( allowedGroups, groups );
    ArrayList<String> siteS = subSet( allowedSites, sites );
    ArrayList<String> areaS = subSet( allowedAreas, areas );
    if ( ( userS != null && userS.size() == 0 ) || ( groupS != null && groupS.size() == 0 )
        || ( siteS != null && siteS.size() == 0 ) || ( areaS != null && areaS.size() == 0 ) )
      return "ar_user:impossible_query";
    return makeFilter( siteS, areaS, userS, groupS );
  }

  /**
   * Creates a query filter for given access limitations.
   * 
   * @param sites
   *          - sites this request is allowed to search.
   * @param areas
   *          - sites this request is allowed to search.
   * @param users
   *          - search under these user names.
   * @param groups
   *          - search under these group names.
   */
  public static String makeFilter( String sites, String areas, String users, String groups )
  {
    ArrayList<String> siteS = splitToArray( sites );
    ArrayList<String> areaS = splitToArray( areas );
    ArrayList<String> userS = splitToArray( users );
    ArrayList<String> groupS = splitToArray( groups );
    return makeFilter( siteS, areaS, userS, groupS );
  }

  /**
   * Creates a query filter for given access limitations.
   * 
   * @param sites
   *          - sites this request is allowed to search.
   * @param areas
   *          - sites this request is allowed to search.
   * @param users
   *          - search under these user names.
   * @param groups
   *          - search under these group names.
   */
  public static String makeFilter( ArrayList<String> sites, ArrayList<String> areas, ArrayList<String> users,
      ArrayList<String> groups )
  {
    StringBuilder fq = new StringBuilder();
    if ( groups != null && !groups.contains( "public" ) )
      groups.add( "public" );
    boolean allSites = sites == null || ( sites.size() == 1 && sites.get( 0 ).equalsIgnoreCase( "all" ) );
    boolean allAreas = areas == null || ( areas.size() == 1 && areas.get( 0 ).equalsIgnoreCase( "all" ) );
    String start = " (";
    if ( !allSites )
      if ( addClause( fq, "ar_site", sites, start, ")" ) )
        start = " AND (";
    if ( !allAreas )
      if ( addClause( fq, "ar_area", areas, start, ")" ) )
        start = " AND (";
    if ( addClause( fq, "ar_user", users, start, null ) )
      start = " OR ";
    if ( !addClause( fq, "ar_groups", groups, start, ")" ) && start.equals( " OR " ) )
      fq.append( ")" );
    return fq.toString();
  }

  /**
   * Adds a field clause to filtering query.
   * 
   * @param fq
   *          - the query being built.
   * @param filed
   *          - field name.
   * @param values
   *          - a string of space delimited field values.
   * @param start
   *          - add "AND (" if the query body is not empty.
   * @param finish
   *          - add ")" if the query body is not empty.
   * @return true if a clause has been added
   */
  public static boolean addClause( StringBuilder fq, String field, ArrayList<String> values, String start, String finish )
  {
    // this could cause trouble if users or groups were null, but this never happens
    if ( values == null || values.size() == 0 )
      return false;
    StringBuilder clause = new StringBuilder();
    for ( String value : values )
      if ( value.trim().length() > 0 )
      {
        if ( clause.length() > 0 )
          clause.append( " OR " );
        clause.append( field );
        clause.append( ":" );
        clause.append( value.trim() );
      }
    if ( clause.length() > 0 )
    {
      if ( start != null )
        if ( fq.length() > 0 )
          fq.append( start );
        else
          fq.append( "(" );
      else
        fq.append( " " );
      fq.append( clause );
      if ( finish != null )
        fq.append( ")" );
      return true;
    }
    return false;
  }

  static public String addWithDelim( String str1, String str2 ) // assuming str1 never null
  {
    if ( str2 == null || str2.length() == 0 )
      return str1;
    if ( !str1.endsWith( "/" ) && !str2.startsWith( "/" ) )
      return str1 + "/" + str2;
    if ( str1.endsWith( "/" ) && str2.startsWith( "/" ) )
      return str1 + str2.substring( 1 );
    return str1 + str2;
  }

  static public ConfigList newConfigList( String target, String driver ) throws IOException
  {
    ConfigList cfg = ConfigList.newConfigList();
    cfg.set( "target.db", target );
    cfg.set( "db.driver", driver );
    cfg.finalise();
    return cfg;
  }

  public static void gc()
  {
    Runtime r = Runtime.getRuntime();
    long was = r.freeMemory();
    r.gc();
    long now = r.freeMemory();
    LOG.debug( "Garbage collection: was available " + was + ", now " + now + ", freed " + ( now - was ) );
  }

  public static void disconnectFilters() throws Exception
  {
    if ( indexFilter != null )
      indexFilter.disconnect();
    if ( prefixFilter != null )
      prefixFilter.disconnect();
    if ( scoringFilter != null )
      scoringFilter.disconnect();
    if ( scanUtil != null )
      scanUtil.disconnect();
    if ( dedupReducer != null )
      dedupReducer.disconnect();
    if ( dbFilter != null )
      dbFilter.disconnect();
  }

  /*
   * Debug output of request parameters
   */
  public static void dumpRequest( Log LOG, SolrQueryRequest req )
  {
    if ( LOG.isDebugEnabled() && req.getContext().get( "HttpServletRequest" ) != null )
    {
      String par = "";
      HttpServletRequest hreq = (HttpServletRequest)req.getContext().get( "HttpServletRequest" );
      Enumeration paramNames = hreq.getParameterNames();
      while ( paramNames.hasMoreElements() )
      {
        String paramName = (String)paramNames.nextElement();
        String[] paramValues = hreq.getParameterValues( paramName );
        for ( int ii = 0; ii < paramValues.length; ii++ )
          par += "\n" + paramName + " = " + paramValues[ ii ];
      }
      LOG.debug( "Request: " + par );
    }
  }

  public static String notNull( String str )
  {
    if ( str != null )
      return str;
    else
      return "";
  }

  public static String notEmpty( String a )
  {
    if ( a != null && a.length() > 0 )
      return a;
    else
      return null;
  }

  public static String getOption( String name, String[] args, String def ) throws Exception
  {
    int i = getIndex( name, args );
    if ( i < 0 )
      return def;
    if ( i == args.length - 1 || args[ i + 1 ].charAt( 0 ) == '-' )
      throw new Exception( "Invalid option value for " + name );
    return args[ i + 1 ];
  }

  public static boolean getOption( String name, String[] args, boolean def ) throws Exception
  {
    int i = getIndex( name, args );
    if ( i < 0 )
      return def;
    if ( i == args.length - 1 || args[ i + 1 ].charAt( 0 ) == '-' )
      return true;
    String s = args[ i + 1 ].toLowerCase();
    return s.equals( "true" ) || s.equals( "1" ) || s.equals( "on" );
  }

  public static int getIndex( String name, String[] args )
  {
    for ( int i = 0; i < args.length; i++ )
      if ( args[ i ].equals( name ) )
        return i;
    return -1;
  }

  public static String readFileAsString( String fileName ) throws IOException
  {
    byte[] buffer = new byte[ (int)new File( fileName ).length() ];
    BufferedInputStream f = null;
    try
    {
      f = new BufferedInputStream( new FileInputStream( fileName ) );
      f.read( buffer );
    } finally
    {
      if ( f != null )
        try
        {
          f.close();
        } catch ( IOException ignored )
        {}
    }
    return new String( buffer );
  }

  public static boolean URL2File( String fileName, String smurl )
  {
    OutputStream out = null;
    try
    {
      URL url = new URL( smurl );
      URLConnection connection = url.openConnection();
      InputStream in = connection.getInputStream();
      out = new FileOutputStream( fileName );
      byte[] buf = new byte[ 1024 ];
      while ( true )
      {
        int len = in.read( buf );
        if ( len == -1 )
          break;
        out.write( buf, 0, len );
      }
      in.close();
      out.close();
    } catch ( Exception e )
    {
      LOG.error( "ERROR downloading " + smurl + ": " + e.getMessage() );
      if ( out != null )
        try
        {
          out.close();
        } catch ( Exception ee )
        {}
      ;
      return false;
    }
    return true;
  }

  public static String cutOut( String src, String start, String end, boolean repeat )
  {
    return cutOut( src, start, end, repeat, true, true );
  }

  public static String cutOut( String src, String start, String end, boolean repeat, boolean removeStart,
      boolean removeEnd )
  {
    int startPos = 0;
    do
    {
      int pos1 = src.indexOf( start, startPos );
      if ( pos1 < 0 )
        return src;
      int pos2 = src.indexOf( end, pos1 + start.length() );
      if ( pos2 < 0 )
        return src;
      String result = "";
      if ( !removeStart )
        pos1 += start.length();
      if ( pos1 > 0 )
        result = src.substring( 0, pos1 );
      int endLength = end.length();
      if ( !removeEnd )
        endLength = 0;
      if ( pos2 < src.length() - end.length() )
        result += src.substring( pos2 + endLength );
      src = result;
      startPos = removeEnd ? pos1 : pos1 + end.length();
    } while ( repeat );

    return src;
  }

  public static void startJetty( String nutchHome, ConfigList cfg ) throws Exception
  {
    String portStr = cfg.getInherited( "jetty.http.port", null, "JETTY_PORT" );
    String jettyOpts = cfg.getInherited( "jetty.opts", null, "JETTY_OPTS" );
    if ( portStr == null )
      return;
    int port = Integer.parseInt( portStr );

    // prepare an NPN module for the current java
    String version = System.getProperty( "java.version" ) ;
    String needNPN = "npn-" + version + ".mod" ;
    File folder = new File( nutchHome + "/jetty/modules/npn" ) ;
    if ( folder.exists() )
    {
      File[] listOfFiles = folder.listFiles();
      File prevFile = null;
      Arrays.sort( listOfFiles );
      for ( File file : listOfFiles )
        {
          if ( !file.isFile() )
          continue;
          if ( file.getName().equals( needNPN ) )
            {
              prevFile = null;
              break ;
            }
          if ( file.getName().compareTo( needNPN ) > 0 )
            {
              if ( prevFile == null )
                throw new Exception( "No Jetty NPN module exists for your java version " + version );
              renameNPN( prevFile, needNPN, version, nutchHome );
              prevFile = null;
              break;
            }
          prevFile = file;
        }
      if ( prevFile != null )
        renameNPN( prevFile, needNPN, version, nutchHome );
    }
    // check if jetty is running already
    ServerSocket ss = null;
    DatagramSocket ds = null;
    try
    {
      ss = new ServerSocket( port );
      ss.setReuseAddress( true );
      ds = new DatagramSocket( port );
      ds.setReuseAddress( true );
      ds.close();
      ds = null;
      if ( ss != null )
        try
        {
          ss.close();
        } catch ( IOException e )
        {}
      ss = null;
      LOG.info( " Starting jetty on port " + port + "." );
      ArrayList<String> opts = new ArrayList<String>();
      opts.add( "java" );
      opts.add( "-Dsolr.solr.home=" + nutchHome );
      opts.add( "-Djetty.port=" + port );
      if ( jettyOpts != null )
      {
        String[] tokens = jettyOpts.split( " " );
        for ( String token : tokens )
          opts.add( token );
      }
      opts.add( "-jar" );
      opts.add( "start.jar" );
      String[] optsAr = opts.toArray( new String[ opts.size() ] );
      ProcessBuilder pb = new ProcessBuilder( optsAr );
      pb.directory( new File( nutchHome + "/jetty" ) );
      File log = new File( nutchHome + "/jetty/logs/jetty.log" );
      pb.redirectErrorStream( true );
      pb.redirectOutput( Redirect.appendTo( log ) );
      Process p = pb.start();
      assert pb.redirectInput() == Redirect.PIPE;
      assert pb.redirectOutput().file() == log;
      assert p.getInputStream().read() == -1;
    } catch ( IOException e )
    {
      LOG.info( " Can't start jetty, must be running already?" );
    } finally
    {
      if ( ds != null )
        ds.close();
      if ( ss != null )
        try
        {
          ss.close();
        } catch ( IOException e )
        {}
    }
  }

  public static void renameNPN( File prevFile, String needNPN, String version, String nutchHome )
  {
    LOG.warn( "No NPN module found for java version " + version + ", renaming " + prevFile.getName() + " to " + needNPN );
    prevFile.renameTo( new File( nutchHome + "/jetty/modules/npn/" + needNPN ) );
  }

}
