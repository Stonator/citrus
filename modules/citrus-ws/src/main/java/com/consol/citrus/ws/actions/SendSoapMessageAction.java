/*
 * Copyright 2006-2010 the original author or authors.
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

package com.consol.citrus.ws.actions;

import com.consol.citrus.actions.SendMessageAction;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.message.Message;
import com.consol.citrus.util.FileUtils;
import com.consol.citrus.util.XMLUtils;
import com.consol.citrus.ws.SoapAttachment;
import com.consol.citrus.ws.message.SoapMessage;
import com.consol.citrus.xml.XsdSchemaRepository;
import com.consol.citrus.xml.schema.TargetNamespaceSchemaMappingStrategy;
import com.consol.citrus.xml.schema.XsdSchemaMappingStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.xml.xsd.XsdSchema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Message sender implementation sending SOAP messages.
 * 
 *  This special implementation adds SOAP attachment support to normal
 *  message sender.
 *  
 * @author Christoph Deppisch
 */
public class SendSoapMessageAction extends SendMessageAction {

	private static Logger log = LoggerFactory.getLogger(SendSoapMessageAction.class);

    /** SOAP attachment data */
    private String attachmentData;
    
    /** SOAP attachment data as external file resource path */
    private String attachmentResourcePath;
    
    /** SOAP attachment */
	private SoapAttachment attachment = new SoapAttachment();

	/**
	 * enable/disable mtom attachments
	 */
	private Boolean mtomEnabled = false;

    @Override
    protected SoapMessage createMessage(TestContext context, String messageType) {
        Message message = super.createMessage(context, getMessageType());

		final String attachmentContent;

		try {
            if (StringUtils.hasText(attachmentData)) {
				attachmentContent = context.replaceDynamicContentInString(attachmentData);
				attachment.setContent(attachmentContent);
			} else if (attachmentResourcePath != null) {
				if (attachment.getContentType().startsWith("text/")) {
					attachmentContent = context.replaceDynamicContentInString(FileUtils.readToString(FileUtils.getFileResource(attachmentResourcePath, context)));
					attachment.setContent(attachmentContent);
				} else {
					// binary content
					final Resource attachmentResource = FileUtils.getFileResource(attachmentResourcePath, context);
					attachment.setContent(new DataHandler(new DataSource() {
						@Override
						public InputStream getInputStream() throws IOException {
							return attachmentResource.getInputStream();
						}

						@Override
						public OutputStream getOutputStream() throws IOException {
							throw new UnsupportedOperationException("Not supported yet.");
						}

						@Override
						public String getContentType() {
							return attachment.getContentType();
						}

						@Override
						public String getName() {
							return attachmentResource.getFilename();
						}
					}));
				}
            } else {
				attachmentContent = null;
				attachment = null;
			}

			if (attachment != null && mtomEnabled) {
//				Document doc = XMLUtils.parseMessagePayload(message.getPayload().toString());
//				message.setPayload(XMLUtils.serialize(doc));
				String cid = "cid:" + attachment.getContentId();
				String messagePayload = message.getPayload().toString();
				if (attachment.getMtomInline()) {
					if (messagePayload.contains(cid) && attachment.getInputStream().available() > 0) {
						String xsiType = getAttachmentXsiType(context, message, cid);
						if (xsiType.equals("base64binary")) {
							messagePayload = messagePayload.replaceAll(cid, Base64.encodeBase64String(IOUtils.toByteArray(attachment.getInputStream())));
						} else if (xsiType.equals("hexBinary")) {
							messagePayload = messagePayload.replaceAll(cid, Hex.encodeHexString(IOUtils.toByteArray(attachment.getInputStream())));
						} else {
							throw new CitrusRuntimeException("Unsupported xsiType<" + xsiType + "> for attachment " + cid);
						}
						attachment = null;
					}
				} else {
					messagePayload = messagePayload.replaceAll(cid, "<xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\" href=\"" + cid + "\"/>");
				}
				message.setPayload(messagePayload);
			}

        } catch (IOException e) {
            throw new CitrusRuntimeException(e);
        }

		final SoapMessage soapMessage = new SoapMessage(message);
		soapMessage.setMtomEnabled(mtomEnabled);
		
		if (attachment != null) {
			soapMessage.addAttachment(attachment);
		}
		
        return soapMessage;
    }

    /**
     * Set the Attachment data file resource.
     * @param attachment the attachment to set
     */
    public void setAttachmentResourcePath(String attachment) {
        this.attachmentResourcePath = attachment;
    }

    /**
     * Set the content type, delegates to soap attachment.
     * @param contentType the contentType to set
     */
    public void setContentType(String contentType) {
        attachment.setContentType(contentType);
    }

    /**
     * Set the content id, delegates to soap attachment.
     * @param contentId the contentId to set
     */
    public void setContentId(String contentId) {
        attachment.setContentId(contentId);
    }
    
    /**
     * Set the charset name, delegates to soap attachment.
     * @param charsetName the charsetName to set
     */
    public void setCharsetName(String charsetName) {
        attachment.setCharsetName(charsetName);
    }

    /**
     * Set the attachment data as string value.
     * @param attachmentData the attachmentData to set
     */
    public void setAttachmentData(String attachmentData) {
        this.attachmentData = attachmentData;
    }

    /**
     * Gets the attachmentData.
     * @return the attachmentData
     */
    public String getAttachmentData() {
        return attachmentData;
    }

    /**
     * Gets the attachmentResource.
     * @return the attachmentResource
     */
    public String getAttachmentResourcePath() {
        return attachmentResourcePath;
    }

    /**
     * Gets the attachment.
     * @return the attachment
     */
    public SoapAttachment getAttachment() {
        return attachment;
	}

	/**
	 * Enable or disable mtom attachments
	 *
	 * @param mtomEnabled
	 */
	public void setMtomEnabled(Boolean enable) {
		this.mtomEnabled = enable;
	}

	public Boolean getMtomEnabled() {
		return this.mtomEnabled;
	}

	/**
	 * Set the mtom-inline, delegates to soap attachment.
	 *
	 * @param contentId the contentId to set
	 */
	public void setMtomInline(Boolean inline) {
		attachment.setMtomInline(inline);
	}

	private String getAttachmentXsiType(TestContext context, Message message, String cid) {
		String xsiType = "base64binary";
		String xmlMessage = message.getPayload().toString();
		XsdSchemaRepository schemaRepository = context.getApplicationContext().getBean("schemaRepository", XsdSchemaRepository.class);
		if (schemaRepository != null) {
			XsdSchemaMappingStrategy schemaMappingStrategy = new TargetNamespaceSchemaMappingStrategy();
			XsdSchema schema = schemaMappingStrategy.getSchema(
					schemaRepository.getSchemas(), XMLUtils.parseMessagePayload(xmlMessage));
			if (schema == null) {
				log.error("No matching schema found to parse the attachment xml element for cid: " + cid);
			} else {
				try {
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					dbf.setNamespaceAware(true);
					SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

					ArrayList<Source> schemaList = new ArrayList(schemaRepository.getSchemas().size());
					for (XsdSchema xsd : schemaRepository.getSchemas()) {
						schemaList.add(xsd.getSource());
					}
					dbf.setSchema(sf.newSchema(schemaList.toArray(new Source[schemaList.size()])));
					DocumentBuilder db = dbf.newDocumentBuilder();
					Document doc = db.parse(new InputSource(new StringReader(xmlMessage)));
					doc.getDocumentElement().normalize();

					XPath xPath = XPathFactory.newInstance().newXPath();
					Node node = (Node) xPath.compile("//[text()=" + cid + "]").evaluate(doc, XPathConstants.NODE);
					if (node instanceof Element) {
						xsiType = ((Element) node).getSchemaTypeInfo().getTypeName();
					} else {
						log.warn("parent element of cid: " + cid + " not found in xml payload.");
					}
				} catch (SAXException | ParserConfigurationException | IOException | XPathExpressionException e) {
					log.warn(e.getLocalizedMessage(), e);
				}
			}
		}

		return xsiType;
	}
}
