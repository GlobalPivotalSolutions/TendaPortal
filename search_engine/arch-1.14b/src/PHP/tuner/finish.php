<?php

 // Entry page
 // Creates a new context and displays a form for email, name and first query

 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( "utils.php" ) ) ;
 require_once( local( "context.php" )  ) ;
 Utils::resumeSession() ;
 
 $context =  &$_SESSION[ "context" ] ;
 if ( $context == null )
    { // TODO: do it nicer 
      echo "Your session must have expired.<br>".
           "Please restart the experiment." ;  
      die ;
    }
 $x = $_REQUEST[ "x" ] ;
 $i = $_REQUEST[ "i" ] ; 
 Context::updateScores( $x, $i ) ;                              
 $e = &$context[ "experiments" ][ $x ] ;
 $scores = Array() ; $names = Array() ;
 $tot = "Data set: ".$e[ "access" ] ;
 foreach( $e[ "engines" ] as $name => $engine )
 {
   $number = $engine[ "number" ] ;
   $names[ $number ] = $name ;
   $aver[ $number ] = round( Context::getAverage( $e, $i, $number ), 1 ) ;  
   $m = $name. " on " .$i. " precision: ".$aver[ $number ] ."%" ;
   $tot .= " $name: ". $aver[ $number ] ."%" ;
   Utils::L( log_INFO, $m ) ;
 }
 $e[ "units" ] = "%%" ; // mark that have moved scores to percents.
 Utils::totalsOut( $tot ) ;
     
 $smarty = Utils::initSmarty() ;
 $smarty->assign( 'names', $names ) ;
 $smarty->assign( 'aver', $aver ) ;
 $smarty->assign( 'scores', $e[ "scores" ] ) ;
 $smarty->assign( 'qNum', $i ) ;
 $smarty->assign( "queries", $e[ "queries" ] ) ;

 // next experiment?
 $_SESSION[ "context" ] = &$context ;
 $numTests = count( $context[ "experiments" ] ) ;
 if ( $x < $numTests - 1 )
    {
      $x++ ;
      $e1 = $context[ "experiments" ][ $x ] ;
      $smarty->assign( 'titleText', $e1[ "titleText" ] ) ;
      $smarty->assign( "x", $x ) ;
      $nextQuery = Context::getNextQuery( $x, -1 ) ;
      $smarty->assign( 'nextQuery', $nextQuery ) ;
    } else // this is time to save relevance judgements
    {
      Utils::relevanceOut( $context[ "queries" ] ) ;
    }
 $smarty->assign( 'sid', Utils::storeSession() ) ;
 $smarty->display( local_template( "finish.tpl" ) ) ;
?>
