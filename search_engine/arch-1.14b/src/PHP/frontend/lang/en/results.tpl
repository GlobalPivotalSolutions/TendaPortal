<!DOCTYPE html>
<html lang="en">
<head>

  <meta http-equiv="Content-Type" content="text/html;charset=utf-8">

  <title>Arch Search Results</title>
  <script type="text/javascript" src="scripts/ajax.js"></script>
  <script type="text/javascript" src="scripts/arch.js"></script>
{literal}
  <script type="text/javascript">
  var ajaxAddress = "ajax.php" ;
  var query_prompt = "Please enter a name for saved query." ;
  var archAddress = "" ;
  function keyPressed( event, form ) 
  {
    if ((event.which&&event.which==13)||(event.keyCode&&event.keyCode==13))
                               form.submit(); else return true;
  }
  </script>
{/literal}
  <link rel="stylesheet" type="text/css" href="css/arch-{$theme}.css"/>
  <link rel="alternate stylesheet" type="text/css" href="css/arch-blue.css" title="blue" />
  <link rel="alternate stylesheet" type="text/css" href="css/arch-green.css" title="green" />
  <link rel="alternate stylesheet" type="text/css" href="css/arch-purple.css" title="purple" />
  <link rel="shortcut icon" type="image/x-icon" href="images/favicon.ico"/>

</head>
<BODY onload="initPage();">
<div class="page">
<div class="content">

<fieldset>
    <legend class="results">Search results</legend>
<div style="padding-top: 0.1cm;padding-left:0.5cm;">
 <table><tr valign="top"><td width="430">
   <form name="qform" id="qform" action="arch.php">
     <b><label for="q">Query:</lablel></b>
              &nbsp;<input size="45" type='text' value='{$query}' 
          name='q' id="q" onkeydown="return keyPressed(event, document.qform);" >
  
       <input type="submit" name="aa" value="Search">
       <input type="hidden" name="lang" value="{$language}">
   </form>
  </td>
  <td>
  <button name="saveQuery" onclick="saveQuery();return false;">Save query</button>
  <button name="newQuery" onclick="navigate( 'arch.php' );return false;">New query</button>
  </td></tr>
 </table>
</div>

<div id="facetsAndResults">

{assign var=ind value=1}

 <div id="results">
     { include file="resultsBody.tpl" }
 </div> <!-- results-->

{assign var=aaa value=$items|@count} 

{ if $allfacets } {* If there are any facets at all *}
   <div id="facets">
    <p><strong>Refine your results:</strong></p>

{* Sites, areas illustrate automatically building facets based on ordered collections *}
{ assign var='sites' value=$facets.ar_site }
{ if ( $sites ) }
  <div id="sites">
   <h3>Sites</h3>{assign var=aaa value=$aaa+1}
{ foreach from = $sites key = k item = v  }
   <div class="facetDiv">{assign var=aaa value=$aaa+1}
          <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
          <label for="box{$aaa}" class="facet">{$k} ({$v})</label>
          <input type="hidden" value="ar_site:{$k}">
   </div>
{/foreach}
  </div> <!-- sites -->
{/if} <!-- sites -->

{ assign var='areas' value=$facets.ar_area }
{ if ( $areas ) }
     <div id="areas">
  <h3>Areas</h3>
{ foreach from = $areas key = k item = v  }
   <div class="facetDiv">{assign var=aaa value=$aaa+1}
          <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
          <label for="box{$aaa}" class="facet">{$k} ({$v})</label>
          <input type="hidden" value="ar_area:{$k}">
   </div>
{/foreach}
  </div> <!-- areas -->
{/if} <!-- areas -->

{*
## Here is an example of how to do the same for queries of arbitrary type using the global map.
## This includes range queries. Using this method, you can provide custom labels.
## The disadvantage is that you have to code each entry instead of iterating as above.
*}

 { if ( $typeOn ) }
     <div id="format">
      <h3>Format</h3>

      {assign var=tmp value="type:HTML"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
          <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
          <label for="box{$aaa}" class="facet">  HTML ({$allfacets.$tmp})</label>
          <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:PDF"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  PDF ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Text"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Plain text ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Document"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Document (Office, ps, etc.) ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Presentation"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Presentation ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Spreadsheet"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Spreadsheet ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:XML"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  XML ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Code"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Code ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Audio"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Audio ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Video"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Video ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}

      {assign var=tmp value="type:Other"}  { if $allfacets.$tmp }
        <div class="facetDiv">{assign var=aaa value=$aaa+1}
         <input id="box{$aaa}" type="checkbox" class="facet" tabindex="{$aaa}">
         <label for="box{$aaa}" class="facet">  Other ({$allfacets.$tmp})</label>
         <input type="hidden" value="{$tmp}">
        </div>
      {/if}
     </div>
   {/if} <!-- typeOn -->

{/if} <!-- allfacets -->

</div> <!-- facets -->

</fieldset>
</div>
</div>

</body>
</html>