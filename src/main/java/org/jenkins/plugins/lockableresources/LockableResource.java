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
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import org.kohsuke.stapler.DataBoundConstructor;

public class LockableResource
		extends AbstractDescribableImpl<LockableResource>
		implements Comparable<LockableResource> {

	public static final int NOT_QUEUED = 0;
	private static final int QUEUE_TIMEOUT = 60;

	private final String name;
	private final String description;
	@XStreamConverter(value=LabelConverter.class)
	private final LinkedHashSet<String> labels = new LinkedHashSet<String>();
	private String reservedBy;
	private String properties;

	private transient int queueItemId = NOT_QUEUED;
	private transient String queueItemProject = null;
	private transient AbstractBuild<?, ?> build = null;
	private transient long queuingStarted = 0;

	@DataBoundConstructor
	public LockableResource(String name, String description, String labels, String reservedBy, String properties) {
		this.name = Util.fixEmptyAndTrim(name);
		if ( this.name == null ) throw new IllegalArgumentException("name cannot be null!");
		this.description = Util.fixEmptyAndTrim(description);
		this.labels.addAll(labelsFromString(Util.fixNull(labels).trim()));
		this.reservedBy = Util.fixEmptyAndTrim(reservedBy);
		this.properties = properties;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

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
	
	public Set<String> getLabelSet() {
		if ( labels == null ) return Collections.emptySet();
		return Collections.unmodifiableSet(labels);
	}

	public Boolean isValidLabel(String candidate) {
		return labels.contains(candidate);
	}

	public String getReservedBy() {
		return reservedBy;
	}

	public boolean isReserved() {
		return reservedBy != null;
	}
	
	public String getProperties() {
		return properties;
	}

	public boolean isQueued() {
		this.validateQueuingTimeout();
		return queueItemId != NOT_QUEUED;
	}

	// returns True if queued by any other task than the given one
	public boolean isQueued(int taskId) {
		this.validateQueuingTimeout();
		return queueItemId != NOT_QUEUED && queueItemId != taskId;
	}

	public boolean isQueuedByTask(int taskId) {
		this.validateQueuingTimeout();
		return queueItemId == taskId;
	}

	public void unqueue() {
		queueItemId = NOT_QUEUED;
		queueItemProject = null;
		queuingStarted = 0;
	}

	public boolean isLocked() {
		return build != null;
	}

	public boolean isFree() {
		return !isLocked() && !isQueued() && !isReserved();
	}

	public AbstractBuild<?, ?> getBuild() {
		return build;
	}

	public void setBuild(AbstractBuild<?, ?> lockedBy) {
		this.build = lockedBy;
	}

	public Task getTask() {
		Item item = Queue.getInstance().getItem(queueItemId);
		if (item != null) {
			return item.task;
		} else {
			return null;
		}
	}

	public int getQueueItemId() {
		this.validateQueuingTimeout();
		return queueItemId;
	}

	public String getQueueItemProject() {
		this.validateQueuingTimeout();
		return this.queueItemProject;
	}

	public void setQueued(int queueItemId) {
		this.queueItemId = queueItemId;
		this.queuingStarted = System.currentTimeMillis() / 1000;
	}

	public void setQueued(int queueItemId, String queueProjectName) {
		this.setQueued(queueItemId);
		this.queueItemProject = queueProjectName;
	}

	private void validateQueuingTimeout() {
		if (queuingStarted > 0) {
			long now = System.currentTimeMillis() / 1000;
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
		public String getDisplayName() {
			return "Resource";
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
		return Arrays.asList(labelString.split("\\s+"));
	}
}
