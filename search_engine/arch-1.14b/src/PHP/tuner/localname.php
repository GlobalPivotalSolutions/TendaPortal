<?php
  /* if (session_id() == "") */ session_start() ; 

  function local( $filename ) 
  {
    $localName = getLocalName( $filename ) ;
    if ( file_exists( $localName ) ) return $localName ;
       else return $filename ;
  }
    
  function local_template( $file )
  {
    global $language ;  
    $localfile = "./language/$language/local/$file" ;
    if ( file_exists( $localfile ) ) return "local/$file" ; 
      return $file ;
  }

  function getLocalName( $filename ) 
  {
    $local = "" ;
    $i = strrpos( $filename, '/' ) ;
    if ( $i === false ) $i = strrpos( $filename, '\\' ) ;
    if ( $i === false ) $local = "local/$filename" ;
     else $local = substr( $filename, 0, $i )."/local".substr( $filename, $i ) ;
    return $local ;
  }
?>
