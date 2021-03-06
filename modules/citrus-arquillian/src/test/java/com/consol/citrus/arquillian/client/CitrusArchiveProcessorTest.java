/*
 * Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.arquillian.client;

import com.consol.citrus.Citrus;
import com.consol.citrus.arquillian.configuration.CitrusConfiguration;
import com.consol.citrus.arquillian.helper.InjectionHelper;
import org.easymock.EasyMock;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.*;
import org.jboss.shrinkwrap.api.spec.*;
import org.jboss.shrinkwrap.impl.base.MemoryMapArchiveImpl;
import org.jboss.shrinkwrap.impl.base.spec.*;
import org.springframework.util.ReflectionUtils;
import org.testng.Assert;
import org.testng.annotations.*;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.easymock.EasyMock.*;

public class CitrusArchiveProcessorTest {

    private CitrusArchiveProcessor archiveProcessor = new CitrusArchiveProcessor();
    private Instance<CitrusConfiguration> configurationInstance = EasyMock.createMock(Instance.class);

    private CitrusConfiguration configuration;

    @BeforeClass
    public void setCitrusVersion() {
        Field version = ReflectionUtils.findField(Citrus.class, "version");
        ReflectionUtils.makeAccessible(version);
        ReflectionUtils.setField(version, Citrus.class, "2.3-SNAPSHOT");
    }

    @BeforeMethod
    public void prepareConfiguration() throws IllegalAccessException {
        configuration = CitrusConfiguration.from(new Properties());

        reset(configurationInstance);
        expect(configurationInstance.get()).andReturn(configuration).anyTimes();
        replay(configurationInstance);

        InjectionHelper.inject(archiveProcessor, "configurationInstance", configurationInstance);
    }

    @Test
    public void testProcessEnterpriseArchive() throws Exception {
        EnterpriseArchive enterpriseArchive = new EnterpriseArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(enterpriseArchive, new TestClass(this.getClass()));
        verifyArtifact(enterpriseArchive, "/citrus-core-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-jms-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-http-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-websocket-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-ws-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-ftp-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-camel-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-ssh-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-mail-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-vertx-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-java-dsl-.*jar");

        JavaArchive javaArchive = new JavaArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(javaArchive, new TestClass(this.getClass()));
        Assert.assertEquals(javaArchive.getContent().size(), 0L);

        verify(configurationInstance);
    }

    @Test
    public void testProcessExplicitCitrusVersion() throws Exception {
        configuration.setCitrusVersion(Citrus.getVersion());

        EnterpriseArchive enterpriseArchive = new EnterpriseArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(enterpriseArchive, new TestClass(this.getClass()));
        verifyArtifact(enterpriseArchive, "/citrus-core-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-jms-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-http-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-websocket-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-ws-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-ftp-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-camel-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-ssh-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-mail-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-vertx-.*jar");
        verifyArtifact(enterpriseArchive, "/citrus-java-dsl-.*jar");

        verify(configurationInstance);
    }

    @Test
    public void testProcessWebArchive() throws Exception {
        WebArchive webArchive = new WebArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(webArchive, new TestClass(this.getClass()));
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-core-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-jms-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-http-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-websocket-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-ws-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-ftp-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-camel-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-ssh-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-mail-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-vertx-.*jar");
        verifyArtifact(webArchive, "/WEB-INF/lib/citrus-java-dsl-.*jar");

        verify(configurationInstance);
    }

    @Test
    public void testProcessNoAutoPackage() throws Exception {
        configuration.setAutoPackage(false);

        EnterpriseArchive enterpriseArchive = new EnterpriseArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(enterpriseArchive, new TestClass(this.getClass()));
        Assert.assertEquals(enterpriseArchive.getContent().size(), 0L);
        JavaArchive javaArchive = new JavaArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(javaArchive, new TestClass(this.getClass()));
        Assert.assertEquals(javaArchive.getContent().size(), 0L);
        WebArchive webArchive = new WebArchiveImpl(new MemoryMapArchiveImpl(new ConfigurationBuilder().build()));
        archiveProcessor.process(webArchive, new TestClass(this.getClass()));
        Assert.assertEquals(webArchive.getContent().size(), 0L);

        verify(configurationInstance);
    }

    private void verifyArtifact(Archive archive, String expectedFileNamePattern) {
        for (Object path : archive.getContent().keySet()) {
            if (((ArchivePath) path).get().matches(expectedFileNamePattern)) {
                return;
            }
        }

        Assert.fail("Missing artifact resource for file name pattern: " + expectedFileNamePattern);
    }
}