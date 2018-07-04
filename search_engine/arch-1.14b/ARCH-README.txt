CSIRO Arch README

For a high level overview of what is Arch and what it can do for you, please read ArchWhitePaper.pdf.

For instructions on Arch deployment, read DeployingArch.pdf.

For quick start, read QuickStartGuide.pdf.

It is also strongly recommended to read NUTCH-README.txt

New in Arch 1.14b

1. Ported to Nutch 1.14 and Solr 7.2

New in Arch 1.9.2

1. PHP used to put junky looking content in the query field on results pages when an advanced
query was submitted. Now it leaves this field empty in case of an advanced query.
2. Made name field shorter (1K instead of 2K) in site DB tables. A too loong field length resulted 
in an index key that was too long for some MySQL configurations.
3. Moved to new version numbering scheme to align it with the Apache Nutch version numbering 
scheme. 

New in Arch 1.91

1. Fixed a bug that caused enforcing access permissions problems. 

New in Arch 1.9

1. Fixed bugs found in 1.9b.
2. Added post-parsing pruning.
3. Changed order of application of parsers, moved Tika to top.

New in Arch 1.9b

1. Fixed Nutch bug that effectively blocked use of multiple parsers on a document.
2. Improved scoring and fetching of dynamic content.
3. Ported to Nutch 1.9.
4. Improved identification of junk records and IP addresses to ignore when analysing log files.
5. Improved (more scalable) identification and removal of duplicated URLs.
6. Added removal of gone URLs from the Solr index.
7. Small bug fixes.

New in Arch 1.7

1. Added a plugin for use of H2 RDBMS.
2. Added Jetty servlet engine and made it default Solr (index) server.
3. Ported to Nutch 1.7.
4. Made per-site configuration folders optional.
5. Simplified deployment for simple/small sites. It takes 15 minutes now to deploy Arch.
6. Small bug fixes.

New in Arch 1.6

Fixed bugs found in version 1.6b.

New in Arch 1.6b

1. Ported to Nutch 1.6.
2. Added scanning web pages for threats and vulnerabilities.
3. Added reporting of various changes, such as new pages, scripts, added or removed links.
4. Added customizable page pruning before indexing.
5. Made output Level A Web Content Accessibility Guidelines (WCAG) 2.0 compliant.
6. Small bug fixes.

New in Arch 1.43

1. Last minute improvements to faceting and minor bug fixes.

New in Arch 1.42

1. Added easy to customize faceted search. See section 5.10 of the deployment manual. 
2. Added remote log processing. Read more in section 4.7.1 of the deployment manual.
3. Added an option to delete logs after processing.
4. Added an option to switch security OFF (e.g. for debugging).
5. Added a DB plugin that uses Apache Derby instead of MySQL.
6. Removed jars that were conflicting with Tomcat in some setups.
7. Changed the default protocol plugin from protocol-httpclient to protocol-http.

New in Arch 1.41

1. Introduced a GUI based configuration management tool (at beta stage now). Read more in section 4.9 
of the deployment manual.
2. Small bug fixes.

New in Arch 1.4

1. Fixed locale specific differences in writing and reading area indexing time.
2. Tuned Solr query processing parameters to optimise search precision.
3. Limited Solr Porter stemmer to performing just the first two stages of the algorithm.
4. Implemented a fix to Solr suboptimal porocessing of tokenizable strings.
5. Other small bug fixes. 

New in Arch 1.4b2

1. Removed obsolete cleanup.on.fail parameter from configuration files.
2. Added a couple of examples to httpclient-auth.xml.
3. Cleaned core-site.xml.
4. Corrected the deployment manual: arch seach form is not available before first crawl is done.
5. Fixed comment lines in Arch config files. They were causing problems.
6. Added bad config line reporting.
7. Fixed a Nutch bug in removing duplicates.
8. Added admin IP based authenticator.
8. Updated solrconfig: secured all unsecured request handlers with admin IP based authentication.
9. Added default admin request handler.

New in Arch 1.4b

1. Ported to Nutch 1.4 - a completely new architecture. See Arch White Paper. 
2. Added parallel crawling of multiple sites.
3. Simplified configuration and restructured configuration data.
4. Optimized use of RDB connections.
5. Fixed many bugs caused by the big move, and very likely, added new ones.

New in Arch 1.23

1. Added compatibility with Windows and Cygwin.

New in Arch 1.22

1. Added a "hot switch" to the new index after crawl using a HTTP request.
2. Fixed a bug in reuse of old index segment. 

New in Arch 1.21

1. Ported to the latest release of Nutch 1.2.
2. Added bookmarks as a special area. This allows indexing sets of third party pages.
3. Added email notifications. 
4. Fixed a few minor bugs and issues.




 


