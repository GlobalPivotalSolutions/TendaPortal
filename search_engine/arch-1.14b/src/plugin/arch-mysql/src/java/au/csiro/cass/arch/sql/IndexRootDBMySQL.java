/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.logProcessing.LogFile;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Implements a set of db functions for access to tables shared by all sites and areas
 * 
 * @author Arkadi Kosmynin
 *
 */
public class IndexRootDBMySQL extends IndexRootDBBaseImpl
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexRootDBMySQL.class ) ;

 /**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexRootDBMySQL(  ConfigList cfg )
 { super( cfg ) ; }
 
 /** 
 * IndexRootDBImpl factory
 * 
 * @param cfg   configuration parameters
 * @return  connected IndexRootDBImpl object
 */
 public static IndexRootDBMySQL newIndexRootDBMySQL( ConfigList cfg, boolean servletMode ) throws Exception
 {
  IndexRootDBMySQL dd = new IndexRootDBMySQL( cfg ) ;
  dd.servletMode = servletMode ;
  dd.connect() ;
//  dd.init() ; this is called from connect() now, to do re-init when reconnecting.
  return dd ;
 }
 
}


