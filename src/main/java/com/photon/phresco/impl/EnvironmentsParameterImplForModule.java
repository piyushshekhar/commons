package com.photon.phresco.impl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.photon.phresco.api.ConfigManager;
import com.photon.phresco.api.DynamicParameter;
import com.photon.phresco.api.DynamicParameterForModule;
import com.photon.phresco.commons.model.ApplicationInfo;
import com.photon.phresco.configuration.Environment;
import com.photon.phresco.exception.ConfigurationException;
//import com.photon.phresco.plugins.model.Mojos.Mojo.Configuration.Parameters.Parameter.PossibleValues;
//import com.photon.phresco.plugins.model.Mojos.Mojo.Configuration.Parameters.Parameter.PossibleValues.Value;
import com.photon.phresco.plugins.model.Module.Configurations.Configuration.Parameter.PossibleValues;
import com.photon.phresco.plugins.model.Module.Configurations.Configuration.Parameter.PossibleValues.Value;
import com.photon.phresco.util.Constants;
import com.photon.phresco.util.Utility;

public class EnvironmentsParameterImplForModule implements DynamicParameterForModule, Constants {

	private static final long serialVersionUID = 1L;

    @Override
	public PossibleValues getValues(Map<String, Object> paramsMap) throws IOException, ParserConfigurationException, SAXException, ConfigurationException {
    	PossibleValues possibleValues = new PossibleValues();
    	ApplicationInfo applicationInfo = (ApplicationInfo) paramsMap.get(KEY_APP_INFO);
    	String customer = (String) paramsMap.get(KEY_CUSTOMER_ID);
    	if (paramsMap != null) {
    	    String showSettings = (String) paramsMap.get(KEY_SHOW_SETTINGS);
        	if (Boolean.parseBoolean(showSettings)) {
            	String settingsPath = getSettingsPath(customer);
            	ConfigManager configManager = new ConfigManagerImpl(new File(settingsPath)); 
            	List<Environment> environments = configManager.getEnvironments();
            	for (Environment environment : environments) {
            		Value value = new Value();
            		value.setValue(environment.getName());
            		possibleValues.getValue().add(value);
        		}
        	}
    	}
    	
    	String projectDirectory = applicationInfo.getAppDirName();
    	String configPath = getConfigurationPath(projectDirectory).toString();
    	ConfigManager configManager = new ConfigManagerImpl(new File(configPath)); 
    	List<Environment> environments = configManager.getEnvironments();
    	for (Environment environment : environments) {
    		Value value = new Value();
    		value.setValue(environment.getName());
    		possibleValues.getValue().add(value);
		}
    	
    	return possibleValues;
    }
    
    private StringBuilder getConfigurationPath(String projectDirectory) {
		 StringBuilder builder = new StringBuilder(Utility.getProjectHome());
		 builder.append(projectDirectory);
		 builder.append(File.separator);
		 builder.append(DOT_PHRESCO_FOLDER);
		 builder.append(File.separator);
		 builder.append(CONFIGURATION_INFO_FILE);
		 
		 return builder;
	 }
    
    private String getSettingsPath(String customer) {
    	return Utility.getProjectHome() + customer +"-settings.xml";
    }
}
