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

import java.util.*;
import java.util.logging.Logger;

public class Utils {

    static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

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
			AbstractProject<?, ?> project, EnvVars env) {
		if (project instanceof MatrixConfiguration) {
			env.putAll(((MatrixConfiguration) project).getCombination());
			project = (AbstractProject<?, ?>) project.getParent();
		}

		RequiredResourcesProperty property = project.getProperty(RequiredResourcesProperty.class);
		if (property != null) {
			List<LockableResourcesStruct> res = new ArrayList<>();
			for (RequiredResourcesProperty.Resource r : property.resources) {
				res.add(new LockableResourcesStruct(r, env));
			}
			return res;
		}
		return Collections.emptyList();
	}

	public static String getExpandedVariables(String originalString, EnvVars env) {
		return getExpandedVariables(originalString, env, 0);
	}

	public static String getExpandedVariables(String originalString, EnvVars env, int expectedSize) {
		if (originalString == null) return null;
		Set<String> tmpSet = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList(originalString.split("\\s+"))), env);
		if (expectedSize != 0 && tmpSet.size() != expectedSize) {
			return null;
		}
		StringBuilder returnString = new StringBuilder();
		for (String tmpString : tmpSet) {
			returnString.append(tmpString).append(" ");
		}
		return returnString.toString().trim();
	}

	public static Set<String> getExpandedListOfVariables(Set<String> requiredNamesList, EnvVars env) {
		Set<String> newRequiredNamesList = new LinkedHashSet<>();
		for (String initialNames : requiredNamesList) {
            for (String name : initialNames.split("\\s+")) {
                String tmpKey = expandVariable(name, env);
                LOGGER.finest("Adding name[" + name + "]=expand[" + tmpKey + "]");
                newRequiredNamesList.add(tmpKey);
            }
		}
        // lets check for another recursion example, if the list is the same as it started
        if (newRequiredNamesList.containsAll(requiredNamesList)) {
            LOGGER.warning("Variables are the same after expanding, catch the infinite loop of doom!");
            return newRequiredNamesList;
        }
		boolean isFullyExpanded = true;
        for (String requiredName : newRequiredNamesList) {
            if (requiredName.contains(" ")) {
                // we need to split on the new space, break out and go again
                isFullyExpanded = false;
                break;
            } else if ((requiredName.startsWith("${") && requiredName.endsWith("}")) || (requiredName.startsWith("%") && requiredName.endsWith("%"))) {
                // lets check against some bad recursion waiting to happen
                String tmpKey = expandVariable(requiredName, env);
                LOGGER.finest("Checking name[" + requiredName + "]=expand[" + tmpKey + "]");
                if (tmpKey.equals(requiredName)) {
                    LOGGER.warning("Variable equals itself, catch the infinite loop of doom!");
                    continue;
                }
                // we need to check if the variable exists or else this will never expand
                int startIndex = 1;
                if (requiredName.startsWith("${")) startIndex = 2;
                LOGGER.finest("Checking required name[" + requiredName + "]");
                if (env.containsKey(requiredName.substring(startIndex, requiredName.length() - 1))) {
                    isFullyExpanded = false;
                    break;
                }
            }
        }
		if (!isFullyExpanded) return getExpandedListOfVariables(newRequiredNamesList, env);
		return newRequiredNamesList;
	}

    public static String expandVariable(String name, EnvVars env) {
        if (name.startsWith("%") && name.endsWith("%")){
            name = "${" + name.substring(1, name.length() - 1) + "}";
        }
        return env.expand(name);
    }
}
