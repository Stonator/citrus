/*
 * Copyright 2006-2011 the original author or authors.
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

package com.consol.citrus.ws.message.converter;

import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.message.Message;
import com.consol.citrus.message.MessageHeaderUtils;
import com.consol.citrus.message.MessageHeaders;
import com.consol.citrus.ws.SoapAttachment;
import com.consol.citrus.ws.client.WebServiceEndpointConfiguration;
import com.consol.citrus.ws.message.SoapMessage;
import com.consol.citrus.ws.message.SoapMessageHeaders;
import com.consol.citrus.ws.message.callback.SoapResponseMessageCallback;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.mime.Attachment;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.axiom.AxiomSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.springframework.xml.namespace.QNameUtils;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;

/**
 * Default converter implementation for SOAP messages. By default strips away the SOAP envelope and constructs internal message representation
 * from incoming SOAP request messages. Response messages are created from internal message representation accordingly.
 *
 * @author Christoph Deppisch
 * @since 2.0
 */
public class SoapMessageConverter implements WebServiceMessageConverter {

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(SoapResponseMessageCallback.class);
    
    /** Should keep soap envelope when creating internal message */
    private boolean keepSoapEnvelope = false;

    @Override
    public WebServiceMessage convertOutbound(Message internalMessage, WebServiceEndpointConfiguration endpointConfiguration) {
        WebServiceMessage message = endpointConfiguration.getMessageFactory().createWebServiceMessage();
        convertOutbound(message, internalMessage, endpointConfiguration);
        return message;
    }

    @Override
    public void convertOutbound(WebServiceMessage webServiceMessage, Message message, WebServiceEndpointConfiguration endpointConfiguration) {
        org.springframework.ws.soap.SoapMessage soapRequest = ((org.springframework.ws.soap.SoapMessage)webServiceMessage);

        SoapMessage soapMessage;
        if (message instanceof SoapMessage) {
            soapMessage = (SoapMessage) message;
        } else {
            soapMessage = new SoapMessage(message);
        }

        // Copy payload into soap-body:
        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(new StringSource(soapMessage.getPayload().toString()), soapRequest.getSoapBody().getPayloadResult());
        } catch (TransformerException e) {
            throw new CitrusRuntimeException("Failed to write SOAP body payload", e);
        }

        // Copy headers into soap-header:
        for (Entry<String, Object> headerEntry : soapMessage.copyHeaders().entrySet()) {
            if (MessageHeaderUtils.isSpringInternalHeader(headerEntry.getKey())) {
                continue;
            }

            if (headerEntry.getKey().equalsIgnoreCase(SoapMessageHeaders.SOAP_ACTION)) {
                soapRequest.setSoapAction(headerEntry.getValue().toString());
            } else if (headerEntry.getKey().toLowerCase().startsWith(SoapMessageHeaders.HTTP_PREFIX)) {
                handleOutboundMimeMessageHeader(soapRequest,
                        headerEntry.getKey().substring(SoapMessageHeaders.HTTP_PREFIX.length()),
                        headerEntry.getValue(),
                        endpointConfiguration.isHandleMimeHeaders());
            } else if (!headerEntry.getKey().startsWith(MessageHeaders.PREFIX)) {
                SoapHeaderElement headerElement;
                if (QNameUtils.validateQName(headerEntry.getKey())) {
                    headerElement = soapRequest.getSoapHeader().addHeaderElement(QNameUtils.parseQNameString(headerEntry.getKey()));
                } else {
                    headerElement = soapRequest.getSoapHeader().addHeaderElement(QNameUtils.createQName("", headerEntry.getKey(), ""));
                }

                headerElement.setText(headerEntry.getValue().toString());
            }
        }

        for (String headerData : soapMessage.getHeaderData()) {
            try {
                Transformer transformer = transformerFactory.newTransformer();
                transformer.transform(new StringSource(headerData),
                        soapRequest.getSoapHeader().getResult());
            } catch (TransformerException e) {
                throw new CitrusRuntimeException("Failed to write SOAP header content", e);
            }
        }

        for (final Attachment attachment : soapMessage.getAttachments()) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Adding attachment to SOAP message: '%s' ('%s')", attachment.getContentId(), attachment.getContentType()));
			}

			if (soapMessage.getMtomEnabled()) {
				if (soapRequest instanceof SaajSoapMessage) {
					log.debug("Converting SaajSoapMessage to XOP package");
					((SaajSoapMessage) soapRequest).convertToXopPackage();
				} else if (soapRequest instanceof AxiomSoapMessage) {
					log.warn("AxiomSoapMessage cannot be converted to XOP package");
				}
			}
			soapRequest.addAttachment(attachment.getContentId(), new InputStreamSource() {
				public InputStream getInputStream() throws IOException {
					return attachment.getInputStream();
				}
			}, attachment.getContentType());
        }
    }

    @Override
    public SoapMessage convertInbound(WebServiceMessage message, WebServiceEndpointConfiguration endpointConfiguration) {
        return convertInbound(message, null, endpointConfiguration);
    }

    @Override
    public SoapMessage convertInbound(WebServiceMessage webServiceMessage, MessageContext messageContext, WebServiceEndpointConfiguration endpointConfiguration) {
        try {
            StringResult payloadResult = new StringResult();

            if (keepSoapEnvelope) {
                webServiceMessage.writeTo(payloadResult.getOutputStream());
            } else if (webServiceMessage.getPayloadSource() != null) {
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.transform(webServiceMessage.getPayloadSource(), payloadResult);
            }

            SoapMessage message = new SoapMessage(payloadResult.toString());

            handleInboundMessageProperties(messageContext, message);

            if (webServiceMessage instanceof org.springframework.ws.soap.SoapMessage) {
                handleInboundSoapMessage((org.springframework.ws.soap.SoapMessage) webServiceMessage, message, endpointConfiguration);
            }

            handleInboundHttpHeaders(message);

            return message;
        } catch (TransformerException e) {
            throw new CitrusRuntimeException("Failed to read web service message payload source", e);
        } catch (IOException e) {
            throw new CitrusRuntimeException("Failed to read web service message");
        }
    }

    /**
     * Method handles SOAP specific message information such as SOAP action headers and SOAP attachments.
     *
     * @param soapMessage
     * @param message
     * @param endpointConfiguration
     */
    protected void handleInboundSoapMessage(org.springframework.ws.soap.SoapMessage soapMessage, SoapMessage message, WebServiceEndpointConfiguration endpointConfiguration) {
        handleInboundSoapHeaders(soapMessage, message);
        handleInboundAttachments(soapMessage, message);

        if (endpointConfiguration.isHandleMimeHeaders()) {
            handleInboundMimeHeaders(soapMessage, message);
        }
    }

    /**
     * Reads information from Http connection and adds them as Http marked headers to internal message representation.
     *
     * @param message
     */
    protected void handleInboundHttpHeaders(SoapMessage message) {
        TransportContext transportContext = TransportContextHolder.getTransportContext();
        if (transportContext == null) {
            log.warn("Unable to get complete set of http request headers - no transport context available");
            return;
        }

        WebServiceConnection connection = transportContext.getConnection();
        if (connection instanceof HttpServletConnection) {
            UrlPathHelper pathHelper = new UrlPathHelper();
            HttpServletConnection servletConnection = (HttpServletConnection) connection;
            message.setHeader(SoapMessageHeaders.HTTP_REQUEST_URI, pathHelper.getRequestUri(servletConnection.getHttpServletRequest()));
            message.setHeader(SoapMessageHeaders.HTTP_CONTEXT_PATH, pathHelper.getContextPath(servletConnection.getHttpServletRequest()));

            String queryParams = pathHelper.getOriginatingQueryString(servletConnection.getHttpServletRequest());
            message.setHeader(SoapMessageHeaders.HTTP_QUERY_PARAMS, queryParams != null ? queryParams : "");

            message.setHeader(SoapMessageHeaders.HTTP_REQUEST_METHOD, servletConnection.getHttpServletRequest().getMethod().toString());
        } else {
            log.warn("Unable to get complete set of http request headers");

            try {
                message.setHeader(SoapMessageHeaders.HTTP_REQUEST_URI, connection.getUri());
            } catch (URISyntaxException e) {
                log.warn("Unable to get http request uri from http connection", e);
            }
        }
    }

    /**
     * Reads all soap headers from web service message and 
     * adds them to message builder as normal headers. Also takes care of soap action header.
     * 
     * @param soapMessage the web service message.
     * @param message the response message builder.
     */
    protected void handleInboundSoapHeaders(org.springframework.ws.soap.SoapMessage soapMessage, SoapMessage message) {
        try {
            SoapHeader soapHeader = soapMessage.getSoapHeader();

            if (soapHeader != null) {
                Iterator<?> iter = soapHeader.examineAllHeaderElements();
                while (iter.hasNext()) {
                    SoapHeaderElement headerEntry = (SoapHeaderElement) iter.next();
                    MessageHeaderUtils.setHeader(message, headerEntry.getName().getLocalPart(), headerEntry.getText());
                }

                if (soapHeader.getSource() != null) {
                    StringResult headerData = new StringResult();
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.transform(soapHeader.getSource(), headerData);

                    message.addHeaderData(headerData.toString());
                }
            }

            if (StringUtils.hasText(soapMessage.getSoapAction())) {
                if (soapMessage.getSoapAction().equals("\"\"")) {
                    message.setHeader(SoapMessageHeaders.SOAP_ACTION, "");
                } else {
                    if (soapMessage.getSoapAction().startsWith("\"") && soapMessage.getSoapAction().endsWith("\"")) {
                        message.setHeader(SoapMessageHeaders.SOAP_ACTION,
                                soapMessage.getSoapAction().substring(1, soapMessage.getSoapAction().length() - 1));
                    } else {
                        message.setHeader(SoapMessageHeaders.SOAP_ACTION, soapMessage.getSoapAction());
                    }
                }
            }
        } catch (TransformerException e) {
            throw new CitrusRuntimeException("Failed to read SOAP header source", e);
        }
    }

    /**
     * Adds a HTTP message header to the SOAP message.
     *
     * @param message the SOAP request message.
     * @param name the header name.
     * @param value the header value.
     * @param handleMimeHeaders should handle mime headers.
     */
    private void handleOutboundMimeMessageHeader(org.springframework.ws.soap.SoapMessage message, String name, Object value, boolean handleMimeHeaders) {
        if (!handleMimeHeaders) {
            return;
        }

        if (message instanceof SaajSoapMessage) {
            SaajSoapMessage soapMsg = (SaajSoapMessage) message;
            MimeHeaders headers = soapMsg.getSaajMessage().getMimeHeaders();
            headers.setHeader(name, value.toString());
        } else if (message instanceof AxiomSoapMessage) {
            log.warn("Unable to set mime message header '" + name + "' on AxiomSoapMessage - unsupported");
        } else {
            log.warn("Unsupported SOAP message implementation - unable to set mime message header '" + name + "'");
        }
    }
    
    /**
     * Adds mime headers to constructed response message. This can be HTTP headers in case
     * of HTTP transport. Note: HTTP headers may have multiple values that are represented as 
     * comma delimited string value.
     * 
     * @param soapMessage the source SOAP message.
     * @param message the message build constructing the result message.
     */
    protected void handleInboundMimeHeaders(org.springframework.ws.soap.SoapMessage soapMessage, SoapMessage message) {
        Map<String, String> mimeHeaders = new HashMap<String, String>();
        MimeHeaders messageMimeHeaders = null;
        
        // to get access to mime headers we need to get implementation specific here
        if (soapMessage instanceof SaajSoapMessage) {
            messageMimeHeaders = ((SaajSoapMessage)soapMessage).getSaajMessage().getMimeHeaders();
        } else if (soapMessage instanceof AxiomSoapMessage) {
            // we do not handle axiom message implementations as it is very difficult to get access to the mime headers there
            log.warn("Skip mime headers for AxiomSoapMessage - unsupported");
        } else {
            log.warn("Unsupported SOAP message implementation - skipping mime headers");
        }
        
        if (messageMimeHeaders != null) {
            Iterator<?> mimeHeaderIterator = messageMimeHeaders.getAllHeaders();
            while (mimeHeaderIterator.hasNext()) {
                MimeHeader mimeHeader = (MimeHeader)mimeHeaderIterator.next();
                // http headers can have multpile values so headers might occur several times in map
                if (mimeHeaders.containsKey(mimeHeader.getName())) {
                    // header is already present, so concat values to a single comma delimited string
                    String value = mimeHeaders.get(mimeHeader.getName());
                    value += ", " + mimeHeader.getValue();
                    mimeHeaders.put(mimeHeader.getName(), value);
                } else {
                    mimeHeaders.put(mimeHeader.getName(), mimeHeader.getValue());
                }
            }
            
            for (Entry<String, String> httpHeaderEntry : mimeHeaders.entrySet()) {
                message.setHeader(httpHeaderEntry.getKey(), httpHeaderEntry.getValue());
            }
        }
    }
    
    /**
     * Adds all message properties from web service message to message builder 
     * as normal header entries.
     * 
     * @param messageContext the web service request message context.
     * @param message the request message builder.
     */
    protected void handleInboundMessageProperties(MessageContext messageContext, SoapMessage message) {
        if (messageContext == null) { return; }
        
        String[] propertyNames = messageContext.getPropertyNames();
        if (propertyNames != null) {
            for (String propertyName : propertyNames) {
                message.setHeader(propertyName, messageContext.getProperty(propertyName));
            }
        }
    }
    
    /**
     * Adds attachments if present in soap web service message.
     * 
     * @param soapMessage the web service message.
     * @param message the response message builder.
     * @throws IOException 
     */
    protected void handleInboundAttachments(org.springframework.ws.soap.SoapMessage soapMessage, SoapMessage message) {
        Iterator<?> attachments = soapMessage.getAttachments();

        while (attachments.hasNext()) {
            Attachment attachment = (Attachment)attachments.next();
            SoapAttachment soapAttachment = SoapAttachment.from(attachment);

            if (log.isDebugEnabled()) {
                log.debug(String.format("SOAP message contains attachment with contentId '%s'", attachment.getContentId()));
            }

            message.addAttachment(soapAttachment);
        }
    }

    /**
     * Gets the keep soap envelope flag.
     * @return
     */
    public boolean isKeepSoapEnvelope() {
        return keepSoapEnvelope;
    }

    /**
     * Sets the keep soap header flag.
     * @param keepSoapEnvelope
     */
    public void setKeepSoapEnvelope(boolean keepSoapEnvelope) {
        this.keepSoapEnvelope = keepSoapEnvelope;
    }

}
