/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013-2015, 6WIND S.A.                                 *
 *                          SAP SE                                     *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.queue;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.actions.LockedResourcesBuildAction;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class LockRunListener extends RunListener<AbstractBuild<?, ?>> {

	static final String LOG_PREFIX = "[lockable-resources]";
	static final Logger LOGGER = Logger.getLogger(LockRunListener.class
			.getName());

	@Override
	public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
		// Skip locking for multiple configuration projects,
		// only the child jobs will actually lock resources.
		if (build instanceof MatrixBuild)
			return;

		AbstractProject<?, ?> proj = Utils.getProject(build);
		LockedResourcesBuildAction requiredResourcesAction = build.getAction(LockedResourcesBuildAction.class);
		if ( proj != null && requiredResourcesAction != null && !requiredResourcesAction.matchedResources.isEmpty() ) {
			List<String> required = requiredResourcesAction.matchedResources;
			if (LockableResourcesManager.get().lock(required, build)) {
				requiredResourcesAction.populateLockedResources(build);
				listener.getLogger().printf("%s acquired lock on %s", LOG_PREFIX, required);
				listener.getLogger().println();
				LOGGER.log(Level.FINE, "{0} acquired lock on {1}",
						new Object[]{build.getFullDisplayName(), required});

			} else {
				listener.getLogger().printf("%s failed to lock %s", LOG_PREFIX, required);
				listener.getLogger().println();
				LOGGER.log(Level.FINE, "{0} failed to lock {1}",
						new Object[]{build.getFullDisplayName(), required});
			}
		}
	}

	@Override
	public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException, Run.RunnerAbortedException {
		EnvVars env = new EnvVars();
		// add environment variable
		LockedResourcesBuildAction requiredResourcesAction = build.getAction(LockedResourcesBuildAction.class);
		Map<LockableResourcesStruct, Integer> indexes = new HashMap<>();
		for (String matched : requiredResourcesAction.matchedResources) {
			LockableResourcesStruct s = requiredResourcesAction.matchedResourcesMap.get(matched);
			if (indexes.get(s) == null)
				indexes.put(s, 1);
			else
				indexes.put(s, indexes.get(s) + 1);
			String prefix = null;
			if (s != null ) {
				if (s.requiredVar != null) {
					if (env.get(s.requiredVar, null) != null) {
						env.put(s.requiredVar, env.get(s.requiredVar) + " " + matched);
					} else {
						env.put(s.requiredVar, matched);
					}
				}
				if (s.resourceVarsPrefix != null)
					prefix = s.resourceVarsPrefix;
			}
			LockableResource r = LockableResourcesManager.get().fromName(matched);
			String envProps = r.getProperties();
			if ( envProps != null ) {
				for ( String prop : envProps.split("\\s*[\\r\\n]+\\s*") ) {
					if (prefix != null && !prop.isEmpty()) {
						env.addLine(prefix + indexes.get(s).toString() + prop);
					}
				}
			}
		}
		return Environment.create(env);
	}

	@Override
	public void onCompleted(AbstractBuild<?, ?> build, @Nonnull TaskListener listener) {
		// Skip unlocking for multiple configuration projects,
		// only the child jobs will actually unlock resources.
		if (build instanceof MatrixBuild)
			return;

		// obviously project name cannot be obtained here
		List<LockableResource> required = LockableResourcesManager.get()
				.getResourcesFromBuild(build);
		if (required.size() > 0) {
			LockableResourcesManager.get().unlock(required, build);
			listener.getLogger().printf("%s released lock on %s\n",
					LOG_PREFIX, required);
			LOGGER.log(Level.FINE, "{0} released lock on {1}",
					new Object[]{build.getFullDisplayName(), required});
		}

	}

	@Override
	public void onDeleted(AbstractBuild<?, ?> build) {
		// Skip unlocking for multiple configuration projects,
		// only the child jobs will actually unlock resources.
		if (build instanceof MatrixBuild)
			return;

		List<LockableResource> required = LockableResourcesManager.get()
				.getResourcesFromBuild(build);
		if (required.size() > 0) {
			LockableResourcesManager.get().unlock(required, build);
			LOGGER.log(Level.FINE, "{0} released lock on {1}",
					new Object[]{build.getFullDisplayName(), required});
		}
	}

}
