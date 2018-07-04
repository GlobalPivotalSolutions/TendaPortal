/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A reference blocker implementation
 * 
 */
package au.csiro.cass.arch.security;

import org.apache.hadoop.conf.Configuration;


public class BasicBlocker extends Blocker
{
  Configuration        conf ;
  
  /**
   * This class is provided as an example of using a pruner to block unwanted pages.
   * As it is, it is not relevant to your organisation, unless your organisation
   * is CSIRO Australia.
   * 
   * This class uses the Pruner interface to block pages served by CSIRO public web
   * server in response to requesting non-existing URLs. The server just sends back
   * the home page. This creates a robot trap when relative URLs are extracted from
   * this page and combined with the one that was used to receive it to get yet more
   * non-existent URLs to fetch. To prevent this, we will identify the home page by its
   * content and block it unless it was served in response to its proper URL request.   
   */
  @Override
  public String preParsePrune( ScanContext context, String src ) throws Exception
  {
	if ( ( src.contains( "<meta name=\"DC.Identifier\" content=\"http://www.csiro.au/en.aspx\" />" ) ||
		   src.contains( "<meta name=\"DC.Identifier\" content=\"http://www.csiro.au/\" />" ) ) && 
		   !context.url.equals( "http://www.csiro.au/" ) ) return "" ;
	if ( ( src.contains( "<meta name=\"DC.Identifier\" content=\"http://my.csiro.au/en.aspx\" />" ) ||
		   src.contains( "<meta name=\"DC.Identifier\" content=\"http://my.csiro.au/\" />" ) ) && 
		   !context.url.equals( "http://my.csiro.au/" ) ) return "" ;
    return src ;
  }
    
  @Override
  public void setConf( Configuration conf )
  {
    this.conf = conf ;
  }

  @Override
  public Configuration getConf()
  { return conf ; }

  /* (non-Javadoc)
   * @see au.csiro.cass.arch.security.Pruner#postParsePrune(au.csiro.cass.arch.security.ScanContext, java.lang.String)
   */
  @Override
  public String postParsePrune( ScanContext context, String content ) throws Exception
  {
    return content;
  }

}
