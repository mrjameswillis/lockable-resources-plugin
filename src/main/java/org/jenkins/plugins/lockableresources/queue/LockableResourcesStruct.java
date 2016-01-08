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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

public class LockableResourcesStruct {

    static final Logger LOGGER = Logger.getLogger(LockableResourcesStruct.class.getName());

	public final Set<LockableResource> required;
	public final transient String requiredNames;
	public final String requiredVar;
	public final String resourceVarsPrefix;
	public final String requiredNumber;

	public LockableResourcesStruct( RequiredResourcesParameterValue param ) {
		this(param.value, null, "1", null, new EnvVars());
	}
	public LockableResourcesStruct(RequiredResourcesProperty.Resource resource, EnvVars env ) {
		this(resource.resourceNames, resource.resourceNamesVar, resource.resourceNumber, resource.resourceVarsPrefix, env);
	}
	private LockableResourcesStruct( String requiredNames, String requiredVar, String requiredNumber, String varsPrefix, EnvVars env ) {
		Set<LockableResource> required = new LinkedHashSet<>();
		requiredNames = Util.fixEmptyAndTrim(requiredNames);
		if ( requiredNames != null ) {
			if ( requiredNames.startsWith(Constants.GROOVY_LABEL_MARKER) ) {
                LOGGER.finest("Trying to find groovy resource with: " + requiredNames);
                required.addAll(LockableResourcesManager.get().getResourcesForExpression(requiredNames, env));
            } else if ( requiredNames.startsWith(Constants.EXACT_LABEL_MARKER) ) {
                LOGGER.finest("Trying to find exact label resource with: " + requiredNames);
                required.addAll(LockableResourcesManager.get().getResourcesWithLabels(requiredNames, env));
			} else {
				for ( String name : requiredNames.split("\\s+") ) {
                    if (name.startsWith("%") && name.endsWith("%")){
                        name = "${" + name.substring(1, name.length() - 1) + "}";
                    }
					name = env.expand(name);
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
		this.requiredNames = requiredNames;
		this.required = Collections.unmodifiableSet(required);

		this.requiredVar = Util.fixEmptyAndTrim(requiredVar);

		requiredNumber = Util.fixEmptyAndTrim(requiredNumber);
		if ( requiredNumber != null && requiredNumber.equals("0") ) requiredNumber = null;
		this.requiredNumber = requiredNumber;
		this.resourceVarsPrefix = varsPrefix;
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
