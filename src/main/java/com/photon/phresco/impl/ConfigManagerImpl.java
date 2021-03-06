package com.photon.phresco.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.photon.phresco.api.ConfigManager;
import com.photon.phresco.commons.model.CertificateInfo;
import com.photon.phresco.configuration.ConfigReader;
import com.photon.phresco.configuration.Configuration;
import com.photon.phresco.configuration.Environment;
import com.photon.phresco.exception.ConfigurationException;
import com.photon.phresco.exception.PhrescoException;
import com.photon.phresco.util.Utility;

public class ConfigManagerImpl implements ConfigManager {
	
	private static Document document = null;
	private static Element rootElement = null;
	private File configFile = null;
	
	public ConfigManagerImpl(File configFile) throws ConfigurationException {
		this.configFile = configFile;
		try {
			if(!configFile.exists()) {
				createNewDoc();
			} else {
				createDocFromExist(new FileInputStream(configFile));
			}
		} catch (Exception e) {
			throw new ConfigurationException(e);
		}
		
	}
	
	@Override
	public List<Environment> getEnvironments(List<String> environmentNames)
			throws ConfigurationException {
		if(!configFile.exists()) {
			throw new ConfigurationException("Config File Not Exists");
		}
		ConfigReader configReader = new ConfigReader(configFile);
		return configReader.getEnvironments(environmentNames);
	}
	
	@Override
	public void addEnvironments(List<Environment> environments) throws ConfigurationException {
		// This block will remove all the env node due to modify the order of Environment
		StringBuilder xpathEnvs = getXpathEnvs();
		NodeList nodeList = getNodeList(xpathEnvs.toString());
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node envNode = nodeList.item(i);
			envNode.getParentNode().removeChild(envNode);
		}
		
		createEnvironment(environments, configFile);
	}
	
	@Override
	public void updateEnvironment(Environment environment) throws ConfigurationException {
		String envName = environment.getName();
		Node oldNode = getNode(getXpathEnv(envName).toString());
		Element newNode = createEnvironmentNode(environment);
		rootElement.replaceChild(newNode, oldNode);
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	@Override
	public void deleteEnvironment(String envName) throws ConfigurationException {
		String xpath = getXpathEnv(envName).toString();
		Element envNode = (Element) getNode(xpath);
		envNode.getParentNode().removeChild(envNode);
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	private void createEnvironment(List<Environment> envs, File configFile) throws ConfigurationException {
		for (Environment env : envs) {
			Element envNode = createEnvironmentNode(env);
			rootElement.appendChild(envNode);
		}
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (Exception e) {
			throw new ConfigurationException(e);
		}
	}
	
	private Element createEnvironmentNode(Environment environment) throws ConfigurationException {
		Element envNode = document.createElement("environment");
		envNode.setAttribute("name", environment.getName());
		envNode.setAttribute("desc", environment.getDesc());
		List<String> appliesTo = environment.getAppliesTo();
		if (CollectionUtils.isNotEmpty(appliesTo)) {
			String appliesToAsStr = getAppliesToAsStr(appliesTo);
			envNode.setAttribute("appliesTo", appliesToAsStr);
		}
		envNode.setAttribute("default", Boolean.toString(environment.isDefaultEnv()));
		for (Configuration configuration : environment.getConfigurations()) {
			Element configNode = document.createElement(configuration.getType());
			configNode.setAttribute("name", configuration.getName());
			createProperties(configNode, configuration.getProperties());
			envNode.appendChild(configNode);
		}
		return envNode;
	}
	
	private String getAppliesToAsStr(List<String> appliesTo) {
		String appliesToStr = "";
		for (String applies : appliesTo) {
			appliesToStr += applies + ",";
		}
		return appliesToStr.substring(0, appliesToStr.length() - 1);
	}
	
	private void createProperties(Element configNode, Properties properties) {
		Set<Object> keySet = properties.keySet();
		for (Object key : keySet) {
			String value = (String) properties.get(key);
			Element propNode = document.createElement(key.toString());
			propNode.setTextContent(value);
			configNode.appendChild(propNode);
		}
	}
	
	private void createNewDoc() throws ConfigurationException {
		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(false);
            DocumentBuilder builder = domFactory.newDocumentBuilder();
            document = builder.newDocument();
            rootElement = document.createElement("environments");
            document.appendChild(rootElement);
		} catch (ParserConfigurationException e) {
			throw new ConfigurationException(e);
		}
	}
	
	private void createDocFromExist(InputStream configFileStream) throws ConfigurationException {
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(false);
		DocumentBuilder builder;
		try {
			builder = domFactory.newDocumentBuilder();
			document = builder.parse(configFileStream);
			rootElement = (Element) document.getElementsByTagName("environments").item(0);
		} catch (ParserConfigurationException e) {
			throw new ConfigurationException(e);
		} catch (SAXException e) {
			throw new ConfigurationException(e);
		} catch (IOException e) {
			throw new ConfigurationException(e);
		} finally {
			if(configFileStream != null) {
				Utility.closeStream(configFileStream);
			}
		}
	}
	
	public void writeXml(OutputStream fos) throws ConfigurationException {
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer;
		try {
			transformer = tFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			Source src = new DOMSource(document);
			Result res = new StreamResult(fos);
			transformer.transform(src, res);
		} catch (TransformerConfigurationException e) {
			throw new ConfigurationException(e);
		} catch (TransformerException e) {
			throw new ConfigurationException(e);
		} finally {
			if(fos != null) {
				Utility.closeStream(fos);
			}
		}
	}
	
	private StringBuilder getXpathEnvs() {
		StringBuilder expBuilder = new StringBuilder();
		expBuilder.append("/environments/environment"); 
		return expBuilder;
	}
	
	private StringBuilder getXpathEnv(String envName) {
		StringBuilder expBuilder = new StringBuilder();
		expBuilder.append("/environments/environment[@name='"); 
		expBuilder.append(envName);
		expBuilder.append("']");	
		return expBuilder;
	}
	
	private Node getNode(String xpath) throws ConfigurationException {
		XPathFactory xPathFactory = XPathFactory.newInstance();
	    XPath newXPath = xPathFactory.newXPath();	
		XPathExpression xPathExpression;
		Node xpathNode = null;
		try {
			xPathExpression = newXPath.compile(xpath);
			xpathNode = (Node) xPathExpression.evaluate(document, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			throw new ConfigurationException(e);
		}
		return xpathNode;
	}
	
	private NodeList getNodeList(String xpath) throws ConfigurationException {
		XPathFactory xPathFactory = XPathFactory.newInstance();
	    XPath newXPath = xPathFactory.newXPath();	
		XPathExpression xPathExpression;
		NodeList xpathNodes = null;
		try {
			xPathExpression = newXPath.compile(xpath);
			xpathNodes = (NodeList) xPathExpression.evaluate(document, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			throw new ConfigurationException(e);
		}
		return xpathNodes;
	}

	@Override
	public List<Environment> getEnvironments() throws ConfigurationException {
		ConfigReader configReader = new ConfigReader(configFile);
		return configReader.getAllEnvironments();
	}

	@Override
	public List<Configuration> getConfigurations(String envName, String type)
			throws ConfigurationException {
		ConfigReader configReader = null;
		try {
			configReader = new ConfigReader(configFile);
			if (envName == null || envName.isEmpty() ) {
				envName = configReader.getDefaultEnvName();
			}
		} catch (ConfigurationException e) {
			throw new ConfigurationException(e);
		}
		return configReader.getConfigurations(envName, type);
	}

	@Override
	public void createConfiguration(String envName, Configuration configuration)
			throws ConfigurationException {
		ConfigReader configReader = new ConfigReader(configFile);
		Element element = configReader.getEnviroments().get(envName);
		Element imported = (Element)document.importNode(element, Boolean.TRUE);
		imported.appendChild(createConfigElement(configuration));
		Node oldNode = getNode(getXpathEnv(envName).toString());
		rootElement.replaceChild(imported, oldNode);
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	public Element createConfigElement(Configuration configuration) throws ConfigurationException {
		Element configNode = document.createElement(configuration.getType());
		configNode.setAttribute("name", configuration.getName());
		configNode.setAttribute("desc", configuration.getDesc());
		createProperties(configNode, configuration.getProperties());
		return configNode;
	}

	@Override
	public void updateConfiguration(String envName, String oldConfigName,
			Configuration configuration) throws ConfigurationException {
		Node environment = getNode(getXpathEnv(envName).toString());
		Node oldConfigNode = getNode(getXpathConfigByEnv(envName, oldConfigName));
		Element configElement = createConfigElement(configuration);
		environment.replaceChild(configElement, oldConfigNode);
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	private String getXpathConfigByEnv(String envName, String configName) {
		StringBuilder expBuilder = getXpathEnv(envName);
		expBuilder.append("/*[@name='");
		expBuilder.append(configName);
		expBuilder.append("']");
		return expBuilder.toString();
	}
	
	@Override
	public void deleteConfigurations(List<Configuration> configurations)
			throws ConfigurationException {
		try {
			for (Configuration configuration : configurations) {
				Node environment = getNode(getXpathEnv(configuration.getEnvName()).toString());
				Node configNode = getNode(getXpathConfigByEnv(configuration.getEnvName(), configuration.getName()));
				if (environment != null) {
					environment.removeChild(configNode);
				}
			}
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}

	@Override
	public void deleteConfiguration(String envName, Configuration configuration)
			throws ConfigurationException {
		Node environment = getNode(getXpathEnv(envName).toString());
		Node configNode = getNode(getXpathConfigByEnv(envName, configuration.getName()));
		environment.removeChild(configNode);
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	@Override
	public Configuration getConfiguration(String envName, String type,
			String configName) throws ConfigurationException {
		ConfigReader configReader = null;
		Configuration configurationFound = null;
		try {
			configReader = new ConfigReader(configFile);
			if (envName == null || envName.isEmpty() ) {
				envName = configReader.getDefaultEnvName();
			}
			List<Configuration> configurations = configReader.getConfigurations(envName, type);
			for (Configuration configuration : configurations) {
				if(configuration.getName().equals(configName)) {
					configurationFound = configuration;
				}
			}
		} catch (ConfigurationException e) {
			throw new ConfigurationException(e);
		}
		return configurationFound;
	}

	@Override
	public void deleteConfigurations(String envName, List<String> configurations) throws ConfigurationException {
		deleteConfiguration(envName, configurations);
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	private void deleteConfiguration(String envName, List<String> configNames) throws ConfigurationException {
		Node environment = getNode(getXpathEnv(envName).toString());
		for (String configName : configNames) {
			Node configNode = getNode(getXpathConfigByEnv(envName, configName));
			environment.removeChild(configNode);
		}
	}

	@Override
	public void deleteConfigurations(Map<String, List<String>> configurations)
			throws ConfigurationException {
		Set<String> keySet = configurations.keySet();
		for (String envName : keySet) {
			List<String> configNames = configurations.get(envName);
			deleteConfiguration(envName, configNames);
		}
		try {
			writeXml(new FileOutputStream(configFile));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}
	
	
	@Override
	public List<CertificateInfo> getCertificate(String host, int port) throws PhrescoException {
		List<CertificateInfo> certificates = new ArrayList<CertificateInfo>();
		CertificateInfo info;
		try {
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			SSLContext context = SSLContext.getInstance("TLS");
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);
			X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
			SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
			context.init(null, new TrustManager[] { tm }, null);
			SSLSocketFactory factory = context.getSocketFactory();
			SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
			socket.setSoTimeout(10000);
			try {
				socket.startHandshake();
				socket.close();
			} catch (SSLException e) {
				
			}
			X509Certificate[] chain = tm.chain;
			for (int i = 0; i < chain.length; i++) {
				X509Certificate x509Certificate = chain[i];
				String subjectDN = x509Certificate.getSubjectDN().getName();
				String[] split = subjectDN.split(",");
				info = new CertificateInfo();
				info.setSubjectDN(subjectDN);
				info.setDisplayName(split[0]);
				info.setCertificate(x509Certificate);
				certificates.add(info);
			}
		} catch (Exception e) {
			throw new PhrescoException();
		}
		return certificates;
	}

	@Override
	public void addCertificate(CertificateInfo info, File file) throws PhrescoException {
		char[] passphrase = "changeit".toCharArray();
		InputStream inputKeyStore = null;
		OutputStream outputKeyStore = null;
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null);
			keyStore.setCertificateEntry(info.getDisplayName(), info.getCertificate());
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				file.createNewFile();
			}
			outputKeyStore = new FileOutputStream(file);
			keyStore.store(outputKeyStore, passphrase);
		} catch (Exception e) {
			throw new PhrescoException(e);
		} finally {
			Utility.closeStream(inputKeyStore);
			Utility.closeStream(outputKeyStore);
		}
	}
}


class SavingTrustManager implements X509TrustManager {

	private final X509TrustManager tm;
	X509Certificate[] chain;

	SavingTrustManager(X509TrustManager tm) {
		this.tm = tm;
	}

	public X509Certificate[] getAcceptedIssuers() {
		throw new UnsupportedOperationException();
	}

	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		this.chain = chain;
		tm.checkServerTrusted(chain, authType);
	}
}