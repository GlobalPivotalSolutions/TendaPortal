<?php

 $language = "en" ;

 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( 'out.php' ) ) ; 

 $page = showDirectory() ;
 echo $page ;
?>
