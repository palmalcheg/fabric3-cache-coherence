package org.fabric3.cache.coherence.provision;

import org.fabric3.cache.spi.PhysicalCacheResourceDefinition;
import org.w3c.dom.Document;

public class CoherencePhysicalResourceDefinition extends PhysicalCacheResourceDefinition {
    private static final long serialVersionUID = -6400612928297999316L;
    private Document configuration;

    public CoherencePhysicalResourceDefinition(String cacheName, Document configuration) {
        super(cacheName);
        this.configuration = configuration;
    }

    public Document getConfiguration() {
        return configuration;
    }
	
}