/*
 * Fabric3
 * Copyright (c) 2009-2011 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.fabric3.cache.coherence.runtime;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.fabric3.cache.coherence.provision.CoherencePhysicalResourceDefinition;
import org.fabric3.cache.spi.CacheBuildException;
import org.fabric3.cache.spi.CacheManager;
import org.oasisopen.sca.annotation.Destroy;
import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Init;

import com.oracle.coherence.environment.extensible.ExtensibleEnvironment;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.run.xml.XmlDocument;
import com.tangosol.run.xml.XmlElement;
import com.tangosol.run.xml.XmlHelper;

/**
 * Manages Coherence caches on a runtime.
 */
@EagerInit
public class CoherenceCacheManager implements CacheManager<CoherencePhysicalResourceDefinition> {
	
	private ExtensibleEnvironment environment;

    private Map<String, ConfigurationPair> caches = new ConcurrentHashMap<String, ConfigurationPair>();

    private AtomicBoolean isConfigurationLoaded = new AtomicBoolean(false);

    private XmlElement globalConfiguration;

    @Destroy
    public void stopManager() {
        CacheFactory.ensureCluster().shutdown();
    }

    @Init
    public void startManager() {
        this.environment = new ExtensibleEnvironment();
        globalConfiguration = environment.getConfig();
    }

    public XmlElement updateEnvironmentConfiguration(XmlElement cfg, CoherencePhysicalResourceDefinition configuration, ConfigurationPair pair)
            throws CacheBuildException {
        XmlHelper.azzert(cfg != null);
        XmlDocument xml = parseConfiguration(configuration);
        XmlHelper.azzert(xml != null);

        XmlElement cacheMapping = xml.findElement("cache-mapping");
        if (cacheMapping == null) {
            try {
                environment.findSchemeMapping(configuration.getCacheName());
            } catch (Exception e) {
                throw new CacheBuildException(e);
            }
        } else {
        	XmlHelper.azzert("cache-config".equals(cfg.getName()));
            XmlElement cm = cfg.findElement("caching-scheme-mapping");
            List<XmlElement> elements = xml.getElementList();
            XmlElement cs = cfg.findElement("caching-schemes");
            for (XmlElement e : elements) {
                if (e.getName().endsWith("scheme")) {
                    updateConfiguration(cs, e);
                }
            }
            updateConfiguration(cm, cacheMapping);
            XmlElement physicalCacheName = cacheMapping.findElement("cache-name");
            if (pair!=null) {
                pair.physicalCache = physicalCacheName != null ? physicalCacheName.getString() : null;
            }
        }
        return cfg;
    }
    
    

    private void updateConfiguration(XmlElement parent, XmlElement elementToAdd) {
        XmlHelper.azzert(parent != null);
        if (elementToAdd != null)
            XmlHelper.addElements(parent, Arrays.asList(elementToAdd).iterator());
    }    

    /** (non-Javadoc)
     * @see org.fabric3.cache.spi.CacheManager#getCache(java.lang.String)
     * 
     * Coherence lazy initialization
     */
    public NamedCache getCache(String name) {        
        if (!isConfigurationLoaded.getAndSet(true)) {
            // TODO: initialize environment as a part of composite lifecycle, not by switcher  
            environment.setConfig(globalConfiguration);
            CacheFactory.setConfigurableCacheFactory(environment);
        }
        ConfigurationPair pair = caches.get(name);
        if (pair == null) {
            return null;
        }
        //Lazy init of a NamedCache
        else if (pair.physicalCache !=null && pair.cache == null){
            pair.cache = environment.ensureCache(pair.physicalCache, getClass().getClassLoader());
        }
        return pair.cache;
    }

    public void create(CoherencePhysicalResourceDefinition configuration) throws CacheBuildException {
    	ConfigurationPair pair = new ConfigurationPair();
        updateEnvironmentConfiguration(globalConfiguration, configuration,pair);        
        caches.put(configuration.getCacheName(), pair);
    }

    public void remove(CoherencePhysicalResourceDefinition configuration) throws CacheBuildException {
        ConfigurationPair cacheToRelease = caches.remove(configuration.getCacheName());
        if (cacheToRelease != null && cacheToRelease.cache!=null)
            cacheToRelease.cache.release();

    }

    private XmlDocument parseConfiguration(CoherencePhysicalResourceDefinition resourceDefinition)
            throws CacheBuildException {
        try {
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            Source source = new DOMSource(resourceDefinition.getConfiguration());
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(source, result);
            return XmlHelper.loadXml(writer.toString());
        } catch (TransformerConfigurationException e) {
            throw new CacheBuildException(e);
        } catch (TransformerException e) {
            throw new CacheBuildException(e);
        } catch (TransformerFactoryConfigurationError e) {
            throw new CacheBuildException(e);
        }
    }
    
    private static  class ConfigurationPair {
        public String physicalCache;
        public NamedCache cache;
    }
}