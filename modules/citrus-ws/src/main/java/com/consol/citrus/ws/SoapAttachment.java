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

package com.consol.citrus.ws;

import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.util.FileUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.springframework.ws.mime.Attachment;

/**
 * Citrus SOAP attachment implementation.
 *
 * @author Christoph Deppisch
 */
public class SoapAttachment implements Attachment, Serializable {

    /** Serial */
    private static final long serialVersionUID = 6277464458242523954L;

    /** Content body as string */
	private String content;

	/**
	 * Content body as binary
	 */
	private DataHandler binaryContent = null;

    /** Content type */
    private String contentType = "text/plain";

    /** Content identifier */
    private String contentId = null;

    /** Chosen charset of content body */
	private String charsetName = "UTF-8";

	/**
	 * send mtom attachments inline as hex or base64 coded
	 */
	private Boolean mtomInline = false;

    /**
     * Default constructor
     */
    public SoapAttachment() {
    }

    /**
     * Static construction method from Spring mime attachment.
     * @param attachment
     * @return
     */
    public static SoapAttachment from(Attachment attachment) {
        SoapAttachment soapAttachment = new SoapAttachment();
        soapAttachment.setContentId(attachment.getContentId());
        soapAttachment.setContentType(attachment.getContentType());

		if (attachment.getContentType().startsWith("text/")) {
			// text content
			try {
				soapAttachment.setContent(FileUtils.readToString(attachment.getInputStream()).trim());
			} catch (IOException e) {
            throw new CitrusRuntimeException("Failed to read SOAP attachment content", e);
			}
		} else {
			// binary content
			soapAttachment.setContent(attachment.getDataHandler());
		}

        //TODO set charset name from attachment
        soapAttachment.setCharsetName("UTF-8");

        return soapAttachment;
    }

    /**
     * Constructor using fields.
     * @param content
     */
    public SoapAttachment(String content) {
        this.content = content;
    }

    /**
     * @see org.springframework.ws.mime.Attachment#getContentId()
     */
    public String getContentId() {
        return contentId;
    }

    /**
     * @see org.springframework.ws.mime.Attachment#getContentType()
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @see org.springframework.ws.mime.Attachment#getDataHandler()
     */
	public DataHandler getDataHandler() {
		if (binaryContent != null) {
			return binaryContent;
		} else {
			return new DataHandler(new DataSource() {
				public OutputStream getOutputStream() throws IOException {
					throw new UnsupportedOperationException();
				}

				public String getName() {
					return contentId;
				}

				public InputStream getInputStream() throws IOException {
					return new ByteArrayInputStream(content.getBytes(charsetName));
				}

				public String getContentType() {
					return contentType;
				}
			});
		}
    }

    /**
     * @see org.springframework.ws.mime.Attachment#getInputStream()
     */
	public InputStream getInputStream() throws IOException {
		return getDataHandler().getInputStream();
    }

    /**
     * @see org.springframework.ws.mime.Attachment#getSize()
     */
	public long getSize() {
		try {
			if (content != null) {
				return content.getBytes(charsetName).length;
			} else if (binaryContent != null) {
				return getSizeOfContent(binaryContent.getInputStream());
			} else {
				return 0;
			}
        } catch (UnsupportedEncodingException e) {
            throw new CitrusRuntimeException(e);
		} catch (IOException ioe) {
			throw new CitrusRuntimeException(ioe);
		}
	}

    @Override
    public String toString() {
		return String.format("%s [contentId: %s, contentType: %s, content: %s]", getClass().getSimpleName().toUpperCase(), contentId, contentType, getContent());
    }

    /**
     * Get the content body.
     * @return the content
     */
	public String getContent() {
		if (binaryContent != null) {
			try {
				return Base64.encodeBase64String(IOUtils.toByteArray(binaryContent.getInputStream()));
			} catch (IOException e) {
				throw new CitrusRuntimeException(e);
			}
		} else {
			return content;
		}
    }

    /**
     * Set the content body.
     * @param content the content to set
     */
    public void setContent(String content) {
		this.content = content;
		this.binaryContent = null;
	}

	/**
	 * Set the content body as binary data.
	 *
	 * @param content the content to set
	 */
	public void setContent(DataHandler content) {
		this.binaryContent = content;
		this.content = null;
	}

    /**
     * Get the charset name.
     * @return the charsetName
     */
    public String getCharsetName() {
        return charsetName;
    }

    /**
     * Set the charset name.
     * @param charsetName the charsetName to set
     */
    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

    /**
     * Set the content type.
     * @param contentType the contentType to set
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Set the content id.
     * @param contentId the contentId to set
     */
    public void setContentId(String contentId) {
        this.contentId = contentId;
	}

	public void setMtomInline(Boolean inline) {
		this.mtomInline = inline;
	}

	public Boolean getMtomInline() {
		return this.mtomInline;
	}

	/**
	 * Get size in bytes of the given input stream
	 *
	 * @param is Read all data from stream to calculate size of the stream
	 */
	private static long getSizeOfContent(InputStream is) throws IOException {
		long size = 0;
		while (is.read() != -1) {
			size++;
		}
		return size;
	}
}