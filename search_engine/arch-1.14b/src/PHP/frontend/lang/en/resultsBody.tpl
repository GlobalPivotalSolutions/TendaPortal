{ if $total eq 0 }

Sorry, your query did not match any results.

{elseif $total eq -1 }

Sorry, your search caused an error, please report this to the administrator.

{else}
<table style="border-spacing:0;border-collapse:collapse;width:100%;"> 
<tr class="pages"><td>Results {$start} - {$end} of {$total}   &nbsp;&nbsp; Pages: {$pages}</td></tr>

{section name=resultitem loop=$items}
<tr class="restitle"><td><a class="restitle" href="{$items[resultitem].link}">{$items[resultitem].title}</a></td>
    
</tr> 
<tr class="resdesc"><td>{$items[resultitem].description}</td></tr>

{assign var=ind value=$ind+1}

<tr class="reslink"><td><a class="reslink" tabindex="{$ind}" href="{$items[resultitem].link}">{$items[resultitem].link}</a></td></tr>
{/section}

<tr class="pages"><td>Results {$start} - {$end} of {$total}   &nbsp;&nbsp; Pages: {$pages}</td></tr>
<tr><td>
<span style="padding-left:0.3cm;">Powered by <a href="http://www.atnf.csiro.au/computing/software/arch/">CSIRO Arch</a></span>
</td></tr> 

</table>
{/if}
