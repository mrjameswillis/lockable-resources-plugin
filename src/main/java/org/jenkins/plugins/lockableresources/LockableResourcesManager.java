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
import hudson.model.AbstractBuild;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkins.plugins.lockableresources.queue.LockableResourcesStruct;

import org.kohsuke.stapler.StaplerRequest;

@Extension
public class LockableResourcesManager extends GlobalConfiguration {

	private static final transient Random rand = new Random();

	private boolean useResourcesEvenly = false;
	private final LinkedHashSet<LockableResource> resources;

	public LockableResourcesManager() {
		resources = new LinkedHashSet<LockableResource>();
		load();
	}

	public Collection<LockableResource> getResources() {
		return resources;
	}

	public boolean getUseResourcesEvenly() {
		return useResourcesEvenly;
	}

	public List<LockableResource> getResourcesFromProject(String fullName) {
		List<LockableResource> matching = new ArrayList<LockableResource>();
		for (LockableResource r : resources) {
			String rName = r.getQueueItemProject();
			if (rName != null && rName.equals(fullName)) {
				matching.add(r);
			}
		}
		return matching;
	}

	public List<LockableResource> getResourcesFromBuild(AbstractBuild<?, ?> build) {
		List<LockableResource> matching = new ArrayList<LockableResource>();
		for (LockableResource r : resources) {
			AbstractBuild<?, ?> rBuild = r.getBuild();
			if (rBuild != null && rBuild == build) {
				matching.add(r);
			}
		}
		return matching;
	}

	public Boolean isValidLabel(String label)
	{
		return this.getAllLabels().contains(label);
	}

	public Set<String> getAllLabels()
	{
		Set<String> labels = new HashSet<String>();
		for (LockableResource r : this.resources) {
			String rl = r.getLabels();
			if (rl == null || "".equals(rl))
				continue;
			labels.addAll(Arrays.asList(rl.split("\\s+")));
		}
		return labels;
	}

	public int getFreeResourceAmount(String label)
	{
		int free = 0;
		for (LockableResource r : this.resources) {
			if (r.isLocked() || r.isQueued() || r.isReserved())
				continue;
			if (Arrays.asList(r.getLabels().split("\\s+")).contains(label))
				free += 1;
		}
		return free;
	}

	public List<LockableResource> getResourcesWithLabel(String label) {
		List<LockableResource> found = new ArrayList<LockableResource>();
		for (LockableResource r : this.resources) {
			if (r.isValidLabel(label))
				found.add(r);
		}
		return found;
	}

	public LockableResource fromName(String resourceName) {
		if (resourceName != null) {
			for (LockableResource r : resources) {
				if (resourceName.equals(r.getName())) {
					return r;
				}
			}
		}
		return null;
	}

	public synchronized boolean queue(List<LockableResource> resources, int queueItemId) {
		for (LockableResource r : resources) {
			if (r.isReserved() || r.isQueued(queueItemId) || r.isLocked()) {
				return false;
			}
		}
		for (LockableResource r : resources) {
			r.setQueued(queueItemId);
		}
		return true;
	}

	public synchronized List<LockableResource> queue(LockableResourcesStruct requiredResources,
	                                                 int queueItemId,
	                                                 String queueItemProject,
	                                                 int number,  // 0 means all
	                                                 Logger log) {
		List<LockableResource> selected = new ArrayList<LockableResource>();

		if (!checkCurrentResourcesStatus(selected, queueItemProject, queueItemId, log)) {
			// The project has another buildable item waiting -> bail out
			log.log(Level.FINEST, "{0} has another build waiting resources." +
			        " Waiting for it to proceed first.",
			        new Object[]{queueItemProject});
			return null;
		}

		List<LockableResource> candidates;
		if (requiredResources.label != null && requiredResources.label.isEmpty()) {
			candidates = requiredResources.required;
		} else {
			candidates = getResourcesWithLabel(requiredResources.label);
		}

		int required_amount = number == 0 ? candidates.size() : number;

		List<LockableResource> availableCandidates = new ArrayList<LockableResource>();
		for (LockableResource rs : candidates) {
			if (!rs.isReserved() && !rs.isLocked() && !rs.isQueued())
				availableCandidates.add(rs);
		}

		while ( selected.size() < required_amount && availableCandidates.size() > 0 ) {
			LockableResource r = selectResourceToUse(availableCandidates);
			availableCandidates.remove(r);
			selected.add(r);
		}

		// if did not get wanted amount or did not get all
		if (selected.size() != required_amount) {
			log.log(Level.FINEST, "{0} found {1} resource(s) to queue." +
			        "Waiting for correct amount: {2}.",
			        new Object[]{queueItemProject, selected.size(), required_amount});
			// just to be sure, clean up
			for (LockableResource x : resources) {
				if (x.getQueueItemProject() != null &&
				    x.getQueueItemProject().equals(queueItemProject))
					x.unqueue();
			}
			return null;
		}

		for (LockableResource rsc : selected) {
			rsc.setQueued(queueItemId, queueItemProject);
		}
		return selected;
	}

	private LockableResource selectResourceToUse( List<LockableResource> resources ) {
		if ( useResourcesEvenly ) {
			return resources.get(rand.nextInt(resources.size()));
		}
		//else
			return resources.get(0);
	}

	// Adds already selected (in previous queue round) resources to 'selected'
	// Return false if another item queued for this project -> bail out
	private boolean checkCurrentResourcesStatus(List<LockableResource> selected,
	                                            String project,
	                                            int taskId,
	                                            Logger log) {
		for (LockableResource r : resources) {
			// This project might already have something in queue
			String rProject = r.getQueueItemProject();
			if (rProject != null && rProject.equals(project)) {
				if (r.isQueuedByTask(taskId)) {
					// this item has queued the resource earlier
					selected.add(r);
				} else {
					// The project has another buildable item waiting -> bail out
					log.log(Level.FINEST, "{0} has another build " +
						"that already queued resource {1}. Continue queueing.",
						new Object[]{project, r});
					return false;
				}
			}
		}
		return true;
	}

	public synchronized boolean lock(List<LockableResource> resources,
			AbstractBuild<?, ?> build) {
		for (LockableResource r : resources) {
			if (r.isReserved() || r.isLocked()) {
				return false;
			}
		}
		for (LockableResource r : resources) {
			r.unqueue();
			r.setBuild(build);
		}
		return true;
	}

	public synchronized void unlock(List<LockableResource> resources,
			AbstractBuild<?, ?> build) {
		for (LockableResource r : resources) {
			if (build == null || build == r.getBuild()) {
				r.unqueue();
				r.setBuild(null);
			}
		}
	}

	public synchronized boolean reserve(List<LockableResource> resources,
			String userName) {
		for (LockableResource r : resources) {
			if (r.isReserved() || r.isLocked() || r.isQueued()) {
				return false;
			}
		}
		for (LockableResource r : resources) {
			r.setReservedBy(userName);
		}
		save();
		return true;
	}

	public synchronized void unreserve(List<LockableResource> resources) {
		for (LockableResource r : resources) {
			r.unReserve();
		}
		save();
	}

	@Override
	public String getDisplayName() {
		return "External Resources";
	}

	public synchronized void reset(List<LockableResource> resources) {
		for (LockableResource r : resources) {
			r.reset();
		}
		save();
	}

	@Override
	public boolean configure(StaplerRequest req, JSONObject json) {
		try {
			useResourcesEvenly = json.getBoolean("useResourcesEvenly");
			List<LockableResource> newResouces = req.bindJSONToList(
					LockableResource.class, json.get("resources"));
			for (LockableResource r : newResouces) {
				LockableResource old = fromName(r.getName());
				if (old != null) {
					r.setBuild(old.getBuild());
					r.setQueued(r.getQueueItemId(), r.getQueueItemProject());
				}
			}
			resources.clear();
			resources.addAll(newResouces);
			save();
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	public static LockableResourcesManager get() {
		return (LockableResourcesManager) Jenkins.getInstance()
				.getDescriptorOrDie(LockableResourcesManager.class);
	}

}
