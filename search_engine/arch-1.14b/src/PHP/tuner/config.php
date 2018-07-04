<?php
  // A template for configuration parameters. This file should not be edited to
  // avoid loss of data when upgrading. Instead, create local/config.php
  // and edit it. If exists, it will be used instead of this one.

  // Trace levels. Leave as they are.
  define( 'log_NONE', 0 ) ; 
  define( 'log_ERROR', 1 ) ; 
  define( 'log_WARN', 2 ) ; 
  define( 'log_INFO', 3 ) ; 
  define( 'log_TRACE', 4 ) ; 
  define( 'log_DEBUG', 5 ) ; 
  define( 'log_DUMP', 6 ) ; 
  
  // Log level and destination file
  $logLevel = log_INFO ;
  $logFile = "log.txt" ;

  // Results (totals) file
  $resultsFile = "results.txt" ;
  
  // Relevance judgements file
  $relevanceFile = "relevance.txt" ;
  
  // If having problems with sessions, set this to a path where to store
  // serialised session data for a quick and dirty patch.
  // Just don't forget to clean it periodically if have many users
  // If sessions work OK, set this to null. It is not needed.
  $sessionStore = "sessionData" ;
  
  // Have to define a time zone to shut up complaining PHP 5
  $tz = "Australia/Sydney" ;
  date_default_timezone_set( $tz ) ;

  // Default language
  $language = "en" ;
  
  // User and groups names which are used to search Arch. This module accesses Arch via a frontend intterface.
  // Normally, fronends are supposed to use an authentication module to establish user name and groups the
  // user belongs to. These names are used to filter user query. For evaluation and testing purposes,
  // especially if Arch is being compared to Google and only the publick part of the index is being used,
  // groups names can be set constant.
  $groups = "public" ;
      
  // Define search engines and their search modules. These pairs are defined as array
  // elements in form of key => value where key is a descriptive name and value is name
  // of the engine-specific php module to include and function to call.
  // Example: Google => search_google defines a search engine named Google and tells to include
  // search_google.php and call function search_google() with certain parameters to get results
  // for this engine.
  // The number of engines is not limited, but a search module must be provided for each of
  // them. Search modules for Arch, Nutch (1.2 or earlier), Google and Funnelback are included
  // in the package.
  // NOTE: YOU HAVE TO CONFIGURE SUPPLIED SEARCH MODULES by editing their source code.
  $engines = array( 
                  "Arch" => "search_arch",
                  "Nutch" => "search_nutch11",
                  "Funnelback" => "search_funnelback",
                  "Google" => "search_google" ) ;
                    
  // Define the tests as sets of search engines. In each test the engines 
  // are evaluated against each other by running multiple queries and then doing blind 
  // results comparison.
  // Each test's key element is "public" - for searching public pages only,
  // or "all" - for searching all pages.
  // For example, Google global search can only be used to search public pages as it
  // does not index intranet pages. Nutch and Arch can index and search all pages.
  // Arch can be used to search either only public or all pages, depending on search
  // parameters. This can be achieved with Nutch by creating two indexes.
  // The value array contains names of search engines that must be defined in the
  // engines array above. These search engines will be queried and compared in this test.
  $tests = array( "public" => array( "Google", "Arch" ),
                  "all" => array( "Nutch", "Arch" )
                ) ;
                       
  // Number of top documents show in tuning pages                      
  $topDocuments = 50 ;
  
  // Synonims for index/home pages
  $home = "index.html index.htm home.html home.htm index.php home.php" ;                                                
?>
