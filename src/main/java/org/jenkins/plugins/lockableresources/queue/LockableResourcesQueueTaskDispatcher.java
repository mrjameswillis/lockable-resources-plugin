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
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.RequiredResourcesParameterValue;
import org.jenkins.plugins.lockableresources.RequiredResourcesProperty;

@Extension
public class LockableResourcesQueueTaskDispatcher extends QueueTaskDispatcher {

	static final Logger LOGGER = Logger
			.getLogger(LockableResourcesQueueTaskDispatcher.class.getName());

	@Override
	public CauseOfBlockage canRun(Queue.Item item) {
		// Skip locking for multiple configuration projects,
		// only the child jobs will actually lock resources.
		if (item.task instanceof MatrixProject)
			return null;

		try {
			AbstractProject<?, ?> project = Utils.getProject(item);
			if (project == null)
				return null;

			ArrayList<LockableResourcesStruct> resources = new ArrayList<>();
			for ( ParametersAction pa : item.getActions(ParametersAction.class) ) {
				for ( ParameterValue pv : pa.getParameters() ) {
					if ( pv instanceof RequiredResourcesParameterValue ) {
						resources.add(new LockableResourcesStruct((RequiredResourcesParameterValue)pv));
					}
				}
			}
			LockableResourcesStruct s = Utils.requiredResources(project);
			if (s != null)
				resources.add(s);
			if ( resources.isEmpty() || resources.stream().anyMatch(lockableResourcesStruct -> lockableResourcesStruct.required == null)) {
				return null;
			}

			LOGGER.log(Level.FINEST, "{0} trying to get resources with these details: {1}",
					new Object[]{project.getFullName(), resources});

			LockableResourcesManager rm = LockableResourcesManager.get();
			//resources.add(new LockableResourcesStruct(new RequiredResourcesProperty("sta", "ACQUIRED_STA", "2"), new EnvVars()));
			Collection<LockableResource> selected = null;
			if (rm != null) {
				selected = rm.queue(resources, item, project.getFullName());
			}

			if (selected != null) {
				LOGGER.log(Level.FINEST, "{0} reserved resources {1}",
						new Object[]{project.getFullName(), selected});
				return null;
			} else {
				LOGGER.log(Level.FINEST, "{0} waiting for resources", project.getFullName());
				return new BecauseResourcesLocked(resources);
			}
		}
		catch ( RuntimeException ex ) {
			LOGGER.log(Level.SEVERE, "Unexpected exception!", ex);
			throw ex;
		}
	}

	public static class BecauseResourcesLocked extends CauseOfBlockage {

		private final ArrayList<LockableResourcesStruct> rscStruct;

		public BecauseResourcesLocked(ArrayList<LockableResourcesStruct> r) {
			this.rscStruct = r;
		}

		@Override
		public String getShortDescription() {
			return "Waiting for resources: " + rscStruct.stream().map(r -> r.requiredNames).collect(Collectors.joining(", "));
		}
	}

}
