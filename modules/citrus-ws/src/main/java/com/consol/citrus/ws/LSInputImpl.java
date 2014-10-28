package com.consol.citrus.ws;

import java.io.InputStream;
import java.io.Reader;
import org.w3c.dom.ls.LSInput;

/**
 * LSInput implementation that can be used in custom LSResourceResolver
 * implementations for getting schema import and include references resolved.
 * This class is responsible for holding the content of the resolved schema.
 */
public class LSInputImpl implements LSInput {

	private String baseURI;
	private String encoding;
	private String publicId;
	private String systemId;
	private String stringData;
	private Reader characterStream;
	private InputStream byteStream;
	private boolean certifiedText;

	public LSInputImpl() {
	}

	public LSInputImpl(String publicId, String systemId, InputStream byteStream) {
		this.publicId = publicId;
		this.systemId = systemId;
		this.byteStream = byteStream;
	}

	@Override
	public InputStream getByteStream() {
		return byteStream;
	}

	@Override
	public void setByteStream(InputStream byteStream) {
		this.byteStream = byteStream;
	}

	@Override
	public Reader getCharacterStream() {
		return characterStream;
	}

	@Override
	public void setCharacterStream(Reader characterStream) {
		this.characterStream = characterStream;
	}

	@Override
	public String getStringData() {
		return stringData;
	}

	@Override
	public void setStringData(String stringData) {
		this.stringData = stringData;
	}

	@Override
	public String getEncoding() {
		return encoding;
	}

	@Override
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	@Override
	public String getPublicId() {
		return publicId;
	}

	@Override
	public void setPublicId(String publicId) {
		this.publicId = publicId;
	}

	@Override
	public String getSystemId() {
		return systemId;
	}

	@Override
	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}

	@Override
	public String getBaseURI() {
		return baseURI;
	}

	@Override
	public void setBaseURI(String baseURI) {
		this.baseURI = baseURI;
	}

	@Override
	public boolean getCertifiedText() {
		return certifiedText;
	}

	@Override
	public void setCertifiedText(boolean certifiedText) {
		this.certifiedText = certifiedText;
	}

}
