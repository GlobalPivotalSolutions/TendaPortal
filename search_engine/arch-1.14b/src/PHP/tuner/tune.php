<?php
  // read log exatrcts, parse records, get queries and relevant numbers
  // submit the queries, extract relevant urls and write them to a file
  require_once( "localname.php" ) ;
  require_once( local( "config.php" ) ) ;
  require_once( local( "utils.php" ) ) ;
  set_time_limit(0) ;
    
//  $_REQUEST[ "query" ] = "astrophysical masers" ;
  
  $smarty = Utils::initSmarty() ;
  $relevant = readRelevant() ;
  $public = true ; // limit search to public pages only 
  if ( isset( $_REQUEST[ "query" ] ) )
     { $qstring = "Query: ".$_REQUEST[ "query" ]."," ;
       $query =  $_REQUEST[ "query" ] ;
       Utils::resumeSession() ;
       $i = $_REQUEST[ "i" ] ;
     }
    else // this is the first page, start session
     { $query = "" ; $qstring = "" ;
       Utils::restartSession() ;
       $i = -1 ;
       if ( isset( $_REQUEST[ "go" ] ) )
       {  
         $nextQuery = findNextQuery( $relevant, $query ) ;
         $query = $nextQuery ;
         do 
         { 
           $i++ ;
           $nextQuery = findNextQuery( $relevant, $query ) ;
           if ( $nextQuery == "" ) break ;
           showResults( $relevant, $query, $smarty, $topDocuments, $i, $public, false ) ;  
           $query = $nextQuery ;
         } while( true ) ;  
       }
     }
  $public = ( isset( $_REQUEST[ 'publicSearch' ] ) && $_REQUEST[ 'publicSearch' ] == 'on' ) ;
  $smarty->assign( "qstring", $qstring ) ;
  $smarty->assign( "top", $topDocuments ) ;
  $smarty->display( local_template( "tuneHeader.tpl" ) ) ;
  
  if ( $i >= 0 ) showResults( $relevant, $query, $smarty, $topDocuments, $i, $public, true ) ;
  $nextQuery = findNextQuery( $relevant, $query ) ;
  $smarty->assign( "nextQuery", $nextQuery ) ;
  $smarty->assign( "i", $i+1 ) ;
  $smarty->assign( 'sid', Utils::storeSession() ) ;
  $smarty->assign( "publicOnly", $public ) ;
  $smarty->assign( "public", $public ) ;
  $smarty->display( local_template( "tuneQuery.tpl" ) ) ;

  function showResults( $relevant, $query, $smarty, $top, $i, $public, $show )
  {
    global $engines ;
    foreach( $engines as $engine=>$module )
    {
      require_once( local( $module.".php" ) ) ;
      $results = $module( $public, $query, $top ) ; // for now, only public pages
//      $results = Utils::padResults( $results, $top ) ;
      $total = markRelevant( $engine, $relevant, $query, $results, $top, $i,
                                             $rel10, $relTop, $av10, $avTop ) ;
      if ( $show )
      {
        $smarty->assign( 'query', $query ) ;
        $smarty->assign( 'total', $total ) ;
        $smarty->assign( 'engine', $engine ) ;
        $smarty->assign( 'rel10', $rel10 ) ;
        $smarty->assign( 'relTop', $relTop ) ;
        $smarty->assign( 'av10', $av10 ) ;
        $smarty->assign( 'avTop', $avTop ) ;
        $smarty->assign( 'top', $top ) ;
        $smarty->assign( 'r', $results ) ;
        $smarty->display( "tuneResults.tpl" ) ;
      }
    }
  }    
  
  function readRelevant()
  {
    global $relevanceFile ;   
      
    $lines = file( $relevanceFile ) ;
    $q = array() ;
    $query = "" ;
    foreach( $lines as $l )
    {
      if ( substr( $l, 0, 6 ) == "######" ) 
         { $query = trim( substr( $l, 7 ) ) ; $q[ $query ] = array() ; }
        else if ( substr( $l, 0, 3 ) == " + " )
                { 
                  $url = Utils::normalise( trim( substr( $l, 3 ) ) ) ;
                  $q[ $query ][ $url ] = "r" ;
                }
    }
    return $q ;  
  }
  
  function markRelevant( $engine, &$relevant, $query, &$results, $top, $i,
                                        &$rel10, &$relTop, &$av10, &$avTop )
  {
    $rel10 = 0 ; $relTop = 0 ; $a = 1 ; $num = 0 ;
    if ( isset( $relevant[ $query ] ) ) $qq = $relevant[ $query ] ;  
    foreach( $results as &$item )
          {
            $num++ ;
            $url = Utils::normalise( trim( $item[ "link" ] ) ) ;
            if ( isset( $qq[ $url ] ) )
                 { $item[ "style" ] = "relevant" ; $relTop++ ; 
                   if ( $num <= 10 ) $rel10++ ;
                 }
              else 
                 { $item[ "style" ] = "restitle" ; }
            $item[ "index" ] = $a++ ;
          }
    $engine10 = $engine."10" ;
    $engineTop = $engine."Top" ;
    $engine10Arr = isset( $_SESSION[ $engine10 ] ) ? $_SESSION[ $engine10 ] : array() ;
    $engineTopArr = isset( $_SESSION[ $engineTop ] ) ? $_SESSION[ $engineTop ] : array() ;
    $engine10Arr[ $i ] = $rel10 ;  
    $engineTopArr[ $i ] = $relTop ;
    $_SESSION[ $engine10 ] = $engine10Arr ;
    $_SESSION[ $engineTop ] = $engineTopArr ;
    $av10 = 0 ; $avTop = 0 ; 
    for ( $j = 0 ; $j <= $i ; $j++ )
      {
        $av10 += $engine10Arr[ $j ] ; $avTop += $engineTopArr[ $j ] ; 
      }
    $av10 = round( (float)$av10 / ( $i + 1 ), 2 ) ;
    $avTop = round( (float)$avTop / ( $i + 1 ), 2 ) ;
  }
  
  function findNextQuery( $q, $query )
  {
    $found = 0 ;
    foreach( $q as $qq=>$urls )
    {
      if ( $query == "" ) return $qq ;
      if ( $found != 0 ) return $qq ;
      if ( $qq == $query ) $found = 1 ;
    }  
    return "" ;
  }
  
?>
