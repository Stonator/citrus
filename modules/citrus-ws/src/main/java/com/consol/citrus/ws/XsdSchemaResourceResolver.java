package com.consol.citrus.ws;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.util.FileUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;
import org.springframework.xml.xsd.XsdSchema;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

public class XsdSchemaResourceResolver implements LSResourceResolver {

	private static Logger log = LoggerFactory.getLogger(XsdSchemaResourceResolver.class);

	private final List<XsdSchema> schemas;
	private final List<Resource> locations;

	public XsdSchemaResourceResolver(List<XsdSchema> schemas, List<String> schemaRepoLocations) {
		this.schemas = schemas;
		this.locations = new ArrayList<Resource>();

		PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

		try {
			for (String location : schemaRepoLocations) {
				Resource[] findings = resourcePatternResolver.getResources(location);

				for (Resource resource : findings) {
					if (resource.getFilename().endsWith(".xsd")) {
						this.locations.add(resource);
					}
				}
			}
		} catch (IOException e) {
			log.error(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
		LSInputImpl input = null;

		XsdSchema schema = getSchemaForNamespace(namespaceURI);
		if (schema != null) {
			try {
				Resource schemaResource = getLocationForSchema(systemId);

				input = new LSInputImpl();

				input.setByteStream(schemaResource.getInputStream());
				input.setPublicId(publicId);
				input.setSystemId(systemId);
				input.setBaseURI(baseURI);
			} catch (IOException e) {
				log.error(e.getLocalizedMessage(), e);
			}
		}
		return input;
	}

	private XsdSchema getSchemaForNamespace(String namespace) {
		for (XsdSchema schema : schemas) {
			if (StringUtils.hasText(schema.getTargetNamespace())
					&& schema.getTargetNamespace().equals(namespace)) {
				return schema;
			}
		}

		return null;
	}

	private Resource getLocationForSchema(String systemId) {
		for (Resource location : locations) {
			if (StringUtils.hasText(systemId)
					&& location.getFilename().toLowerCase().contains(systemId.toLowerCase())) {
				return location;
			}
		}

		return null;
	}
}
