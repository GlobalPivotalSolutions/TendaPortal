<html>
<head>

  <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">

  <title>{$title}</title>
  <meta name="DC.Title" content="Query Results" lang="en">
  <meta name="DC.Subject" content="atnf;websites;sitemap;directory" lang="en">
  <meta name="DC.Description" content="Query results in Arch evaluation experiment." lang="en">
    <link rel="stylesheet" href="css/standard.css" type="text/css">
  <link rel="stylesheet" type="text/css" href="css/eval.css" title="blue" />


  <script type="text/javascript" src="scripts/eval.js"></script> 
  <script type="text/javascript"> 
  var home = "{$homePages}" ;
  var homePages = null ;
  </script> 
</head>

<body BGCOLOR=#FFFFFF LEFTMARGIN=0 TOPMARGIN=0 MARGINWIDTH=0 MARGINHEIGHT=0">  

<div class="page">

<fieldset>
 <legend>{$title}</legend>
  <form name="qform" id="qform" action="results.php" onsubmit="return validate( this );">

   <table>
    <tr>
     <td align='right' width="90"><b>Your query:</b></td>
     <td align='left' width="370"><B>{$query}</b></td> 
    </tr>
   </table>
<br>
{ if $total eq -1 }

Sorry, your query did not match any results. Please correct it.
 <p style="margin-left: 0.5cm;">
    <b>Query:</b>&nbsp;&nbsp;<input size=40 type='text' value='{$nextQuery}' name='query'>&nbsp;
       <input type="submit" name="aa" value="Search"/>&nbsp;
       <input type="hidden" name="lang" value="{$language}">
       <input type="hidden" name="x" value="{$x}">       
       <input type="hidden" name="i" value="{$i}">       
       <input type="hidden" name="ignore" value="y">       

{else}

 <p style="margin-left: 0.5cm;">To mark an item as relevant, click anywhere on its title row.

 <table width="100%" cellpadding="0" cellspacing="0"> 
     {section name=i1 loop=$r}
       <tr class="restitle">
         <td class="{$r[i1].style}" id="td{$r[i1].index}" onclick="event.cancelBubble=true;return relevant(this);">
         {math equation="x + y" x=$r[i1].index y=1}.
           { if $r[i1].style eq "relevant" }
            <input type="hidden" name="cb{$r[i1].index}" id="cb{$r[i1].index}" value="y">
           {else} 
            <input type="hidden" name="cb{$r[i1].index}" id="cb{$r[i1].index}" value="">
           {/if}
             <a href="{$r[i1].link}" id="a{$r[i1].index}" onclick="event.cancelBubble=true;return relevant(this);">{$r[i1].title}</a>
         </td>
       </tr> 
       <tr class="resdesc">
         <td>
             {$r[i1].description}
<!--             
         <div><b>User approval rate:</b>&nbsp;&nbsp;&nbsp;&nbsp;
         <input type="radio" name="rate{$r[i1].index}" value="-1">-1&nbsp;&nbsp;&nbsp;&nbsp;
         <input type="radio" name="rate{$r[i1].index}" value="0" checked>0&nbsp;&nbsp;&nbsp;&nbsp;
         <input type="radio" id="rate{$r[i1].index}"  name="rate{$r[i1].index}" value="+1">+1&nbsp;&nbsp;&nbsp;&nbsp;
         </div> 
-->
         </td>
       </tr>
       <tr class="reslink">
         <td>
{ if $r[i1].debug eq 1 }
            <a class="reslink" href="{$r[i1].link}#" target="_blank">{$r[i1].link}</a>
{else}            
            <a class="reslink" href="{$r[i1].link}" target="_blank">{$r[i1].link}</a>
{/if}
         </td>
       </tr>
     {/section}
   </table>
<fieldset>
  <legend>Next query</b></legend>

 <p style="margin-left: 0.5cm;">
 To continue with this test, please enter a different query and click on &quot;Search&quot; <b><u>after</u></b> marking
 all the relevant search results shown on this page. 
  
 <p style="margin-left: 0.5cm;">
 To finish the test, click on &quot;Finish this test&quot; test <b><u>after</u></b> marking all the relevant search results
 shown on this page
 <p style="margin-left: 0.5cm;">
    <b>Query:</b>&nbsp;&nbsp;<input size=40 type='text' value='{$nextQuery}' name='query'>&nbsp;
       <input type="submit" name="aa" value="Search"/>&nbsp;
       <button name="finish" onClick="form.action='finish.php';form.submit;">Finish this test</button>&nbsp;
       <button name="finish" onClick="form.action='index.php';form.submit;">Reset</button>
       <input type="hidden" name="lang" value="{$language}">
       <input type="hidden" name="x" value="{$x}">       
       <input type="hidden" name="i" value="{$i}">   
       {$sid}       

</fieldset>
{/if}
</form>

</fieldset>
</div>

 </body>

</html>