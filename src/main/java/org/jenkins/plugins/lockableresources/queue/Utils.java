/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.queue;

import hudson.EnvVars;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Queue;

import org.jenkins.plugins.lockableresources.RequiredResourcesProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {

	public static AbstractProject<?, ?> getProject(Queue.Item item) {
		if (item.task instanceof AbstractProject)
			return (AbstractProject<?, ?>) item.task;
		return null;
	}

	public static AbstractProject<?, ?> getProject(AbstractBuild<?, ?> build) {
		Object p = build.getParent();
		if (p instanceof AbstractProject)
			return (AbstractProject<?, ?>) p;
		return null;
	}

	public static List<LockableResourcesStruct> requiredResources(
			AbstractProject<?, ?> project) {
		RequiredResourcesProperty property = null;
		EnvVars env = new EnvVars();

		if (project instanceof MatrixConfiguration) {
			env.putAll(((MatrixConfiguration) project).getCombination());
			project = (AbstractProject<?, ?>) project.getParent();
		}

		property = project.getProperty(RequiredResourcesProperty.class);
		if (property != null) {
			List<LockableResourcesStruct> res = new ArrayList<>();
			for (RequiredResourcesProperty.Resource r : property.resources) {
				res.add(new LockableResourcesStruct(r, property.resourceNamesVar, env));
			}
			return res;
		}
		return Collections.emptyList();
	}
}
