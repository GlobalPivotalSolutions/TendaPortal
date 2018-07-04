<?php

require_once( "localname.php" ) ;
require_once( local( "utils.php" ) ) ;


// Have to base everything on arrays because object serialisation works funny
// with sessions
class Context
{ 
 
 /**
  * Context factory. Creates experiments objects and sets the current experiment
  * 
  */
 static function newContext()
 {
   $c = Array() ;
   $c[ "experiments" ] = Context::newExperiments() ; 
   $_SESSION[ "context" ] = &$c ;
   
   $e = &$c[ "experiments" ][ 0 ] ;
   $m = "New experiment, Name = \"".$_REQUEST[ "firstName" ]." ".$_REQUEST[ "lastName" ] ;
   $m .= "\" email=".$_REQUEST[ "email" ] ;
   $m .= " starting experiment: \"".$e[ "titleText" ]."\"" ;
   Utils::L( log_INFO, $m ) ;
   return $c ;
 }
 
 // takes context from the session, ranking results from the request
 // and updates scores and queries 
 static function updateScores( $x, $iteration ) 
 {
   global $context ;
   $e = &$context[ "experiments" ][ $x ] ;
   if ( !isset( $e[ "index" ][ $iteration-1 ] ) ) return ;
   $index = $e[ "index" ][ $iteration-1 ] ;
   $query = $e[ "queries" ][ $iteration-1 ] ;
   if ( !isset( $context[ "queries" ] ) ) $context[ "queries" ] = array() ;
   if ( !isset( $context[ "queries" ][ $query ] ) )
                                $context[ "queries" ][ $query ] = array() ; 
   $scores = Array() ;
   $max = count( $e[ "engines" ] ) ;
   for ( $i = 0 ; $i < $max ; $i++ ) 
                            { $scores[ $i ] = 0 ; } 
   $max *= 10 ;
   for ( $j = 0 ; $j < $max ; $j++ ) // if this result is relevant
     {  
       if ( isset( $_REQUEST[ "cb".$j ] ) && $_REQUEST[ "cb".$j ] == "y" ) 
        {
          $scores[ $index[ $j ] ]++ ;
          $url = $context[ "lastURLs" ][ $j ] ;
          $url = Utils::normalise( $url ) ;
          $context[ "queries" ][ $query ][ $url ] = 'y' ; 
        }
     }
   $m = "\"".$e[ "titleText" ]."\" query: \"".$query. "\" > \"" ;
   $max /= 10 ;
   for ( $j = 0 ; $j < $max ; $j++ )
       {  $m .= " ".$scores[ $j ] ;
          if ( !isset( $e[ "scores" ] ) ) $e[ "scores" ] = Array() ;
          if ( !isset( $e[ "scores" ][ $j ] ) ) $e[ "scores" ][ $j ] = Array() ;
          $e[ "scores" ][ $j ][ $iteration-1 ] = $scores[ $j ] ;
       }
   $m .=  "\"" ;
   Utils::L( log_INFO, $m ) ;
 }


 static function mergeResults( &$e, $iteration, &$results ) 
 {
   global $context ;
   $index = array() ;
   $merged = array() ;
   $context[ "lastURLs" ] = array() ; // reset last URLs
   $query = $e[ "queries" ][ $iteration ] ;
   $sizes = Array() ;
   $counters = Array() ;
   for ( $i = 0 ; $i < count( $results ) ; $i++ ) 
       { $sizes[ $i ] = count( $results[ $i ] ) ; $counters[ $i ] = 0 ; } 
   $ii = 0 ;
   while( ( $next = Context::nextResult( $sizes, $counters ) ) != -1 )
   {
     $item = $results[ $next ][ $counters[ $next ] ] ;
     $counters[ $next ]++ ;
     $item[ "index" ] = $ii++ ;
     $url = Utils::normalise( $item[ "link" ] ) ;
     if ( isset( $context[ "queries" ][ $query ][ $url ] ) ) 
               $item[ "style" ] = "relevant" ; else $item[ "style" ] = "restitle" ;                                 
     $merged[] = $item ;
     $index[] = $next ;
     $context[ "lastURLs" ][] = $url ;
   }
   if ( !isset( $e[ "index" ] ) ) $e[ "index" ] = Array() ;
   $e[ "index" ][ $iteration ] = $index ;
   return $merged ;
 }
 
 // Random selection of the next result item to put in merged results
 static function nextResult( &$sizes, &$counters )
 {
  $max = count( $sizes ) - 1 ;
  $i0 = rand( 0, $max ) ;
  $i = $i0 ;
  do {
       if ( $counters[ $i ] < $sizes[ $i ] ) return $i ;
       $i++ ; if ( $i > $max ) $i = 0 ; 
     } while( $i != $i0 ) ;
  return -1 ;
 }

 // finds a query to ise next in all prev. experiments that were long enough
 static function getNextQuery( $x, $i )
 {
  global $context ;
  $ex = &$context[ "experiments" ] ;
  $foundQuery = null ;
  for ( $j = 0 ; $j < $x ; $j++ )
   {
      if ( isset( $ex[ $j ][ "queries" ][ $i + 1 ] ) )
       { $foundQuery = $ex[ $j ][ "queries" ][ $i + 1 ] ; break ; }
   }
  if ( !$foundQuery ) $foundQuery = "" ;
  return $foundQuery ;  
 }
 

 static function addQuery( &$e, $q, $i ) 
 { 
   if ( !isset( $e[ "queries" ] ) )
        $e[ "queries" ] = Array() ;
   $e[ "queries" ][ $i ] = $q ;
 }
               
 static function getAverage( &$e, $i, $num ) 
 { 
   $multiplier = 10 ;
   if ( isset( $e[ "units" ] ) && $e[ "units" ] == "%%" ) $multiplier = 1 ;
   $av = 0 ;
   $arr = &$e[ "scores" ][ $num ] ; 
   for ( $j = 0 ; $j < $i ; $j++ )
     { 
       $arr[ $j ] *= $multiplier ;
       $av += $arr[ $j ] ;
     }
   return $av / $i ;
 }

          
 /**
  * Creates experiment objects
  * 
  */
 static function newExperiments()
 {
   global $tests, $engines ;
   
   $experiments = Array() ;
   $x = 0 ;
   foreach( $tests as $access => $arr )
   {
    $experiment = Array() ;
    $experiment[ "access" ] = $access ;
    $experiment[ "engines" ] = Array() ;
    $i = 0 ;
    $title = "" ; 
    foreach( $arr as $engine ) // Check that engines are defined in the config file
    { 
     if ( $engines[ $engine ] == null )
         throw new Exception( "Engine ".$engine." is not defined in configuration." ) ;
     if ( !file_exists( local( $engines[ $engine ].".php" ) ) )
         throw new Exception( "Fetching module for ".$engine." does not exist." ) ;
     // Add a container for engine related data, such as scores
     $experiment[ "engines" ][ $engine ] = Array() ;
     $experiment[ "engines" ][ $engine ][ "number" ] = $i ; $i++ ;
     if ( $title != "" ) $title .= " vs " ;  $title .= $engine ; 
    }
    $experiment[ "titleText" ] = $title ;    
    $experiments[ $x ] = $experiment ; $x++ ;
   }
   return $experiments ;
 }
  
}
?>
