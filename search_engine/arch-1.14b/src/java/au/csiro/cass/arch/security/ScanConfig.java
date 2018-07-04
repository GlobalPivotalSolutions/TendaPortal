/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A container for security scanning related configuration parameters
 * 
 */


package au.csiro.cass.arch.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import au.csiro.cass.arch.utils.ConfigList;

public class ScanConfig
{
	public Set<String>           ignoredLinks ; // links to ignore, such as in header and footer in each page
	public Set<String>           contentTypes ; // content types to scan
	public Set<String>              fileTypes ; // file types to scan
    public Set<String>      pruneContentTypes ; // content types to prune
    public Set<String>         pruneFileTypes ; // file types to prune
    public Set<String> pruneContentTypesAfter ; // content types to prune after parsing
    public Set<String>    pruneFileTypesAfter ; // file types to prune after parsing
	public Set<String>        srcContentTypes ; // source content types to scan
	public Set<String>           srcFileTypes ; // source file types to scan
	public boolean              normalizeURLs ; // remove query string before processing
    public ArrayList<String[]>    ignoredBits ; // starts and ends of bits to cut out before scanning
    public ArrayList<String[]> ignoredBitsAfter ; // starts and ends of bits to cut out after parsing
	public ArrayList<String[]>    scriptEdges ; // starts and ends of script fragments in text
	public int                  minScriptSize ; // ignore scripts smaller than this size
	public ArrayList<String>           ignore ; // ignore scripts that contain these fragments
	public ArrayList<String[]>          alert ; // alert of pages that contain these fragments
	public ConfigList                  config ; // the original configuration
	public String                sourceAccess ; // URL to supply source code
	public boolean          reportBrokenLinks ; // report broken links
	public boolean             reportNewPages ; // report any new pages
	public boolean           reportNewScripts ; // report new pages with scripts
	public boolean         reportChangedPages ; // report changed pages
	public boolean       reportChangedScripts ; // report changed pages with scripts
	public boolean             reportNewForms ; // report new pages with forms
	public boolean         reportChangedForms ; // report changed pages with forms
	public boolean          reportLinkChanges ; // report changes in links, like new or removed links
	public int                     alertLevel ; // ignore alerts with level lower than this
	public boolean                scanEnabled ; // false if security scanning is disabled
	
	public ScanConfig( ConfigList cfg ) throws Exception
	{
	  config = cfg ;
	  ignoredLinks = new HashSet<String>() ;	
	  contentTypes = new HashSet<String>() ;	
	  fileTypes = new HashSet<String>() ;	
      pruneContentTypes = new HashSet<String>() ;   
      pruneFileTypes = new HashSet<String>() ;  
      pruneContentTypesAfter = new HashSet<String>() ;   
      pruneFileTypesAfter = new HashSet<String>() ;  
	  srcContentTypes = new HashSet<String>() ;	
	  srcFileTypes = new HashSet<String>() ;	
      ignoredBits = new ArrayList<String[]>() ; 
      ignoredBitsAfter = new ArrayList<String[]>() ; 
	  scriptEdges = new ArrayList<String[]>() ;	
	  ignore = new ArrayList<String>() ;	
	  alert = new ArrayList<String[]>() ;
	  
	  setSet( "scan.ignore.links", ignoredLinks, false ) ;
	  setSet( "scan.content.types", contentTypes ) ;
	  setSet( "scan.file.types", fileTypes ) ;
      setSet( "prune.content.types", pruneContentTypes ) ;
      setSet( "prune.file.types", pruneFileTypes ) ;
      setSet( "prune.content.types.after", pruneContentTypesAfter ) ;
      setSet( "prune.file.types.after", pruneFileTypesAfter ) ;
	  setSet( "scan.src.content.types", srcContentTypes ) ;
	  setSet( "scan.src.file.types", srcFileTypes ) ;
      setIgnoreBits( "scan.ignore.bits", ignoredBits ) ;
      setIgnoreBits( "scan.ignore.bits.after", ignoredBitsAfter ) ;
	  setArray( "scan.script.edges", scriptEdges ) ;
	  setSet( "scan.ignore.scripts", ignore ) ;
	  setArray( "scan.alert", alert ) ;
	  
	  reportBrokenLinks = config.getInherited( "scan.report.broken.links", true ) ;
	  reportChangedForms = config.getInherited( "scan.report.changed.forms", true ) ;
	  reportChangedScripts = config.getInherited( "scan.report.changed.scripts", true ) ;
	  reportChangedPages = config.getInherited( "scan.report.changed.pages", true ) ;
	  reportNewForms = config.getInherited( "scan.report.new.forms", true ) ;
	  reportNewScripts = config.getInherited( "scan.report.new.scripts", true ) ;
	  reportNewPages = config.getInherited( "scan.report.new.pages", true ) ;
	  reportLinkChanges = config.getInherited( "scan.report.link.changes", true ) ;

	  normalizeURLs = config.getInherited( "scan.normalize.urls", true ) ;
	  minScriptSize = config.getInherited( "scan.min.script.size", -1 ) ;
	  sourceAccess = config.getInherited( "scan.source.access.url", config.get( "url", "", "SITE_URL" ) + "/source/getsource.php?url=" ) ;
	  String level = cfg.getInherited( "scan.alert.level", "UNSURE" ).toLowerCase() ;
	  switch ( level.charAt( 3 ) )
	  {
	    case 'e' : if ( level.charAt( 0 ) == 's' ) alertLevel = ScanAlert.SAFE ; else alertLevel = ScanAlert.THREAT ; break ;
	    case 'u' : alertLevel = ScanAlert.UNSURE ; break ;
	    case 'a' : alertLevel = ScanAlert.UNSAFE ; break ;
	    default: throw new Exception( "Wrong definition of scan.alert.level in " + cfg.getFileName() ) ;
	  }
	  scanEnabled = config.getInherited( "scan.enabled", true ) ;
	}
	
	void setSet( String name, Collection<String> set )
	{ setSet( name, set, true ) ; }
	
	void setSet( String name, Collection<String> set, boolean lowerCase  )
	{
	  String[] arr = config.getAllInherited( name ) ;
	  if ( arr != null )
	  for ( String ea : arr )
	  {
		String separator = ea.indexOf( '|' ) > 0 ? "\\|" : " " ;
		if ( lowerCase ) ea = ea.toLowerCase() ;
		String[] fields = ea.split( separator ) ;
		for ( String field : fields )
			             set.add( field.trim() ) ;
	  }
	}
	
	void setMap( String name, Map<String,String> map ) throws Exception
	{
	  String[] arr = config.getAllInherited( name ) ;
	  if ( arr != null )
	  for ( String ea : arr )
	  {
	    String separator = ea.indexOf( '|' ) > 0 ? "\\|" : " " ;  
	    String[] fields = ea.toLowerCase().split( separator ) ;
		if ( fields.length != 2 )
			throw new Exception( "Wrong " + name + " parameter: " + ea + " in file " + config.fileName ) ;
		map.put( fields[ 0 ].trim(), fields[ 1 ].trim() ) ;
	  }
	}
	
	void setArray( String name, Collection<String[]> set )
	{
	  String[] arr = config.getAllInherited( name ) ;
	  if ( arr != null )
	  for ( String ea : arr )
	  {
	    String separator = ea.indexOf( '|' ) > 0 ? "\\|" : " " ;  
	    String[] fields = ea.toLowerCase().split( separator ) ;
	    for ( int i = 0 ; i < fields.length ; i++  ) fields[ i ] = fields[ i ].trim() ; 
	    set.add( fields ) ;
	  }
	}

	
	void setIgnoreBits( String name, Collection<String[]> set ) throws Exception
	{
	  String[] arr = config.getAllInherited( name ) ;
	  if ( arr != null )
	  for ( String ea : arr )
	  {
	    String separator = "\\|" ;
	    String r = ea.trim() ;
	    String start = r.substring( 0, 1 ) ;
	    String end = r.substring( r.length() - 1 ) ;
	    if ( start.charAt(0) != '{' && start.charAt(0) != '[' )
			throw new Exception( "Wrong " + start + " range start in " + r + " in file " + config.fileName ) ;
	    if ( end.charAt(0) != '}' && end.charAt(0) != ']' )
			throw new Exception( "Wrong " + end + " range end in " + r + " in file " + config.fileName ) ;
	    r = r.substring( 1, r.length() - 1 ) ;
	    String[] bits = r.split( separator ) ;
		if ( bits.length != 2 )
			throw new Exception( "Wrong scan.ignore.bits parameter (check separators): " + ea + " in file " + config.fileName ) ;

	    String[] fields = new String[ 4 ] ;
	    fields[ 0 ] = start ; fields[ 3 ] = end ;
	    fields[ 1 ] = bits[ 0 ].trim() ;
	    fields[ 2 ] = bits[ 1 ].trim() ;
	    set.add( fields ) ;
	  }
	}

	/**
	 * @return the ignoredLinks
	 */
	public Set<String> getIgnoredLinks() {
		return ignoredLinks;
	}

	/**
	 * @param ignoredLinks the ignoredLinks to set
	 */
	public void setIgnoredLinks(Set<String> ignoredLinks) {
		this.ignoredLinks = ignoredLinks;
	}


	/**
	 * @return the normalizeURL
	 */
	public boolean isNormalizeURLs() {
		return normalizeURLs;
	}

	/**
	 * @param normalizeURL the normalizeURL to set
	 */
	public void setNormalizeURLs(boolean normalizeURLs) {
		this.normalizeURLs = normalizeURLs;
	}

	/**
	 * @return the ignoredBits
	 */
	public ArrayList<String[]> getIgnoaredBits() {
		return ignoredBits;
	}

	/**
	 * @param ignoredBits the ignoredBits to set
	 */
	public void setIgnoredBits(ArrayList<String[]> ignoredBits) {
		this.ignoredBits = ignoredBits;
	}

	/**
	 * @return the scriptEdges
	 */
	public ArrayList<String[]> getScriptEdges() {
		return scriptEdges;
	}

	/**
	 * @param scriptEdges the scriptEdges to set
	 */
	public void setScriptEdges(ArrayList<String[]> scriptEdges) {
		this.scriptEdges = scriptEdges;
	}

	/**
	 * @return the minScriptSize
	 */
	public int getMinScriptSize() {
		return minScriptSize;
	}

	/**
	 * @param minScriptSize the minScriptSize to set
	 */
	public void setMinScriptSize(int minScriptSize) {
		this.minScriptSize = minScriptSize;
	}

	/**
	 * @return the ignore
	 */
	public ArrayList<String> getIgnore() {
		return ignore;
	}

	/**
	 * @param ignore the ignore to set
	 */
	public void setIgnore(ArrayList<String> ignore) {
		this.ignore = ignore;
	}

	/**
	 * @return the alert
	 */
	public ArrayList<String[]> getAlert() {
		return alert;
	}

	/**
	 * @param alert the alert to set
	 */
	public void setAlert(ArrayList<String[]> alert) {
		this.alert = alert;
	}

	/**
	 * @return the config
	 */
	public ConfigList getConfig() {
		return config;
	}

	/**
	 * @param config the config to set
	 */
	public void setConfig(ConfigList config) {
		this.config = config;
	}

	/**
	 * @return the sourceAccess
	 */
	public String getSourceAccess() {
		return sourceAccess;
	}

	/**
	 * @param sourceAccess the sourceAccess to set
	 */
	public void setSourceAccess(String sourceAccess) {
		this.sourceAccess = sourceAccess;
	}

	/**
	 * @return the reportBrokenLinks
	 */
	public boolean isReportBrokenLinks() {
		return reportBrokenLinks;
	}

	/**
	 * @param reportBrokenLinks the reportBrokenLinks to set
	 */
	public void setReportBrokenLinks(boolean reportBrokenLinks) {
		this.reportBrokenLinks = reportBrokenLinks;
	}

	/**
	 * @return the reportNewPages
	 */
	public boolean isReportNewPages() {
		return reportNewPages;
	}

	/**
	 * @param reportNewPages the reportNewPages to set
	 */
	public void setReportNewPages(boolean reportNewPages) {
		this.reportNewPages = reportNewPages;
	}

	/**
	 * @return the reportNewScripts
	 */
	public boolean isReportNewScripts() {
		return reportNewScripts;
	}

	/**
	 * @param reportNewScripts the reportNewScripts to set
	 */
	public void setReportNewScripts(boolean reportNewScripts) {
		this.reportNewScripts = reportNewScripts;
	}

	/**
	 * @return the reportChangedPages
	 */
	public boolean isReportChangedPages() {
		return reportChangedPages;
	}

	/**
	 * @param reportChangedPages the reportChangedPages to set
	 */
	public void setReportChangedPages(boolean reportChangedPages) {
		this.reportChangedPages = reportChangedPages;
	}

	/**
	 * @return the reportChangedScripts
	 */
	public boolean isReportChangedScripts() {
		return reportChangedScripts;
	}

	/**
	 * @param reportChangedScripts the reportChangedScripts to set
	 */
	public void setReportChangedScripts(boolean reportChangedScripts) {
		this.reportChangedScripts = reportChangedScripts;
	}

	/**
	 * @return the reportNewForms
	 */
	public boolean isReportNewForms() {
		return reportNewForms;
	}

	/**
	 * @param reportNewForms the reportNewForms to set
	 */
	public void setReportNewForms(boolean reportNewForms) {
		this.reportNewForms = reportNewForms;
	}

	/**
	 * @return the reportChangedForms
	 */
	public boolean isReportChangedForms() {
		return reportChangedForms;
	}

	/**
	 * @param reportChangedForms the reportChangedForms to set
	 */
	public void setReportChangedForms(boolean reportChangedForms) {
		this.reportChangedForms = reportChangedForms;
	}

	/**
	 * @return the contentTypes
	 */
	public Set<String> getContentTypes() {
		return contentTypes;
	}

	/**
	 * @param contentTypes the contentTypes to set
	 */
	public void setContentTypes(Set<String> contentTypes) {
		this.contentTypes = contentTypes;
	}

	/**
	 * @return the fileTypes
	 */
	public Set<String> getFileTypes() {
		return fileTypes;
	}

	/**
	 * @param fileTypes the fileTypes to set
	 */
	public void setFileTypes(Set<String> fileTypes) {
		this.fileTypes = fileTypes;
	}

	/**
	 * @return the srcContentTypes
	 */
	public Set<String> getSrcContentTypes() {
		return srcContentTypes;
	}

	/**
	 * @param srcContentTypes the srcContentTypes to set
	 */
	public void setSrcContentTypes(Set<String> srcContentTypes) {
		this.srcContentTypes = srcContentTypes;
	}

	/**
	 * @return the srcFileTypes
	 */
	public Set<String> getSrcFileTypes() {
		return srcFileTypes;
	}

	/**
	 * @param srcFileTypes the srcFileTypes to set
	 */
	public void setSrcFileTypes(Set<String> srcFileTypes) {
		this.srcFileTypes = srcFileTypes;
	}

	/**
	 * @return the reportLinkChanges
	 */
	public boolean isReportLinkChanges() {
		return reportLinkChanges;
	}

	/**
	 * @param reportLinkChanges the reportLinkChanges to set
	 */
	public void setReportLinkChanges(boolean reportLinkChanges) {
		this.reportLinkChanges = reportLinkChanges;
	}
}
