/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.filters;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.parse.ParseStatus;
import org.apache.nutch.parse.Parser;
import org.apache.nutch.protocol.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentTypeBlocker implements Parser
{

  public static final Logger LOG = LoggerFactory.getLogger( "au.csiro.cass.arch.filters.ContentTypeBlocker" ) ;

  private Configuration conf;  

  public ContentTypeBlocker () { }

  public ParseResult getParse( Content content )
  {

    String contentType = content.getContentType();
    return new ParseStatus( ParseStatus.FAILED,
                      "This content type is blocked: " + contentType ).getEmptyParseResult(content.getUrl(), getConf() ) ;
  }
  
  public void setConf(Configuration conf)
  {
    this.conf = conf;
  }

  public Configuration getConf() {
    return this.conf;
  }
}
