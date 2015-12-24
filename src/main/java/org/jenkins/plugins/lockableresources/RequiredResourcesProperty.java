/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013-2015, 6WIND S.A.                                 *
 *                          SAP SE                                     *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources;

import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static org.jenkins.plugins.lockableresources.Constants.*;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class RequiredResourcesProperty extends JobProperty<Job<?, ?>> {

	public final List<Resource> resources;

	@DataBoundConstructor
	public RequiredResourcesProperty(List<Resource> resources) {
		super();
		this.resources = new ArrayList<>();
		if (resources != null)
			this.resources.addAll(resources);
	}

	public static class Resource extends AbstractDescribableImpl<Resource> {
		public final String resourceNames;
		public final String resourceNumber;
		public final String resourceNamesVar;
		public final String resourceVarsPrefix;

		@DataBoundConstructor
		public Resource(String resourceNames, String resourceNumber, String resourceNamesVar, String resourceVarsPrefix) {
			this.resourceNames = Util.fixEmptyAndTrim(resourceNames);
			this.resourceNumber = Util.fixEmptyAndTrim(resourceNumber);
			this.resourceNamesVar = Util.fixEmptyAndTrim(resourceNamesVar);
			this.resourceVarsPrefix = Util.fixEmptyAndTrim(resourceVarsPrefix);
		}

		@Extension
		public static class DescriptorImpl extends Descriptor<Resource> {
			public String getDisplayName() { return ""; }
		}
	}

	@SuppressWarnings("unused")
	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return "Required Lockable Resources";
		}

		public FormValidation doCheckResourceNames(@QueryParameter String value) {
			String names = Util.fixEmptyAndTrim(value);
			if (names == null) {
				return FormValidation.ok();
			} else {
				List<String> wrongNames = new ArrayList<>();
				for (String name : names.split(RESOURCES_SPLIT_REGEX)) {
					boolean found = false;
					for (LockableResource r : LockableResourcesManager.get()
							.getResources()) {
						if (r.getName().equals(name)) {
							found = true;
							break;
						}
					}
					if (!found)
						wrongNames.add(name);
				}
				// now filter out valid labels
				Iterator<String> it = wrongNames.iterator();
				while ( it.hasNext() ) {
					String label = it.next();
					if (LockableResourcesManager.get().isValidLabel(label)) {
						it.remove();
					}
				}
				if (wrongNames.isEmpty()) {
					return FormValidation.ok();
				} else {
					return FormValidation
							.error("The following resources do not exist: "
									+ wrongNames);
				}
			}
		}

		public FormValidation doCheckResourceNumber(@QueryParameter String value,
				@QueryParameter String resourceNames) {

			String number = Util.fixEmptyAndTrim(value);
			String names = Util.fixEmptyAndTrim(resourceNames);

			if (number == null || number.equals("") || number.trim().equals("0")) {
				return FormValidation.ok();
			}

			int numAsInt;
			try {
				numAsInt = Integer.parseInt(number);
			} catch(NumberFormatException e)  {
				return FormValidation.error(
					"Could not parse the given value as integer.");
			}


			int numResources = 0;
			if (names != null) {
				if ( names.startsWith(Constants.GROOVY_LABEL_MARKER) ) {
					numResources = Integer.MAX_VALUE;
				}
				else {
					HashSet<String> resources = new HashSet<>();
					resources.addAll(Arrays.asList(names.split(RESOURCES_SPLIT_REGEX)));
					Iterator<String> it = resources.iterator();
					HashSet<String> labelResources = new HashSet<>();
					while ( it.hasNext() ) {
						String resource = it.next();
						if ( LockableResourcesManager.get().fromName(resource) == null ) {
							it.remove();
							for ( LockableResource r : LockableResourcesManager.get().getResourcesWithLabel(resource) ) {
								labelResources.add(r.getName());
							}
						}
					}
					resources.addAll(labelResources);
					numResources = resources.size();
				}
			}

			if (numResources < numAsInt) {
				return FormValidation.error(String.format(
					"Given amount %d is greater than amount of resources: %d.",
					numAsInt,
					numResources));
			}
			return FormValidation.ok();
		}

		public AutoCompletionCandidates doAutoCompleteResourceNames(
				@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();

			value = Util.fixEmptyAndTrim(value);

			if (value != null) {
				for (LockableResource r : LockableResourcesManager.get()
						.getResources()) {
					if (r.getName().startsWith(value))
						c.add(r.getName());
				}
				for (String l : LockableResourcesManager.get().getAllLabels()) {
					if ( l.startsWith(value) ) c.add(l);
				}
			}

			return c;
		}
	}
}

