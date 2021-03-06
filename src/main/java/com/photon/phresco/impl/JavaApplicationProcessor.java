package com.photon.phresco.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.photon.phresco.commons.model.ApplicationInfo;
import com.photon.phresco.commons.model.ArtifactGroup;
import com.photon.phresco.exception.PhrescoException;
import com.photon.phresco.util.Constants;
import com.photon.phresco.util.ProjectUtils;
import com.photon.phresco.util.Utility;

public class JavaApplicationProcessor extends AbstractApplicationProcessor {

	@Override
	public void postUpdate(ApplicationInfo appInfo,
			List<ArtifactGroup> artifactGroup, List<ArtifactGroup> deletedFeatures) throws PhrescoException {
		File pomFile = new File(Utility.getProjectHome() + appInfo.getAppDirName() + File.separator + Constants.POM_NAME);
		ProjectUtils projectUtils = new ProjectUtils();
		if(CollectionUtils.isNotEmpty(artifactGroup)) {
			projectUtils.updatePOMWithPluginArtifact(pomFile, artifactGroup);
		}
		if(CollectionUtils.isNotEmpty(deletedFeatures)) {
			projectUtils.deleteFeatureDependencies(appInfo, deletedFeatures);
		}
		projectUtils.deletePluginFromPom(pomFile);
		projectUtils.addServerPlugin(appInfo, pomFile);
		BufferedReader breader = projectUtils.ExtractFeature(appInfo);
		try {
			String line = "";
			while ((line = breader.readLine()) != null) {
				if (line.startsWith("[ERROR]")) {
					System.err.println(line);
				}
			}
		} catch (IOException e) {
			throw new PhrescoException(e);
		}
	}
}
