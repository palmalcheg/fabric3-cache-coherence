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
package org.fabric3.cache.coherence.composite;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.fabric3.cache.coherence.introspection.CoherenceTypeLoader;
import org.fabric3.cache.coherence.provision.CoherencePhysicalResourceDefinition;
import org.fabric3.cache.coherence.runtime.CoherenceCacheManager;
import org.fabric3.spi.introspection.DefaultIntrospectionContext;
import org.fabric3.spi.introspection.xml.LoaderHelper;
import org.w3c.dom.Document;

import com.tangosol.run.xml.XmlDocument;
import com.tangosol.run.xml.XmlHelper;


/**
 * Unit tests for Coherence configuration loader.
 */
public class CoherenceTestCase extends TestCase {
    
    private String TEST_CFG = "<cache-config>" +
                                "<caching-scheme-mapping/>" +
                                "<caching-schemes/>" +
                              "</cache-config>";
    
    private static  String cacheCfg = "<cache-mapping>"+
                                "<cache-name>DataCache</cache-name>"+
                                "<scheme-name>dist-default</scheme-name>"+
                              "</cache-mapping>";

    private static  String schemeCfg = 
                                "<distributed-scheme>"+
                                "<scheme-name>default-limited</scheme-name>"+
                                "<serializer>"+
                                "   <class-name>com.tangosol.io.pof.ConfigurablePofContext</class-name>"+
                                "    <init-params>"+
                                "        <init-param>"+
                                "            <param-type>string</param-type>"+
                                "            <param-value>sample-pof-config.xml</param-value>"+
                                "        </init-param>"+
                                "    </init-params>"+
                                "</serializer>"+
                                
                                "<backing-map-scheme>"+
                                "   <local-scheme>"+
                                "      <high-units>1000000</high-units>"+
                                "     <expiry-delay>1h</expiry-delay>"+
                                " </local-scheme>"+
                                "</backing-map-scheme>"+
                                "</distributed-scheme>";

	private static final String config = "<caches><cache name='DataCache'>" +
                                			 cacheCfg+
                                			 schemeCfg+
                                		 "</cache></caches>";
	
	/**
	 * Test method for {@link CoherenceTypeLoader#load(javax.xml.stream.XMLStreamReader, org.fabric3.spi.introspection.IntrospectionContext)}.
	 */
	public final void testLoad() throws Exception {
        DefaultIntrospectionContext context = new DefaultIntrospectionContext();
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(config.getBytes()));
        LoaderHelper loaderHelper = EasyMock.createMock(LoaderHelper.class);
        Document doc = EasyMock.createMock(Document.class);
        EasyMock.expect(loaderHelper.transform(reader)).andReturn(doc);
        
        EasyMock.replay(loaderHelper); 
        new CoherenceTypeLoader(loaderHelper).load(reader, context);
        EasyMock.verify(loaderHelper);
	}
	
	public final void testManagerConfiguration() throws Exception {
	   
	    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
	    Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new StreamSource(new StringReader("<cache>"+cacheCfg+schemeCfg+"</cache>")), new DOMResult(doc));
        XmlDocument cfg = XmlHelper.loadXml(new ByteArrayInputStream(TEST_CFG.getBytes()));
        
        CoherenceCacheManager mgr = new CoherenceCacheManager();
        mgr.startManager();
        
        assertTrue(cfg.findElement("caching-scheme-mapping").getElementList().size() == 0);
        assertTrue(cfg.findElement("caching-schemes").getElementList().size() == 0);
        
        CoherencePhysicalResourceDefinition configuration = new CoherencePhysicalResourceDefinition("cache", doc);
        cfg = (XmlDocument) mgr.updateEnvironmentConfiguration(cfg, configuration, null);
        
        assertTrue(cfg.findElement("caching-scheme-mapping").getElementList().size() ==1 );
        assertTrue(cfg.findElement("caching-schemes").getElementList().size() == 1);
	}
}



