<html>
<head>

  <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">

  <title>Search Engines Evaluation</title>
  <meta name="DC.Title" content="Arch Search Evaluation System" lang="en">
  <meta name="DC.Subject" content="atnf;websites;sitemap;directory" lang="en">
  <meta name="DC.Description" content="Arch Search Evaluation System" lang="en">
    <link rel="stylesheet" href="css/standard.css" type="text/css">
  <link rel="stylesheet" type="text/css" href="css/eval.css" title="blue" />


  <script type="text/javascript">
  {literal}
  finction validate( ff )
  {
    return true ; // TODO change this  
  }
  {/literal}
  </script>

</head>
<body bodystr='bgcolor="#ffffff" text="#000000"' >

<div class="page" style="margin:0.3cm;">
  <form name="search1" id="search1" action="results.php" method="get" onsubmit="return validate( this );">
<fieldset>
  <legend>Search Engines Evaluation</legend>

<center>
<table width="700">

<tr><td colspan="2">

  <p>Hello!
  <p>
  You are invited to participate in search engines evaluation.
  <p>
  We would like you to do a test measuring relative performance of Arch 1.4 vs Funnelback on ATNF web sites. 
  <p>
   These test uses a &quot;blind&quot; test approach where the user submits a query that is sent in parallel
   (i.e. at the same time) to several search engines. The first 10 results from each search engine are
   returned with the full set results interleaved in a random way so that it is very difficult
   for the user to tell which search engine produced which result. However, the results from each
   search engine are shown in the original order. See <a href="conditions.html">experiment conditions</a>
   for details.
  <p>
  Please read instructions before doing the tests: 
  <UL>
    <LI>Firefox and Opera browsers work fine, <b>please do not use Internet Explorer</b>.
    <LI>To start a test, enter a query that is within your own range
     of expertise, so that you are able to assess relevance of the received results.
     Then click on the &quot;Search&quot; button. 
    <LI>Do not try to guess which results are received from which engine.
    <LI>Carefully consider each result item and mark it as relevant if and only if it contains information you were looking for.
    <b> Do not consider a document relevant just because it has your search phrases or because you can see why it was 
        returned in response to your query.</b>
        To mark an item as relevant, click anywhere on the title row. To deselect, click again. 
    <LI>To examine the document, click on the item link. It will open the web page for that link
     in a new tab (or window). Select the previous tab (or window) to return to the evaluation
     test.
    <LI>
       Make sure that you mark all relevant results in each results page.
       The title for the items you have selected will be shown in red. 
    <LI> Now repeat this process by entering another search string in the query box.
    <LI>
        Please aim to enter at least 5 different queries before clicking on &quot;Finish this test&quot;.
  </UL>
  <p>
  
    Thank you for your help. 

  { if $groups == "public" }

  { include file='publicmessage.tpl' }

  { /if }
  
  <div id="standardPanel"> 
    <table width="100%">
     <tr>
       <td align='right'><b>Your first name:</b></td>
       <td><input size=50 type='text' value='' name='firstName'>
       </td>
     </tr>
     <tr>
       <td align='right'><b>Your last name:</b></td>
       <td><input size=50 type='text' value='' name='lastName'>
       </td>
     </tr>
     <tr>
       <td align='right'><b>Your email:</b></td>
       <td><input size=50 type='text' value='' name='email'>
       </td>
     </tr>
  </table>
  </div>


</td></tr>

</table>
</center>

<fieldset>
  <legend>Test: Arch 1.4 vs Funnelback on all (public and internal) pages</legend>
  <div style="margin: 0.5cm;"><b>Query:</b>&nbsp;&nbsp;<input size=40 type='text' value='' name='query'>
         &nbsp;&nbsp;<input type="submit" name="aa" value="Search"></div>
  <input type="hidden" name="x" value="0">       
  <input type="hidden" name="i" value="0">       
  {$sid}       
</fieldset>

   </fieldset>
 </form>

</div>

</body>

</html>
