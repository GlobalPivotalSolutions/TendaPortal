/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.io.BufferedWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.auth.Permissions;
import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.index.IndexSite;
import au.csiro.cass.arch.index.LogLinks;
import au.csiro.cass.arch.logProcessing.ScoreFile;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexNode;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.URLSplit;
import au.csiro.cass.arch.utils.Utils;

/**
 * A set of db related functions needed by IndexSite and LogSite objects
 * 
 */
public class IndexSiteDBMySQL extends IndexSiteDBBaseImpl
{
  public static final Logger LOG = LoggerFactory.getLogger( IndexSiteDBMySQL.class );

  /**
   * Constructor
   * 
   * @param cfg
   *          configuration parameters
   * 
   */
  IndexSiteDBMySQL( ConfigList cfg )
  {
    super( cfg );
  };

  /**
   * Factory, returns initialised and connected object.
   * 
   * @param cfg
   *          configuration parameters
   * @param site
   *          site name
   * 
   */
  public static IndexSiteDBMySQL newIndexSiteDBMySQL( ConfigList cfg, String site, boolean servletMode )
      throws Exception
  {
    IndexSiteDBMySQL dd = new IndexSiteDBMySQL( cfg );
    dd.site = site;
    dd.table = "site_" + site;
    dd.servletMode = servletMode;
    dd.connect();
    // dd.init() ; this is being called from connect()
    dd.url = cfg.get( "url", "", "SITE_URL" );
    if ( dd.url.length() == 0 )
      LOG.warn( "URL is missing for site " + site + ", ignore if the site is being dropped." );
    return dd;
  }

}
