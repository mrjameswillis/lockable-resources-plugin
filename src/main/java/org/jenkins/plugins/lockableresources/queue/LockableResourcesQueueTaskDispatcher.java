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
import hudson.model.*;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.RequiredResourcesParameterValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

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

			EnvVars env = new EnvVars();
			ArrayList<LockableResourcesStruct> resources = new ArrayList<>();
			for ( ParametersAction pa : item.getActions(ParametersAction.class) ) {
				for ( ParameterValue pv : pa.getParameters() ) {
					if ( pv instanceof RequiredResourcesParameterValue ) {
						resources.add(new LockableResourcesStruct((RequiredResourcesParameterValue)pv));
					} else if (pv instanceof StringParameterValue) {
						env.put(pv.getName(), pv.getValue().toString());
					}
				}
			}
			resources.addAll(Utils.requiredResources(project, env));
			boolean isOk = true;
			for (LockableResourcesStruct r : resources) {
				if (r.required == null) {
					isOk = false;
					break;
				}
			}
			if ( resources.isEmpty() || !isOk ) {
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
				return new BecauseResourcesLocked(resources, env);
			}
		}
		catch ( RuntimeException ex ) {
			LOGGER.log(Level.SEVERE, "Unexpected exception!", ex);
			throw ex;
		}
	}

	public static class BecauseResourcesLocked extends CauseOfBlockage {

		private final ArrayList<LockableResourcesStruct> rscStruct;
		private final EnvVars env;

		public BecauseResourcesLocked(ArrayList<LockableResourcesStruct> r) {
			this.rscStruct = r;
			this.env = new EnvVars();
		}

		public BecauseResourcesLocked(ArrayList<LockableResourcesStruct> r, EnvVars env) {
			this.rscStruct = r;
			this.env = env;
		}

		@Override
		public String getShortDescription() {
			StringBuilder sb = new StringBuilder("Waiting for resources: ");
			boolean first = true;
			for (LockableResourcesStruct r : rscStruct) {
				if (!first) {
					sb.append(", ");
				}
				first = false;
				sb.append(env.expand(r.requiredNames));
			}
			return sb.toString();
		}
	}

}
