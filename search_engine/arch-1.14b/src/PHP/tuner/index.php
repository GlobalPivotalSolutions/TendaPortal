<?php

 // Entry page
 // Creates a new context and displays a form for email, name and first query

 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( "utils.php" ) ) ;
 require_once( local( "context.php" )  ) ;
 Utils::restartSession() ;
 srand( time() ) ;
 
 $smarty = Utils::initSmarty() ;
 $smarty->assign( 'groups', $groups ) ;
 $smarty->assign( 'engines', "" ) ;
 $sidtag = Utils::storeSession() ;
 $smarty->assign( 'sid', $sidtag ) ;
 $smarty->display( local_template( "index.tpl" ) ) ;
?>
