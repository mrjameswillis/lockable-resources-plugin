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

import java.util.*;
import java.util.stream.Collectors;

import static org.jenkins.plugins.lockableresources.Constants.*;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class RequiredResourcesProperty extends JobProperty<Job<?, ?>> {

	public final List<Resource> resources;

	@DataBoundConstructor
	public RequiredResourcesProperty(List<Resource> resources) {
		super();
		this.resources = resources == null ? new ArrayList<>() : resources;
	}

	public List<Resource> getResources() {
		return resources;
	}

	public static class Resource extends AbstractDescribableImpl<Resource> {
		public String uniqueID;
		public final String resourceNames;
		public final String resourceNumber;
		public final String resourceNamesVar;
		public final String resourceVarsPrefix;
		public boolean usePercentMatching;

        @DataBoundConstructor
        public Resource(String uniqueID, String resourceNames, String resourceNumber, String resourceNamesVar, String resourceVarsPrefix, boolean usePercentMatching) {
            this.uniqueID = Util.fixEmptyAndTrim(uniqueID);
            this.resourceNames = Util.fixEmptyAndTrim(resourceNames);
            this.resourceNumber = Util.fixEmptyAndTrim(resourceNumber);
            this.resourceNamesVar = Util.fixEmptyAndTrim(resourceNamesVar);
            this.resourceVarsPrefix = Util.fixEmptyAndTrim(resourceVarsPrefix);
            this.usePercentMatching = usePercentMatching;
        }

		public String getUniqueID() {
			return uniqueID;
		}

		@Override
		public boolean equals(Object other) {
			boolean result = false;
			if (other instanceof Resource) {
				Resource that = (Resource) other;
				result = (that.canEqual(this) && this.uniqueID.equals(that.uniqueID) && super.equals(that));
			}
			return result;
		}

		@Override
		public int hashCode() {
			return (41 * super.hashCode() + uniqueID.hashCode());
		}

		public boolean canEqual(Object other) {
			return (other instanceof Resource);
		}

		public static String generateUniqueID() {
			return UUID.randomUUID().toString();
		}

		@SuppressWarnings("unused")
		@Extension
		public static class DescriptorImpl extends Descriptor<Resource> {

            @Override
			public Resource newInstance(StaplerRequest req, JSONObject formData) throws FormException {
				Resource resource = super.newInstance(req, formData);
				if (resource.uniqueID == null)
					resource.uniqueID = Resource.generateUniqueID();
				return resource;
			}

            public boolean getUsePercentMatchingDefault() {
                return LockableResourcesManager.get().getUsePercentMatchingDefault();
            }

			public String getDisplayName() { return ""; }
		}
	}

	@SuppressWarnings("unused")
	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return "Z Required Lockable Resources";
		}

		public FormValidation doCheckResourceNames(@QueryParameter String value) {
			String names = Util.fixEmptyAndTrim(value);
			if (names == null) {
				return FormValidation.ok();
			} else {
				List<String> wrongNames = new ArrayList<>();
				for (String name : names.split(RESOURCES_SPLIT_REGEX)) {
					boolean found = false;
					for (LockableResource r : LockableResourcesManager.get().getResources()) {
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
							labelResources.addAll(LockableResourcesManager.get().getResourcesWithLabel(resource)
									.stream().map(LockableResource::getName).collect(Collectors.toList()));
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

		public AutoCompletionCandidates doAutoCompleteResourceNames(@QueryParameter String value) {
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

