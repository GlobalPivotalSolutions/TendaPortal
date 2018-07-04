package focusedCrawler.link.frontier;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import focusedCrawler.link.LinkStorageConfig;
import focusedCrawler.util.LinkFilter;
import focusedCrawler.util.ParameterFile;

public class FrontierManagerFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(FrontierManagerFactory.class);

    public static FrontierManager create(LinkStorageConfig config,
                                  String configPath,
                                  String dataPath,
                                  String seedFile,
                                  String stoplistFile) {
        
        String[] seedUrls = ParameterFile.getSeeds(seedFile);
        
        String directory = Paths.get(dataPath, config.getLinkDirectory()).toString();
        
        Frontier frontier = null;
        if (config.isUseScope()) {
            Map<String, Integer> scope = extractDomains(seedUrls);
            frontier = new Frontier(directory, config.getMaxCacheUrlsSize(), scope);
        } else {
            frontier = new Frontier(directory, config.getMaxCacheUrlsSize());
        }
        
        LinkFilter linkFilter = new LinkFilter(configPath);
        
        LinkSelectionStrategy linkSelector = createLinkSelector(config);
        logger.info("LINK_SELECTOR: "+linkSelector.getClass().getName());
        return new FrontierManager(
                frontier,
                config.getMaxSizeLinkQueue(),
                config.getMaxSizeLinkQueue(),
                linkSelector,
                linkFilter);
    }

    private static LinkSelectionStrategy createLinkSelector(LinkStorageConfig config) {
        String linkSelectorConfig = config.getLinkSelector();
        if(linkSelectorConfig != null) {
            if(linkSelectorConfig.equals("TopkLinkSelector")) {
                return new TopkLinkSelector();
            }
            if(linkSelectorConfig.equals("PoliteTopkLinkSelector")) {
                return new PoliteTopkLinkSelector(4, 10000);
            }
            else if(linkSelectorConfig.equals("SiteLinkSelector")) {
                return new SiteLinkSelector();
            }
            else if(linkSelectorConfig.equals("RandomLinkSelector")) {
                return new RandomLinkSelector();
            }
            else if(linkSelectorConfig.equals("NonRandomLinkSelector")) {
                return new NonRandomLinkSelector();
            }
            else if(linkSelectorConfig.equals("MultiLevelLinkSelector")) {
                return new MultiLevelLinkSelector();
            }
            else if(linkSelectorConfig.equals("TopicLinkSelector")) {
                return new TopicLinkSelector();
            }
            else if(linkSelectorConfig.equals("MaximizeWebsitesLinkSelector")) {
                return new MaximizeWebsitesLinkSelector();
            }
        }
        
        // Maintain old defaults to keep compatibility
        if (config.isUseScope()) {
            if (config.getTypeOfClassifier().contains("Baseline")) {
                return new SiteLinkSelector();
            } else {
                return new MultiLevelLinkSelector();
            }
        } else {
            if (config.getTypeOfClassifier().contains("Baseline")) {
                return new NonRandomLinkSelector();
            } else {
                return new MultiLevelLinkSelector();
            }
        }
    }

    private static HashMap<String, Integer> extractDomains(String[] urls) {
        HashMap<String, Integer> scope = new HashMap<String, Integer>();
        for (int i = 0; i < urls.length; i++) {
            try {
                URL url = new URL(urls[i]);
                String host = url.getHost();
                scope.put(host, new Integer(1));
            } catch (MalformedURLException e) {
                logger.warn("Invalid URL in seeds file. Ignoring URL: " + urls[i]);
            }
        }
        logger.info("Using scope of following domains:");
        for (String host: scope.keySet()) {
            logger.info(host);
        }
        return scope;
    }

}
