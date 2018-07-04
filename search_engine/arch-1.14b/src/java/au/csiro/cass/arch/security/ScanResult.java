/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A container for security scanning results
 * 
 */

package au.csiro.cass.arch.security;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ScanResult
{
  public ArrayList<ScanAlert>         alerts ; // alerts related to this page
  public int                            code ; // highest alert code
  public int                   minAlertLevel ; // retain alerts with level at least as high as this
  public long                         outCRC ; // CRC code for normalised output page 
  public long                         srcCRC ; // CRC code for normalised source page
  public boolean                 scriptFound ; // true if page contains a script 
  public boolean                   formFound ; // true if page contains a form
  public boolean                  notChanged ; // true if the page has not been changed since previous scan
  public String                          url ; // url of the scanned page
  
  public Set<String>                   links ; // links contained in the page
  public Set<String>                newLinks ; // links added to the page since last scan
  public Set<String>            removedLinks ; // links removed since last scan
  
  public ScanResult()
  {
    alerts = new ArrayList<ScanAlert>() ;
    links = new HashSet<String>() ;
    newLinks = new HashSet<String>() ;
    removedLinks = new HashSet<String>() ;
  }
  
  public ScanResult( int alertLevel )
  {
	this() ;
	minAlertLevel = alertLevel ;
  }

  /**
   * @return the alerts
   */
  public ArrayList<ScanAlert> getAlerts() {
	return alerts;
  }

  /**
   * @param alerts the alerts to set
   */
  public void addAlert( ScanAlert alert )
  {
	if ( alert.code < minAlertLevel ) return ; // ignore
	if ( code < alert.code ) code = alert.code ;
	alerts.add( alert ) ;
  }

  /**
   * @return the highest alert code
   */
  public int getCode() {
	return code;
  }

  /**
   * @param code the code to set
   */
  public void setCode( int code ) {
	this.code = code;
  }

  /**
   * @return the outCRC
   */
  public long getOutCRC() {
	return outCRC;
  }

  /**
   * @param outCRC the outCRC to set
   */
  public void setOutCRC(long outCRC) {
	this.outCRC = outCRC;
  }

  /**
   * @return the srcCRC
   */
  public long getSrcCRC() {
	return srcCRC;
  }

  /**
   * @param srcCRC the srcCRC to set
   */
  public void setSrcCRC(long srcCRC) {
	this.srcCRC = srcCRC;
  }

  /**
   * @return the scriptFound
   */
  public boolean isScriptFound() {
	return scriptFound;
  }

  /**
   * @param scriptFound the scriptFound to set
   */
  public void setScriptFound(boolean scriptFound) {
	this.scriptFound = scriptFound;
  }

  /**
   * @return the formFound
   */
  public boolean isFormFound() {
	return formFound;
  }

  /**
   * @param formFound the formFound to set
   */
  public void setFormFound(boolean formFound) {
	this.formFound = formFound;
  }

  /**
   * @return the newLinks
   */
  public Set<String> getNewLinks() {
	return newLinks;
  }

  /**
   * @param url the link to add
   */
  public void addNewLink( String url ) {
	this.newLinks.add( url ) ;
  }

  /**
   * @return the removedLinks
   */
  public Set<String> getRemovedLinks() {
	return removedLinks;
  }

  /**
   * @param url the removed link to add
   */
  public void addRemovedLinks( String url ) {
	this.removedLinks.add( url ) ;
  }

  /**
   * @return the links
   */
  public Set<String> getLinks() {
	return links;
  }

  /**
   * @param links the links to set
   */
  public void addLink( String link ) {
	this.links.add( link ) ;
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
}
