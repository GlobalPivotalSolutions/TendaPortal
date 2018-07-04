<?php

 // Entry page
 // Creates a new context and displays a form for email, name and first query

 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( "utils.php" ) ) ;
 require_once( local( "context.php" )  ) ;
 Utils::resumeSession() ;

 if ( !isset( $_SESSION[ "context" ] ) ) $context = Context::newContext() ; // init everything 
                                    else $context = &$_SESSION[ "context" ] ;
 $x = $_REQUEST[ "x" ] ; // Test number and query number. These have to be in the url, else
 $i = $_REQUEST[ "i" ] ; // the system is sensitive to page reloads and going back.
 $e = &$context[ "experiments" ][ $x ] ; 
 // coming from a results page
 if (  $i > 0 && !isset( $_REQUEST[ "ignore" ] ) ) Context::updateScores( $x, $i ) ;
 if ( $i == 0 ) $e[ "queries" ] = Array() ;
 $query = $_REQUEST[ "query" ] ;
 $e[ "queries" ][ $i ] = $query ;
 $results = Array() ;
 foreach( $e[ "engines" ] as $engine => $arr )
  {
    $module = $engines[ $engine ] ;
    require_once( local( $module.".php" ) ) ;
    $results[ $e[ "engines" ][ $engine ][ "number" ] ] = $module( $e[ "access" ] == "public", $query, 10 ) ;
  }
 $total = Utils::checkResults( $results, $message ) ;
 if ( $total != -1 ) // if there are any results
      $results = Context::mergeResults( $e, $i, $results ) ;
      else throw new Exception( "One or more engines did not return results" ) ;
          
 $_SESSION[ "context" ] = &$context ;
 $smarty = Utils::initSmarty() ;
 $smarty->assign( 'c', $context ) ;
 $smarty->assign( 'total', $total ) ;
 $smarty->assign( 'r', $results ) ;
 $smarty->assign( 'query', $query ) ;
 $smarty->assign( 'title', $e[ "titleText" ] ) ;
 $smarty->assign( 'x', $x ) ;
 $smarty->assign( 'homePages', $home ) ;
 if ( $total != -1 )
    {
      $smarty->assign( 'i', $i + 1 ) ;
      $nextQuery = Context::getNextQuery( $x, $i ) ; // find suggested next query
    } else
    { $smarty->assign( 'i', $i ) ; $nextQuery = $query ; }
 $smarty->assign( 'nextQuery', $nextQuery ) ;
 $smarty->assign( 'sid', Utils::storeSession() ) ;
 $smarty->display( local_template( "results.tpl" ) ) ;
?>
