/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013-2015, Aki Asikainen                              *
 *                          SAP SE                                     *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.queue;

import hudson.EnvVars;
import hudson.Util;
import org.jenkins.plugins.lockableresources.*;

import java.util.*;
import java.util.logging.Logger;

public class LockableResourcesStruct {

    static final Logger LOGGER = Logger.getLogger(LockableResourcesStruct.class.getName());

	public final Set<LockableResource> required;
	public final transient String requiredNames;
	public final String requiredVar;
	public final String resourceVarsPrefix;
	public final String requiredNumber;
    public final boolean usePercentMatching;
    public final EnvVars env;

	public LockableResourcesStruct(RequiredResourcesParameterValue param) {
		this(param.value, null, "1", null, false, new EnvVars());
	}
	public LockableResourcesStruct(RequiredResourcesProperty.Resource resource, EnvVars env) {
		this(resource.resourceNames, resource.resourceNamesVar, resource.resourceNumber, resource.resourceVarsPrefix, resource.usePercentMatching, env);
	}
	private LockableResourcesStruct(String requiredNames, String requiredVar, String requiredNumber, String varsPrefix, boolean usePercentMatching, EnvVars env) {
		Set<LockableResource> required = new LinkedHashSet<>();
		requiredNames = Util.fixEmptyAndTrim(requiredNames);
        this.env = env;
		if ( requiredNames != null ) {
			if ( requiredNames.startsWith(Constants.GROOVY_LABEL_MARKER) ) {
                LOGGER.finest("Trying to find groovy resource with: " + requiredNames);
                required.addAll(LockableResourcesManager.get().getResourcesForExpression(requiredNames, this.env));
            } else if ( requiredNames.startsWith(Constants.EXACT_LABEL_MARKER) ) {
                LOGGER.finest("Trying to find exact label resource with: " + requiredNames);
                required.addAll(LockableResourcesManager.get().getResourcesWithLabels(requiredNames, this.env));
			} else {
                Set<String> requiredNamesList = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList(requiredNames.split("\\s+"))), this.env);
				for ( String name : requiredNamesList ) {
					LockableResource r = LockableResourcesManager.get().fromName(name);
					if (r != null) {
                        LOGGER.finest("Found resource with name: " + name);
						required.add(r);
					} else {
                        LOGGER.finest("Found resource with label: " + name);
						required.addAll(LockableResourcesManager.get().getResourcesWithLabel(name));
					}
				}
			}
		}
        LOGGER.finest("Found required resources: " + required);
		this.requiredNames = requiredNames;
		this.required = Collections.unmodifiableSet(required);

		this.requiredVar = Utils.getExpandedVariables(Util.fixEmptyAndTrim(requiredVar), this.env, 1);

		requiredNumber = Utils.getExpandedVariables(Util.fixEmptyAndTrim(requiredNumber), this.env, 1);
		if (requiredNumber != null && requiredNumber.equals("0")) requiredNumber = null;
		this.requiredNumber = requiredNumber;
		this.resourceVarsPrefix = Utils.getExpandedVariables(varsPrefix, this.env, 1);
        this.usePercentMatching = usePercentMatching;
	}

	public int getRequiredNumber() {
		int resourceNumber;
		try {
			resourceNumber = Integer.parseInt(requiredNumber);
		} catch (NumberFormatException e) {
			resourceNumber = 0;
		}
		return resourceNumber;
	}
	public String toString() {
		return "Required resources: " + this.required +
			", Variable name: " + this.requiredVar +
			", Number of resources: " + this.requiredNumber;
	}
}
