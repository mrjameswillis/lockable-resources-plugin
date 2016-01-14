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

import hudson.EnvVars;
import hudson.Plugin;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.Queue;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkins.plugins.lockableresources.actions.LockedResourcesBuildAction;
import org.jenkins.plugins.lockableresources.queue.LockableResourcesStruct;
import org.jenkins.plugins.lockableresources.queue.Utils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.jenkins.plugins.lockableresources.Constants.RESOURCES_SPLIT_REGEX;

public class LockableResourcesManager extends Plugin {
	
	private static final transient Random rand = new Random();
	private static final Logger LOGGER = Logger.getLogger(LockableResourcesManager.class.getName());

	private final LinkedHashSet<String> loadBalancingLabels;
	private boolean useResourcesEvenly = false;
    private boolean usePercentMatchingDefault = Constants.DEFAULT_USE_PERCENT_MATCHING;
	private final LinkedHashSet<LockableResource> resources;
	private final LinkedHashMap<String,String> labelAliases;

	private final transient Map<String,Set<LockableResource>> labelsCache = new TreeMap<>();
	private final transient Map<String,Set<LockableResource>> lbLabelsCache = new HashMap<>();
	private final transient Map<String,LockableResource> resourceMapCache = new HashMap<>();

	public LockableResourcesManager() {
		super();
		resources = new LinkedHashSet<>();
		loadBalancingLabels = new LinkedHashSet<>();
		labelAliases = new LinkedHashMap<>();
		try {
			load();
		}
		catch ( IOException ex ) {
			LOGGER.log(Level.SEVERE, "Unable to load plugin configuration!", ex);
			throw new RuntimeException("Unable to load plugin configuration!", ex);
		}
	}

	public Collection<LockableResource> getResources() {
		return resources;
	}

	public String getLoadBalancingLabels() {
		if ( loadBalancingLabels.size() > 0 ) {
			StringBuilder sb = new StringBuilder();
			for ( String l : loadBalancingLabels ) {
				sb.append(" ").append(l);
			}
			return sb.substring(1);
		} else {
			return null;
		}
	}

	public boolean getUseResourcesEvenly() {
		return useResourcesEvenly;
	}
    public boolean getUsePercentMatchingDefault() {
        return usePercentMatchingDefault;
    }

	public List<LockableResource> getResourcesFromProject(String fullName) {
		List<LockableResource> matching = new ArrayList<>();
		for (LockableResource r : resources) {
			String rName = r.getQueueItemProject();
			if (rName != null && rName.equals(fullName)) {
				matching.add(r);
			}
		}
		return matching;
	}

	public List<LockableResource> getResourcesFromBuild(AbstractBuild<?, ?> build) {
		List<LockableResource> matching = new ArrayList<>();
		for (LockableResource r : resources) {
			AbstractBuild<?, ?> rBuild = r.getBuild();
			if (rBuild != null && rBuild == build) {
				matching.add(r);
			}
		}
		return matching;
	}

	public boolean isValidLabel(String label) {
		return label != null && (label.startsWith(Constants.GROOVY_LABEL_MARKER) || this.labelsCache.containsKey(label));
	}

	public Set<String> getAllLabels()
	{
		return Collections.unmodifiableSet(labelsCache.keySet());
	}

	public Map<String,String> getLabelAliases()
	{
		return Collections.unmodifiableMap(labelAliases);
	}

	public String dereferenceLabelAlias( String labelAlias ) {
		if ( labelsCache.containsKey(labelAlias) && labelAliases.containsKey(labelAlias) ) {
			return labelAliases.get(labelAlias);
		}
		return null;
	}

	public int getFreeResourceAmount(String label)
	{
		int free = 0;
		for ( LockableResource r : labelsCache.get(Util.fixEmpty(label)) ) {
			if ( r.isFree() ) free++;
		}
		return free;
	}

	public List<LockableResource> getResourcesWithLabels(String expression, EnvVars env) {
		String expressionToEvaluate = expression.replace(Constants.EXACT_LABEL_MARKER, "");
		Set<String> requiredNamesList = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList(expressionToEvaluate.split("\\s+"))), env);
		List<String> labels = new LinkedList<>();
		for ( String label : requiredNamesList ) {
			if ( labelsCache.containsKey(label) && labelAliases.containsKey(label) ) {
				LOGGER.log(Level.FINER, "Converting label alias {0} to real label.", label);
				label = labelAliases.get(label);
			}
			labels.add(label);
		}
        LOGGER.log(Level.FINER, "Exact label matching looking for labels {0}", labels);
		return this.resources.stream().filter(r -> r.isValidLabelSet(labels)).collect(Collectors.toList());
	}

	public List<LockableResource> getResourcesWithLabel(String label) {
		if ( labelsCache.containsKey(label) && labelAliases.containsKey(label) ) {
			LOGGER.log(Level.FINER, "Converting label alias {0} to real label.", label);
			label = labelAliases.get(label);
		}
		List<LockableResource> found = new ArrayList<>();
		for (LockableResource r : this.resources) {
			if (r.isValidLabel(label)) found.add(r);
		}
		return found;
	}

	/**
	 * Evaluates a groovy expression to find matching resources.
	 */
	public List<LockableResource> getResourcesForExpression(String expr, Map<String,String> params) {
		return this.resources.stream().filter(r -> r.expressionMatches(expr, params)).collect(Collectors.toList());
	}

	public LockableResource fromName(String resourceName) {
		return resourceMapCache.get(resourceName);
	}

	public synchronized Collection<LockableResource> queue(ArrayList<LockableResourcesStruct> requiredResourcesList,
	                                                       Queue.Item queueItem,
	                                                       String queueItemProject /*,
	                                                       int numRequired */) { // 0 means all
		// ensure there is a resources build action available to store state on
		LockedResourcesBuildAction action = queueItem.getAction(LockedResourcesBuildAction.class);
		if (action == null) {
			action = new LockedResourcesBuildAction();
			queueItem.addAction(action);
		}

		// the almighty overall selected, all resources sent back will be in here
		final ConcurrentMap<LockableResource, LockableResourcesStruct> overallSelected = new ConcurrentHashMap<>();
		int overallTotalNumRequired = 0;

		// check for any already queued resources that we can remove
		Set<LockableResource> selected = new HashSet<>();
		checkCurrentResourcesStatus(selected, action.matchedResources, queueItem.getId());
		LOGGER.log(Level.FINEST, "Initial selected removed from queue: {0}", selected);
		selected.forEach(LockableResource::unqueue);

		// now loop through the required resources and create our map
		for (LockableResourcesStruct requiredResources : requiredResourcesList) {
			List<LockableResource> tmpSelectedQueue = Collections.synchronizedList(new ArrayList<>());
			List<LockableResource> candidates = Collections.synchronizedList(new ArrayList<>(requiredResources.required));
			LOGGER.log(Level.FINEST, "Initial candidates: {0}", candidates);

			int numRequired = requiredResources.getRequiredNumber() <= 0 ? candidates.size() : requiredResources.getRequiredNumber();
			overallTotalNumRequired += numRequired;

			if (numRequired <= 0) {
				LOGGER.log(Level.FINE, "Required resources already queued: {0}", tmpSelectedQueue);
			} else {
				List<LockableResource> availableCandidates = Collections.synchronizedList(new ArrayList<>());
				for (LockableResource rs : candidates) {
					if (!rs.isReserved() && !rs.isLocked() && !rs.isQueued())
						availableCandidates.add(rs);
					else
						LOGGER.log(Level.FINE, "Removing resource: {0} [Reserved={1},Locked={2},Queued={3}]",
								new Object[]{rs, rs.isReserved(), rs.isLocked(), rs.isQueued()});
				}
				LOGGER.log(Level.FINEST, "Available candidates*: {0}", availableCandidates);

				// only use fancy logic if we don't need to lock all of them
				if (numRequired < availableCandidates.size()) {
					LOGGER.log(Level.FINEST, "Selecting {0} resources from {1} available", new Object[]{numRequired, availableCandidates.size()});
					if (!loadBalancingLabels.isEmpty() && loadBalancingLabels.size() > 1) {
						LOGGER.log(Level.FINEST, "Load balancing labels: {0}", loadBalancingLabels);
						// now filter based on the load balancing labels parameter
						// first break our available candidates into a list for each LB label
						Map<String, List<LockableResource>> groups = new HashMap<>(loadBalancingLabels.size() + 1);
						for (LockableResource r : availableCandidates) {
							boolean foundLabel = false;
							for (String label : loadBalancingLabels) {
								if (r.isValidLabel(label)) {
									foundLabel = true;
									if (!groups.containsKey(label))
										groups.put(label, new ArrayList<>());
									groups.get(label).add(r);
									break;
								}
							}
							if (!foundLabel) {
								if (!groups.containsKey(null)) groups.put(null, new ArrayList<>());
								groups.get(null).add(r);
							}
						}
						LOGGER.log(Level.FINER, "Load Balancing Groups: {0}", groups);
						// now repeatedly select a candidate resource from the label with the lowest current usage
						boolean resourcesLeft = true;
						while (tmpSelectedQueue.size() < numRequired && resourcesLeft) {
							resourcesLeft = false;
							double lowestUsage = 2;
							String lowestUsageLabel = null;
							for (String label : groups.keySet()) {
								if (groups.get(label).size() > 0) {
									double usage = calculateLbLabelUsage(label);
									if (usage < lowestUsage) {
										resourcesLeft = true;
										lowestUsage = usage;
										lowestUsageLabel = label;
									}
								}
							}
							LOGGER.log(Level.FINEST, "Lowest usage label: {0}", lowestUsageLabel);
							if (resourcesLeft) {
								List<LockableResource> group = groups.get(lowestUsageLabel);
								LockableResource r = selectResourceToUse(group);
								group.remove(r);
								tmpSelectedQueue.add(r);
								r.setQueued(queueItem.getId(), queueItemProject);
								LOGGER.log(Level.FINER, "Queued lb resource lock on: {0}", r);
							}
						}
					} else {
						// create a map of candidates by label match so we can use the most selected and work down
						TreeMap<Double, List<LockableResource>> availableCandidatesMap = new TreeMap<>(Collections.reverseOrder());
						if (requiredResources.usePercentMatching) {
                            LOGGER.log(Level.FINEST, "Using percent matching...");
							for (LockableResource r : availableCandidates) {
								int i = getResourcesNumberOfMatchedLabels(r, requiredResources.requiredNames, requiredResources.env);
								LOGGER.log(Level.FINE, "Resource {0} matched labels: {1} / {2}",
										new Object[]{r.getName(), i, r.getLabelSet().size()});
								if (i == 0 || r.getLabelSet().size() == 0 || i > r.getLabelSet().size()) {
									// there was a matched name or groovy script, lets not use this
									LOGGER.log(Level.FINEST, "Resource {0} has an incompatibility with label matching, skipping...", r.getName());
									availableCandidatesMap.clear();
									availableCandidatesMap.put(0.0, availableCandidates);
									break;
								}
								// alright, we should be good to work through the matches
								double percentMatch = ((double) i) / ((double) r.getLabelSet().size());
								if (availableCandidatesMap.containsKey(percentMatch)) {
									LOGGER.log(Level.FINEST, "Adding resource {0} to list[{1}]", new Object[]{r.getName(), percentMatch});
									availableCandidatesMap.get(percentMatch).add(r);
								} else {
									LOGGER.log(Level.FINEST, "Creating list[{1}] with resource {0}", new Object[]{r.getName(), percentMatch});
									availableCandidatesMap.put(percentMatch, new ArrayList<>(Collections.singletonList(r)));
								}
							}
						} else {
                            LOGGER.log(Level.FINEST, "Not using percent matching...");
							availableCandidatesMap.clear();
							availableCandidatesMap.put(0.0, availableCandidates);
						}
						LOGGER.log(Level.FINEST, "Available candidates map*: {0}", availableCandidatesMap);
						for (Map.Entry<Double, List<LockableResource>> entry : availableCandidatesMap.entrySet()) {
							LOGGER.log(Level.FINEST, "Working through map entry[{0}]*: {1}", new Object[]{entry.getValue().size(), entry});
							while (tmpSelectedQueue.size() < numRequired && entry.getValue().size() > 0) {
								final LockableResource r = selectResourceToUse(entry.getValue());
								entry.getValue().remove(r);
								tmpSelectedQueue.add(r);
								r.setQueued(queueItem.getId(), queueItemProject);
								LOGGER.log(Level.FINER, "Queued resource lock on: {0}", r);
							}
							// hard check to see if we got everything
							if (tmpSelectedQueue.size() >= numRequired) {
								LOGGER.log(Level.FINEST, "Found all needed resources [{0}/{1}]: {2}",
										new Object[]{tmpSelectedQueue.size(), numRequired, tmpSelectedQueue});
								break;
							}
						}
					}
				} else {
					LOGGER.log(Level.FINER, "Selecting all available specified resources.");
					tmpSelectedQueue.addAll(availableCandidates.stream().collect(Collectors.toList()));
				}

				LOGGER.log(Level.FINE, "Selected resources: {0}", tmpSelectedQueue);
			}

			for (LockableResource r : tmpSelectedQueue) {
				if (overallSelected.containsKey(r)) {
					LOGGER.log(Level.FINE, "Trying to lock the same resource already selected: {0}", r);
					continue;
				}
				overallSelected.put(r, requiredResources);
			}
		}
		// if did not get wanted amount or did not get all
		if (overallSelected.size() != overallTotalNumRequired) {
			LOGGER.log(Level.FINEST, "{0} found {1} resource(s) to queue. Waiting for correct amount: {2}.",
					new Object[]{queueItemProject, overallSelected.size(), overallTotalNumRequired});
			// just to be sure, clean up since we wont use it now
			overallSelected.keySet().forEach(LockableResource::unqueue);
			return null;
		}
		LOGGER.log(Level.FINER, "Queuing locks for selected resources: {0}", overallSelected);
		action.matchedResources.clear();
		for (LockableResource r : overallSelected.keySet()) {
			LockableResourcesStruct rsc = overallSelected.get(r);
			r.setQueued(queueItem.getId(), queueItemProject);
			action.matchedResources.add(r.getName());
			action.matchedResourcesMap.put(r.getName(), rsc);
		}
		return overallSelected.keySet();
	}

	private synchronized LockableResource selectResourceToUse(List<LockableResource> resources) {
		if (useResourcesEvenly) {
			return resources.get(rand.nextInt(resources.size()));
		}
		return resources.get(0);
	}

	private int getResourcesNumberOfMatchedLabels(LockableResource resource, String requiredNames, EnvVars env) {
		requiredNames = Util.fixEmptyAndTrim(requiredNames);
		if (requiredNames == null) return 0;
		if (requiredNames.startsWith(Constants.GROOVY_LABEL_MARKER)) {
			// not supported yet, will have to filter out text
			return 0;
		} else if (requiredNames.startsWith(Constants.EXACT_LABEL_MARKER)) {
			requiredNames = requiredNames.replace(Constants.EXACT_LABEL_MARKER, "");
		} // else
		int numMatches = 0;
		for (String name : requiredNames.split("\\s+")) {
			if (name.startsWith("%") && name.endsWith("%")){
				name = "${" + name.substring(1, name.length() - 1) + "}";
			}
			if (labelsCache.containsKey(name) && labelAliases.containsKey(name)) {
				name = labelAliases.get(name);
			}
			name = env.expand(name);
			if (resource.getLabelSet().contains(name)) {
				numMatches++;
			}
		}
		return numMatches;
	}

	// Adds already selected (in previous queue round) resources to 'selected'
	// Return false if another item queued for this project -> bail out
	private synchronized void checkCurrentResourcesStatus(Collection<LockableResource> selected,
														  Collection<String> matchedResources,
														  long queueId) {
		for (String rName : matchedResources) {
			for (LockableResource r : resources) {
				if ( r.getName().equals(rName) ) {
					if ( r.isQueuedByTask(queueId) ) {
						selected.add(r);
					}
					break;
				}
			}
		}
	}

	public synchronized boolean lock(Collection<String> resourceNames, AbstractBuild<?, ?> build) {
		ArrayList<LockableResource> resourcesToLock = new ArrayList<>(resourceNames.size());
		for (String rName : resourceNames) {
			LockableResource r = fromName(rName);
			if (r == null || r.isReserved() || r.isLocked()) {
				return false;
			}
			resourcesToLock.add(r);
		}
		for (LockableResource r : resourcesToLock) {
			r.unqueue();
			r.setBuild(build);
		}
		return true;
	}

	public synchronized void unlock(List<LockableResource> resources, AbstractBuild<?, ?> build) {
		for (LockableResource r : resources) {
			if (build == null || build == r.getBuild()) {
				r.unqueue();
				r.setBuild(null);
			}
		}
	}

	public synchronized boolean reserve(List<LockableResource> resources, String userName) {
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
		resources.forEach(LockableResource::unReserve);
		save();
	}

	public synchronized void reset(List<LockableResource> resources) {
		resources.forEach(LockableResource::reset);
		save();
	}

	@Override
	public synchronized void configure(StaplerRequest req, JSONObject json) {
		String loadBalancingLabelsString = json.getString("loadBalancingLabels").trim();
		loadBalancingLabels.clear();
		Collections.addAll(loadBalancingLabels, loadBalancingLabelsString.split(RESOURCES_SPLIT_REGEX));
		useResourcesEvenly = json.getBoolean("useResourcesEvenly");
        usePercentMatchingDefault = json.getBoolean("usePercentMatchingDefault");

		List<KeyValuePair> aliases = req.bindJSONToList(KeyValuePair.class, json.get("labelAliases"));
		labelAliases.clear();
		for ( KeyValuePair p : aliases ) {
			if ( p.key != null && p.value != null ) {
				labelAliases.put(p.key, p.value);
			}
		}

		List<LockableResource> newResources = req.bindJSONToList(LockableResource.class, json.get("resources"));
		for (LockableResource r : newResources) {
			LockableResource old = fromName(r.getName());
			if (old != null) {
				r.setBuild(old.getBuild());
				r.setQueued(r.getQueueItemId(), r.getQueueItemProject());
			}
		}
		resources.clear();
		resources.addAll(newResources);
		save();
	}
	
	public synchronized boolean addResourceLabel( LockableResource res, String label ) {
		Set<String> resLabels = res.getModifyableLabelSet();
		boolean result = resLabels.add(label);
		if ( result ) {
			save();
		}
		return result;
	}
	
	public synchronized boolean removeResourceLabel( LockableResource res, String label ) {
		Set<String> resLabels = res.getModifyableLabelSet();
		boolean result = resLabels.remove(label);
		if ( result ) {
			save();
		}
		return result;
	}

	public static LockableResourcesManager get() {
		Jenkins jenkins = Jenkins.getActiveInstance();
		return jenkins.getPlugin(LockableResourcesManager.class);
	}

	@Override
	public synchronized void load() throws IOException {
		super.load();
		buildCaches();
	}

	@Override
	public synchronized void save() {
		try {
			super.save();
			buildCaches();
		}
		catch ( IOException ex ) {
			LOGGER.log(Level.SEVERE, "Unable to save configuration!", ex);
		}
	}

	private synchronized void buildCaches() {
		labelsCache.clear();
		lbLabelsCache.clear();
		resourceMapCache.clear();
		for ( LockableResource r : resources ) {
			boolean foundLbLabel = false;
			for ( String label : r.getLabelSet() ) {
				if ( !labelsCache.containsKey(label) ) labelsCache.put(label, new HashSet<LockableResource>());
				labelsCache.get(label).add(r);

				if (loadBalancingLabels.contains(label)) {
					foundLbLabel = true;
					if ( !lbLabelsCache.containsKey(label) ) lbLabelsCache.put(label, new HashSet<LockableResource>());
					lbLabelsCache.get(label).add(r);
				}
			}
			if ( !foundLbLabel ) {
				if ( !lbLabelsCache.containsKey(null) ) lbLabelsCache.put(null, new HashSet<LockableResource>());
				lbLabelsCache.get(null).add(r);
			}
			resourceMapCache.put(r.getName(), r);
		}

		// process label aliases
		for ( String alias : labelAliases.keySet() ) {
			if ( !labelsCache.containsKey(alias) && fromName(alias) == null ) {
				String aliasedLabel = labelAliases.get(alias);
				if ( labelsCache.containsKey(aliasedLabel) ) {
					labelsCache.put(alias, labelsCache.get(aliasedLabel));
				}
			}
		}
	}

	private synchronized double calculateLbLabelUsage( String label ) {
		int used = 0;
		for ( LockableResource r : lbLabelsCache.get(label) ) {
			if ( !r.isFree() ) used++;
		}
		return (double)used / lbLabelsCache.get(label).size();
	}

	@Override
	protected XmlFile getConfigXml() {
		File f = new File(Jenkins.getInstance().getRootDir(), this.getClass().getName() + ".xml");
		return new XmlFile(Jenkins.XSTREAM, f);
	}

	public static class KeyValuePair {
		public final String key;
		public final String value;
		@DataBoundConstructor
		public KeyValuePair(String key, String value) {
			this.key = Util.fixEmptyAndTrim(key);
			this.value = Util.fixEmptyAndTrim(value);
		}
	}
}
