<html>
<head>

  <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">

  <title>Experiment Results</title>
  <meta name="DC.Title" content="Experiment Results" lang="en">
  <meta name="DC.Subject" content="atnf;websites;sitemap;directory" lang="en">
  <meta name="DC.Description" content="Results of Arch evaluation experiment." lang="en">
    <link rel="stylesheet" href="css/standard.css" type="text/css">
  <link rel="stylesheet" type="text/css" href="css/eval.css" title="blue" />


</head>
<body BGCOLOR=#FFFFFF LEFTMARGIN=0 TOPMARGIN=0 MARGINWIDTH=0 MARGINHEIGHT=0">

<div class="page">

<fieldset>
 <legend>Test results</legend>

{if $qNum eq 0}

Sorry, you did not submit any valid queries.

{else}

<table width="100%" cellpadding="0" cellspacing="0">
<tr class="pages">
   <td><b>Query</b></td>
      {section name=i1 loop=$names }
         <td><b>{$names[i1]} scores</b></td>
      {/section}
   </tr>
   {section name=rr loop=$queries}      
      <tr class="resdesc">
          <td align="center">{$queries[rr]}</td>
            {section name=i2 loop=$names}      
               <td align="center">{ $scores[i2][rr] }%</td>
            {/section}
      </tr>
   {/section}
<tr class="pages">
          <td><b>Average precision over {$qNum} queries</b></td>
          {section name=i3 loop=$names }
            <td><b>{$aver[i3]}%</b></td>
          {/section}
</tr>
</table>
{/if}
</fieldset>
{ if $nextQuery != null }
<br><br>
<fieldset>
  <legend>Test {math equation="x + y" x=$x y=1}: {$titleText} on all pages</legend>
  <form name="search1" id="search1" action="results.php" method="get" onsubmit="return validate( this );">

  <span style="margin-left: 0.5cm;"><b>First query:</b>&nbsp;&nbsp;<input size=50 type='text' value='{$nextQuery}' name='query'>
         &nbsp;&nbsp;<input type="submit" name="aa" value="Search"></span>
           <input type="hidden" name="x" value="{$x}">       
           <input type="hidden" name="i" value="0">
           {$sid}
 </form>
</fieldset>
{/if}

</div>

 </body>

</html>