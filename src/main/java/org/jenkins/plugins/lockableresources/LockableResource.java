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

import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractBuild;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.model.User;
import hudson.tasks.Mailer.UserProperty;
import hudson.util.FormValidation;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import static org.jenkins.plugins.lockableresources.Constants.*;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 999)
public class LockableResource
		extends AbstractDescribableImpl<LockableResource>
		implements Comparable<LockableResource> {

	private static final Logger LOGGER = Logger.getLogger(LockableResource.class.getName());
	public static final int NOT_QUEUED = 0;
	private static final int QUEUE_TIMEOUT = 60 * 1000;

	public String uniqueID;
	private final String name;
	private final String description;
	@XStreamConverter(value=LabelConverter.class)
	private final LinkedHashSet<String> labels = new LinkedHashSet<>();
	private String reservedBy;
	private List<LockableResourceProperty> properties;

	private transient long queueItemId = NOT_QUEUED;
	private transient String queueItemProject = null;
	private transient AbstractBuild<?, ?> build = null;
	private transient long queuingStarted = 0;

	@DataBoundConstructor
	public LockableResource(String uniqueID, String name, String description, String labels, String reservedBy, List<LockableResourceProperty> properties) {
        this.uniqueID = Util.fixEmptyAndTrim(uniqueID);
		this.name = Util.fixEmptyAndTrim(name);
		if ( this.name == null ) throw new IllegalArgumentException("Resource must have a name!");
		if ( this.name.contains(" ") ) throw new IllegalArgumentException("Resource names cannot contain spaces!");
		this.description = Util.fixEmptyAndTrim(description);
		this.labels.addAll(labelsFromString(Util.fixNull(labels).trim()));
		this.reservedBy = Util.fixEmptyAndTrim(reservedBy);
        this.properties = properties == null ? new ArrayList<>() : properties;
	}

	@Exported
	public String getName() {
		return name;
	}

	@Exported
	public String getDescription() {
		return description;
	}

	@Exported
	public String getLabels() {
		if ( labels != null && labels.size() > 0 ) {
			StringBuilder sb = new StringBuilder();
			for ( String l : labels ) {
				sb.append(" ").append(l);
			}
			return sb.substring(1);
		}
		else {
			return null;
		}
	}

    public static String generateUniqueID() {
        return UUID.randomUUID().toString();
    }
	
	/**
	 * FOR INTERNAL USE ONLY!
	 * 
	 * Returns this objects internal label set.  Changes will be reflected in the containing object.
	 * The caller MUST update the label caches for any changes made.
	 * 
	 * @return the internal set which stores the labels applied to this resource
	 */
	protected Set<String> getModifyableLabelSet() {
		return labels;
	}
	
	public Set<String> getLabelSet() {
		if ( labels == null ) return Collections.emptySet();
		return Collections.unmodifiableSet(labels);
	}

	public boolean isValidLabel(String candidate) {
		if (candidate.startsWith(Constants.GROOVY_LABEL_MARKER) || candidate.startsWith(Constants.EXACT_LABEL_MARKER))
			throw new UnsupportedOperationException("Groovy or Label expressions not supported by this method.");
		return labels.contains(candidate);
	}

    public boolean isValidLabelSet(Collection<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.startsWith(Constants.GROOVY_LABEL_MARKER) || candidate.startsWith(Constants.EXACT_LABEL_MARKER))
                throw new UnsupportedOperationException("Groovy or Label expressions not supported by this method.");
            else if (!labels.contains(candidate))
                return false;
        }
        return true;
    }

	public boolean expressionMatches(String expression, Map<String,String> params) {
		Binding binding = new Binding(params);
		binding.setVariable("resourceName", name);
		binding.setVariable("resourceDescription", ((description == null) ? "" : description));
        // Seeing below when running with groovy script, using string for now!
        // java.lang.ClassCastException: java.util.LinkedList cannot be cast to java.lang.String
        // at hudson.EnvVars.put(EnvVars.java:74)
        // at groovy.lang.Binding.setVariable(Binding.java:77)
        //binding.setVariable("resourceLabels", ((labels == null) ? Collections.EMPTY_LIST : new LinkedList<>(labels)));
        String tmpLabels = getLabels();
		binding.setVariable("resourceLabels", ((tmpLabels == null) ? "" : tmpLabels));
		String expressionToEvaluate = expression.replace(Constants.GROOVY_LABEL_MARKER, "");
        Pattern p = Pattern.compile("%(.*?)%");
        Matcher m = p.matcher(expression);
        while (m.find()) {
            expressionToEvaluate = expressionToEvaluate.replace("%" + m.group(1) + "%", "${" + m.group(1) + "}");
        }
		GroovyShell shell = new GroovyShell(binding);
		try {
			Object result = shell.evaluate(expressionToEvaluate);
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("Checked resource " + name + " for " + expression
						+ " with " + binding + " -> " + result);
			}
			return (Boolean) result;
		} catch (Exception e) {
			LOGGER.log(
					Level.SEVERE,
					"Cannot get boolean result out of groovy expression '"
							+ expressionToEvaluate + "' on (" + binding + ")",
					e);
			return false;
		}
	}

	@Exported
	public String getReservedBy() {
		return reservedBy;
	}

	@Exported
	public boolean isReserved() {
		return reservedBy != null;
	}

	@Exported
	public String getReservedByEmail() {
		if (reservedBy != null) {
			UserProperty email = null;
			User user = Jenkins.getInstance().getUser(reservedBy);
			if (user != null)
				email = user.getProperty(UserProperty.class);
			if (email != null)
				return email.getAddress();
		}
		return null;
	}

    @Exported
    public List<LockableResourceProperty> getProperties() {
        return properties;
    }

    @Exported
    public Map<String, String> getPropertiesAsMap() {
        Map<String, String> tmp = new HashMap<>();
        for (LockableResourceProperty lrp : properties) {
            tmp.put(lrp.getName(), lrp.getValue());
        }
        return tmp;
    }

    @Exported
    public String getPropertyValue(String key) {
        return getPropertiesAsMap().get(key);
    }

	public boolean isQueued() {
		return getQueueItemId() != NOT_QUEUED;
	}

	// returns True if queued by any other task than the given one
	public boolean isQueued(int taskId) {
		return isQueued() && queueItemId != taskId;
	}

	public boolean isQueuedByTask(long taskId) {
		return getQueueItemId() == taskId;
	}

	public void unqueue() {
		queueItemId = NOT_QUEUED;
		queueItemProject = null;
		queuingStarted = 0;
	}

	@Exported
	public boolean isLocked() {
		return build != null;
	}

	public boolean isFree() {
		return !isLocked() && !isQueued() && !isReserved();
	}

	public AbstractBuild<?, ?> getBuild() {
		return build;
	}

	@Exported
	public String getBuildName() {
		if (build != null)
			return build.getFullDisplayName();
		else
			return null;
	}

	public void setBuild(AbstractBuild<?, ?> lockedBy) {
		this.build = lockedBy;
	}

	public Task getTask() {
		Item item = Queue.getInstance().getItem(getQueueItemId());
		if (item != null) {
			return item.task;
		} else {
			return null;
		}
	}

	public long getQueueItemId() {
		this.validateQueuingTimeout();
		return queueItemId;
	}

	public String getQueueItemProject() {
		this.validateQueuingTimeout();
		return this.queueItemProject;
	}

	public void setQueued(long queueItemId, String queueProjectName) {
		this.queueItemId = queueItemId;
		this.queuingStarted = System.currentTimeMillis();
		this.queueItemProject = queueProjectName;
	}

	private void validateQueuingTimeout() {
		if (queuingStarted > 0) {
			long now = System.currentTimeMillis();
			if (now - queuingStarted > QUEUE_TIMEOUT)
				unqueue();
		}
	}

	public void setReservedBy(String userName) {
		this.reservedBy = userName;
	}

	public void unReserve() {
		this.reservedBy = null;
	}

	public void reset() {
		this.unReserve();
		this.unqueue();
		this.setBuild(null);
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		return prime * name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		LockableResource other = (LockableResource) obj;
		if ( name.equals(other.name) ) return true;
		return super.equals(obj);
	}

	public int compareTo(LockableResource o) {
		return name.compareTo(o.name);
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<LockableResource> {

        @Override
        public LockableResource newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            LockableResource resource = super.newInstance(req, formData);
            if (resource.uniqueID == null)
                resource.uniqueID = LockableResource.generateUniqueID();
            return resource;
        }

		@Override
		public String getDisplayName() {
			return "Resource";
		}

		public FormValidation doCheckName(@QueryParameter String value) {
			value = Util.fixEmptyAndTrim(value);
			if (value == null) {
				return FormValidation.error("Resource must have a name!");
			}
			else if ( value.contains(" ") ) {
				return FormValidation.error("Resource names cannot contain spaces!");
			}
			return FormValidation.ok();
		}

		public AutoCompletionCandidates doAutoCompleteLabels(@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();
			value = Util.fixEmptyAndTrim(value);
			if (value != null) {
				for (String l : LockableResourcesManager.get().getAllLabels()) {
					if ( l.startsWith(value) ) c.add(l);
				}
			}
			return c;
		}
	}

	public static class LabelConverter extends CollectionConverter {
		public LabelConverter(Mapper mapper) {
			super(mapper);
		}
		
		@Override
		public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
			LinkedHashSet<String> labels;
			String labelString = reader.getValue();
			if ( labelString != null && !reader.hasMoreChildren() ) {
				labels = new LinkedHashSet<String>();
				labels.addAll(labelsFromString(labelString.trim()));
			}
			else {
				labels = (LinkedHashSet<String>)super.unmarshal(reader, context);
			}
			return labels;
		}
	}
	
	private static List<String> labelsFromString( String labelString ) {
		if ( labelString.length() <= 0 ) return Collections.emptyList();
		return Arrays.asList(labelString.split(RESOURCES_SPLIT_REGEX));
	}
}
