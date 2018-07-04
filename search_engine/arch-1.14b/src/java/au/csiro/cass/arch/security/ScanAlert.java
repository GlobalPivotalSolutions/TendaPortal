/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A container for security scanning related configuration parameters
 * 
 */

package au.csiro.cass.arch.security;

public class ScanAlert
{
  public static final int      SAFE = 0 ; // Nothing suspicious found 
  public static final int    UNSURE = 1 ; // May be a vulnerability, should be reviewed 
  public static final int    UNSAFE = 2 ; // A vulnerability found
  public static final int    THREAT = 3 ; // A hostile object
  public static final String codes[] = { "SAFE", "UNSURE", "UNSAFE", "THREAT" } ;
  
  public int                       code ;
  public String                 message ;
  public String                     url ;
  public String                    site ;
  
  public ScanAlert() {}

  public ScanAlert( String site, String url, String code, String message )
  {
	this( site, url, THREAT, message ) ;
    if ( code.equalsIgnoreCase( "UNSAFE" ) ) this.code = UNSAFE ;  
    else if ( code.equalsIgnoreCase( "UNSURE" ) ) this.code = UNSURE ;  
    else if ( code.equalsIgnoreCase( "SAFE" ) ) this.code = SAFE ;
  }

  public ScanAlert( String site, String url, int code, String message )
  {
    this.code = code ;  
    this.site = site ;
    this.url = url ;
    this.message = message ;
  }

  /**
   * @return the code
   */
  public int getCode() {
	return code;
  }

  /**
   * @param code the code to set
   */
  public void setCode(int code) {
	this.code = code;
  }

  /**
   * @return the message
   */
  public String getMessage() {
	return message;
  }

  /**
   * @param message the message to set
   */
  public void setMessage(String message) {
	this.message = message;
  }

/**
 * @return the url
 */
public String getUrl() {
	return url;
}

/**
 * @param url the url to set
 */
public void setUrl(String url) {
	this.url = url;
}

/**
 * @return the site
 */
public String getSite() {
	return site;
}

/**
 * @param site the site to set
 */
public void setSite(String site) {
	this.site = site;
} 
}
